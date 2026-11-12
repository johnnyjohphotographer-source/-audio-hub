package com.audiohub.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class OfflineDj {

    public static List<Integer> createMix(List<AudioTrack> tracks, String mood) {
        List<Integer> result = new ArrayList<>();
        if (tracks == null || tracks.isEmpty()) return result;

        String[] keywords;

        switch (mood.toLowerCase(Locale.ROOT)) {
            case "chill":
                keywords = new String[]{"chill", "slow", "acoustic", "love", "calm", "easy"};
                break;
            case "energy":
                keywords = new String[]{"dance", "party", "remix", "club", "fast", "run", "energy"};
                break;
            case "romantic":
                keywords = new String[]{"love", "heart", "baby", "romance", "kiss", "eyes"};
                break;
            case "focus":
                keywords = new String[]{"instrumental", "ambient", "piano", "focus", "lofi"};
                break;
            default:
                keywords = new String[0];
        }

        List<Integer> matches = new ArrayList<>();
        for (int i = 0; i < tracks.size(); i++) {
            AudioTrack t = tracks.get(i);
            String text = ((t.title == null ? "" : t.title) + " " +
                    (t.artist == null ? "" : t.artist) + " " +
                    (t.album == null ? "" : t.album)).toLowerCase(Locale.ROOT);

            for (String word : keywords) {
                if (text.contains(word)) {
                    matches.add(i);
                    break;
                }
            }
        }

        if (matches.size() < 5) {
            matches.clear();
            for (int i = 0; i < tracks.size(); i++) matches.add(i);
        }

        Collections.shuffle(matches, new Random());
        int limit = Math.min(30, matches.size());
        result.addAll(matches.subList(0, limit));
        return result;
    }
}
