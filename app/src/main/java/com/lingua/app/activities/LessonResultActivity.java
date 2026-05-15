package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.GamificationStats;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lesson result screen displayed after completing a lesson.
 * Shows: XP earned, accuracy, current streak, and a celebratory animation.
 */
public class LessonResultActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lesson_result);

        int xp = getIntent().getIntExtra("xpEarned", 0);
        int sp = getIntent().getIntExtra("scorePercent", 0);
        int correct = getIntent().getIntExtra("correct", 0);
        int total = getIntent().getIntExtra("total", 0);

        TextView tvXp = findViewById(R.id.tvXp);
        TextView tvAccuracy = findViewById(R.id.tvAccuracy);
        TextView tvCorrect = findViewById(R.id.tvCorrect);
        TextView tvStreak = findViewById(R.id.tvStreak);
        Button btnDone = findViewById(R.id.btnDone);
        View celebrate = findViewById(R.id.celebrate);

        tvXp.setText("+" + xp + " XP");
        tvAccuracy.setText(sp + "%");
        tvCorrect.setText(correct + " / " + total);

        try {
            celebrate.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce_celebrate));
        } catch (Exception ignored) {}

        // Load streak update
        ApiClient.getService(this).getMyStats().enqueue(new Callback<ApiResponse<GamificationStats>>() {
            @Override public void onResponse(Call<ApiResponse<GamificationStats>> c, Response<ApiResponse<GamificationStats>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    int streak = r.body().getData().getStreak();
                    runOnUiThread(() -> tvStreak.setText("🔥 " + streak + " ngày"));
                }
            }
            @Override public void onFailure(Call<ApiResponse<GamificationStats>> c, Throwable t) {}
        });

        // 6.4 FIX: thay vì chỉ finish() (sẽ rơi về PracticeActivity đã hết bài,
        // gây UX khó hiểu), điều hướng rõ ràng về MainActivity và clear stack
        // các activity PracticeActivity / CourseDetailActivity ở giữa.
        // BUG #L2 FIX: gắn extra "forceRefresh"=true để MainActivity bypass
        // cooldown 30s và refresh ngay XP/streak/quest progress.
        btnDone.setOnClickListener(v -> goHome());
    }

    private void goHome() {
        Intent i = new Intent(LessonResultActivity.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("forceRefresh", true);
        startActivity(i);
        finish();
    }

    @Override
    public void onBackPressed() {
        // BUG #L2 FIX: nút back cũng phải forceRefresh — nếu không, user nhấn back
        // sẽ rơi về MainActivity và cooldown 30s khiến XP/streak không cập nhật.
        goHome();
    }
}
