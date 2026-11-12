package com.audiohub.music;

import android.content.Context;
import android.media.MediaPlayer;

import java.io.IOException;

public class AudioPlayer {

    public interface Listener {
        void onPrepared();
        void onCompleted();
        void onError(String message);
    }

    private MediaPlayer player;
    private Listener listener;

    public void play(Context context, AudioTrack track, Listener callback) {
        release();
        listener = callback;

        player = new MediaPlayer();

        try {
            player.setDataSource(context, track.uri);

            player.setOnPreparedListener(mp -> {
                mp.start();
                if (listener != null) listener.onPrepared();
            });

            player.setOnCompletionListener(mp -> {
                if (listener != null) listener.onCompleted();
            });

            player.setOnErrorListener((mp, what, extra) -> {
                if (listener != null) {
                    listener.onError("Unable to play this song.");
                }
                return true;
            });

            player.prepareAsync();

        } catch (IOException | IllegalArgumentException e) {
            if (listener != null) {
                listener.onError(e.getMessage());
            }
        }
    }

    public void pause() {
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    public void resume() {
        if (player != null && !player.isPlaying()) {
            player.start();
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void release() {
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
