package com.flickmatch.app;

// IMPORTANT: Before running the app, register the following activities in AndroidManifest.xml
// inside the <application> tag:
//
//   <activity android:name=".CreateRoomActivity" />
//   <activity android:name=".RoomActivity" />

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for FlickMatch. Manages three sequential UI states:
 *
 * <ol>
 *   <li><b>Loading</b> — anonymous Firebase sign-in runs in background.</li>
 *   <li><b>Name entry</b> — first-time users choose a display name.</li>
 *   <li><b>Rooms list</b> — returning users see their rooms and can create/join.</li>
 * </ol>
 *
 * All Firebase calls are delegated to {@link FirebaseHelper}. No Firebase SDK
 * calls are made directly from this Activity (Separation of Concerns).
 */
public class MainActivity extends AppCompatActivity {

    // -------------------------------------------------------------------------
    // Views — loading state
    // -------------------------------------------------------------------------
    private LinearLayout layoutLoading;

    // -------------------------------------------------------------------------
    // Views — name entry state
    // -------------------------------------------------------------------------
    private LinearLayout layoutNameEntry;
    private EditText editDisplayName;
    private Button buttonContinue;

    // -------------------------------------------------------------------------
    // Views — rooms list state
    // -------------------------------------------------------------------------
    private LinearLayout layoutRoomsList;
    private TextView textGreeting;
    private RecyclerView recyclerRooms;
    private TextView textEmptyRooms;
    private Button buttonCreateRoom;
    private Button buttonJoinRoom;

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private FirebaseHelper firebaseHelper;
    private String currentUid;
    private String currentDisplayName;
    private RoomAdapter roomAdapter;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        firebaseHelper = FirebaseHelper.getInstance();

        findViews();
        setupRecyclerView();
        setupButtons();

