package com.lingua.app.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.lingua.app.R;
import com.lingua.app.adapters.LeaderboardAdapter;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.LeaderboardEntry;
import com.lingua.app.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Leaderboard screen with multi-period support (weekly / monthly / all-time).
 *
 * Improvements over the original:
 *   - Three TabLayout tabs let the user switch period.
 *   - Highlights the current user's row.
 *   - Shows a "Your rank" summary card above the list when the user
 *     is on the leaderboard.
 */
public class LeaderboardActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvMyRank;
    private TabLayout tabPeriod;
    private LeaderboardAdapter adapter;
    private final List<LeaderboardEntry> items = new ArrayList<>();
    private LinguaApiService apiService;
    private SessionManager session;
    private String currentPeriod = "weekly";
    // R5-025 FIX: track the in-flight calls so we can cancel them in onPause()/onDestroy().
    // Avant: si l'user quittait l'activity pendant le loading, le callback Retrofit
    // pouvait toujours fire et toucher des Views détruites → NPE / window leaked.
    private Call<ApiResponse<List<LeaderboardEntry>>> currentCall;
    private Call<ApiResponse<List<LeaderboardEntry>>> fallbackCall;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leaderboard);

        apiService = ApiClient.getService(this);
        session = SessionManager.getInstance(this);
        recyclerView = findViewById(R.id.recyclerLeaderboard);
        progressBar = findViewById(R.id.progressBar);
        tvMyRank = findViewById(R.id.tvMyRank);
        tabPeriod = findViewById(R.id.tabPeriod);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LeaderboardAdapter(items);
        adapter.setCurrentUserId(session.getUserId());
        recyclerView.setAdapter(adapter);

        final String[] periods = {"weekly", "monthly", "all"};
        tabPeriod.addTab(tabPeriod.newTab().setText("📅 Tuần này"));
        tabPeriod.addTab(tabPeriod.newTab().setText("📆 Tháng này"));
        tabPeriod.addTab(tabPeriod.newTab().setText("🌐 Mọi lúc"));
        tabPeriod.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentPeriod = periods[tab.getPosition()];
                loadLeaderboard();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadLeaderboard();
    }

    private void loadLeaderboard() {
        progressBar.setVisibility(View.VISIBLE);
        tvMyRank.setVisibility(View.GONE);

        Callback<ApiResponse<List<LeaderboardEntry>>> cb = new Callback<ApiResponse<List<LeaderboardEntry>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<LeaderboardEntry>>> call,
                                   Response<ApiResponse<List<LeaderboardEntry>>> resp) {
                progressBar.setVisibility(View.GONE);
                if (resp.isSuccessful() && resp.body() != null && resp.body().isSuccess()) {
                    List<LeaderboardEntry> data = resp.body().getData();
                    runOnUiThread(() -> {
                        items.clear();
                        if (data != null) items.addAll(data);
                        adapter.notifyDataSetChanged();
                        renderMyRank();
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<List<LeaderboardEntry>>> call, Throwable t) {
                if (call.isCanceled()) return; // R5-025: skip if user already left.
                progressBar.setVisibility(View.GONE);
                // Fallback to base endpoint when ?period= is not implemented
                if (!"all".equals(currentPeriod)) {
                    fallbackCall = apiService.getLeaderboard();
                    fallbackCall.enqueue(simpleFallback());
                } else {
                    Toast.makeText(LeaderboardActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        };

        // R5-025: cancel any previous in-flight call before starting a new one (rapid tab-switch)
        if (currentCall != null) currentCall.cancel();
        if (fallbackCall != null) fallbackCall.cancel();
        currentCall = apiService.getLeaderboardByPeriod(currentPeriod);
        currentCall.enqueue(cb);
    }

    /**
     * R5-025 FIX: cancel in-flight Retrofit calls when the activity is paused
     * to prevent NPE / "window already detached" crashes from late callbacks.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (currentCall != null) { currentCall.cancel(); currentCall = null; }
        if (fallbackCall != null) { fallbackCall.cancel(); fallbackCall = null; }
    }

    private Callback<ApiResponse<List<LeaderboardEntry>>> simpleFallback() {
        return new Callback<ApiResponse<List<LeaderboardEntry>>>() {
            @Override public void onResponse(Call<ApiResponse<List<LeaderboardEntry>>> c, Response<ApiResponse<List<LeaderboardEntry>>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    List<LeaderboardEntry> data = r.body().getData();
                    items.clear();
                    if (data != null) items.addAll(data);
                    adapter.notifyDataSetChanged();
                    renderMyRank();
                }
            }
            @Override public void onFailure(Call<ApiResponse<List<LeaderboardEntry>>> c, Throwable t) {}
        };
    }

    private void renderMyRank() {
        long uid = session.getUserId();
        if (uid <= 0) return;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id == uid) {
                LeaderboardEntry e = items.get(i);
                tvMyRank.setVisibility(View.VISIBLE);
                tvMyRank.setText("👉 Bạn đang ở hạng #" + (e.rank > 0 ? e.rank : (i + 1)) + " — " + e.xp + " XP");
                recyclerView.scrollToPosition(Math.max(0, i - 1));
                return;
            }
        }
    }
}
