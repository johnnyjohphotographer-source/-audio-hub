package com.audiohub.music;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private static final int AUDIO_PERMISSION_REQUEST = 100;
    private static final int MIC_PERMISSION_REQUEST = 102;
    private static final int MIC_REQUEST_CODE = 103;
    private static final int THEME_PHOTO_REQUEST = 104;

    int background = 0xFF09090F;
    int purple = 0xFFA855F7;
    int white = 0xFFFFFFFF;
    int gray = 0xFFA1A1AA;

    LinearLayout root, content, trackList;
    TextView status, nowPlaying;
    Button playPauseButton, previousButton, nextButton;
    EditText searchInput;

    List<AudioTrack> currentTracks = new ArrayList<>();
    PlaylistStore playlistStore;
    ThemeManager themeManager;

    int currentIndex = -1;
    boolean isPlaying = false;
    boolean shuffle = false;
    boolean repeat = false;
    boolean receiverRegistered = false;

    String currentPage = "Home";

    final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!PlaybackService.ACTION_UPDATE.equals(intent.getAction())) return;

            currentIndex = intent.getIntExtra("index", currentIndex);
            isPlaying = intent.getBooleanExtra("playing", false);

            String title = intent.getStringExtra("title");
            String artist = intent.getStringExtra("artist");

            if (nowPlaying != null) {
                nowPlaying.setText("NOW PLAYING: " +
                    (title == null ? "Audio Hub" : title));
            }

            if (status != null) {
                status.setText((isPlaying ? "Playing" : "Paused") +
                    (artist == null || artist.isEmpty() ? "" : " • " + artist));
            }

            if (playPauseButton != null) {
                playPauseButton.setText(isPlaying ? "PAUSE" : "RESUME");
            }
        }
    };

    void applyThemeBackground() {
        String photo = themeManager.getPhoto();

        if ("Custom Theme".equals(themeManager.getTheme())
                && photo != null && !photo.isEmpty()) {
            try {
                android.graphics.BitmapFactory.Options options =
                    new android.graphics.BitmapFactory.Options();
                options.inSampleSize = 4;

                android.graphics.Bitmap bitmap;
                try (java.io.InputStream input =
                        getContentResolver().openInputStream(Uri.parse(photo))) {
                    if (input == null) return;
                    bitmap = BitmapFactory.decodeStream(input, null, options);
                }

                if (bitmap != null) {
                    BitmapDrawable image = new BitmapDrawable(
                        getResources(), bitmap);
                    image.setGravity(Gravity.FILL);

                    LayerDrawable layers = new LayerDrawable(
                        new android.graphics.drawable.Drawable[]{
                            image,
                            new ColorDrawable(0x99000000)
                        });
                    root.setBackground(layers);
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        root.setBackgroundColor(background);
    }

    void applyThemeColors() {
        String theme = themeManager.getTheme();

        switch (theme) {
            case "AMOLED Black":
                background = 0xFF000000;
                purple = 0xFFBB86FC;
                white = 0xFFFFFFFF;
                gray = 0xFF9E9E9E;
                break;
            case "Cyber Blue":
                background = 0xFF06111F;
                purple = 0xFF00D9FF;
                white = 0xFFFFFFFF;
                gray = 0xFF89B8D0;
                break;
            case "Crimson Red":
                background = 0xFF160709;
                purple = 0xFFFF3B4F;
                white = 0xFFFFFFFF;
                gray = 0xFFD6A0A5;
                break;
            case "Pure Light":
                background = 0xFFF5F5F7;
                purple = 0xFF6D28D9;
                white = 0xFF17171C;
                gray = 0xFF666677;
                break;
            case "Custom Theme":
                background = themeManager.getCustomBackground();
                purple = themeManager.getCustomAccent();
                white = themeManager.getCustomText();
                gray = themeManager.getCustomText();
                break;
            default:
                background = 0xFF09090F;
                purple = 0xFFA855F7;
                white = 0xFFFFFFFF;
                gray = 0xFFA1A1AA;
                break;
        }
    }

    TextView makeText(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);

        if (color == 0xFFFFFFFF) color = white;
        else if (color == 0xFFA855F7) color = purple;
        else if (color == 0xFFA1A1AA) color = gray;

        text.setTextColor(color);
        return text;
    }

    Button makeButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(white);
        button.setTextSize(13);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setPadding(dp(18), dp(12), dp(18), dp(12));
        button.setMinHeight(dp(48));
        GradientDrawable shape = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{purple, darken(purple)});
        shape.setCornerRadius(dp(16));
        shape.setStroke(dp(1), 0x66FFFFFF);
        button.setBackground(shape);
        button.setElevation(dp(3));
        return button;
    }

    LinearLayout makeRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }


    int dp(float value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    int darken(int color) {
        return android.graphics.Color.rgb(
            (int)(android.graphics.Color.red(color) * 0.72f),
            (int)(android.graphics.Color.green(color) * 0.72f),
            (int)(android.graphics.Color.blue(color) * 0.72f));
    }

    GradientDrawable roundedCard(int color, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(18));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    void styleScreen() {
        if (content != null) styleViewTree(content);
    }

    void styleViewTree(View view) {
        if (view instanceof Button) {
            Button b = (Button)view;
            b.setAllCaps(false);
            b.setTextColor(white);
            b.setTextSize(13);
            b.setTypeface(null, android.graphics.Typeface.BOLD);
            GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{purple, darken(purple)});
            shape.setCornerRadius(dp(16));
            shape.setStroke(dp(1), 0x66FFFFFF);
            b.setBackground(shape);
            b.setElevation(dp(2));
        } else if (view instanceof EditText) {
            EditText e = (EditText)view;
            e.setTextColor(white);
            e.setHintTextColor(gray);
            e.setPadding(dp(16), dp(13), dp(16), dp(13));
            e.setBackground(roundedCard(0xDD171722, 0x665B3B79));
        } else if (view instanceof TextView) {
            TextView t = (TextView)view;
            String value = t.getText() == null ? "" : t.getText().toString();
            if (value.contains("\\n") || value.startsWith("♫") ||
                value.startsWith("NOW PLAYING") || value.contains("songs shown")) {
                t.setPadding(dp(15), dp(14), dp(15), dp(14));
                t.setBackground(roundedCard(0xCC171722, 0x554A3A62));
            }
        }

        if (view instanceof ScrollView) {
            ((ViewGroup) view).setClipToPadding(false);
            view.setPadding(0, dp(5), 0, dp(5));
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); i++) {
                styleViewTree(group.getChildAt(i));
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        playlistStore = new PlaylistStore(this);
        themeManager = new ThemeManager(this);
        applyThemeColors();

        IntentFilter filter = new IntentFilter(PlaybackService.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(playbackReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(playbackReceiver, filter);
        }
        receiverRegistered = true;

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
        }

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 25, 20, 12);
        applyThemeBackground();

        TextView title = makeText("AUDIO HUB", 27, white);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title);

        TextView subtitle = makeText("YOUR MUSIC. YOUR WORLD.", 11, purple);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout navigation = makeRow();
        navigation.setPadding(0, dp(7), 0, dp(3));
        String[] pages = {"Home", "Library", "Playlists", "AI DJ", "Themes"};
        String[] icons = {"⌂", "♫", "▤", "✦", "◐"};

        for (int i = 0; i < pages.length; i++) {
            final String page = pages[i];
            TextView button = makeText(icons[i] + "\n" + page, 10,
                page.equals("Home") ? purple : gray);
            button.setGravity(Gravity.CENTER);
            button.setTypeface(null, android.graphics.Typeface.BOLD);
            button.setPadding(dp(3), dp(8), dp(3), dp(8));
            button.setBackground(roundedCard(
                page.equals("Home") ? 0x332A1740 : 0x00171722,
                page.equals("Home") ? 0x885B3B79 : 0x00333333));
            button.setOnClickListener(v -> showPage(page));
            navigation.addView(button,
                new LinearLayout.LayoutParams(0, -2, 1));
        }
        root.addView(navigation);
        setContentView(root);

        showPage("Home");
    }

    void showPage(String page) {
        currentPage = page;
        content.removeAllViews();

        if (page.equals("Library")) {
            showLibrary();
        } else if (page.equals("Playlists")) {
            showPlaylists();
        } else if (page.equals("AI DJ")) {
            showDj();
        } else if (page.equals("Themes")) {
            showThemes();
        } else {
            showHome();
        }

        styleScreen();
    }


    void showThemes() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panel);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, -1));

        TextView heading = makeText("THEME STUDIO", 22, white);
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading);

        TextView info = makeText(
            "Choose your Audio Hub appearance", 13, gray);
        info.setGravity(Gravity.CENTER);
        panel.addView(info);

        String[] themes = {
            "Midnight Purple", "AMOLED Black", "Cyber Blue",
            "Crimson Red", "Pure Light", "Custom Theme"
        };

        for (String name : themes) {
            Button choice = makeButton(
                name.equals(themeManager.getTheme()) ? "✓ " + name : name);
            panel.addView(choice);
            choice.setOnClickListener(v -> {
                themeManager.setTheme(name);
                applyThemeColors();
                applyThemeBackground();
                showPage("Themes");
                Toast.makeText(this, name + " applied",
                    Toast.LENGTH_SHORT).show();
            });
        }

        Button photo = makeButton("CHOOSE BACKGROUND PHOTO");
        panel.addView(photo);
        photo.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("image/*");
            pick.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            );
            startActivityForResult(pick, THEME_PHOTO_REQUEST);
        });

        Button accent = makeButton("CUSTOM BUTTON COLOR");
        panel.addView(accent);
        accent.setOnClickListener(v -> chooseCustomAccent());

        Button bg = makeButton("CUSTOM BACKGROUND COLOR");
        panel.addView(bg);
        bg.setOnClickListener(v -> chooseCustomBackground());

        Button text = makeButton("CUSTOM TEXT COLOR");
        panel.addView(text);
        text.setOnClickListener(v -> chooseCustomText());

        TextView note = makeText(
            "Custom Theme lets you use a phone photo and choose colors.",
            12, gray);
        note.setGravity(Gravity.CENTER);
        panel.addView(note);
    }

    void chooseCustomAccent() {
        String[] names = {"Purple", "Cyan", "Red", "Gold", "Green", "Pink"};
        int[] colors = {
            0xFFA855F7, 0xFF00D9FF, 0xFFFF3B4F,
            0xFFFFC107, 0xFF4CAF50, 0xFFFF69B4
        };
        new AlertDialog.Builder(this)
            .setTitle("Button color")
            .setItems(names, (dialog, which) -> {
                themeManager.setCustomAccent(colors[which]);
                themeManager.setTheme("Custom Theme");
                applyThemeColors();
                applyThemeBackground();
                showPage("Themes");
            }).show();
    }

    void chooseCustomBackground() {
        String[] names = {"Dark", "Black", "Navy", "Purple", "Light"};
        int[] colors = {
            0xFF09090F, 0xFF000000, 0xFF06111F,
            0xFF24103D, 0xFFF5F5F7
        };
        new AlertDialog.Builder(this)
            .setTitle("Background color")
            .setItems(names, (dialog, which) -> {
                themeManager.setCustomBackground(colors[which]);
                themeManager.setPhoto("");
                themeManager.setTheme("Custom Theme");
                applyThemeColors();
                applyThemeBackground();
                showPage("Themes");
            }).show();
    }

    void chooseCustomText() {
        String[] names = {"White", "Soft White", "Gold", "Cyan", "Black"};
        int[] colors = {
            0xFFFFFFFF, 0xFFE5E5E5, 0xFFFFD700,
            0xFF00D9FF, 0xFF17171C
        };
        new AlertDialog.Builder(this)
            .setTitle("Text color")
            .setItems(names, (dialog, which) -> {
                themeManager.setCustomText(colors[which]);
                themeManager.setTheme("Custom Theme");
                applyThemeColors();
                applyThemeBackground();
                showPage("Themes");
            }).show();
    }

    void addPlayerControls() {
        nowPlaying = makeText("NOW PLAYING: Audio Hub", 13, purple);
        nowPlaying.setPadding(0, 8, 0, 8);
        content.addView(nowPlaying);

        playPauseButton = makeButton(isPlaying ? "PAUSE" : "RESUME");
        content.addView(playPauseButton);

        Button micButton = makeButton("🎙 VOICE COMMAND");
        content.addView(micButton);
        micButton.setOnClickListener(v -> startVoiceCommand());

        LinearLayout controls = makeRow();
        previousButton = makeButton("PREVIOUS");
        nextButton = makeButton("NEXT");

        controls.addView(previousButton,
            new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(nextButton,
            new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(controls);

        previousButton.setOnClickListener(v -> sendAction(PlaybackService.ACTION_PREVIOUS));
        nextButton.setOnClickListener(v -> sendAction(PlaybackService.ACTION_NEXT));

        playPauseButton.setOnClickListener(v ->
            sendAction(isPlaying
                ? PlaybackService.ACTION_PAUSE
                : PlaybackService.ACTION_RESUME));

        LinearLayout modes = makeRow();
        Button shuffleButton = makeButton(shuffle ? "SHUFFLE: ON" : "SHUFFLE: OFF");
        Button repeatButton = makeButton(repeat ? "REPEAT: ON" : "REPEAT: OFF");

        modes.addView(shuffleButton, new LinearLayout.LayoutParams(0, -2, 1));
        modes.addView(repeatButton, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(modes);

        shuffleButton.setOnClickListener(v -> {
            shuffle = !shuffle;
            updateModes();
            shuffleButton.setText(shuffle ? "SHUFFLE: ON" : "SHUFFLE: OFF");
        });

        repeatButton.setOnClickListener(v -> {
            repeat = !repeat;
            updateModes();
            repeatButton.setText(repeat ? "REPEAT: ON" : "REPEAT: OFF");
        });
    }

    void showHome() {
        TextView heading = makeText(
            "\nWELCOME TO AUDIO HUB\n\nYour music, your world.",
            20, white);
        heading.setGravity(Gravity.CENTER);
        content.addView(heading,
            new LinearLayout.LayoutParams(-1, 0, 1));

        addPlayerControls();

        Button scan = makeButton("SCAN MY MUSIC");
        content.addView(scan);
        scan.setOnClickListener(v -> checkAudioPermission());

        status = makeText(
            currentTracks.isEmpty()
                ? "Scan your phone to load your songs."
                : currentTracks.size() + " songs in your library",
            13, gray);
        status.setGravity(Gravity.CENTER);
        content.addView(status);
    }

    void showLibrary() {
        TextView heading = makeText("YOUR MUSIC LIBRARY", 19, white);
        heading.setTypeface(null, 1);
        heading.setPadding(0, 12, 0, 8);
        content.addView(heading);

        addPlayerControls();

        Button scanButton = makeButton("SCAN / REFRESH MUSIC");
        content.addView(scanButton);

        status = makeText("Long-press a song to add it to a playlist.", 12, gray);
        status.setPadding(0, 8, 0, 8);
        content.addView(status);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("Search songs or artists");
        searchInput.setHintTextColor(gray);
        searchInput.setTextColor(white);
        content.addView(searchInput);

        ScrollView scroll = new ScrollView(this);
        trackList = new LinearLayout(this);
        trackList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(trackList);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderTrackList(s.toString());
            }
            public void afterTextChanged(Editable e) {}
        });

        scanButton.setOnClickListener(v -> checkAudioPermission());
        renderTrackList("");
    }

    void checkAudioPermission() {
        String permission = Build.VERSION.SDK_INT >= 33
            ? Manifest.permission.READ_MEDIA_AUDIO
            : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{permission}, AUDIO_PERMISSION_REQUEST);
        } else {
            scanMusic();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == MIC_PERMISSION_REQUEST) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceCommand();
            } else {
                Toast.makeText(this, "Microphone permission denied",
                    Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (requestCode == AUDIO_PERMISSION_REQUEST) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                scanMusic();
            } else {
                Toast.makeText(this, "Music permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void startVoiceCommand() {
        if (!android.speech.SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition unavailable",
                Toast.LENGTH_LONG).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO},
                MIC_PERMISSION_REQUEST);
            return;
        }

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
            "Say Play, Pause, Next, Previous, or Search a song");
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        try {
            startActivityForResult(intent, MIC_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Voice command could not start",
                Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == THEME_PHOTO_REQUEST
                && resultCode == RESULT_OK && data != null
                && data.getData() != null) {
            Uri selectedPhoto = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(
                    selectedPhoto,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                );
                themeManager.setPhoto(selectedPhoto.toString());
                themeManager.setTheme("Custom Theme");
                applyThemeColors();
                applyThemeBackground();
                showPage("Themes");
                Toast.makeText(this, "Background photo saved",
                    Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Could not save selected photo",
                    Toast.LENGTH_LONG).show();
            }
        }

        if (requestCode == MIC_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results =
                data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

            if (results != null && !results.isEmpty()) {
                handleVoiceCommand(results.get(0));
            }
        }
    }

    void handleVoiceCommand(String command) {
        if (command == null) return;

        String spoken = command.trim().toLowerCase(Locale.ROOT);

        if (spoken.contains("pause") || spoken.contains("stop") ||
                spoken.contains("simama")) {
            sendAction(PlaybackService.ACTION_PAUSE);
            Toast.makeText(this, "Paused", Toast.LENGTH_SHORT).show();
            return;
        }

        if (spoken.equals("play") || spoken.equals("resume") ||
                spoken.equals("endelea") || spoken.equals("cheza")) {
            sendAction(PlaybackService.ACTION_RESUME);
            Toast.makeText(this, "Playing", Toast.LENGTH_SHORT).show();
            return;
        }

        if (spoken.contains("next") || spoken.contains("skip") ||
                spoken.contains("inayofuata")) {
            sendAction(PlaybackService.ACTION_NEXT);
            return;
        }

        if (spoken.contains("previous") || spoken.contains("back") ||
                spoken.contains("iliyopita")) {
            sendAction(PlaybackService.ACTION_PREVIOUS);
            return;
        }

        if (spoken.contains("shuffle") || spoken.contains("changanya")) {
            shuffle = !shuffle;
            updateModes();
            Toast.makeText(this, shuffle ? "Shuffle ON" : "Shuffle OFF",
                Toast.LENGTH_SHORT).show();
            return;
        }

        if (spoken.contains("repeat") || spoken.contains("rudia")) {
            repeat = !repeat;
            updateModes();
            Toast.makeText(this, repeat ? "Repeat ON" : "Repeat OFF",
                Toast.LENGTH_SHORT).show();
            return;
        }

        String query = spoken.replaceFirst(
            "^(play|search|find|tafuta|cheza)\\s*", "").trim();

        if (query.isEmpty()) {
            Toast.makeText(this, "Command not recognized",
                Toast.LENGTH_SHORT).show();
            return;
        }

        int match = -1;
        for (int i = 0; i < currentTracks.size(); i++) {
            AudioTrack track = currentTracks.get(i);
            String title = track.title == null ? "" :
                track.title.toLowerCase(Locale.ROOT);
            String artist = track.artist == null ? "" :
                track.artist.toLowerCase(Locale.ROOT);

            if (title.contains(query) || artist.contains(query)) {
                match = i;
                break;
            }
        }

        if (match >= 0) {
            playQueue(currentTracks, match);
            Toast.makeText(this, "Playing " + currentTracks.get(match).title,
                Toast.LENGTH_SHORT).show();
        } else {
            showPage("Library");
            if (searchInput != null) searchInput.setText(query);
            Toast.makeText(this, "Song not found. Showing search results.",
                Toast.LENGTH_LONG).show();
        }
    }

    void scanMusic() {
        if (status != null) status.setText("Scanning music...");

        new Thread(() -> {
            List<AudioTrack> tracks = AudioScanner.scan(this);
            runOnUiThread(() -> {
                currentTracks = tracks;
                if (status != null) status.setText(tracks.size() + " songs found");
                if (trackList != null) {
                    String query = searchInput == null
                        ? "" : searchInput.getText().toString();
                    renderTrackList(query);
                }
                Toast.makeText(this, tracks.size() + " songs found",
                    Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    void renderTrackList(String query) {
        if (trackList == null) return;
        trackList.removeAllViews();

        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;

        for (int i = 0; i < currentTracks.size(); i++) {
            final int index = i;
            AudioTrack track = currentTracks.get(i);
            String title = track.title == null ? "" : track.title;
            String artist = track.artist == null ? "" : track.artist;

            if (!title.toLowerCase(Locale.ROOT).contains(q) &&
                    !artist.toLowerCase(Locale.ROOT).contains(q)) continue;

            TextView item = makeText(
                title + "\n" + artist + "  •  " +
                formatDuration(track.duration), 14, white);
            item.setPadding(dp(15), dp(14), dp(15), dp(14));
            item.setBackground(roundedCard(0xCC171722, 0x554A3A62));

            item.setOnClickListener(v -> playQueue(currentTracks, index));
            item.setOnLongClickListener(v -> {
                addTrackToPlaylist(track);
                return true;
            });

            trackList.addView(item);
            View line = new View(this);
            line.setBackgroundColor(0xFF292936);
            trackList.addView(line,
                new LinearLayout.LayoutParams(-1, 1));
            shown++;
        }

        if (status != null) status.setText(shown + " songs shown • Long-press to add to playlist");

        if (shown == 0) {
            TextView empty = makeText(
                q.isEmpty() ? "No music loaded. Scan your music." : "No matching songs.",
                14, gray);
            empty.setPadding(0, 20, 0, 20);
            trackList.addView(empty);
        }
    }

    void playQueue(List<AudioTrack> tracks, int selectedIndex) {
        if (tracks == null || tracks.isEmpty()) return;

        ArrayList<Uri> uris = new ArrayList<>();
        ArrayList<String> titles = new ArrayList<>();
        ArrayList<String> artists = new ArrayList<>();

        for (AudioTrack track : tracks) {
            uris.add(track.uri);
            titles.add(track.title == null ? "Unknown title" : track.title);
            artists.add(track.artist == null ? "" : track.artist);
        }

        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PLAY);
        intent.putParcelableArrayListExtra(PlaybackService.EXTRA_URIS, uris);
        intent.putStringArrayListExtra(PlaybackService.EXTRA_TITLES, titles);
        intent.putStringArrayListExtra(PlaybackService.EXTRA_ARTISTS, artists);
        intent.putExtra(PlaybackService.EXTRA_INDEX, selectedIndex);

        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
        else startService(intent);
    }

    void sendAction(String action) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        startService(intent);
    }

    void updateModes() {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_MODES);
        intent.putExtra("shuffle", shuffle);
        intent.putExtra("repeat", repeat);
        startService(intent);
    }

    void showPlaylists() {
        TextView heading = makeText("MY PLAYLISTS", 20, white);
        heading.setPadding(0, 15, 0, 12);
        content.addView(heading);

        Button create = makeButton("＋ CREATE PLAYLIST");
        content.addView(create);
        create.setOnClickListener(v -> createPlaylistDialog());

        addPlayerControls();

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        List<String> names = playlistStore.names();
        if (names.isEmpty()) {
            list.addView(makeText("No playlists yet. Create your first playlist.",
                14, gray));
        }

        for (String name : names) {
            TextView item = makeText(
                "♫  " + name + "  (" + playlistStore.tracks(name).size() + " songs)",
                15, white);
            item.setPadding(12, 18, 12, 18);
            item.setOnClickListener(v -> openPlaylist(name));
            item.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setTitle("Delete " + name + "?")
                    .setMessage("This will delete the playlist, not the music.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("DELETE", (d, w) -> {
                        playlistStore.delete(name);
                        showPage("Playlists");
                    }).show();
                return true;
            });
            list.addView(item);
        }
    }

    void createPlaylistDialog() {
        EditText input = new EditText(this);
        input.setHint("Playlist name");

        new AlertDialog.Builder(this)
            .setTitle("Create playlist")
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CREATE", (d, w) -> {
                String name = input.getText().toString().trim();
                if (playlistStore.create(name)) {
                    Toast.makeText(this, "Playlist created", Toast.LENGTH_SHORT).show();
                    showPage("Playlists");
                } else {
                    Toast.makeText(this, "Name empty or already exists",
                        Toast.LENGTH_SHORT).show();
                }
            }).show();
    }

    void addTrackToPlaylist(AudioTrack track) {
        List<String> names = playlistStore.names();
        if (names.isEmpty()) {
            new AlertDialog.Builder(this)
                .setMessage("Create a playlist first?")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("CREATE", (d, w) -> createPlaylistDialog())
                .show();
            return;
        }

        String[] options = names.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle("Add " + track.title + " to...")
            .setItems(options, (d, which) -> {
                playlistStore.add(options[which], track.id);
                Toast.makeText(this, "Added to " + options[which],
                    Toast.LENGTH_SHORT).show();
            }).show();
    }

    void openPlaylist(String name) {
        content.removeAllViews();

        TextView heading = makeText(name, 20, white);
        heading.setPadding(0, 12, 0, 12);
        content.addView(heading);

        Button back = makeButton("BACK TO PLAYLISTS");
        content.addView(back);
        back.setOnClickListener(v -> showPage("Playlists"));

        addPlayerControls();

        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        List<Long> ids = playlistStore.tracks(name);
        List<AudioTrack> queue = new ArrayList<>();

        for (Long id : ids) {
            for (AudioTrack track : currentTracks) {
                if (track.id == id) {
                    queue.add(track);
                    break;
                }
            }
        }

        for (int i = 0; i < queue.size(); i++) {
            final int position = i;
            AudioTrack track = queue.get(i);
            TextView item = makeText(
                track.title + "\n" + track.artist, 14, white);
            item.setPadding(12, 15, 12, 15);
            item.setOnClickListener(v -> playQueue(queue, position));
            item.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this)
                    .setMessage("Remove this song from playlist?")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("REMOVE", (d, w) -> {
                        playlistStore.remove(name, track.id);
                        openPlaylist(name);
                    }).show();
                return true;
            });
            list.addView(item);
        }

        if (queue.isEmpty()) {
            list.addView(makeText(
                "No songs here. Scan music and long-press songs to add them.",
                14, gray));
        }
    }

    void showDj() {
        TextView heading = makeText("OFFLINE AI DJ", 21, white);
        heading.setPadding(0, 18, 0, 8);
        content.addView(heading);

        TextView info = makeText(
            "Create a mix from music stored on your phone. No internet required.",
            13, gray);
        content.addView(info);

        addPlayerControls();

        String[] moods = {"CHILL", "ENERGY", "ROMANTIC", "FOCUS"};
        for (String mood : moods) {
            Button button = makeButton("CREATE " + mood + " MIX");
            content.addView(button);
            button.setOnClickListener(v -> createDjMix(mood));
        }

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 15));

        TextView online = makeText("ONLINE AI DJ", 19, purple);
        content.addView(online);

        TextView onlineInfo = makeText(
            "ChatGPT-powered music recommendations will need an API key and internet connection. "
            + "Online chat is not active yet.",
            13, gray);
        content.addView(onlineInfo);
    }

    void createDjMix(String mood) {
        if (currentTracks.isEmpty()) {
            Toast.makeText(this, "Scan your music library first",
                Toast.LENGTH_SHORT).show();
            return;
        }

        List<Integer> indexes = OfflineDj.createMix(currentTracks, mood);
        List<AudioTrack> mix = new ArrayList<>();

        for (Integer index : indexes) {
            mix.add(currentTracks.get(index));
        }

        if (!mix.isEmpty()) {
            playQueue(mix, 0);
            Toast.makeText(this, mood + " mix: " + mix.size() + " songs",
                Toast.LENGTH_SHORT).show();
        }
    }

    String formatDuration(long milliseconds) {
        long seconds = milliseconds / 1000;
        return String.format(Locale.getDefault(),
            "%d:%02d", seconds / 60, seconds % 60);
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(playbackReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }
}
