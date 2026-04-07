package com.flickmatch.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * screen where a user creates a new FlickMatch room.
 *
 * each room has over 10 configurable fields e.g. name, genres, runtime,
 * ratings, etc. If we used a regular constructor, we'd have to pass all
 * values in the exact right order every time; one mistake and everything
 * will break. The Builder pattern fixes this by letting you set only the
 * fields you need, by name, in any preferred order. That's why Room.Builder exists.
 *
 */

public class CreateRoomActivity extends AppCompatActivity {

    // constants for year range (seekbar 0..56 → 1970..2026) //
    private static final int YEAR_BASE = 1970;
    private static final int YEAR_MAX_VAL = 2026;
    private static final int YEAR_RANGE = YEAR_MAX_VAL - YEAR_BASE; // 56

    // initialise on-screen elements variables //
    private EditText editRoomName;
    private GridLayout gridGenres;
    private SeekBar seekRuntime;
    private TextView textRuntime;
    private CheckBox cbRatingG, cbRatingPG, cbRatingPG13, cbRatingR;
    private SeekBar seekYearMin, seekYearMax;
    private TextView textYearRange;
    private Button buttonCreateRoom;

    // genre checkboxes - LinkedHashMap preserves the order of insertion //
    private final Map<CheckBox, String> genreCheckboxes = new LinkedHashMap<>();

    // list of TMDB genre names used by the discovery API //
    private static final String[] GENRES = {
            "Action", "Adventure", "Animation", "Comedy", "Crime",
            "Documentary", "Drama", "Family", "Fantasy", "History",
            "Horror", "Music", "Mystery", "Romance", "Science Fiction",
            "Thriller", "War", "Western"
    };

