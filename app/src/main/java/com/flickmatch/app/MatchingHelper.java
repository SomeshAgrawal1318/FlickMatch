package com.flickmatch.app;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This is a plain Java utility class that handles the core matching logic for FlickMatch -
 * no Android or Firebase dependencies, just pure data structure logic.
 *
 * A match happens when every member of a room has swiped right on the same movie. This is
 * detected efficiently by intersecting each member's liked movies using HashSets
 *
 * // data structure analysis //
 *
 * 1. HashSet<String> - stores each member's liked movie IDs.
 *    functions:
 *      - add(): O(1) average; hash the string, insert at bucket
 *      - contains(): O(1) average; hash the string, check bucket
 *
 *    this is much faster compared to an ArrayList, which would have
 *    to scan through every element one by one.
 *
 * 2. HashSet.retainAll(other) - in-place set intersection.
 *    - Keeps only elements present in both the receiver set and other.
 *    - Time complexity: O(min(|A|, |B|)) average; iterates the smaller set,
 *      and calling contains() on the larger (O(1) per call with HashSet).
 *    - This is the heart of .findMatches: it removes any movie that at least
 *      one member didn't like, leaving only universal favourites.
 *
 * 3. HashMap<String, Boolean> - each member's raw swipe map.
 *    - get(movieId): O(1) average; used in isMovieMatchedByAll for a per-movie
 *    fast-path that avoids computing the full intersection.
 *
 * Overall complexity of findMatches:
 *      - O(n * m) where n = number of members, m = avg liked movies per member.
 *      - Each retainAll call is O(m); we call it (n-1) times.
 *      - Brute-force alternative (nested loop over every movie * every member) would
 *        be O(M * n) where M is the total number of distinct movies swiped; potentially
 *        tens of thousands. The HashSet intersection limits work to only the liked subset,
 *        which is far smaller.
 *
 * Short-circuit optimization:
 *      If any member has liked zero movies, the intersection is necessarily empty. We return
 *      immediately rather than continuing the loop; this saves O(n × m) work in the common
 *      early-stage case where some members have barely swiped.
 *
 * // observer pattern connection //
 *
 * Whenever a member swipes, Firestore automatically triggers a callback in RoomActivity, which
 * then calls MatchingHelper.findMatches() or isMovieMatchedByAll() to check if a match has
 * occurred. MatchingHelper itself doesn't hold any state; it's just pure logic that gets called
 * each time something changes, keeping it simple and reusable.
 */
public final class MatchingHelper {

    private MatchingHelper() {}

    // Public API //

    /**
     * Computes the set of movie IDs that every member in memberUids has liked (swiped right on).
     *
     * Algorithm - HashSet intersection with retainAll:
     *
     * 1. For each member, collect their liked movie IDs into a HashSet. O(k) per member where
     *    k = number of swipes.
     * 2. Seed a "running intersection" with the first member's liked set.
     * 3. For each subsequent member, call runningSet.retainAll(memberLikedSet); this removes
     *    any movie that member didn't like. O(m) per call.
     * 4. Short-circuit if the running set becomes empty; no further intersection can produce results.
     * 5. Return the final set.
     *
     * Time complexity: O(n * m) - n members, m average liked movies each.
     *
     * params:
     * allSwipes - outer key = memberUid, inner map = movieId -> liked
     * memberUids - all member UIDs that must agree for a match
     *
     * this returns set of movie IDs liked by every member; empty if no matches
     */
    public static Set<String> findMatches(
            Map<String, Map<String, Boolean>> allSwipes,
            List<String> memberUids) {

        // edge cases - nothing to intersect
        if (allSwipes == null || allSwipes.isEmpty()
                || memberUids == null || memberUids.isEmpty()) {
            return new HashSet<>();
        }

        Set<String> runningIntersection = null;

        for (String uid : memberUids) {
            Map<String, Boolean> swipeMap = allSwipes.get(uid);

            // member has not swiped at all - intersection with null = null
            // short-circuit: no matches can exist if any member has zero data.
            if (swipeMap == null || swipeMap.isEmpty()) {
                return new HashSet<>();
            }

            // build member's liked set using HashSet for O(1) contains().
            // ArrayList would make retainAll O(n^2), which takes too long for large queues.
            Set<String> likedByMember = getLikedMoviesForMember(swipeMap);

            if (likedByMember.isEmpty()) {
                // member swiped but never liked anything - intersection is empty.
                return new HashSet<>();
            }

            if (runningIntersection == null) {
                // first member: seed the running intersection with a copy.
                // we copy so retainAll() on subsequent members doesn't mutate the original liked set.
                runningIntersection = new HashSet<>(likedByMember);

            } else {
                // subsequent members: retainAll performs set intersection in-place.
                // after this call, runningIntersection contains only movies that
                // both the accumulated set and this member liked.
                // O(min(|runningIntersection|, |likedByMember|)) per call.
                runningIntersection.retainAll(likedByMember);

                // short-circuit: if the running intersection is already empty,
                // no further members can make it non-empty. Save O((n-i) * m) work.
                if (runningIntersection.isEmpty()) {
                    return runningIntersection;
                }
            }
        }

        return runningIntersection != null ? runningIntersection : new HashSet<>();
    }

    /**
     * Fast-path check: has every member liked this specific movie?
     * Called after each individual swipe in RoomActivity. It's much cheaper than findMatches()
     * when you only need to check one movie rather than the entire liked set.
     *
     * How it works:
     * For each member, it looks up whether they liked the movie in their swipe map. The moment
     * anyone hasn't liked it, it returns false immediately without checking the rest; so the
     * average case is even faster than the worst case.
     *
     * Time complexity:
     * O(m) where m = number of members. Each lookup is O(1), and we do at most one per member.
     *
     * Why not just call findMatches().contains(movieId) directly?
     * That would rebuild and intersect every member's entire liked set just to answer a simple
     * yes/no question about one movie, which is far more work than necessary.
     *
     * params:
     * movieId - TMDB movie ID to test
     * allSwipes - outer key = memberUid, inner map = movieId -> liked
     * memberUids - all member UIDs that must have liked the movie
     *
     * this returns true if and only if every member has explicitly liked movieId
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

            // member has not swiped at all - movie cannot be a match yet
            if (swipeMap == null) return false;

            // O(1) HashMap lookup - did this member swipe on this movie?
            Boolean liked = swipeMap.get(movieId);

            // if null, not yet swiped on this movie
            // if false, explicitly disliked
            // either way, not a match.
            if (liked == null || !liked) return false;
        }

        // every member passed the check
        return true;
    }

    /**
     * Extract the set of movie IDs that one member has liked from their raw swipe map.
     *
     * This returns a HashSet so that callers (.findMatches) can use O(1) contains() and benefit
     * from retainAll's hash-based intersection rather than an O(n) linear scan.
     *
     * params:
     * swipeMap - movieId -> liked for a single member; may be null
     *
     * this returns set of liked movie IDs (never null; empty if swipeMap is null)
     */
    public static Set<String> getLikedMoviesForMember(Map<String, Boolean> swipeMap) {
        /**
         * choosing HashSet over ArrayList (list of comparisons):
         *      - HashSet add() vs ArrayList add() -> same (O(1))
         *      - HashSet contains() vs ArrayList contains() -> HashSet better (O(1) vs O(n))
         *      - retainAll() internally calls contains() on every element, so using HashSet here
         *        turns the intersection from O(n^2) into O(n).
         */
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