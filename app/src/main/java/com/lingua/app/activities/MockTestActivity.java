package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.MockTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Mock test list (GET /api/mock-tests).
 *
 * Tabs filter by language. Tap a row to open MockTestDetailActivity which
 * starts the test session.
 */
public class MockTestActivity extends AppCompatActivity {
    private LinguaApiService apiService;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private ProgressBar progressBar;
    private TabLayout tabLanguage;
    private final List<MockTest> items = new ArrayList<>();
    private MockAdapter adapter;
    private String currentLang = "ja";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mock_test);
        apiService = ApiClient.getService(this);

        tabLanguage = findViewById(R.id.tabLanguage);
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MockAdapter();
        recyclerView.setAdapter(adapter);

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("📝 Bài thi thử");

        String[] langs = {"🇯🇵 JLPT", "🇬🇧 TOEIC/IELTS", "🇨🇳 HSK"};
        final String[] codes = {"ja", "en", "zh"};
        for (String l : langs) tabLanguage.addTab(tabLanguage.newTab().setText(l));
        tabLanguage.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentLang = codes[tab.getPosition()];
                load();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // 1.4 FIX: nút "Lịch sử thi" — mở dialog danh sách attempts của user.
        Button btnHistory = findViewById(R.id.btnHistory);
        if (btnHistory != null) btnHistory.setOnClickListener(v -> showAttemptsDialog());

        load();
    }

    /**
     * 1.4 FIX: hiển thị lịch sử các lần thi của user.
     * Endpoint: GET /api/mock-tests/attempts → trả về list các attempt.
     */
    private void showAttemptsDialog() {
        ProgressBar pb = new ProgressBar(this);
        AlertDialog loading = new AlertDialog.Builder(this)
                .setTitle("📜 Lịch sử thi")
                .setView(pb)
                .setCancelable(true)
                .show();

        apiService.getMockTestAttempts().enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> c,
                                             Response<ApiResponse<List<Map<String, Object>>>> r) {
                loading.dismiss();
                List<Map<String, Object>> attempts =
                        r.isSuccessful() && r.body() != null ? r.body().getData() : null;
                if (attempts == null || attempts.isEmpty()) {
                    new AlertDialog.Builder(MockTestActivity.this)
                            .setTitle("📜 Lịch sử thi")
                            .setMessage("Bạn chưa có lần thi nào.")
                            .setPositiveButton("OK", null)
                            .show();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> a : attempts) {
                    String title = String.valueOf(a.getOrDefault("mockTestTitle",
                            a.getOrDefault("title", "Mock Test")));
                    Object score = a.getOrDefault("score", a.get("totalScore"));
                    Object passed = a.get("passed");
                    Object correct = a.getOrDefault("correctAnswers", a.get("correct"));
                    Object total = a.getOrDefault("totalQuestions", a.get("total"));
                    Object date = a.getOrDefault("createdAt", a.get("created_at"));
                    sb.append("• ").append(title).append("\n");
                    if (score != null) sb.append("  Điểm: ").append(score);
                    if (passed != null) {
                        boolean p = passed instanceof Boolean ? (Boolean) passed
                                : "true".equalsIgnoreCase(String.valueOf(passed));
                        sb.append(p ? "  ✅ Đạt" : "  ❌ Chưa đạt");
                    }
                    sb.append("\n");
                    if (correct != null && total != null) {
                        sb.append("  Đúng: ").append(correct).append("/").append(total).append("\n");
                    }
                    if (date != null) {
                        String d = String.valueOf(date);
                        if (d.length() > 10) d = d.substring(0, 10);
                        sb.append("  Ngày: ").append(d).append("\n");
                    }
                    sb.append("\n");
                }
                new AlertDialog.Builder(MockTestActivity.this)
                        .setTitle("📜 Lịch sử thi (" + attempts.size() + ")")
                        .setMessage(sb.toString().trim())
                        .setPositiveButton("Đóng", null)
                        .show();
            }
            @Override public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> c, Throwable t) {
                loading.dismiss();
                Toast.makeText(MockTestActivity.this,
                        "Không tải được lịch sử: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void load() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getMockTestsV2(currentLang, null).enqueue(new Callback<ApiResponse<List<MockTest>>>() {
            @Override public void onResponse(Call<ApiResponse<List<MockTest>>> c, Response<ApiResponse<List<MockTest>>> r) {
                progressBar.setVisibility(View.GONE);
                items.clear();
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    items.addAll(r.body().getData());
                }
                adapter.notifyDataSetChanged();
                tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void onFailure(Call<ApiResponse<List<MockTest>>> c, Throwable t) {
                // Try legacy endpoint as fallback
                apiService.getMockTests(currentLang, null).enqueue(new Callback<ApiResponse<List<MockTest>>>() {
                    @Override public void onResponse(Call<ApiResponse<List<MockTest>>> c1, Response<ApiResponse<List<MockTest>>> r1) {
                        progressBar.setVisibility(View.GONE);
                        items.clear();
                        if (r1.isSuccessful() && r1.body() != null && r1.body().getData() != null) {
                            items.addAll(r1.body().getData());
                        }
                        adapter.notifyDataSetChanged();
                        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                    @Override public void onFailure(Call<ApiResponse<List<MockTest>>> c1, Throwable t1) {
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private class MockAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mock_test, parent, false);
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MockTest m = items.get(position);
            View v = holder.itemView;
            ((TextView) v.findViewById(R.id.tvFlag)).setText(m.flagEmoji != null ? m.flagEmoji : "📝");
            ((TextView) v.findViewById(R.id.tvTitle)).setText(m.title != null ? m.title : "Mock Test");
            ((TextView) v.findViewById(R.id.tvDescription)).setText(m.description != null ? m.description : "");
            String meta = m.totalQuestions + " câu  ·  Pass: " + m.passScore + "%";
            ((TextView) v.findViewById(R.id.tvMeta)).setText(meta);
            v.setOnClickListener(view -> {
                Intent i = new Intent(MockTestActivity.this, MockTestDetailActivity.class);
                i.putExtra("mockTestId", m.id);
                startActivity(i);
            });
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
