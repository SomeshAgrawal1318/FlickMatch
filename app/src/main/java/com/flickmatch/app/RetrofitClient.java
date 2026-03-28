package com.flickmatch.app;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton that owns the single shared {@link Retrofit} instance.
 *
 * <p><b>Why Singleton here?</b> Retrofit builds an OkHttp connection pool and
 * a thread pool internally. Creating a new Retrofit object per network call
 * would spin up a fresh pool each time, wasting memory and negating connection
 * reuse (keep-alive, HTTP/2 multiplexing). One shared instance across the
 * whole app avoids this waste.
 *
 * <p>Usage: {@code RetrofitClient.getService().discoverMovies(...)}
 */
public final class RetrofitClient {

    private static Retrofit        retrofit;
    private static TMDBApiService  service;

    /** Prevent instantiation — all access is through static methods. */
    private RetrofitClient() {}

    /**
     * Returns the shared {@link TMDBApiService}, lazily initializing the
     * {@link Retrofit} instance on the first call.
     *
     * <p>Synchronized to be safe if called from multiple threads during cold
     * start (though in practice Android Activities run on the main thread).
     *
     * @return the singleton {@link TMDBApiService} implementation
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
