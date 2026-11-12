package com.audiohub.music;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int AUDIO_PERMISSION_REQUEST = 100;

    int background = 0xFF09090F;
    int purple = 0xFFA855F7;
    int white = 0xFFFFFFFF;
    int gray = 0xFFA1A1AA;

    LinearLayout root, content, trackList;
    TextView status, nowPlaying;
    Button playPauseButton, previousButton, nextButton;
    EditText searchInput;

    AudioPlayer audioPlayer;
    List<AudioTrack> currentTracks;
    boolean isPrepared = false;
    int currentIndex = -1;

    TextView makeText(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(white);
        button.setBackgroundTintList(
            android.content.res.ColorStateList.valueOf(purple));
        return button;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        audioPlayer = new AudioPlayer();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 30, 20, 15);
        root.setBackgroundColor(background);

        TextView title = makeText("オーディオハブ", 27, white);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView subtitle = makeText(
            "YOUR MUSIC. YOUR WORLD.", 11, purple);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setGravity(Gravity.CENTER);

        String[] pages = {"Home", "Library", "Downloads", "AI DJ"};

        for (String page : pages) {
            TextView button = makeText(page, 12, gray);
            button.setGravity(Gravity.CENTER);
            button.setPadding(4, 18, 4, 18);
            button.setOnClickListener(v -> showPage(page));
            navigation.addView(button,
                new LinearLayout.LayoutParams(0, -2, 1));
        }

        root.addView(navigation);
        setContentView(root);

        showPage("Home");
    }

    void showPage(String page) {
        content.removeAllViews();

        if (page.equals("Library")) {
            showLibrary();
            return;
        }

        TextView message = makeText(
            "\n" + page + "\n\nYour music, all in one place.",
            20, white);
        message.setGravity(Gravity.CENTER);
        content.addView(message,
            new LinearLayout.LayoutParams(-1, 0, 1));
    }

    void showLibrary() {
        TextView heading = makeText("YOUR MUSIC LIBRARY", 19, white);
        heading.setTypeface(null, 1);
        heading.setPadding(0, 20, 0, 15);
        content.addView(heading);

        nowPlaying = makeText("NOTHING PLAYING", 14, purple);
        nowPlaying.setPadding(0, 8, 0, 8);
        content.addView(nowPlaying);

        playPauseButton = makeButton("PLAY");
        playPauseButton.setEnabled(false);
        content.addView(playPauseButton);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        previousButton = makeButton("PREVIOUS");
        nextButton = makeButton("NEXT");

        controls.addView(previousButton,
            new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(nextButton,
            new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(controls);

        previousButton.setEnabled(false);
        nextButton.setEnabled(false);

        previousButton.setOnClickListener(v -> {
            if (currentTracks == null || currentTracks.isEmpty()) return;
            int index = currentIndex <= 0
                ? currentTracks.size() - 1 : currentIndex - 1;
            playTrack(index);
        });

        nextButton.setOnClickListener(v -> {
            if (currentTracks == null || currentTracks.isEmpty()) return;
            int index = currentIndex < 0 ||
                currentIndex >= currentTracks.size() - 1
                ? 0 : currentIndex + 1;
            playTrack(index);
        });

        playPauseButton.setOnClickListener(v -> {
            if (!isPrepared) return;

            if (audioPlayer.isPlaying()) {
                audioPlayer.pause();
                playPauseButton.setText("RESUME");
            } else {
                audioPlayer.resume();
                playPauseButton.setText("PAUSE");
            }
        });

        Button scanButton = makeButton("SCAN MY MUSIC");
        content.addView(scanButton);

        status = makeText(
            "Scan internal storage and SD card for music.",
            14, gray);
        status.setPadding(0, 15, 0, 15);
        content.addView(status);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Search songs or artists");
        searchInput.setHintTextColor(gray);
        searchInput.setTextColor(white);
        content.addView(searchInput);

        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderTrackList(s.toString());
            }
            public void afterTextChanged(Editable e) {}
        });

        ScrollView scroll = new ScrollView(this);
        trackList = new LinearLayout(this);
        trackList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(trackList);

        content.addView(scroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

        scanButton.setOnClickListener(v -> checkAudioPermission());
    }

    void checkAudioPermission() {
        String permission;

        if (Build.VERSION.SDK_INT >= 33) {
            permission = Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(permission)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{permission},
                AUDIO_PERMISSION_REQUEST);
        } else {
            scanMusic();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
            requestCode, permissions, grantResults);

        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanMusic();
            } else if (status != null) {
                status.setText("Permission denied. Allow music access.");
            }
        }
    }

    void scanMusic() {
        status.setText("Scanning music...");
        trackList.removeAllViews();

        new Thread(() -> {
            List<AudioTrack> tracks = AudioScanner.scan(this);

            runOnUiThread(() -> {
                currentTracks = tracks;
                previousButton.setEnabled(!tracks.isEmpty());
                nextButton.setEnabled(!tracks.isEmpty());
                renderTrackList(searchInput.getText().toString());
            });
        }).start();
    }

    void renderTrackList(String query) {
        trackList.removeAllViews();

        if (currentTracks == null) return;

        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;

        for (int i = 0; i < currentTracks.size(); i++) {
            final int index = i;
            AudioTrack track = currentTracks.get(i);

            String title = track.title == null ? "" : track.title;
            String artist = track.artist == null ? "" : track.artist;

            if (!title.toLowerCase(Locale.ROOT).contains(q) &&
                !artist.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }

            TextView item = makeText(
                title + "\\n" + artist + "  •  " +
                formatDuration(track.duration), 15, white);

            item.setPadding(12, 15, 12, 15);
            item.setOnClickListener(v -> playTrack(index));
            trackList.addView(item);

            View line = new View(this);
            line.setBackgroundColor(0xFF292936);
            trackList.addView(line,
                new LinearLayout.LayoutParams(-1, 1));

            shown++;
        }

        status.setText(shown + " songs found");

        if (shown == 0) {
            TextView empty = makeText(
                q.isEmpty() ? "No music found." : "No matching songs found.",
                15, gray);
            empty.setPadding(0, 20, 0, 20);
            trackList.addView(empty);
        }
    }

    void playTrack(int index) {
        if (currentTracks == null ||
                index < 0 || index >= currentTracks.size()) return;

        currentIndex = index;
        isPrepared = false;

        AudioTrack track = currentTracks.get(index);

        nowPlaying.setText("LOADING: " + track.title);
        status.setText("Preparing audio...");
        playPauseButton.setEnabled(false);
        playPauseButton.setText("LOADING");

        audioPlayer.play(this, track, new AudioPlayer.Listener() {
            @Override
            public void onPrepared() {
                isPrepared = true;
                nowPlaying.setText("NOW PLAYING: " + track.title);
                status.setText("Playing • " + track.artist);
                playPauseButton.setEnabled(true);
                playPauseButton.setText("PAUSE");
            }

            @Override
            public void onCompleted() {
                isPrepared = false;
                status.setText("Song finished.");
                playPauseButton.setText("PLAY");
            }

            @Override
            public void onError(String message) {
                isPrepared = false;
                status.setText("Playback error: " + message);
                playPauseButton.setText("PLAY");
                playPauseButton.setEnabled(false);
            }
        });
    }

    String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        return String.format(Locale.getDefault(),
            "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void onDestroy() {
        if (audioPlayer != null) {
            audioPlayer.release();
        }
        super.onDestroy();
    }
}
