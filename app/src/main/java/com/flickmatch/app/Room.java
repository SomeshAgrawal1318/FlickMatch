package com.flickmatch.app;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a movie-swiping room in FlickMatch.
 *
 * <p>One person creates a Room, configures filters (genres, runtime, ratings, year range),
 * and shares an invite code. Others join and swipe through the same movie queue.
 * Matches and watched movies are tracked per room.
 *
 * <p>This class maps to the Firestore document at /rooms/{roomId}.
 *
 * <p><b>Why the Builder pattern?</b>
 * Room has 12+ fields. A traditional constructor with that many parameters would be
 * error-prone and unreadable (e.g., easy to swap two String arguments). The Builder
 * lets callers set only the fields they care about, in any order, with named methods,
 * while defaults handle the rest.
 *
 * <p>Example usage:
 * <pre>
 *   Room room = new Room.Builder()
 *       .setRoomName("Movie Night")
 *       .setInviteCode("HK39BT")
 *       .setCreatorUid(currentUser.getUid())
 *       .setGenres(Arrays.asList("Action", "Comedy"))
 *       .setCreatedAt(Timestamp.now())
 *       .build();
 * </pre>
 */
public class Room {

    // -------------------------------------------------------------------------
    // Fields
    // -------------------------------------------------------------------------

    /**
     * The Firestore document ID for this room.
     * Stored in the Java object for convenience, but NOT written to Firestore
     * (it is the document path, not a document field).
     */
    private String roomId;

    private String roomName;
    private String inviteCode;
    private String creatorUid;

    /** UIDs of all members who have joined the room (including the creator). */
    private List<String> memberUids;

    /** TMDB genre names selected as filters for this room (e.g., "Action", "Drama"). */
    private List<String> genres;

    /** Maximum movie runtime in minutes. Defaults to 240 (4 hours). */
    private int maxRuntime;

    /** Age rating filters (e.g., "G", "PG", "PG-13", "R"). */
    private List<String> ageRatings;

    /** Earliest release year filter. Defaults to 2000. */
    private int yearMin;

    /** Latest release year filter. Defaults to 2026. */
    private int yearMax;

    /** Ordered list of TMDB movie IDs queued for swiping. */
    private List<String> movieQueue;

    /** TMDB movie IDs that all members swiped right on (matches). */
    private List<String> matchedMovies;

    /** TMDB movie IDs that have already been watched by the group. */
    private List<String> watchedMovies;

    /** Firestore server timestamp recording when this room was created. */
    private Timestamp createdAt;

    // -------------------------------------------------------------------------
    // No-argument constructor
    // -------------------------------------------------------------------------

