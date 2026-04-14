package com.flickmatch.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for FlickMatch. Manages three sequential UI states:
 *
 * 1. Loading screen — anonymous Firebase sign-in runs in background
 * 2. Name entry — first-time users choose a display name
 * 3. Rooms list — returning users see their rooms and can create/join
 *
 */

public class MainActivity extends AppCompatActivity {

    // loading state views //
    private LinearLayout layoutLoading;

    // name entry state views //
    private LinearLayout layoutNameEntry;
    private EditText editDisplayName;
    private Button buttonContinue;

    // rooms list state views //
    private LinearLayout layoutRoomsList;
    private TextView textGreeting;
    private RecyclerView recyclerRooms;
    private TextView textEmptyRooms;
    private Button buttonCreateRoom;
    private Button buttonJoinRoom;

    // states //
    private FirebaseHelper firebaseHelper;
    private String currentUid;
    private String currentDisplayName;
    private RoomAdapter roomAdapter;

    // lifecycle of app //
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

    // reload rooms list every time the user returns to this screen
    @Override
    protected void onResume() {
        super.onResume();

        // reload only if we're already on the rooms list state //
        if (layoutRoomsList != null && layoutRoomsList.getVisibility() == View.VISIBLE) {
            loadUserRooms();
        }
    }

    // setup of on-screen elements in layouts //
    private void findViews() {
        layoutLoading = findViewById(R.id.layoutLoading);
        layoutNameEntry = findViewById(R.id.layoutNameEntry);
        layoutRoomsList = findViewById(R.id.layoutRoomsList);

        editDisplayName = findViewById(R.id.editDisplayName);
        buttonContinue = findViewById(R.id.buttonContinue);

        textGreeting = findViewById(R.id.textGreeting);
        recyclerRooms = findViewById(R.id.recyclerRooms);
        textEmptyRooms = findViewById(R.id.textEmptyRooms);
        buttonCreateRoom = findViewById(R.id.buttonCreateRoom);
        buttonJoinRoom = findViewById(R.id.buttonJoinRoom);
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

    /** authentication flow - starts with a sign in and checks if user has
     * a display name before deciding the next state to show
     */
    private void startAuthFlow() {
        firebaseHelper.signInAnonymously(new FirebaseHelper.AuthCallback() {
            @Override
            public void onSuccess(String uid) {
                currentUid = uid;

                // checks if user has set a display name //
                firebaseHelper.getDisplayName(uid, new FirebaseHelper.AuthCallback() {
                    @Override
                    public void onSuccess(String displayName) {
                        if (displayName != null && !displayName.trim().isEmpty()) {

                            // goes to room list if it's returning user //
                            currentDisplayName = displayName;
                            showRoomsListState(displayName);

                        } else {

                            // else display name is blank; treat as new user & go to name entry state //
                            showNameEntryState();
                        }
                    }

                    @Override
                    public void onFailure(String error) {

                        // first-time user, go to name entry state //
                        showNameEntryState();
                    }
                });
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(MainActivity.this,
                        "Sign-in failed: " + error, Toast.LENGTH_LONG).show();

                // stay on loading state so user can restart the app //
            }
        });
    }

    // transitions between states //
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

    // name entry state //
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

    // rooms list state //
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

        // display an AlertDialog with an EditText for the invite code //
        EditText codeInput = new EditText(this);
        codeInput.setHint("Invite code");
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        codeInput.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(Constants.INVITE_CODE_LENGTH) });

        // forcing light text colour in EditText //
        codeInput.setTextColor(Color.parseColor("#E8E8E8"));
        codeInput.setHintTextColor(Color.parseColor("#1E1E21"));

        // wrap in a LinearLayout to apply padding as AlertDialog doesn't pad views automatically //
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int px = dpToPx(16);
        container.setPadding(px, px, px, 0);
        container.addView(codeInput);

        new MaterialAlertDialogBuilder(this, R.style.CustomAlertDialog)
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

    // ensure display looks consistent across all devices //
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    /**
     * RecyclerView adapter for the rooms list.
     *
     * defined as an inner class of MainActivity so it can reference MainActivity.this
     * to start RoomActivity upon item tap, which avoids passing a Context separately
     *
     */

    private class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

        private List<Room> rooms;

        RoomAdapter(List<Room> rooms) {
            this.rooms = rooms;
        }

        // replaces current list and refreshes RecyclerView //
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
                intent.putExtra(RoomActivity.EXTRA_ROOM_ID, room.getRoomId());
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
                textRoomName = itemView.findViewById(R.id.textRoomName);
                textMemberCount = itemView.findViewById(R.id.textMemberCount);
                textInviteCode = itemView.findViewById(R.id.textInviteCode);
            }
        }
    }
}