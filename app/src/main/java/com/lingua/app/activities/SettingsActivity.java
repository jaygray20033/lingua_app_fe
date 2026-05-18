package com.lingua.app.activities;

import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.utils.NotificationScheduler;
import com.lingua.app.utils.OfflineCache;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Settings screen.
 *
 * Lets the user toggle:
 *   - Dark mode (instantly applied via AppCompatDelegate).
 *   - Sound / haptic feedback.
 *   - Daily learning reminder + the time of the reminder
 *     (scheduled via {@link NotificationScheduler}, which uses WorkManager).
 *   - Streak warning + achievement notifications.
 *   - Daily XP goal (re-uses ProfileActivity's dialog flow).
 *   - Target language.
 *   - Audio autoplay.
 *   - Offline cache toggle + a one-tap "clear cache" action.
 *
 * All preferences are stored in SharedPreferences("LinguaPrefs").
 */
public class SettingsActivity extends AppCompatActivity {
    public static final String PREFS = "LinguaPrefs";
    public static final String KEY_DARK_MODE = "dark_mode";
    public static final String KEY_SOUND = "feedback_sound";
    public static final String KEY_HAPTIC = "feedback_haptic";
    public static final String KEY_DAILY_REMINDER = "daily_reminder";
    public static final String KEY_REMINDER_HOUR = "reminder_hour";
    public static final String KEY_REMINDER_MIN = "reminder_minute";
    public static final String KEY_STREAK_ALERT = "streak_alert";
    public static final String KEY_ACHIEVEMENT_NOTIF = "achievement_notif";
    public static final String KEY_AUTOPLAY = "autoplay_audio";
    public static final String KEY_OFFLINE = "offline_cache";

    // UX-8 FIX: lưu scroll position tạm khi recreate() vì toggle Dark Mode.
    private static int sPendingScrollY = -1;

    // LF-5 FIX: cache danh sách ngôn ngữ lấy được từ backend
    // (/api/gamification/languages). Fallback về 4 ngôn ngữ mặc định nếu
    // API chưa trả về (mạng lỗi / lần đầu mở Settings).
    private final List<String> langCodes = new ArrayList<>();
    private final List<String> langLabels = new ArrayList<>();

    private SharedPreferences prefs;

    private SwitchCompat switchDarkMode, switchSound, switchHaptic, switchDailyReminder;
    private SwitchCompat switchStreakAlert, switchAchievements, switchAutoplay, switchOffline;
    private Button btnReminderTime, btnClearCache;
    private TextView tvDailyGoal, tvLanguage;
    private LinearLayout rowDailyGoal, rowLanguage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        switchDarkMode = findViewById(R.id.switchDarkMode);
        switchSound = findViewById(R.id.switchSound);
        switchHaptic = findViewById(R.id.switchHaptic);
        switchDailyReminder = findViewById(R.id.switchDailyReminder);
        switchStreakAlert = findViewById(R.id.switchStreakAlert);
        switchAchievements = findViewById(R.id.switchAchievements);
        switchAutoplay = findViewById(R.id.switchAutoplay);
        switchOffline = findViewById(R.id.switchOffline);
        btnReminderTime = findViewById(R.id.btnReminderTime);
        btnClearCache = findViewById(R.id.btnClearCache);
        tvDailyGoal = findViewById(R.id.tvDailyGoal);
        tvLanguage = findViewById(R.id.tvLanguage);
        rowDailyGoal = findViewById(R.id.rowDailyGoal);
        rowLanguage = findViewById(R.id.rowLanguage);

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("⚙️ Cài đặt");

        loadCurrentValues();
        setupListeners();
        applyDynamicVersion();
        // LF-5 FIX: load ngôn ngữ động từ backend ngay khi vào Settings, để khi
        // user mở dialog đổi ngôn ngữ đã có danh sách mới nhất.
        loadAvailableLanguages();

        // UX-8 FIX: khôi phục scroll position sau khi recreate() vì toggle Dark Mode.
        final ScrollView sv = findViewById(R.id.scrollSettings);
        if (sv != null && sPendingScrollY >= 0) {
            final int y = sPendingScrollY;
            sPendingScrollY = -1;
            sv.post(() -> sv.scrollTo(0, y));
        }
    }

    /**
     * U26 FIX: read the real versionName from PackageInfo so the "Phiên bản"
     * label in the About card always matches build.gradle (was hardcoded
     * "Phiên bản 2.0.0").
     */
    private void applyDynamicVersion() {
        TextView tvVersion = findViewById(R.id.tvVersion);
        if (tvVersion == null) return;
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            tvVersion.setText("Phiên bản " + info.versionName);
        } catch (Exception ignore) {
            // fall back to the layout default
        }
    }

    private void loadCurrentValues() {
        switchDarkMode.setChecked(prefs.getBoolean(KEY_DARK_MODE, false));
        switchSound.setChecked(prefs.getBoolean(KEY_SOUND, true));
        switchHaptic.setChecked(prefs.getBoolean(KEY_HAPTIC, true));
        switchDailyReminder.setChecked(prefs.getBoolean(KEY_DAILY_REMINDER, true));
        switchStreakAlert.setChecked(prefs.getBoolean(KEY_STREAK_ALERT, true));
        switchAchievements.setChecked(prefs.getBoolean(KEY_ACHIEVEMENT_NOTIF, true));
        switchAutoplay.setChecked(prefs.getBoolean(KEY_AUTOPLAY, true));
        switchOffline.setChecked(prefs.getBoolean(KEY_OFFLINE, true));

        int hour = prefs.getInt(KEY_REMINDER_HOUR, 20);
        int min = prefs.getInt(KEY_REMINDER_MIN, 0);
        btnReminderTime.setText(String.format(Locale.getDefault(), "%02d:%02d", hour, min));

        int goal = prefs.getInt("daily_goal_xp", 50);
        tvDailyGoal.setText(goal + " XP");

        String lang = prefs.getString(OnboardingActivity.KEY_TARGET_LANG, "ja");
        tvLanguage.setText(displayLanguage(lang));
    }

    private void setupListeners() {
        // Dark mode — apply instantly using AppCompatDelegate
        switchDarkMode.setOnCheckedChangeListener((b, isChecked) -> {
            prefs.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES
                    : AppCompatDelegate.MODE_NIGHT_NO);
            // UX-8 FIX: lưu scroll position trước khi recreate để user không
            // bị nhảy ngược lên đầu màn hình khi toggle Dark Mode.
            ScrollView sv = findViewById(R.id.scrollSettings);
            sPendingScrollY = (sv != null) ? sv.getScrollY() : -1;
            recreate();
        });

        switchSound.setOnCheckedChangeListener((b, c) ->
                prefs.edit().putBoolean(KEY_SOUND, c).apply());
        switchHaptic.setOnCheckedChangeListener((b, c) ->
                prefs.edit().putBoolean(KEY_HAPTIC, c).apply());

        switchDailyReminder.setOnCheckedChangeListener((b, c) -> {
            prefs.edit().putBoolean(KEY_DAILY_REMINDER, c).apply();
            applyReminderSchedule();
        });

        switchStreakAlert.setOnCheckedChangeListener((b, c) ->
                prefs.edit().putBoolean(KEY_STREAK_ALERT, c).apply());
        switchAchievements.setOnCheckedChangeListener((b, c) ->
                prefs.edit().putBoolean(KEY_ACHIEVEMENT_NOTIF, c).apply());
        switchAutoplay.setOnCheckedChangeListener((b, c) ->
                prefs.edit().putBoolean(KEY_AUTOPLAY, c).apply());

        switchOffline.setOnCheckedChangeListener((b, c) -> {
            prefs.edit().putBoolean(KEY_OFFLINE, c).apply();
            if (!c) {
                OfflineCache.getInstance(getApplicationContext()).clear();
            }
        });

        btnReminderTime.setOnClickListener(v -> showTimePicker());
        btnClearCache.setOnClickListener(v -> confirmClearCache());

        rowDailyGoal.setOnClickListener(v -> showDailyGoalDialog());
        rowLanguage.setOnClickListener(v -> showLanguageDialog());
    }

    private void showTimePicker() {
        int hour = prefs.getInt(KEY_REMINDER_HOUR, 20);
        int min = prefs.getInt(KEY_REMINDER_MIN, 0);
        new TimePickerDialog(this, (view, h, m) -> {
            prefs.edit()
                    .putInt(KEY_REMINDER_HOUR, h)
                    .putInt(KEY_REMINDER_MIN, m)
                    .apply();
            btnReminderTime.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m));
            applyReminderSchedule();
        }, hour, min, true).show();
    }

    private void applyReminderSchedule() {
        boolean enabled = prefs.getBoolean(KEY_DAILY_REMINDER, true);
        int hour = prefs.getInt(KEY_REMINDER_HOUR, 20);
        int min = prefs.getInt(KEY_REMINDER_MIN, 0);
        if (enabled) {
            NotificationScheduler.scheduleDaily(getApplicationContext(), hour, min);
            Toast.makeText(this, "✅ Nhắc nhở mỗi ngày lúc "
                    + String.format(Locale.getDefault(), "%02d:%02d", hour, min),
                    Toast.LENGTH_SHORT).show();
        } else {
            NotificationScheduler.cancelDaily(getApplicationContext());
        }
    }

    private void showDailyGoalDialog() {
        final int[] choices = {20, 50, 100, 200};
        String[] labels = {"😌 Thoải mái — 20 XP",
                "🙂 Thường — 50 XP",
                "💪 Cố gắng — 100 XP",
                "🔥 Tham vọng — 200 XP"};
        new AlertDialog.Builder(this)
                .setTitle("🎯 Mục tiêu hàng ngày")
                .setItems(labels, (d, w) -> {
                    int xp = choices[w];
                    // Optimistic update of local prefs + UI so it feels instant
                    prefs.edit().putInt("daily_goal_xp", xp).apply();
                    tvDailyGoal.setText(xp + " XP");

                    // BUG B15 FIX: actually persist daily goal on the server.
                    // Without this, the goal would only live in SharedPreferences
                    // and the server's goal_met flag in daily_xp_logs would
                    // never update — so streak/quest rewards were silently lost.
                    // BUG #R3-C1 FIX: ApiClient.getApi() does not exist; the correct
                    // factory method is getService(Context).
                    LinguaApiService api = ApiClient.getService(getApplicationContext());
                    Map<String, Object> body = new HashMap<>();
                    body.put("dailyXpGoal", xp);
                    api.setDailyGoal(body).enqueue(new Callback<ApiResponse<Object>>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Object>> call,
                                               @NonNull Response<ApiResponse<Object>> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(SettingsActivity.this,
                                        "✅ Đã cập nhật mục tiêu",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(SettingsActivity.this,
                                        "⚠️ Không đồng bộ được mục tiêu lên máy chủ",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Object>> call,
                                              @NonNull Throwable t) {
                            Toast.makeText(SettingsActivity.this,
                                    "⚠️ Mất kết nối — mục tiêu sẽ đồng bộ lại sau",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    /**
     * LF-5 FIX: load danh sách ngôn ngữ động từ backend để nhất quán với
     * OnboardingActivity.LanguageFragment. Trước đây Settings hardcode 4 ngôn
     * ngữ → nếu backend thêm Pháp/Tây Ban Nha/Đức..., user thấy trong
     * Onboarding nhưng không đổi được trong Settings.
     *
     * Trong khi chờ API, ta seed bằng 4 ngôn ngữ mặc định để dialog vẫn hoạt
     * động được ngay cả khi offline.
     */
    private void loadAvailableLanguages() {
        // Seed fallback — hiển thị ngay khi API chưa trả về.
        langCodes.clear();
        langLabels.clear();
        langCodes.add("ja"); langLabels.add("🇯🇵 Tiếng Nhật");
        langCodes.add("en"); langLabels.add("🇬🇧 Tiếng Anh");
        langCodes.add("zh"); langLabels.add("🇨🇳 Tiếng Trung");
        langCodes.add("ko"); langLabels.add("🇰🇷 Tiếng Hàn");

        LinguaApiService api = ApiClient.getService(getApplicationContext());
        api.getLanguages().enqueue(new Callback<ApiResponse<java.util.List<Map<String, Object>>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<java.util.List<Map<String, Object>>>> call,
                                   @NonNull Response<ApiResponse<java.util.List<Map<String, Object>>>> r) {
                if (!r.isSuccessful() || r.body() == null || r.body().getData() == null) return;
                java.util.List<Map<String, Object>> data = r.body().getData();
                if (data.isEmpty()) return;
                langCodes.clear();
                langLabels.clear();
                for (Map<String, Object> lg : data) {
                    String code = String.valueOf(lg.getOrDefault("code", "?"));
                    String name = String.valueOf(lg.getOrDefault("name", code));
                    String flag = String.valueOf(lg.getOrDefault("flag_emoji",
                            lg.getOrDefault("flagEmoji", "🏳️")));
                    if ("null".equals(flag)) flag = "🏳️";
                    langCodes.add(code);
                    langLabels.add(flag + " " + name);
                }
                // Cập nhật nhãn hiện tại nếu ngôn ngữ đã chọn nằm trong list mới.
                String currentCode = prefs.getString(OnboardingActivity.KEY_TARGET_LANG, "ja");
                int idx = langCodes.indexOf(currentCode);
                if (idx >= 0) tvLanguage.setText(langLabels.get(idx));
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<java.util.List<Map<String, Object>>>> call,
                                  @NonNull Throwable t) {
                // Im lặng — vẫn dùng fallback 4 ngôn ngữ mặc định.
            }
        });
    }

    private void showLanguageDialog() {
        // LF-5 FIX: dùng danh sách động (đã load từ backend) thay vì hardcode.
        if (langCodes.isEmpty()) {
            loadAvailableLanguages();
        }
        final String[] codes = langCodes.toArray(new String[0]);
        final String[] labels = langLabels.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle("🌍 Ngôn ngữ đang học")
                .setItems(labels, (d, w) -> {
                    final String newCode = codes[w];
                    prefs.edit().putString(OnboardingActivity.KEY_TARGET_LANG, newCode).apply();
                    tvLanguage.setText(labels[w]);

                    // LF-4 FIX: sync ngôn ngữ lên backend để recommendation engine
                    // biết user đổi target language. Trước đây chỉ lưu local nên
                    // backend vẫn nghĩ user đang học ngôn ngữ cũ; nếu user login
                    // trên thiết bị khác, language bị reset.
                    LinguaApiService api = ApiClient.getService(getApplicationContext());
                    Map<String, Object> body = new HashMap<>();
                    body.put("targetLanguage", newCode);
                    body.put("target_language", newCode); // alternate snake_case key
                    api.updateProfile(body).enqueue(new Callback<com.lingua.app.models.ApiResponse<com.lingua.app.models.User>>() {
                        @Override
                        public void onResponse(@NonNull Call<com.lingua.app.models.ApiResponse<com.lingua.app.models.User>> c,
                                               @NonNull Response<com.lingua.app.models.ApiResponse<com.lingua.app.models.User>> r) {
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Toast.makeText(SettingsActivity.this,
                                        "✅ Đã đổi ngôn ngữ đang học",
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(SettingsActivity.this,
                                        "⚠️ Chưa đồng bộ được — đã lưu cục bộ",
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<com.lingua.app.models.ApiResponse<com.lingua.app.models.User>> c,
                                              @NonNull Throwable t) {
                            Toast.makeText(SettingsActivity.this,
                                    "⚠️ Mất kết nối — ngôn ngữ sẽ đồng bộ sau",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void confirmClearCache() {
        new AlertDialog.Builder(this)
                .setTitle("Xoá cache")
                .setMessage("Tất cả dữ liệu offline (từ vựng, ngữ pháp đã tải) sẽ bị xoá. Bạn có chắc chắn?")
                .setPositiveButton("Xoá", (d, w) -> {
                    OfflineCache.getInstance(getApplicationContext()).clear();
                    Toast.makeText(this, "✅ Đã xoá cache", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private String displayLanguage(String code) {
        if (code == null) return "🇯🇵 Tiếng Nhật";
        switch (code) {
            case "en": return "🇬🇧 Tiếng Anh";
            case "zh": return "🇨🇳 Tiếng Trung";
            case "ko": return "🇰🇷 Tiếng Hàn";
            default: return "🇯🇵 Tiếng Nhật";
        }
    }
}
