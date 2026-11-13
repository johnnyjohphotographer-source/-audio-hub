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
    TextView status, nowPlaying, playerArtist;
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
        LinearLayout header = makeRow();
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

        // Premium bottom navigation
        LinearLayout navigationBar = new LinearLayout(this);
        navigationBar.setOrientation(LinearLayout.VERTICAL);
        navigationBar.setPadding(dp(5), dp(8), dp(5), dp(3));
        navigationBar.setBackground(
                roundedCard(0xFF151320, 0x554A3A62));

        LinearLayout navigation = makeRow();
        navigation.setGravity(Gravity.CENTER_VERTICAL);

        String[] pages = {"Home", "Library", "Playlists", "AI DJ", "Themes"};
        String[] icons = {"⌂", "♫", "▤", "✦", "◐"};

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
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(roundedCard(0xFF151320, 0x995B3B79));
        card.setElevation(dp(5));

        TextView label = makeText(
            "♫   NOW PLAYING", 11, purple
        );
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(label);

        LinearLayout hero = makeRow();
        hero.setGravity(android.view.Gravity.CENTER_VERTICAL);
        hero.setPadding(0, dp(15), 0, dp(15));

        TextView artwork = makeText("♫", 42, 0xFFFFFFFF);
        artwork.setGravity(android.view.Gravity.CENTER);
        artwork.setBackground(roundedCard(
            0xFF39205F, 0xFFB56CFF
        ));

        LinearLayout.LayoutParams artParams =
            new LinearLayout.LayoutParams(dp(92), dp(92));
        artParams.setMargins(0, 0, dp(15), 0);
        hero.addView(artwork, artParams);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(android.view.Gravity.CENTER_VERTICAL);

        nowPlaying = makeText(
            currentIndex >= 0 && currentIndex < currentTracks.size()
                ? currentTracks.get(currentIndex).title
                : "Choose a song",
            17, white
        );
        nowPlaying.setTypeface(null, android.graphics.Typeface.BOLD);
        nowPlaying.setMaxLines(2);
        details.addView(nowPlaying);

        playerArtist = makeText(
            "AUDIO HUB  •  SIGNATURE PLAYER", 11, gray
        );
        playerArtist.setPadding(0, dp(6), 0, 0);
        playerArtist.setMaxLines(1);
        details.addView(playerArtist);

        hero.addView(details,
            new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(hero);

        LinearLayout transport = makeRow();
        transport.setGravity(android.view.Gravity.CENTER);
        transport.setPadding(0, dp(5), 0, dp(8));

        previousButton = makeButton("⏮");
        playPauseButton = makeButton(
            isPlaying ? "Ⅱ  PAUSE" : "▶  PLAY"
        );
        nextButton = makeButton("⏭");

        previousButton.setTextSize(17);
        nextButton.setTextSize(17);
        playPauseButton.setTextSize(13);

        transport.addView(previousButton,
            new LinearLayout.LayoutParams(0, dp(50), 1));
        transport.addView(playPauseButton,
            new LinearLayout.LayoutParams(0, dp(50), 1.6f));
        transport.addView(nextButton,
            new LinearLayout.LayoutParams(0, dp(50), 1));

        card.addView(transport);

        previousButton.setOnClickListener(v ->
            sendAction(PlaybackService.ACTION_PREVIOUS));
        nextButton.setOnClickListener(v ->
            sendAction(PlaybackService.ACTION_NEXT));
        playPauseButton.setOnClickListener(v ->
            sendAction(isPlaying
                ? PlaybackService.ACTION_PAUSE
                : PlaybackService.ACTION_RESUME));

        LinearLayout extras = makeRow();
        Button micButton = makeButton("MIC");
        Button shuffleButton = makeButton(
            shuffle ? "SHUFFLE ON" : "SHUFFLE"
        );
        Button repeatButton = makeButton(
            repeat ? "REPEAT ON" : "REPEAT"
        );

        extras.addView(micButton,
            new LinearLayout.LayoutParams(0, -2, 1));
        extras.addView(shuffleButton,
            new LinearLayout.LayoutParams(0, -2, 1));
        extras.addView(repeatButton,
            new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(extras);

        micButton.setOnClickListener(v -> startVoiceCommand());

        shuffleButton.setOnClickListener(v -> {
            shuffle = !shuffle;
            updateModes();
            shuffleButton.setText(
                shuffle ? "SHUFFLE ON" : "SHUFFLE"
            );
        });

        repeatButton.setOnClickListener(v -> {
            repeat = !repeat;
            updateModes();
            repeatButton.setText(
                repeat ? "REPEAT ON" : "REPEAT"
            );
        });

        LinearLayout.LayoutParams params =
            new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(12), 0, dp(14));
        content.addView(card, params);
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

        addPlayerControls();
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

        addPlayerControls();

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

        ScrollView scroll = new ScrollView(this);
        trackList = new LinearLayout(this);
        trackList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(trackList);

        content.addView(scroll,
            new LinearLayout.LayoutParams(-1, 0, 1));

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

            String title = track.title == null ? "Unknown title" : track.title;
            String artist = track.artist == null ? "Unknown artist" : track.artist;

            if (!title.toLowerCase(Locale.ROOT).contains(q)
                    && !artist.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }

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
            card.addView(artwork, new LinearLayout.LayoutParams(dp(54), dp(54)));

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

            TextView duration = makeText(formatDuration(track.duration), 10, gray);
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

            shown++;
        }

        if (status != null) {
            status.setText(shown + " songs  •  Tap to play  •  Hold for playlist");
        }

        if (shown == 0) {
            TextView empty = makeText(
                    q.isEmpty()
                            ? "No music loaded. Scan your phone to begin."
                            : "No matching songs found.",
                    14, gray);
            empty.setPadding(dp(10), dp(22), dp(10), dp(22));
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
        TextView heading = makeText("YOUR PLAYLISTS", 24, white);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(10), 0, dp(8));
        content.addView(heading);

        Button create = makeButton("＋  CREATE PLAYLIST");
        content.addView(create);
        create.setOnClickListener(v -> createPlaylistDialog());

        addPlayerControls();

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

        addPlayerControls();

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

    void showDj() {
        TextView heading = makeText("OFFLINE DJ", 25, white);
        heading.setTypeface(null, android.graphics.Typeface.BOLD);
        heading.setPadding(0, dp(10), 0, dp(5));
        content.addView(heading);

        TextView info = makeText(
            "Build a mood-based mix from music stored on your phone.",
            12, gray);
        content.addView(info);

        addPlayerControls();

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
    protected void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(playbackReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }
}