        showLoadingState();
        startAuthFlow();
    }

    /**
     * Reload the rooms list each time the user returns to this screen
     * (e.g., after creating a new room in CreateRoomActivity).
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Only reload if we're already on the rooms list state.
        if (layoutRoomsList != null && layoutRoomsList.getVisibility() == View.VISIBLE) {
            loadUserRooms();
        }
    }

    // -------------------------------------------------------------------------
    // View setup
    // -------------------------------------------------------------------------

    private void findViews() {
        layoutLoading   = findViewById(R.id.layoutLoading);
        layoutNameEntry = findViewById(R.id.layoutNameEntry);
        layoutRoomsList = findViewById(R.id.layoutRoomsList);

        editDisplayName = findViewById(R.id.editDisplayName);
        buttonContinue  = findViewById(R.id.buttonContinue);

        textGreeting   = findViewById(R.id.textGreeting);
        recyclerRooms  = findViewById(R.id.recyclerRooms);
        textEmptyRooms = findViewById(R.id.textEmptyRooms);
        buttonCreateRoom = findViewById(R.id.buttonCreateRoom);
        buttonJoinRoom   = findViewById(R.id.buttonJoinRoom);
    }

    private void setupRecyclerView() {
        roomAdapter = new RoomAdapter(new ArrayList<>());
        recyclerRooms.setLayoutManager(new LinearLayoutManager(this));
        recyclerRooms.setAdapter(roomAdapter);
    }

    private void setupButtons() {
        buttonContinue.setOnClickListener(v -> onContinueClicked());
        buttonCreateRoom.setOnClickListener(v -> openCreateRoom());
        buttonJoinRoom.setOnClickListener(v -> showJoinRoomDialog());
    }

    // -------------------------------------------------------------------------
    // Auth flow
    // -------------------------------------------------------------------------

    /**
     * Starts anonymous sign-in, then checks whether the user already has a
     * display name to decide which state to show next.
     */
    private void startAuthFlow() {
        firebaseHelper.signInAnonymously(new FirebaseHelper.AuthCallback() {
            @Override
            public void onSuccess(String uid) {
                currentUid = uid;
                // Check whether this user already set a display name.
                firebaseHelper.getDisplayName(uid, new FirebaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(String displayName) {
                        if (displayName != null && !displayName.trim().isEmpty()) {
                            // Returning user — go straight to rooms list.
                            currentDisplayName = displayName;
                            showRoomsListState(displayName);
                        } else {
                            // Display name exists but is blank — treat as new user.
                            showNameEntryState();
                        }
                    }

                    @Override
                    public void onFailure(String error) {
                        // No user document yet — first-time user.
                        showNameEntryState();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(MainActivity.this,
                        "Sign-in failed: " + error, Toast.LENGTH_LONG).show();
                // Stay on loading; user can restart the app.
            }
        });
    }

    // -------------------------------------------------------------------------
    // State transitions
    // -------------------------------------------------------------------------

    private void showLoadingState() {
        layoutLoading.setVisibility(View.VISIBLE);
        layoutNameEntry.setVisibility(View.GONE);
        layoutRoomsList.setVisibility(View.GONE);
    }

    private void showNameEntryState() {
        layoutLoading.setVisibility(View.GONE);
        layoutNameEntry.setVisibility(View.VISIBLE);
        layoutRoomsList.setVisibility(View.GONE);
    }

    private void showRoomsListState(String displayName) {
        layoutLoading.setVisibility(View.GONE);
        layoutNameEntry.setVisibility(View.GONE);
        layoutRoomsList.setVisibility(View.VISIBLE);

        textGreeting.setText("Hey, " + displayName + "!");
        loadUserRooms();
    }

    // -------------------------------------------------------------------------
    // Name entry
    // -------------------------------------------------------------------------

    private void onContinueClicked() {
        String name = editDisplayName.getText().toString().trim();

        if (name.isEmpty()) {
            editDisplayName.setError("Please enter a name");
            return;
        }
        if (name.length() > 20) {
            editDisplayName.setError("Name must be 20 characters or fewer");
            return;
        }

        buttonContinue.setEnabled(false);

        firebaseHelper.saveDisplayName(currentUid, name, new FirebaseHelper.SimpleCallback() {
            @Override
            public void onSuccess() {
                currentDisplayName = name;
                showRoomsListState(name);
            }

            @Override
            public void onFailure(String error) {
                buttonContinue.setEnabled(true);
                Toast.makeText(MainActivity.this,
                        "Couldn't save name: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Rooms list
    // -------------------------------------------------------------------------

    private void loadUserRooms() {
        firebaseHelper.getUserRooms(currentUid, new FirebaseHelper.RoomsListCallback() {
            @Override
            public void onSuccess(List<Room> rooms) {
                if (rooms.isEmpty()) {
                    recyclerRooms.setVisibility(View.GONE);
                    textEmptyRooms.setVisibility(View.VISIBLE);
                } else {
                    textEmptyRooms.setVisibility(View.GONE);
                    recyclerRooms.setVisibility(View.VISIBLE);
                    roomAdapter.setRooms(rooms);
                }
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(MainActivity.this,
                        "Couldn't load rooms: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openCreateRoom() {
        Intent intent = new Intent(this, CreateRoomActivity.class);
        startActivity(intent);
    }

    private void showJoinRoomDialog() {
        // Build a simple AlertDialog with an EditText for the invite code.
        EditText codeInput = new EditText(this);
        codeInput.setHint("Invite code");
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        codeInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(Constants.INVITE_CODE_LENGTH) });

        // Wrap in a LinearLayout to apply padding (AlertDialog doesn't pad views automatically).
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int px = dpToPx(16);
        container.setPadding(px, px, px, 0);
        container.addView(codeInput);

        new AlertDialog.Builder(this)
                .setTitle("Join a Room")
                .setMessage("Enter the 6-character invite code:")
                .setView(container)
                .setPositiveButton("Join", (dialog, which) -> {
                    String code = codeInput.getText().toString().trim().toUpperCase();
                    if (code.isEmpty()) {
                        Toast.makeText(this, "Please enter an invite code", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    joinRoom(code);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void joinRoom(String inviteCode) {
        firebaseHelper.joinRoom(inviteCode, currentUid, new FirebaseHelper.RoomCallback() {
            @Override
            public void onSuccess(Room room) {
                Toast.makeText(MainActivity.this,
                        "Joined \"" + room.getRoomName() + "\"!", Toast.LENGTH_SHORT).show();
                loadUserRooms();
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(MainActivity.this,
                        "Couldn't join room: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    // =========================================================================
    // Inner class — RoomAdapter
    // =========================================================================

    /**
     * RecyclerView adapter for the rooms list.
     *
     * <p>Defined as an inner class of MainActivity so it can reference
     * {@code MainActivity.this} to start RoomActivity on item tap — avoids
     * passing a Context separately.
     */
    private class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

        private List<Room> rooms;

        RoomAdapter(List<Room> rooms) {
            this.rooms = rooms;
        }

        /** Replaces the current list and refreshes the RecyclerView. */
        void setRooms(List<Room> newRooms) {
            this.rooms = newRooms;
            notifyDataSetChanged();
        }

        @Override
        public RoomViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_room, parent, false);
            return new RoomViewHolder(view);
        }

        @Override
        public void onBindViewHolder(RoomViewHolder holder, int position) {
            Room room = rooms.get(position);

            holder.textRoomName.setText(room.getRoomName());

            int memberCount = room.getMemberUids() != null ? room.getMemberUids().size() : 0;
            holder.textMemberCount.setText(memberCount + (memberCount == 1 ? " member" : " members"));

            holder.textInviteCode.setText("Code: " + room.getInviteCode());

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, RoomActivity.class);
                intent.putExtra(RoomActivity.EXTRA_ROOM_ID,   room.getRoomId());
                intent.putExtra(RoomActivity.EXTRA_ROOM_NAME, room.getRoomName());
                startActivity(intent);
            });
        }

        @Override
        public int getItemCount() {
            return rooms.size();
        }

        class RoomViewHolder extends RecyclerView.ViewHolder {
            TextView textRoomName;
            TextView textMemberCount;
            TextView textInviteCode;

            RoomViewHolder(View itemView) {
                super(itemView);
                textRoomName    = itemView.findViewById(R.id.textRoomName);
                textMemberCount = itemView.findViewById(R.id.textMemberCount);
                textInviteCode  = itemView.findViewById(R.id.textInviteCode);
            }
        }
    }
}
