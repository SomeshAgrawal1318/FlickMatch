package com.flickmatch.app;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * represents the response we get back from TMDB when fetching movies. the TMDB wraps
 * results in a pagination envelope that looks like this:
 * {
 *   "page": 1,
 *   "results": [ {...}, {...} ],
 *   "total_results": 10000,
 *   "total_pages": 500
 * }
 *
 * Gson automatically converts this JSON into a Java object for us. The empty constructor
 * is required for Gson to work - it creates the object first, then fills in the fields.
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

    // required by Gson for deserialization //
    public DiscoverResponse() {}

    public int getPage(){ return page; }
    public List<Movie> getResults(){ return results; }
    public int getTotalResults(){ return totalResults; }
    public int getTotalPages(){ return totalPages; }
}