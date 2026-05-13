package com.lingua.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.lingua.app.R;
import com.lingua.app.utils.NotificationScheduler;
import com.lingua.app.utils.SessionManager;

/**
 * Splash screen / app entry point.
 *
 * Routing logic:
 *   1. Apply persisted dark-mode preference before any UI work.
 *   2. After 1.5s splash:
 *        - If logged-in AND already onboarded → MainActivity.
 *        - If logged-in AND NOT onboarded     → OnboardingActivity.
 *        - Otherwise                          → LoginActivity.
 *   3. (Re)schedule the daily reminder if the user wants it.
 */
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply dark-mode setting BEFORE inflating any view.
        SharedPreferences prefs = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        boolean dark = prefs.getBoolean(SettingsActivity.KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(dark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Create notification channels and re-schedule the reminder if the user
        // had previously opted in.
        NotificationScheduler.ensureChannels(this);
        if (prefs.getBoolean(SettingsActivity.KEY_DAILY_REMINDER, true)) {
            int hour = prefs.getInt(SettingsActivity.KEY_REMINDER_HOUR, 20);
            int min = prefs.getInt(SettingsActivity.KEY_REMINDER_MIN, 0);
            NotificationScheduler.scheduleDaily(this, hour, min);
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::route, 1500);
    }

    private void route() {
        SessionManager session = SessionManager.getInstance(this);
        SharedPreferences prefs = getSharedPreferences(OnboardingActivity.PREFS, MODE_PRIVATE);
        boolean onboarded = prefs.getBoolean(OnboardingActivity.KEY_ONBOARDED, false);

        Intent intent;
        if (!session.isLoggedIn()) {
            intent = new Intent(this, LoginActivity.class);
        } else if (!onboarded) {
            intent = new Intent(this, OnboardingActivity.class);
        } else {
            intent = new Intent(this, MainActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
