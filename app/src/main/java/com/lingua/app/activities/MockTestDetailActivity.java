package com.lingua.app.activities;

import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

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
    private final Handler timerHandler = new Handler();
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
        apiService.getMockTestDetail(mockTestId).enqueue(new Callback<ApiResponse<MockTestDetail>>() {
            @Override public void onResponse(Call<ApiResponse<MockTestDetail>> c, Response<ApiResponse<MockTestDetail>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                    detail = r.body().getData();
                    if (detail != null) startTest();
                }
            }
            @Override public void onFailure(Call<ApiResponse<MockTestDetail>> c, Throwable t) {
                Toast.makeText(MockTestDetailActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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
        startedAtMs = System.currentTimeMillis();
        durationMs = detail.durationMin > 0 ? detail.durationMin * 60_000L : 0;
        if (durationMs > 0) timerHandler.post(timerTick);
        else tvTimer.setVisibility(View.GONE);
        showQuestion(0);
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
                .setNegativeButton("Ấ không, tiếp tục", null)
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
        timerHandler.removeCallbacks(timerTick);
        if (mediaPlayer != null) { try { mediaPlayer.release(); } catch (Exception ignore) {} }
        super.onDestroy();
    }
}
