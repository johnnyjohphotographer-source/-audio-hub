package com.audiohub.music;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.util.ArrayList;

public class PlaybackService extends Service {

    public static final String ACTION_PLAY = "audiohub.PLAY";
    public static final String ACTION_PAUSE = "audiohub.PAUSE";
    public static final String ACTION_RESUME = "audiohub.RESUME";
    public static final String ACTION_NEXT = "audiohub.NEXT";
    public static final String ACTION_PREVIOUS = "audiohub.PREVIOUS";
    public static final String ACTION_STOP = "audiohub.STOP";
    public static final String ACTION_MODES = "audiohub.MODES";
    public static final String ACTION_UPDATE = "audiohub.UPDATE";

    public static final String EXTRA_URIS = "uris";
    public static final String EXTRA_TITLES = "titles";
    public static final String EXTRA_ARTISTS = "artists";
    public static final String EXTRA_INDEX = "index";

    private static final String CHANNEL = "audiohub_playback";
    private static final int NOTIFICATION_ID = 77;

    private MediaPlayer player;
    private MediaSession mediaSession;
    private ArrayList<Uri> uris = new ArrayList<>();
    private ArrayList<String> titles = new ArrayList<>();
    private ArrayList<String> artists = new ArrayList<>();
    private int index = -1;
    private boolean prepared = false;
    private boolean shuffle = false;
    private boolean repeat = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
        mediaSession = new MediaSession(this, "AudioHubSession");
        mediaSession.setActive(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        startForeground(NOTIFICATION_ID, buildNotification("Audio Hub", false));

        String action = intent.getAction();

        if (ACTION_PLAY.equals(action)) {
            ArrayList<Uri> newUris =
                intent.getParcelableArrayListExtra(EXTRA_URIS);
            ArrayList<String> newTitles =
                intent.getStringArrayListExtra(EXTRA_TITLES);
            ArrayList<String> newArtists =
                intent.getStringArrayListExtra(EXTRA_ARTISTS);

            if (newUris != null && !newUris.isEmpty()) {
                uris = newUris;
                titles = newTitles == null ? new ArrayList<>() : newTitles;
                artists = newArtists == null ? new ArrayList<>() : newArtists;
                index = intent.getIntExtra(EXTRA_INDEX, 0);
                playCurrent();
            }
        } else if (ACTION_PAUSE.equals(action)) {
            pausePlayer();
        } else if (ACTION_RESUME.equals(action)) {
            resumePlayer();
        } else if (ACTION_NEXT.equals(action)) {
            next();
        } else if (ACTION_PREVIOUS.equals(action)) {
            previous();
        } else if (ACTION_MODES.equals(action)) {
            shuffle = intent.getBooleanExtra("shuffle", false);
            repeat = intent.getBooleanExtra("repeat", false);
            broadcastUpdate();
        } else if (ACTION_STOP.equals(action)) {
            stopPlayback();
        }

        return START_NOT_STICKY;
    }

    private void playCurrent() {
        if (index < 0 || index >= uris.size()) return;

        releasePlayer();
        prepared = false;
        player = new MediaPlayer();

        try {
            player.setDataSource(this, uris.get(index));
            player.setOnPreparedListener(mp -> {
                prepared = true;
                mp.start();
                updateNotification();
                broadcastUpdate();
            });
            player.setOnCompletionListener(mp -> {
                if (repeat) {
                    playCurrent();
                } else {
                    next();
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                broadcastUpdate();
                return true;
            });
            player.prepareAsync();
            updateNotification();
        } catch (Exception e) {
            releasePlayer();
            broadcastUpdate();
        }
    }

    private void pausePlayer() {
        if (player != null && prepared && player.isPlaying()) {
            player.pause();
        }
        updateNotification();
        broadcastUpdate();
    }

    private void resumePlayer() {
        if (player != null && prepared && !player.isPlaying()) {
            player.start();
        } else if (player == null && index >= 0) {
            playCurrent();
            return;
        }
        updateNotification();
        broadcastUpdate();
    }

    private void next() {
        if (uris.isEmpty()) return;
        if (shuffle && uris.size() > 1) {
            int old = index;
            do {
                index = (int) (Math.random() * uris.size());
            } while (index == old);
        } else {
            index = (index + 1) % uris.size();
        }
        playCurrent();
    }

    private void previous() {
        if (uris.isEmpty()) return;
        index = index <= 0 ? uris.size() - 1 : index - 1;
        playCurrent();
    }

    public static void setModes(Service service, boolean shuffleMode,
                                boolean repeatMode) {
        if (service instanceof PlaybackService) {
            PlaybackService playback = (PlaybackService) service;
            playback.shuffle = shuffleMode;
            playback.repeat = repeatMode;
        }
    }

    private void broadcastUpdate() {
        Intent update = new Intent(ACTION_UPDATE);
        update.setPackage(getPackageName());
        update.putExtra("index", index);
        update.putExtra("playing",
            player != null && prepared && player.isPlaying());
        update.putExtra("title", currentTitle());
        update.putExtra("artist", currentArtist());
        sendBroadcast(update);
    }

    private String currentTitle() {
        return index >= 0 && index < titles.size()
            ? titles.get(index) : "Audio Hub";
    }

    private String currentArtist() {
        return index >= 0 && index < artists.size()
            ? artists.get(index) : "";
    }

    private void updateNotification() {
        boolean playing = player != null && prepared && player.isPlaying();
        NotificationManager manager =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, buildNotification(currentTitle(), playing));
    }

    private Notification buildNotification(String title, boolean playing) {
        PendingIntent previous = actionIntent(ACTION_PREVIOUS, 1);
        PendingIntent toggle = actionIntent(
            playing ? ACTION_PAUSE : ACTION_RESUME, 2);
        PendingIntent next = actionIntent(ACTION_NEXT, 3);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL)
            : new Notification.Builder(this);

        builder.setSmallIcon(R.drawable.ic_audio_hub)
            .setContentTitle(title)
            .setContentText(currentArtist().isEmpty()
                ? "Audio Hub" : currentArtist())
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .addAction(android.R.drawable.ic_media_previous,
                "Previous", previous)
            .addAction(playing
                ? android.R.drawable.ic_media_pause
                : android.R.drawable.ic_media_play,
                playing ? "Pause" : "Play", toggle)
            .addAction(android.R.drawable.ic_media_next, "Next", next)
            .setStyle(new Notification.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));

        return builder.build();
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(action);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, requestCode, intent, flags);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Audio Hub Playback",
                NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Music playback controls");
            NotificationManager manager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    private void releasePlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        prepared = false;
    }

    private void stopPlayback() {
        releasePlayer();
        if (mediaSession != null) mediaSession.setActive(false);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