    // lifecycle of screen //
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room);

        // show back arrow at the top of the screen //
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Create a Room");
        }

        findViews();
        buildGenreGrid();
        setupRuntimeSeekBar();
        setupYearSeekBars();

        buttonCreateRoom.setOnClickListener(v -> onCreateClicked());
    }

    // handle the back arrow in the toolbar //
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // assigning the views to the variables //
    private void findViews() {
        editRoomName = findViewById(R.id.editRoomName);
        gridGenres = findViewById(R.id.gridGenres);
        seekRuntime = findViewById(R.id.seekRuntime);
        textRuntime = findViewById(R.id.textRuntime);
        cbRatingG = findViewById(R.id.cbRatingG);
        cbRatingPG = findViewById(R.id.cbRatingPG);
        cbRatingPG13 = findViewById(R.id.cbRatingPG13);
        cbRatingR = findViewById(R.id.cbRatingR);
        seekYearMin = findViewById(R.id.seekYearMin);
        seekYearMax = findViewById(R.id.seekYearMax);
        textYearRange = findViewById(R.id.textYearRange);
        buttonCreateRoom = findViewById(R.id.buttonCreateRoom);
    }

    /**
     * Instead of manually creating 18 separate checkboxes in XML (which would
     * be tedious and hard to maintain), we generate them in code and store each
     * one in the genreCheckboxes map. That way, when the user taps Create, we
     * can easily loop through them all and see which genres were selected.
     */
    private void buildGenreGrid() {
        gridGenres.setColumnCount(3);

        for (String genre : GENRES) {
            CheckBox cb = new CheckBox(this);
            cb.setText(genre);
            cb.setTextSize(13f);

            // give each cell equal weight across the 3-column grid //
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(0, 4, 0, 4);
            cb.setLayoutParams(params);

            gridGenres.addView(cb);
            genreCheckboxes.put(cb, genre);
        }
    }

    private void setupRuntimeSeekBar() {

        // SeekBar range 0-180 maps to 60-240 minutes //
        seekRuntime.setMax(180);
        seekRuntime.setProgress(180); // default: 240 min
        textRuntime.setText("240 min");

        seekRuntime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                textRuntime.setText((progress + 60) + " min");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
    }

    private void setupYearSeekBars() {

        // both seekbars: 0-56 maps to 1970-2026 //
        seekYearMin.setMax(YEAR_RANGE);
        seekYearMax.setMax(YEAR_RANGE);

        // default: 2000 - 2026 -> progress 30 / 56
        seekYearMin.setProgress(Constants.DEFAULT_YEAR_MIN - YEAR_BASE);
        seekYearMax.setProgress(YEAR_RANGE); // 2026

        updateYearRangeLabel();

        SeekBar.OnSeekBarChangeListener yearListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {

                // sets a restriction such that min must not exceed max, and vice versa //
                if (bar == seekYearMin && seekYearMin.getProgress() > seekYearMax.getProgress()) {
                    seekYearMin.setProgress(seekYearMax.getProgress());
                } else if (bar == seekYearMax && seekYearMax.getProgress() < seekYearMin.getProgress()) {
                    seekYearMax.setProgress(seekYearMin.getProgress());
                }
                updateYearRangeLabel();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        };

        seekYearMin.setOnSeekBarChangeListener(yearListener);
        seekYearMax.setOnSeekBarChangeListener(yearListener);
    }

    private void updateYearRangeLabel() {
        int yearMin = YEAR_BASE + seekYearMin.getProgress();
        int yearMax = YEAR_BASE + seekYearMax.getProgress();
        textYearRange.setText(yearMin + " - " + yearMax);
    }

    // code for 'Create' button //
    private void onCreateClicked() {

        // read user's inputs and selections //
        String roomName = editRoomName.getText().toString().trim();

        List<String> selectedGenres = new ArrayList<>();
        for (Map.Entry<CheckBox, String> entry : genreCheckboxes.entrySet()) {
            if (entry.getKey().isChecked()) {
                selectedGenres.add(entry.getValue());
            }
        }

        int maxRuntime = seekRuntime.getProgress() + 60; // maps 0-180 to 60-240

        List<String> selectedRatings = new ArrayList<>();
        if (cbRatingG.isChecked()) selectedRatings.add("G");
        if (cbRatingPG.isChecked()) selectedRatings.add("PG");
        if (cbRatingPG13.isChecked()) selectedRatings.add("PG-13");
        if (cbRatingR.isChecked()) selectedRatings.add("R");

        int yearMin = YEAR_BASE + seekYearMin.getProgress();
        int yearMax = YEAR_BASE + seekYearMax.getProgress();

        // validate inputs and selections //
        if (roomName.isEmpty()) {
            Toast.makeText(this, "Please enter a room name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedGenres.isEmpty()) {
            Toast.makeText(this, "Please select at least one genre", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedRatings.isEmpty()) {
            Toast.makeText(this, "Please select at least one age rating", Toast.LENGTH_SHORT).show();
            return;
        }
        if (yearMin > yearMax) {
            Toast.makeText(this, "Min year can't be after max year", Toast.LENGTH_SHORT).show();
            return;
        }

        buttonCreateRoom.setEnabled(false);

        // build room using Builder pattern //

        String uid = FirebaseHelper.getInstance().getCurrentUid();

        List<String> members = new ArrayList<>();
        members.add(uid); // creator is the first member

        Room room = new Room.Builder()
                .setRoomName(roomName)
                .setCreatorUid(uid)
                .setMemberUids(members)
                .setGenres(selectedGenres)
                .setMaxRuntime(maxRuntime)
                .setAgeRatings(selectedRatings)
                .setYearMin(yearMin)
                .setYearMax(yearMax)
                .build();

        /** save room details to Firebase that can generate the invite code, write
         * document and update user's roomIds list
         */
        FirebaseHelper.getInstance().createRoom(room, new FirebaseHelper.RoomCallback() {
            @Override
            public void onSuccess(Room createdRoom) {
                Intent intent = new Intent(CreateRoomActivity.this, RoomActivity.class);
                intent.putExtra("ROOM_ID",   createdRoom.getRoomId());
                intent.putExtra("ROOM_NAME", createdRoom.getRoomName());
                startActivity(intent);
                finish(); // pop back stack so Back returns to MainActivity, not here
            }

            @Override
            public void onFailure(String error) {
                Toast.makeText(CreateRoomActivity.this, error, Toast.LENGTH_SHORT).show();
                buttonCreateRoom.setEnabled(true);
            }
        });
    }
}