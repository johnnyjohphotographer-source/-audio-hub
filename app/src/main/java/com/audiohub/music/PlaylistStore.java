package com.audiohub.music;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PlaylistStore {
    private final SharedPreferences prefs;

    public PlaylistStore(Context context) {
        prefs = context.getSharedPreferences("audio_hub_playlists", Context.MODE_PRIVATE);
    }

    private JSONObject read() {
        try {
            return new JSONObject(prefs.getString("data", "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void save(JSONObject data) {
        prefs.edit().putString("data", data.toString()).apply();
    }

    public List<String> names() {
        List<String> result = new ArrayList<>();
        JSONObject data = read();
        Iterator<String> keys = data.keys();
        while (keys.hasNext()) result.add(keys.next());
        return result;
    }

    public boolean create(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        JSONObject data = read();
        String clean = name.trim();
        if (data.has(clean)) return false;
        try {
            data.put(clean, new JSONArray());
            save(data);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void delete(String name) {
        JSONObject data = read();
        data.remove(name);
        save(data);
    }

    public void add(String name, long trackId) {
        JSONObject data = read();
        JSONArray songs = data.optJSONArray(name);
        if (songs == null) return;
        for (int i = 0; i < songs.length(); i++) {
            if (songs.optLong(i) == trackId) return;
        }
        songs.put(trackId);
        save(data);
    }

    public void remove(String name, long trackId) {
        JSONObject data = read();
        JSONArray old = data.optJSONArray(name);
        if (old == null) return;
        JSONArray updated = new JSONArray();
        for (int i = 0; i < old.length(); i++) {
            long id = old.optLong(i);
            if (id != trackId) updated.put(id);
        }
        try {
            data.put(name, updated);
            save(data);
        } catch (Exception ignored) {}
    }

    public List<Long> tracks(String name) {
        List<Long> result = new ArrayList<>();
        JSONArray songs = read().optJSONArray(name);
        if (songs == null) return result;
        for (int i = 0; i < songs.length(); i++) {
            result.add(songs.optLong(i));
        }
        return result;
    }
}
