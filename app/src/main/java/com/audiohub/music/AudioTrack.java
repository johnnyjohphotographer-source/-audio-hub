package com.audiohub.music;

import android.net.Uri;

public class AudioTrack {
    public final long id;
    public final String title;
    public final String artist;
    public final String album;
    public final long duration;
    public final Uri uri;

    public AudioTrack(long id, String title, String artist,
                      String album, long duration, Uri uri) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.uri = uri;
    }
}
