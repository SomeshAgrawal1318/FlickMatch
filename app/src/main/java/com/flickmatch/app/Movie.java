package com.flickmatch.app;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a movie fetched from the TMDB API.
 *
 * This class represents a movie fetched from TMDB via encapsulation. All
 * fields are private and only accessible through getters and setters; this
 * keeps the internal data protected and means any conversion logic (e.g.
 * turning an int ID into a String) stays in one place rather than being
 * repeated everywhere.
 *
 * The @SerializedName annotations are needed because TMDB's JSON uses
 * snake_case (e.g. poster_path) while Java uses camelCase (like posterPath).
 * Without them, Gson wouldn't know they're the same field and would just
 * leave it as null.
 */

public class Movie {

    // TMDB movie ID //

    /**
     * TMDB returns this as an integer in JSON, but it is treated as a String
     * everywhere else in the app (Firestore movieQueue, matchedMovies,
     * watchedMovies, swipeMap keys). Gson can coerce a JSON integer directly
     * into a String field, so we simply annotate the String field with
     * @SerializedName and let Gson handle the conversion; no extra transient
     * field required.
     */
    @SerializedName("id")
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("overview")
    private String overview;

    // Relative path returned by TMDB, e.g. "/abc123.jpg".
    // Use getFullPosterUrl() for the full URL.
    @SerializedName("poster_path")
    private String posterPath;

    // Relative path returned by TMDB. Use getFullBackdropUrl() for the full URL
    @SerializedName("backdrop_path")
    private String backdropPath;

    // ISO 8601 date string, e.g. "2024-03-15"
    @SerializedName("release_date")
    private String releaseDate;

    // TMDB user rating on a 0–10 scale
    @SerializedName("vote_average")
    private double voteAverage;

    // TMDB popularity score
    @SerializedName("popularity")
    private double popularity;

    // Running time in minutes. 0 if not yet known
    @SerializedName("runtime")
    private int runtime;

    // List of TMDB genre IDs associated with this movie
    @SerializedName("genre_ids")
    private List<Integer> genreIds;

    // Constructors

    /**
     * No-arg constructor required by Gson for automatic JSON deserialisation.
     * Sets safe defaults so partially-populated responses don't cause NPEs.
     */
    public Movie() {
        this.genreIds = new ArrayList<>();
        this.runtime = 0;
        this.voteAverage = 0.0;
        this.popularity = 0.0;
    }

    /**
     * Full constructor for manual construction (e.g. in tests or when building
     * a Movie from a Firestore document rather than a raw TMDB response).
     */
    public Movie(String id, String title, String overview, String posterPath,
                 String backdropPath, String releaseDate, double voteAverage,
                 double popularity, int runtime, List<Integer> genreIds) {
        this.id = id;
        this.title = title;
        this.overview = overview;
        this.posterPath = posterPath;
        this.backdropPath = backdropPath;
        this.releaseDate = releaseDate;
        this.voteAverage = voteAverage;
        this.popularity = popularity;
        this.runtime = runtime;
        this.genreIds = (genreIds != null) ? genreIds : new ArrayList<>();
    }

    // Getters and setters //

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getOverview() {
        return overview;
    }

    public void setOverview(String overview) {
        this.overview = overview;
    }

    public String getPosterPath() {
        return posterPath;
    }

    public void setPosterPath(String posterPath) {
        this.posterPath = posterPath;
    }

    public String getBackdropPath() {
        return backdropPath;
    }

    public void setBackdropPath(String backdropPath) {
        this.backdropPath = backdropPath;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public double getVoteAverage() {
        return voteAverage;
    }

    public void setVoteAverage(double voteAverage) {
        this.voteAverage = voteAverage;
    }

    public double getPopularity() {
        return popularity;
    }

    public void setPopularity(double popularity) {
        this.popularity = popularity;
    }

    public int getRuntime() {
        return runtime;
    }

    public void setRuntime(int runtime) {
        this.runtime = runtime;
    }

    public List<Integer> getGenreIds() {
        return genreIds;
    }

    public void setGenreIds(List<Integer> genreIds) {
        this.genreIds = (genreIds != null) ? genreIds : new ArrayList<>();
    }

    // Helper methods //

    /**
     * Returns the full TMDB poster image URL at w500 resolution.
     * Returns null if posterPath is null (i.e. movie has no poster on TMDB).
     */
    public String getFullPosterUrl() {
        if (posterPath == null) return null;
        return "https://image.tmdb.org/t/p/w500" + posterPath;
    }

    /**
     * Returns the full TMDB backdrop image URL at w780 resolution.
     * Returns null if backdropPath is null (i.e. movie has no backdrop on TMDB).
     */
    public String getFullBackdropUrl() {
        if (backdropPath == null) return null;
        return "https://image.tmdb.org/t/p/w780" + backdropPath;
    }
}