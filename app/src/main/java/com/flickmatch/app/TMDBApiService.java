package com.flickmatch.app;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Retrofit interface mapping Java methods to TMDB HTTP endpoints.
 *
 * Retrofit reads the annotations at runtime and generates a concrete
 * implementation that builds the URL, attaches query parameters, and
 * executes the HTTP request. We never write that boilerplate ourselves.
 *
 * Obtain an instance via RetrofitClient.getService()
 */
public interface TMDBApiService {

    /**
     * Discovers movies matching the given filter criteria.
     *
     * this maps to: GET https://api.themoviedb.org/3/discover/movie
     *
     * params:
     * apiKey - TMDB API key; see Constants.TMDB_API_KEY
     * genres - comma-separated TMDB genre IDs, e.g. "28,35,27"
     * releaseDateGte - earliest release date filter formatted as "YYYY-01-01"
     * releaseDateLte - latest release date filter formatted as "YYYY-12-31"
     * sortBy - sort order; always use "popularity.desc"
     * page - 1-based page index for pagination
     * voteCountGte - minimum vote count; filters out obscure, unrated films
     * language - ISO 639-1 language code; we use "en"
     * certCountry - country whose certification system to apply; "US"
     * certLte - most permissive certification to include, e.g. "PG-13"
     *
     * this returns a Retrofit Call wrapping a DiscoverResponse
     */
    @GET("discover/movie")
    Call<DiscoverResponse> discoverMovies(
            @Query("api_key") String apiKey,
            @Query("with_genres") String genres,
            @Query("primary_release_date.gte") String releaseDateGte,
            @Query("primary_release_date.lte") String releaseDateLte,
            @Query("sort_by") String sortBy,
            @Query("page") int page,
            @Query("vote_count.gte") int voteCountGte,
            @Query("with_original_language") String language,
            @Query("certification_country") String certCountry,
            @Query("certification.lte") String certLte
    );

    /**
     * fetches full details for a single movie, including runtime which
     * the discover endpoint omits.
     *
     * it maps to: GET https://api.themoviedb.org/3/movie/{movie_id}
     *
     * params:
     * movieId - TMDB integer movie ID (path segment, not a query param)
     * apiKey - TMDB API key
     *
     * it returns a Retrofit Call wrapping a Movie
     */
    @GET("movie/{movie_id}")
    Call<Movie> getMovieDetails(
            @Path("movie_id") int movieId,
            @Query("api_key") String apiKey
    );
}