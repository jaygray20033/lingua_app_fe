package com.lingua.app.utils;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.lingua.app.R;
import com.lingua.app.activities.MainActivity;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Schedules a daily learning reminder using WorkManager.
 *
 * Why WorkManager (not AlarmManager)?
 *  - Survives reboots & process death.
 *  - Respects battery optimisation / Doze mode.
 *  - No special permissions needed (Android 12+ exact-alarm restrictions).
 *
 * Public API:
 *   - scheduleDaily(ctx, hour, minute) – (re)schedule the daily reminder.
 *   - cancelDaily(ctx)                 – cancel any scheduled reminder.
 *   - showStreakAlert(ctx, hours)      – fire a one-shot streak warning.
 *   - showAchievementUnlocked(ctx, t, b) – fire an achievement notification.
 */
public final class NotificationScheduler {
    public static final String CHANNEL_DAILY = "lingua_daily_reminder";
    public static final String CHANNEL_STREAK = "lingua_streak_alert";
    public static final String CHANNEL_ACHIEVEMENT = "lingua_achievement";
    public static final String WORK_DAILY = "lingua_daily_reminder_work";

    private NotificationScheduler() {}

    /**
     * R5-033 FIX — Vérifie que l'app a la permission POST_NOTIFICATIONS sur
     * Android 13+ (API 33). Sur les versions antérieures, retourne true (permission
     * implicite). Toutes les méthodes `show*` doivent appeler cette check avant
     * `nm.notify()` pour éviter SecurityException + faire échouer silencieusement
     * si l'utilisateur a refusé la permission depuis Settings.
     */
    public static boolean canShowNotifications(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Create notification channels (required on Android 8+). */
    public static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;

        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_DAILY, "Nhắc học hàng ngày",
                NotificationManager.IMPORTANCE_DEFAULT));

        NotificationChannel streak = new NotificationChannel(
                CHANNEL_STREAK, "Cảnh báo streak",
                NotificationManager.IMPORTANCE_HIGH);
        streak.setDescription("Cảnh báo khi streak sắp mất");
        nm.createNotificationChannel(streak);

        nm.createNotificationChannel(new NotificationChannel(
                CHANNEL_ACHIEVEMENT, "Thành tựu",
                NotificationManager.IMPORTANCE_DEFAULT));
    }

    public static void scheduleDaily(Context ctx, int hour, int minute) {
        ensureChannels(ctx);
        long delay = computeInitialDelay(hour, minute);
        Data input = new Data.Builder()
                .putInt("hour", hour)
                .putInt("minute", minute)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                DailyReminderWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .setConstraints(new Constraints.Builder().build())
                .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_DAILY,
                ExistingPeriodicWorkPolicy.UPDATE,
                request);
    }

    public static void cancelDaily(Context ctx) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_DAILY);
    }

    /** Compute milliseconds until the next occurrence of HH:mm. */
    private static long computeInitialDelay(int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar next = (Calendar) now.clone();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, minute);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);
        return next.getTimeInMillis() - now.getTimeInMillis();
    }

    /** One-shot streak-warning notification (fired by the app when streak < 24h left). */
    public static void showStreakAlert(Context ctx, int hoursLeft) {
        // R5-033 FIX: bail-out silencieux si la permission est révoquée
        // (Android 13+). Évite SecurityException sur nm.notify() qui ferait
        // crasher des WorkManager workers.
        if (!canShowNotifications(ctx)) return;
        ensureChannels(ctx);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_STREAK)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("🔥 Sắp mất streak!")
                .setContentText("Còn " + hoursLeft + "h để duy trì streak. Học ngay nhé!")
                .setAutoCancel(true)
                .setContentIntent(homeIntent(ctx))
                .build();
        // BUG-019 FIX: dùng unique ID dựa trên timestamp để nhiều alert
        // streak (vd: app fire lúc 8:00 và 12:00 cùng ngày) không ghi đè nhau.
        // Mặt nạ 0xFFFFFFF để giữ giá trị dương (Notification ID là int).
        int streakId = 2000 + ((int) (System.currentTimeMillis() & 0xFFFFF));
        nm.notify(streakId, n);
    }

    /** One-shot achievement notification. */
    public static void showAchievementUnlocked(Context ctx, String title, String body) {
        // R5-033 FIX: cf. showStreakAlert.
        if (!canShowNotifications(ctx)) return;
        ensureChannels(ctx);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        Notification n = new NotificationCompat.Builder(ctx, CHANNEL_ACHIEVEMENT)
                .setSmallIcon(android.R.drawable.btn_star_big_on)
                .setContentTitle("🏆 " + title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(homeIntent(ctx))
                .build();
        // BUG-019 FIX: unique ID để user unlock nhiều achievement trong cùng buổi
        // học đều hiển thị được. Trước đây notify(3001) cố định → ach #2 ghi
        // đè ach #1.
        int achId = 3000 + ((int) (System.currentTimeMillis() & 0xFFFFF));
        nm.notify(achId, n);
    }

    private static PendingIntent homeIntent(Context ctx) {
        Intent i = new Intent(ctx, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getActivity(ctx, 0, i, flags);
    }

    // -----------------------------------------------------------------------
    //  Worker
    // -----------------------------------------------------------------------
    public static class DailyReminderWorker extends Worker {
        public DailyReminderWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull @Override public Result doWork() {
            Context ctx = getApplicationContext();
            // R5-033 FIX: ne pas tenter de notifier si la permission a été révoquée
            // (sinon SecurityException → Worker retry → bad backoff loop).
            if (!canShowNotifications(ctx)) return Result.success();
            ensureChannels(ctx);
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm == null) return Result.success();

            String[] titles = {
                    "🦉 Đã đến giờ học rồi!",
                    "📚 5 phút mỗi ngày — đừng bỏ lỡ!",
                    "🔥 Giữ streak nhé!",
                    "✨ Học chút nào!",
                    "🌱 Một bước nhỏ mỗi ngày."
            };
            String[] bodies = {
                    "Hoàn thành mục tiêu hôm nay để nhận XP và giữ streak.",
                    "Mở Lingua và học vài từ mới ngay nào.",
                    "Streak của bạn sắp mất — học 1 bài để duy trì!",
                    "Một bài học ngắn cũng đủ để giỏi hơn hôm qua.",
                    "Từ vựng và ngữ pháp mới đang chờ bạn!"
            };
            int idx = (int) (Math.random() * titles.length);

            Notification n = new NotificationCompat.Builder(ctx, CHANNEL_DAILY)
                    .setSmallIcon(android.R.drawable.ic_menu_today)
                    .setContentTitle(titles[idx])
                    .setContentText(bodies[idx])
                    .setAutoCancel(true)
                    .setContentIntent(homeIntent(ctx))
                    .build();
            nm.notify(1001, n);
            return Result.success();
        }
    }
}
