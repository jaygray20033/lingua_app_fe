package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.lingua.app.R;
import com.lingua.app.adapters.EnrollmentAdapter;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * "My courses" / browse-courses screen.
 *
 *  - mode = "enrolled" (default): GET /api/enrollments — courses the user is enrolled in.
 *  - mode = "browse": GET /api/courses — full catalog. Tap to open CourseDetail.
 */
public class MyCoursesActivity extends AppCompatActivity {
    private LinguaApiService apiService;
    private RecyclerView recyclerView;
    private TextView tvTitle, tvEmpty;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);

        apiService = ApiClient.getService(this);
        tvTitle = findViewById(R.id.tvTitle);
        tvEmpty = findViewById(R.id.tvEmpty);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        String mode = getIntent().getStringExtra("mode");
        if ("browse".equals(mode)) {
            tvTitle.setText("📚 Khám phá khoá học");
            loadCatalog();
        } else {
            tvTitle.setText("📚 Khoá học của tôi");
            loadEnrollments();
        }
    }

    private void loadEnrollments() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getMyEnrollments().enqueue(new Callback<ApiResponse<List<Enrollment>>>() {
            @Override public void onResponse(Call<ApiResponse<List<Enrollment>>> c, Response<ApiResponse<List<Enrollment>>> r) {
                progressBar.setVisibility(View.GONE);
                List<Enrollment> data = r.isSuccessful() && r.body() != null ? r.body().getData() : null;
                List<Enrollment> list = data != null ? data : new ArrayList<>();
                EnrollmentAdapter ad = new EnrollmentAdapter(list, e -> {
                    Intent i = new Intent(MyCoursesActivity.this, CourseDetailActivity.class);
                    i.putExtra("courseId", e.courseId);
                    startActivity(i);
                });
                recyclerView.setAdapter(ad);
                tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void onFailure(Call<ApiResponse<List<Enrollment>>> c, Throwable t) {
                progressBar.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void loadCatalog() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getCourses().enqueue(new Callback<ApiResponse<List<Course>>>() {
            @Override public void onResponse(Call<ApiResponse<List<Course>>> c, Response<ApiResponse<List<Course>>> r) {
                progressBar.setVisibility(View.GONE);
                List<Course> data = r.isSuccessful() && r.body() != null ? r.body().getData() : null;
                List<Course> list = data != null ? data : new ArrayList<>();
                recyclerView.setAdapter(new CourseAdapter(list));
                tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void onFailure(Call<ApiResponse<List<Course>>> c, Throwable t) {
                progressBar.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    /** Inline simple adapter for course catalog. */
    private class CourseAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Course> items;
        CourseAdapter(List<Course> items) { this.items = items; }

        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (14 * getResources().getDisplayMetrics().density);
            root.setPadding(pad, pad, pad, pad);
            root.setBackgroundColor(0xFFFFFFFF);
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = (int) (4 * getResources().getDisplayMetrics().density);
            lp.setMargins(m, m, m, m);
            root.setLayoutParams(lp);

            TextView t = new TextView(parent.getContext());
            t.setId(android.R.id.text1);
            t.setTextSize(17);
            t.setTextColor(0xFF3C3C3C);
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            root.addView(t);

            TextView s = new TextView(parent.getContext());
            s.setId(android.R.id.text2);
            s.setTextSize(13);
            s.setTextColor(0xFFAFAFAF);
            root.addView(s);

            return new RecyclerView.ViewHolder(root) {};
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            Course c = items.get(pos);
            ((TextView) h.itemView.findViewById(android.R.id.text1)).setText(
                    (c.flagEmoji != null ? c.flagEmoji + "  " : "") + c.title);
            StringBuilder sub = new StringBuilder();
            if (c.levelCode != null) sub.append(c.levelCode);
            if (c.totalLessons > 0) {
                if (sub.length() > 0) sub.append("  ·  ");
                sub.append(c.totalLessons).append(" bài");
            }
            if (c.isEnrolled == 1) {
                if (sub.length() > 0) sub.append("  ·  ");
                sub.append("✅ Đã ghi danh");
            }
            ((TextView) h.itemView.findViewById(android.R.id.text2)).setText(sub.toString());

            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(MyCoursesActivity.this, CourseDetailActivity.class);
                i.putExtra("courseId", c.id);
                startActivity(i);
            });
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
