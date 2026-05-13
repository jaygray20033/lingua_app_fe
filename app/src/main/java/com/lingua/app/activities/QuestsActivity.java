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
import com.lingua.app.adapters.QuestAdapter;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.DailyQuest;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class QuestsActivity extends AppCompatActivity implements QuestAdapter.OnClaimListener {
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvTitle, tvEmpty;
    private QuestAdapter adapter;
    private List<DailyQuest> items = new ArrayList<>();
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

        tvTitle.setText("🎯 Nhiệm vụ hàng ngày");

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new QuestAdapter(items, this);
        recyclerView.setAdapter(adapter);

        loadQuests();
    }

    private void loadQuests() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getDailyQuests().enqueue(new Callback<ApiResponse<List<DailyQuest>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<DailyQuest>>> call,
                                   Response<ApiResponse<List<DailyQuest>>> resp) {
                progressBar.setVisibility(View.GONE);
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<DailyQuest> data = resp.body().getData();
                    runOnUiThread(() -> {
                        items.clear();
                        if (data != null) items.addAll(data);
                        adapter.notifyDataSetChanged();
                        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    });
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<DailyQuest>>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(QuestsActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onClaim(DailyQuest quest, int position) {
        apiService.claimQuest(quest.id).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(Call<ApiResponse<Object>> call, Response<ApiResponse<Object>> resp) {
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    runOnUiThread(() -> {
                        quest.claimedAt = "claimed";
                        adapter.notifyItemChanged(position);
                        Toast.makeText(QuestsActivity.this, "🎉 Đã nhận thưởng " + quest.rewardGems + " 💎!", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Object>> call, Throwable t) {
                Toast.makeText(QuestsActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
