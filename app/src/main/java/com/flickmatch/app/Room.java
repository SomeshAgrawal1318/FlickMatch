package com.flickmatch.app;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Room is where the movie-swiping happens in FlickMatch.
 *
 * A user will create one, sets up filters (e.g. genres, runtime, ratings, etc.),
 * then shares an invite code for others to join. Everyone swipes through the same
 * movie queue, and the room keeps track of matches and watched movies.
 *
 * This class maps to the Firestore document at /rooms/{roomId}.
 */

public class Room {

    // variables needed for this class //

    // variables for Firestore document ID, which is stored in here for convenience //
    private String roomId;
    private String roomName;
    private String inviteCode;
    private String creatorUid;

    private List<String> memberUids; // UIDs of all members who joined the room (including creator)
    private List<String> genres; // TMDB genres selected as filters for the room e.g. Comedy
    private int maxRuntime; // maximum movie runtime, default: 240mins / 4hrs
    private List<String> ageRatings; // age rating filers e.g. NC16
    private int yearMin; // earliest release year filter, default: 2000
    private int yearMax; // latest release year filter, default: 2026
    private List<String> movieQueue; // ordered list of TMDB movie IDs queued for swiping
    private List<String> matchedMovies; // TMDB movie IDs that have matches i.e. everyone swiped right
    private List<String> watchedMovies; // TMDB movie IDs that have been watched by the group
    private Timestamp createdAt; // Firestore server timestamp when the room was created

    // default no-argument constructor //

    /**
     * required by Firestore for automatic deserialization. when Firestore reads a
     * document and converts it to a Room object, it needs a public no-arg constructor
     * to instantiate the class before populating fields via setters.
     *
     * safe defaults are also initialised so list fields are never null.
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

    // getters
    public String getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public String getInviteCode() { return inviteCode; }
    public String getCreatorUid() { return creatorUid; }
    public List<String> getMemberUids(){ return memberUids; }
    public List<String> getGenres(){ return genres; }
    public int getMaxRuntime(){ return maxRuntime; }
    public List<String> getAgeRatings(){ return ageRatings; }
    public int getYearMin(){ return yearMin; }
    public int getYearMax(){ return yearMax; }
    public List<String> getMovieQueue(){ return movieQueue; }
    public List<String> getMatchedMovies(){ return matchedMovies; }
    public List<String> getWatchedMovies(){ return watchedMovies; }
    public Timestamp getCreatedAt(){ return createdAt; }

    // setters
    public void setRoomId(String roomId){ this.roomId = roomId; }
    public void setRoomName(String roomName){ this.roomName = roomName; }
    public void setInviteCode(String inviteCode){ this.inviteCode = inviteCode; }
    public void setCreatorUid(String creatorUid){ this.creatorUid = creatorUid; }
    public void setMemberUids(List<String> memberUids){ this.memberUids = memberUids; }
    public void setGenres(List<String> genres){ this.genres = genres; }
    public void setMaxRuntime(int maxRuntime){ this.maxRuntime = maxRuntime; }
    public void setAgeRatings(List<String> ageRatings){ this.ageRatings = ageRatings; }
    public void setYearMin(int yearMin){ this.yearMin = yearMin; }
    public void setYearMax(int yearMax){ this.yearMax = yearMax; }
    public void setMovieQueue(List<String> movieQueue){ this.movieQueue = movieQueue; }
    public void setMatchedMovies(List<String> matchedMovies){ this.matchedMovies = matchedMovies; }
    public void setWatchedMovies(List<String> watchedMovies){ this.watchedMovies = watchedMovies; }
    public void setCreatedAt(Timestamp createdAt){ this.createdAt = createdAt; }

    // Firestore serialization

    /**
     * converts a Room object into a Map so it can be saved to Firestore
     *
     * roomId is intentionally excluded as it is the Firestore document path
     * (e.g., /rooms/abc123), not a field inside the document. numeric fields
     * are cast to long because Firestore stores all integer numbers as 64-bit longs internally.
     *
     * the result would be a Map with 13 entries representing the Firestore document fields.
     */

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("roomName", roomName);
        map.put("inviteCode", inviteCode);
        map.put("creatorUid", creatorUid);
        map.put("memberUids", memberUids);
        map.put("genres", genres);
        map.put("maxRuntime", (long) maxRuntime);
        map.put("ageRatings", ageRatings);
        map.put("yearMin", (long) yearMin);
        map.put("yearMax", (long) yearMax);
        map.put("movieQueue", movieQueue);
        map.put("matchedMovies", matchedMovies);
        map.put("watchedMovies", watchedMovies);
        map.put("createdAt", createdAt);
        return map;
    }

    // Builder

    /**
     * Builds a Room instance step by step.
     *
     * Why the Builder pattern?
     * Because Room has 10+ fields, using a regular constructor would be challenging; it's
     * easy to mix up the arguments or even forget one. The Builder pattern lets you set
     * only what you need, by name, in any order, and everything else just uses its default.
     *
     * Example Usage:
     *   Room room = new Room.Builder()
     *       .setRoomName("Friday Night Films")
     *       .setInviteCode("HK39BT")
     *       .setCreatorUid(uid)
     *       .setCreatedAt(Timestamp.now())
     *       .build();
     */

    public static class Builder {

        // mirror all Room fields so the Builder is self-contained //
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

        // initialise all defaults so callers would only need to set what they want //
        public Builder() {
            memberUids = new ArrayList<>();
            movieQueue = new ArrayList<>();
            matchedMovies = new ArrayList<>();
            watchedMovies = new ArrayList<>();
            maxRuntime = 240;
            yearMin = 2000;
            yearMax = 2026;
        }

        public Builder setRoomId(String roomId){
            this.roomId = roomId;
            return this;
        }
        public Builder setRoomName(String roomName){
            this.roomName = roomName;
            return this;
        }
        public Builder setInviteCode(String inviteCode){
            this.inviteCode = inviteCode;
            return this;
        }
        public Builder setCreatorUid(String creatorUid){
            this.creatorUid = creatorUid;
            return this;
        }
        public Builder setMemberUids(List<String> memberUids){
            this.memberUids = memberUids;
            return this;
        }
        public Builder setGenres(List<String> genres){
            this.genres = genres;
            return this;
        }
        public Builder setMaxRuntime(int maxRuntime){
            this.maxRuntime = maxRuntime;
            return this;
        }
        public Builder setAgeRatings(List<String> ageRatings){
            this.ageRatings = ageRatings;
            return this;
        }
        public Builder setYearMin(int yearMin){
            this.yearMin = yearMin;
            return this;
        }
        public Builder setYearMax(int yearMax){
            this.yearMax = yearMax;
            return this;
        }
        public Builder setMovieQueue(List<String> movieQueue){
            this.movieQueue = movieQueue;
            return this;
        }
        public Builder setMatchedMovies(List<String> matchedMovies){
            this.matchedMovies = matchedMovies;
            return this;
        }
        public Builder setWatchedMovies(List<String> watchedMovies){
            this.watchedMovies = watchedMovies;
            return this;
        }
        public Builder setCreatedAt(Timestamp createdAt){
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Constructs the Room from the values set on this Builder and returns
         * a fully initialized Room instance.
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