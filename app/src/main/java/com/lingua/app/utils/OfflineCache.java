package com.lingua.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.lingua.app.models.Word;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight offline cache for vocabulary lists.
 *
 * Implementation note:
 *  - Uses SharedPreferences + Gson rather than Room. The dataset is small
 *    (a few hundred words per language/level) and we only need read/write,
 *    not complex querying. This avoids pulling in androidx.room (~1 MB)
 *    and an annotation processor in this lightweight project.
 *  - Caches are keyed by (language, level) so different filters do not
 *    overwrite each other.
 *  - A timestamp is stored per key so callers can decide if the cache is
 *    fresh enough.
 *
 * If/when the project adopts Room, this class can be replaced behind the
 * same getInstance() / getWords() / putWords() / clear() API.
 */
public final class OfflineCache {
    private static final String PREFS = "LinguaOfflineCache";
    private static final String KEY_PREFIX_WORDS = "words::";
    private static final String KEY_PREFIX_TS = "ts::";
    /** Cache TTL: 24h. */
    public static final long DEFAULT_TTL_MS = 24 * 60 * 60 * 1000L;

    private static volatile OfflineCache INSTANCE;
    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    private OfflineCache(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static OfflineCache getInstance(Context ctx) {
        if (INSTANCE == null) {
            synchronized (OfflineCache.class) {
                if (INSTANCE == null) INSTANCE = new OfflineCache(ctx);
            }
        }
        return INSTANCE;
    }

    /** Save a vocabulary page to the cache for the given (language, level). */
    public void putWords(String language, String level, List<Word> words) {
        if (words == null) return;
        String key = wordKey(language, level);
        prefs.edit()
                .putString(KEY_PREFIX_WORDS + key, gson.toJson(words))
                .putLong(KEY_PREFIX_TS + key, System.currentTimeMillis())
                .apply();
    }

    /** Returns cached words or an empty list if absent / stale. */
    public List<Word> getWords(String language, String level) {
        return getWords(language, level, DEFAULT_TTL_MS);
    }

    public List<Word> getWords(String language, String level, long maxAgeMs) {
        String key = wordKey(language, level);
        long ts = prefs.getLong(KEY_PREFIX_TS + key, 0L);
        if (ts == 0L) return new ArrayList<>();
        if (maxAgeMs > 0 && System.currentTimeMillis() - ts > maxAgeMs) return new ArrayList<>();
        String json = prefs.getString(KEY_PREFIX_WORDS + key, null);
        if (json == null) return new ArrayList<>();
        try {
            Type t = new TypeToken<List<Word>>() {}.getType();
            List<Word> out = gson.fromJson(json, t);
            return out != null ? out : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /** Returns true if a fresh cache exists for (language, level). */
    public boolean hasFresh(String language, String level) {
        String key = wordKey(language, level);
        long ts = prefs.getLong(KEY_PREFIX_TS + key, 0L);
        return ts > 0 && System.currentTimeMillis() - ts <= DEFAULT_TTL_MS;
    }

    /** Wipe everything – called from the Settings screen. */
    public void clear() {
        prefs.edit().clear().apply();
    }

    private static String wordKey(String language, String level) {
        return (language == null ? "any" : language) + "/" + (level == null ? "any" : level);
    }
}
