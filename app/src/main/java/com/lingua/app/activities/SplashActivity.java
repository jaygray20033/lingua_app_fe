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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    // BUG-004 FIX: thay vì dùng `new Thread(...)` thô (anti-pattern: không thể
    // shutdown, giữ Runnable reference → khó GC, có thể NPE nếu ai đó đổi
    // sang getContext() activity-scoped), ta dùng ExecutorService có thể shutdown
    // được trong onDestroy() khi user swipe-kill app giữa 800ms splash delay.
    private ExecutorService bgExec;
    private final Handler routeHandler = new Handler(Looper.getMainLooper());

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
        // TD-2 FIX: NotificationScheduler.scheduleDaily() bên trong gọi
        // WorkManager.enqueue() — lần khởi động đầu trên thiết bị mới, init
        // WorkManager có thể block UI thread 100–300ms gây chớp màn hình splash.
        // Đẩy cả ensureChannels + scheduleDaily ra background thread; chúng
        // không phụ thuộc View nên hoàn toàn an toàn.
        final boolean reminderEnabled = prefs.getBoolean(SettingsActivity.KEY_DAILY_REMINDER, true);
        final int reminderHour = prefs.getInt(SettingsActivity.KEY_REMINDER_HOUR, 20);
        final int reminderMin  = prefs.getInt(SettingsActivity.KEY_REMINDER_MIN, 0);
        // BUG-004 FIX: dùng ExecutorService thay vì `new Thread()` để có thể
        // shutdownNow() trong onDestroy() khi user swipe-kill app < 800ms.
        bgExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "notif-scheduler-init");
            t.setDaemon(true);
            return t;
        });
        bgExec.execute(() -> {
            try {
                NotificationScheduler.ensureChannels(getApplicationContext());
                if (reminderEnabled) {
                    NotificationScheduler.scheduleDaily(getApplicationContext(),
                            reminderHour, reminderMin);
                }
            } catch (Throwable ignore) {
                // Defensive: nếu WorkManager init fail, đừng crash splash.
            }
        });

        // BUG #17 FIX: giảm delay từ 1500ms xuống 800ms. Trên thiết bị nhanh,
        // 1.5 giây là quá dư thừa và làm user cảm thấy app khởi động chậm.
        routeHandler.postDelayed(this::route, 800);
    }

    @Override
    protected void onDestroy() {
        // BUG-004 FIX: huỷ các callback và background task đang chạy khi user
        // swipe-kill app trước 800ms để tránh leak Runnable reference.
        routeHandler.removeCallbacksAndMessages(null);
        if (bgExec != null) {
            try {
                bgExec.shutdownNow();
                bgExec.awaitTermination(200, TimeUnit.MILLISECONDS);
            } catch (Exception ignore) {}
            bgExec = null;
        }
        super.onDestroy();
    }

    /**
     * FIX 2.5: Improved routing logic:
     *   1. If not logged in → Login.
     *   2. If logged in but token looks invalid (empty) → Login (clear stale session).
     *   3. If logged in but not onboarded → Onboarding (show only first time).
     *   4. If logged in + onboarded → MainActivity (Dashboard).
     *
     * Token validation: we only check locally if the token exists and is non-empty.
     * Full server-side validation happens when the first API call returns 401,
     * which triggers TokenAuthenticator to refresh or redirect to login.
     */
    private void route() {
        SessionManager session = SessionManager.getInstance(this);
        SharedPreferences prefs = getSharedPreferences(OnboardingActivity.PREFS, MODE_PRIVATE);
        boolean onboarded = prefs.getBoolean(OnboardingActivity.KEY_ONBOARDED, false);

        Intent intent;
        if (!session.isLoggedIn()) {
            intent = new Intent(this, LoginActivity.class);
        } else {
            // FIX 2.5: check that the access token is not empty/null.
            // If it is, the session is stale and user should re-login.
            String token = session.getAccessToken();
            if (token == null || token.trim().isEmpty()) {
                session.clear();
                intent = new Intent(this, LoginActivity.class);
            } else if (!onboarded) {
                intent = new Intent(this, OnboardingActivity.class);
            } else {
                intent = new Intent(this, MainActivity.class);
            }
        }
        startActivity(intent);
        finish();
    }
}
