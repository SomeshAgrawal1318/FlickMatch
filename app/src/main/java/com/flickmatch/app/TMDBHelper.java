package com.flickmatch.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * High-level helper that translates Room filter settings into TMDB API calls
 * and returns processed movie lists via callbacks.
 *
 * Responsibilities:
 * 1. Convert genre names (stored in Room) to TMDB integer IDs
 * 2. Build date-range and certification parameters from Room filters
 * 3. Call the TMDB discover endpoint via {@link RetrofitClient}
 * 4. Return results (and total page count) to the caller's callback
 *
 * Design:
 * Not a singleton - TMDBHelper holds no mutable state. Create instances with
 * new TMDBHelper() wherever needed.  All network calls are asynchronous (enqueue),
 * so callbacks arrive on the main thread.
 */

public class TMDBHelper {

    // Genre name -> TMDB ID lookup //

    /**
     * Maps the genre names stored in Room (and shown in CreateRoomActivity) to
     * the integer IDs that the TMDB API expects in the with_genres query parameter.
     * Source: https://api.themoviedb.org/3/genre/movie/list
     */
    private static final Map<String, Integer> GENRE_MAP = new HashMap<>();
    static {
        GENRE_MAP.put("Action", 28);
        GENRE_MAP.put("Adventure", 12);
        GENRE_MAP.put("Animation",  16);
        GENRE_MAP.put("Comedy", 35);
        GENRE_MAP.put("Crime", 80);
        GENRE_MAP.put("Documentary", 99);
        GENRE_MAP.put("Drama", 18);
        GENRE_MAP.put("Family", 10751);
        GENRE_MAP.put("Fantasy", 14);
        GENRE_MAP.put("History", 36);
        GENRE_MAP.put("Horror", 27);
        GENRE_MAP.put("Music", 10402);
        GENRE_MAP.put("Mystery", 9648);
        GENRE_MAP.put("Romance", 10749);
        GENRE_MAP.put("Science Fiction", 878);
        GENRE_MAP.put("Thriller", 53);
        GENRE_MAP.put("War", 10752);
        GENRE_MAP.put("Western", 37);
    }

    /**
     * Ratings are ordered from most restrictive to most permissive. When the user
     * picks a rating, TMDB's certification.lte filter includes that rating and
     * everything below it; so picking R would include R, PG-13, PG, and G in the
     * following order: G(1) < PG(2) < PG-13(3) < R(4).
     */
    private static final Map<String, Integer> CERT_ORDER = new HashMap<>();
    static {
        CERT_ORDER.put("G", 1);
        CERT_ORDER.put("PG", 2);
        CERT_ORDER.put("PG-13", 3);
        CERT_ORDER.put("R", 4);
    }

    // Callbacks //

    /**
     * Callback for a page of movies fetched from the discover endpoint.
     * totalPages lets the caller know whether more pages exist.
     */
    public interface MovieListCallback {
        void onSuccess(List<Movie> movies, int totalPages);
        void onFailure(String error);
    }

    // callback for a single full movie detail fetch (includes runtime) //
    public interface DetailCallback {
        void onSuccess(Movie movie);
        void onFailure(String error);
    }

    // Public API //

