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
    private Button btnSubmit, btnNext, btnHint, btnListen, btnSkip; // BUG U6: btnSkip
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

    // UX-3 FIX: track xem user đã bấm 🔊 nghe ít nhất một lần cho bài
    // LISTEN_SELECT / DICTATION hiện tại chưa. Khi chưa nghe → disable btnSubmit
    // để user không bấm KIỂM TRA luôn với câu trống.
    private boolean hasListenedThisExercise = false;

    // TD-4 FIX: track xem TextToSpeech engine đã init thành công chưa, để
    // disable btnListen nếu init fail (thiết bị không có TTS engine hoặc
    // không hỗ trợ ngôn ngữ).
    private boolean ttsReady = false;

    // R4-H1 FIX: keys cho onSaveInstanceState / onRestoreInstanceState để bảo
    // toàn tiến độ bài học khi user xoay máy. Trước đây toàn bộ state
    // (currentIndex, score, attemptId, exercises[], exerciseStartMs,
    // orderAnswer, matchUserPairs) bị mất → app gọi startLesson() lần thứ hai
    // → backend tạo attempt MỚI → user mất XP/streak của attempt cũ.
    private static final String STATE_CURRENT_INDEX     = "r4_current_index";
    private static final String STATE_SCORE             = "r4_score";
    private static final String STATE_ATTEMPT_ID        = "r4_attempt_id";
    private static final String STATE_LESSON_ID         = "r4_lesson_id";
    private static final String STATE_EXERCISES_JSON    = "r4_exercises_json";
    private static final String STATE_CURRENT_HEARTS    = "r4_current_hearts";
    private static final String STATE_EXERCISE_START_MS = "r4_exercise_start_ms";
    private static final String STATE_ORDER_ANSWER      = "r4_order_answer";
    private static final String STATE_MATCH_USER_PAIRS_K= "r4_match_pairs_k";
    private static final String STATE_MATCH_USER_PAIRS_V= "r4_match_pairs_v";
    private static final String STATE_HAS_LISTENED      = "r4_has_listened";
    private static final String STATE_ANSWER_LOCKED     = "r4_answer_locked";

    // R4-H1 FIX: cờ để showExercise() biết đây là restore (không reset start
    // timer / has-listened flag) hay là exercise mới.
    private boolean restoredFromState = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);

        apiService = ApiClient.getService(this);
        tts = new TextToSpeech(this, this);

        // R4-C1 FIX: dùng sentinel -1L thay vì default 1 — tránh start lesson
        // #1 ngoài ý muốn khi Activity được start mà thiếu extra "lessonId"
        // (deep-link, notification, lỗi navigation). Đồng bộ với LessonActivity
        // (R3-M6).
        lessonId = getIntent().getLongExtra("lessonId", -1L);

        // U16 FIX: load sound/haptic prefs (keys defined in SettingsActivity).
        SharedPreferences prefs = getSharedPreferences("LinguaPrefs", MODE_PRIVATE);
        prefSound = prefs.getBoolean("feedback_sound", true);
        prefHaptic = prefs.getBoolean("feedback_haptic", true);

        initViews();
        loadHearts();

        // R4-H1 FIX: nếu có savedInstanceState, restore state thay vì gọi
        // startLesson() lần nữa (sẽ tạo attempt mới trên backend).
        if (savedInstanceState != null && restoreStateFromBundle(savedInstanceState)) {
            restoredFromState = true;
            // Không gọi startLesson() — attemptId đã có sẵn, exercises đã restore.
        } else {
            // R4-C1 FIX: validate lessonId trước khi start — không cho start
            // với id <= 0.
            if (lessonId <= 0) {
                new AlertDialog.Builder(this)
                        .setTitle("Không xác định được bài học")
                        .setMessage("Thiếu thông tin bài học. Vui lòng quay lại danh sách bài học.")
                        .setPositiveButton("Đóng", (d, w) -> finish())
                        .setCancelable(false)
                        .show();
                return;
            }
            startLesson();
        }
    }

    /**
     * R4-H1 FIX: save tiến độ bài học trước khi configuration change
     * (rotation, language change, dark-mode toggle, low memory) hoặc process
     * death. Lưu attemptId nên có thể reload exercises từ backend bằng cách
     * gọi loadExercises() — nhưng để an toàn ta serialize luôn cả exercises
     * list dưới dạng JSON (gson) để khôi phục instant kể cả khi offline.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_INDEX, currentIndex);
        outState.putInt(STATE_SCORE, score);
        outState.putLong(STATE_ATTEMPT_ID, attemptId);
        outState.putLong(STATE_LESSON_ID, lessonId);
        outState.putInt(STATE_CURRENT_HEARTS, currentHearts);
        outState.putLong(STATE_EXERCISE_START_MS, exerciseStartMs);
        outState.putBoolean(STATE_HAS_LISTENED, hasListenedThisExercise);
        outState.putBoolean(STATE_ANSWER_LOCKED, answerLocked);
        try {
            outState.putString(STATE_EXERCISES_JSON, gson.toJson(exercises));
        } catch (Exception ignore) {}
        outState.putStringArrayList(STATE_ORDER_ANSWER, new ArrayList<>(orderAnswer));
        ArrayList<String> mk = new ArrayList<>(matchUserPairs.keySet());
        ArrayList<String> mv = new ArrayList<>();
        for (String k : mk) mv.add(matchUserPairs.get(k));
        outState.putStringArrayList(STATE_MATCH_USER_PAIRS_K, mk);
        outState.putStringArrayList(STATE_MATCH_USER_PAIRS_V, mv);
    }

    /** R4-H1 FIX: restore state lưu bởi onSaveInstanceState. */
    private boolean restoreStateFromBundle(Bundle s) {
        try {
            long savedLessonId = s.getLong(STATE_LESSON_ID, -1L);
            if (savedLessonId <= 0) return false;
            // Re-validate lessonId match — nếu khác (impossible nhưng phòng xa),
            // ta tin lessonId trong intent hiện tại.
            if (lessonId <= 0) lessonId = savedLessonId;
            attemptId = s.getLong(STATE_ATTEMPT_ID, -1L);
            currentIndex = s.getInt(STATE_CURRENT_INDEX, 0);
            score = s.getInt(STATE_SCORE, 0);
            currentHearts = s.getInt(STATE_CURRENT_HEARTS, -1);
            exerciseStartMs = s.getLong(STATE_EXERCISE_START_MS, 0L);
            hasListenedThisExercise = s.getBoolean(STATE_HAS_LISTENED, false);
            answerLocked = s.getBoolean(STATE_ANSWER_LOCKED, false);

            String exJson = s.getString(STATE_EXERCISES_JSON);
            exercises.clear();
            if (exJson != null && !exJson.isEmpty()) {
                Exercise[] arr = gson.fromJson(exJson, Exercise[].class);
                if (arr != null) {
                    for (Exercise e : arr) exercises.add(e);
                }
            }
            orderAnswer.clear();
            ArrayList<String> oa = s.getStringArrayList(STATE_ORDER_ANSWER);
            if (oa != null) orderAnswer.addAll(oa);
            matchUserPairs.clear();
            ArrayList<String> mk = s.getStringArrayList(STATE_MATCH_USER_PAIRS_K);
            ArrayList<String> mv = s.getStringArrayList(STATE_MATCH_USER_PAIRS_V);
            if (mk != null && mv != null && mk.size() == mv.size()) {
                for (int i = 0; i < mk.size(); i++) matchUserPairs.put(mk.get(i), mv.get(i));
            }

            // Restore UI sau khi views sẵn sàng.
            runOnUiThread(() -> {
                if (!exercises.isEmpty() && currentIndex < exercises.size()) {
                    // Hiển thị lại bài tập hiện tại — không reset start time / listen-flag
                    // (đã restore từ bundle).
                    showExercise(currentIndex);
                } else if (attemptId > 0 && exercises.isEmpty()) {
                    // Edge case: chỉ còn attemptId, không có exercises (bundle bị strip)
                    // → fetch lại exercises bằng attemptId / lessonId.
                    loadExercises();
                }
            });
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * BUG U7 FIX : recharger les préférences sound/haptic à chaque retour
     * dans l'activité. Sinon, si l'utilisateur ouvre Settings depuis Practice
     * et toggle off "feedback_sound", la valeur en mémoire reste à `true`
     * tant que Practice n'a pas été détruite (singleTask launchMode).
     */
    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("LinguaPrefs", MODE_PRIVATE);
        prefSound  = prefs.getBoolean("feedback_sound", true);
        prefHaptic = prefs.getBoolean("feedback_haptic", true);
    }

    /**
     * U2 FIX: confirm before leaving the lesson so the user does not lose
     * progress by accidentally tapping back.
     *
     * R5-024 FIX: si l'utilisateur confirme la sortie ET qu'un attempt est
     * en cours côté backend, on appelle POST /lessons/attempts/{id}/abandon
     * (fire-and-forget) pour fermer proprement l'attempt → status = ABANDONED.
     * Cela évite que les attempts restent en IN_PROGRESS éternellement et
     * pollue les analytics / le streak future. Pas de pénalité XP.
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
                .setPositiveButton("Thoát", (d, w) -> {
                    abandonAttemptIfNeeded();
                    super.onBackPressed();
                })
                .setNegativeButton("Tiếp tục học", null)
                .show();
    }

    /**
     * R5-024: fire-and-forget POST /lessons/attempts/{id}/abandon.
     * On ignore le résultat (le serveur est idempotent et l'utilisateur
     * a déjà quitté l'écran — pas besoin de feedback UI).
     */
    private void abandonAttemptIfNeeded() {
        if (attemptId <= 0) return;
        try {
            apiService.abandonLesson(attemptId).enqueue(
                new retrofit2.Callback<com.lingua.app.models.ApiResponse<java.util.Map<String, Object>>>() {
                    @Override public void onResponse(
                            retrofit2.Call<com.lingua.app.models.ApiResponse<java.util.Map<String, Object>>> call,
                            retrofit2.Response<com.lingua.app.models.ApiResponse<java.util.Map<String, Object>>> response) {
                        // no-op : on a juste besoin du fire-and-forget.
                    }
                    @Override public void onFailure(
                            retrofit2.Call<com.lingua.app.models.ApiResponse<java.util.Map<String, Object>>> call,
                            Throwable t) {
                        // Network error : le serveur nettoiera via cron sur les
                        // attempts IN_PROGRESS trop anciens (TODO future). Pas critique.
                    }
                }
            );
        } catch (Exception ignored) {
            // Ne jamais bloquer la sortie de l'écran sur cette opération.
        }
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
        btnSkip = findViewById(R.id.btnSkip); // BUG U6
        layoutMultiChoice = findViewById(R.id.layoutMultiChoice);
        layoutFillBlank = findViewById(R.id.layoutFillBlank);
        layoutMatch = findViewById(R.id.layoutMatch);
        layoutSentenceOrder = findViewById(R.id.layoutSentenceOrder);
        progressBar = findViewById(R.id.progressBar);
        progressBarLesson = findViewById(R.id.progressBarLesson);

        btnSubmit.setOnClickListener(v -> submitAnswer());
        btnNext.setOnClickListener(v -> nextExercise());
        btnHint.setOnClickListener(v -> showHint());
        btnListen.setOnClickListener(v -> {
            // UX-3 FIX: đánh dấu user đã nghe ít nhất 1 lần, và enable btnSubmit
            // cho các bài LISTEN_SELECT / DICTATION.
            hasListenedThisExercise = true;
            btnSubmit.setEnabled(true);
            btnSubmit.setAlpha(1.0f);
            playAudio();
        });
        // BUG U6 FIX : « Tôi chưa biết » → soumet une réponse vide (comptée fausse,
        // l'app perd 1 ❤️) puis affiche la bonne réponse via le mécanisme normal.
        if (btnSkip != null) {
            btnSkip.setOnClickListener(v -> skipCurrentExercise());
        }
    }

    /** BUG U6 — soumettre une réponse vide comme « je ne sais pas ». */
    private void skipCurrentExercise() {
        if (answerLocked) return;
        if (exercises.isEmpty() || currentIndex >= exercises.size()) return;
        new AlertDialog.Builder(this)
                .setTitle("Bỏ qua câu này?")
                .setMessage("Bạn sẽ thấy đáp án và mất 1 tim ❤️. Tiếp tục?")
                .setPositiveButton("Bỏ qua", (d, w) -> submitSelectedAnswer(""))
                .setNegativeButton("Huỷ", null)
                .show();
    }

    /** Load current heart count to display in the header. */
    private void loadHearts() {
        apiService.getMyStats().enqueue(new Callback<ApiResponse<GamificationStats>>() {
            @Override public void onResponse(Call<ApiResponse<GamificationStats>> c, Response<ApiResponse<GamificationStats>> r) {
                // BUG-015 FIX: kiểm tra isSuccess() của body — backend có thể trả
                // 200 OK nhưng {success:false, data:{...}} (loại phản hồi không đáng
                // tin cậy). Trước đây code lấy data bất chấp success=false → hiển
                // thị số tim đáng ngờ cho user.
                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()
                        && r.body().getData() != null) {
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
        btnSubmit.setEnabled(true);
        btnSubmit.setAlpha(1.0f);
        if (btnSkip != null) btnSkip.setVisibility(View.VISIBLE); // BUG U6

        // R4-M6 FIX: enable/disable btnHint dựa vào việc bài tập có hint hay
        // không. Trước đây btnHint luôn VISIBLE → user bấm việc không xảy ra
        // → confusion. Giờ disable + giảm alpha hoặc ẩn hẳn nếu không có hint.
        String hintTextPreview = ex.hintJson != null ? extractHintText(ex.hintJson) : null;
        boolean hasHint = hintTextPreview != null && !hintTextPreview.isEmpty();
        if (btnHint != null) {
            btnHint.setEnabled(hasHint);
            btnHint.setAlpha(hasHint ? 1.0f : 0.4f);
            btnHint.setVisibility(hasHint ? View.VISIBLE : View.GONE);
        }

        // UX-3 FIX: reset listen-flag mỗi khi chuyển bài mới.
        // R4-H1: chỉ reset nếu không phải đang restore từ savedInstanceState.
        if (!restoredFromState) {
            hasListenedThisExercise = false;
        } else {
            restoredFromState = false;
        }
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

        // UX-3 FIX: disable btnSubmit cho đến khi user bấm 🔊 nghe ít nhất 1 lần.
        // Tránh user bấm KIỂM TRA luôn với câu trống → trả lời sai oan.
        btnSubmit.setEnabled(false);
        btnSubmit.setAlpha(0.5f);

        if (prompt != null && prompt.has("text") && tts != null && ttsReady) {
            String listenText = prompt.get("text").getAsString();
            // BUG-005 FIX: setLanguage() theo target_language trước khi speak.
            // Trước đây TTS dùng locale mặc định của thiết bị (thường vi-VN cho
            // user VN) → đọc text Nhật/Hàn/Trung bằng giọng tiếng Việt → âm
            // thanh nhảm nhí. AIRoleplayActivity đã set language đúng mạch này
            // (xem speakAI()) — đồng bộ lại cho Practice.
            String targetLang = getSharedPreferences("LinguaPrefs", MODE_PRIVATE)
                    .getString(OnboardingActivity.KEY_TARGET_LANG, "ja");
            Locale ttsLocale;
            switch (targetLang) {
                case "en": ttsLocale = Locale.ENGLISH; break;
                case "zh": ttsLocale = Locale.CHINESE; break;
                case "ko": ttsLocale = Locale.KOREAN; break;
                default:   ttsLocale = Locale.JAPANESE;
            }
            try { tts.setLanguage(ttsLocale); } catch (Exception ignore) {}
            tts.speak(listenText, TextToSpeech.QUEUE_FLUSH, null, null);
            // UX-3 FIX: TTS đã tự phát một lần → coi như user đã nghe.
            hasListenedThisExercise = true;
            btnSubmit.setEnabled(true);
            btnSubmit.setAlpha(1.0f);
        } else if (!ttsReady && tts == null) {
            // TD-4: nếu TTS không sẵn sàng, vẫn cho phép user bấm 🔊 để dùng
            // audio URL (playAudio) — listener của btnListen sẽ enable btnSubmit.
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
        // R5-015 FIX: guard contre le double-tap dès la PREMIERE ligne. Avant,
        // le flag answerLocked n'était positionné que dans submitSelectedAnswer()
        // (donc après ~30ms de validation/Toast/build de payload). Si l'utilisateur
        // double-tappait dans cet intervalle, on pouvait entrer 2 fois dans
        // submitAnswer() → 2 appels submitAnswer côté serveur → 2 hearts perdus.
        if (answerLocked) return;

        Exercise ex = exercises.get(currentIndex);
        String type = ex.type != null ? ex.type : "";
        if ("SENTENCE_ORDER".equals(type)) {
            // LF-1 FIX: chặn submit khi user chưa kéo từ nào vào ô trả lời.
            // Trước đây chuỗi rỗng bị gửi lên backend → đánh sai → mất tim.
            if (orderAnswer.isEmpty()) {
                Toast.makeText(this, "Hãy sắp xếp ít nhất một từ vào câu trả lời",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            String joined = android.text.TextUtils.join(" ", orderAnswer);
            submitSelectedAnswer(joined);
            return;
        }
        if ("MATCHING".equals(type) || "MATCH".equals(type)) {
            // LF-2 FIX: yêu cầu user đã ghép đủ số cặp trước khi cho submit.
            // Trước đây user chỉ cần ghép 1/4 cặp rồi bấm KIỂM TRA → backend
            // chấm sai → mất tim oan.
            if (!matchPairsCorrect.isEmpty()
                    && matchUserPairs.size() < matchPairsCorrect.size()) {
                Toast.makeText(this,
                        "Hãy ghép tất cả " + matchPairsCorrect.size() + " cặp",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            if (matchUserPairs.isEmpty()) {
                Toast.makeText(this, "Hãy ghép các cặp trước khi kiểm tra",
                        Toast.LENGTH_SHORT).show();
                return;
            }
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
        // UX-3 FIX: với LISTEN_SELECT / DICTATION (dùng layoutFillBlank), yêu cầu
        // user phải bấm "Nghe" ít nhất một lần trước khi cho phép submit. Nếu
        // không user dễ bấm KIỂM TRA luôn với câu trống → trả lời sai mà không
        // biết câu trả lời đúng là gì.
        if ("LISTEN_SELECT".equals(type) || "DICTATION".equals(type)) {
            if (!hasListenedThisExercise) {
                Toast.makeText(this, "Hãy bấm 🔊 nghe trước khi trả lời",
                        Toast.LENGTH_SHORT).show();
                return;
            }
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
                        if (btnSkip != null) btnSkip.setVisibility(View.GONE); // BUG U6
                        btnNext.setVisibility(View.VISIBLE);
                        // BUG-007 FIX: i18n — toàn bộ app dùng tiếng Việt nhưng
                        // riêng dòng score này lại "Score: x/y". Đổi sang "🎯 Điểm: x/y".
                        // BUG-028: dùng Locale.US cho số để tránh hiển thị số Ả Rập
                        // khi user thiết bị locale ar/fa.
                        tvScore.setText(String.format(Locale.US, "🎯 Điểm: %d/%d", score, currentIndex + 1));
                    });
                } else {
                    // BUG L5 FIX: si le serveur renvoie 4xx/5xx, l'ancien code
                    // ne ré-activait JAMAIS answerLocked → tous les boutons restaient
                    // gris pour le reste de la session (jusqu'à kill app).
                    // On ré-active la submission + on informe l'utilisateur.
                    String msg;
                    try {
                        if (response.body() != null && response.body().getMessage() != null) {
                            msg = response.body().getMessage();
                        } else {
                            msg = "Lỗi máy chủ (mã " + response.code() + "). Vui lòng thử lại.";
                        }
                    } catch (Exception ignore) {
                        msg = "Lỗi máy chủ (mã " + response.code() + "). Vui lòng thử lại.";
                    }
                    final String finalMsg = msg;
                    runOnUiThread(() -> {
                        answerLocked = false;
                        // BUG #R3-M1 FIX: restore tường minh visibility của
                        // btnSubmit/btnSkip và ẩn btnNext khi server trả lỗi.
                        // BUG L5 chỉ re-enable layoutMultiChoice, không revert
                        // visibility của các nút, nên với FILL_BLANK /
                        // SENTENCE_ORDER / MATCHING — nếu logic submit đã ẩn
                        // btnSubmit ở bất kỳ branch nào trước đó — user sẽ
                        // không còn nút nào để thử lại.
                        btnSubmit.setVisibility(View.VISIBLE);
                        if (btnSkip != null) btnSkip.setVisibility(View.VISIBLE);
                        btnNext.setVisibility(View.GONE);
                        // Ré-activer les choix multi-choice pour permettre un retry
                        if (layoutMultiChoice != null && layoutMultiChoice.getVisibility() == View.VISIBLE) {
                            for (int i = 0; i < layoutMultiChoice.getChildCount(); i++) {
                                layoutMultiChoice.getChildAt(i).setEnabled(true);
                            }
                        }
                        // BUG #R3-M1 FIX: re-enable input cho các exercise type khác.
                        if (etFillBlank != null && etFillBlank.getVisibility() == View.VISIBLE) {
                            etFillBlank.setEnabled(true);
                        }
                        if (layoutSentenceOrder != null && layoutSentenceOrder.getVisibility() == View.VISIBLE) {
                            for (int i = 0; i < layoutSentenceOrder.getChildCount(); i++) {
                                layoutSentenceOrder.getChildAt(i).setEnabled(true);
                            }
                        }
                        if (layoutMatch != null && layoutMatch.getVisibility() == View.VISIBLE) {
                            for (int i = 0; i < layoutMatch.getChildCount(); i++) {
                                layoutMatch.getChildAt(i).setEnabled(true);
                            }
                        }
                        Toast.makeText(PracticeActivity.this, finalMsg, Toast.LENGTH_SHORT).show();
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<AnswerResult>> call, Throwable t) {
                // Allow user to retry on network failure.
                runOnUiThread(() -> {
                    answerLocked = false;
                    // BUG #R3-M1 FIX: restore button visibility ở cả nhánh
                    // onFailure để đảm bảo user luôn có thể thử lại.
                    btnSubmit.setVisibility(View.VISIBLE);
                    if (btnSkip != null) btnSkip.setVisibility(View.VISIBLE);
                    btnNext.setVisibility(View.GONE);
                    if (layoutMultiChoice != null && layoutMultiChoice.getVisibility() == View.VISIBLE) {
                        for (int i = 0; i < layoutMultiChoice.getChildCount(); i++) {
                            layoutMultiChoice.getChildAt(i).setEnabled(true);
                        }
                    }
                    if (etFillBlank != null && etFillBlank.getVisibility() == View.VISIBLE) {
                        etFillBlank.setEnabled(true);
                    }
                    if (layoutSentenceOrder != null && layoutSentenceOrder.getVisibility() == View.VISIBLE) {
                        for (int i = 0; i < layoutSentenceOrder.getChildCount(); i++) {
                            layoutSentenceOrder.getChildAt(i).setEnabled(true);
                        }
                    }
                    if (layoutMatch != null && layoutMatch.getVisibility() == View.VISIBLE) {
                        for (int i = 0; i < layoutMatch.getChildCount(); i++) {
                            layoutMatch.getChildAt(i).setEnabled(true);
                        }
                    }
                    Toast.makeText(PracticeActivity.this,
                            "Lỗi mạng. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                });
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
                        // BUG-014 FIX: nếu user bấm Back giữa lúc request inflight,
                        // callback vẫn fire sau khi Activity destroyed →
                        // AlertDialog.Builder(this) trên context destroyed sẽ
                        // throws WindowManager$BadTokenException. Guard lại trước khi
                        // chạm view.
                        if (isFinishing() || isDestroyed()) return;
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

    /**
     * R4-L4 FIX: parse hintJson đúng cách thay vì gọi toString() — trước đây
     * user nhìn thấy "💡 {hint=Hãy chú ý đến trạng từ}" (dạng Map.toString)
     * thay vì "💡 Hãy chú ý đến trạng từ". Hỗ trợ cả 3 kiểu:
     *  - String thuần
     *  - Map/JsonObject có field "hint" hoặc "text"
     *  - Bất kỳ kiểu nào khác → fallback toString() đã được làm sạch.
     */
    private void showHint() {
        Exercise ex = exercises.get(currentIndex);
        if (ex.hintJson == null) return;
        String hintText = extractHintText(ex.hintJson);
        if (hintText == null || hintText.isEmpty()) return;
        tvHint.setText("💡 " + hintText);
        tvHint.setVisibility(View.VISIBLE);
    }

    /** R4-L4 FIX: parse hint từ nhiều kiểu cấu trúc khác nhau. */
    private String extractHintText(Object hintObj) {
        if (hintObj == null) return null;
        if (hintObj instanceof String) return (String) hintObj;
        try {
            JsonElement el = gson.toJsonTree(hintObj);
            if (el == null) return null;
            if (el.isJsonPrimitive()) return el.getAsString();
            if (el.isJsonObject()) {
                JsonObject jo = el.getAsJsonObject();
                if (jo.has("hint") && jo.get("hint").isJsonPrimitive())
                    return jo.get("hint").getAsString();
                if (jo.has("text") && jo.get("text").isJsonPrimitive())
                    return jo.get("text").getAsString();
                if (jo.has("message") && jo.get("message").isJsonPrimitive())
                    return jo.get("message").getAsString();
                // Nếu có exactly 1 entry primitive, lấy value đó.
                if (jo.entrySet().size() == 1) {
                    JsonElement v = jo.entrySet().iterator().next().getValue();
                    if (v.isJsonPrimitive()) return v.getAsString();
                }
            }
            if (el.isJsonArray() && el.getAsJsonArray().size() > 0) {
                JsonElement first = el.getAsJsonArray().get(0);
                if (first.isJsonPrimitive()) return first.getAsString();
            }
        } catch (Exception ignore) {}
        return null;
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
            // BUG-020 FIX: stop() trước khi release() — một số OEM giữ lại
            // AudioTrack buffer phát thêm 100–200ms sau release() → khi user
            // vừa thoát màn hình vẫn nghe nhẹ tiếng cuối câu.
            try {
                if (currentMediaPlayer.isPlaying()) currentMediaPlayer.stop();
            } catch (Exception ignore) {}
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
                // LF-7 FIX: chỉ trigger LESSON_COMPLETE quest khi backend xác nhận
                // lesson đã được complete thành công. Trước đây quest được trigger
                // ngay cả khi response 4xx/5xx → quest tăng nhưng XP không tính →
                // inconsistency trong gamification stats.
                boolean lessonCompleted = response.isSuccessful()
                        && response.body() != null
                        && response.body().isSuccess();
                LessonResult result = response.body() != null ? response.body().getData() : null;
                int xp = result != null ? result.xpEarned : 0;
                int sp = result != null ? result.scorePercent : (score * 100 / Math.max(1, exercises.size()));
                if (lessonCompleted) {
                    triggerLessonCompleteQuest();
                }
                openResultScreen(xp, sp);
            }
            @Override public void onFailure(Call<ApiResponse<LessonResult>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                // LF-7 FIX: KHÔNG trigger quest khi completeLesson thất bại
                // (network lỗi, server down) — nếu không quest tăng nhưng XP
                // không được tính → user thấy stats không khớp.
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
    public void onInit(int status) {
        // TD-4 FIX: handle TTS init status. Nếu engine không available (thiết bị
        // không cài, ngôn ngữ không support), trước đây tts.speak() fail im lặng.
        // Giờ ta đánh dấu trạng thái ready để showListenMode() biết và xử lý.
        ttsReady = (status == TextToSpeech.SUCCESS);
        if (!ttsReady) {
            runOnUiThread(() -> {
                // Không Toast ồn ào — chỉ disable btnListen nếu nó đang hiển thị
                // và thông báo nhẹ. User vẫn có thể đọc câu hỏi để trả lời.
                if (btnListen != null && btnListen.getVisibility() == View.VISIBLE) {
                    btnListen.setEnabled(false);
                    btnListen.setAlpha(0.5f);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        // R4-M2 FIX: shutdown TTS an toàn hơn — clear utterance listener trước
        // (callback có thể fire trên Activity đã destroy gây NPE/leak), gọi
        // stop() hai lần (một số OEM cần) và abandon audio focus để audio
        // không kẹt lại vài giây sau khi finish.
        if (tts != null) {
            try { tts.setOnUtteranceProgressListener(null); } catch (Exception ignore) {}
            try { if (tts.isSpeaking()) tts.stop(); } catch (Exception ignore) {}
            try { tts.stop(); } catch (Exception ignore) {}
            try { tts.shutdown(); } catch (Exception ignore) {}
            tts = null;
        }
        // R4-M2 FIX: abandon audio focus nếu có (một số OEM Samsung/Xiaomi
        // giữ focus sau TTS shutdown → audio app khác bị mute vài giây).
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) am.abandonAudioFocus(null);
        } catch (Exception ignore) {}
        // BUG 7.1 FIX: release MediaPlayer nếu user thoát màn hình khi audio
        // vẫn đang chuẩn bị / phát → tránh leak native AudioTrack handle.
        releaseCurrentMediaPlayer();
        super.onDestroy();
    }
}
