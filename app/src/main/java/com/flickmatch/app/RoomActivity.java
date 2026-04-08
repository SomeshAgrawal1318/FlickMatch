package com.flickmatch.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * This is the main screen of FlickMatch, handling the Lobby, Swiping, and
 * Watchlist in one Activity
 *
 * Key Data Structure Algorithms (DSA):
 * 1. movieQueue - A LinkedList used as a queue. Adding and removing movies from
 * either end is instant (O(1)), whereas an ArrayList would have to shift every
 * element when removing from the front, which gets slow
 *
 * 2. mySwipes - A HashMap that tracks which movies the user has already swiped
 * on, so the same card never shows up twice. Lookups are instant regardless of
 * how many movies there are
 *
 * 3. movieCache - A HashMap that stores movie data by TMDB ID, so when building
 * the Watchlist, we can grab the details instantly without making extra API calls.
 *
 * Firestore listeners watch for any changes to the room or swipes in real time and
 * automatically update the UI when something changes. They are started in onStart()
 * and cleaned up in onStop() so they don't keep running and leak memory when the
 * screen isn't visible
 *
 */

public class RoomActivity extends AppCompatActivity {

    // intent extra keys which matches what is sent from MainActivity & CreateRoomActivity //
    public static final String EXTRA_ROOM_ID = "ROOM_ID";
    public static final String EXTRA_ROOM_NAME = "ROOM_NAME";

    // Firebase helpers //
    private FirebaseHelper firebaseHelper;
    private TMDBHelper tmdbHelper;
    private String currentUid;

    // Room state //
    private String roomId;
    private Room room;   // updated whenever the Firestore listener fires

    /**
     * Firestore snapshot listeners
     * the activity starts on onStart() and removed on onStop() such that
     * they're active whenever the activity is visible and prevent memory leaks
     * and stale callbacks after the activity has been destroyed respectively
     */

    private ListenerRegistration roomListener;
    private ListenerRegistration swipesListener;

    /**
     * DSA - using LinkedList<Movie> as a Queue
     *
     * LinkedList takes O(1) time to perform any function, e.g. peek(), poll(), offer(), etc.
     * Compared to that, ArrayList takes O(n) time to just remove from index 0.
     */
    private final Queue<Movie> movieQueue = new LinkedList<>();

    /**
     * DSA - using HashMap for "have I swiped this already?" lookup, which takes O(1) time.
     *
     * The key would be the TMDB movie ID, and the values will be true or false depending on
     * whether the movie has been liked or disliked. This prevents the same card shown again
     * after re-entering the activity.
     */
    private final Map<String, Boolean> mySwipes = new HashMap<>();

    /**
     * Movie object cache avoids fetching the movie details twice. This is populated as
     * the cards are loaded into the activity, from where the Watchlist reads from first.
     */
    private final Map<String, Movie> movieCache = new HashMap<>();

    // Latest snapshot of all members' swipes, updated by swipesListener //
    private Map<String, Map<String, Boolean>> latestAllSwipes = new HashMap<>();

    // TMDB pagination //
    private int currentPage = 1;
    private int totalPages  = 1;

    // States of Watchlist //
    private final List<Movie> watchlistMovies = new ArrayList<>();
    private final List<Movie> watchedMovies = new ArrayList<>();
    private WatchlistAdapter watchlistAdapter;
    private WatchlistAdapter watchedAdapter;
    private String currentSort = "order"; // "order" | "rating" | "popularity"

    // Views for different sections //

    // Tabs
    private TextView tabLobby, tabSwipe, tabWatchlist;
    private ViewFlipper viewFlipper;

    // Lobby
    private TextView textRoomTitle, textInviteCode;
    private Button buttonCopyCode, buttonStartSwiping;
    private LinearLayout layoutMembers;

