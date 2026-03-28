package com.flickmatch.app;

/**
 * App-wide configuration constants — single source of truth for API keys,
 * base URLs, and default values. Import from here instead of hardcoding
 * strings across multiple files (DRY principle). If a URL or default changes,
 * update it here and the change propagates everywhere automatically.
 */
public final class Constants {

    /**
     * Private constructor prevents anyone from instantiating this class.
     * Constants is a utility class — its fields are accessed statically
     * (Constants.TMDB_BASE_URL), never through an object instance.
     */
    private Constants() {}

    // -------------------------------------------------------------------------
    // TMDB API
    // -------------------------------------------------------------------------

    /** Replace with your real TMDB API key before running the app. */
    public static final String TMDB_API_KEY = "5d93d705b50b62abdca5790d1431d3a0";

    /** Base URL for all TMDB API v3 endpoints. */
    public static final String TMDB_BASE_URL = "https://api.themoviedb.org/3/";

    /** Base URL for TMDB image CDN. Append a size and a path to build a full image URL. */
    public static final String TMDB_IMAGE_BASE_URL = "https://image.tmdb.org/t/p/";

    /** Image size used for poster thumbnails (swipe cards, search results). */
    public static final String TMDB_POSTER_SIZE = "w500";

    /** Image size used for backdrop / hero images. */
    public static final String TMDB_BACKDROP_SIZE = "w780";

    // -------------------------------------------------------------------------
    // Firestore collection names
    // -------------------------------------------------------------------------

    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_ROOMS = "rooms";
    public static final String COLLECTION_SWIPES = "swipes";

    // -------------------------------------------------------------------------
    // Room defaults
    // -------------------------------------------------------------------------

    /** Maximum runtime filter (minutes) applied when creating a new room. */
    public static final int DEFAULT_MAX_RUNTIME = 240;

    /** Earliest release year shown in the movie filter. */
    public static final int DEFAULT_YEAR_MIN = 2000;

    /** Latest release year shown in the movie filter. */
    public static final int DEFAULT_YEAR_MAX = 2026;

    /** Hard cap on the number of members allowed in a single room. */
    public static final int MAX_ROOM_MEMBERS = 8;

    /** Length of the alphanumeric invite code generated for each room. */
    public static final int INVITE_CODE_LENGTH = 6;

    // -------------------------------------------------------------------------
    // Swipe queue
    // -------------------------------------------------------------------------

    /** Prefetch more movies when the local queue drops below this many cards. */
    public static final int QUEUE_PREFETCH_THRESHOLD = 5;

    /** Number of movie results TMDB returns per page from /discover/movie. */
    public static final int MOVIES_PER_PAGE = 20;

    // -------------------------------------------------------------------------
    // Helper methods
    // -------------------------------------------------------------------------

    /**
     * Builds the full TMDB poster URL from a relative path.
     * Useful in adapters and anywhere a Movie object isn't directly available.
     *
     * @param posterPath relative path from TMDB, e.g. "/abc123.jpg"
     * @return full image URL, or null if posterPath is null
     */
    public static String getFullPosterUrl(String posterPath) {
        if (posterPath == null) return null;
        return TMDB_IMAGE_BASE_URL + TMDB_POSTER_SIZE + posterPath;
    }

    /**
     * Builds the full TMDB backdrop URL from a relative path.
     *
     * @param backdropPath relative path from TMDB, e.g. "/xyz789.jpg"
     * @return full image URL, or null if backdropPath is null
     */
    public static String getFullBackdropUrl(String backdropPath) {
        if (backdropPath == null) return null;
        return TMDB_IMAGE_BASE_URL + TMDB_BACKDROP_SIZE + backdropPath;
    }
}
