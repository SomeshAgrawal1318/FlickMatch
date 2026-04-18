# FlickMatch 🍿
FlickMatch is an Android app that takes the frustration out of deciding what to watch with friends. Create a room, invite your group, and swipe through movies together - when everyone likes the same film, it's a match.
Think of it as Tinder for movie nights.

## How It Works

1. Create or join a room - one person creates a room and shares a 6-character invite code with their group. Everyone else joins by entering the code.
2. Set your filters - the room creator picks genres, a max runtime, age ratings, and a release year range.
3. Swipe together - every member independently swipes through the same curated movie queue. Like or skip each film.
4. Get a match - when all members swipe right on the same movie, it's added to the group's matched watchlist.
5. Mark as watched - once you've watched a film, mark it as done and it moves to your watched history.

## Features

- Anonymous sign-in - no account or email required. Just open the app and pick a name.
- Room system - up to 8 members per room, multiple rooms per user.
- Smart movie queue - movies are fetched from TMDB based on the room's filters and queued for everyone to swipe through in the same order.
- Real-time sync - member joins, swipes, and matches update live via Firestore listeners. No refresh needed.
- Match detection - the app automatically detects when all members have liked the same film and flags it as a match instantly.
- Watchlist & watched history - matched movies are saved to a watchlist. Watched films move to a separate section.
- Sortable watchlist - sort matched films by match order, rating, or popularity.
- Configurable filters - genres (18 options), max runtime (60–240 min), age ratings (G / PG / PG-13 / R), and release year range (1970–2026).

## Project Structure

```plaintext
app/src/main/java/com/flickmatch/app/
├── MainActivity.java                  # Entry point - handles sign-in, name entry, rooms list
├── CreateRoomActivity.java            # Room creation form with filters
├── RoomActivity.java                  # Main room screen - Lobby, Swipe, and Watchlist tabs
│
├── FirebaseHelper.java                # Singleton - all Firestore and Auth operations
├── TMDBHelper.java                    # Translates room filters into TMDB API calls
├── RetrofitClient.java                # Retrofit singleton for TMDB network calls
├── TMDBApiService.java                # Retrofit interface - TMDB endpoint definitions
│
├── Room.java                          # Firestore data model with Builder pattern
├── Movie.java                         # TMDB movie data model
├── DiscoverResponse.java              # TMDB discover API response wrapper
├── MatchingHelper.java                # Match detection logic
└── Constants.java                     # API keys, URLs, and app-wide defaults
