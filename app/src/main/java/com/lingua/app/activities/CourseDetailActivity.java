package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.lingua.app.R;
import com.lingua.app.adapters.CoursePathAdapter;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Course detail screen.
 *
 * Renders the full learning path (Sections → Units → Lessons) by calling
 * GET /api/courses/{id}/path and shows enrollment status with an Enroll/Unenroll
 * button (POST and DELETE /api/courses/{id}/enroll). Tapping a lesson opens
 * PracticeActivity.
 */
public class CourseDetailActivity extends AppCompatActivity {
    private TextView tvTitle, tvSubtitle, tvProgressText, tvEmpty;
    private ProgressBar progressOverall, progressBar;
    private Button btnEnroll;
    private RecyclerView recyclerPath;

    private CoursePathAdapter adapter;
    private final List<CoursePath.PathSection> sections = new ArrayList<>();
    private LinguaApiService apiService;
    private long courseId;
    private boolean isEnrolled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_course_detail);
        apiService = ApiClient.getService(this);
        courseId = getIntent().getLongExtra("courseId", 0);

        tvTitle = findViewById(R.id.tvTitle);
        tvSubtitle = findViewById(R.id.tvSubtitle);
        tvProgressText = findViewById(R.id.tvProgressText);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressOverall = findViewById(R.id.progressOverall);
        progressBar = findViewById(R.id.progressBar);
        btnEnroll = findViewById(R.id.btnEnroll);
        recyclerPath = findViewById(R.id.recyclerPath);

        recyclerPath.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CoursePathAdapter(sections, this::openLesson);
        recyclerPath.setAdapter(adapter);

        btnEnroll.setOnClickListener(v -> {
            if (isEnrolled) confirmUnenroll();
            else enroll();
        });

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Chi tiết khoá học");
        loadCourse();
        loadPath();
        loadEnrollmentDetail();
    }

    /**
     * 1.5 FIX: gọi GET /api/courses/{id}/enrollment để lấy chi tiết enrollment
     * (completed lessons, tổng số bài, ngày bắt đầu…) và hiển thị bổ sung
     * vào tvProgressText. Nếu user chưa enroll, endpoint có thể trả về
     * null/404 — ta bỏ qua im lặng.
     */
    private void loadEnrollmentDetail() {
        apiService.getEnrollmentDetail(courseId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override public void onResponse(Call<ApiResponse<Map<String, Object>>> c,
                                             Response<ApiResponse<Map<String, Object>>> r) {
                if (!r.isSuccessful() || r.body() == null || r.body().getData() == null) return;
                Map<String, Object> d = r.body().getData();
                Object completed = d.getOrDefault("completedLessons", d.get("completed_lessons"));
                Object total = d.getOrDefault("totalLessons", d.get("total_lessons"));
                Object progress = d.getOrDefault("progress", d.get("progress_percent"));
                Object enrolledAt = d.getOrDefault("enrolledAt", d.get("enrolled_at"));

                StringBuilder sb = new StringBuilder();
                if (progress != null) {
                    sb.append(progress).append("% hoàn thành");
                    try {
                        int p = (int) Math.round(Double.parseDouble(String.valueOf(progress)));
                        progressOverall.setProgress(p);
                    } catch (Exception ignore) {}
                }
                if (completed != null && total != null) {
                    if (sb.length() > 0) sb.append("  ·  ");
                    sb.append(completed).append("/").append(total).append(" bài");
                }
                if (enrolledAt != null) {
                    String d2 = String.valueOf(enrolledAt);
                    if (d2.length() > 10) d2 = d2.substring(0, 10);
                    if (sb.length() > 0) sb.append("\n");
                    sb.append("📅 Ghi danh: ").append(d2);
                }
                if (sb.length() > 0) {
                    runOnUiThread(() -> tvProgressText.setText(sb.toString()));
                }
                isEnrolled = true;
                runOnUiThread(() -> updateEnrollButton());
            }
            @Override public void onFailure(Call<ApiResponse<Map<String, Object>>> c, Throwable t) {
                // Im lặng — có thể user chưa enroll, fallback về loadPath()
            }
        });
    }

    private void loadCourse() {
        apiService.getCourseDetail(courseId).enqueue(new Callback<ApiResponse<Course>>() {
            @Override
            public void onResponse(Call<ApiResponse<Course>> call, Response<ApiResponse<Course>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Course course = response.body().getData();
                    runOnUiThread(() -> {
                        if (getSupportActionBar() != null) getSupportActionBar().setTitle(course.title);
                        tvTitle.setText((course.flagEmoji != null ? course.flagEmoji + " " : "") +
                                (course.title != null ? course.title : ""));
                        StringBuilder sub = new StringBuilder();
                        if (course.levelCode != null) sub.append(course.levelCode);
                        if (course.totalLessons > 0) {
                            if (sub.length() > 0) sub.append("  ·  ");
                            sub.append(course.totalLessons).append(" bài học");
                        }
                        if (course.description != null && !course.description.isEmpty()) {
                            if (sub.length() > 0) sub.append("\n");
                            sub.append(course.description);
                        }
                        tvSubtitle.setText(sub.toString());
                        isEnrolled = course.isEnrolled == 1;
                        updateEnrollButton();
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<Course>> call, Throwable t) {}
        });
    }

    private void loadPath() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getCoursePath(courseId).enqueue(new Callback<ApiResponse<CoursePath>>() {
            @Override
            public void onResponse(Call<ApiResponse<CoursePath>> call, Response<ApiResponse<CoursePath>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    CoursePath data = response.body().getData();
                    sections.clear();
                    if (data != null && data.sections != null) sections.addAll(data.sections);
                    if (data != null) {
                        progressOverall.setProgress(data.progress);
                        tvProgressText.setText(data.progress + "% hoàn thành");
                        if (data.isEnrolled == 1) {
                            isEnrolled = true;
                            updateEnrollButton();
                        }
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(sections.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
            @Override public void onFailure(Call<ApiResponse<CoursePath>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateEnrollButton() {
        if (isEnrolled) {
            btnEnroll.setText("🚪 Bỏ ghi danh");
            btnEnroll.setBackgroundTintList(getResources().getColorStateList(R.color.lingua_red));
        } else {
            btnEnroll.setText("📥 Ghi danh");
            btnEnroll.setBackgroundTintList(getResources().getColorStateList(R.color.lingua_green));
        }
    }

    private void enroll() {
        apiService.enrollCourse(courseId).enqueue(new Callback<ApiResponse<Object>>() {
            @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    isEnrolled = true;
                    updateEnrollButton();
                    Toast.makeText(CourseDetailActivity.this, "✅ Đã ghi danh", Toast.LENGTH_SHORT).show();
                    loadPath();
                } else {
                    Toast.makeText(CourseDetailActivity.this, "Không ghi danh được", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {
                Toast.makeText(CourseDetailActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmUnenroll() {
        new AlertDialog.Builder(this)
                .setTitle("Bỏ ghi danh")
                .setMessage("Bạn có chắc muốn bỏ ghi danh khoá học này?")
                .setPositiveButton("Bỏ ghi danh", (d, w) -> unenroll())
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void unenroll() {
        apiService.unenrollCourse(courseId).enqueue(new Callback<ApiResponse<Object>>() {
            @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    isEnrolled = false;
                    updateEnrollButton();
                    Toast.makeText(CourseDetailActivity.this, "Đã bỏ ghi danh", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(CourseDetailActivity.this, "Không bỏ ghi danh được", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {
                Toast.makeText(CourseDetailActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openLesson(CoursePath.PathLesson lesson) {
        if (lesson.isLocked()) {
            Toast.makeText(this, "🔒 Bài học đang khoá", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, PracticeActivity.class);
        i.putExtra("lessonId", lesson.id);
        startActivity(i);
    }
}
