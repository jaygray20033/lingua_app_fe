package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.tabs.TabLayout;
import com.lingua.app.R;
import com.lingua.app.adapters.WordAdapter;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.Favorite;
import com.lingua.app.models.Word;
import com.lingua.app.utils.OfflineCache;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.*;

/**
 * Vocabulary screen.
 *
 * Improvements over the original:
 *   - Infinite scroll pagination (loads page+1 when reaching the bottom).
 *   - Bookmark/Favorite icon per word — POST/DELETE /api/favorites.
 *   - Tap a word to open WordDetailActivity (examples + audio).
 *   - Plays per-word audioUrl via MediaPlayer when available, falls back to TTS.
 */
public class VocabularyActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private RecyclerView recyclerView;
    private WordAdapter wordAdapter;
    private final List<Word> wordList = new ArrayList<>();
    private TabLayout tabLanguage;
    private Spinner spinnerLevel;
    private EditText etSearch;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private TextToSpeech tts;
    private LinguaApiService apiService;
    private android.media.MediaPlayer mediaPlayer;
    private final Handler searchDebounce = new Handler();
    private Runnable searchRunnable;

    private String currentLanguage = "ja";
    private String currentLevel = "";
    private int currentPage = 1;
    private boolean isLoading = false;
    private boolean hasMore = true;

    private static final String[][] LEVELS = {
        {"N5", "N4", "N3", "N2", "N1"},
        {"A1", "A2", "B1", "B2", "C1", "C2"},
        {"HSK1", "HSK2", "HSK3", "HSK4", "HSK5", "HSK6"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vocabulary);

        apiService = ApiClient.getService(this);
        tts = new TextToSpeech(this, this);

        tabLanguage = findViewById(R.id.tabLanguage);
        spinnerLevel = findViewById(R.id.spinnerLevel);
        etSearch = findViewById(R.id.etSearch);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty = findViewById(R.id.tvEmpty);

        LinearLayoutManager lm = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(lm);
        wordAdapter = new WordAdapter(wordList, this::speakWord);
        wordAdapter.setOnWordClickListener(this::openWordDetail);
        wordAdapter.setOnFavoriteListener(this::toggleFavorite);
        recyclerView.setAdapter(wordAdapter);

        // 6.6 FIX: FAB scroll-to-top — hiển thị khi cuộn xuống > 5 items.
        final com.google.android.material.floatingactionbutton.FloatingActionButton fab =
                findViewById(R.id.fabScrollTop);
        if (fab != null) {
            fab.setOnClickListener(v -> recyclerView.smoothScrollToPosition(0));
        }

        // Infinite scroll + FAB visibility
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                int first = lm.findFirstVisibleItemPosition();
                if (fab != null) {
                    if (first > 5) fab.show(); else fab.hide();
                }
                if (dy <= 0 || isLoading || !hasMore) return;
                int total = lm.getItemCount();
                int last = lm.findLastVisibleItemPosition();
                if (last >= total - 5) {
                    currentPage++;
                    loadWords(false);
                }
            }
        });

        setupTabs();
        setupSearch();
        updateLevelSpinner(0);
        loadFavoriteIds();
        loadWords(true);
    }

    private void setupTabs() {
        String[] langs = {"🇯🇵 Nhật", "🇬🇧 Anh", "🇨🇳 Trung"};
        final String[] langCodes = {"ja", "en", "zh"};
        for (String lang : langs) tabLanguage.addTab(tabLanguage.newTab().setText(lang));
        tabLanguage.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                int pos = tab.getPosition();
                currentLanguage = langCodes[pos];
                currentLevel = "";
                resetPagination();
                updateLevelSpinner(pos);
                loadWords(true);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    /**
     * 6.6 FIX: tránh `loadWords()` chạy 2 lần khi đổi tab ngôn ngữ.
     * - Vấn đề cũ: `tabLanguage.onTabSelected` đã gọi `loadWords(true)`,
     *   sau đó `updateLevelSpinner()` set adapter mới khiến
     *   `Spinner.setOnItemSelectedListener` fire ngay (position=0) → call
     *   `loadWords(true)` lần 2. Lãng phí 1 request.
     * - Cách fix: dùng cờ `suppressSpinnerEvent` để bỏ qua callback đầu tiên
     *   sau khi setAdapter. Callback bị suppress đúng 1 lần.
     */
    private boolean suppressSpinnerEvent = false;

    private void updateLevelSpinner(int langIndex) {
        if (langIndex >= LEVELS.length) langIndex = 0;
        String[] levels = LEVELS[langIndex];
        final String[] allLevels = new String[levels.length + 1];
        allLevels[0] = "Tất cả";
        System.arraycopy(levels, 0, allLevels, 1, levels.length);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, allLevels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Suppress initial callback do setAdapter sẽ trigger.
        suppressSpinnerEvent = true;
        spinnerLevel.setAdapter(adapter);
        spinnerLevel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (suppressSpinnerEvent) {
                    suppressSpinnerEvent = false;
                    return;
                }
                currentLevel = position == 0 ? "" : allLevels[position];
                resetPagination();
                loadWords(true);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupSearch() {
        // 6.6 FIX: nút clear (X) cạnh ô tìm kiếm.
        final android.widget.ImageButton btnClear = findViewById(R.id.btnClearSearch);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                etSearch.setText("");
                btnClear.setVisibility(View.GONE);
            });
        }
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (btnClear != null) {
                    btnClear.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                }
                if (searchRunnable != null) searchDebounce.removeCallbacks(searchRunnable);
                searchRunnable = () -> {
                    resetPagination();
                    loadWords(true);
                };
                searchDebounce.postDelayed(searchRunnable, 350);
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void resetPagination() {
        currentPage = 1;
        hasMore = true;
        wordList.clear();
        wordAdapter.notifyDataSetChanged();
    }

    private void loadWords(boolean firstPage) {
        if (isLoading) return;
        isLoading = true;
        progressBar.setVisibility(View.VISIBLE);

        final String search = etSearch.getText().toString().trim();
        // For the first, unfiltered page, use the offline cache as a "while-loading"
        // placeholder so the screen never appears empty when the device has no network.
        if (firstPage && search.isEmpty()) {
            List<Word> cached = OfflineCache.getInstance(VocabularyActivity.this)
                    .getWords(currentLanguage, currentLevel);
            if (!cached.isEmpty()) {
                wordList.clear();
                wordList.addAll(cached);
                wordAdapter.notifyDataSetChanged();
                tvEmpty.setVisibility(View.GONE);
            }
        }

        apiService.getWords(currentLanguage, currentLevel.isEmpty() ? null : currentLevel,
                search.isEmpty() ? null : search, currentPage, 30)
                .enqueue(new Callback<ApiResponse<List<Word>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<List<Word>>> call, Response<ApiResponse<List<Word>>> response) {
                        isLoading = false;
                        progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            List<Word> words = response.body().getData();
                            int newCount = words != null ? words.size() : 0;
                            if (newCount < 30) hasMore = false;
                            int previousSize = wordList.size();
                            if (firstPage) wordList.clear();
                            if (words != null) wordList.addAll(words);
                            if (firstPage) wordAdapter.notifyDataSetChanged();
                            else wordAdapter.notifyItemRangeInserted(previousSize, newCount);
                            tvEmpty.setVisibility(wordList.isEmpty() ? View.VISIBLE : View.GONE);

                            // Cache the first page (unfiltered) for offline access.
                            if (firstPage && search.isEmpty() && words != null && !words.isEmpty()
                                    && offlineEnabled()) {
                                OfflineCache.getInstance(VocabularyActivity.this)
                                        .putWords(currentLanguage, currentLevel, words);
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<ApiResponse<List<Word>>> call, Throwable t) {
                        isLoading = false;
                        progressBar.setVisibility(View.GONE);
                        // Network failure → fall back to cache if we have one.
                        if (firstPage && search.isEmpty()) {
                            List<Word> cached = OfflineCache.getInstance(VocabularyActivity.this)
                                    .getWords(currentLanguage, currentLevel);
                            if (!cached.isEmpty()) {
                                wordList.clear();
                                wordList.addAll(cached);
                                wordAdapter.notifyDataSetChanged();
                                tvEmpty.setVisibility(View.GONE);
                                Toast.makeText(VocabularyActivity.this,
                                        "📡 Đang xem dữ liệu offline", Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        Toast.makeText(VocabularyActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Reads the offline-cache toggle from Settings. Defaults to true. */
    private boolean offlineEnabled() {
        return getSharedPreferences("LinguaPrefs", MODE_PRIVATE)
                .getBoolean("offline_cache", true);
    }

    /** Loads existing word favorites and tells the adapter which IDs are starred. */
    private void loadFavoriteIds() {
        apiService.getFavorites("WORD").enqueue(new Callback<ApiResponse<List<Favorite>>>() {
            @Override public void onResponse(Call<ApiResponse<List<Favorite>>> c, Response<ApiResponse<List<Favorite>>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    Set<Long> ids = new HashSet<>();
                    for (Favorite f : r.body().getData()) ids.add(f.itemId);
                    wordAdapter.setFavoriteIds(ids);
                }
            }
            @Override public void onFailure(Call<ApiResponse<List<Favorite>>> c, Throwable t) {}
        });
    }

    private void toggleFavorite(Word word, boolean nowFavorite) {
        if (nowFavorite) {
            Map<String, Object> body = new HashMap<>();
            body.put("type", "WORD");
            body.put("itemId", word.getId());
            body.put("item_id", word.getId());
            apiService.addFavorite(body).enqueue(new Callback<ApiResponse<Object>>() {
                @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                    Toast.makeText(VocabularyActivity.this, "🔖 Đã thêm vào yêu thích", Toast.LENGTH_SHORT).show();
                }
                @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {}
            });
        } else {
            apiService.removeFavorite("WORD", word.getId()).enqueue(new Callback<ApiResponse<Object>>() {
                @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {
                    Toast.makeText(VocabularyActivity.this, "Đã bỏ khỏi yêu thích", Toast.LENGTH_SHORT).show();
                }
                @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {}
            });
        }
    }

    private void openWordDetail(Word word) {
        Intent i = new Intent(this, WordDetailActivity.class);
        i.putExtra("wordId", word.getId());
        i.putExtra("languageCode", word.getLanguageCode());
        startActivity(i);
    }

    public void speakWord(Word word) {
        // Prefer audioUrl if available
        if (word.getAudioUrl() != null && !word.getAudioUrl().isEmpty()) {
            try {
                if (mediaPlayer != null) { mediaPlayer.release(); }
                mediaPlayer = new android.media.MediaPlayer();
                mediaPlayer.setDataSource(word.getAudioUrl());
                mediaPlayer.prepareAsync();
                mediaPlayer.setOnPreparedListener(mp -> mp.start());
                return;
            } catch (Exception e) { /* fall through to TTS */ }
        }
        if (tts == null) return;
        Locale locale;
        switch (word.getLanguageCode() != null ? word.getLanguageCode() : "ja") {
            case "en": locale = Locale.ENGLISH; break;
            case "zh": locale = Locale.CHINESE; break;
            case "ko": locale = Locale.KOREAN; break;
            default: locale = Locale.JAPANESE; break;
        }
        tts.setLanguage(locale);
        tts.speak(word.getText(), TextToSpeech.QUEUE_FLUSH, null, null);
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            Toast.makeText(this, "TTS không khả dụng", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFavoriteIds();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (mediaPlayer != null) { try { mediaPlayer.release(); } catch (Exception ignore) {} }
        super.onDestroy();
    }
}
