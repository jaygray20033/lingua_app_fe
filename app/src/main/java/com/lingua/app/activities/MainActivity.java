package com.lingua.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Map;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.lingua.app.R;
import com.lingua.app.adapters.EnrollmentAdapter;
import com.lingua.app.adapters.QuestAdapter;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.DailyQuest;
import com.lingua.app.models.Enrollment;
import com.lingua.app.models.GamificationStats;
import com.lingua.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Home / Dashboard.
 *
 * Replaces the old "empty welcome page" with a real dashboard:
 *   - Daily XP goal progress bar (todayXp / dailyGoal).
 *   - "Continue learning" button that jumps to the next unfinished lesson
 *     (uses next_lesson_id from the most recently accessed enrollment).
 *   - Enrolled courses list (GET /api/enrollments).
 *   - 1–2 daily quests preview (GET /api/gamification/quests).
 *   - Quick shortcuts: Grammar / Mock test / Favorites / Shadowing.
 */
public class MainActivity extends AppCompatActivity {
    private static final int DEFAULT_DAILY_GOAL_XP = 50;

    private TextView tvUserName, tvXp, tvStreak, tvHearts, tvLevel;
    private TextView tvDailyGoalText, tvEnrollmentsEmpty;
    private ProgressBar progressDailyGoal;
    private Button btnContinue;
    private RecyclerView recyclerEnrollments, recyclerQuestPreview;
    private LinguaApiService apiService;
    private SessionManager session;

    private final List<Enrollment> enrollments = new ArrayList<>();
    private EnrollmentAdapter enrollmentAdapter;
    private final List<DailyQuest> questPreview = new ArrayList<>();
    private QuestAdapter questAdapter;

    // BUG #11 FIX: cooldown 30s cho onResume() để tránh gọi 4 API requests mỗi
    // lần user quay về Home (kể cả khi chỉ tắt màn hình rồi bật lại).
    private static final long REFRESH_COOLDOWN_MS = 30_000L;
    private long lastRefreshMs = 0;

