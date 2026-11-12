package com.audiohub.music;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;

public class AudioScanner {

    public static List<AudioTrack> scan(Context context) {
        List<AudioTrack> tracks = new ArrayList<>();

        if (Build.VERSION.SDK_INT < 29) {
            return tracks;
        }

        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        };

        for (String volume :
                MediaStore.getExternalVolumeNames(context)) {

            Uri collection =
                MediaStore.Audio.Media.getContentUri(volume);

            try (Cursor cursor = context.getContentResolver().query(
                    collection,
                    projection,
                    MediaStore.Audio.Media.IS_MUSIC + " != 0",
                    null,
                    MediaStore.Audio.Media.TITLE + " ASC")) {

                if (cursor == null) continue;

                int idColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ARTIST);
                int albumColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.ALBUM);
                int durationColumn = cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Media.DURATION);

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    String album = cursor.getString(albumColumn);
                    long duration = cursor.getLong(durationColumn);

                    Uri audioUri = ContentUris.withAppendedId(
                        collection, id);

                    tracks.add(new AudioTrack(
                        id, title, artist, album, duration, audioUri));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return tracks;
    }
}
