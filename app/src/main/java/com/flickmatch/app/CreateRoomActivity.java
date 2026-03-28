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
 * Screen where a user creates a new FlickMatch room.
 *
 * <p><b>Builder pattern in action:</b> Room has 12+ configurable fields (name,
 * genres, runtime, ratings, year range, member list, etc.). A traditional
 * constructor with 12 parameters would be nearly impossible to call correctly —
 * callers would have to remember the exact order of every argument. The Builder
 * solves this: each field is set by name, in any order, and fields we don't
 * touch keep their defaults. This is exactly why Room.Builder was written.
 *
 * <p>All Firebase operations are delegated to {@link FirebaseHelper}.
 * No Firebase SDK calls are made directly from this Activity.
 */
public class CreateRoomActivity extends AppCompatActivity {

    // -------------------------------------------------------------------------
    // Year range constants (seekbar 0..56 → 1970..2026)
    // -------------------------------------------------------------------------
    private static final int YEAR_BASE    = 1970;
    private static final int YEAR_MAX_VAL = 2026;
    private static final int YEAR_RANGE   = YEAR_MAX_VAL - YEAR_BASE; // 56

    // -------------------------------------------------------------------------
    // Views
    // -------------------------------------------------------------------------
    private EditText   editRoomName;
    private GridLayout gridGenres;
    private SeekBar    seekRuntime;
    private TextView   textRuntime;
    private CheckBox   cbRatingG, cbRatingPG, cbRatingPG13, cbRatingR;
    private SeekBar    seekYearMin, seekYearMax;
    private TextView   textYearRange;
    private Button     buttonCreateRoom;

    // -------------------------------------------------------------------------
    // Genre checkboxes — LinkedHashMap preserves insertion order
    // -------------------------------------------------------------------------
    private final Map<CheckBox, String> genreCheckboxes = new LinkedHashMap<>();

    // Full list of TMDB genre names used by the discovery API.
    private static final String[] GENRES = {
        "Action", "Adventure", "Animation", "Comedy", "Crime",
        "Documentary", "Drama", "Family", "Fantasy", "History",
        "Horror", "Music", "Mystery", "Romance", "Science Fiction",
        "Thriller", "War", "Western"
    };

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_room);

        // Show back arrow in the action bar.
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

    /** Handle the back arrow in the toolbar. */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // -------------------------------------------------------------------------
    // View setup
    // -------------------------------------------------------------------------

    private void findViews() {
        editRoomName     = findViewById(R.id.editRoomName);
        gridGenres       = findViewById(R.id.gridGenres);
        seekRuntime      = findViewById(R.id.seekRuntime);
        textRuntime      = findViewById(R.id.textRuntime);
        cbRatingG        = findViewById(R.id.cbRatingG);
        cbRatingPG       = findViewById(R.id.cbRatingPG);
        cbRatingPG13     = findViewById(R.id.cbRatingPG13);
        cbRatingR        = findViewById(R.id.cbRatingR);
        seekYearMin      = findViewById(R.id.seekYearMin);
        seekYearMax      = findViewById(R.id.seekYearMax);
        textYearRange    = findViewById(R.id.textYearRange);
        buttonCreateRoom = findViewById(R.id.buttonCreateRoom);
    }

    /**
     * Populates the genre grid programmatically to avoid declaring 18 separate
     * view IDs in XML. Each CheckBox is stored in the genreCheckboxes map so we
     * can iterate it when the user taps Create.
     */
    private void buildGenreGrid() {
        gridGenres.setColumnCount(3);

        for (String genre : GENRES) {
            CheckBox cb = new CheckBox(this);
            cb.setText(genre);
            cb.setTextSize(13f);

            // Give each cell equal weight across the 3-column grid.
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width          = 0;
            params.columnSpec     = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(0, 4, 0, 4);
            cb.setLayoutParams(params);

            gridGenres.addView(cb);
            genreCheckboxes.put(cb, genre);
        }
    }

    private void setupRuntimeSeekBar() {
        // SeekBar range 0-180 maps to 60-240 minutes.
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
        // Both seekbars: 0-56 maps to 1970-2026.
        seekYearMin.setMax(YEAR_RANGE);
        seekYearMax.setMax(YEAR_RANGE);

        // Default: 2000 - 2026  →  progress 30 / 56
        seekYearMin.setProgress(Constants.DEFAULT_YEAR_MIN - YEAR_BASE);
        seekYearMax.setProgress(YEAR_RANGE); // 2026

        updateYearRangeLabel();

        SeekBar.OnSeekBarChangeListener yearListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                // Clamp: min must not exceed max, and vice versa.
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
        textYearRange.setText(yearMin + " — " + yearMax);
    }

    // -------------------------------------------------------------------------
    // Create button
    // -------------------------------------------------------------------------

    private void onCreateClicked() {
        // --- 1. Gather inputs ---
        String roomName = editRoomName.getText().toString().trim();

        List<String> selectedGenres = new ArrayList<>();
        for (Map.Entry<CheckBox, String> entry : genreCheckboxes.entrySet()) {
            if (entry.getKey().isChecked()) {
                selectedGenres.add(entry.getValue());
            }
        }

        int maxRuntime = seekRuntime.getProgress() + 60; // 0-180 → 60-240

        List<String> selectedRatings = new ArrayList<>();
        if (cbRatingG.isChecked())    selectedRatings.add("G");
        if (cbRatingPG.isChecked())   selectedRatings.add("PG");
        if (cbRatingPG13.isChecked()) selectedRatings.add("PG-13");
        if (cbRatingR.isChecked())    selectedRatings.add("R");

        int yearMin = YEAR_BASE + seekYearMin.getProgress();
        int yearMax = YEAR_BASE + seekYearMax.getProgress();

        // --- 2. Validate ---
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

        // --- 3. Build the Room using the Builder pattern ---
        //
        // Room has 12+ optional fields. The Builder lets us set only what we
        // care about here, in any order, without a telescoping constructor.
        // This is the key advantage of the Builder pattern for complex objects.
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

        // --- 4. Save to Firebase ---
        // FirebaseHelper generates the invite code, writes the document, and
        // updates the user's roomIds list — all handled in one method call.
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