    // BUG #R3-H4 FIX: trên Android 13+ (API 33), permission POST_NOTIFICATIONS
    // phải được user grant runtime. Trước đây app chỉ khai báo trong manifest
    // và schedule notification mà không bao giờ request quyền → notification
    // im lặng nếu user chưa cấp. ActivityResultLauncher dưới đây xử lý
    // request, và nếu user từ chối thì tự tắt cờ daily reminder trong prefs
    // để tránh schedule alarm vô ích.
    private final ActivityResultLauncher<String> notifPermLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    SharedPreferences p = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
                    // Don't disable the user-facing toggle outright — just stop
                    // scheduling. The Settings screen can re-request when user
                    // re-enables the switch.
                    com.lingua.app.utils.NotificationScheduler.cancelDaily(getApplicationContext());
                    Toast.makeText(this,
                            "⚠️ Bạn đã từ chối quyền thông báo. Nhắc nhở hàng ngày sẽ không hiển thị.",
                            Toast.LENGTH_LONG).show();
                }
            });

    /**
     * BUG #R3-H4 FIX: yêu cầu POST_NOTIFICATIONS permission khi app khởi động
     * lần đầu (chỉ trên Android 13+). Nếu permission đã grant thì không làm gì.
     * Nếu user đã chọn "Không hiển thị nữa" trước đó (shouldShowRationale =
     * false sau lần deny) thì cũng skip.
     */
    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        // Only request if user has not permanently denied. Otherwise we'd be
        // spamming the system permission dialog (which would just be a no-op).
        SharedPreferences p = getSharedPreferences(SettingsActivity.PREFS, MODE_PRIVATE);
        if (p.getBoolean("notif_perm_asked", false)
                && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            return;
        }
        p.edit().putBoolean("notif_perm_asked", true).apply();
        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        session = SessionManager.getInstance(this);
        apiService = ApiClient.getService(this);

        // BUG #R3-H4 FIX: yêu cầu POST_NOTIFICATIONS runtime trên Android 13+
        // để daily reminder / streak alert / achievement notification thực sự
        // hiển thị thay vì fail silent.
        ensureNotificationPermission();

        // UX-5 FIX: nếu lần finishOnboarding() trước fail vì offline / 5xx,
        // pending sync sẽ được retry tự động khi user mở lại app (online).
        OnboardingActivity.retryPendingSyncIfNeeded(getApplicationContext());

        tvUserName = findViewById(R.id.tvUserName);
        tvXp = findViewById(R.id.tvXp);
        tvStreak = findViewById(R.id.tvStreak);
        tvHearts = findViewById(R.id.tvHearts);
        tvLevel = findViewById(R.id.tvLevel);
        tvDailyGoalText = findViewById(R.id.tvDailyGoalText);
        tvEnrollmentsEmpty = findViewById(R.id.tvEnrollmentsEmpty);
        progressDailyGoal = findViewById(R.id.progressDailyGoal);
        btnContinue = findViewById(R.id.btnContinue);
        recyclerEnrollments = findViewById(R.id.recyclerEnrollments);
        recyclerQuestPreview = findViewById(R.id.recyclerQuestPreview);

        tvUserName.setText("👤 " + session.getDisplayName());

        recyclerEnrollments.setLayoutManager(new LinearLayoutManager(this));
        enrollmentAdapter = new EnrollmentAdapter(enrollments, this::openEnrollment);
        recyclerEnrollments.setAdapter(enrollmentAdapter);

        recyclerQuestPreview.setLayoutManager(new LinearLayoutManager(this));
        questAdapter = new QuestAdapter(questPreview, (quest, position) -> {
            // BUG #12 FIX: kiểm tra quest đã hoàn thành & chưa claim trước khi
            // gọi API. Trước đây mọi quest đều có thể claim → backend trả lỗi
            // nhưng app không có phản hồi phù hợp cho user.
            if (quest.completed != 1) {
                Toast.makeText(MainActivity.this,
                        "⏳ Chưa hoàn thành nhiệm vụ này!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (quest.claimedAt != null) {
                Toast.makeText(MainActivity.this,
                        "✅ Nhiệm vụ này đã được nhận thưởng!", Toast.LENGTH_SHORT).show();
                return;
            }
            // Allow claiming straight from the home preview when completed.
            apiService.claimQuest(quest.id).enqueue(new Callback<ApiResponse<Object>>() {
                @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                    // BUG-022 FIX: tránh BadTokenException khi user đóng MainActivity
                    // trước khi callback về (Toast/loadDailyQuests vẫn chạy trên
                    // context đã destroyed).
                    if (isFinishing() || isDestroyed()) return;
                    if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                        Toast.makeText(MainActivity.this, "🎉 +" + quest.rewardGems + " 💎", Toast.LENGTH_SHORT).show();
                        loadDailyQuests();
                    } else {
                        // BUG #12 FIX: hiển thị lỗi từ backend thay vì im lặng.
                        String msg = (r.body() != null && r.body().getMessage() != null)
                                ? r.body().getMessage()
                                : "Không nhận được thưởng. Vui lòng thử lại.";
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {
                    if (isFinishing() || isDestroyed()) return;
                    // BUG #12 FIX: thông báo network error cho user.
                    Toast.makeText(MainActivity.this,
                            "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
        recyclerQuestPreview.setAdapter(questAdapter);

        findViewById(R.id.btnBrowseCourses).setOnClickListener(v ->
                startActivity(new Intent(this, MyCoursesActivity.class).putExtra("mode", "browse")));
        findViewById(R.id.btnGrammar).setOnClickListener(v ->
                startActivity(new Intent(this, GrammarActivity.class)));
        findViewById(R.id.btnMockTest).setOnClickListener(v ->
                startActivity(new Intent(this, MockTestActivity.class)));
        findViewById(R.id.btnFavorites).setOnClickListener(v ->
                startActivity(new Intent(this, FavoritesActivity.class)));
        findViewById(R.id.btnShadowing).setOnClickListener(v ->
                startActivity(new Intent(this, ShadowingActivity.class)));

        // 1.3 FIX: Review card click opens the first lesson in the spaced-review
        // queue. Background load updates the badge so the user sees how many
        // lessons are waiting.
        View cardReview = findViewById(R.id.cardReview);
        if (cardReview != null) {
            cardReview.setOnClickListener(v -> openFirstReviewLesson());
        }

        // 6.3 FIX: pull-to-refresh — gọi lại tất cả các loader.
        // BUG #9 FIX: dùng AtomicInteger đếm callback thay vì ẩn spinner sau
        // 800ms cố định. Trên mạng 3G chậm spinner biến mất trước khi data
        // refresh xong → user tưởng đã refresh nhưng vẫn xem dữ liệu cũ.
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe = findViewById(R.id.swipeRefresh);
        if (swipe != null) {
            swipe.setOnRefreshListener(() -> {
                final AtomicInteger pending = new AtomicInteger(4);
                final Runnable onDone = () -> {
                    if (pending.decrementAndGet() <= 0) {
                        swipe.setRefreshing(false);
                    }
                };
                loadStats(onDone);
                loadEnrollments(onDone);
                loadDailyQuests(onDone);
                loadReviewQueue(onDone);
                // Safety timeout: nếu sau 15s vẫn còn pending thì ẩn spinner
                // (tránh trường hợp kẹt spinner do timeout network).
                swipe.postDelayed(() -> {
                    if (swipe.isRefreshing()) swipe.setRefreshing(false);
                }, 15_000);
                // Đồng thời reset cooldown để onResume không gọi lại ngay.
                lastRefreshMs = System.currentTimeMillis();
            });
        }

        BottomNavigationView nav = findViewById(R.id.bottomNavigation);
        // 6.3 FIX: đảm bảo tab Home luôn được highlight khi quay về MainActivity.
        nav.setSelectedItemId(R.id.nav_home);
        // FIX 2.4: Updated to 4 tabs: Trang chủ / Khóa học / Từ vựng / Cá nhân
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // Already home
            } else if (id == R.id.nav_courses) {
                navigateToTab(MyCoursesActivity.class);
            } else if (id == R.id.nav_vocabulary) {
                navigateToTab(VocabularyActivity.class);
            } else if (id == R.id.nav_profile) {
                navigateToTab(ProfileActivity.class);
            }
            return true;
        });
    }

    /**
     * BUG 4 FIX: dùng FLAG_ACTIVITY_REORDER_TO_FRONT kết hợp singleTop ở
     * Manifest để tránh phình to back stack khi chuyển qua lại giữa các tab
     * bottom nav. Nếu Activity đã tồn tại trong stack thì chỉ đưa nó lên top
     * thay vì tạo instance mới.
     */
    private void navigateToTab(Class<?> target) {
        Intent i = new Intent(this, target);
        i.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    /**
     * BUG-002 FIX: MainActivity có launchMode="singleTop" trong manifest, nên
     * khi LessonResultActivity gọi startActivity(MainActivity) với
     * FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP + putExtra("forceRefresh", true),
     * Android KHÔNG tạo lại Activity mà gọi onNewIntent(intent) rồi onResume().
     * Nếu không gọi setIntent(intent), getIntent() vẫn trả về intent cũ (không có
     * extra "forceRefresh") → onResume() vẫn bị cooldown 30s chặn → XP/streak
     * không cập nhật ngay sau khi user hoàn thành lesson.
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // BUG U8 FIX : re-highlighter le tab Home à chaque retour dans MainActivity.
        // Avant : `setSelectedItemId` n'était appelé que dans onCreate → si l'user
        // partait vers WordDetailActivity puis revenait, le tab Home pouvait être
        // dé-sélectionné (bug visuel : aucun tab en surbrillance).
        BottomNavigationView navResume = findViewById(R.id.bottomNavigation);
        if (navResume != null && navResume.getSelectedItemId() != R.id.nav_home) {
            navResume.setSelectedItemId(R.id.nav_home);
        }

        // BUG #11 FIX: cooldown 30s để tránh gọi 4 API requests mỗi lần user
        // quay về Home (kể cả khi chỉ tắt màn hình rồi bật lại, hoặc quay từ
        // Settings về). Tốn pin, tốn dữ liệu, có thể bị rate-limit.
        //
        // BUG #L2 FIX: nhưng nếu Activity được start lại với extra "forceRefresh"
        // (ví dụ sau khi user hoàn thành lesson và quay về Home từ LessonResultActivity),
        // ta bypass cooldown để XP/streak/quest progress hiện ra ngay lập tức,
        // tránh trải nghiệm "đã học xong nhưng XP không tăng".
        //
        // R4-M7 FIX: chỉ honor force-refresh nếu đã qua min-interval (5s) kể
        // từ lần refresh gần nhất. Trước đây mỗi Activity B set force_refresh_main
        // rồi finish → 4 API calls; user spam có thể tạo 40+ calls/phút → rate
        // limit. Giờ có force flag cũng phải đợi 5s.
        long now = System.currentTimeMillis();
        long sinceLast = now - lastRefreshMs;
        boolean canHonorForce = sinceLast > 5000L; // R4-M7: min 5s giữa 2 lần force
        boolean force = getIntent() != null
                && getIntent().getBooleanExtra("forceRefresh", false);
        if (force && canHonorForce) {
            getIntent().removeExtra("forceRefresh");
            lastRefreshMs = 0; // reset cooldown
        } else if (force) {
            // Force flag có nhưng vừa refresh xong → bỏ flag, không refresh nữa.
            getIntent().removeExtra("forceRefresh");
        }

        // BUG-011 FIX: ProfileActivity đặt cờ "force_refresh_main" sau khi update
        // daily goal thành công. Khi user back về Home, ta detect cờ này và
        // bypass cooldown 30s để tiến độ daily goal hiển thị đúng ngay lập tức.
        // R4-M7 FIX: cùng áp dụng min-interval 5s.
        SharedPreferences p = getSharedPreferences("LinguaPrefs", MODE_PRIVATE);
        if (p.getBoolean("force_refresh_main", false)) {
            p.edit().remove("force_refresh_main").apply();
            if (canHonorForce) {
                lastRefreshMs = 0;
            }
        }

        if (now - lastRefreshMs < REFRESH_COOLDOWN_MS) {
            return;
        }
        lastRefreshMs = now;
        loadStats();
        loadEnrollments();
        loadDailyQuests();
        loadReviewQueue();
    }

    // -----------------------------------------------------------------------
    // 1.3 FIX: Review queue integration with /lessons/review-queue.
    // -----------------------------------------------------------------------
    private List<Map<String, Object>> reviewQueueCache = new ArrayList<>();

    private void loadReviewQueue() { loadReviewQueue(null); }

    // BUG #9 FIX: nhận onDone callback để báo hoàn thành cho SwipeRefresh.
    private void loadReviewQueue(Runnable onDone) {
        apiService.getReviewQueue().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    List<Map<String, Object>> data = r.body().getData();
                    runOnUiThread(() -> updateReviewCard(data));
                }
                if (onDone != null) runOnUiThread(onDone);
            }
            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                if (onDone != null) runOnUiThread(onDone);
            }
        });
    }

    private void updateReviewCard(List<Map<String, Object>> data) {
        reviewQueueCache.clear();
        if (data != null) reviewQueueCache.addAll(data);
        TextView tvSubtitle = findViewById(R.id.tvReviewSubtitle);
        TextView tvBadge = findViewById(R.id.tvReviewBadge);
        if (tvSubtitle == null || tvBadge == null) return;
        int count = reviewQueueCache.size();
        if (count == 0) {
            tvSubtitle.setText("Không có bài cần ôn — tuyệt vời! 🎉");
            tvBadge.setVisibility(View.GONE);
        } else {
            tvSubtitle.setText(count + " bài học đang chờ bạn ôn lại");
            tvBadge.setText(String.valueOf(count));
            tvBadge.setVisibility(View.VISIBLE);
        }
    }

    /**
     * R4-H3 FIX: robust lessonId extraction từ review queue item.
     *  - Hỗ trợ kiểu Number, String, và nested object {value: N} / {id: N}
     *  - Fallback sang field "lessonId" nếu "id" không hợp lệ
     *  - Log chi tiết khi parse fail để dev dễ debug (không Crashlytics trong
     *    sandbox, dùng Log.e tạm)
     *  - User-visible message có gợi ý thay vì chỉ "Lỗi: không lấy được lessonId".
     */
    private void openFirstReviewLesson() {
        if (reviewQueueCache.isEmpty()) {
            Toast.makeText(this, "Không có bài học nào cần ôn lúc này 🎉", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> first = reviewQueueCache.get(0);
        long lessonId = extractLessonId(first);
        if (lessonId <= 0) {
            android.util.Log.e("MainActivity",
                    "openFirstReviewLesson: failed to parse lessonId from item=" + first);
            Toast.makeText(this,
                    "Không xác định được bài học — vui lòng kéo xuống để tải lại",
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, PracticeActivity.class);
        i.putExtra("lessonId", lessonId);
        startActivity(i);
    }

    /**
     * R4-H3 FIX: defensive lessonId parser. Thử nhiều key và nhiều kiểu
     * dữ liệu khác nhau để không silent-fail khi backend thay đổi format.
     */
    private long extractLessonId(Map<String, Object> item) {
        if (item == null) return 0L;
        // Thứ tự ưu tiên key: lessonId > id
        String[] keys = { "lessonId", "lesson_id", "id" };
        for (String key : keys) {
            Object v = item.get(key);
            long parsed = parseLongFlexible(v);
            if (parsed > 0) return parsed;
        }
        return 0L;
    }

    @SuppressWarnings("unchecked")
    private long parseLongFlexible(Object v) {
        if (v == null) return 0L;
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof String) {
            try { return Long.parseLong(((String) v).trim()); } catch (Exception ignore) {}
            return 0L;
        }
        if (v instanceof Map) {
            // Nested object: thử lấy với key phổ biến.
            try {
                Map<String, Object> m = (Map<String, Object>) v;
                String[] subKeys = { "value", "id", "lessonId" };
                for (String sk : subKeys) {
                    long parsed = parseLongFlexible(m.get(sk));
                    if (parsed > 0) return parsed;
                }
            } catch (Exception ignore) {}
            return 0L;
        }
        // Fallback: chức đã từng work — try toString().
        try { return Long.parseLong(v.toString().trim()); } catch (Exception ignore) {}
        return 0L;
    }

    private void loadStats() { loadStats(null); }

    // BUG #9 FIX: nhận onDone callback để báo hoàn thành cho SwipeRefresh.
    private void loadStats(Runnable onDone) {
        apiService.getMyStats().enqueue(new Callback<ApiResponse<GamificationStats>>() {
            @Override
            public void onResponse(Call<ApiResponse<GamificationStats>> call, Response<ApiResponse<GamificationStats>> resp) {
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    GamificationStats s = resp.body().getData();
                    runOnUiThread(() -> {
                        tvXp.setText("⚡ " + s.totalXp + " XP");
                        tvStreak.setText("🔥 " + s.getStreak());
                        tvHearts.setText("❤️ " + s.hearts);
                        if (tvLevel != null) tvLevel.setText("Lv." + s.level);

                        // Daily goal: prefer server-provided value, otherwise local fallback.
                        int serverGoal = s.getDailyGoal();
                        int goal = serverGoal > 0 ? serverGoal : readUserDailyGoal();
                        int today = s.getTodayXp();
                        progressDailyGoal.setMax(Math.max(goal, 1));
                        progressDailyGoal.setProgress(Math.min(today, goal));
                        tvDailyGoalText.setText(today + " / " + goal + " XP");
                    });
                }
                if (onDone != null) runOnUiThread(onDone);
            }
            @Override public void onFailure(Call<ApiResponse<GamificationStats>> call, Throwable t) {
                // U20 FIX: Snackbar voi action "Thu lai" cho loadStats.
                runOnUiThread(() -> showRetrySnackbar("Không tải được thông tin tiến độ", v -> loadStats()));
                if (onDone != null) runOnUiThread(onDone);
            }
        });
    }

    private int readUserDailyGoal() {
        return getSharedPreferences("LinguaPrefs", MODE_PRIVATE)
                .getInt("daily_goal_xp", DEFAULT_DAILY_GOAL_XP);
    }

    private void loadEnrollments() { loadEnrollments(null); }

    // BUG #9 FIX: nhận onDone callback để báo hoàn thành cho SwipeRefresh.
    private void loadEnrollments(Runnable onDone) {
        apiService.getMyEnrollments().enqueue(new Callback<ApiResponse<List<Enrollment>>>() {
            @Override public void onResponse(Call<ApiResponse<List<Enrollment>>> c, Response<ApiResponse<List<Enrollment>>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    List<Enrollment> data = r.body().getData();
                    runOnUiThread(() -> {
                        enrollments.clear();
                        if (data != null) enrollments.addAll(data);
                        enrollmentAdapter.notifyDataSetChanged();
                        tvEnrollmentsEmpty.setVisibility(enrollments.isEmpty() ? View.VISIBLE : View.GONE);
                        configureContinueButton();
                    });
                }
                if (onDone != null) runOnUiThread(onDone);
            }
            @Override public void onFailure(Call<ApiResponse<List<Enrollment>>> c, Throwable t) {
                // U20 FIX: Snackbar voi action "Thu lai" thay vi de user mac ket.
                runOnUiThread(() -> {
                    tvEnrollmentsEmpty.setVisibility(View.VISIBLE);
                    showRetrySnackbar("Không tải được danh sách khóa học", v -> loadEnrollments());
                });
                if (onDone != null) runOnUiThread(onDone);
            }
        });
    }

    /**
     * U20 FIX: helper hien thi Snackbar voi action "Thu lai" thay vi Toast im lang.
     * User co the bam Thu lai ngay tai chinh man hinh thay vi phai thoat ra/vao lai.
     */
    private void showRetrySnackbar(String message, android.view.View.OnClickListener onRetry) {
        android.view.View root = findViewById(android.R.id.content);
        if (root == null) return;
        try {
            // R4-L6 FIX: text "Thu lai" thiếu dấu (do mojibake khi save file)
            // → sửa lại "Thử lại". Logn long-term nên move vào strings.xml.
            Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Thử lại", onRetry)
                    .show();
        } catch (Throwable t) {
            // Fallback Toast neu Snackbar khong khoi tao duoc (theme khong tuong thich,...)
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void configureContinueButton() {
        Enrollment best = null;
        for (Enrollment e : enrollments) {
            if (e.nextLessonId != null && e.nextLessonId > 0) {
                if (best == null) {
                    best = e;
                    continue;
                }
                // BUG #R3-M7 FIX: logic cũ chỉ swap khi CẢ HAI enrollment đều
                // có lastAccessedAt != null. Nếu best.lastAccessedAt == null
                // (vd. enrollment vừa tạo, chưa học bài nào) thì e (có
                // lastAccessed mới hơn) sẽ KHÔNG BAO GIỜ thay thế best →
                // Continue button trỏ đến course user chưa học gần đây.
                //
                // Logic mới: ưu tiên enrollment có lastAccessedAt khác null;
                // nếu cả hai cùng có thì so sánh timestamp; nếu best chưa có
                // mà e có → swap để chọn enrollment user thực sự đang học.
                if (e.lastAccessedAt != null
                        && (best.lastAccessedAt == null
                            || e.lastAccessedAt.compareTo(best.lastAccessedAt) > 0)) {
                    best = e;
                }
            }
        }
        if (best == null) {
            btnContinue.setVisibility(View.GONE);
            return;
        }
        final Enrollment target = best;
        String label = "▶ Tiếp tục: " + (target.nextLessonTitle != null ? target.nextLessonTitle : target.getDisplayTitle());
        btnContinue.setText(label);
        btnContinue.setVisibility(View.VISIBLE);
        btnContinue.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, PracticeActivity.class);
            i.putExtra("lessonId", target.nextLessonId);
            startActivity(i);
        });
    }

    private void loadDailyQuests() { loadDailyQuests(null); }

    // BUG #9 FIX: nhận onDone callback để báo hoàn thành cho SwipeRefresh.
    private void loadDailyQuests(Runnable onDone) {
        apiService.getDailyQuests().enqueue(new Callback<ApiResponse<List<DailyQuest>>>() {
            @Override public void onResponse(Call<ApiResponse<List<DailyQuest>>> c, Response<ApiResponse<List<DailyQuest>>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    List<DailyQuest> data = r.body().getData();
                    runOnUiThread(() -> {
                        questPreview.clear();
                        if (data != null) {
                            int limit = Math.min(2, data.size()); // top 2 quests preview
                            for (int i = 0; i < limit; i++) questPreview.add(data.get(i));
                        }
                        questAdapter.notifyDataSetChanged();
                    });
                }
                if (onDone != null) runOnUiThread(onDone);
            }
            @Override public void onFailure(Call<ApiResponse<List<DailyQuest>>> c, Throwable t) {
                if (onDone != null) runOnUiThread(onDone);
            }
        });
    }

    private void openEnrollment(Enrollment e) {
        Intent i = new Intent(this, CourseDetailActivity.class);
        i.putExtra("courseId", e.courseId);
        startActivity(i);
    }
}