    // Swipe
    private FrameLayout movieCard;
    private ImageView imagePoster;
    private TextView textMovieTitle, textMovieYear, textMovieRating, textMovieOverview;
    private TextView textEmptySwipe;
    private LinearLayout layoutSwipeButtons;
    private Button buttonNope, buttonLike;

    // Watchlist
    private RecyclerView recyclerWatchlist, recyclerWatched;
    private TextView textEmptyWatchlist, headerMatched, headerWatched;
    private TextView sortMatchOrder, sortRating, sortPopularity;

    // Lifecycle of activity //
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        firebaseHelper = FirebaseHelper.getInstance();
        tmdbHelper = new TMDBHelper();
        currentUid = firebaseHelper.getCurrentUid();

        roomId = getIntent().getStringExtra(EXTRA_ROOM_ID);
        String roomName = getIntent().getStringExtra(EXTRA_ROOM_NAME);

        if (roomId == null) {
            Toast.makeText(this, "Room not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViews();
        setupTabs();
        setupSwipeButtons();
        setupWatchlistAdapters();
        setupSortButtons();

        if (roomName != null && !roomName.isEmpty()) {
            textRoomTitle.setText(roomName);
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(roomName);
        }

        // initial room fetch to populate Lobby before the listener fires //
        loadRoom();
    }

    /**
     * starting Firestore listeners when the Activity becomes visible.
     *
     * instead of constantly asking Firestore 'has anything changed?', we register
     * listeners (listenToRoom / listenToSwipes) that Firestore calls automatically
     * whenever something updates. It pushes the changes to us in real time, so we
     * don't have to keep checking.
     */
    @Override
    protected void onStart() {
        super.onStart();

        // observe room document changes (new members joining, queue updates, etc.) //
        roomListener = firebaseHelper.listenToRoom(roomId, new FirebaseHelper.RoomCallback() {
            @Override
            public void onSuccess(Room updatedRoom) {
                room = updatedRoom;
                refreshLobbyMembers();
                refreshWatchlist();
            }
            @Override
            public void onFailure(String error) {
                // not fatal since we already have an initial fetch //
            }
        });

        // observe every member's swipe document which is needed for real-time match detection //
        swipesListener = firebaseHelper.listenToSwipes(roomId, new FirebaseHelper.AllSwipesCallback() {
            @Override
            public void onSuccess(Map<String, Map<String, Boolean>> allSwipes) {
                latestAllSwipes = allSwipes;

                // check for new matches triggered by another member's swipe //
                if (room != null) {
                    Set<String> matches = MatchingHelper.findMatches(allSwipes, room.getMemberUids());
                    for (String movieId : matches) {

                        // notify only if not already in matchedMovies //
                        if (room.getMatchedMovies() == null
                                || !room.getMatchedMovies().contains(movieId)) {
                            String title = movieId; // fallback to ID
                            Movie cached = movieCache.get(movieId);
                            if (cached != null) title = cached.getTitle();
                            Toast.makeText(RoomActivity.this,
                                    "🎬 New match: " + title, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
            @Override
            public void onFailure(String error) {
                // non-fatal //
            }
        });
    }

    /**
     * remove Firestore listeners when the Activity is no longer visible.
     *
     * failing to call remove() here would keep the listener alive in the background,
     * resulting in delivering callbacks to a dead Activity and leaking memory.
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (roomListener  != null) roomListener.remove();
        if (swipesListener != null) swipesListener.remove();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // setup of views //
    private void findViews() {
        tabLobby = findViewById(R.id.tabLobby);
        tabSwipe = findViewById(R.id.tabSwipe);
        tabWatchlist = findViewById(R.id.tabWatchlist);
        viewFlipper = findViewById(R.id.viewFlipper);

        textRoomTitle = findViewById(R.id.textRoomTitle);
        textInviteCode = findViewById(R.id.textInviteCode);
        buttonCopyCode = findViewById(R.id.buttonCopyCode);
        buttonStartSwiping = findViewById(R.id.buttonStartSwiping);
        layoutMembers = findViewById(R.id.layoutMembers);

        movieCard = findViewById(R.id.movieCard);
        imagePoster = findViewById(R.id.imagePoster);
        textMovieTitle = findViewById(R.id.textMovieTitle);
        textMovieYear = findViewById(R.id.textMovieYear);
        textMovieRating = findViewById(R.id.textMovieRating);
        textMovieOverview = findViewById(R.id.textMovieOverview);
        textEmptySwipe = findViewById(R.id.textEmptySwipe);
        layoutSwipeButtons = findViewById(R.id.layoutSwipeButtons);
        buttonNope = findViewById(R.id.buttonNope);
        buttonLike = findViewById(R.id.buttonLike);

        recyclerWatchlist = findViewById(R.id.recyclerWatchlist);
        recyclerWatched = findViewById(R.id.recyclerWatched);
        textEmptyWatchlist = findViewById(R.id.textEmptyWatchlist);
        headerMatched = findViewById(R.id.headerMatched);
        headerWatched = findViewById(R.id.headerWatched);
        sortMatchOrder = findViewById(R.id.sortMatchOrder);
        sortRating = findViewById(R.id.sortRating);
        sortPopularity = findViewById(R.id.sortPopularity);
    }

    private void setupTabs() {
        tabLobby.setOnClickListener(v -> showSection(0));
        tabSwipe.setOnClickListener(v -> showSection(1));
        tabWatchlist.setOnClickListener(v -> showSection(2));
        showSection(0); // start on Lobby
    }

    private void setupSwipeButtons() {
        buttonCopyCode.setOnClickListener(v -> copyInviteCode());
        buttonStartSwiping.setOnClickListener(v -> onStartSwipingClicked());
        buttonNope.setOnClickListener(v -> onSwipe(false));
        buttonLike.setOnClickListener(v -> onSwipe(true));
    }

    private void setupWatchlistAdapters() {
        watchlistAdapter = new WatchlistAdapter(watchlistMovies, false);
        recyclerWatchlist.setLayoutManager(new LinearLayoutManager(this));
        recyclerWatchlist.setAdapter(watchlistAdapter);

        watchedAdapter = new WatchlistAdapter(watchedMovies, true);
        recyclerWatched.setLayoutManager(new LinearLayoutManager(this));
        recyclerWatched.setAdapter(watchedAdapter);
    }

    private void setupSortButtons() {
        sortMatchOrder.setOnClickListener(v -> applySortAndHighlight("order", sortMatchOrder));
        sortRating.setOnClickListener(v -> applySortAndHighlight("rating", sortRating));
        sortPopularity.setOnClickListener(v -> applySortAndHighlight("popularity", sortPopularity));
    }

    // switching tabs

    /**
     * Shows the requested ViewFlipper section and updates tab highlight colors.
     * index 0 = Lobby, 1 = Swipe, 2 = Watchlist
     */
    private void showSection(int index) {
        viewFlipper.setDisplayedChild(index);

        // reset all tabs to inactive style //
        for (TextView tab : new TextView[]{tabLobby, tabSwipe, tabWatchlist}) {
            tab.setTextColor(0xFF666666);
            tab.setTypeface(null, android.graphics.Typeface.NORMAL);
            tab.setBackgroundResource(R.drawable.bg_tab_inactive);
        }

        // highlight the active tab with red pill background //
        TextView active = index == 0 ? tabLobby : index == 1 ? tabSwipe : tabWatchlist;
        active.setTextColor(0xFFFFFFFF);
        active.setTypeface(null, android.graphics.Typeface.BOLD);
        active.setBackgroundResource(R.drawable.bg_tab_active);

        if (index == 2) refreshWatchlist();
    }

    // loading Room
    private void loadRoom() {
        firebaseHelper.getRoom(roomId, new FirebaseHelper.RoomCallback() {
            @Override
            public void onSuccess(Room fetchedRoom) {
                room = fetchedRoom;
                textRoomTitle.setText(room.getRoomName());
                textInviteCode.setText(room.getInviteCode());
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(room.getRoomName());
                }
                refreshLobbyMembers();

                // pre-populate the my-swipes map from our own existing swipe doc //
                firebaseHelper.getAllSwipes(roomId, new FirebaseHelper.AllSwipesCallback() {
                    @Override
                    public void onSuccess(Map<String, Map<String, Boolean>> allSwipes) {
                        latestAllSwipes = allSwipes;
                        Map<String, Boolean> mine = allSwipes.get(currentUid);
                        if (mine != null) mySwipes.putAll(mine);
                    }
                    @Override public void onFailure(String error) {}
                });
            }
            @Override
            public void onFailure(String error) {
                Toast.makeText(RoomActivity.this,
                        "Couldn't load room: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Lobby section //
    private void copyInviteCode() {
        String code = textInviteCode.getText().toString();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("FlickMatch invite code", code));
        Toast.makeText(this, "Invite code copied!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Whenever the room data changes, the member list is refreshed from scratch. Each
     * member's display name is fetched from Firestore in the background as it goes.
     */
    private void refreshLobbyMembers() {
        if (room == null) return;
        layoutMembers.removeAllViews();

        List<String> memberUids = room.getMemberUids();
        if (memberUids == null) return;

        for (String uid : memberUids) {

            // inflate a row for this member (built programmatically) //
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 8, 0, 8);

            TextView nameView = new TextView(this);
            nameView.setTextSize(15f);
            nameView.setTextColor(0xFF1A1A1A);
            nameView.setText("Loading…");
            row.addView(nameView);

            if (uid.equals(room.getCreatorUid())) {
                TextView badge = new TextView(this);
                badge.setText("  (creator)");
                badge.setTextSize(12f);
                badge.setTextColor(0xFF888888);
                row.addView(badge);
            }

            layoutMembers.addView(row);

            // async name fetch - update the TextView once we have the name //
            firebaseHelper.getDisplayName(uid, new FirebaseHelper.AuthCallback() {
                @Override
                public void onSuccess(String displayName) {
                    nameView.setText(displayName);
                }
                @Override
                public void onFailure(String error) {
                    nameView.setText("Unknown member");
                }
            });
        }
    }

    // initialising queue to start swiping //
    private void onStartSwipingClicked() {
        if (room == null) return;
        buttonStartSwiping.setEnabled(false);

        // use the room's saved movie queue if exists (in Firestore) //
        List<String> existingQueue = room.getMovieQueue();
        if (existingQueue != null && !existingQueue.isEmpty()) {
            loadMovieDetailsForIds(existingQueue);
            showSection(1);
            return;
        }

        // else, fetch the first page from TMDB //
        currentPage = 1;
        tmdbHelper.fetchMoviesForRoom(room, currentPage, new TMDBHelper.MovieListCallback() {
            @Override
            public void onSuccess(List<Movie> movies, int pages) {
                totalPages = pages;
                addMoviesToQueue(movies);

                // continue the movie IDs to Firestore so other members see the same queue //
                List<String> ids = new ArrayList<>();
                for (Movie m : movies) ids.add(m.getId());
                firebaseHelper.updateMovieQueue(roomId, ids, new FirebaseHelper.SimpleCallback() {
                    @Override public void onSuccess() {}
                    @Override public void onFailure(String error) {
                        // non-fatal as swiping can continue without persistence
                    }
                });

                showSection(1);
                showNextCard();
                buttonStartSwiping.setEnabled(true);
            }
            @Override
            public void onFailure(String error) {
                Toast.makeText(RoomActivity.this,
                        "Couldn't fetch movies: " + error, Toast.LENGTH_SHORT).show();
                buttonStartSwiping.setEnabled(true);
            }
        });
    }

    /**
     * fetches full Movie details for a list of IDs (e.g. from an existing queue),
     * then populates the local movie queue.
     */
    private void loadMovieDetailsForIds(List<String> ids) {
        for (String id : ids) {

            // DSA: HashMap O(1) cache check - skip API call if we already have the movie //
            if (movieCache.containsKey(id)) {
                movieQueue.offer(movieCache.get(id)); // O(1) enqueue
                continue;
            }

            int intId;
            try { intId = Integer.parseInt(id); }
            catch (NumberFormatException e) { continue; }

            tmdbHelper.fetchMovieDetails(intId, new TMDBHelper.DetailCallback() {
                @Override
                public void onSuccess(Movie movie) {
                    movieCache.put(movie.getId(), movie); // cache for later
                    if (!mySwipes.containsKey(movie.getId())) {
                        movieQueue.offer(movie); // O(1) - LinkedList add to back
                    }
                    if (movieQueue.size() == 1) showNextCard(); // show first card
                }
                @Override
                public void onFailure(String error) {
                    // skip this movie
                }
            });
        }
        showNextCard();
    }

    // Swiping section //

    /**
     * handles a swipe action (like or nope).
     *
     * Steps:
     * 1. Read the front of the queue (O(1) peek).
     * 2. Persist the swipe to Firestore.
     * 3. Check for a match using MatchingHelper.
     * 4. Poll the card off the front (O(1)) and show the next one.
     * 5. If the queue is running low, prefetch the next TMDB page.
     */
    private void onSwipe(boolean liked) {

        // DSA: Queue.peek() is O(1) - look at front without removing //
        Movie current = movieQueue.peek();
        if (current == null) return;

        buttonLike.setEnabled(false);
        buttonNope.setEnabled(false);

        // DSA: HashMap O(1) write - record this swipe locally //
        mySwipes.put(current.getId(), liked);

        firebaseHelper.saveSwipe(roomId, currentUid, current.getId(), liked,
                new FirebaseHelper.SimpleCallback() {
            @Override
            public void onSuccess() {

                // DSA: Queue.poll() is O(1) - remove from front of LinkedList //
                movieQueue.poll();

                if (liked) checkForMatch(current.getId());

                showNextCard();
                buttonLike.setEnabled(true);
                buttonNope.setEnabled(true);

                // prefetch next page when queue drops below threshold //
                if (movieQueue.size() < Constants.QUEUE_PREFETCH_THRESHOLD) {
                    fetchNextPageIfAvailable();
                }
            }
            @Override
            public void onFailure(String error) {
                Toast.makeText(RoomActivity.this,
                        "Swipe failed: " + error, Toast.LENGTH_SHORT).show();

                // remove swipe from local map since it wasn't persisted //
                mySwipes.remove(current.getId());
                buttonLike.setEnabled(true);
                buttonNope.setEnabled(true);
            }
        });
    }

    // renders the card at the front of movieQueue, or shows the empty state //
    private void showNextCard() {

        // DSA: Queue.peek() - O(1) look without removing //
        Movie next = movieQueue.peek();

        if (next == null) {
            movieCard.setVisibility(View.GONE);
            layoutSwipeButtons.setVisibility(View.GONE);
            textEmptySwipe.setVisibility(View.VISIBLE);
            return;
        }

        textEmptySwipe.setVisibility(View.GONE);
        movieCard.setVisibility(View.VISIBLE);
        layoutSwipeButtons.setVisibility(View.VISIBLE);

        textMovieTitle.setText(next.getTitle());
        textMovieRating.setText("★ " + String.format("%.1f", next.getVoteAverage()));
        textMovieOverview.setText(next.getOverview());

        // extract year from "YYYY-MM-DD" release date //
        String releaseDate = next.getReleaseDate();
        if (releaseDate != null && releaseDate.length() >= 4) {
            textMovieYear.setText(releaseDate.substring(0, 4));
        } else {
            textMovieYear.setText("");
        }

        com.bumptech.glide.Glide.with(this)
                .load(next.getFullPosterUrl())
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_dialog_alert)
                .centerCrop()
                .into(imagePoster);
    }

    /**
     * checks whether the just-swiped movie is now a full-room match by running
     * MatchingHelper against the latest allSwipes snapshot.
     */
    private void checkForMatch(String movieId) {
        if (room == null) return;
        Set<String> matches = MatchingHelper.findMatches(latestAllSwipes, room.getMemberUids());
        if (matches.contains(movieId)) {
            String title = movieId;
            Movie cached = movieCache.get(movieId);
            if (cached != null) title = cached.getTitle();

            final String movieTitle = title;
            firebaseHelper.addMatchedMovie(roomId, movieId, new FirebaseHelper.SimpleCallback() {
                @Override
                public void onSuccess() {
                    showMatchDialog(movieTitle);
                }
                @Override
                public void onFailure(String error) {
                    // Non-fatal - match will be detected again by the listener
                }
            });
        }
    }

    private void showMatchDialog(String movieTitle) {
        new AlertDialog.Builder(this)
                .setTitle("🎬 It's a Match!")
                .setMessage("Everyone liked \"" + movieTitle + "\"!\nCheck the Watchlist.")
                .setPositiveButton("View Watchlist", (d, w) -> showSection(2))
                .setNegativeButton("Keep Swiping", null)
                .show();
    }

    /**
     * Adds new Movie objects to the back of the queue, skipping any which the user
     * has already swiped on.
     *
     * DSA: offer() on LinkedList takes O(1) time - appends to tail without shifting.
     * HashMap.containsKey() for mySwipes takes O(1) time.
     */
    private void addMoviesToQueue(List<Movie> movies) {
        for (Movie m : movies) {
            movieCache.put(m.getId(), m); // cache for Watchlist

            // DSA: O(1) HashMap lookup - skip already-swiped movies //
            if (!mySwipes.containsKey(m.getId())) {
                movieQueue.offer(m); // O(1) LinkedList append
            }
        }
    }

    private void fetchNextPageIfAvailable() {
        if (room == null || currentPage >= totalPages) return;
        currentPage++;
        tmdbHelper.fetchMoviesForRoom(room, currentPage, new TMDBHelper.MovieListCallback() {
            @Override
            public void onSuccess(List<Movie> movies, int pages) {
                totalPages = pages;
                addMoviesToQueue(movies);

                // if the card area was showing "empty", reveal the new cards //
                if (movieQueue.peek() != null) showNextCard();
            }
            @Override
            public void onFailure(String error) {
                // Silently ignore prefetch failures; the user can keep swiping
                // whatever's left in the local queue
            }
        });
    }

    // Watchlist section //

    /**
     * syncs in-memory watchlist and watched lists from the latest room state,
     * then notifies the adapters.
     */
    private void refreshWatchlist() {
        if (room == null) return;

        watchlistMovies.clear();
        watchedMovies.clear();

        List<String> matched = room.getMatchedMovies();
        List<String> watched = room.getWatchedMovies();

        if (matched != null) {
            for (String id : matched) {
                Movie m = movieCache.get(id);
                if (m != null) watchlistMovies.add(m);
                // If not in cache, a background fetch could populate it,
                // but for the demo the movie will have been swiped first.
            }
        }

        if (watched != null) {
            for (String id : watched) {
                Movie m = movieCache.get(id);
                if (m != null) watchedMovies.add(m);
            }
        }

        // re-apply the current sort before notifying //
        applySortList(watchlistMovies, currentSort);

        boolean hasMatches = !watchlistMovies.isEmpty();
        boolean hasWatched = !watchedMovies.isEmpty();

        textEmptyWatchlist.setVisibility(hasMatches || hasWatched ? View.GONE : View.VISIBLE);
        headerMatched.setVisibility(hasMatches ? View.VISIBLE : View.GONE);
        headerWatched.setVisibility(hasWatched ? View.VISIBLE : View.GONE);

        watchlistAdapter.notifyDataSetChanged();
        watchedAdapter.notifyDataSetChanged();
    }

    /**
     * Sorts the provided list in-place using a Comparator selected at runtime.
     *
     * The watchlist can be sorted in different ways by simply swapping out the sorting
     * strategy at runtime; the rest of the code doesn't change at all. Whichever sort
     * the user picks (by title, rating, etc.), we just pass a different Comparator to
     * Collections.sort(). This is known as the Strategy pattern, and it's an example
     * of polymorphism.
     *
     * movies - the list to sort in-place
     * sortBy - "rating" | "popularity" | anything else = keep order
     */
    private void applySortList(List<Movie> movies, String sortBy) {
        switch (sortBy) {
            case "rating":
                // Comparator: sort descending by voteAverage (double comparison)
                Collections.sort(movies, (m1, m2) ->
                        Double.compare(m2.getVoteAverage(), m1.getVoteAverage()));
                break;
            case "popularity":
                // Comparator: sort descending by popularity score
                Collections.sort(movies, (m1, m2) ->
                        Double.compare(m2.getPopularity(), m1.getPopularity()));
                break;
            default:
                // "order" - preserve the original match order, no-op
                break;
        }
    }

    private void applySortAndHighlight(String sortBy, TextView selectedButton) {
        currentSort = sortBy;
        applySortList(watchlistMovies, sortBy);
        watchlistAdapter.notifyDataSetChanged();

        // update button highlight - active = red fill, inactive = grey
        for (TextView btn : new TextView[]{sortMatchOrder, sortRating, sortPopularity}) {
            btn.setBackgroundResource(R.drawable.bg_sort_inactive);
            btn.setTextColor(0xFF777777);
        }
        selectedButton.setBackgroundResource(R.drawable.bg_sort_active);
        selectedButton.setTextColor(0xFFFFFFFF);
    }

    // WatchlistAdapter inner class //

    /**
     * Adapter shared by both the "Matches" RecyclerView and the "Watched" RecyclerView.
     *
     * isWatched controls whether the "Mark Watched" button is shown:
     * 1. false - show the button (matched but not yet watched)
     * 2. true - hide the button (already watched)
     */
    private class WatchlistAdapter extends RecyclerView.Adapter<WatchlistAdapter.VH> {
        private final List<Movie> movies;
        private final boolean isWatched;

        WatchlistAdapter(List<Movie> movies, boolean isWatched) {
            this.movies    = movies;
            this.isWatched = isWatched;
        }

        @Override
        public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_watchlist, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            Movie movie = movies.get(position);

            holder.title.setText(movie.getTitle());
            holder.rating.setText("★ " + String.format("%.1f", movie.getVoteAverage()));

            String releaseDate = movie.getReleaseDate();
            holder.year.setText(releaseDate != null && releaseDate.length() >= 4
                    ? releaseDate.substring(0, 4) : "");

            if (isWatched) {
                holder.buttonWatched.setVisibility(View.GONE);
            } else {
                holder.buttonWatched.setVisibility(View.VISIBLE);
                holder.buttonWatched.setOnClickListener(v -> {
                    firebaseHelper.addWatchedMovie(roomId, movie.getId(),
                            new FirebaseHelper.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            // room listener will fire and call refreshWatchlist() //
                        }
                        @Override
                        public void onFailure(String error) {
                            Toast.makeText(RoomActivity.this,
                                    "Couldn't mark as watched: " + error,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
        }

        @Override
        public int getItemCount() { return movies.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView title, year, rating;
            Button   buttonWatched;

            VH(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.textWatchlistTitle);
                year = itemView.findViewById(R.id.textWatchlistYear);
                rating = itemView.findViewById(R.id.textWatchlistRating);
                buttonWatched = itemView.findViewById(R.id.buttonMarkWatched);
            }
        }
    }
}