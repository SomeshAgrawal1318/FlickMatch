package com.flickmatch.app;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Maps TMDB's paginated discover response to a Java object.
 *
 * <p>TMDB wraps movie results in a pagination envelope:
 * <pre>
 * {
 *   "page": 1,
 *   "results": [ {...}, {...} ],
 *   "total_results": 10000,
 *   "total_pages": 500
 * }
 * </pre>
 *
 * <p>Gson deserializes the JSON directly into this class. The no-arg
 * constructor is required by Gson — it instantiates the class before
 * populating fields via reflection.
 */
public class DiscoverResponse {

    @SerializedName("page")
    private int page;

    @SerializedName("results")
    private List<Movie> results;

    @SerializedName("total_results")
    private int totalResults;

    @SerializedName("total_pages")
    private int totalPages;

    /** Required by Gson for deserialization. */
    public DiscoverResponse() {}

    public int getPage()              { return page; }
    public List<Movie> getResults()   { return results; }
    public int getTotalResults()      { return totalResults; }
    public int getTotalPages()        { return totalPages; }
}
