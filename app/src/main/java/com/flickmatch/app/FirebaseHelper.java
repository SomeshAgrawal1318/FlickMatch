package com.flickmatch.app;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Single entry point for all Firebase operations in FlickMatch.
 *
 * Activities never talk to Firebase directly. Instead, they just pass in data and wait for
 * a result through callbacks. All the Firestore paths, queries, and authentication logic
 * live here instead of being scattered across the app (aka Singleton).
 *
 * Only one instance of this class is ever created, on the first time it's needed, and that
 * same instance is reused throughout the app's lifetime. Spinning up Firebase objects more
 * than once is unnecessary and costly, so we avoid it.
 */
public class FirebaseHelper {

    // Singleton //
    private static FirebaseHelper instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    // Private constructor where we use .getInstance() instead //
    private FirebaseHelper() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Returns the shared singleton instance, creating it on first call.
     * Safe to call from any thread after the Firebase SDK has been initialized
     * (which happens automatically when the Application class starts).
     */
    public static synchronized FirebaseHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseHelper();
        }
        return instance;
    }

    // Callback interfaces //

    /**
     * Generic string-result callback. Used for Auth operations that return a UID,
     * and for any single-String read (e.g. displayName).
     */
    public interface AuthCallback {
        void onSuccess(String uid);
        void onFailure(String error);
    }

    // returns a single Room document //
    public interface RoomCallback {
        void onSuccess(Room room);
        void onFailure(String error);
    }

    // returns a list of Room documents //
    public interface RoomsListCallback {
        void onSuccess(List<Room> rooms);
        void onFailure(String error);
    }

    // used for write operations where no return value is needed //
    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String error);
    }

    // returns a single member's swipe map: movieId -> liked. //
    public interface SwipeMapCallback {
        void onSuccess(Map<String, Boolean> swipeMap);
        void onFailure(String error);
    }

    /**
     * returns swipe data for every member in the room.
     * outer key = memberUid, inner map = movieId -> liked.
     */
    public interface AllSwipesCallback {
        void onSuccess(Map<String, Map<String, Boolean>> allSwipes);
        void onFailure(String error);
    }

    // Authentication

    /**
     * signs the user in anonymously via Firebase Auth.
     *
     * if a user is already signed in this session, the existing UID is returned
     * immediately without a network call. this avoids creating duplicate anonymous
     * accounts on every app launch.
     *
     * params:
     * callback - receives the UID on success, or an error message on failure
     */
    public void signInAnonymously(AuthCallback callback) {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {

            // already authenticated - return the existing UID immediately //
            callback.onSuccess(current.getUid());
            return;
        }

        auth.signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = authResult.getUser();
                    if (user != null) {
                        callback.onSuccess(user.getUid());
                    } else {
                        callback.onFailure("Sign-in succeeded but user is null");
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // returns the current user's UID, or null if no user is signed in //
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    // Returns true if a user is currently signed in. //
    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    // User operations //

    /**
     * creates or updates the user document at /users/{uid}
     *
     * uses merge mode when saving, so any existing fields (e.g. roomIds) aren't wiped out.
     * createdAt is only written the first time; on subsequent saves it's simply left out,
     * so Firestore won't overwrite it.
     *
     * params:
     * uid - the user's Firebase UID
     * displayName - the chosen display name
     * callback - fires onSuccess when written, onFailure on error
     */
    public void saveDisplayName(String uid, String displayName, SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("displayName", displayName);
        data.put("createdAt", FieldValue.serverTimestamp());

        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * reads the displayName field from /users/{uid}.
     *
     * params:
     * uid - the user's Firebase UID
     * callback - receives the displayName string on success
     */
    public void getDisplayName(String uid, AuthCallback callback) {
        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onFailure("User document not found for uid: " + uid);
                        return;
                    }
                    String name = doc.getString("displayName");
                    if (name == null) {
                        callback.onFailure("displayName field is missing");
                        return;
                    }
                    callback.onSuccess(name);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // Room operations //

    /**
     * creates a new room document in Firestore in the following steps:
     *
     * 1. Generates a unique 6-character alphanumeric invite code (up to 5 attempts
     *    to avoid collisions)
     * 2. Writes the room document using Room.toMap()
     * 3. Adds the new roomId to the creator's roomIds list
     * 4. Returns the fully populated Room (with roomId set) via callback
     *
     * params:
     * room - a Room built via Room.Builder, without an invite code (the code is generated here)
     * callback - receives the saved Room on success
     */
    public void createRoom(Room room, RoomCallback callback) {
        generateUniqueInviteCode(0, code -> {
            if (code == null) {
                callback.onFailure("Failed to generate a unique invite code after 5 attempts");
                return;
            }

            room.setInviteCode(code);

            // Let Firestore auto-generate the document ID.
            DocumentReference ref = db.collection(Constants.COLLECTION_ROOMS).document();
            String roomId = ref.getId();
            room.setRoomId(roomId);

            ref.set(room.toMap())
                    .addOnSuccessListener(unused -> {
                        // Add roomId to the creator's roomIds list.
                        db.collection(Constants.COLLECTION_USERS)
                                .document(room.getCreatorUid())
                                .update("roomIds", FieldValue.arrayUnion(roomId))
                                .addOnSuccessListener(u -> callback.onSuccess(room))
                                .addOnFailureListener(e -> callback.onFailure(
                                        "Room created but failed to update user: " + e.getMessage()));
                    })
                    .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
        });
    }

    /**
     * joins a room by invite code.
     *
     * this queries for a room with the given invite code, checks capacity, adds the user's
     * UID to memberUids (arrayUnion is idempotent - safe if the user somehow calls join twice),
     * and adds the roomId to the user's roomIds.
     *
     * params:
     * inviteCode - 6-character code displayed to the room creator
     * uid - the joining user's UID
     * callback - receives the joined Room on success
     */
    public void joinRoom(String inviteCode, String uid, RoomCallback callback) {
        db.collection(Constants.COLLECTION_ROOMS)
                .whereEqualTo("inviteCode", inviteCode.toUpperCase())
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        callback.onFailure("No room found with invite code: " + inviteCode);
                        return;
                    }

                    DocumentSnapshot doc = query.getDocuments().get(0);
                    List<?> members = (List<?>) doc.get("memberUids");
                    int memberCount = members != null ? members.size() : 0;

                    if (memberCount >= Constants.MAX_ROOM_MEMBERS) {
                        callback.onFailure("Room is full (max " + Constants.MAX_ROOM_MEMBERS + " members)");
                        return;
                    }

                    String roomId = doc.getId();

                    // add uid to room's memberUids and to user's roomIds atomically //
                    db.collection(Constants.COLLECTION_ROOMS)
                            .document(roomId)
                            .update("memberUids", FieldValue.arrayUnion(uid))
                            .addOnSuccessListener(unused ->
                                    db.collection(Constants.COLLECTION_USERS)
                                            .document(uid)
                                            .update("roomIds", FieldValue.arrayUnion(roomId))
                                            .addOnSuccessListener(u -> {
                                                // re-fetch the room so the callback gets fresh data //
                                                getRoom(roomId, callback);
                                            })
                                            .addOnFailureListener(e -> callback.onFailure(
                                                    "Joined room but failed to update user: " + e.getMessage())))
                            .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * fetches a single room document by its Firestore document ID.
     *
     * params:
     * roomId - Firestore document ID (not the invite code)
     * callback - receives the Room on success
     */
    public void getRoom(String roomId, RoomCallback callback) {
        db.collection(Constants.COLLECTION_ROOMS)
                .document(roomId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onFailure("Room not found: " + roomId);
                        return;
                    }
                    Room room = documentToRoom(doc);
                    callback.onSuccess(room);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * fetches all rooms the user belongs to.
     *
     * this reads roomIds from /users/{uid}, then fetches each room document. the results
     * are returned once all fetches complete (fan-out approach using a simple counter to
     * detect completion).
     *
     * params:
     * uid - the user's Firebase UID
     * callback - receives the list of Room objects on success
     */
    public void getUserRooms(String uid, RoomsListCallback callback) {
        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (!userDoc.exists()) {
                        callback.onFailure("User document not found: " + uid);
                        return;
                    }

                    List<String> roomIds = (List<String>) userDoc.get("roomIds");
                    if (roomIds == null || roomIds.isEmpty()) {
                        callback.onSuccess(new ArrayList<>());
                        return;
                    }

                    List<Room> rooms = new ArrayList<>();
                    int[] remaining = {roomIds.size()}; // single-element array for lambda mutation

                    for (String roomId : roomIds) {
                        db.collection(Constants.COLLECTION_ROOMS)
                                .document(roomId)
                                .get()
                                .addOnSuccessListener(doc -> {
                                    if (doc.exists()) {
                                        rooms.add(documentToRoom(doc));
                                    }
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        callback.onSuccess(rooms);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    remaining[0]--;
                                    if (remaining[0] == 0) {
                                        // return whatever we managed to fetch.
                                        callback.onSuccess(rooms);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * attaches a real-time listener to a room document.
     *
     * the callback fires immediately with the current state, and again every time the
     * document changes. The caller should store the returned ListenerRegistration and
     * call .remove() in Activity.onStop() to avoid memory leaks.
     *
     * params:
     * roomId - Firestore document ID
     * callback - receives the updated Room on every change
     * this returns a registration handle - call .remove() when done
     */
    public ListenerRegistration listenToRoom(String roomId, RoomCallback callback) {
        return db.collection(Constants.COLLECTION_ROOMS)
                .document(roomId)
                .addSnapshotListener((doc, error) -> {
                    if (error != null) {
                        callback.onFailure(error.getMessage());
                        return;
                    }
                    if (doc == null || !doc.exists()) {
                        callback.onFailure("Room document does not exist: " + roomId);
                        return;
                    }
                    callback.onSuccess(documentToRoom(doc));
                });
    }

    // Swipe operations

    /**
     * records a single swipe for a member.
     *
     * this uses SetOptions.merge() so previous swipes in the same document are preserved -
     * only the new movieId key is added or overwritten.
     *
     * params:
     * roomId - the room this swipe belongs to
     * uid - the swiping member's UID
     * movieId - TMDB movie ID (stored as String throughout the app)
     * liked - {@code true} = swiped right, {@code false} = swiped left
     * callback - fires onSuccess when written
     */
    public void saveSwipe(String roomId, String uid, String movieId,
                          boolean liked, SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        Map<String, Boolean> swipeEntry = new HashMap<>();
        swipeEntry.put(movieId, liked);
        data.put("swipeMap", swipeEntry);

        db.collection(Constants.COLLECTION_ROOMS)
                .document(roomId)
                .collection(Constants.COLLECTION_SWIPES)
                .document(uid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * reads the swipe document for a single member.
     *
     * params:
     * roomId - the room document ID
     * uid - the member whose swipes to fetch
     * callback - receives the swipeMap (movieId -> liked)
     */
    public void getSwipesForMember(String roomId, String uid, SwipeMapCallback callback) {
        db.collection(Constants.COLLECTION_ROOMS)
                .document(roomId)
                .collection(Constants.COLLECTION_SWIPES)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        // no swipes yet is a valid state, not an error //
                        callback.onSuccess(new HashMap<>());
                        return;
                    }
                    Map<String, Boolean> swipeMap = extractSwipeMap(doc);
                    callback.onSuccess(swipeMap);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * reads swipe documents for all members in a room.
     *
     * params:
     * roomId - the room document ID
     * callback - receives a map of memberUid -> swipeMap
     */
    public void getAllSwipes(String roomId, AllSwipesCallback callback) {
        db.collection(Constants.COLLECTION_ROOMS)
                .document(roomId)
                .collection(Constants.COLLECTION_SWIPES)
                .get()
                .addOnSuccessListener(query -> {
                    Map<String, Map<String, Boolean>> allSwipes = new HashMap<>();
                    for (QueryDocumentSnapshot doc : query) {
                        allSwipes.put(doc.getId(), extractSwipeMap(doc));
                    }
                    callback.onSuccess(allSwipes);
                })
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * attaches a real-time listener to the entire swipes subcollection.
     *
     * this fires whenever any member's swipe document changes. The caller must store
     * and .remove() the returned registration in Activity.onStop()
     *
     * params:
     * roomId - the room document ID
     * callback - receives the full memberUid -> swipeMap on each change
     * this returns a registration handle - call .remove() when done
     */
    public ListenerRegistration listenToSwipes(String roomId, AllSwipesCallback callback) {
        return db.collection(Constants.COLLECTION_ROOMS)
                .document(roomId)
                .collection(Constants.COLLECTION_SWIPES)
                .addSnapshotListener((query, error) -> {
                    if (error != null) {
                        callback.onFailure(error.getMessage());
                        return;
                    }
                    if (query == null) {
                        callback.onSuccess(new HashMap<>());
                        return;
                    }
                    Map<String, Map<String, Boolean>> allSwipes = new HashMap<>();
                    for (QueryDocumentSnapshot doc : query) {
                        allSwipes.put(doc.getId(), extractSwipeMap(doc));
                    }
                    callback.onSuccess(allSwipes);
                });
    }

    // Room update operations //

    /**
     * replaces the movieQueue field on a room document.
     *
     * this is called by the queue manager when a new page of movies is fetched from TMDB
     * and needs to be shared with all room members
     *
     * params:
     * roomId - the room document ID
     * movieIds - ordered list of TMDB movie IDs (as Strings)
     * callback - fires onSuccess when written
     */
    public void updateMovieQueue(String roomId, List<String> movieIds, SimpleCallback callback) {
        db.collection(Constants.COLLECTION_ROOMS)
                .document(roomId)
                .update("movieQueue", movieIds)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * adds a movie to the room's matchedMovies list.
     *
     * FieldValue.arrayUnion ensures no duplicates even if called for the same movie twice
     * (e.g. a race condition between two members' last swipes)
     *
     * params:
     * roomId - the room document ID
     * movieId - TMDB movie ID (String)
     * callback - fires onSuccess when written
     */
    public void addMatchedMovie(String roomId, String movieId, SimpleCallback callback) {
        db.collection(Constants.COLLECTION_ROOMS)
                .document(roomId)
                .update("matchedMovies", FieldValue.arrayUnion(movieId))
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * moves a movie from matchedMovies to watchedMovies atomically.
     *
     * a WriteBatch ensures both the remove and the add happen together. if either step fails,
     * Firestore rolls back both - the movie cannot end up missing from both lists or present
     * in both lists simultaneously.
     *
     * params:
     * roomId - the room document ID
     * movieId - TMDB movie ID (String)
     * callback - fires onSuccess when the batch commits
     */
    public void addWatchedMovie(String roomId, String movieId, SimpleCallback callback) {
        DocumentReference roomRef = db.collection(Constants.COLLECTION_ROOMS).document(roomId);

        WriteBatch batch = db.batch();
        batch.update(roomRef, "matchedMovies", FieldValue.arrayRemove(movieId));
        batch.update(roomRef, "watchedMovies", FieldValue.arrayUnion(movieId));

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    // Private helpers //

    /**
     * recursively generates a unique invite code, retrying up to 5 times if the generated
     * code already exists in Firestore.
     *
     * params:
     * attempt - current attempt index (0-based); stops at 5
     * listener - receives the unique code, or null after 5 failures
     */
    private void generateUniqueInviteCode(int attempt, InviteCodeListener listener) {
        if (attempt >= 5) {
            listener.onResult(null);
            return;
        }

        String code = randomCode(Constants.INVITE_CODE_LENGTH);

        db.collection(Constants.COLLECTION_ROOMS)
                .whereEqualTo("inviteCode", code)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        listener.onResult(code); // no collision; use this code
                    } else {
                        generateUniqueInviteCode(attempt + 1, listener); // try again
                    }
                })
                .addOnFailureListener(e -> {
                    // on query failure, fall back to using the code anyway rather than
                    // blocking room creation entirely. Collisions are astronomically rare
                    listener.onResult(code);
                });
    }

    // generates a random uppercase alphanumeric string of the given length. //
    private static String randomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // internal callback used only by .generateUniqueInviteCode //
    private interface InviteCodeListener {
        void onResult(String code);
    }

    /**
     * converts a Firestore DocumentSnapshot to a Room object.
     *
     * This reads each field by name and populates the Room via setters. The roomId is set
     * from the document ID. Numeric fields arrive as Long from Firestore and are cast to int.
     */
    private Room documentToRoom(DocumentSnapshot doc) {
        Room room = new Room();
        room.setRoomId(doc.getId());
        room.setRoomName(doc.getString("roomName"));
        room.setInviteCode(doc.getString("inviteCode"));
        room.setCreatorUid(doc.getString("creatorUid"));

        List<String> memberUids = (List<String>) doc.get("memberUids");
        if (memberUids != null) room.setMemberUids(memberUids);

        List<String> genres = (List<String>) doc.get("genres");
        if (genres != null) room.setGenres(genres);

        Long maxRuntime = doc.getLong("maxRuntime");
        if (maxRuntime != null) room.setMaxRuntime(maxRuntime.intValue());

        List<String> ageRatings = (List<String>) doc.get("ageRatings");
        if (ageRatings != null) room.setAgeRatings(ageRatings);

        Long yearMin = doc.getLong("yearMin");
        if (yearMin != null) room.setYearMin(yearMin.intValue());

        Long yearMax = doc.getLong("yearMax");
        if (yearMax != null) room.setYearMax(yearMax.intValue());

        List<String> movieQueue = (List<String>) doc.get("movieQueue");
        if (movieQueue != null) room.setMovieQueue(movieQueue);

        List<String> matchedMovies = (List<String>) doc.get("matchedMovies");
        if (matchedMovies != null) room.setMatchedMovies(matchedMovies);

        List<String> watchedMovies = (List<String>) doc.get("watchedMovies");
        if (watchedMovies != null) room.setWatchedMovies(watchedMovies);

        room.setCreatedAt(doc.getTimestamp("createdAt"));

        return room;
    }

    /**
     * extracts the swipeMap field from a swipe document. Firestore stores booleans natively,
     * so the cast is safe. this returns an empty map if the field is absent.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Boolean> extractSwipeMap(DocumentSnapshot doc) {
        Map<String, Boolean> swipeMap = (Map<String, Boolean>) doc.get("swipeMap");
        return swipeMap != null ? swipeMap : new HashMap<>();
    }
}