    /**
     * Required by Firestore for automatic deserialization.
     * When Firestore reads a document and converts it to a Room object,
     * it needs a public no-arg constructor to instantiate the class before
     * populating fields via setters.
     *
     * <p>Also initializes safe defaults so list fields are never null.
     */
    public Room() {
        memberUids    = new ArrayList<>();
        movieQueue    = new ArrayList<>();
        matchedMovies = new ArrayList<>();
        watchedMovies = new ArrayList<>();
        maxRuntime    = 240;
        yearMin       = 2000;
        yearMax       = 2026;
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getRoomId()              { return roomId; }
    public String getRoomName()            { return roomName; }
    public String getInviteCode()          { return inviteCode; }
    public String getCreatorUid()          { return creatorUid; }
    public List<String> getMemberUids()    { return memberUids; }
    public List<String> getGenres()        { return genres; }
    public int getMaxRuntime()             { return maxRuntime; }
    public List<String> getAgeRatings()   { return ageRatings; }
    public int getYearMin()               { return yearMin; }
    public int getYearMax()               { return yearMax; }
    public List<String> getMovieQueue()   { return movieQueue; }
    public List<String> getMatchedMovies(){ return matchedMovies; }
    public List<String> getWatchedMovies(){ return watchedMovies; }
    public Timestamp getCreatedAt()        { return createdAt; }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setRoomId(String roomId)                        { this.roomId = roomId; }
    public void setRoomName(String roomName)                    { this.roomName = roomName; }
    public void setInviteCode(String inviteCode)                { this.inviteCode = inviteCode; }
    public void setCreatorUid(String creatorUid)                { this.creatorUid = creatorUid; }
    public void setMemberUids(List<String> memberUids)          { this.memberUids = memberUids; }
    public void setGenres(List<String> genres)                  { this.genres = genres; }
    public void setMaxRuntime(int maxRuntime)                   { this.maxRuntime = maxRuntime; }
    public void setAgeRatings(List<String> ageRatings)         { this.ageRatings = ageRatings; }
    public void setYearMin(int yearMin)                         { this.yearMin = yearMin; }
    public void setYearMax(int yearMax)                         { this.yearMax = yearMax; }
    public void setMovieQueue(List<String> movieQueue)          { this.movieQueue = movieQueue; }
    public void setMatchedMovies(List<String> matchedMovies)    { this.matchedMovies = matchedMovies; }
    public void setWatchedMovies(List<String> watchedMovies)    { this.watchedMovies = watchedMovies; }
    public void setCreatedAt(Timestamp createdAt)               { this.createdAt = createdAt; }

    // -------------------------------------------------------------------------
    // Firestore serialization
    // -------------------------------------------------------------------------

    /**
     * Converts this Room to a Map suitable for writing to Firestore.
     *
     * <p>Note: {@code roomId} is intentionally excluded — it is the Firestore
     * document path (e.g., /rooms/abc123), not a field inside the document.
     *
     * <p>Numeric fields are cast to {@code long} because Firestore stores all
     * integer numbers as 64-bit longs internally.
     *
     * @return a Map with 13 entries representing the Firestore document fields.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("roomName",      roomName);
        map.put("inviteCode",    inviteCode);
        map.put("creatorUid",    creatorUid);
        map.put("memberUids",    memberUids);
        map.put("genres",        genres);
        map.put("maxRuntime",    (long) maxRuntime);
        map.put("ageRatings",    ageRatings);
        map.put("yearMin",       (long) yearMin);
        map.put("yearMax",       (long) yearMax);
        map.put("movieQueue",    movieQueue);
        map.put("matchedMovies", matchedMovies);
        map.put("watchedMovies", watchedMovies);
        map.put("createdAt",     createdAt);
        return map;
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Builds a {@link Room} instance step by step.
     *
     * <p><b>Why Builder?</b> Room has 13 configurable fields. A single constructor
     * with 13 parameters would be nearly impossible to call correctly — callers
     * would have to remember the exact order of every parameter. The Builder
     * solves this by letting each field be set by name, in any order, and skipping
     * fields that should keep their defaults.
     *
     * <p>Usage:
     * <pre>
     *   Room room = new Room.Builder()
     *       .setRoomName("Friday Night Films")
     *       .setInviteCode("HK39BT")
     *       .setCreatorUid(uid)
     *       .setCreatedAt(Timestamp.now())
     *       .build();
     * </pre>
     */
    public static class Builder {

        // Mirror all Room fields so the Builder is self-contained.
        private String roomId;
        private String roomName;
        private String inviteCode;
        private String creatorUid;
        private List<String> memberUids;
        private List<String> genres;
        private int maxRuntime;
        private List<String> ageRatings;
        private int yearMin;
        private int yearMax;
        private List<String> movieQueue;
        private List<String> matchedMovies;
        private List<String> watchedMovies;
        private Timestamp createdAt;

        /** Initializes all defaults so callers only need to set what they want. */
        public Builder() {
            memberUids    = new ArrayList<>();
            movieQueue    = new ArrayList<>();
            matchedMovies = new ArrayList<>();
            watchedMovies = new ArrayList<>();
            maxRuntime    = 240;
            yearMin       = 2000;
            yearMax       = 2026;
        }

        public Builder setRoomId(String roomId)                        { this.roomId = roomId;               return this; }
        public Builder setRoomName(String roomName)                    { this.roomName = roomName;            return this; }
        public Builder setInviteCode(String inviteCode)                { this.inviteCode = inviteCode;        return this; }
        public Builder setCreatorUid(String creatorUid)                { this.creatorUid = creatorUid;        return this; }
        public Builder setMemberUids(List<String> memberUids)          { this.memberUids = memberUids;        return this; }
        public Builder setGenres(List<String> genres)                  { this.genres = genres;                return this; }
        public Builder setMaxRuntime(int maxRuntime)                   { this.maxRuntime = maxRuntime;        return this; }
        public Builder setAgeRatings(List<String> ageRatings)         { this.ageRatings = ageRatings;       return this; }
        public Builder setYearMin(int yearMin)                         { this.yearMin = yearMin;              return this; }
        public Builder setYearMax(int yearMax)                         { this.yearMax = yearMax;              return this; }
        public Builder setMovieQueue(List<String> movieQueue)          { this.movieQueue = movieQueue;        return this; }
        public Builder setMatchedMovies(List<String> matchedMovies)    { this.matchedMovies = matchedMovies;  return this; }
        public Builder setWatchedMovies(List<String> watchedMovies)    { this.watchedMovies = watchedMovies;  return this; }
        public Builder setCreatedAt(Timestamp createdAt)               { this.createdAt = createdAt;          return this; }

        /**
         * Constructs the {@link Room} from the values set on this Builder.
         *
         * @return a fully initialized Room instance.
         */
        public Room build() {
            Room room = new Room();
            room.roomId        = this.roomId;
            room.roomName      = this.roomName;
            room.inviteCode    = this.inviteCode;
            room.creatorUid    = this.creatorUid;
            room.memberUids    = this.memberUids;
            room.genres        = this.genres;
            room.maxRuntime    = this.maxRuntime;
            room.ageRatings    = this.ageRatings;
            room.yearMin       = this.yearMin;
            room.yearMax       = this.yearMax;
            room.movieQueue    = this.movieQueue;
            room.matchedMovies = this.matchedMovies;
            room.watchedMovies = this.watchedMovies;
            room.createdAt     = this.createdAt;
            return room;
        }
    }
}
