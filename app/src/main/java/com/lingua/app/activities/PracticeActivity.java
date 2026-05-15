package com.lingua.app.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.*;

/**
 * Lesson practice screen.
 *
 * Supports MULTIPLE_CHOICE, FILL_BLANK / TRANSLATE, LISTEN_SELECT / DICTATION,
 * SENTENCE_ORDER (drag-tap to build sentence) and MATCHING (pair source ↔ target).
 *
 * Integrates the gamification flow:
 *   - Loses a heart on a wrong answer (POST /api/gamification/hearts/lose).
 *   - Triggers a LESSON_COMPLETE quest event when the lesson finishes
 *     (POST /api/quests/event).
 *   - Shows a lesson-result summary screen (XP earned, accuracy, streak).
 */
public class PracticeActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private TextView tvQuestion, tvScore, tvProgress, tvFeedback, tvHint, tvHearts;
    private EditText etFillBlank;
    private Button btnSubmit, btnNext, btnHint, btnListen;
    private LinearLayout layoutMultiChoice, layoutFillBlank, layoutMatch, layoutSentenceOrder;
    private LinearLayout layoutOrderTokens, layoutOrderAnswer;
    private LinearLayout layoutMatchLeft, layoutMatchRight;
    private ProgressBar progressBar, progressBarLesson;
    private LinguaApiService apiService;
    private TextToSpeech tts;

    private final List<Exercise> exercises = new ArrayList<>();
    private int currentIndex = 0;
    private int score = 0;
    private int currentHearts = -1; // unknown until first hearts response
    private long attemptId = -1;
    private long lessonId;
    private final Gson gson = new Gson();

    // U16 FIX: read sound/haptic preferences from SettingsActivity so they
    // actually take effect during practice.
    private boolean prefSound = true;
    private boolean prefHaptic = true;

    // U1 FIX: prevent double-submit on multi-choice (user double-tapping would
    // previously submit twice and lose 2 hearts).
    private boolean answerLocked = false;

    // BUG 7.1 FIX: giữ instance MediaPlayer hiện tại để có thể release()
    // khi user bấm "🔊 Nghe" liên tục hoặc thoát màn hình giữa chừng.
    // Nếu không release(), mỗi click sẽ tạo một MediaPlayer mới không được
    // GC trên các thiết bị RAM thấp → memory/resource leak và có thể crash.
    private android.media.MediaPlayer currentMediaPlayer = null;

    // BUG 6 FIX: đo thời gian trả lời thực tế cho từng bài tập. Trước đây
    // timeMs được hardcode = 5000 cho mọi câu trả lời → backend nhận toàn bộ
    // là 5 giây bất kể user trả lời trong 0.5s hay 50s → metric "tốc độ phản
    // hồi" và adaptive difficulty dựa trên tốc độ đều vô nghĩa.
    private long exerciseStartMs = 0L;

    // Sentence-order state
    private final List<String> orderTokens = new ArrayList<>();
    private final List<String> orderAnswer = new ArrayList<>();

    // Matching state
    private String matchSelectedLeft = null;
    private final Map<String, String> matchPairsCorrect = new HashMap<>();
    private final Map<String, String> matchUserPairs = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);

        apiService = ApiClient.getService(this);
        tts = new TextToSpeech(this, this);
        lessonId = getIntent().getLongExtra("lessonId", 1);

        // U16 FIX: load sound/haptic prefs (keys defined in SettingsActivity).
        SharedPreferences prefs = getSharedPreferences("LinguaPrefs", MODE_PRIVATE);
        prefSound = prefs.getBoolean("feedback_sound", true);
        prefHaptic = prefs.getBoolean("feedback_haptic", true);

        initViews();
        loadHearts();
        startLesson();
    }

    /**
     * U2 FIX: confirm before leaving the lesson so the user does not lose
     * progress by accidentally tapping back.
     */
    @Override
    public void onBackPressed() {
        if (exercises.isEmpty() || currentIndex >= exercises.size()) {
            super.onBackPressed();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Thoát bài học?")
                .setMessage("Tiến trình hiện tại sẽ không được lưu. Bạn có chắc chắn muốn thoát không?")
                .setPositiveButton("Thoát", (d, w) -> super.onBackPressed())
                .setNegativeButton("Tiếp tục học", null)
                .show();
    }

    private void initViews() {
        tvQuestion = findViewById(R.id.tvQuestion);
        tvScore = findViewById(R.id.tvScore);
        tvProgress = findViewById(R.id.tvProgress);
        tvFeedback = findViewById(R.id.tvFeedback);
        tvHint = findViewById(R.id.tvHint);
        tvHearts = findViewById(R.id.tvHearts);
        etFillBlank = findViewById(R.id.etFillBlank);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnNext = findViewById(R.id.btnNext);
        btnHint = findViewById(R.id.btnHint);
        btnListen = findViewById(R.id.btnListen);
        layoutMultiChoice = findViewById(R.id.layoutMultiChoice);
        layoutFillBlank = findViewById(R.id.layoutFillBlank);
        layoutMatch = findViewById(R.id.layoutMatch);
        layoutSentenceOrder = findViewById(R.id.layoutSentenceOrder);
        progressBar = findViewById(R.id.progressBar);
        progressBarLesson = findViewById(R.id.progressBarLesson);

        btnSubmit.setOnClickListener(v -> submitAnswer());
        btnNext.setOnClickListener(v -> nextExercise());
        btnHint.setOnClickListener(v -> showHint());
        btnListen.setOnClickListener(v -> playAudio());
    }

    /** Load current heart count to display in the header. */
    private void loadHearts() {
        apiService.getMyStats().enqueue(new Callback<ApiResponse<GamificationStats>>() {
            @Override public void onResponse(Call<ApiResponse<GamificationStats>> c, Response<ApiResponse<GamificationStats>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    currentHearts = r.body().getData().hearts;
                    runOnUiThread(() -> {
                        if (tvHearts != null) tvHearts.setText("❤️ " + currentHearts);
                    });
                } else {
                    // 6.4 FIX: nếu API lỗi (response không thành công) cũng phải
                    // thay text "❤️ …" để user không bị "stuck" với "…" suốt buổi.
                    runOnUiThread(() -> {
                        if (tvHearts != null && currentHearts < 0) tvHearts.setText("❤️ ?");
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<GamificationStats>> c, Throwable t) {
                // 6.4 FIX: khi network/timeout cũng phải reset placeholder
                runOnUiThread(() -> {
                    if (tvHearts != null && currentHearts < 0) tvHearts.setText("❤️ ?");
                });
            }
        });
    }

    private void startLesson() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.startLesson(lessonId).enqueue(new Callback<ApiResponse<AttemptData>>() {
            @Override
            public void onResponse(Call<ApiResponse<AttemptData>> call, Response<ApiResponse<AttemptData>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()
                        && response.body().getData() != null) {
                    attemptId = response.body().getData().attemptId;
                    loadExercises();
                } else {
                    // 6.4 FIX: nếu /lessons/{id}/start trả lỗi (404/401/...) thì
                    // hiển thị thông báo và đóng màn hình để user không bị
                    // "stuck" với attemptId=-1 không thể submit.
                    progressBar.setVisibility(View.GONE);
                    String msg = response.body() != null && response.body().getMessage() != null
                            ? response.body().getMessage()
                            : "Không thể bắt đầu bài học (mã " + response.code() + ")";
                    showStartLessonError(msg);
                }
            }
            @Override public void onFailure(Call<ApiResponse<AttemptData>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                showStartLessonError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    /**
     * 6.4 FIX: thay vì im lặng để user thấy bài học load xong nhưng submit không
     * làm gì, hiện dialog cho phép thử lại hoặc thoát.
     */
    private void showStartLessonError(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("Không bắt đầu được bài học")
                .setMessage(msg)
                .setPositiveButton("Thử lại", (d, w) -> startLesson())
                .setNegativeButton("Thoát", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void loadExercises() {
        progressBar.setVisibility(View.VISIBLE);
        apiService.getLessonExercises(lessonId).enqueue(new Callback<ApiResponse<LessonData>>() {
            @Override
            public void onResponse(Call<ApiResponse<LessonData>> call, Response<ApiResponse<LessonData>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    LessonData data = response.body().getData();
                    exercises.clear();
                    if (data.exercises != null) exercises.addAll(data.exercises);
                    runOnUiThread(() -> {
                        if (!exercises.isEmpty()) {
                            showExercise(0);
                        } else {
                            // BUG 3 FIX: response thành công nhưng exercises rỗng cũng
                            // phải báo cho user thay vì để màn hình trắng.
                            showLoadExercisesError("Bài học không có bài tập nào.");
                        }
                    });
                } else {
                    // BUG 3 FIX: response không thành công (404/500/...) phải hiện dialog
                    // thay vì để màn hình trắng không có nút gì.
                    String msg = response.body() != null && response.body().getMessage() != null
                            ? response.body().getMessage()
                            : "Không tải được bài tập (mã " + response.code() + ")";
                    showLoadExercisesError(msg);
                }
            }
            @Override public void onFailure(Call<ApiResponse<LessonData>> call, Throwable t) {
                // BUG 3 FIX: trước đây onFailure không làm gì cả → progressBar ẩn,
                // exercises rỗng, showExercise() không bao giờ được gọi → user nhìn
                // thấy màn hình trắng không có nút gì, không hiểu chuyện gì đang xảy ra
                // và không có cách retry. Bây giờ hiện dialog với "Thử lại" / "Thoát".
                progressBar.setVisibility(View.GONE);
                showLoadExercisesError("Lỗi kết nối: " + t.getMessage());
            }
        });
    }

    /**
     * BUG 3 FIX: dialog xử lý lỗi khi không tải được bài tập. Tương tự
     * showStartLessonError() nhưng cho /lessons/{id}/exercises.
     */
    private void showLoadExercisesError(String msg) {
        new AlertDialog.Builder(PracticeActivity.this)
                .setTitle("Không tải được bài tập")
                .setMessage(msg)
                .setPositiveButton("Thử lại", (d, w) -> loadExercises())
                .setNegativeButton("Thoát", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showExercise(int index) {
        if (index >= exercises.size()) { showCompletion(); return; }
        currentIndex = index;
        Exercise ex = exercises.get(index);

        // BUG 6 FIX: ghi lại thời điểm bắt đầu để tính elapsed time khi submit.
        exerciseStartMs = System.currentTimeMillis();

        hideAllLayouts();
        tvFeedback.setVisibility(View.GONE);
        btnNext.setVisibility(View.GONE);
        btnSubmit.setVisibility(View.VISIBLE);
        tvProgress.setText(String.format(Locale.getDefault(), "Bài tập %d/%d", index + 1, exercises.size()));

        // overall lesson progress
        if (progressBarLesson != null) {
            progressBarLesson.setMax(exercises.size());
            progressBarLesson.setProgress(index);
        }

        JsonObject prompt = parsePrompt(ex.promptJson);
        String question = prompt != null && prompt.has("question") ? prompt.get("question").getAsString() : "";
        if (question.isEmpty() && prompt != null && prompt.has("text")) question = prompt.get("text").getAsString();
        tvQuestion.setText(question);

        switch (ex.type != null ? ex.type : "") {
            case "FILL_BLANK":
            case "TRANSLATE":
                showFillBlank();
                break;
            case "LISTEN_SELECT":
            case "DICTATION":
                showListenMode(prompt);
                break;
            case "SENTENCE_ORDER":
                showSentenceOrder(prompt);
                break;
            case "MATCHING":
            case "MATCH":
                showMatching(prompt, ex.answerJson);
                break;
            default:
                if (prompt != null && prompt.has("options")) {
                    showMultiChoice(prompt);
                } else {
                    showFillBlank();
                }
        }
    }

    private JsonObject parsePrompt(Object obj) {
        if (obj == null) return null;
        try {
            JsonElement el = gson.toJsonTree(obj);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) { return null; }
    }

    private void showFillBlank() {
        layoutFillBlank.setVisibility(View.VISIBLE);
        etFillBlank.setText("");
        etFillBlank.requestFocus();
    }

    /**
     * U-additional FIX: hiển thị multi-choice với highlight đáp án đã chọn,
     * yêu cầu xác nhận bằng nút "KIỂM TRA" thay vì auto-submit.
     * - Tap option lần 1 → highlight option đó (đổi background green nhạt).
     * - Bấm "KIỂM TRA ✓" để submit.
     * - Có thể đổi đáp án bằng cách tap option khác trước khi submit.
     */
    private String selectedMultiChoice = null;

    private void showMultiChoice(JsonObject prompt) {
        layoutMultiChoice.setVisibility(View.VISIBLE);
        layoutMultiChoice.removeAllViews();
        selectedMultiChoice = null;
        if (prompt.has("options")) {
            JsonArray options = prompt.getAsJsonArray("options");
            for (int i = 0; i < options.size(); i++) {
                String option = options.get(i).getAsString();
                Button btn = new Button(this);
                btn.setText(option);
                btn.setTag(option);
                btn.setAllCaps(false);
                btn.setBackgroundTintList(getResources().getColorStateList(R.color.lingua_blue));
                btn.setTextColor(0xFFFFFFFF);
                int margin = (int) (4 * getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(margin, margin, margin, margin);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> {
                    if (answerLocked) return;
                    selectedMultiChoice = (String) v.getTag();
                    // Highlight option đã chọn (xanh lá), reset các option khác.
                    for (int k = 0; k < layoutMultiChoice.getChildCount(); k++) {
                        View child = layoutMultiChoice.getChildAt(k);
                        if (child instanceof Button) {
                            boolean isSelected = child == v;
                            child.setBackgroundTintList(getResources().getColorStateList(
                                    isSelected ? R.color.lingua_green : R.color.lingua_blue));
                        }
                    }
                });
                layoutMultiChoice.addView(btn);
            }
        }
        // U1 FIX: yêu cầu bấm "KIỂM TRA ✓" để xác nhận, không auto-submit.
        btnSubmit.setVisibility(View.VISIBLE);
    }

    private void showListenMode(JsonObject prompt) {
        layoutFillBlank.setVisibility(View.VISIBLE);
        tvQuestion.setText("🎧 Nghe và viết lại nội dung bạn nghe được:");
        btnListen.setVisibility(View.VISIBLE);
        etFillBlank.setText("");

        if (prompt != null && prompt.has("text") && tts != null) {
            String listenText = prompt.get("text").getAsString();
            tts.speak(listenText, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    /**
     * Renders the SENTENCE_ORDER exercise.
     * The user taps tokens from the bank (top row) to append them to the answer
     * row (bottom). Tapping a placed token sends it back to the bank.
     */
    private void showSentenceOrder(JsonObject prompt) {
        layoutSentenceOrder.setVisibility(View.VISIBLE);
        layoutSentenceOrder.removeAllViews();
        orderTokens.clear();
        orderAnswer.clear();

        if (prompt != null && prompt.has("tokens")) {
            JsonArray arr = prompt.getAsJsonArray("tokens");
            for (int i = 0; i < arr.size(); i++) orderTokens.add(arr.get(i).getAsString());
        } else if (prompt != null && prompt.has("words")) {
            JsonArray arr = prompt.getAsJsonArray("words");
            for (int i = 0; i < arr.size(); i++) orderTokens.add(arr.get(i).getAsString());
        }
        Collections.shuffle(orderTokens);

        // Answer row
        TextView label1 = new TextView(this);
        label1.setText("👇 Câu của bạn (nhấn để bỏ):");
        label1.setTextColor(0xFFAFAFAF);
        layoutSentenceOrder.addView(label1);

        // U10 FIX: dùng com.google.android.flexbox.FlexboxLayout nếu có; fallback
        // sang một custom WrapLayout tự xuống dòng để token không bị tràn màn hình
        // khi câu dài (6+ từ). Trước đây LinearLayout HORIZONTAL khiến các từ bị
        // đẩy ra ngoài viewport.
        layoutOrderAnswer = makeWrapContainer();
        layoutOrderAnswer.setMinimumHeight((int) (60 * getResources().getDisplayMetrics().density));
        layoutOrderAnswer.setBackgroundResource(R.drawable.edit_text_bg);
        layoutOrderAnswer.setPadding(8, 8, 8, 8);
        layoutSentenceOrder.addView(layoutOrderAnswer);

        TextView label2 = new TextView(this);
        label2.setText("📦 Bộ từ:");
        label2.setTextColor(0xFFAFAFAF);
        label2.setPadding(0, 16, 0, 4);
        layoutSentenceOrder.addView(label2);

        layoutOrderTokens = makeWrapContainer();
        layoutSentenceOrder.addView(layoutOrderTokens);

        rebuildOrderTokens();
    }

    /**
     * U10 FIX: trả về một LinearLayout đặc biệt có override onMeasure để wrap
     * các child sang dòng mới nếu không đủ chỗ — giống FlexboxLayout nhưng
     * không yêu cầu thêm dependency.
     */
    private LinearLayout makeWrapContainer() {
        return new com.lingua.app.views.WrapLayout(this);
    }

    private void rebuildOrderTokens() {
        if (layoutOrderTokens == null || layoutOrderAnswer == null) return;
        layoutOrderTokens.removeAllViews();
        layoutOrderAnswer.removeAllViews();
        for (String t : orderTokens) layoutOrderTokens.addView(makeTokenChip(t, true));
        for (String t : orderAnswer) layoutOrderAnswer.addView(makeTokenChip(t, false));
    }

    private Button makeTokenChip(String token, boolean fromBank) {
        Button b = new Button(this);
        b.setText(token);
        b.setAllCaps(false);
        b.setTextSize(14);
        int margin = (int) (4 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(margin, margin, margin, margin);
        b.setLayoutParams(lp);
        b.setBackgroundTintList(getResources().getColorStateList(
                fromBank ? R.color.lingua_blue : R.color.lingua_green));
        b.setTextColor(0xFFFFFFFF);
        b.setOnClickListener(v -> {
            if (fromBank) {
                orderTokens.remove(token);
                orderAnswer.add(token);
            } else {
                orderAnswer.remove(token);
                orderTokens.add(token);
            }
            rebuildOrderTokens();
        });
        return b;
    }

    /**
     * Renders MATCHING: two columns of tappable items. Tap one on the left then
     * one on the right to create a pair. Existing pairs can be removed by
     * tapping the same item again.
     */
    private void showMatching(JsonObject prompt, Object answerJson) {
        layoutMatch.setVisibility(View.VISIBLE);
        layoutMatch.removeAllViews();
        matchSelectedLeft = null;
        matchPairsCorrect.clear();
        matchUserPairs.clear();

        // pairs source — try several common shapes
        List<String[]> pairs = new ArrayList<>();
        if (prompt != null && prompt.has("pairs")) {
            JsonArray arr = prompt.getAsJsonArray("pairs");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject obj = arr.get(i).getAsJsonObject();
                String l = obj.has("left") ? obj.get("left").getAsString() :
                        (obj.has("a") ? obj.get("a").getAsString() : "");
                String r = obj.has("right") ? obj.get("right").getAsString() :
                        (obj.has("b") ? obj.get("b").getAsString() : "");
                pairs.add(new String[]{l, r});
            }
        }
        if (pairs.isEmpty() && answerJson != null) {
            try {
                JsonElement el = gson.toJsonTree(answerJson);
                if (el.isJsonObject() && el.getAsJsonObject().has("pairs")) {
                    JsonArray arr = el.getAsJsonObject().getAsJsonArray("pairs");
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject obj = arr.get(i).getAsJsonObject();
                        String l = obj.has("left") ? obj.get("left").getAsString() : "";
                        String r = obj.has("right") ? obj.get("right").getAsString() : "";
                        pairs.add(new String[]{l, r});
                    }
                }
            } catch (Exception ignore) {}
        }
        for (String[] p : pairs) matchPairsCorrect.put(p[0], p[1]);

        List<String> lefts = new ArrayList<>();
        List<String> rights = new ArrayList<>();
        for (String[] p : pairs) { lefts.add(p[0]); rights.add(p[1]); }
        Collections.shuffle(lefts);
        Collections.shuffle(rights);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        layoutMatchLeft = new LinearLayout(this);
        layoutMatchLeft.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams colParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        layoutMatchLeft.setLayoutParams(colParams);

        layoutMatchRight = new LinearLayout(this);
        layoutMatchRight.setOrientation(LinearLayout.VERTICAL);
        layoutMatchRight.setLayoutParams(colParams);

        for (String l : lefts) layoutMatchLeft.addView(makeMatchItem(l, true));
        for (String r : rights) layoutMatchRight.addView(makeMatchItem(r, false));
        container.addView(layoutMatchLeft);
        container.addView(layoutMatchRight);
        layoutMatch.addView(container);
    }

    private Button makeMatchItem(String text, boolean isLeft) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTag(new Object[]{text, isLeft});
        int margin = (int) (4 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(margin, margin, margin, margin);
        b.setLayoutParams(lp);
        b.setBackgroundTintList(getResources().getColorStateList(R.color.lingua_blue));
        b.setTextColor(0xFFFFFFFF);
        b.setOnClickListener(v -> onMatchClicked(text, isLeft, b));
        return b;
    }

    private void onMatchClicked(String text, boolean isLeft, Button btn) {
        if (isLeft) {
            matchSelectedLeft = text;
            // visual feedback
            for (int i = 0; i < layoutMatchLeft.getChildCount(); i++) {
                layoutMatchLeft.getChildAt(i).setAlpha(0.6f);
            }
            btn.setAlpha(1f);
        } else {
            if (matchSelectedLeft == null) {
                Toast.makeText(this, "Chọn 1 mục bên trái trước", Toast.LENGTH_SHORT).show();
                return;
            }
            matchUserPairs.put(matchSelectedLeft, text);
            // mark both as paired
            tagPaired(layoutMatchLeft, matchSelectedLeft);
            tagPaired(layoutMatchRight, text);
            matchSelectedLeft = null;
            for (int i = 0; i < layoutMatchLeft.getChildCount(); i++) {
                layoutMatchLeft.getChildAt(i).setAlpha(
                        ((Object[]) layoutMatchLeft.getChildAt(i).getTag())[0].equals(text) ? 1f : 1f);
            }
        }
    }

    private void tagPaired(LinearLayout col, String text) {
        for (int i = 0; i < col.getChildCount(); i++) {
            View v = col.getChildAt(i);
            Object[] tag = (Object[]) v.getTag();
            if (tag != null && text.equals(tag[0])) {
                ((Button) v).setBackgroundTintList(getResources().getColorStateList(R.color.lingua_green));
            }
        }
    }

    private void submitAnswer() {
        Exercise ex = exercises.get(currentIndex);
        String type = ex.type != null ? ex.type : "";
        if ("SENTENCE_ORDER".equals(type)) {
            String joined = android.text.TextUtils.join(" ", orderAnswer);
            submitSelectedAnswer(joined);
            return;
        }
        if ("MATCHING".equals(type) || "MATCH".equals(type)) {
            // serialize pairs as "left=right;left=right"
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : matchUserPairs.entrySet()) {
                if (sb.length() > 0) sb.append(";");
                sb.append(e.getKey()).append("=").append(e.getValue());
            }
            submitSelectedAnswer(sb.toString());
            return;
        }
        // U1 FIX: multi-choice (default branch) — yêu cầu user đã chọn 1 option.
        if (layoutMultiChoice != null && layoutMultiChoice.getVisibility() == View.VISIBLE) {
            if (selectedMultiChoice == null || selectedMultiChoice.isEmpty()) {
                Toast.makeText(this, "Hãy chọn một đáp án", Toast.LENGTH_SHORT).show();
                return;
            }
            submitSelectedAnswer(selectedMultiChoice);
            return;
        }
        String answer = etFillBlank.getText().toString().trim();
        if (answer.isEmpty()) {
            Toast.makeText(this, "Hãy nhập câu trả lời của bạn", Toast.LENGTH_SHORT).show();
            return;
        }
        submitSelectedAnswer(answer);
    }

    private void submitSelectedAnswer(String answer) {
        if (attemptId == -1) return;
        // U1 FIX: ignore double-submit. Was: rapid double-tap on a multi-choice
        // button could fire submitAnswer twice, costing 2 hearts on a wrong tap.
        if (answerLocked) return;
        answerLocked = true;

        Exercise ex = exercises.get(currentIndex);

        // U1 FIX: also visually disable the multi-choice buttons so they cannot
        // be tapped a second time before the feedback arrives.
        if (layoutMultiChoice != null && layoutMultiChoice.getVisibility() == View.VISIBLE) {
            for (int i = 0; i < layoutMultiChoice.getChildCount(); i++) {
                layoutMultiChoice.getChildAt(i).setEnabled(false);
            }
        }

        // BUG 6 FIX: tính elapsed time thực tế thay vì hardcode 5000ms. Nếu vì
        // lý do gì đó exerciseStartMs chưa được khởi tạo (=0), fallback về 0
        // để backend biết là không đo được, hơn là gửi giá trị giả 5000ms.
        long elapsed = exerciseStartMs > 0 ? (System.currentTimeMillis() - exerciseStartMs) : 0L;

        Map<String, Object> body = new HashMap<>();
        body.put("exerciseId", ex.id);
        body.put("userAnswer", answer);
        body.put("timeMs", elapsed);

        apiService.submitAnswer(attemptId, body).enqueue(new Callback<ApiResponse<AnswerResult>>() {
            @Override
            public void onResponse(Call<ApiResponse<AnswerResult>> call, Response<ApiResponse<AnswerResult>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AnswerResult result = response.body().getData();
                    boolean correct = result != null && result.isCorrect;
                    if (correct) score++;
                    else loseHeart();

                    runOnUiThread(() -> {
                        showFeedback(correct, result);
                        playFeedbackEffect(correct);
                        btnSubmit.setVisibility(View.GONE);
                        btnNext.setVisibility(View.VISIBLE);
                        tvScore.setText(String.format(Locale.getDefault(), "Score: %d/%d", score, currentIndex + 1));
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<AnswerResult>> call, Throwable t) {
                // Allow user to retry on network failure.
                answerLocked = false;
            }
        });
    }

    /** Calls /api/gamification/hearts/lose on a wrong answer. */
    private void loseHeart() {
        apiService.loseHeart().enqueue(new Callback<ApiResponse<HeartsData>>() {
            @Override public void onResponse(Call<ApiResponse<HeartsData>> c, Response<ApiResponse<HeartsData>> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null) {
                    currentHearts = r.body().getData().hearts;
                    runOnUiThread(() -> {
                        if (tvHearts != null) tvHearts.setText("❤️ " + currentHearts);
                        if (currentHearts <= 0) {
                            // BUG 5 FIX: trước đây chỉ Toast, không block gì → user
                            // vẫn bấm Next và học tiếp, XP và quest vẫn tính bình thường,
                            // gamification "tim" mất ý nghĩa. Bây giờ hiện dialog block
                            // và không cho tiếp tục.
                            showOutOfHeartsDialog();
                        }
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<HeartsData>> c, Throwable t) {}
        });
    }

    /**
     * BUG 5 FIX: hiển thị dialog block khi user hết tim. Dialog không thể
     * dismiss bằng cách bấm ra ngoài; chỉ có thể về trang chủ (kết thúc
     * lesson, không tính XP/quest cho phần còn lại).
     */
    private void showOutOfHeartsDialog() {
        new AlertDialog.Builder(PracticeActivity.this)
                .setTitle("💔 Hết tim rồi!")
                .setMessage("Bạn cần chờ tim hồi phục hoặc mua Streak Freeze để tiếp tục.")
                .setPositiveButton("Về trang chủ", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void showFeedback(boolean correct, AnswerResult result) {
        tvFeedback.setVisibility(View.VISIBLE);
        if (correct) {
            tvFeedback.setText("✅ Chính xác!");
            tvFeedback.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            tvFeedback.setBackgroundColor(getResources().getColor(R.color.correct_green));
        } else {
            String correctAns = result != null && result.correctAnswer != null
                ? result.correctAnswer.toString() : "?";
            tvFeedback.setText("❌ Sai rồi! Đáp án: " + correctAns);
            tvFeedback.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            tvFeedback.setBackgroundColor(getResources().getColor(R.color.wrong_red));
        }
    }

    /**
     * Plays a small visual/haptic effect: scale-up "confetti-like" bounce when
     * correct, or a shake for wrong answers.
     *
     * U16 FIX: respect SettingsActivity's sound + haptic switches. Previously
     * the haptic always fired, regardless of the user's preference.
     */
    private void playFeedbackEffect(boolean correct) {
        try {
            if (correct) {
                tvFeedback.startAnimation(AnimationUtils.loadAnimation(this, R.anim.bounce_celebrate));
                if (prefSound) {
                    try {
                        ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60);
                        tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
                    } catch (Exception ignore) {}
                }
            } else {
                tvFeedback.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
                if (prefHaptic) {
                    tvFeedback.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                }
                if (prefSound) {
                    try {
                        ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60);
                        tone.startTone(ToneGenerator.TONE_PROP_NACK, 200);
                    } catch (Exception ignore) {}
                }
            }
        } catch (Exception ignored) {}
    }

    private void nextExercise() {
        answerLocked = false; // U1 FIX: re-enable submission for the next exercise.
        showExercise(currentIndex + 1);
    }

    private void showHint() {
        Exercise ex = exercises.get(currentIndex);
        if (ex.hintJson != null) {
            tvHint.setText("💡 " + ex.hintJson.toString());
            tvHint.setVisibility(View.VISIBLE);
        }
    }

    /**
     * BUG 7.1 FIX: trước đây mỗi click "🔊 Nghe" tạo một MediaPlayer mới
     * nhưng không bao giờ release() → memory/resource leak, dễ crash trên
     * thiết bị RAM thấp. Đồng thời tên parameter lambda `MediaPlayer` shadow
     * tên class, gây nhầm lẫn cho người đọc code.
     *
     * Bản sửa:
     *   - Release MediaPlayer cũ (nếu có) trước khi tạo cái mới.
     *   - Đăng ký onCompletionListener / onErrorListener để tự release sau
     *     khi phát xong hoặc khi xảy ra lỗi.
     *   - Lưu instance vào field `currentMediaPlayer` để release trong
     *     onDestroy() khi user thoát màn hình giữa chừng.
     *   - Đổi tên parameter lambda từ `MediaPlayer` → `player` để tránh
     *     shadow tên class.
     */
    private void playAudio() {
        Exercise ex = exercises.get(currentIndex);
        if (ex.audioUrl == null || ex.audioUrl.isEmpty()) return;

        // Release the previous player first (user may have tapped multiple times).
        releaseCurrentMediaPlayer();

        android.media.MediaPlayer mp = new android.media.MediaPlayer();
        currentMediaPlayer = mp;
        try {
            mp.setDataSource(ex.audioUrl);
            mp.setOnPreparedListener(player -> player.start());
            mp.setOnCompletionListener(player -> {
                player.release();
                if (currentMediaPlayer == player) currentMediaPlayer = null;
            });
            mp.setOnErrorListener((player, what, extra) -> {
                player.release();
                if (currentMediaPlayer == player) currentMediaPlayer = null;
                return true;
            });
            mp.prepareAsync();
        } catch (Exception e) {
            e.printStackTrace();
            // setDataSource / prepareAsync có thể ném exception trước khi
            // các listener được kích hoạt → release thủ công để không leak.
            try { mp.release(); } catch (Exception ignore) {}
            if (currentMediaPlayer == mp) currentMediaPlayer = null;
        }
    }

    /** BUG 7.1 FIX: helper để release MediaPlayer hiện tại một cách an toàn. */
    private void releaseCurrentMediaPlayer() {
        if (currentMediaPlayer != null) {
            try {
                currentMediaPlayer.release();
            } catch (Exception ignore) {}
            currentMediaPlayer = null;
        }
    }

    /**
     * Called after the last exercise. Calls completeLesson, fires the
     * LESSON_COMPLETE quest event, and shows the result screen with XP and
     * accuracy.
     */
    private void showCompletion() {
        if (attemptId == -1) return;
        progressBar.setVisibility(View.VISIBLE);
        apiService.completeLesson(attemptId).enqueue(new Callback<ApiResponse<LessonResult>>() {
            @Override
            public void onResponse(Call<ApiResponse<LessonResult>> call, Response<ApiResponse<LessonResult>> response) {
                progressBar.setVisibility(View.GONE);
                LessonResult result = response.body() != null ? response.body().getData() : null;
                int xp = result != null ? result.xpEarned : 0;
                int sp = result != null ? result.scorePercent : (score * 100 / Math.max(1, exercises.size()));
                triggerLessonCompleteQuest();
                openResultScreen(xp, sp);
            }
            @Override public void onFailure(Call<ApiResponse<LessonResult>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                openResultScreen(0, score * 100 / Math.max(1, exercises.size()));
            }
        });
    }

    /** BUG B4 FIX: backend đọc `event` / `value` (chứ không phải `type` / `amount`).
     *  (BUG #19 FIX: comment tiếng Pháp → tiếng Việt.) */
    private void triggerLessonCompleteQuest() {
        Map<String, Object> body = new HashMap<>();
        body.put("event", "LESSON_COMPLETE");
        body.put("value", 1);
        body.put("lessonId", lessonId);
        apiService.triggerQuestEvent(body).enqueue(new Callback<ApiResponse<Object>>() {
            @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {}
            @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {}
        });
    }

    private void openResultScreen(int xpEarned, int scorePercent) {
        Intent i = new Intent(this, LessonResultActivity.class);
        i.putExtra("xpEarned", xpEarned);
        i.putExtra("scorePercent", scorePercent);
        i.putExtra("correct", score);
        i.putExtra("total", exercises.size());
        i.putExtra("lessonId", lessonId);
        startActivity(i);
        finish();
    }

    private void hideAllLayouts() {
        layoutMultiChoice.setVisibility(View.GONE);
        layoutFillBlank.setVisibility(View.GONE);
        layoutMatch.setVisibility(View.GONE);
        layoutSentenceOrder.setVisibility(View.GONE);
        btnListen.setVisibility(View.GONE);
        tvHint.setVisibility(View.GONE);
    }

    @Override
    public void onInit(int status) {}

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        // BUG 7.1 FIX: release MediaPlayer nếu user thoát màn hình khi audio
        // vẫn đang chuẩn bị / phát → tránh leak native AudioTrack handle.
        releaseCurrentMediaPlayer();
        super.onDestroy();
    }
}
