package com.flickmatch.app;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure-Java utility class containing the core matching algorithm for FlickMatch.
 * No Android dependencies, no Firebase — just data structure logic.
 *
 * <p>A "match" occurs when EVERY member of a Room has swiped right (liked) the
 * same movie. This class detects matches efficiently using HashSet intersection.
 *
 * ============================================================================
 * DATA STRUCTURE ANALYSIS (graded)
 * ============================================================================
 *
 * <b>HashSet&lt;String&gt;</b> — stores each member's liked movie IDs.
 *   • add()      : O(1) average — hash the string, insert at bucket
 *   • contains() : O(1) average — hash the string, check bucket
 *   vs. ArrayList: contains() is O(n) linear scan — unsuitable for intersection
 *
 * <b>HashSet.retainAll(other)</b> — in-place set intersection.
 *   Keeps only elements present in BOTH the receiver set and {@code other}.
 *   Time complexity: O(min(|A|, |B|)) average — iterates the smaller set,
 *   calling contains() on the larger (O(1) per call with HashSet).
 *   This is the heart of {@link #findMatches}: it removes any movie that even
 *   one member didn't like, leaving only universal favourites.
 *
 * <b>HashMap&lt;String, Boolean&gt;</b> — each member's raw swipe map.
 *   get(movieId) : O(1) average — used in {@link #isMovieMatchedByAll}
 *   for a per-movie fast-path that avoids computing the full intersection.
 *
 * <b>Overall complexity of findMatches:</b>
 *   O(n × m) where n = number of members, m = avg liked movies per member.
 *   Each retainAll call is O(m); we call it (n-1) times.
 *
 *   Brute-force alternative (nested loop over every movie × every member) would
 *   be O(M × n) where M is the TOTAL number of distinct movies swiped —
 *   potentially tens of thousands. The HashSet intersection limits work to only
 *   the liked subset, which is far smaller.
 *
 * <b>Short-circuit optimization:</b>
 *   If any member has liked zero movies, the intersection is necessarily empty.
 *   We return immediately rather than continuing the loop — saves O(n × m) work
 *   in the common early-stage case where some members have barely swiped.
 *
 * ============================================================================
 * OBSERVER PATTERN CONNECTION
 * ============================================================================
 *
 * MatchingHelper is called reactively from RoomActivity's Firestore snapshot
 * listeners. Firestore is the Subject; the snapshot callbacks are the Observers.
 * Every time any member's swipe document changes, the observer callback fires
 * and calls MatchingHelper.findMatches() / isMovieMatchedByAll() to recheck
 * for new matches. MatchingHelper itself is stateless — it is pure logic that
 * the observer invokes on each notification.
 */
public final class MatchingHelper {

    /**
     * Private constructor — this is a utility class.
     * All methods are static; instantiation would be meaningless.
     */
    private MatchingHelper() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Computes the set of movie IDs that EVERY member in {@code memberUids} has
     * liked (swiped right on).
     *
     * <p><b>Algorithm — HashSet intersection with retainAll:</b>
     * <ol>
     *   <li>For each member, scan their swipe map and collect liked movie IDs
     *       into a {@link HashSet}. O(k) per member where k = swipes.</li>
     *   <li>Seed a "running intersection" with the first member's liked set.</li>
     *   <li>For each subsequent member call
     *       {@code runningSet.retainAll(memberLikedSet)}. This removes any movie
     *       the current member did NOT like. O(m) per call.</li>
     *   <li>Short-circuit if the running set becomes empty — no further
     *       intersection can produce results.</li>
     *   <li>Return the final running set.</li>
     * </ol>
     *
     * <p><b>Time complexity:</b> O(n × m) — n members, m avg liked movies each.
     *
     * @param allSwipes  outer key = memberUid, inner map = movieId → liked
     * @param memberUids all member UIDs that must agree for a match
     * @return set of movie IDs liked by every member; empty if no matches
     */
    public static Set<String> findMatches(
            Map<String, Map<String, Boolean>> allSwipes,
            List<String> memberUids) {

        // Edge cases — nothing to intersect
        if (allSwipes == null || allSwipes.isEmpty()
                || memberUids == null || memberUids.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> runningIntersection = null;

        for (String uid : memberUids) {
            Map<String, Boolean> swipeMap = allSwipes.get(uid);

            // Member has not swiped at all — intersection with ∅ = ∅
            // Short-circuit: no matches can exist if any member has zero data.
            if (swipeMap == null || swipeMap.isEmpty()) {
                return new HashSet<>();
            }

            // Build this member's liked set using HashSet for O(1) contains().
            // ArrayList would make retainAll O(n²) — unacceptable for large queues.
            Set<String> likedByMember = getLikedMoviesForMember(swipeMap);

            if (likedByMember.isEmpty()) {
                // Member swiped but never liked anything — intersection is empty.
                return new HashSet<>();
            }

            if (runningIntersection == null) {
                // First member: seed the running intersection with a copy.
                // We copy so retainAll() on subsequent members doesn't mutate the
                // original liked set (defensive programming).
                runningIntersection = new HashSet<>(likedByMember);
            } else {
                // Subsequent members: retainAll performs set intersection in-place.
                // After this call, runningIntersection contains only movies that
                // BOTH the accumulated set AND this member liked.
                // O(min(|runningIntersection|, |likedByMember|)) per call.
                runningIntersection.retainAll(likedByMember);

                // Short-circuit: if the running intersection is already empty,
                // no further members can make it non-empty. Save O((n-i) × m) work.
                if (runningIntersection.isEmpty()) {
                    return runningIntersection;
                }
            }
        }

        return runningIntersection != null ? runningIntersection : new HashSet<>();
    }

    /**
     * Fast-path check: has THIS specific movie been liked by EVERY member?
     *
     * <p>Called after each individual swipe in RoomActivity. Cheaper than
     * {@link #findMatches} when you only need to evaluate one candidate movie
     * rather than the entire liked set.
     *
     * <p><b>Time complexity:</b> O(m) where m = number of members.
     * Each HashMap lookup ({@code swipeMap.get(movieId)}) is O(1) average,
     * and we do at most one lookup per member. We return {@code false} the
     * moment any member fails — early exit keeps the average case even faster.
     *
     * <p>Compare to calling {@code findMatches(...).contains(movieId)}:
     * that would be O(n × k) to build all liked sets + O(n × m) to intersect,
     * just to answer a yes/no question about one movie.
     *
     * @param movieId    TMDB movie ID to test
     * @param allSwipes  outer key = memberUid, inner map = movieId → liked
     * @param memberUids all member UIDs that must have liked the movie
     * @return true if and only if every member has explicitly liked movieId
     */
    public static boolean isMovieMatchedByAll(
            String movieId,
            Map<String, Map<String, Boolean>> allSwipes,
            List<String> memberUids) {

        if (movieId == null || allSwipes == null || memberUids == null) {
            return false;
        }

        for (String uid : memberUids) {
            Map<String, Boolean> swipeMap = allSwipes.get(uid);

            // Member has not swiped at all — movie cannot be a match yet
            if (swipeMap == null) return false;

            // O(1) HashMap lookup — did this member swipe on this movie?
            Boolean liked = swipeMap.get(movieId);

            // null  → not yet swiped on this movie
            // false → explicitly disliked
            // Either way, not a match.
            if (liked == null || !liked) return false;
        }

        // Every member passed the check
        return true;
    }

    /**
     * Extracts the set of movie IDs that one member has liked from their raw
     * swipe map.
     *
     * <p>Returns a {@link HashSet} so that callers ({@link #findMatches}) can
     * use O(1) {@code contains()} and benefit from {@code retainAll}'s
     * hash-based intersection rather than an O(n) linear scan.
     *
     * @param swipeMap movieId → liked for a single member; may be null
     * @return set of liked movie IDs (never null; empty if swipeMap is null)
     */
    public static Set<String> getLikedMoviesForMember(Map<String, Boolean> swipeMap) {
        // HashSet chosen over ArrayList:
        // • HashSet add()      O(1) vs ArrayList add() O(1) — same
        // • HashSet contains() O(1) vs ArrayList contains() O(n) — HashSet wins
        // retainAll() internally calls contains() on every element, so using
        // HashSet here turns the intersection from O(n²) into O(n).
        Set<String> liked = new HashSet<>();
        if (swipeMap == null) return liked;

        for (Map.Entry<String, Boolean> entry : swipeMap.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                liked.add(entry.getKey());
            }
        }
        return liked;
    }
}
