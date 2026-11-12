package com.audiohub.music;

import android.content.Context;
import android.content.SharedPreferences;

public class ThemeManager {
    private final SharedPreferences prefs;

    public ThemeManager(Context context) {
        prefs = context.getSharedPreferences(
            "audio_hub_theme", Context.MODE_PRIVATE);
    }

    public void setTheme(String name) {
        prefs.edit().putString("theme", name).apply();
    }

    public String getTheme() {
        return prefs.getString("theme", "Midnight Purple");
    }

    public void setPhoto(String uri) {
        prefs.edit().putString("photo_uri", uri).apply();
    }

    public String getPhoto() {
        return prefs.getString("photo_uri", "");
    }

    public void setCustomAccent(int color) {
        prefs.edit().putInt("custom_accent", color).apply();
    }

    public int getCustomAccent() {
        return prefs.getInt("custom_accent", 0xFFA855F7);
    }

    public void setCustomBackground(int color) {
        prefs.edit().putInt("custom_background", color).apply();
    }

    public int getCustomBackground() {
        return prefs.getInt("custom_background", 0xFF09090F);
    }

    public void setCustomText(int color) {
        prefs.edit().putInt("custom_text", color).apply();
    }

    public int getCustomText() {
        return prefs.getInt("custom_text", 0xFFFFFFFF);
    }
}
