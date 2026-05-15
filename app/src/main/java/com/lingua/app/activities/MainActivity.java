package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        session = SessionManager.getInstance(this);
        apiService = ApiClient.getService(this);

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
            // Allow claiming straight from the home preview when completed.
            apiService.claimQuest(quest.id).enqueue(new Callback<ApiResponse<Object>>() {
                @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                    if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                        Toast.makeText(MainActivity.this, "🎉 +" + quest.rewardGems + " 💎", Toast.LENGTH_SHORT).show();
                        loadDailyQuests();
                    }
                }
                @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {}
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
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipe = findViewById(R.id.swipeRefresh);
        if (swipe != null) {
            swipe.setOnRefreshListener(() -> {
                loadStats();
                loadEnrollments();
                loadDailyQuests();
                loadReviewQueue();
                swipe.postDelayed(() -> swipe.setRefreshing(false), 800);
            });
        }

        BottomNavigationView nav = findViewById(R.id.bottomNavigation);
        // 6.3 FIX: đảm bảo tab Home luôn được highlight khi quay về MainActivity.
        nav.setSelectedItemId(R.id.nav_home);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // Already home
            } else if (id == R.id.nav_vocabulary) {
                navigateToTab(VocabularyActivity.class);
            } else if (id == R.id.nav_flashcard) {
                navigateToTab(SrsDeckPickerActivity.class);
            } else if (id == R.id.nav_ai) {
                navigateToTab(AIRoleplayActivity.class);
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

    @Override
    protected void onResume() {
        super.onResume();
        loadStats();
        loadEnrollments();
        loadDailyQuests();
        loadReviewQueue();
    }

    // -----------------------------------------------------------------------
    // 1.3 FIX: Review queue integration with /lessons/review-queue.
    // -----------------------------------------------------------------------
    private List<Map<String, Object>> reviewQueueCache = new ArrayList<>();

    private void loadReviewQueue() {
        apiService.getReviewQueue().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    List<Map<String, Object>> data = r.body().getData();
                    runOnUiThread(() -> updateReviewCard(data));
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {}
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

    private void openFirstReviewLesson() {
        if (reviewQueueCache.isEmpty()) {
            Toast.makeText(this, "Không có bài học nào cần ôn lúc này 🎉", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> first = reviewQueueCache.get(0);
        Object idObj = first.get("id");
        long lessonId = 0;
        if (idObj instanceof Number) lessonId = ((Number) idObj).longValue();
        else if (idObj != null) {
            try { lessonId = Long.parseLong(idObj.toString()); } catch (Exception ignore) {}
        }
        if (lessonId <= 0) {
            Toast.makeText(this, "Lỗi: không lấy được lessonId", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, PracticeActivity.class);
        i.putExtra("lessonId", lessonId);
        startActivity(i);
    }

    private void loadStats() {
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
            }
            @Override public void onFailure(Call<ApiResponse<GamificationStats>> call, Throwable t) {
                // U20 FIX: Snackbar voi action "Thu lai" cho loadStats.
                runOnUiThread(() -> showRetrySnackbar("Khong tai duoc thong tin tien do", v -> loadStats()));
            }
        });
    }

    private int readUserDailyGoal() {
        return getSharedPreferences("LinguaPrefs", MODE_PRIVATE)
                .getInt("daily_goal_xp", DEFAULT_DAILY_GOAL_XP);
    }

    private void loadEnrollments() {
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
            }
            @Override public void onFailure(Call<ApiResponse<List<Enrollment>>> c, Throwable t) {
                // U20 FIX: Snackbar voi action "Thu lai" thay vi de user mac ket.
                runOnUiThread(() -> {
                    tvEnrollmentsEmpty.setVisibility(View.VISIBLE);
                    showRetrySnackbar("Khong tai duoc danh sach khoa hoc", v -> loadEnrollments());
                });
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
            Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Thu lai", onRetry)
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
                if (best == null) best = e;
                // Prefer the most recently accessed enrollment with a next lesson.
                if (e.lastAccessedAt != null && best.lastAccessedAt != null
                        && e.lastAccessedAt.compareTo(best.lastAccessedAt) > 0) {
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

    private void loadDailyQuests() {
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
            }
            @Override public void onFailure(Call<ApiResponse<List<DailyQuest>>> c, Throwable t) {}
        });
    }

    private void openEnrollment(Enrollment e) {
        Intent i = new Intent(this, CourseDetailActivity.class);
        i.putExtra("courseId", e.courseId);
        startActivity(i);
    }
}
