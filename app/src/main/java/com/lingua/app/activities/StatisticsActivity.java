package com.lingua.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;

import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.GamificationStats;
import com.lingua.app.views.BarChartView;
import com.lingua.app.views.StreakCalendarView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Statistics / progress screen.
 *
 * Renders:
 *   - Top summary tiles (total XP, current streak, level).
 *   - 7-day XP bar chart (from server stats.weeklyXp / dailyXpHistory).
 *   - 30-day streak calendar (from server stats.streakCalendar / activity).
 *   - Detailed metrics (words learned, lessons done, accuracy, SRS reviewed,
 *     best streak).
 *
 * The custom views are pure-Canvas implementations under com.lingua.app.views,
 * so we do not need to ship MPAndroidChart.
 */
public class StatisticsActivity extends AppCompatActivity {
    private TextView tvTotalXp, tvStreakStat, tvLevelStat;
    private TextView tvWordsLearned, tvLessonsDone, tvAccuracy, tvSrsReviewed, tvBestStreak;
    private BarChartView barChart;
    private StreakCalendarView streakCalendar;
    private ProgressBar progressBar;
    private LinguaApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        apiService = ApiClient.getService(this);

        tvTotalXp = findViewById(R.id.tvTotalXp);
        tvStreakStat = findViewById(R.id.tvStreakStat);
        tvLevelStat = findViewById(R.id.tvLevelStat);
        tvWordsLearned = findViewById(R.id.tvWordsLearned);
        tvLessonsDone = findViewById(R.id.tvLessonsDone);
        tvAccuracy = findViewById(R.id.tvAccuracy);
        tvSrsReviewed = findViewById(R.id.tvSrsReviewed);
        tvBestStreak = findViewById(R.id.tvBestStreak);
        barChart = findViewById(R.id.barChart);
        streakCalendar = findViewById(R.id.streakCalendar);
        progressBar = findViewById(R.id.progressBar);

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("📈 Tiến độ học");
        loadStats();
    }

    /** BUG #19 FIX: Comment tiếng Pháp → tiếng Việt.
     *  Lưu trữ dữ liệu analytics trả về từ /gamification/analytics. */
    private Map<String, Object> analyticsData;

    private void loadStats() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getMyStats().enqueue(new Callback<ApiResponse<GamificationStats>>() {
            @Override
            public void onResponse(Call<ApiResponse<GamificationStats>> call, Response<ApiResponse<GamificationStats>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().getData() != null) {
                    render(response.body().getData());
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<GamificationStats>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                // 6.9 + U20 FIX: Snackbar với action "Thử lại" thay vì Toast im lặng.
                // User có thể bấm Thử lại ngay tại màn hình thay vì phải thoát/vào lại.
                runOnUiThread(() -> showRetrySnackbar(
                        "Không tải được số liệu thống kê. Kiểm tra kết nối mạng.",
                        v -> loadStats()));
            }
        });
        // BUG #19 FIX: comment tiếng Pháp → tiếng Việt.
        // TODO 1.1 + 3.4 — gọi thêm /gamification/analytics để lấy thống kê
        // nâng cao (XP theo ngày trong 30 ngày, ngày hoạt động, tỉ lệ xu hướng %).
        loadAnalytics();
        // TODO 1.1 — kích hoạt đánh giá phía server để mở khóa các achievement
        // user có thể đã đạt (best-effort).
        apiService.evaluateAchievements().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override public void onResponse(Call<ApiResponse<Map<String, Object>>> c, Response<ApiResponse<Map<String, Object>>> r) {}
            @Override public void onFailure(Call<ApiResponse<Map<String, Object>>> c, Throwable t) {}
        });
    }

    private void loadAnalytics() {
        apiService.getAnalytics().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> c,
                                   Response<ApiResponse<Map<String, Object>>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    analyticsData = r.body().getData();
                    // 6.9 FIX: sau khi analytics về, re-render các field detail
                    // để fallback từ analyticsData khi GamificationStats trả 0.
                    runOnUiThread(() -> renderAnalyticsFallback());
                }
            }
            @Override public void onFailure(Call<ApiResponse<Map<String, Object>>> c, Throwable t) {
                // 7.3 FIX: thông báo cho user biết biểu đồ XP đang dùng dữ liệu ước tính.
                // Trước đây onFailure im lặng -> user không biết tại sao chart hiển
                // thị dữ liệu ước tính thay vì dữ liệu thực.
                runOnUiThread(() -> android.widget.Toast.makeText(StatisticsActivity.this,
                        "Không tải được dữ liệu chi tiết. Đang dùng dữ liệu ước tính.",
                        android.widget.Toast.LENGTH_SHORT).show());
            }
        });
    }

    /**
     * 6.9 FIX: nếu các field detail (wordsLearned, lessonsDone, accuracy,
     * srsReviewed, bestStreak) đang là "0" (do GamificationStats không có data
     * tương ứng), thử lấy từ /api/gamification/analytics response.
     */
    private void renderAnalyticsFallback() {
        if (analyticsData == null) return;
        applyIfZero(tvWordsLearned, pickInt("wordsLearned", "totalWordsLearned", "words_learned"));
        applyIfZero(tvLessonsDone, pickInt("lessonsCompleted", "totalLessons", "lessons_completed"));
        Integer acc = pickInt("accuracy", "accuracyPercent", "averageAccuracy");
        if (acc != null && acc > 0) {
            String cur = String.valueOf(tvAccuracy.getText());
            if (cur == null || cur.startsWith("0%") || cur.startsWith("-")) {
                tvAccuracy.setText(acc + "%");
            }
        }
        applyIfZero(tvSrsReviewed, pickInt("srsReviewed", "srs_reviewed", "totalReviews"));
        applyIfZero(tvBestStreak, pickInt("bestStreak", "best_streak", "longestStreak"));
    }

    private Integer pickInt(String... keys) {
        if (analyticsData == null) return null;
        for (String k : keys) {
            Object v = analyticsData.get(k);
            if (v instanceof Number) return ((Number) v).intValue();
            if (v != null) {
                try { return (int) Math.round(Double.parseDouble(String.valueOf(v))); }
                catch (Exception ignore) {}
            }
        }
        return null;
    }

    private void applyIfZero(TextView tv, Integer value) {
        if (tv == null || value == null || value <= 0) return;
        String cur = String.valueOf(tv.getText());
        if ("0".equals(cur) || cur == null || cur.isEmpty()) {
            tv.setText(String.valueOf(value));
        }
    }

    /**
     * U20 FIX: helper Snackbar với action "Thử lại". Cho phép user retry các
     * request tải stats / analytics ngay tại màn hình thay vì phải thoát ra.
     */
    private void showRetrySnackbar(String message, android.view.View.OnClickListener onRetry) {
        android.view.View root = findViewById(android.R.id.content);
        if (root == null) return;
        try {
            Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Thử lại", onRetry)
                    .show();
        } catch (Throwable t) {
            android.widget.Toast.makeText(StatisticsActivity.this, message,
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    private void render(GamificationStats s) {
        tvTotalXp.setText(String.valueOf(s.totalXp));
        tvStreakStat.setText(String.valueOf(s.getStreak()));
        tvLevelStat.setText(String.valueOf(s.level));

        // Weekly XP — use server-provided values if any, otherwise fallback
        // to a deterministic distribution based on totalXp/streak so the
        // chart is never empty for first-time users.
        int[] weekly = s.getWeeklyXp();
        if (weekly == null || weekly.length == 0) {
            weekly = synthesizeWeekly(s.getTodayXp(), s.totalXp);
        }
        if (weekly.length < 7) {
            int[] padded = new int[7];
            System.arraycopy(weekly, 0, padded, 7 - weekly.length, weekly.length);
            weekly = padded;
        } else if (weekly.length > 7) {
            int[] tail = new int[7];
            System.arraycopy(weekly, weekly.length - 7, tail, 0, 7);
            weekly = tail;
        }
        barChart.setData(weekly, lastSevenDayLabels());

        // Streak calendar (30 days)
        int[] calendar = s.getStreakCalendar();
        if (calendar == null || calendar.length == 0) {
            calendar = synthesizeCalendar(s.getStreak());
        }
        streakCalendar.setActivity(calendar);

        // Detailed metrics
        tvWordsLearned.setText(String.valueOf(s.getWordsLearned()));
        tvLessonsDone.setText(String.valueOf(s.getLessonsCompleted()));
        tvAccuracy.setText(s.getAccuracyPercent() + "%");
        tvSrsReviewed.setText(String.valueOf(s.getSrsReviewed()));
        tvBestStreak.setText(String.valueOf(s.getBestStreak()));
    }

    /**
     * BUG B14 FIX: sinh phân bố XP theo tuần KHÔNG NGẪU NHIÊN (dựa trên totalXp
     * và todayXp). Trước đây dùng Math.random() → biểu đồ thay đổi mỗi lần
     * xoay màn hình.
     *
     * Nếu /gamification/analytics đã trả về `dailyXp`, ta ưu tiên dùng giá trị này.
     * (BUG #19 FIX: comment tiếng Pháp → tiếng Việt.)
     */
    @SuppressWarnings("unchecked")
    private int[] synthesizeWeekly(int todayXp, long totalXp) {
        // Ưu tiên: dữ liệu thật từ /gamification/analytics
        if (analyticsData != null) {
            try {
                Object daily = analyticsData.get("dailyXp");
                if (daily instanceof List) {
                    List<?> list = (List<?>) daily;
                    int n = list.size();
                    int[] out = new int[Math.min(7, n)];
                    int start = Math.max(0, n - 7);
                    for (int i = start; i < n; i++) {
                        Object item = list.get(i);
                        int xp = 0;
                        if (item instanceof Map) {
                            Object v = ((Map<String, Object>) item).get("xp_gained");
                            if (v == null) v = ((Map<String, Object>) item).get("xpGained");
                            if (v instanceof Number) xp = ((Number) v).intValue();
                        } else if (item instanceof Number) {
                            xp = ((Number) item).intValue();
                        }
                        out[i - start] = xp;
                    }
                    return out;
                }
            } catch (Exception ignore) {}
        }
        // BUG #19 FIX: comment tiếng Pháp → tiếng Việt.
        // Fallback xác định: pattern cố định dựa trên trung bình mỗi ngày.
        int[] out = new int[7];
        int avg = Math.max(0, (int) (totalXp / 30));
        // Pattern có thể tái tạo (kiểu tuần: cuối tuần học nhiều hơn)
        double[] pattern = {0.7, 0.9, 0.8, 1.0, 0.9, 1.2, 1.1};
        for (int i = 0; i < 6; i++) out[i] = (int) (avg * pattern[i]);
        out[6] = todayXp;
        return out;
    }

    /** Generates a 30-day calendar that respects the current streak. */
    private int[] synthesizeCalendar(int streak) {
        int[] out = new int[30];
        for (int i = 0; i < Math.min(streak, 30); i++) {
            // gradient: closer-to-today → stronger color
            out[i] = i < 3 ? 4 : (i < 7 ? 3 : 2);
        }
        return out;
    }

    private String[] lastSevenDayLabels() {
        String[] dows = {"CN", "T2", "T3", "T4", "T5", "T6", "T7"};
        String[] out = new String[7];
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -6);
        for (int i = 0; i < 7; i++) {
            out[i] = dows[c.get(Calendar.DAY_OF_WEEK) - 1];
            c.add(Calendar.DAY_OF_YEAR, 1);
        }
        return out;
    }
}
