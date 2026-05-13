package com.lingua.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lingua.app.R;
import com.lingua.app.adapters.AchievementAdapter;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.Achievement;
import com.lingua.app.models.ApiResponse;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AchievementsActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvTitle, tvEmpty;
    private AchievementAdapter adapter;
    private List<Achievement> items = new ArrayList<>();
    private LinguaApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple_list);

        apiService = ApiClient.getService(this);

        tvTitle = findViewById(R.id.tvTitle);
        tvEmpty = findViewById(R.id.tvEmpty);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);

        tvTitle.setText("🎖 Thành tựu");

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AchievementAdapter(items);
        recyclerView.setAdapter(adapter);

        loadAchievements();
    }

    private void loadAchievements() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getAchievements().enqueue(new Callback<ApiResponse<List<Achievement>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Achievement>>> call,
                                   Response<ApiResponse<List<Achievement>>> resp) {
                progressBar.setVisibility(View.GONE);
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<Achievement> data = resp.body().getData();
                    runOnUiThread(() -> {
                        items.clear();
                        if (data != null) items.addAll(data);
                        adapter.notifyDataSetChanged();
                        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    });
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Achievement>>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AchievementsActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
