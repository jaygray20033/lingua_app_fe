package com.lingua.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Encrypted session manager with singleton pattern.
 * Falls back to plain SharedPreferences if encryption is unavailable.
 */
public class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREF_NAME = "LinguaSession";
    private static final String FALLBACK_PREF = "LinguaSession_fallback";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_EMAIL = "email";

    private static volatile SessionManager instance;
    private final SharedPreferences prefs;

    // CB-2 FIX: constructor private de enforce singleton pattern.
    // Truoc day constructor public cho phep new SessionManager(ctx) tao instance
    // thu hai, co the gay race condition khi doc/ghi token tren background thread
    // -> user bi logout ngau nhien. Chi cho phep truy cap qua getInstance().
    private SessionManager(Context context) {
        SharedPreferences tmp;
        try {
            MasterKey masterKey = new MasterKey.Builder(context.getApplicationContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            tmp = EncryptedSharedPreferences.create(
                    context.getApplicationContext(), PREF_NAME, masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, using fallback", e);
            tmp = context.getApplicationContext().getSharedPreferences(FALLBACK_PREF, Context.MODE_PRIVATE);
        }
        prefs = tmp;
    }

    /** Thread-safe singleton accessor */
    public static SessionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SessionManager.class) {
                if (instance == null) {
                    instance = new SessionManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public synchronized void saveSession(long userId, String accessToken, String refreshToken,
                                         String displayName, String email) {
        prefs.edit()
                .putLong(KEY_USER_ID, userId)
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_DISPLAY_NAME, displayName != null ? displayName : "")
                .putString(KEY_EMAIL, email != null ? email : "")
                .apply();
    }

    public synchronized void updateTokens(String accessToken, String refreshToken) {
        SharedPreferences.Editor editor = prefs.edit();
        if (accessToken != null) editor.putString(KEY_ACCESS_TOKEN, accessToken);
        if (refreshToken != null) editor.putString(KEY_REFRESH_TOKEN, refreshToken);
        editor.apply();
    }

    public String getAccessToken() { return prefs.getString(KEY_ACCESS_TOKEN, null); }
    public String getRefreshToken() { return prefs.getString(KEY_REFRESH_TOKEN, null); }
    public long getUserId() { return prefs.getLong(KEY_USER_ID, -1); }
    public String getDisplayName() { return prefs.getString(KEY_DISPLAY_NAME, ""); }
    public String getEmail() { return prefs.getString(KEY_EMAIL, ""); }
    public boolean isLoggedIn() { return getAccessToken() != null; }

    public synchronized void updateAccessToken(String token) {
        updateTokens(token, null);
    }

    public synchronized void clear() {
        prefs.edit().clear().apply();
    }
}
