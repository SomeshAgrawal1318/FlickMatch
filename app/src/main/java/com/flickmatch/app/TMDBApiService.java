package com.flickmatch.app;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Retrofit interface mapping Java methods to TMDB HTTP endpoints.
 *
 * <p>Retrofit reads the annotations at runtime and generates a concrete
 * implementation that builds the URL, attaches query parameters, and
 * executes the HTTP request. We never write that boilerplate ourselves.
 *
 * <p>Obtain an instance via {@link RetrofitClient#getService()}.
 */
public interface TMDBApiService {

    /**
     * Discovers movies matching the given filter criteria.
     *
     * <p>Maps to: {@code GET https://api.themoviedb.org/3/discover/movie}
     *
     * @param apiKey         TMDB API key — see {@link Constants#TMDB_API_KEY}
     * @param genres         comma-separated TMDB genre IDs, e.g. {@code "28,35,27"}
     * @param releaseDateGte earliest release date filter, format {@code "YYYY-01-01"}
     * @param releaseDateLte latest  release date filter, format {@code "YYYY-12-31"}
     * @param sortBy         sort order — we always use {@code "popularity.desc"}
     * @param page           1-based page index for pagination
     * @param voteCountGte   minimum vote count; filters out obscure, unrated films
     * @param language       ISO 639-1 language code; we use {@code "en"}
     * @param certCountry    country whose certification system to apply; {@code "US"}
     * @param certLte        most permissive certification to include, e.g. {@code "PG-13"}
     * @return a Retrofit {@link Call} wrapping a {@link DiscoverResponse}
     */
    @GET("discover/movie")
    Call<DiscoverResponse> discoverMovies(
            @Query("api_key")                    String apiKey,
            @Query("with_genres")                String genres,
            @Query("primary_release_date.gte")   String releaseDateGte,
            @Query("primary_release_date.lte")   String releaseDateLte,
            @Query("sort_by")                    String sortBy,
            @Query("page")                       int    page,
            @Query("vote_count.gte")             int    voteCountGte,
            @Query("with_original_language")     String language,
            @Query("certification_country")      String certCountry,
            @Query("certification.lte")          String certLte
    );

    /**
     * Fetches full details for a single movie, including {@code runtime} which
     * the discover endpoint omits.
     *
     * <p>Maps to: {@code GET https://api.themoviedb.org/3/movie/{movie_id}}
     *
     * @param movieId TMDB integer movie ID (path segment, not a query param)
     * @param apiKey  TMDB API key
     * @return a Retrofit {@link Call} wrapping a {@link Movie}
     */
    @GET("movie/{movie_id}")
    Call<Movie> getMovieDetails(
            @Path("movie_id") int    movieId,
            @Query("api_key") String apiKey
    );
}
