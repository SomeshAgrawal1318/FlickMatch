package com.flickmatch.app;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton that owns the single shared Retrofit instance.
 *
 * Retrofit builds an OkHttp connection pool and a thread pool internally.
 * Creating a new Retrofit object per network call would spin up a fresh pool
 * each time, wasting memory and negating connection reuse (keep-alive, HTTP/2
 * multiplexing). One shared instance across the whole app avoids this waste.
 *
 * e.g. RetrofitClient.getService().discoverMovies(...)
 */
public final class RetrofitClient {

    private static Retrofit retrofit;
    private static TMDBApiService service;

    // to prevent instantiation; all access is through static methods. //
    private RetrofitClient() {}

    /**
     * returns the shared TMDBApiService and lazily initializing the Retrofit
     * instance on the first call.
     *
     * synchronized to be safe if called from multiple threads during cold start.
     *
     * this returns the singleton TMDBApiService implementation
     */
    public static synchronized TMDBApiService getService() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(Constants.TMDB_BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        if (service == null) {
            service = retrofit.create(TMDBApiService.class);
        }
        return service;
    }
}