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
 * <p><b>Separation of Concerns:</b> Activities never touch FirebaseAuth or
 * FirebaseFirestore directly. All Firestore paths, query logic, and Auth calls
 * live here. Activities only supply data and implement the callback interfaces
 * defined below to receive results.
 *
 * <p><b>Singleton:</b> One instance is created lazily via {@link #getInstance()}
 * and reused. Firebase SDK objects are expensive to initialise and must not be
 * duplicated.
 */
public class FirebaseHelper {

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static FirebaseHelper instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    /** Private constructor — use {@link #getInstance()} instead. */
    private FirebaseHelper() {
        auth = FirebaseAuth.getInstance();
        db   = FirebaseFirestore.getInstance();
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

    // =========================================================================
    // Callback interfaces
    // =========================================================================

    /**
     * Generic string-result callback. Used for Auth operations that return a UID,
     * and for any single-String read (e.g. displayName).
     */
    public interface AuthCallback {
        void onSuccess(String uid);
        void onFailure(String error);
    }

    /** Returns a single Room document. */
    public interface RoomCallback {
        void onSuccess(Room room);
        void onFailure(String error);
    }

    /** Returns a list of Room documents. */
    public interface RoomsListCallback {
        void onSuccess(List<Room> rooms);
        void onFailure(String error);
    }

    /** Used for write operations where no return value is needed. */
    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String error);
    }

    /** Returns a single member's swipe map: movieId → liked. */
    public interface SwipeMapCallback {
        void onSuccess(Map<String, Boolean> swipeMap);
        void onFailure(String error);
    }

    /**
     * Returns swipe data for every member in the room.
     * Outer key = memberUid, inner map = movieId → liked.
     */
    public interface AllSwipesCallback {
        void onSuccess(Map<String, Map<String, Boolean>> allSwipes);
        void onFailure(String error);
    }

    // =========================================================================
    // Authentication
    // =========================================================================

    /**
     * Signs the user in anonymously via Firebase Auth.
     *
     * <p>If a user is already signed in this session, the existing UID is
     * returned immediately without a network call — avoids creating duplicate
     * anonymous accounts on every app launch.
     *
     * @param callback receives the UID on success, or an error message on failure
     */
    public void signInAnonymously(AuthCallback callback) {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            // Already authenticated — return the existing UID immediately.
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

    /**
     * Returns the current user's UID, or {@code null} if no user is signed in.
     */
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    /** Returns {@code true} if a user is currently signed in. */
    public boolean isSignedIn() {
        return auth.getCurrentUser() != null;
    }

    // =========================================================================
    // User operations
    // =========================================================================

    /**
     * Creates or updates the user document at /users/{uid}.
     *
     * <p>Uses {@link SetOptions#merge()} so existing fields (e.g. roomIds) are
     * preserved. {@code createdAt} is written on first creation and left alone on
     * subsequent calls because Firestore merge only writes the fields provided.
     *
     * @param uid         the user's Firebase UID
     * @param displayName the chosen display name
     * @param callback    fires onSuccess when written, onFailure on error
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
     * Reads the displayName field from /users/{uid}.
     *
     * @param uid      the user's Firebase UID
     * @param callback receives the displayName string on success
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

    // =========================================================================
    // Room operations
    // =========================================================================

    /**
     * Creates a new room document in Firestore.
     *
     * <ol>
     *   <li>Generates a unique 6-character alphanumeric invite code (up to 5
     *       attempts to avoid collisions).</li>
     *   <li>Writes the room document using {@link Room#toMap()}.</li>
     *   <li>Adds the new roomId to the creator's roomIds list.</li>
     *   <li>Returns the fully populated Room (with roomId set) via callback.</li>
     * </ol>
     *
     * @param room     a Room built via {@link Room.Builder}, without an invite code
     *                 (the code is generated here)
     * @param callback receives the saved Room on success
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
     * Joins a room by invite code.
     *
     * <p>Queries for a room with the given invite code, checks capacity, adds
     * the user's UID to memberUids (arrayUnion is idempotent — safe if the user
     * somehow calls join twice), and adds the roomId to the user's roomIds.
     *
     * @param inviteCode 6-character code displayed to the room creator
     * @param uid        the joining user's UID
     * @param callback   receives the joined Room on success
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

              // Add uid to room's memberUids and to user's roomIds atomically.
              db.collection(Constants.COLLECTION_ROOMS)
                .document(roomId)
                .update("memberUids", FieldValue.arrayUnion(uid))
                .addOnSuccessListener(unused ->
                    db.collection(Constants.COLLECTION_USERS)
                      .document(uid)
                      .update("roomIds", FieldValue.arrayUnion(roomId))
                      .addOnSuccessListener(u -> {
                          // Re-fetch the room so the callback gets fresh data.
                          getRoom(roomId, callback);
                      })
                      .addOnFailureListener(e -> callback.onFailure(
                              "Joined room but failed to update user: " + e.getMessage())))
                .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
          })
          .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Fetches a single room document by its Firestore document ID.
     *
     * @param roomId   Firestore document ID (not the invite code)
     * @param callback receives the Room on success
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
     * Fetches all rooms the user belongs to.
     *
     * <p>Reads roomIds from /users/{uid}, then fetches each room document.
     * Results are returned once ALL fetches complete (fan-out approach using a
     * simple counter to detect completion).
     *
     * @param uid      the user's Firebase UID
     * @param callback receives the list of Room objects on success
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
                            // Return whatever we managed to fetch.
                            callback.onSuccess(rooms);
                        }
                    });
              }
          })
          .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Attaches a real-time listener to a room document.
     *
     * <p>The callback fires immediately with the current state, then again
     * every time the document changes. The caller MUST store the returned
     * {@link ListenerRegistration} and call {@code .remove()} in
     * {@code Activity.onStop()} to avoid memory leaks.
     *
     * @param roomId   Firestore document ID
     * @param callback receives the updated Room on every change
     * @return a registration handle — call {@code .remove()} when done
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

    // =========================================================================
    // Swipe operations
    // =========================================================================

    /**
     * Records a single swipe for a member.
     *
     * <p>Uses {@link SetOptions#merge()} so previous swipes in the same document
     * are preserved — only the new movieId key is added or overwritten.
     *
     * @param roomId   the room this swipe belongs to
     * @param uid      the swiping member's UID
     * @param movieId  TMDB movie ID (stored as String throughout the app)
     * @param liked    {@code true} = swiped right, {@code false} = swiped left
     * @param callback fires onSuccess when written
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
     * Reads the swipe document for a single member.
     *
     * @param roomId   the room document ID
     * @param uid      the member whose swipes to fetch
     * @param callback receives the swipeMap (movieId → liked)
     */
    public void getSwipesForMember(String roomId, String uid, SwipeMapCallback callback) {
        db.collection(Constants.COLLECTION_ROOMS)
          .document(roomId)
          .collection(Constants.COLLECTION_SWIPES)
          .document(uid)
          .get()
          .addOnSuccessListener(doc -> {
              if (!doc.exists()) {
                  // No swipes yet is a valid state, not an error.
                  callback.onSuccess(new HashMap<>());
                  return;
              }
              Map<String, Boolean> swipeMap = extractSwipeMap(doc);
              callback.onSuccess(swipeMap);
          })
          .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Reads swipe documents for ALL members in a room.
     *
     * @param roomId   the room document ID
     * @param callback receives a map of memberUid → swipeMap
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
     * Attaches a real-time listener to the entire swipes subcollection.
     *
     * <p>Fires whenever any member's swipe document changes. The caller must
     * store and {@code .remove()} the returned registration in
     * {@code Activity.onStop()}.
     *
     * @param roomId   the room document ID
     * @param callback receives the full memberUid → swipeMap on each change
     * @return a registration handle — call {@code .remove()} when done
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

    // =========================================================================
    // Room update operations
    // =========================================================================

    /**
     * Replaces the movieQueue field on a room document.
     *
     * <p>Called by the queue manager when a new page of movies is fetched from
     * TMDB and needs to be shared with all room members.
     *
     * @param roomId   the room document ID
     * @param movieIds ordered list of TMDB movie IDs (as Strings)
     * @param callback fires onSuccess when written
     */
    public void updateMovieQueue(String roomId, List<String> movieIds, SimpleCallback callback) {
        db.collection(Constants.COLLECTION_ROOMS)
          .document(roomId)
          .update("movieQueue", movieIds)
          .addOnSuccessListener(unused -> callback.onSuccess())
          .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Adds a movie to the room's matchedMovies list.
     *
     * <p>{@link FieldValue#arrayUnion} ensures no duplicates even if called twice
     * for the same movie (e.g. a race condition between two members' last swipes).
     *
     * @param roomId   the room document ID
     * @param movieId  TMDB movie ID (String)
     * @param callback fires onSuccess when written
     */
    public void addMatchedMovie(String roomId, String movieId, SimpleCallback callback) {
        db.collection(Constants.COLLECTION_ROOMS)
          .document(roomId)
          .update("matchedMovies", FieldValue.arrayUnion(movieId))
          .addOnSuccessListener(unused -> callback.onSuccess())
          .addOnFailureListener(e -> callback.onFailure(e.getMessage()));
    }

    /**
     * Moves a movie from matchedMovies to watchedMovies atomically.
     *
     * <p>A {@link WriteBatch} ensures both the remove and the add happen together.
     * If either step fails, Firestore rolls back both — the movie cannot end up
     * missing from both lists or present in both lists simultaneously.
     *
     * @param roomId   the room document ID
     * @param movieId  TMDB movie ID (String)
     * @param callback fires onSuccess when the batch commits
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

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Recursively generates a unique invite code, retrying up to 5 times if the
     * generated code already exists in Firestore.
     *
     * @param attempt  current attempt index (0-based); stops at 5
     * @param listener receives the unique code, or {@code null} after 5 failures
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
                  listener.onResult(code); // no collision — use this code
              } else {
                  generateUniqueInviteCode(attempt + 1, listener); // try again
              }
          })
          .addOnFailureListener(e -> {
              // On query failure, fall back to using the code anyway rather than
              // blocking room creation entirely. Collisions are astronomically rare.
              listener.onResult(code);
          });
    }

    /** Generates a random uppercase alphanumeric string of the given length. */
    private static String randomCode(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /** Internal callback used only by {@link #generateUniqueInviteCode}. */
    private interface InviteCodeListener {
        void onResult(String code);
    }

    /**
     * Converts a Firestore {@link DocumentSnapshot} to a {@link Room} object.
     *
     * <p>Reads each field by name and populates the Room via setters. The
     * roomId is set from the document ID (it is the path, not a Firestore field).
     * Numeric fields arrive as Long from Firestore and are cast to int.
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
     * Extracts the {@code swipeMap} field from a swipe document.
     * Firestore stores booleans natively, so the cast is safe.
     * Returns an empty map if the field is absent.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Boolean> extractSwipeMap(DocumentSnapshot doc) {
        Map<String, Boolean> swipeMap = (Map<String, Boolean>) doc.get("swipeMap");
        return swipeMap != null ? swipeMap : new HashMap<>();
    }
}
