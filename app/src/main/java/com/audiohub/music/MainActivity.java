package com.audiohub.music;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.graphics.Bitmap;
import android.widget.ImageView;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.Environment;
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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private MediaPlayer discoverPlayer;
    private Button activePreviewButton;
    private boolean discoverPreviewPrepared = false;

    private static final int AUDIO_PERMISSION_REQUEST = 100;
    private static final int MIC_PERMISSION_REQUEST = 102;
    private static final int MIC_REQUEST_CODE = 103;
    private static final int THEME_PHOTO_REQUEST = 104;

    int background = 0xFF09090F;
    int purple = 0xFFA855F7;
    int white = 0xFFFFFFFF;
    int gray = 0xFFA1A1AA;

    LinearLayout root, content, trackList;
    ScrollView libraryScroll;
    TextView status, nowPlaying, playerArtist;
    ImageView playerArtwork;
    Button playPauseButton, previousButton, nextButton;
    EditText searchInput;
    SeekBar playerSeekBar;
    TextView elapsedTimeText, durationTimeText;
    boolean userSeeking = false;
    LinearLayout appHeader, navigationBar, miniPlayer;
    ImageView miniArtwork;
    TextView miniTitle, miniArtist;
    Button miniPlayPauseButton, miniNextButton;

    List<AudioTrack> currentTracks = new ArrayList<>();
    int renderedTrackCount = 0;
    String currentTrackQuery = "";
    List<Integer> filteredTrackIndices = new ArrayList<>();
    PlaylistStore playlistStore;
    ThemeManager themeManager;

    int currentIndex = -1;
    boolean isPlaying = false;
    boolean shuffle = false;
    boolean repeat = false;
    boolean receiverRegistered = false;

    String currentPage = "Home";
    String previousPageForPlayer = "Home";

    final BroadcastReceiver playbackReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!PlaybackService.ACTION_UPDATE.equals(intent.getAction())) return;

            int previousTrackIndex = currentIndex;
            currentIndex = intent.getIntExtra("index", currentIndex);
            isPlaying = intent.getBooleanExtra("playing", false);
            String title = intent.getStringExtra("title");
            String artist = intent.getStringExtra("artist");

            if (miniTitle != null && miniArtist != null
                    && miniPlayPauseButton != null && miniPlayer != null) {
                miniTitle.setText(title == null || title.isEmpty()
                        ? "Nothing playing" : title);
                miniArtist.setText(artist == null || artist.isEmpty()
                        ? "AUDIO HUB" : artist);
                miniPlayPauseButton.setText(isPlaying ? "Ⅱ" : "▶");
                miniPlayer.setVisibility(
                        "Now Playing".equals(currentPage) ? View.GONE : View.VISIBLE);
            }
            if (currentIndex != previousTrackIndex) loadMiniArtwork();

            if (playerArtwork != null
                    && currentIndex != previousTrackIndex
                    && "Now Playing".equals(currentPage)) {
                loadCurrentArtwork();
            }


            int position = intent.getIntExtra("position", 0);
            int duration = intent.getIntExtra("duration", 0);

            if (playerSeekBar != null && duration > 0) {
                playerSeekBar.setMax(duration);
                if (!userSeeking) playerSeekBar.setProgress(position);
            }
            if (elapsedTimeText != null) {
                elapsedTimeText.setText(formatTime(position));
            }
            if (durationTimeText != null) {
                durationTimeText.setText(formatTime(duration));
            }

            if (nowPlaying != null) {
                nowPlaying.setText(
                    title == null || title.isEmpty()
                        ? "Nothing playing" : title
                );
            }
            if (playerArtist != null) {
                playerArtist.setText(
                    artist == null || artist.isEmpty()
                        ? "AUDIO HUB" : artist
                );
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

    String formatTime(int milliseconds) {
        int totalSeconds = Math.max(0, milliseconds / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

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
            Button b = (Button) view;
            boolean primary = b == playPauseButton;

            b.setAllCaps(false);
            b.setTextSize(12);
            b.setTypeface(null, android.graphics.Typeface.BOLD);
            b.setTextColor(primary ? 0xFF10130A : white);

            int[] colors = primary
                ? new int[]{0xFFD9FF58, 0xFFA5D52B}
                : new int[]{purple, darken(purple)};

            GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, colors);
            shape.setCornerRadius(dp(16));
            shape.setStroke(dp(1), primary ? 0x99FFFFFF : 0x66FFFFFF);
            b.setBackground(shape);
            b.setElevation(dp(3));

        } else if (view instanceof EditText) {
            EditText e = (EditText) view;
            e.setTextColor(white);
            e.setHintTextColor(gray);
            e.setPadding(dp(16), dp(13), dp(16), dp(13));
            e.setBackground(roundedCard(0xFF171722, 0x665B3B79));
        }

        if (view instanceof ScrollView) {
            ((ViewGroup) view).setClipToPadding(false);
            view.setPadding(0, dp(5), 0, dp(5));
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
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
        root.setPadding(dp(16), dp(10), dp(16), dp(8));
        applyThemeBackground();

        // Premium compact brand header
        appHeader = makeRow();
        LinearLayout header = appHeader;
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(3), dp(5), dp(3), dp(12));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);

        TextView brandName = makeText("AUDIO HUB", 19, white);
        brandName.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView brandCaption = makeText("MUSIC WITHOUT LIMITS", 9, purple);
        brandCaption.setTypeface(null, android.graphics.Typeface.BOLD);
        brandCaption.setPadding(0, dp(2), 0, 0);

        brand.addView(brandName);
        brand.addView(brandCaption);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView searchAction = makeText("⌕", 27, white);
        searchAction.setGravity(Gravity.CENTER);
        searchAction.setBackground(roundedCard(0xFF171722, 0x554A3A62));
        header.addView(searchAction,
                new LinearLayout.LayoutParams(dp(43), dp(43)));
        searchAction.setOnClickListener(v -> showPage("Library"));

        root.addView(header);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        createMiniPlayer();

        // Premium bottom navigation
        navigationBar = new LinearLayout(this);
        navigationBar.setOrientation(LinearLayout.VERTICAL);
        navigationBar.setPadding(dp(5), dp(8), dp(5), dp(3));
        navigationBar.setBackground(
                roundedCard(0xFF151320, 0x554A3A62));

        LinearLayout navigation = makeRow();
        navigation.setGravity(Gravity.CENTER_VERTICAL);

        String[] pages = {"Home", "Library", "Playlists", "AI DJ", "Assistant", "Discover", "Themes"};
        String[] icons = {"⌂", "♫", "▤", "✦", "🤖", "⌕", "◐"};

        for (int i = 0; i < pages.length; i++) {
            final String page = pages[i];
            final int position = i;

            LinearLayout navItem = new LinearLayout(this);
            navItem.setOrientation(LinearLayout.VERTICAL);
            navItem.setGravity(Gravity.CENTER);
            navItem.setPadding(dp(2), dp(5), dp(2), dp(5));

            boolean active = page.equals("Home");

            TextView icon = makeText(
                    icons[i], 21, active ? purple : gray);
            icon.setGravity(Gravity.CENTER);

            TextView label = makeText(
                    page.toUpperCase(Locale.ROOT), 8,
                    active ? white : gray);
            label.setGravity(Gravity.CENTER);
            label.setTypeface(null, android.graphics.Typeface.BOLD);
            label.setPadding(0, dp(3), 0, 0);

            View indicator = new View(this);
            indicator.setBackgroundColor(active ? purple : 0x00171722);

            navItem.addView(icon);
            navItem.addView(label);

            LinearLayout.LayoutParams indicatorParams =
                    new LinearLayout.LayoutParams(dp(17), dp(2));
            indicatorParams.setMargins(0, dp(5), 0, 0);
            navItem.addView(indicator, indicatorParams);

            navItem.setOnClickListener(v -> {
                showPage(page);

                for (int j = 0; j < navigation.getChildCount(); j++) {
                    LinearLayout child =
                            (LinearLayout) navigation.getChildAt(j);
                    boolean selected = j == position;

                    ((TextView) child.getChildAt(0)).setTextColor(
                            selected ? purple : gray);
                    ((TextView) child.getChildAt(1)).setTextColor(
                            selected ? white : gray);
                    child.getChildAt(2).setBackgroundColor(
                            selected ? purple : 0x00171722);
                }
            });

            navigation.addView(navItem,
                    new LinearLayout.LayoutParams(0, -2, 1));
        }

        navigationBar.addView(navigation);

        LinearLayout.LayoutParams navParams =
                new LinearLayout.LayoutParams(-1, -2);
        navParams.setMargins(0, dp(8), 0, 0);
        root.addView(navigationBar, navParams);

        setContentView(root);
        showPage("Home");
    }


    void createMiniPlayer() {
        miniPlayer = makeRow();
        miniPlayer.setGravity(Gravity.CENTER_VERTICAL);
        miniPlayer.setPadding(dp(10), dp(8), dp(9), dp(8));
        miniPlayer.setBackground(roundedCard(0xFF191522, 0x665B3B79));
        miniPlayer.setElevation(dp(7));

        miniArtwork = new ImageView(this);
        miniArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        miniArtwork.setBackground(roundedCard(0xFF39205F, 0xFF8050B8));
        miniArtwork.setClipToOutline(true);
        miniPlayer.addView(miniArtwork,
                new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        info.setPadding(dp(10), 0, dp(5), 0);

        miniTitle = makeText("Choose a song", 12, white);
        miniTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        miniTitle.setMaxLines(1);
        miniTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(miniTitle);

        miniArtist = makeText("AUDIO HUB", 10, gray);
        miniArtist.setMaxLines(1);
        miniArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(miniArtist);

        miniPlayer.addView(info,
                new LinearLayout.LayoutParams(0, -2, 1));

        miniPlayPauseButton = makeButton(isPlaying ? "Ⅱ" : "▶");
        miniPlayPauseButton.setTextSize(18);
        miniPlayer.addView(miniPlayPauseButton,
                new LinearLayout.LayoutParams(dp(43), dp(44)));

        miniNextButton = makeButton("⏭");
        miniNextButton.setTextSize(17);
        miniPlayer.addView(miniNextButton,
                new LinearLayout.LayoutParams(dp(43), dp(44)));

        miniPlayPauseButton.setOnClickListener(v ->
                sendAction(isPlaying
                        ? PlaybackService.ACTION_PAUSE
                        : PlaybackService.ACTION_RESUME));

        miniNextButton.setOnClickListener(v ->
                sendAction(PlaybackService.ACTION_NEXT));

        miniPlayer.setOnClickListener(v -> openNowPlaying());

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(7), 0, dp(2));
        root.addView(miniPlayer, params);
        miniPlayer.setVisibility(currentIndex < 0 ? View.GONE : View.VISIBLE);
    }

    void loadMiniArtwork() {
        if (miniArtwork == null || currentIndex < 0
                || currentIndex >= currentTracks.size()) return;

        AudioTrack track = currentTracks.get(currentIndex);
        Uri requestedUri = track.uri;
        if (requestedUri.equals(miniArtwork.getTag())) return;

        miniArtwork.setTag(requestedUri);
        miniArtwork.setImageDrawable(null);

        new Thread(() -> {
            Bitmap bitmap = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(getApplicationContext(), requestedUri);
                byte[] picture = retriever.getEmbeddedPicture();
                if (picture != null) {
                    bitmap = BitmapFactory.decodeByteArray(
                            picture, 0, picture.length);
                }
            } catch (Exception ignored) {
            } finally {
                try { retriever.release(); } catch (Exception ignored) {}
            }

            Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (miniArtwork != null
                        && requestedUri.equals(miniArtwork.getTag())
                        && result != null) {
                    miniArtwork.setImageBitmap(result);
                }
            });
        }).start();
    }

    void showPage(String page) {
        currentPage = page;

        boolean immersive = page.equals("Now Playing");
        if (appHeader != null) {
            appHeader.setVisibility(immersive ? View.GONE : View.VISIBLE);
        }
        if (navigationBar != null) {
            navigationBar.setVisibility(immersive ? View.GONE : View.VISIBLE);
        }
        if (miniPlayer != null) {
            miniPlayer.setVisibility(
                    immersive || currentIndex < 0 ? View.GONE : View.VISIBLE);
        }

        content.removeAllViews();

        if (page.equals("Now Playing")) {
            showNowPlaying();
        } else if (page.equals("Library")) {
            showLibrary();
        } else if (page.equals("Playlists")) {
            showPlaylists();
        } else if (page.equals("AI DJ")) {
            showDj();
        } else if (page.equals("Assistant")) {
            showAssistant();
        } else if (page.equals("Discover")) {
            showDiscover();
        } else if (page.equals("Themes")) {
            showThemes();
        } else {
            showHome();
        }

        styleScreen();
    }


    void openNowPlaying() {
        if (!"Now Playing".equals(currentPage)) {
            previousPageForPlayer = currentPage;
        }
        showPage("Now Playing");
    }

    void closeNowPlaying() {
        showPage(previousPageForPlayer == null ? "Home" : previousPageForPlayer);
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
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setBackground(roundedCard(0xFF151320, 0x995B3B79));
        card.setElevation(dp(4));

        TextView artwork = makeText("♫", 27, white);
        artwork.setGravity(Gravity.CENTER);
        artwork.setBackground(roundedCard(0xFF39205F, 0xFFB56CFF));
        card.addView(artwork, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(12), 0, dp(8), 0);

        String title = currentIndex >= 0 && currentIndex < currentTracks.size()
                ? currentTracks.get(currentIndex).title : "Choose a song";
        String artist = currentIndex >= 0 && currentIndex < currentTracks.size()
                ? currentTracks.get(currentIndex).artist : "AUDIO HUB";

        nowPlaying = makeText(title, 14, white);
        nowPlaying.setTypeface(null, android.graphics.Typeface.BOLD);
        nowPlaying.setMaxLines(1);
        nowPlaying.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(nowPlaying);

        playerArtist = makeText(artist, 11, gray);
        playerArtist.setMaxLines(1);
        playerArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        details.addView(playerArtist);

        card.addView(details, new LinearLayout.LayoutParams(0, -2, 1));

        playPauseButton = makeButton(isPlaying ? "Ⅱ" : "▶");
        playPauseButton.setTextSize(17);
        card.addView(playPauseButton,
                new LinearLayout.LayoutParams(dp(52), dp(48)));

        playPauseButton.setOnClickListener(v ->
                sendAction(isPlaying
                        ? PlaybackService.ACTION_PAUSE
                        : PlaybackService.ACTION_RESUME));

        card.setOnClickListener(v -> openNowPlaying());

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(12), 0, dp(14));
        content.addView(card, params);
    }


    void showNowPlaying() {
        LinearLayout player = new LinearLayout(this);
        player.setOrientation(LinearLayout.VERTICAL);
        player.setGravity(Gravity.CENTER_HORIZONTAL);
        player.setPadding(dp(24), dp(12), dp(24), dp(18));
        player.setBackground(new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF202020, 0xFF121212, 0xFF080808}));

        LinearLayout topBar = makeRow();
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        Button backButton = makeButton("⌄");
        backButton.setTextSize(27);
        backButton.setTextColor(white);
        backButton.setBackground(roundedCard(0xFF292929, 0x00292929));
        topBar.addView(backButton,
                new LinearLayout.LayoutParams(dp(46), dp(46)));
        backButton.setOnClickListener(v -> closeNowPlaying());

        LinearLayout topTitle = new LinearLayout(this);
        topTitle.setOrientation(LinearLayout.VERTICAL);
        topTitle.setGravity(Gravity.CENTER);

        TextView heading = makeText("PLAYING FROM YOUR LIBRARY", 10, 0xFFBDBDBD);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        topTitle.addView(heading);

        TextView subHeading = makeText("AUDIO HUB", 9, 0xFF888888);
        subHeading.setPadding(0, dp(3), 0, 0);
        topTitle.addView(subHeading);

        topBar.addView(topTitle,
                new LinearLayout.LayoutParams(0, -2, 1));

        Button queueButton = makeButton("☷");
        queueButton.setTextSize(23);
        queueButton.setTextColor(white);
        queueButton.setBackground(roundedCard(0xFF292929, 0x00292929));
        topBar.addView(queueButton,
                new LinearLayout.LayoutParams(dp(46), dp(46)));
        queueButton.setOnClickListener(v ->
                Toast.makeText(this, "Queue is managed from your library",
                        Toast.LENGTH_SHORT).show());

        player.addView(topBar);

        playerArtwork = new ImageView(this);
        playerArtwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        playerArtwork.setBackground(roundedCard(0xFF282828, 0x00282828));
        playerArtwork.setClipToOutline(true);
        playerArtwork.setElevation(dp(12));
        playerArtwork.setContentDescription("Album artwork");

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int artSize = Math.min(dp(330), screenWidth - dp(48));

        LinearLayout.LayoutParams artParams =
                new LinearLayout.LayoutParams(artSize, artSize);
        artParams.setMargins(0, dp(22), 0, dp(26));
        player.addView(playerArtwork, artParams);
        loadCurrentArtwork();

        String title = currentIndex >= 0 && currentIndex < currentTracks.size()
                ? currentTracks.get(currentIndex).title : "Choose a song";
        String artist = currentIndex >= 0 && currentIndex < currentTracks.size()
                ? currentTracks.get(currentIndex).artist : "AUDIO HUB";

        LinearLayout songInfo = makeRow();
        songInfo.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout songLabels = new LinearLayout(this);
        songLabels.setOrientation(LinearLayout.VERTICAL);

        nowPlaying = makeText(title, 22, white);
        nowPlaying.setTypeface(null, android.graphics.Typeface.BOLD);
        nowPlaying.setMaxLines(2);
        nowPlaying.setEllipsize(android.text.TextUtils.TruncateAt.END);
        songLabels.addView(nowPlaying);

        playerArtist = makeText(artist, 14, 0xFFB3B3B3);
        playerArtist.setPadding(0, dp(5), 0, 0);
        playerArtist.setMaxLines(1);
        playerArtist.setEllipsize(android.text.TextUtils.TruncateAt.END);
        songLabels.addView(playerArtist);

        songInfo.addView(songLabels,
                new LinearLayout.LayoutParams(0, -2, 1));

        TextView heart = makeText("♡", 30, 0xFFB56CFF);
        heart.setGravity(Gravity.CENTER);
        songInfo.addView(heart,
                new LinearLayout.LayoutParams(dp(44), dp(50)));
        player.addView(songInfo);

        playerSeekBar = new SeekBar(this);
        playerSeekBar.setMax(1);
        playerSeekBar.setProgress(0);
        playerSeekBar.setProgressTintList(
                android.content.res.ColorStateList.valueOf(0xFFB56CFF));
        playerSeekBar.setThumbTintList(
                android.content.res.ColorStateList.valueOf(white));

        LinearLayout.LayoutParams seekParams =
                new LinearLayout.LayoutParams(-1, dp(34));
        seekParams.setMargins(0, dp(12), 0, 0);
        player.addView(playerSeekBar, seekParams);

        LinearLayout timeRow = makeRow();
        timeRow.setGravity(Gravity.CENTER_VERTICAL);

        elapsedTimeText = makeText("00:00", 11, 0xFFB3B3B3);
        durationTimeText = makeText("00:00", 11, 0xFFB3B3B3);

        timeRow.addView(elapsedTimeText,
                new LinearLayout.LayoutParams(0, -2, 1));
        durationTimeText.setGravity(Gravity.RIGHT);
        timeRow.addView(durationTimeText,
                new LinearLayout.LayoutParams(0, -2, 1));
        player.addView(timeRow);

        playerSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress,
                                          boolean fromUser) {
                if (fromUser && elapsedTimeText != null) {
                    elapsedTimeText.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                Intent seekIntent = new Intent(MainActivity.this,
                        PlaybackService.class);
                seekIntent.setAction(PlaybackService.ACTION_SEEK);
                seekIntent.putExtra("position", bar.getProgress());
                startService(seekIntent);
            }
        });

        LinearLayout transport = makeRow();
        transport.setGravity(Gravity.CENTER);
        transport.setPadding(0, dp(18), 0, dp(12));

        previousButton = makeButton("⏮");
        playPauseButton = makeButton(isPlaying ? "Ⅱ" : "▶");
        nextButton = makeButton("⏭");

        previousButton.setTextSize(24);
        previousButton.setTextColor(white);
        previousButton.setBackground(roundedCard(0x00202020, 0x00202020));

        playPauseButton.setTextSize(27);
        playPauseButton.setTextColor(0xFF111111);
        GradientDrawable playCircle = new GradientDrawable();
        playCircle.setShape(GradientDrawable.OVAL);
        playCircle.setColor(0xFFFFFFFF);
        playPauseButton.setBackground(playCircle);
        playPauseButton.setElevation(dp(8));

        nextButton.setTextSize(24);
        nextButton.setTextColor(white);
        nextButton.setBackground(roundedCard(0x00202020, 0x00202020));

        transport.addView(previousButton,
                new LinearLayout.LayoutParams(0, dp(58), 1));
        transport.addView(playPauseButton,
                new LinearLayout.LayoutParams(dp(66), dp(66)));
        transport.addView(nextButton,
                new LinearLayout.LayoutParams(0, dp(58), 1));
        player.addView(transport);

        previousButton.setOnClickListener(v ->
                sendAction(PlaybackService.ACTION_PREVIOUS));
        nextButton.setOnClickListener(v ->
                sendAction(PlaybackService.ACTION_NEXT));
        playPauseButton.setOnClickListener(v ->
                sendAction(isPlaying
                        ? PlaybackService.ACTION_PAUSE
                        : PlaybackService.ACTION_RESUME));

        LinearLayout extras = makeRow();
        extras.setGravity(Gravity.CENTER);
        extras.setPadding(0, dp(8), 0, 0);

        Button micButton = makeButton("MIC");
        Button shuffleButton = makeButton(shuffle ? "SHUFFLE ON" : "SHUFFLE");
        Button repeatButton = makeButton(repeat ? "REPEAT ON" : "REPEAT");

        micButton.setTextSize(10);
        shuffleButton.setTextSize(10);
        repeatButton.setTextSize(10);

        micButton.setTextColor(0xFFBDBDBD);
        shuffleButton.setTextColor(shuffle ? 0xFFB56CFF : 0xFFBDBDBD);
        repeatButton.setTextColor(repeat ? 0xFFB56CFF : 0xFFBDBDBD);

        micButton.setBackground(roundedCard(0xFF242424, 0x00242424));
        shuffleButton.setBackground(roundedCard(
                shuffle ? 0xFF382448 : 0xFF242424, 0x00242424));
        repeatButton.setBackground(roundedCard(
                repeat ? 0xFF382448 : 0xFF242424, 0x00242424));

        extras.addView(micButton,
                new LinearLayout.LayoutParams(0, dp(42), 1));
        extras.addView(shuffleButton,
                new LinearLayout.LayoutParams(0, dp(42), 1.25f));
        extras.addView(repeatButton,
                new LinearLayout.LayoutParams(0, dp(42), 1.15f));
        player.addView(extras);

        micButton.setOnClickListener(v -> startVoiceCommand());

        shuffleButton.setOnClickListener(v -> {
            shuffle = !shuffle;
            updateModes();
            shuffleButton.setText(shuffle ? "SHUFFLE ON" : "SHUFFLE");
            shuffleButton.setTextColor(shuffle ? 0xFFB56CFF : 0xFFBDBDBD);
            shuffleButton.setBackground(roundedCard(
                    shuffle ? 0xFF382448 : 0xFF242424, 0x00242424));
        });

        repeatButton.setOnClickListener(v -> {
            repeat = !repeat;
            updateModes();
            repeatButton.setText(repeat ? "REPEAT ON" : "REPEAT");
            repeatButton.setTextColor(repeat ? 0xFFB56CFF : 0xFFBDBDBD);
            repeatButton.setBackground(roundedCard(
                    repeat ? 0xFF382448 : 0xFF242424, 0x00242424));
        });

        content.addView(player, new LinearLayout.LayoutParams(-1, -1));
    }

    Bitmap createArtworkFallback(String title) {
        int size = 600;
        Bitmap bitmap = Bitmap.createBitmap(size, size,
                Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas =
                new android.graphics.Canvas(bitmap);

        android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        paint.setShader(new android.graphics.LinearGradient(
                0, 0, size, size,
                new int[]{0xFF54258A, 0xFF21143D, 0xFF090914},
                null, android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, size, size, paint);
        paint.setShader(null);

        paint.setColor(0x33FFFFFF);
        canvas.drawCircle(480, 100, 190, paint);
        canvas.drawCircle(80, 520, 230, paint);

        paint.setColor(0x55B56CFF);
        canvas.drawCircle(300, 270, 145, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.create(
                "sans-serif", android.graphics.Typeface.BOLD));
        paint.setTextSize(190);
        canvas.drawText("♫", 300, 335, paint);

        paint.setTextSize(27);
        paint.setLetterSpacing(0.15f);
        canvas.drawText("A U D I O   H U B", 300, 510, paint);

        paint.setTextSize(20);
        paint.setColor(0xFFCFB9E8);
        String label = title == null ? "" : title.trim();
        if (label.length() > 30) label = label.substring(0, 27) + "...";
        canvas.drawText(label, 300, 555, paint);

        return bitmap;
    }

    void loadCurrentArtwork() {
        if (playerArtwork == null || currentIndex < 0
                || currentIndex >= currentTracks.size()) return;

        AudioTrack track = currentTracks.get(currentIndex);
        android.net.Uri requestedUri = track.uri;

        if (requestedUri.equals(playerArtwork.getTag())) return;

        playerArtwork.setTag(requestedUri);
        playerArtwork.setImageBitmap(createArtworkFallback(track.title));

        new Thread(() -> {
            Bitmap bitmap = null;
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();

            try {
                retriever.setDataSource(getApplicationContext(), requestedUri);
                byte[] picture = retriever.getEmbeddedPicture();

                if (picture != null) {
                    bitmap = BitmapFactory.decodeByteArray(
                            picture, 0, picture.length);
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {
                }
            }

            final Bitmap result = bitmap;
            runOnUiThread(() -> {
                if (playerArtwork != null
                        && requestedUri.equals(playerArtwork.getTag())
                        && result != null) {
                    playerArtwork.setImageBitmap(result);
                }
            });
        }).start();
    }

    void showHome() {
        ScrollView homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        homeScroll.setClipToPadding(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(2), dp(8), dp(2), dp(22));
        homeScroll.addView(page);

        content.addView(homeScroll,
                new LinearLayout.LayoutParams(-1, -1));

        LinearLayout top = makeRow();
        top.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout welcome = new LinearLayout(this);
        welcome.setOrientation(LinearLayout.VERTICAL);

        TextView greeting = makeText("GOOD TO SEE YOU", 10, purple);
        greeting.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView heading = makeText("Discover your sound.", 23, white);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(4), 0, 0);

        welcome.addView(greeting);
        welcome.addView(heading);

        TextView profile = makeText("♫", 24, purple);
        profile.setGravity(Gravity.CENTER);
        profile.setBackground(roundedCard(0xFF211A30, 0x665B3B79));

        top.addView(welcome,
                new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(profile,
                new LinearLayout.LayoutParams(dp(48), dp(48)));

        page.addView(top);

        TextView intro = makeText(
                "Your music, your mood, your world.",
                12, gray);
        intro.setPadding(0, dp(5), 0, dp(18));
        page.addView(intro);

        LinearLayout featured = new LinearLayout(this);
        featured.setOrientation(LinearLayout.VERTICAL);
        featured.setPadding(dp(18), dp(18), dp(18), dp(18));

        GradientDrawable featuredBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF39205F, 0xFF20203B, 0xFF11111B});
        featuredBg.setCornerRadius(dp(25));
        featuredBg.setStroke(dp(1), 0x665B4A91);
        featured.setBackground(featuredBg);

        LinearLayout featuredTop = makeRow();
        featuredTop.setGravity(Gravity.CENTER_VERTICAL);

        TextView featuredLabel = makeText(
                "✦  FEATURED FOR YOU", 10, 0xFFD9FF58);
        featuredLabel.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView featuredTag = makeText("AUDIO HUB", 9, 0xFFD0C8E0);
        featuredTag.setGravity(Gravity.CENTER);
        featuredTag.setPadding(dp(9), dp(5), dp(9), dp(5));
        featuredTag.setBackground(roundedCard(0x332A1740, 0x665B3B79));

        featuredTop.addView(featuredLabel,
                new LinearLayout.LayoutParams(0, -2, 1));
        featuredTop.addView(featuredTag);
        featured.addView(featuredTop);

        TextView featuredTitle = makeText(
                currentTracks.isEmpty()
                        ? "Your next\nfavourite starts here."
                        : "Your music.\nYour moment.",
                27, white);
        featuredTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        featuredTitle.setPadding(0, dp(24), 0, dp(7));
        featured.addView(featuredTitle);

        TextView featuredInfo = makeText(
                currentTracks.isEmpty()
                        ? "Build your collection and let the music flow."
                        : currentTracks.size() + " tracks waiting in your collection.",
                12, 0xFFD0C8E0);
        featured.addView(featuredInfo);

        Button discover = makeButton(
                currentTracks.isEmpty()
                        ? "＋  ADD YOUR MUSIC"
                        : "♫  EXPLORE YOUR LIBRARY");
        LinearLayout.LayoutParams discoverParams =
                new LinearLayout.LayoutParams(-1, -2);
        discoverParams.setMargins(0, dp(20), 0, 0);
        featured.addView(discover, discoverParams);
        discover.setOnClickListener(v -> {
            if (currentTracks.isEmpty()) {
                checkAudioPermission();
            } else {
                showPage("Library");
            }
        });

        LinearLayout.LayoutParams featuredParams =
                new LinearLayout.LayoutParams(-1, -2);
        featuredParams.setMargins(0, dp(4), 0, dp(20));
        page.addView(featured, featuredParams);

        TextView quickTitle = makeText("YOUR SPACE", 16, white);
        quickTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        page.addView(quickTitle);

        TextView quickSubtitle = makeText(
                "Everything you need, one tap away",
                11, gray);
        quickSubtitle.setPadding(0, dp(4), 0, dp(12));
        page.addView(quickSubtitle);

        LinearLayout shortcuts = makeRow();
        shortcuts.setGravity(Gravity.CENTER_VERTICAL);

        String[] shortcutNames = {
                "LIBRARY", "PLAYLISTS", "AI DJ"
        };
        String[] shortcutIcons = {
                "♫", "▤", "✦"
        };
        String[] shortcutPages = {
                "Library", "Playlists", "AI DJ"
        };

        for (int i = 0; i < shortcutNames.length; i++) {
            final String target = shortcutPages[i];

            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(5), dp(15), dp(5), dp(15));
            tile.setBackground(roundedCard(
                    0xFF171722, 0x554A3A62));

            TextView icon = makeText(shortcutIcons[i], 25, purple);
            icon.setGravity(Gravity.CENTER);

            TextView name = makeText(shortcutNames[i], 9, white);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(8), 0, 0);

            tile.addView(icon);
            tile.addView(name);

            LinearLayout.LayoutParams tileParams =
                    new LinearLayout.LayoutParams(0, -2, 1);
            tileParams.setMargins(dp(3), 0, dp(3), 0);
            shortcuts.addView(tile, tileParams);

            tile.setOnClickListener(v -> showPage(target));
        }

        page.addView(shortcuts);

        LinearLayout collectionHeading = makeRow();
        collectionHeading.setGravity(Gravity.CENTER_VERTICAL);
        collectionHeading.setPadding(0, dp(22), 0, dp(10));

        TextView collectionTitle = makeText(
                "IN YOUR COLLECTION", 16, white);
        collectionTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView collectionCount = makeText(
                currentTracks.size() + " TRACKS", 10, purple);
        collectionCount.setTypeface(null, android.graphics.Typeface.BOLD);

        collectionHeading.addView(collectionTitle,
                new LinearLayout.LayoutParams(0, -2, 1));
        collectionHeading.addView(collectionCount);
        page.addView(collectionHeading);

        if (currentTracks.isEmpty()) {
            TextView empty = makeText(
                    "Your music collection will appear here after scanning.",
                    12, gray);
            empty.setPadding(dp(15), dp(20), dp(15), dp(20));
            empty.setBackground(roundedCard(0xFF171722, 0x554A3A62));
            page.addView(empty);
        } else {
            int count = Math.min(4, currentTracks.size());

            for (int i = 0; i < count; i++) {
                final int index = i;
                AudioTrack track = currentTracks.get(i);

                String trackTitle = track.title == null
                        ? "Unknown title" : track.title;
                String trackArtist = track.artist == null
                        ? "Unknown artist" : track.artist;

                LinearLayout item = makeRow();
                item.setGravity(Gravity.CENTER_VERTICAL);
                item.setPadding(dp(10), dp(9), dp(10), dp(9));
                item.setBackground(roundedCard(
                        0xFF171722, 0x554A3A62));

                TextView artwork = makeText("♫", 22, purple);
                artwork.setGravity(Gravity.CENTER);
                artwork.setBackground(roundedCard(
                        0xFF29213B, 0x665B3B79));

                item.addView(artwork,
                        new LinearLayout.LayoutParams(dp(48), dp(48)));

                LinearLayout details = new LinearLayout(this);
                details.setOrientation(LinearLayout.VERTICAL);
                details.setPadding(dp(12), 0, dp(5), 0);

                TextView name = makeText(trackTitle, 13, white);
                name.setTypeface(null, android.graphics.Typeface.BOLD);
                name.setMaxLines(1);
                name.setEllipsize(android.text.TextUtils.TruncateAt.END);

                TextView artist = makeText(trackArtist, 10, gray);
                artist.setMaxLines(1);
                artist.setEllipsize(android.text.TextUtils.TruncateAt.END);
                artist.setPadding(0, dp(4), 0, 0);

                details.addView(name);
                details.addView(artist);

                item.addView(details,
                        new LinearLayout.LayoutParams(0, -2, 1));

                TextView play = makeText("▶", 16, purple);
                play.setGravity(Gravity.CENTER);
                item.addView(play,
                        new LinearLayout.LayoutParams(dp(30), -2));

                LinearLayout.LayoutParams itemParams =
                        new LinearLayout.LayoutParams(-1, -2);
                itemParams.setMargins(0, dp(4), 0, dp(4));
                page.addView(item, itemParams);

                item.setOnClickListener(v ->
                        playQueue(currentTracks, index));
            }
        }

        TextView playerHeading = makeText(
                "NOW PLAYING", 16, white);
        playerHeading.setTypeface(null, android.graphics.Typeface.BOLD);
        playerHeading.setPadding(0, dp(22), 0, dp(8));
        page.addView(playerHeading);

    }

    void showLibrary() {
        TextView heading = makeText("YOUR LIBRARY", 24, white);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(8), 0, dp(5));
        content.addView(heading);

        TextView subtitle = makeText(
            currentTracks.size() + " TRACKS  •  YOUR COLLECTION",
            10, purple);
        content.addView(subtitle);


        Button scanButton = makeButton("＋  SCAN / REFRESH MUSIC");
        content.addView(scanButton);

        status = makeText(
            "Tap a song to play  •  Long-press to add to playlist",
            11, gray);
        status.setPadding(0, dp(7), 0, dp(7));
        content.addView(status);

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setHint("⌕  Search songs or artists");
        searchInput.setHintTextColor(gray);
        searchInput.setTextColor(white);
        content.addView(searchInput);

        libraryScroll = new ScrollView(this);
        trackList = new LinearLayout(this);
        trackList.setOrientation(LinearLayout.VERTICAL);
        libraryScroll.addView(trackList);

        content.addView(libraryScroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

        libraryScroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            if (libraryScroll.getChildCount() > 0
                    && y + libraryScroll.getHeight()
                    >= libraryScroll.getChildAt(0).getHeight() - dp(350)) {
                appendTrackCards();
            }
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(
                CharSequence s, int start, int count, int after) {}

            public void onTextChanged(
                CharSequence s, int start, int before, int count) {
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

    String createGenrePlaylist(String genre) {
        if (currentTracks == null || currentTracks.isEmpty()) {
            return "Scan your music library first, then ask me again.";
        }

        String cleanGenre = genre.trim();
        String[] keywords = {
            "r&b", "rnb", "rhythm and blues", "soul", "neo soul",
            "slow jam", "slow jams", "usher", "sza", "bryson",
            "chris brown", "daniel caesar", "giveon", "frank ocean",
            "brent faiyaz", "summer walker", "ella mai", "ne-yo",
            "trey songz", "john legend", "alicia keys", "mary j blige",
            "toni braxton", "boyz ii men", "brian mcknight",
            "the weeknd", "jhené aiko"
        };

        List<AudioTrack> matches = new ArrayList<>();
        for (AudioTrack track : currentTracks) {
            String text = ((track.title == null ? "" : track.title) + " "
                    + (track.artist == null ? "" : track.artist) + " "
                    + (track.album == null ? "" : track.album))
                    .toLowerCase(Locale.ROOT);

            for (String keyword : keywords) {
                if (text.contains(keyword)) {
                    matches.add(track);
                    break;
                }
            }
        }

        if (matches.isEmpty()) {
            return "I couldn't identify R&B songs from your current music metadata. No playlist was created.";
        }

        String playlistName = cleanGenre;
        int suffix = 2;
        while (playlistStore.names().contains(playlistName)) {
            playlistName = cleanGenre + " " + suffix++;
        }

        if (!playlistStore.create(playlistName)) {
            return "I couldn't create the playlist. Please try again.";
        }

        for (AudioTrack track : matches) {
            playlistStore.add(playlistName, track.id);
        }

        return "Created " + playlistName + " with " + matches.size()
                + " songs. Open Playlists to find it.";
    }

    void handleVoiceCommand(String command) {
        if (command == null) return;

        String spoken = command.trim().toLowerCase(Locale.ROOT);

        if (spoken.contains("create") && spoken.contains("playlist")
                && (spoken.contains("r&b") || spoken.contains("rnb")
                || spoken.contains("r and b") || spoken.contains("rhythm and blues"))) {
            String result = createGenrePlaylist("R&B");
            Toast.makeText(this, result, Toast.LENGTH_LONG).show();
            return;
        }

        if (spoken.contains("pause") || spoken.contains("stop") ||
                spoken.contains("simama")) {
            sendAction(PlaybackService.ACTION_PAUSE);
            Toast.makeText(this, "Paused", Toast.LENGTH_SHORT).show();
            return;
        }

        if (spoken.equals("play") || spoken.equals("resume") ||
                spoken.equals("endelea") || spoken.equals("cheza") ||
                spoken.equals("play music") || spoken.equals("cheza muziki") ||
                spoken.equals("fungua muziki")) {
            sendAction(PlaybackService.ACTION_RESUME);
            Toast.makeText(this, "Playing", Toast.LENGTH_SHORT).show();
            return;
        }

        if (spoken.contains("next") || spoken.contains("skip") ||
                spoken.contains("inayofuata") ||
                spoken.contains("wimbo mwingine")) {
            sendAction(PlaybackService.ACTION_NEXT);
            return;
        }

        if (spoken.contains("previous") || spoken.contains("back") ||
                spoken.contains("iliyopita") ||
                spoken.contains("wimbo uliopita")) {
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
        currentTrackQuery = query == null
                ? "" : query.trim().toLowerCase(Locale.ROOT);
        renderedTrackCount = 0;

        filteredTrackIndices.clear();

        for (int i = 0; i < currentTracks.size(); i++) {
            AudioTrack track = currentTracks.get(i);
            String title = track.title == null
                    ? "Unknown title" : track.title;
            String artist = track.artist == null
                    ? "Unknown artist" : track.artist;

            if (title.toLowerCase(Locale.ROOT).contains(currentTrackQuery)
                    || artist.toLowerCase(Locale.ROOT).contains(currentTrackQuery)) {
                filteredTrackIndices.add(i);
            }
        }

        if (libraryScroll != null) libraryScroll.scrollTo(0, 0);

        if (status != null) {
            status.setText(filteredTrackIndices.size()
                    + " songs  •  Tap to play  •  Hold for playlist");
        }

        if (filteredTrackIndices.isEmpty()) {
            TextView empty = makeText(
                    currentTrackQuery.isEmpty()
                            ? "No music loaded. Scan your phone to begin."
                            : "No matching songs found.", 14, gray);
            empty.setPadding(0, dp(22), 0, dp(22));
            trackList.addView(empty);
            return;
        }

        appendTrackCards();
    }

    void appendTrackCards() {
        if (trackList == null || filteredTrackIndices == null) return;

        int end = Math.min(renderedTrackCount + 60,
                filteredTrackIndices.size());

        for (int position = renderedTrackCount; position < end; position++) {
            final int index = filteredTrackIndices.get(position);
            AudioTrack track = currentTracks.get(index);

            String title = track.title == null
                    ? "Unknown title" : track.title;
            String artist = track.artist == null
                    ? "Unknown artist" : track.artist;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(12), dp(11), dp(12), dp(11));
            card.setBackground(roundedCard(0xFF171722, 0x554A3A62));

            LinearLayout.LayoutParams cardParams =
                    new LinearLayout.LayoutParams(-1, -2);
            cardParams.setMargins(0, dp(5), 0, dp(5));

            TextView artwork = makeText("♫", 25, purple);
            artwork.setGravity(Gravity.CENTER);
            artwork.setTypeface(null, android.graphics.Typeface.BOLD);
            artwork.setBackground(roundedCard(0xFF29213B, 0x665B3B79));
            card.addView(artwork,
                    new LinearLayout.LayoutParams(dp(54), dp(54)));

            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setGravity(Gravity.CENTER_VERTICAL);
            details.setPadding(dp(13), 0, dp(5), 0);

            TextView name = makeText(title, 14, white);
            name.setTypeface(null, android.graphics.Typeface.BOLD);
            name.setMaxLines(1);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);

            TextView artistName = makeText(artist, 11, gray);
            artistName.setMaxLines(1);
            artistName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            artistName.setPadding(0, dp(4), 0, 0);

            details.addView(name);
            details.addView(artistName);
            card.addView(details,
                    new LinearLayout.LayoutParams(0, -2, 1));

            LinearLayout right = new LinearLayout(this);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.CENTER);

            TextView duration = makeText(
                    formatDuration(track.duration), 10, gray);
            TextView play = makeText("▶", 15, purple);
            play.setGravity(Gravity.CENTER);
            play.setPadding(0, dp(7), 0, 0);

            right.addView(duration);
            right.addView(play);
            card.addView(right,
                    new LinearLayout.LayoutParams(dp(43), -2));

            trackList.addView(card, cardParams);
            card.setOnClickListener(v -> playQueue(currentTracks, index));
            card.setOnLongClickListener(v -> {
                addTrackToPlaylist(track);
                return true;
            });
        }

        renderedTrackCount = end;
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
        TextView heading = makeText("YOUR PLAYLISTS", 24, white);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(10), 0, dp(8));
        content.addView(heading);

        Button create = makeButton("＋  CREATE PLAYLIST");
        content.addView(create);
        create.setOnClickListener(v -> createPlaylistDialog());


        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        content.addView(scroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

        List<String> names = playlistStore.names();

        if (names.isEmpty()) {
            list.addView(makeText(
                "Your playlists will appear here.\nCreate your first collection.",
                14, gray));
        }

        for (String name : names) {
            TextView item = makeText(
                "♫   " + name + "\n"
                    + playlistStore.tracks(name).size() + " songs  •  OPEN",
                14, white);

            item.setPadding(dp(16), dp(17), dp(12), dp(17));
            item.setBackground(roundedCard(0xFF171722, 0x554A3A62));

            LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(4), 0, dp(4));
            list.addView(item, params);

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

        TextView heading = makeText(name, 23, white);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(10), 0, dp(10));
        content.addView(heading);

        Button back = makeButton("←  BACK TO PLAYLISTS");
        content.addView(back);
        back.setOnClickListener(v -> showPage("Playlists"));


        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        content.addView(scroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

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
                "♫   " + track.title + "\n" + track.artist,
                14, white);
            item.setPadding(dp(15), dp(15), dp(12), dp(15));
            item.setBackground(roundedCard(0xFF171722, 0x554A3A62));

            LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(0, dp(4), 0, dp(4));
            list.addView(item, params);

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
        }

        if (queue.isEmpty()) {
            list.addView(makeText(
                "No songs here yet. Add music from your Library.",
                14, gray));
        }
    }

    void showAssistant() {
        content.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout header = makeRow();
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView heading = makeText("JARVIS", 25, white);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));

        TextView status = makeText("● ONLINE LOCALLY", 10, purple);
        header.addView(status);
        content.addView(header);

        TextView subtitle = makeText(
                "Your personal music assistant", 12, gray);
        subtitle.setPadding(0, 0, 0, dp(14));
        content.addView(subtitle);

        ScrollView chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setClipToPadding(false);

        LinearLayout chatLog = new LinearLayout(this);
        chatLog.setOrientation(LinearLayout.VERTICAL);
        chatLog.setPadding(dp(2), dp(12), dp(2), dp(16));
        chatScroll.addView(chatLog);

        content.addView(chatScroll,
                new LinearLayout.LayoutParams(-1, 0, 1));

        TextView welcome = makeText(
                "Hello! I'm JARVIS. ✦\n\n"
                + "I can help you control your music, find songs "
                + "and create playlists.\n\n"
                + "Habari! Niambie nikusaidie nini leo.",
                15, white);
        welcome.setPadding(dp(16), dp(16), dp(16), dp(16));
        welcome.setBackground(roundedCard(0xFF211D30, 0x554A3A62));
        chatLog.addView(welcome);

        TextView suggestions = makeText(
                "TRY ASKING", 11, gray);
        suggestions.setTypeface(null, android.graphics.Typeface.BOLD);
        suggestions.setPadding(dp(2), dp(20), 0, dp(8));
        chatLog.addView(suggestions);

        String[] prompts = {
                "Create an R&B playlist",
                "Shuffle my music",
                "Play music",
                "Play next song"
        };

        EditText message = new EditText(this);
        message.setSingleLine(true);
        message.setTextColor(white);
        message.setHintTextColor(gray);
        message.setHint("Message JARVIS...");
        message.setTextSize(14);
        message.setPadding(dp(14), 0, dp(14), 0);
        message.setBackground(roundedCard(0xFF211D30, 0x665B3B79));

        LinearLayout inputRow = makeRow();
        inputRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(4), dp(5), dp(4), dp(5));
        inputRow.setBackground(roundedCard(0xFF171421, 0x665B3B79));

        Button send = makeButton("➤");
        inputRow.addView(message,
                new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams sendParams =
                new LinearLayout.LayoutParams(dp(52), dp(48));
        sendParams.setMargins(dp(8), 0, 0, 0);
        inputRow.addView(send, sendParams);

        Runnable[] submit = new Runnable[1];
        submit[0] = () -> {
            String command = message.getText().toString().trim();
            if (command.isEmpty()) return;
            message.setText("");

            TextView userBubble = makeText(command, 14, white);
            userBubble.setPadding(dp(14), dp(12), dp(14), dp(12));
            userBubble.setBackground(
                    roundedCard(0xFF49336D, 0x665B3B79));
            LinearLayout.LayoutParams up =
                    new LinearLayout.LayoutParams(-2, -2);
            up.gravity = android.view.Gravity.RIGHT;
            up.setMargins(dp(36), dp(12), 0, dp(5));
            chatLog.addView(userBubble, up);

            String spoken = command.toLowerCase(Locale.ROOT);
            String reply;

            boolean createRnb = spoken.contains("playlist")
                    && (spoken.contains("create") || spoken.contains("make")
                    || spoken.contains("tengeneza"))
                    && (spoken.contains("r&b") || spoken.contains("rnb")
                    || spoken.contains("r and b")
                    || spoken.contains("rhythm and blues"));

            if (createRnb) {
                reply = createGenrePlaylist("R&B");
            } else {
                handleVoiceCommand(command);

                if (spoken.contains("pause") || spoken.contains("stop")
                        || spoken.contains("simama")
                        || spoken.contains("simamisha")
                        || spoken.contains("sitisha")) {
                    reply = "Sawa! Nimesitisha muziki. ⏸";
                } else if (spoken.equals("play")
                        || spoken.equals("resume")
                        || spoken.equals("endelea")
                        || spoken.equals("cheza")
                        || spoken.equals("play music")
                        || spoken.equals("cheza muziki")) {
                    reply = "Sawa! Tuendelee kusikiliza. ▶";
                } else if (spoken.contains("next")
                        || spoken.contains("skip")
                        || spoken.contains("inayofuata")) {
                    reply = "Sawa! Wimbo unaofuata. ⏭";
                } else if (spoken.contains("previous")
                        || spoken.contains("back")
                        || spoken.contains("iliyopita")) {
                    reply = "Tunarudi kwenye wimbo uliopita. ⏮";
                } else if (spoken.contains("shuffle")
                        || spoken.contains("changanya")) {
                    reply = "Nimebadilisha hali ya Shuffle.";
                } else if (spoken.contains("repeat")
                        || spoken.contains("rudia")) {
                    reply = "Nimebadilisha hali ya Repeat.";
                } else {
                    reply = "Nimepokea amri yako. Kwa sasa naweza "
                            + "kudhibiti muziki na kutengeneza playlists.";
                }
            }

            TextView botBubble = makeText(reply, 14, white);
            botBubble.setPadding(dp(14), dp(12), dp(14), dp(12));
            botBubble.setBackground(
                    roundedCard(0xFF211D30, 0x554A3A62));
            LinearLayout.LayoutParams bp =
                    new LinearLayout.LayoutParams(-2, -2);
            bp.setMargins(0, dp(5), dp(36), dp(8));
            chatLog.addView(botBubble, bp);

            chatScroll.post(() ->
                    chatScroll.fullScroll(View.FOCUS_DOWN));
        };

        send.setOnClickListener(v -> submit[0].run());
        message.setOnEditorActionListener((v, actionId, event) -> {
            submit[0].run();
            return true;
        });

        for (String prompt : prompts) {
            Button chip = makeButton(prompt);
            chip.setTextSize(12);
            LinearLayout.LayoutParams cp =
                    new LinearLayout.LayoutParams(-1, dp(42));
            cp.setMargins(0, 0, 0, dp(7));
            chatLog.addView(chip, cp);
            chip.setOnClickListener(v -> {
                message.setText(prompt);
                submit[0].run();
            });
        }

        content.addView(inputRow);

        TextView offline = makeText(
                "✦  JARVIS LOCAL ASSISTANT  •  OFFLINE",
                10, purple);
        offline.setGravity(android.view.Gravity.CENTER);
        offline.setPadding(0, dp(10), 0, 0);
        content.addView(offline);
    }

    void showDiscover() {
        content.setPadding(dp(16), dp(14), dp(16), dp(12));

        TextView heading = makeText("Discover Music", 25, white);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(heading);

        TextView subtitle = makeText(
            "Search songs, artists and albums from around the world.",
            12, gray
        );
        subtitle.setPadding(0, dp(5), 0, dp(16));
        content.addView(subtitle);

        LinearLayout searchRow = makeRow();
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setPadding(dp(5), dp(4), dp(5), dp(4));
        searchRow.setBackground(roundedCard(0xFF171421, 0x665B3B79));

        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setTextColor(white);
        query.setHintTextColor(gray);
        query.setHint("Song, artist or album...");
        query.setTextSize(14);
        query.setPadding(dp(12), 0, dp(8), 0);
        query.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        searchRow.addView(query,
            new LinearLayout.LayoutParams(0, dp(50), 1));

        Button search = makeButton("SEARCH");
        search.setTextSize(11);
        searchRow.addView(search,
            new LinearLayout.LayoutParams(dp(94), dp(46)));

        content.addView(searchRow);

        TextView note = makeText(
            "iTunes offers short previews. Internet Archive provides full-length CC0 tracks for streaming and download.",
            11, gray
        );
        note.setPadding(dp(2), dp(12), dp(2), dp(12));
        content.addView(note);

        ScrollView resultsScroll = new ScrollView(this);
        resultsScroll.setFillViewport(true);

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(0, dp(4), 0, dp(12));
        resultsScroll.addView(results);

        content.addView(resultsScroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

        TextView empty = makeText(
            "♫  Search for music to discover songs worldwide.",
            13, gray
        );
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(12), dp(35), dp(12), dp(35));
        results.addView(empty);

        search.setOnClickListener(v -> {
            String term = query.getText().toString().trim();
            if (term.isEmpty()) {
                Toast.makeText(this, "Enter a song or artist name",
                    Toast.LENGTH_SHORT).show();
                return;
            }
            searchOnlineMusic(term, results, search);
        });

        query.setOnEditorActionListener((v, actionId, event) -> {
            search.performClick();
            return true;
        });
    }


    void searchOnlineMusic(String term, LinearLayout results, Button search) {
        results.removeAllViews();

        TextView loading = makeText("Searching online music catalogue...", 13, gray);
        loading.setGravity(Gravity.CENTER);
        loading.setPadding(dp(10), dp(25), dp(10), dp(25));
        results.addView(loading);
        search.setEnabled(false);

        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                String encoded = URLEncoder.encode(term, "UTF-8");
                URL url = new URL(
                    "https://itunes.apple.com/search?term=" + encoded
                    + "&entity=song&limit=20"
                );

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("User-Agent", "AudioHub/1.0");

                int responseCode = connection.getResponseCode();
                if (responseCode != 200) {
                    throw new Exception("Online search failed. HTTP " + responseCode);
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                JSONObject data = new JSONObject(response.toString());
                JSONArray tracks = data.optJSONArray("results");

                runOnUiThread(() -> {
                    results.removeAllViews();

                    if (tracks == null || tracks.length() == 0) {
                        TextView none = makeText(
                            "No songs found. Try another song or artist.",
                            13, gray
                        );
                        none.setGravity(Gravity.CENTER);
                        none.setPadding(dp(10), dp(25), dp(10), dp(25));
                        results.addView(none);
                        searchInternetArchive(term, results);
                        return;
                    }

                    for (int i = 0; i < tracks.length(); i++) {
                        JSONObject track = tracks.optJSONObject(i);
                        if (track == null) continue;

                        String title = track.optString("trackName", "Unknown song");
                        String artist = track.optString("artistName", "Unknown artist");
                        String album = track.optString("collectionName", "Album unavailable");
                        String previewUrl = track.optString("previewUrl", "");
                        String trackUrl = track.optString("trackViewUrl", "");

                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setPadding(dp(13), dp(11), dp(13), dp(11));
                        card.setBackground(roundedCard(0xFF171421, 0x443F3155));

                        LinearLayout.LayoutParams cardParams =
                            new LinearLayout.LayoutParams(-1, -2);
                        cardParams.setMargins(0, 0, 0, dp(9));
                        results.addView(card, cardParams);

                        TextView songTitle = makeText(title, 15, white);
                        songTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                        card.addView(songTitle);

                        TextView details = makeText(
                            artist + "  •  " + album, 11, gray
                        );
                        details.setPadding(0, dp(4), 0, dp(10));
                        card.addView(details);

                        LinearLayout actions = makeRow();
                        actions.setGravity(Gravity.CENTER_VERTICAL);
                        card.addView(actions);

                        Button preview = makeButton("PLAY");
                        preview.setTextSize(10);
                        preview.setEnabled(!previewUrl.isEmpty());
                        actions.addView(preview,
                            new LinearLayout.LayoutParams(0, dp(40), 1));

                        Button open = makeButton("OPEN");
                        open.setTextSize(10);
                        LinearLayout.LayoutParams openParams =
                            new LinearLayout.LayoutParams(0, dp(40), 1);
                        openParams.setMargins(dp(7), 0, 0, 0);
                        actions.addView(open, openParams);

                        preview.setOnClickListener(v -> {
                            if (previewUrl.isEmpty()) return;

                            if (activePreviewButton == preview
                                    && discoverPlayer != null) {
                                try {
                                    if (discoverPlayer.isPlaying()) {
                                        discoverPlayer.pause();
                                        preview.setText("PLAY");
                                    } else if (discoverPreviewPrepared) {
                                        discoverPlayer.start();
                                        preview.setText("PAUSE");
                                    }
                                } catch (IllegalStateException ignored) {
                                    preview.setText("PLAY");
                                }
                                return;
                            }

                            if (discoverPlayer != null) {
                                try {
                                    discoverPlayer.release();
                                } catch (Exception ignored) {}
                                discoverPlayer = null;
                            }

                            if (activePreviewButton != null) {
                                activePreviewButton.setText("PLAY");
                            }

                            activePreviewButton = preview;
                            discoverPreviewPrepared = false;
                            preview.setText("LOADING");

                            try {
                                discoverPlayer = new MediaPlayer();
                                discoverPlayer.setAudioStreamType(
                                    android.media.AudioManager.STREAM_MUSIC
                                );
                                discoverPlayer.setDataSource(previewUrl);

                                discoverPlayer.setOnPreparedListener(mp -> {
                                    discoverPreviewPrepared = true;
                                    mp.start();
                                    preview.setText("PAUSE");
                                });

                                discoverPlayer.setOnCompletionListener(mp -> {
                                    preview.setText("PLAY");
                                    discoverPreviewPrepared = false;
                                    discoverPlayer = null;
                                    activePreviewButton = null;
                                    mp.release();
                                });

                                discoverPlayer.setOnErrorListener((mp, what, extra) -> {
                                    preview.setText("PLAY");
                                    discoverPreviewPrepared = false;
                                    activePreviewButton = null;
                                    discoverPlayer = null;
                                    mp.release();
                                    Toast.makeText(this, "Preview playback failed",
                                        Toast.LENGTH_SHORT).show();
                                    return true;
                                });

                                discoverPlayer.prepareAsync();
                            } catch (Exception e) {
                                preview.setText("PLAY");
                                discoverPreviewPrepared = false;
                                activePreviewButton = null;
                                if (discoverPlayer != null) {
                                    discoverPlayer.release();
                                    discoverPlayer = null;
                                }
                                Toast.makeText(this, "Cannot play preview",
                                    Toast.LENGTH_SHORT).show();
                            }
                        });

                        open.setOnClickListener(v -> {
                            if (!trackUrl.isEmpty()) {
                                try {
                                    startActivity(new android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(trackUrl)
                                    ));
                                } catch (Exception e) {
                                    Toast.makeText(this, "Cannot open song page",
                                        Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                });

            } catch (Exception e) {
                String error = e.getMessage() == null
                    ? "Check your internet connection and try again."
                    : e.getMessage();

                runOnUiThread(() -> {
                    results.removeAllViews();
                    TextView failed = makeText(
                        "Search failed: " + error, 12, gray
                    );
                    failed.setPadding(dp(8), dp(20), dp(8), dp(20));
                    results.addView(failed);
                    searchInternetArchive(term, results);
                });

            } finally {
                if (connection != null) connection.disconnect();
                runOnUiThread(() -> search.setEnabled(true));
            }
        }).start();
    }

    void downloadArchiveTrack(String title, String url) {
        try {
            DownloadManager manager = (DownloadManager)
                getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                Toast.makeText(this, "Download service unavailable",
                    Toast.LENGTH_SHORT).show();
                return;
            }

            DownloadManager.Request request =
                new DownloadManager.Request(Uri.parse(url));
            request.setTitle(title);
            request.setDescription("Downloading from Internet Archive (CC0)");
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            manager.enqueue(request);

            Toast.makeText(this, "Download started: " + title,
                Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Download failed: " + e.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }

    void searchInternetArchive(String term, LinearLayout results) {
        new Thread(() -> {
            try {
                String safeTerm = term.replaceAll("[^A-Za-z0-9 ]", " ")
                    .trim().replaceAll("\\s+", " ");
                String query = "mediatype:audio AND licenseurl:"
                    + "\"http://creativecommons.org/publicdomain/zero/1.0/\""
                    + " AND (title:(" + safeTerm + ") OR creator:("
                    + safeTerm + "))";
                String api = "https://archive.org/advancedsearch.php?q="
                    + URLEncoder.encode(query, "UTF-8")
                    + "&fl[]=identifier&rows=12&output=json";

                JSONObject searchData = readArchiveJson(api);
                JSONObject response = searchData.optJSONObject("response");
                JSONArray docs = response == null ? null
                    : response.optJSONArray("docs");

                ArrayList<JSONObject> found = new ArrayList<>();
                if (docs != null) {
                    for (int i = 0; i < docs.length(); i++) {
                        JSONObject doc = docs.optJSONObject(i);
                        if (doc == null) continue;
                        String id = doc.optString("identifier", "");
                        if (id.isEmpty()) continue;

                        JSONObject metadataData = readArchiveJson(
                            "https://archive.org/metadata/"
                                + URLEncoder.encode(id, "UTF-8")
                        );
                        JSONObject metadata =
                            metadataData.optJSONObject("metadata");
                        if (metadata == null) continue;

                        String license = metadata.optString("licenseurl", "");
                        if (!license.equals(
                            "http://creativecommons.org/publicdomain/zero/1.0/"
                        )) continue;

                        JSONArray files = metadataData.optJSONArray("files");
                        if (files == null) continue;
                        for (int j = 0; j < files.length(); j++) {
                            JSONObject file = files.optJSONObject(j);
                            if (file == null) continue;
                            String name = file.optString("name", "");
                            if (!name.toLowerCase(Locale.ROOT).endsWith(".mp3")
                                    || file.has("private")) continue;

                            JSONObject item = new JSONObject();
                            item.put("id", id);
                            item.put("title", metadata.optString("title", id));
                            item.put("creator", metadata.optString("creator",
                                "Unknown artist"));
                            item.put("file", name);
                            found.add(item);
                            break;
                        }
                    }
                }

                runOnUiThread(() -> {
                    if (found.isEmpty()) {
                        TextView none = makeText(
                            "No CC0 full-length MP3 tracks found for this search.",
                            12, gray);
                        none.setPadding(dp(8), dp(12), dp(8), dp(12));
                        results.addView(none);
                        return;
                    }
                    TextView heading = makeText(
                        "INTERNET ARCHIVE • CC0 FULL TRACKS", 13, purple);
                    heading.setTypeface(null, android.graphics.Typeface.BOLD);
                    heading.setPadding(0, dp(14), 0, dp(9));
                    results.addView(heading);

                    for (JSONObject item : found) {
                        String title = item.optString("title", "Unknown track");
                        String creator = item.optString("creator", "Unknown artist");
                        String id = item.optString("id", "");
                        String file = item.optString("file", "");
                        String filePath = Uri.encode(file).replace("+", "%20");
                        String audioUrl = "https://archive.org/download/"
                            + Uri.encode(id) + "/" + filePath;

                        LinearLayout card = new LinearLayout(this);
                        card.setOrientation(LinearLayout.VERTICAL);
                        card.setPadding(dp(13), dp(11), dp(13), dp(11));
                        card.setBackground(roundedCard(0xFF171421, 0x443F3155));
                        LinearLayout.LayoutParams params =
                            new LinearLayout.LayoutParams(-1, -2);
                        params.setMargins(0, 0, 0, dp(9));
                        results.addView(card, params);

                        TextView name = makeText(title, 15, white);
                        name.setTypeface(null, android.graphics.Typeface.BOLD);
                        card.addView(name);
                        TextView details = makeText(
                            creator + "  •  Internet Archive • CC0",
                            11, gray);
                        details.setPadding(0, dp(4), 0, dp(10));
                        card.addView(details);

                        LinearLayout actions = makeRow();
                        actions.setGravity(Gravity.CENTER_VERTICAL);
                        card.addView(actions);

                        Button play = makeButton("PLAY FULL");
                        play.setTextSize(10);
                        actions.addView(play,
                            new LinearLayout.LayoutParams(0, dp(40), 1));

                        Button download = makeButton("DOWNLOAD");
                        download.setTextSize(10);
                        LinearLayout.LayoutParams downloadParams =
                            new LinearLayout.LayoutParams(0, dp(40), 1);
                        downloadParams.setMargins(dp(7), 0, 0, 0);
                        actions.addView(download, downloadParams);

                        play.setOnClickListener(v -> playArchiveTrack(
                            title, audioUrl, play));
                        download.setOnClickListener(v ->
                            downloadArchiveTrack(title, audioUrl));
                    }
                    searchInternetArchive(term, results);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    TextView error = makeText(
                        "Internet Archive search unavailable. iTunes results remain available.",
                        12, gray);
                    error.setPadding(dp(8), dp(10), dp(8), dp(10));
                    results.addView(error);
                });
            }
        }).start();
    }

    JSONObject readArchiveJson(String address) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "AudioHub/1.0");
            if (conn.getResponseCode() != 200) {
                throw new Exception("Archive HTTP " + conn.getResponseCode());
            }
            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
            }
            return new JSONObject(body.toString());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    void playArchiveTrack(String title, String audioUrl, Button play) {
        if (activePreviewButton == play && discoverPlayer != null) {
            try {
                if (discoverPlayer.isPlaying()) {
                    discoverPlayer.pause();
                    play.setText("PLAY FULL");
                } else if (discoverPreviewPrepared) {
                    discoverPlayer.start();
                    play.setText("PAUSE");
                }
            } catch (IllegalStateException ignored) {
                play.setText("PLAY FULL");
            }
            return;
        }

        if (discoverPlayer != null) {
            try { discoverPlayer.release(); } catch (Exception ignored) {}
            discoverPlayer = null;
        }
        if (activePreviewButton != null) activePreviewButton.setText("PLAY");
        activePreviewButton = play;
        discoverPreviewPrepared = false;
        play.setText("LOADING");

        try {
            discoverPlayer = new MediaPlayer();
            discoverPlayer.setAudioStreamType(
                android.media.AudioManager.STREAM_MUSIC);
            discoverPlayer.setDataSource(audioUrl);
            discoverPlayer.setOnPreparedListener(mp -> {
                discoverPreviewPrepared = true;
                mp.start();
                play.setText("PAUSE");
            });
            discoverPlayer.setOnCompletionListener(mp -> {
                play.setText("PLAY FULL");
                discoverPreviewPrepared = false;
                discoverPlayer = null;
                activePreviewButton = null;
                mp.release();
            });
            discoverPlayer.setOnErrorListener((mp, what, extra) -> {
                play.setText("PLAY FULL");
                discoverPreviewPrepared = false;
                discoverPlayer = null;
                activePreviewButton = null;
                mp.release();
                Toast.makeText(this, "Full track playback failed",
                    Toast.LENGTH_SHORT).show();
                return true;
            });
            discoverPlayer.prepareAsync();
        } catch (Exception e) {
            play.setText("PLAY FULL");
            discoverPreviewPrepared = false;
            activePreviewButton = null;
            if (discoverPlayer != null) {
                discoverPlayer.release();
                discoverPlayer = null;
            }
            Toast.makeText(this, "Cannot play full track",
                Toast.LENGTH_SHORT).show();
        }
    }

    void showDj() {
        TextView heading = makeText("OFFLINE DJ", 25, white);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(10), 0, dp(5));
        content.addView(heading);

        TextView info = makeText(
            "Build a mood-based mix from music stored on your phone.",
            12, gray);
        content.addView(info);


        TextView moodTitle = makeText("CHOOSE YOUR MOOD", 12, purple);
        moodTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        content.addView(moodTitle);

        String[] moods = {"CHILL", "ENERGY", "ROMANTIC", "FOCUS"};

        for (int i = 0; i < moods.length; i += 2) {
            LinearLayout row = makeRow();

            for (int j = i; j < i + 2; j++) {
                final String mood = moods[j];
                Button button = makeButton("✦  " + mood);

                row.addView(button,
                    new LinearLayout.LayoutParams(0, -2, 1));

                button.setOnClickListener(v -> createDjMix(mood));
            }

            content.addView(row);
        }

        TextView online = makeText("ONLINE AI DJ", 18, purple);
        online.setPadding(0, dp(18), 0, dp(5));
        content.addView(online);

        TextView onlineInfo = makeText(
            "ChatGPT-powered recommendations need an API key "
                + "and internet connection. Online chat is not active yet.",
            12, gray);
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
    public void onBackPressed() {
        if ("Now Playing".equals(currentPage)) {
            closeNowPlaying();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (discoverPlayer != null) {
            discoverPlayer.release();
            discoverPlayer = null;
            activePreviewButton = null;
        }
        if (receiverRegistered) {
            unregisterReceiver(playbackReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }
}
