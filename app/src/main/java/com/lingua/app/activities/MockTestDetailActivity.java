package com.lingua.app.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.MockTestDetail;
import com.lingua.app.models.MockTestResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Runs a mock test session.
 *
 * GET /api/mock-tests/{id} loads questions; the user navigates with prev/next
 * and submits via POST /api/mock-tests/{id}/submit. A countdown timer enforces
 * duration_min. Results open MockTestResultActivity.
 */
public class MockTestDetailActivity extends AppCompatActivity {
    private LinguaApiService apiService;
    private long mockTestId;
    private MockTestDetail detail;
    private final List<MockTestDetail.TestQuestion> questions = new ArrayList<>();
    private final Map<Long, String> userAnswers = new HashMap<>();
    private int currentIndex = 0;
    private long startedAtMs = 0;

    private TextView tvQuestion, tvProgress, tvTimer;
    private LinearLayout layoutOptions;
    private EditText etAnswer;
    private Button btnPrev, btnNext, btnSubmit, btnPlayAudio;
    private ProgressBar progressBarTest;
    private MediaPlayer mediaPlayer;
    // CB-4 FIX: phải chỉ rõ Looper.getMainLooper() cho Handler.
    // Từ Android 11 (API 30) `new Handler()` bị deprecated và có thể throw
    // RuntimeException nếu được khởi tạo trên non-Looper thread.
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long durationMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mock_test_detail);
        apiService = ApiClient.getService(this);
        mockTestId = getIntent().getLongExtra("mockTestId", 0);

        tvQuestion = findViewById(R.id.tvQuestion);
        tvProgress = findViewById(R.id.tvProgress);
        tvTimer = findViewById(R.id.tvTimer);
        layoutOptions = findViewById(R.id.layoutOptions);
        etAnswer = findViewById(R.id.etAnswer);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnPlayAudio = findViewById(R.id.btnPlayAudio);
        progressBarTest = findViewById(R.id.progressBarTest);

        btnPrev.setOnClickListener(v -> goTo(currentIndex - 1));
        btnNext.setOnClickListener(v -> goTo(currentIndex + 1));
        btnSubmit.setOnClickListener(v -> confirmSubmit());

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("Đang tải...");

        loadDetail();
    }

    private void loadDetail() {
        // UX-7 FIX: hiện loading spinner trong khi đang fetch chi tiết bài thi.
        if (progressBarTest != null) {
            progressBarTest.setVisibility(View.VISIBLE);
            progressBarTest.setIndeterminate(true);
        }
        apiService.getMockTestDetail(mockTestId).enqueue(new Callback<ApiResponse<MockTestDetail>>() {
            @Override public void onResponse(Call<ApiResponse<MockTestDetail>> c, Response<ApiResponse<MockTestDetail>> r) {
                // UX-7 FIX: tắt loading spinner khi load xong.
                if (progressBarTest != null) progressBarTest.setIndeterminate(false);
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    detail = r.body().getData();
                    if (detail != null) {
                        startTest();
                    } else {
                        showLoadErrorWithRetry("Dữ liệu bài thi rỗng");
                    }
                } else {
                    showLoadErrorWithRetry("Không tải được bài thi");
                }
            }
            @Override public void onFailure(Call<ApiResponse<MockTestDetail>> c, Throwable t) {
                if (progressBarTest != null) progressBarTest.setIndeterminate(false);
                // UX-7 FIX: hiện dialog "Thử lại" thay vì chỉ Toast rồi bỏ mặc.
                showLoadErrorWithRetry("Lỗi: " + t.getMessage());
            }
        });
    }

    /**
     * UX-7 FIX: hiển thị dialog lỗi kèm nút "Thử lại" thay vì để user mắc kẹt
     * trên màn hình trắng khi loadDetail() fail.
     */
    private void showLoadErrorWithRetry(String message) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("Không tải được bài thi")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Thử lại", (d, w) -> loadDetail())
                .setNegativeButton("Thoát", (d, w) -> finish())
                .show();
    }

    private void startTest() {
        if (getSupportActionBar() != null) getSupportActionBar().setTitle(detail.title != null ? detail.title : "Mock Test");
        questions.clear();
        if (detail.sections != null) {
            for (MockTestDetail.TestSection s : detail.sections) {
                if (s.questions != null) questions.addAll(s.questions);
            }
        }
        if (questions.isEmpty()) {
            Toast.makeText(this, "Bài thi không có câu hỏi", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // BUG #R3-M2 FIX: nếu activity được tái tạo sau config-change,
        // startedAtMs đã được restore trong onRestoreInstanceState — không
        // được overwrite, nếu không timer sẽ reset về full duration mỗi
        // lần xoay máy.
        if (startedAtMs == 0) {
            startedAtMs = System.currentTimeMillis();
        }
        durationMs = detail.durationMin > 0 ? detail.durationMin * 60_000L : 0;
        if (durationMs > 0) timerHandler.post(timerTick);
        else tvTimer.setVisibility(View.GONE);
        // BUG #R3-M2 FIX: khôi phục câu hỏi đang xem dở thay vì luôn hiển thị câu 0.
        int idxToShow = (currentIndex >= 0 && currentIndex < questions.size())
                ? currentIndex : 0;
        showQuestion(idxToShow);
    }

    // U3 FIX: track which countdown warnings (5-min / 1-min) we've already shown
    // so we don't spam Toasts every second.
    private boolean warned5min = false;
    private boolean warned1min = false;

    private final Runnable timerTick = new Runnable() {
        @Override public void run() {
            long elapsed = System.currentTimeMillis() - startedAtMs;
            long remaining = durationMs - elapsed;
            if (remaining <= 0) {
                tvTimer.setText("⏱ 00:00");
                Toast.makeText(MockTestDetailActivity.this, "⏰ Hết giờ!", Toast.LENGTH_LONG).show();
                submit();
                return;
            }
            long min = remaining / 60_000;
            long sec = (remaining % 60_000) / 1000;
            tvTimer.setText(String.format(Locale.getDefault(), "⏱ %02d:%02d", min, sec));
            // U3 FIX: turn the timer red and show a Toast at 5 min / 1 min left.
            if (remaining <= 60_000) {
                tvTimer.setTextColor(0xFFD32F2F);
                if (!warned1min) {
                    warned1min = true;
                    Toast.makeText(MockTestDetailActivity.this,
                            "⚠️ Còn 1 phút!", Toast.LENGTH_LONG).show();
                }
            } else if (remaining <= 5 * 60_000) {
                tvTimer.setTextColor(0xFFF57C00);
                if (!warned5min) {
                    warned5min = true;
                    Toast.makeText(MockTestDetailActivity.this,
                            "⚠️ Còn 5 phút!", Toast.LENGTH_LONG).show();
                }
            }
            timerHandler.postDelayed(this, 1000);
        }
    };

    /**
     * U3 FIX: confirm before leaving the test so the user doesn't accidentally
     * lose all their answers by tapping back.
     */
    @Override
    public void onBackPressed() {
        if (questions.isEmpty()) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Thoát bài thi?")
                .setMessage("Bài thi sẽ bị huỷ và tất cả câu trả lời của bạn sẽ mất. Bạn có chắc không?")
                .setPositiveButton("Thoát", (d, w) -> super.onBackPressed())
                // BUG #R3-L1 FIX: typo "Ấ không, tiếp tục" → "Không, tiếp tục học".
                .setNegativeButton("Không, tiếp tục học", null)
                .show();
    }

    private void showQuestion(int index) {
        if (index < 0 || index >= questions.size()) return;
        currentIndex = index;
        MockTestDetail.TestQuestion q = questions.get(index);

        tvProgress.setText(String.format(Locale.getDefault(), "Câu %d/%d", index + 1, questions.size()));
        progressBarTest.setMax(questions.size());
        progressBarTest.setProgress(index + 1);
        tvQuestion.setText(q.getDisplayPrompt());

        // Audio button
        if (q.audioUrl != null && !q.audioUrl.isEmpty()) {
            btnPlayAudio.setVisibility(View.VISIBLE);
            btnPlayAudio.setOnClickListener(v -> playAudio(q.audioUrl));
        } else {
            btnPlayAudio.setVisibility(View.GONE);
        }

        layoutOptions.removeAllViews();
        if (q.options != null && !q.options.isEmpty()) {
            etAnswer.setVisibility(View.GONE);
            String saved = userAnswers.get(q.id);
            // U4 FIX: wrap RadioButtons in a RadioGroup so they are mutually
            // exclusive. Previously they sat in a plain LinearLayout, which let
            // the user check several options at once and made the submitted
            // answer ambiguous.
            final RadioGroup group = new RadioGroup(this);
            group.setOrientation(RadioGroup.VERTICAL);
            int idCounter = 1;
            for (final String opt : q.options) {
                RadioButton rb = new RadioButton(this);
                rb.setId(View.generateViewId());
                rb.setText(opt);
                rb.setTextSize(15);
                rb.setPadding(8, 12, 8, 12);
                rb.setChecked(opt.equals(saved));
                rb.setTag(opt);
                group.addView(rb);
            }
            group.setOnCheckedChangeListener((g, checkedId) -> {
                RadioButton selected = g.findViewById(checkedId);
                if (selected != null && selected.getTag() != null) {
                    userAnswers.put(q.id, selected.getTag().toString());
                }
            });
            layoutOptions.addView(group);
        } else {
            etAnswer.setVisibility(View.VISIBLE);
            String saved = userAnswers.get(q.id);
            etAnswer.setText(saved != null ? saved : "");
            etAnswer.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) userAnswers.put(q.id, etAnswer.getText().toString());
            });
        }

        btnPrev.setEnabled(index > 0);
        btnNext.setEnabled(index < questions.size() - 1);
    }

    private void goTo(int idx) {
        // capture current text answer if any
        if (etAnswer.getVisibility() == View.VISIBLE) {
            MockTestDetail.TestQuestion q = questions.get(currentIndex);
            userAnswers.put(q.id, etAnswer.getText().toString());
        }
        if (idx >= 0 && idx < questions.size()) showQuestion(idx);
    }

    private void confirmSubmit() {
        // capture last answer
        if (etAnswer.getVisibility() == View.VISIBLE) {
            MockTestDetail.TestQuestion q = questions.get(currentIndex);
            userAnswers.put(q.id, etAnswer.getText().toString());
        }
        // 6.12 FIX: đếm số câu thực sự có nội dung trả lời (non-empty / non-blank).
        // Trước đây dùng userAnswers.size() đếm cả những câu được put("") khi user
        // mở rồi để trống → báo sai số câu đã làm.
        int answeredCount = countAnsweredNonEmpty();
        new AlertDialog.Builder(this)
                .setTitle("Nộp bài thi?")
                .setMessage("Bạn đã trả lời " + answeredCount + "/" + questions.size() + " câu.")
                .setPositiveButton("Nộp", (d, w) -> submit())
                .setNegativeButton("Tiếp tục", null)
                .show();
    }

    /**
     * 6.12 FIX: đếm số câu trả lời thực sự có nội dung (không null, không trim()
     * thành chuỗi rỗng).
     */
    private int countAnsweredNonEmpty() {
        int n = 0;
        for (String v : userAnswers.values()) {
            if (v != null && !v.trim().isEmpty()) n++;
        }
        return n;
    }

    private void submit() {
        timerHandler.removeCallbacks(timerTick);
        Map<String, Object> body = new HashMap<>();
        List<Map<String, Object>> answers = new ArrayList<>();
        for (Map.Entry<Long, String> e : userAnswers.entrySet()) {
            Map<String, Object> a = new HashMap<>();
            a.put("questionId", e.getKey());
            a.put("question_id", e.getKey());
            a.put("answer", e.getValue());
            answers.add(a);
        }
        body.put("answers", answers);
        body.put("durationSec", (System.currentTimeMillis() - startedAtMs) / 1000);

        apiService.submitMockTest(mockTestId, body).enqueue(new Callback<ApiResponse<MockTestResult>>() {
            @Override public void onResponse(Call<ApiResponse<MockTestResult>> c, Response<ApiResponse<MockTestResult>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    MockTestResult result = r.body().getData();
                    Intent i = new Intent(MockTestDetailActivity.this, MockTestResultActivity.class);
                    i.putExtra("scorePercent", result.getScore());
                    i.putExtra("correctCount", result.correctCount);
                    i.putExtra("total", result.total > 0 ? result.total : questions.size());
                    i.putExtra("xpEarned", result.xpEarned);
                    i.putExtra("passed", result.passed);
                    startActivity(i);

                    // BUG #20 FIX: nếu là placement test, gửi kết quả level đề
                    // xuất về caller (MockTestActivity → OnboardingActivity).
                    boolean isPlacement = getIntent().getBooleanExtra("placement", false);
                    if (isPlacement) {
                        String suggestedLevel = suggestLevelFromScore(
                                result.getScore(),
                                getIntent().getStringExtra("language"));
                        Intent back = new Intent();
                        back.putExtra("level", suggestedLevel);
                        back.putExtra("scorePercent", result.getScore());
                        setResult(RESULT_OK, back);
                    }
                    finish();
                } else {
                    Toast.makeText(MockTestDetailActivity.this, "Không nộp được bài", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<ApiResponse<MockTestResult>> c, Throwable t) {
                Toast.makeText(MockTestDetailActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * BUG #20 FIX: từ điểm % bài placement → suy ra level đề xuất theo từng ngôn ngữ.
     * Ngưỡng đơn giản: <20% → mức thấp nhất, 20–40% → +1, ... ≥80% → mức cao nhất.
     */
    private String suggestLevelFromScore(int scorePercent, String language) {
        String[] levels;
        if ("en".equals(language)) {
            levels = new String[]{"A1", "A2", "B1", "B2", "C1", "C2"};
        } else if ("zh".equals(language)) {
            levels = new String[]{"HSK1", "HSK2", "HSK3", "HSK4", "HSK5", "HSK6"};
        } else if ("ko".equals(language)) {
            levels = new String[]{"TOPIK1", "TOPIK2", "TOPIK3", "TOPIK4", "TOPIK5", "TOPIK6"};
        } else {
            levels = new String[]{"N5", "N4", "N3", "N2", "N1"};
        }
        int idx = (int) Math.floor(scorePercent / 100.0 * levels.length);
        if (idx < 0) idx = 0;
        if (idx >= levels.length) idx = levels.length - 1;
        return levels[idx];
    }

    private void playAudio(String url) {
        try {
            if (mediaPlayer != null) mediaPlayer.release();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
        } catch (Exception e) { /* ignore */ }
    }

    @Override
    protected void onDestroy() {
        // CB-3 FIX: đảm bảo release MediaPlayer + clear tất cả callbacks của
        // timerHandler để không leak native AudioTrack handle khi user vào/
        // thoát mock test liên tiếp. Trước đây chỉ removeCallbacks(timerTick)
        // mà không remove các callback khác có thể đã được post.
        timerHandler.removeCallbacksAndMessages(null);
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Exception ignore) {}
            mediaPlayer = null;
        }
        super.onDestroy();
    }

    /**
     * BUG #R3-M2 FIX: persist user answers + current index + start timestamp
     * across configuration changes (screen rotation, dark-mode toggle, locale
     * change, etc.). Trước đây userAnswers chỉ tồn tại trong RAM của Activity
     * instance — khi user xoay máy, toàn bộ HashMap mất → user phải làm lại
     * bài thi từ đầu.
     *
     * Lưu ý: cũng capture nội dung etAnswer hiện tại (nếu visible) trước khi
     * save, vì với câu hỏi đang nhập dở, listener onFocusChange chỉ fire khi
     * user thực sự rời focus — config-change không trigger focus change.
     */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Capture current input first
        if (etAnswer != null && etAnswer.getVisibility() == View.VISIBLE
                && currentIndex < questions.size()) {
            userAnswers.put(questions.get(currentIndex).id, etAnswer.getText().toString());
        }
        // Serialize HashMap<Long,String> as 2 parallel arraylists since
        // HashMap of Long → String is not directly Parcelable.
        ArrayList<Long> keys = new ArrayList<>(userAnswers.keySet());
        ArrayList<String> values = new ArrayList<>();
        for (Long k : keys) values.add(userAnswers.get(k));
        outState.putSerializable("answers_keys", keys);
        outState.putStringArrayList("answers_values", values);
        outState.putInt("current_index", currentIndex);
        outState.putLong("started_at", startedAtMs);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        // BUG #R3-M2 FIX: restore HashMap from parallel arraylists.
        try {
            ArrayList<Long> keys = (ArrayList<Long>) savedInstanceState.getSerializable("answers_keys");
            ArrayList<String> values = savedInstanceState.getStringArrayList("answers_values");
            if (keys != null && values != null && keys.size() == values.size()) {
                userAnswers.clear();
                for (int i = 0; i < keys.size(); i++) {
                    userAnswers.put(keys.get(i), values.get(i));
                }
            }
            int idx = savedInstanceState.getInt("current_index", 0);
            long started = savedInstanceState.getLong("started_at", 0L);
            if (started > 0) startedAtMs = started;
            // Re-render currentIndex once loadDetail finishes (loadDetail()
            // will call startTest() which calls showQuestion(0) by default).
            // We override the index here so when loadDetail returns,
            // showQuestion is called with the right one.
            currentIndex = idx;
        } catch (Throwable ignored) { /* defensive */ }
    }
}
