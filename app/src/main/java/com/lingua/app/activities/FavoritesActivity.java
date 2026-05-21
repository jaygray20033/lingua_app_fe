package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.Favorite;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.ArrayList;
import java.util.List;

/**
 * Favorites / Bookmarks screen.
 *
 * Two tabs: WORD | GRAMMAR. Loads /api/favorites?type=WORD or =GRAMMAR.
 * Long-press an item to remove it (DELETE /api/favorites/{type}/{itemId}).
 */
public class FavoritesActivity extends AppCompatActivity {

    private LinguaApiService apiService;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private View layoutEmpty;          // BUG U5 — container empty-state
    private Button btnExploreEmpty;    // BUG U5 — CTA
    private final List<Favorite> items = new ArrayList<>();
    private FavoriteAdapter adapter;
    private String currentType = "WORD";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        apiService = ApiClient.getService(this);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);
        layoutEmpty = findViewById(R.id.layoutEmpty);
        btnExploreEmpty = findViewById(R.id.btnExploreEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FavoriteAdapter();
        recyclerView.setAdapter(adapter);

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.addTab(tabs.newTab().setText("📝 Từ vựng"));
        tabs.addTab(tabs.newTab().setText("📖 Ngữ pháp"));
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                currentType = tab.getPosition() == 0 ? "WORD" : "GRAMMAR";
                updateExploreButtonLabel();
                loadFavorites();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        // BUG U5 FIX : bouton CTA — redirige vers Vocabulary ou Grammar selon l'onglet.
        if (btnExploreEmpty != null) {
            btnExploreEmpty.setOnClickListener(v -> {
                Class<?> target = "GRAMMAR".equals(currentType)
                        ? GrammarActivity.class
                        : VocabularyActivity.class;
                startActivity(new Intent(FavoritesActivity.this, target));
            });
        }
        updateExploreButtonLabel();

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("🔖 Yêu thích");
        loadFavorites();
    }

    /**
     * R5-023 FIX: Si l'user retire un favori depuis WordDetailActivity ou
     * GrammarDetailActivity, en revenant ici la liste devait être stale.
     * Maintenant on recharge à chaque onResume() pour refléter les changements.
     */
    @Override
    protected void onResume() {
        super.onResume();
        loadFavorites();
    }

    private void updateExploreButtonLabel() {
        if (btnExploreEmpty == null) return;
        btnExploreEmpty.setText(
                "GRAMMAR".equals(currentType) ? "📖 Khám phá ngữ pháp" : "📚 Khám phá từ vựng"
        );
    }

    /** BUG U5 — affiche/masque le bloc empty-state (TextView + bouton). */
    private void setEmptyVisible(boolean visible) {
        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(visible ? View.VISIBLE : View.GONE);
        } else if (tvEmpty != null) {
            // Fallback si l'inflation a échoué pour une raison X
            tvEmpty.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void loadFavorites() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getFavorites(currentType).enqueue(new Callback<ApiResponse<List<Favorite>>>() {
            @Override public void onResponse(Call<ApiResponse<List<Favorite>>> c, Response<ApiResponse<List<Favorite>>> r) {
                progressBar.setVisibility(View.GONE);
                items.clear();
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    items.addAll(r.body().getData());
                }
                adapter.notifyDataSetChanged();
                setEmptyVisible(items.isEmpty()); // BUG U5
            }
            @Override public void onFailure(Call<ApiResponse<List<Favorite>>> c, Throwable t) {
                progressBar.setVisibility(View.GONE);
                setEmptyVisible(true); // BUG U5
            }
        });
    }

    private void removeFavorite(Favorite fav, int position) {
        apiService.removeFavorite(fav.type != null ? fav.type : currentType, fav.itemId)
                .enqueue(new Callback<ApiResponse<Object>>() {
            @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    items.remove(position);
                    adapter.notifyItemRemoved(position);
                    setEmptyVisible(items.isEmpty()); // BUG U5
                }
            }
            @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {
                Toast.makeText(FavoritesActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class FavoriteAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @Override public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (14 * getResources().getDisplayMetrics().density);
            root.setPadding(pad, pad, pad, pad);
            // 7.5 FIX: dung mau dark-mode aware
            root.setBackgroundColor(androidx.core.content.ContextCompat.getColor(
                    parent.getContext(), R.color.surface_card));
            ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int m = (int) (4 * getResources().getDisplayMetrics().density);
            lp.setMargins(m, m, m, m);
            root.setLayoutParams(lp);

            TextView t = new TextView(parent.getContext());
            t.setId(android.R.id.text1);
            t.setTextSize(18);
            // 7.5 FIX: dark-mode aware text colors
            t.setTextColor(androidx.core.content.ContextCompat.getColor(
                    parent.getContext(), R.color.text_primary));
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            root.addView(t);

            TextView s = new TextView(parent.getContext());
            s.setId(android.R.id.text2);
            s.setTextSize(13);
            s.setTextColor(androidx.core.content.ContextCompat.getColor(
                    parent.getContext(), R.color.text_hint));
            root.addView(s);

            return new RecyclerView.ViewHolder(root) {};
        }

        @Override public void onBindViewHolder(RecyclerView.ViewHolder h, int pos) {
            Favorite f = items.get(pos);
            ((TextView) h.itemView.findViewById(android.R.id.text1)).setText(f.getDisplayTitle());
            ((TextView) h.itemView.findViewById(android.R.id.text2)).setText(f.getDisplaySubtitle());
            h.itemView.setOnLongClickListener(v -> {
                Toast.makeText(FavoritesActivity.this, "Đã bỏ yêu thích", Toast.LENGTH_SHORT).show();
                removeFavorite(f, h.getAdapterPosition());
                return true;
            });
        }

        @Override public int getItemCount() { return items.size(); }
    }
}
