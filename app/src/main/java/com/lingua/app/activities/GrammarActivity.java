package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.lingua.app.R;
import com.lingua.app.adapters.GrammarAdapter;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.Grammar;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Grammar list screen.
 *
 * GET /api/grammars?language=ja&level=N4&search=...
 * Tap an item to open GrammarDetailActivity.
 */
public class GrammarActivity extends AppCompatActivity {
    private TabLayout tabLanguage;
    private Spinner spinnerLevel;
    private EditText etSearch;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private ProgressBar progressBar;

    private LinguaApiService apiService;
    private GrammarAdapter adapter;
    private final List<Grammar> items = new ArrayList<>();
    private String currentLang = "ja";
    private String currentLevel = "";
    private final Handler debounce = new Handler();
    private Runnable searchRun;

    private static final String[][] LEVELS = {
            {"N5", "N4", "N3", "N2", "N1"},
            {"A1", "A2", "B1", "B2", "C1", "C2"},
            {"HSK1", "HSK2", "HSK3", "HSK4", "HSK5", "HSK6"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grammar);

        apiService = ApiClient.getService(this);
        tabLanguage = findViewById(R.id.tabLanguage);
        spinnerLevel = findViewById(R.id.spinnerLevel);
        etSearch = findViewById(R.id.etSearch);
        recyclerView = findViewById(R.id.recyclerView);
        tvEmpty = findViewById(R.id.tvEmpty);
        progressBar = findViewById(R.id.progressBar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GrammarAdapter(items, this::openDetail);
        recyclerView.setAdapter(adapter);

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("📖 Ngữ pháp");

        setupTabs();
        setupSearch();
        updateLevels(0);
        loadGrammars();
    }

    private void setupTabs() {
        String[] langs = {"🇯🇵 Nhật", "🇬🇧 Anh", "🇨🇳 Trung"};
        final String[] codes = {"ja", "en", "zh"};
        for (String l : langs) tabLanguage.addTab(tabLanguage.newTab().setText(l));
        tabLanguage.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                int p = tab.getPosition();
                currentLang = codes[p];
                currentLevel = "";
                updateLevels(p);
                loadGrammars();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void updateLevels(int idx) {
        if (idx >= LEVELS.length) idx = 0;
        String[] arr = LEVELS[idx];
        final String[] all = new String[arr.length + 1];
        all[0] = "Tất cả";
        System.arraycopy(arr, 0, all, 1, arr.length);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, all);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLevel.setAdapter(ad);
        spinnerLevel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentLevel = position == 0 ? "" : all[position];
                loadGrammars();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                if (searchRun != null) debounce.removeCallbacks(searchRun);
                searchRun = GrammarActivity.this::loadGrammars;
                debounce.postDelayed(searchRun, 350);
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });
    }

    private void loadGrammars() {
        progressBar.setVisibility(View.VISIBLE);
        String search = etSearch.getText().toString().trim();
        apiService.getGrammars(currentLang, currentLevel.isEmpty() ? null : currentLevel,
                search.isEmpty() ? null : search)
                .enqueue(new Callback<ApiResponse<List<Grammar>>>() {
                    @Override public void onResponse(Call<ApiResponse<List<Grammar>>> c, Response<ApiResponse<List<Grammar>>> r) {
                        progressBar.setVisibility(View.GONE);
                        items.clear();
                        if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                            items.addAll(r.body().getData());
                        }
                        adapter.notifyDataSetChanged();
                        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                    @Override public void onFailure(Call<ApiResponse<List<Grammar>>> c, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        tvEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void openDetail(Grammar g) {
        Intent i = new Intent(this, GrammarDetailActivity.class);
        i.putExtra("grammarId", g.id);
        startActivity(i);
    }
}
