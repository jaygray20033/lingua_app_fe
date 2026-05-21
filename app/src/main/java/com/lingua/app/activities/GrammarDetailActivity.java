package com.lingua.app.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.Favorite;
import com.lingua.app.models.GrammarDetail;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Grammar detail screen.
 *
 * GET /api/grammars/{id}.
 * Shows title / structure / level / meaning / explanation / example sentences,
 * with a favorite (bookmark) toggle (POST/DELETE /api/favorites with type=GRAMMAR).
 */
public class GrammarDetailActivity extends AppCompatActivity {
    private long grammarId;
    private boolean isFavorite = false;
    private LinguaApiService apiService;
    private MediaPlayer mediaPlayer;

    private TextView tvTitle, tvStructure, tvLevel, tvMeaning, tvExplanation;
    private LinearLayout layoutExamples;
    private ImageButton btnFavorite;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grammar_detail);

        apiService = ApiClient.getService(this);
        grammarId = getIntent().getLongExtra("grammarId", 0);

        tvTitle = findViewById(R.id.tvTitle);
        tvStructure = findViewById(R.id.tvStructure);
        tvLevel = findViewById(R.id.tvLevel);
        tvMeaning = findViewById(R.id.tvMeaning);
        tvExplanation = findViewById(R.id.tvExplanation);
        layoutExamples = findViewById(R.id.layoutExamples);
        btnFavorite = findViewById(R.id.btnFavorite);
        progressBar = findViewById(R.id.progressBar);

        btnFavorite.setOnClickListener(v -> toggleFavorite());

        // 3.7 FIX: open PracticeActivity in grammar-drill mode. We pass both
        // grammarId and a "mode" flag so PracticeActivity can fetch
        // grammar-specific exercises (or fall back to the lesson flow if the
        // backend doesn't have grammar-specific drills yet).
        Button btnPracticeGrammar = findViewById(R.id.btnPracticeGrammar);
        if (btnPracticeGrammar != null) {
            btnPracticeGrammar.setOnClickListener(v -> {
                Intent i = new Intent(this, PracticeActivity.class);
                i.putExtra("mode", "grammar");
                i.putExtra("grammarId", grammarId);
                // lessonId is used as fallback by PracticeActivity if the
                // backend does not yet expose grammar-only exercises.
                i.putExtra("lessonId", grammarId);
                startActivity(i);
            });
        }

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("📖 Ngữ pháp");
        loadDetail();
        checkFavoriteState();
    }

    private void loadDetail() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getGrammarDetail(grammarId).enqueue(new Callback<ApiResponse<GrammarDetail>>() {
            @Override public void onResponse(Call<ApiResponse<GrammarDetail>> c, Response<ApiResponse<GrammarDetail>> r) {
                progressBar.setVisibility(View.GONE);
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    render(r.body().getData());
                }
            }
            @Override public void onFailure(Call<ApiResponse<GrammarDetail>> c, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(GrammarDetailActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void render(GrammarDetail d) {
        if (d == null) return;
        tvTitle.setText(d.title != null ? d.title : "");
        tvStructure.setText(d.structure != null ? d.structure : "");
        String level = d.level != null ? d.level : (d.levelCode != null ? d.levelCode : "");
        tvLevel.setText(level);
        tvLevel.setVisibility(level.isEmpty() ? View.GONE : View.VISIBLE);
        tvMeaning.setText(d.meaning != null ? d.meaning : "—");
        tvExplanation.setText(d.explanation != null ? d.explanation : "—");
        if (d.favorite) {
            isFavorite = true;
            btnFavorite.setImageResource(android.R.drawable.btn_star_big_on);
        }

        layoutExamples.removeAllViews();
        if (d.examples != null) {
            for (GrammarDetail.GrammarExample ex : d.examples) addExampleCard(ex);
        }
    }

    private void addExampleCard(GrammarDetail.GrammarExample ex) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (12 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, p);
        // 7.5 FIX: dung mau dark-mode aware tu resources thay vi hardcode 0xFFFFFFFF.
        box.setBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.surface_card));
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        box.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(ex.sentence != null ? ex.sentence : "");
        t.setTextSize(15);
        // 7.5 FIX: dark-mode aware text colors
        t.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_primary));
        box.addView(t);

        if (ex.translation != null && !ex.translation.isEmpty()) {
            TextView tr = new TextView(this);
            tr.setText(ex.translation);
            tr.setTextSize(13);
            tr.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_secondary));
            box.addView(tr);
        }
        if (ex.note != null && !ex.note.isEmpty()) {
            TextView n = new TextView(this);
            n.setText("💡 " + ex.note);
            n.setTextSize(12);
            n.setTextColor(0xFFFF9600);
            box.addView(n);
        }
        if (ex.audioUrl != null && !ex.audioUrl.isEmpty()) {
            Button play = new Button(this);
            play.setText("🔊 Nghe");
            play.setBackgroundTintList(getResources().getColorStateList(R.color.lingua_blue));
            play.setTextColor(0xFFFFFFFF);
            final String url = ex.audioUrl;
            play.setOnClickListener(v -> playUrl(url));
            box.addView(play);
        }
        layoutExamples.addView(box);
    }

    private void playUrl(String url) {
        // FIX 2.5: Guard null/empty/invalid URL to prevent MediaPlayer crash.
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "Không có audio cho mục này", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Toast.makeText(this, "URL audio không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (mediaPlayer != null) {
                try { mediaPlayer.release(); } catch (Exception ignore) {}
            }
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "Lỗi khi phát audio", Toast.LENGTH_SHORT).show();
                return true;
            });
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (Exception e) { Toast.makeText(this, "Lỗi audio", Toast.LENGTH_SHORT).show(); }
    }

    /**
     * BUG B11 FIX + TODO 1.2:
     * Utilise GET /api/favorites/check?type=GRAMMAR&itemId=... au lieu de
     * charger toute la liste des favoris.
     */
    private void checkFavoriteState() {
        apiService.checkFavorite("GRAMMAR", grammarId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override public void onResponse(Call<ApiResponse<Map<String, Object>>> c, Response<ApiResponse<Map<String, Object>>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    Object favVal = r.body().getData().get("favorite");
                    if (favVal == null) favVal = r.body().getData().get("isFavorite");
                    if (favVal == null) favVal = r.body().getData().get("favorited");
                    boolean fav = favVal != null && (
                            (favVal instanceof Boolean && (Boolean) favVal)
                            || "true".equalsIgnoreCase(String.valueOf(favVal))
                            || "1".equals(String.valueOf(favVal)));
                    if (fav) {
                        isFavorite = true;
                        runOnUiThread(() -> btnFavorite.setImageResource(android.R.drawable.btn_star_big_on));
                    }
                } else {
                    // 7.2 FIX: endpoint loi (404/500) hoac data null -> fallback list.
                    fallbackCheckFavoriteList();
                }
            }
            @Override public void onFailure(Call<ApiResponse<Map<String, Object>>> c, Throwable t) {
                // Fallback (network error)
                fallbackCheckFavoriteList();
            }
        });
    }

    /**
     * 7.2 FIX: fallback tach ra thanh ham rieng de goi ca khi:
     *   - response.isSuccessful() == false (endpoint tra 404/500)
     *   - hoac onFailure (network error)
     * Truoc day fallback chi chay trong onFailure -> neu backend chua implement
     * endpoint /favorites/check thi icon tim grammar luon hien thi trong du
     * da bookmark.
     */
    private void fallbackCheckFavoriteList() {
        apiService.getFavorites("GRAMMAR").enqueue(new Callback<ApiResponse<List<Favorite>>>() {
            @Override public void onResponse(Call<ApiResponse<List<Favorite>>> c2, Response<ApiResponse<List<Favorite>>> r2) {
                if (r2.isSuccessful() && r2.body() != null && r2.body().getData() != null) {
                    for (Favorite f : r2.body().getData()) {
                        if (f.itemId == grammarId) {
                            isFavorite = true;
                            runOnUiThread(() -> btnFavorite.setImageResource(android.R.drawable.btn_star_big_on));
                            break;
                        }
                    }
                }
            }
            @Override public void onFailure(Call<ApiResponse<List<Favorite>>> c2, Throwable t2) {}
        });
    }

    private void toggleFavorite() {
        if (isFavorite) {
            apiService.removeFavorite("GRAMMAR", grammarId).enqueue(new Callback<ApiResponse<Object>>() {
                @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                    isFavorite = false;
                    btnFavorite.setImageResource(android.R.drawable.btn_star_big_off);
                    Toast.makeText(GrammarDetailActivity.this, "Đã bỏ khỏi yêu thích", Toast.LENGTH_SHORT).show();
                }
                @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {}
            });
        } else {
            Map<String, Object> body = new HashMap<>();
            body.put("type", "GRAMMAR");
            body.put("itemId", grammarId);
            body.put("item_id", grammarId);
            apiService.addFavorite(body).enqueue(new Callback<ApiResponse<Object>>() {
                @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                    isFavorite = true;
                    btnFavorite.setImageResource(android.R.drawable.btn_star_big_on);
                    Toast.makeText(GrammarDetailActivity.this, "🔖 Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show();
                }
                @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {}
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (mediaPlayer != null) { try { mediaPlayer.release(); } catch (Exception ignore) {} }
        super.onDestroy();
    }
}
