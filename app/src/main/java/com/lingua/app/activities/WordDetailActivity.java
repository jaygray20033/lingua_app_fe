package com.lingua.app.activities;

import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.ExampleSentence;
import com.lingua.app.models.Favorite;
import com.lingua.app.models.Word;
import com.lingua.app.views.StrokeOrderView;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Word detail screen.
 *
 * Shows full word data: text/reading/level/meanings, example sentences (with
 * per-example audio), and a bookmark/favorite toggle. Loads:
 *   - GET /api/words/{id}      — main word data.
 *   - GET /api/words/{id}/examples (or /api/examples) — example sentences.
 */
public class WordDetailActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {
    private long wordId;
    private String langHint = "ja";
    private LinguaApiService apiService;
    private TextToSpeech tts;
    private MediaPlayer mediaPlayer;
    private boolean isFavorite = false;

    private TextView tvWord, tvReading, tvLevel, tvMeanings, tvEmpty;
    private ImageButton btnFavorite, btnSpeak;
    private LinearLayout layoutExamples;
    private ProgressBar progressBar;
    private Word currentWord;
    // 3.1 Stroke Order: view hiển thị animation các nét chữ Kanji/Hán tự
    private StrokeOrderView strokeOrderView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_detail);

        apiService = ApiClient.getService(this);
        tts = new TextToSpeech(this, this);
        wordId = getIntent().getLongExtra("wordId", 0);
        String lang = getIntent().getStringExtra("languageCode");
        if (lang != null) langHint = lang;

        tvWord = findViewById(R.id.tvWord);
        tvReading = findViewById(R.id.tvReading);
        tvLevel = findViewById(R.id.tvLevel);
        tvMeanings = findViewById(R.id.tvMeanings);
        tvEmpty = findViewById(R.id.tvEmpty);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnSpeak = findViewById(R.id.btnSpeak);
        layoutExamples = findViewById(R.id.layoutExamples);
        progressBar = findViewById(R.id.progressBar);
        strokeOrderView = findViewById(R.id.strokeOrderView);

        btnSpeak.setOnClickListener(v -> playWordAudio());
        btnFavorite.setOnClickListener(v -> toggleFavorite());

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("📝 Chi tiết từ");
        loadWord();
        loadExamples();
        checkFavoriteState();
    }

    private void loadWord() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getWordById(wordId).enqueue(new Callback<ApiResponse<Word>>() {
            @Override public void onResponse(Call<ApiResponse<Word>> c, Response<ApiResponse<Word>> r) {
                progressBar.setVisibility(View.GONE);
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    currentWord = r.body().getData();
                    if (currentWord != null) renderWord();
                }
            }
            @Override public void onFailure(Call<ApiResponse<Word>> c, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(WordDetailActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderWord() {
        tvWord.setText(currentWord.getText());
        tvReading.setText(currentWord.getDisplayReading());
        String level = currentWord.getLevel();
        tvLevel.setText(level != null ? level : "");
        tvLevel.setVisibility(level != null && !level.isEmpty() ? View.VISIBLE : View.GONE);

        if (currentWord.getMeanings() != null && !currentWord.getMeanings().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (Word.WordMeaning m : currentWord.getMeanings()) {
                sb.append(i++).append(". ").append(m.getMeaning()).append("\n");
            }
            tvMeanings.setText(sb.toString().trim());
        } else {
            tvMeanings.setText("(Không có nghĩa)");
        }

        // 3.1 Stroke Order: nếu là 1 ký tự CJK (Kanji / Hán tự), nạp stroke data.
        maybeLoadStrokes();
    }

    /**
     * 3.1 Stroke Order: chỉ kích hoạt khi text là MỘT ký tự thuộc khối CJK
     * (Kanji 4E00-9FFF, Extension A 3400-4DBF). Hiragana / Katakana / Latin
     * bỏ qua vì không có nét chữ tiêu chuẩn KanjiVG.
     */
    private void maybeLoadStrokes() {
        if (strokeOrderView == null || currentWord == null) return;
        String text = currentWord.getText();
        if (text == null || text.length() != 1) return;
        int cp = text.codePointAt(0);
        boolean isCjk = (cp >= 0x4E00 && cp <= 0x9FFF) || (cp >= 0x3400 && cp <= 0x4DBF);
        if (!isCjk) return;

        apiService.getCharacter(text).enqueue(new Callback<ApiResponse<com.lingua.app.models.Character>>() {
            @Override public void onResponse(Call<ApiResponse<com.lingua.app.models.Character>> c,
                                             Response<ApiResponse<com.lingua.app.models.Character>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    com.lingua.app.models.Character ch = r.body().getData();
                    if (ch.strokes != null && !ch.strokes.isEmpty()) {
                        strokeOrderView.setVisibility(View.VISIBLE);
                        strokeOrderView.setStrokes(ch.strokes);
                    } else {
                        // Không có path SVG -> fallback: vẽ ký tự
                        strokeOrderView.setVisibility(View.VISIBLE);
                        strokeOrderView.setCharacter(text);
                    }
                }
            }
            @Override public void onFailure(Call<ApiResponse<com.lingua.app.models.Character>> c, Throwable t) {
                // Im lặng — đây là tính năng bổ sung, không cần báo lỗi
            }
        });
    }

    private void loadExamples() {
        apiService.getWordExamples(wordId).enqueue(new Callback<ApiResponse<List<ExampleSentence>>>() {
            @Override public void onResponse(Call<ApiResponse<List<ExampleSentence>>> c, Response<ApiResponse<List<ExampleSentence>>> r) {
                List<ExampleSentence> data = r.isSuccessful() && r.body() != null ? r.body().getData() : null;
                renderExamples(data);
            }
            @Override public void onFailure(Call<ApiResponse<List<ExampleSentence>>> c, Throwable t) {
                renderExamples(null);
            }
        });
    }

    private void renderExamples(List<ExampleSentence> data) {
        layoutExamples.removeAllViews();
        if (data == null || data.isEmpty()) {
            // Try to fall back to embedded meanings
            if (currentWord != null && currentWord.getMeanings() != null) {
                for (Word.WordMeaning m : currentWord.getMeanings()) {
                    if (m.getExample() != null && !m.getExample().isEmpty()) {
                        addExampleCard(m.getExample(), m.getExampleTranslation(), null);
                    }
                }
            }
            if (layoutExamples.getChildCount() == 0) tvEmpty.setVisibility(View.VISIBLE);
            return;
        }
        for (ExampleSentence ex : data) {
            addExampleCard(ex.getSentence(), ex.getTranslation(), ex.audioUrl);
        }
    }

    private void addExampleCard(String sentence, String translation, String audioUrl) {
        // 6.7 FIX: bỏ inflate android.R.layout.simple_list_item_2 vì view này
        // không được dùng (chỉ tạo container LinearLayout mới). Code cũ inflate
        // rồi vứt đi, gây confusion và lãng phí một lần inflate.
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (12 * getResources().getDisplayMetrics().density);
        container.setPadding(p, p, p, p);
        container.setBackgroundColor(0xFFFFFFFF);
        ViewGroup.MarginLayoutParams lp = new ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        container.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(sentence);
        tv.setTextSize(16);
        tv.setTextColor(0xFF222222);
        container.addView(tv);

        if (translation != null && !translation.isEmpty()) {
            TextView tt = new TextView(this);
            tt.setText(translation);
            tt.setTextSize(13);
            tt.setTextColor(0xFF888888);
            container.addView(tt);
        }

        if (audioUrl != null && !audioUrl.isEmpty()) {
            Button play = new Button(this);
            play.setText("🔊 Nghe");
            play.setBackgroundTintList(getResources().getColorStateList(R.color.lingua_blue));
            play.setTextColor(0xFFFFFFFF);
            play.setOnClickListener(v -> playUrl(audioUrl));
            container.addView(play);
        }
        layoutExamples.addView(container);
    }

    private void playWordAudio() {
        if (currentWord != null && currentWord.getAudioUrl() != null && !currentWord.getAudioUrl().isEmpty()) {
            playUrl(currentWord.getAudioUrl());
            return;
        }
        if (tts == null || currentWord == null) return;
        Locale locale;
        String lang = currentWord.getLanguageCode() != null ? currentWord.getLanguageCode() : langHint;
        // 3.7 / TTS FIX: add Korean. Without this branch, ko words would fall
        // through to Japanese TTS and read nonsense.
        switch (lang) {
            case "en": locale = Locale.ENGLISH; break;
            case "zh": locale = Locale.CHINESE; break;
            case "ko": locale = Locale.KOREAN; break;
            default: locale = Locale.JAPANESE; break;
        }
        tts.setLanguage(locale);
        tts.speak(currentWord.getText(), TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void playUrl(String url) {
        try {
            if (mediaPlayer != null) mediaPlayer.release();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi phát âm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * BUG B11 FIX + TODO 1.2:
     * Utilise le nouvel endpoint léger GET /api/favorites/check?type=WORD&itemId=...
     * au lieu de télécharger toute la liste des favoris (lent + bande passante
     * gaspillée si l'utilisateur a plusieurs centaines de favoris).
     */
    private void checkFavoriteState() {
        apiService.checkFavorite("WORD", wordId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override public void onResponse(Call<ApiResponse<Map<String, Object>>> c, Response<ApiResponse<Map<String, Object>>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    Object favVal = r.body().getData().get("favorite");
                    if (favVal == null) favVal = r.body().getData().get("isFavorite");
                    if (favVal == null) favVal = r.body().getData().get("favorited");
                    isFavorite = favVal != null && (
                            (favVal instanceof Boolean && (Boolean) favVal)
                            || "true".equalsIgnoreCase(String.valueOf(favVal))
                            || "1".equals(String.valueOf(favVal)));
                    runOnUiThread(() -> btnFavorite.setImageResource(isFavorite
                            ? android.R.drawable.btn_star_big_on
                            : android.R.drawable.btn_star_big_off));
                }
            }
            @Override public void onFailure(Call<ApiResponse<Map<String, Object>>> c, Throwable t) {
                // Fallback : ancien comportement (liste complète)
                apiService.getFavorites("WORD").enqueue(new Callback<ApiResponse<List<Favorite>>>() {
                    @Override public void onResponse(Call<ApiResponse<List<Favorite>>> c2, Response<ApiResponse<List<Favorite>>> r2) {
                        if (r2.isSuccessful() && r2.body() != null && r2.body().getData() != null) {
                            for (Favorite f : r2.body().getData()) {
                                if (f.itemId == wordId) { isFavorite = true; break; }
                            }
                            runOnUiThread(() -> btnFavorite.setImageResource(isFavorite
                                    ? android.R.drawable.btn_star_big_on
                                    : android.R.drawable.btn_star_big_off));
                        }
                    }
                    @Override public void onFailure(Call<ApiResponse<List<Favorite>>> c2, Throwable t2) {}
                });
            }
        });
    }

    private void toggleFavorite() {
        if (isFavorite) {
            apiService.removeFavorite("WORD", wordId).enqueue(new Callback<ApiResponse<Object>>() {
                @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                    isFavorite = false;
                    btnFavorite.setImageResource(android.R.drawable.btn_star_big_off);
                    Toast.makeText(WordDetailActivity.this, "Đã bỏ khỏi yêu thích", Toast.LENGTH_SHORT).show();
                }
                @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {}
            });
        } else {
            Map<String, Object> body = new HashMap<>();
            body.put("type", "WORD");
            body.put("itemId", wordId);
            body.put("item_id", wordId);
            apiService.addFavorite(body).enqueue(new Callback<ApiResponse<Object>>() {
                @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                    isFavorite = true;
                    btnFavorite.setImageResource(android.R.drawable.btn_star_big_on);
                    Toast.makeText(WordDetailActivity.this, "🔖 Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show();
                }
                @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {}
            });
        }
    }

    @Override public void onInit(int status) {}

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (mediaPlayer != null) { try { mediaPlayer.release(); } catch (Exception ignore) {} }
        super.onDestroy();
    }
}
