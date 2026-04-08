package com.flickmatch.app;

/**
 * This is a central place to store API keys, base URLs, and default values.
 * Instead of scattering the same strings across multiple files, everything
 * is contained here; so if any variable needs updating, you change it here,
 * and it applies everywhere automatically.
 */

public final class Constants {

    /**
     * Constants is a utility class as its fields are accessed statically
     * (Constants.TMDB_BASE_URL), and never through an object instance.
     */
    private Constants() {}

    // TMDB API //
    public static final String TMDB_API_KEY = "5d93d705b50b62abdca5790d1431d3a0";
    public static final String TMDB_BASE_URL = "https://api.themoviedb.org/3/"; // base TMDB URL
    public static final String TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"; // base TMDB image URL
    public static final String TMDB_POSTER_SIZE = "w500"; // image size for poster
    public static final String TMDB_BACKDROP_SIZE = "w780"; // image size for backdrop images

    // collection names in Firestore //
    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_ROOMS = "rooms";
    public static final String COLLECTION_SWIPES = "swipes";

    // Room defaults //
    public static final int DEFAULT_MAX_RUNTIME = 240; // max runtime filter in mins
    public static final int DEFAULT_YEAR_MIN = 2000; // earliest release year shown in filter
    public static final int DEFAULT_YEAR_MAX = 2026; // latest release year shown in filter
    public static final int MAX_ROOM_MEMBERS = 8; // max members in a room
    public static final int INVITE_CODE_LENGTH = 6; // length of invite code (alphanumeric)

    // Swipe queue //
    public static final int QUEUE_PREFETCH_THRESHOLD = 5; // prefetch threshold
    public static final int MOVIES_PER_PAGE = 20; // num movies returned from TMDB page

    // Helper methods //
    /**
     * builds the full TMDB poster URL from a relative path. This is useful in adapters
     * and anywhere a Movie object isn't directly available.
     *
     * This takes the partial poster path that TMDB gives us (e.g. /abc123.jpg) and
     * builds it into a full image URL, and returns null if there's no path to work with.
     */
    public static String getFullPosterUrl(String posterPath) {
        if (posterPath == null) return null;
        return TMDB_IMAGE_BASE_URL + TMDB_POSTER_SIZE + posterPath;
    }

    /**
     * builds the full TMDB backdrop URL from a relative path.
     *
     * this takes the partial backdrop path from TMDB and builds it into a full
     * image URL, and returns null if there's no path provided.
     */
    public static String getFullBackdropUrl(String backdropPath) {
        if (backdropPath == null) return null;
        return TMDB_IMAGE_BASE_URL + TMDB_BACKDROP_SIZE + backdropPath;
    }
}