    /**
     * Fetches one page of movies matching the room's filter configuration.
     *
     * Steps:
     * 1. Converts room genre names → comma-separated TMDB IDs
     * 2. Builds ISO-8601 date strings from yearMin/yearMax
     * 3. Determines the most permissive certification the room allows
     * 4. Calls TMDB {@code /discover/movie} asynchronously
     * 5. Returns the raw result list and total page count via callback
     *
     * params:
     * room - the Room whose filter fields drive the query
     * page - 1-based TMDB page number
     * callback - receives the movie list on success, or an error message
     */
    public void fetchMoviesForRoom(Room room, int page, MovieListCallback callback) {
        // genre names -> "28,35,27"
        String genreIds = convertGenreNamesToIds(room.getGenres());

        // date range strings from year ints
        String releaseDateGte = room.getYearMin() + "-01-01";
        String releaseDateLte = room.getYearMax() + "-12-31";

        // highest rating selected becomes the certification.lte cap
        String certLte = getMaxCertification(room.getAgeRatings());

        // fire the network call
        Call<DiscoverResponse> call = RetrofitClient.getService().discoverMovies(
                Constants.TMDB_API_KEY,
                genreIds,
                releaseDateGte,
                releaseDateLte,
                "popularity.desc",
                page,
                50, // minimum vote count - filters out obscure/unrated titles
                "en",
                "US",
                certLte
        );

        call.enqueue(new Callback<DiscoverResponse>() {
            @Override
            public void onResponse(Call<DiscoverResponse> call,
                                   Response<DiscoverResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onFailure("TMDB error: HTTP " + response.code());
                    return;
                }

                DiscoverResponse body   = response.body();
                List<Movie> movies = body.getResults();
                int pages  = body.getTotalPages();

                if (movies == null) {
                    movies = new ArrayList<>();
                }

                callback.onSuccess(movies, pages);
            }

            @Override
            public void onFailure(Call<DiscoverResponse> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    /**
     * Fetches full details for a single movie by its TMDB integer ID.
     *
     * The /discover/movie endpoint omits the runtime field. This is called when
     * the runtime of a specific movie is needed (e.g. when displaying the detail
     * card or filtering after discovery).
     *
     * params:
     * movieId - TMDB integer movie ID
     * callback - receives the fully populated Movie on success
     */
    public void fetchMovieDetails(int movieId, DetailCallback callback) {
        Call<Movie> call = RetrofitClient.getService()
                .getMovieDetails(movieId, Constants.TMDB_API_KEY);

        call.enqueue(new Callback<Movie>() {
            @Override
            public void onResponse(Call<Movie> call, Response<Movie> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onFailure("TMDB error: HTTP " + response.code());
                    return;
                }
                callback.onSuccess(response.body());
            }

            @Override
            public void onFailure(Call<Movie> call, Throwable t) {
                callback.onFailure("Network error: " + t.getMessage());
            }
        });
    }

    // Helpers //

    /**
     * Converts a list of genre name strings (as stored in Room) to a
     * comma-separated string of TMDB integer IDs.
     *
     * e.g. ["Action", "Comedy", "Horror"] -> "28,35,27"
     *
     * Genre names that don't appear in .GENRE_MAP are silently skipped - this is safe
     * because the genre list is always populated from the fixed set in CreateRoomActivity.
     *
     * params:
     * genreNames - list of human-readable genre names
     *
     * this returns comma-separated TMDB genre IDs, or an empty string if none match
     */
    public String convertGenreNamesToIds(List<String> genreNames) {
        if (genreNames == null || genreNames.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        for (String name : genreNames) {
            Integer id = GENRE_MAP.get(name);
            if (id != null) {
                if (sb.length() > 0) sb.append(",");
                sb.append(id);
            }
        }
        return sb.toString();
    }

    /**
     * Returns the highest (most permissive) certification from the user's
     * selected age rating list.
     *
     * TMDB's certification.lte parameter includes all movies rated at or below
     * the given level. So if the user selected [G, PG-13], we pass "PG-13" to
     * get G, PG, and PG-13 films, not just PG-13.
     *
     * This defaults to "R" if the list is empty or no known ratings are found,
     * so the call doesn't unintentionally exclude everything.
     *
     * params:
     * ageRatings - list of selected certification strings, e.g. ["G", "PG-13"]
     *
     * This returns the highest certification string, e.g. "PG-13"
     */
    private String getMaxCertification(List<String> ageRatings) {
        if (ageRatings == null || ageRatings.isEmpty()) return "R";

        String maxCert  = null;
        int    maxOrder = -1;

        for (String cert : ageRatings) {
            Integer order = CERT_ORDER.get(cert);
            if (order != null && order > maxOrder) {
                maxOrder = order;
                maxCert  = cert;
            }
        }

        return maxCert != null ? maxCert : "R";
    }
}