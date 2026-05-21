package com.lingua.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.ApiResponse;
import com.lingua.app.models.ExampleSentence;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.*;

/**
 * Shadowing practice screen.
 *
 * Replaces the previous hard-coded sentence list with API-loaded sentences:
 *   GET /api/examples?language=ja&level=N4&limit=20
 *
 * For each sentence:
 *   - Plays the original audio from audioUrl (MediaPlayer) — falls back to TTS
 *     if audioUrl is missing.
 *   - Records the user's speech, transcribes it via Android SpeechRecognizer,
 *     and computes a Levenshtein-distance similarity score.
 */
public class ShadowingActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private static final int REQUEST_MICROPHONE = 101;

    private TextView tvTitle, tvOriginalText, tvRecognizedText, tvScore, tvStatus;
    private Button btnPlay, btnRecord, btnStop, btnNextContent, btnPrev;
    private ProgressBar progressBar;
    private TextToSpeech tts;
    private LinguaApiService apiService;

    private MediaPlayer mediaPlayer;
    private MediaRecorder mediaRecorder;
    private SpeechRecognizer speechRecognizer;
    private String recordingFilePath;
    private boolean isRecording = false;

    private final List<ExampleSentence> contents = new ArrayList<>();
    private int currentContentIndex = 0;

    /** Fallback content used when the API is offline. */
    private static final String[][] FALLBACK_CONTENTS = {
        {"ja", "N4", "いただきます", "食事の前に言う言葉です。「いただきます」"},
        {"ja", "N3", "Conversation at station", "すみません、新宿駅はどこですか？あそこの左側です。ありがとうございます。"},
        {"en", "B1", "Ordering food", "Excuse me, I would like to order the pasta, please. And could I have some water as well?"},
        {"zh", "HSK3", "Introduction", "你好，我叫李明。我是中国人。我在北京工作。很高兴认识你！"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shadowing);

        tts = new TextToSpeech(this, this);
        apiService = ApiClient.getService(this);
        initViews();
        checkMicPermission();
        loadSentences();
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvOriginalText = findViewById(R.id.tvOriginalText);
        tvRecognizedText = findViewById(R.id.tvRecognizedText);
        tvScore = findViewById(R.id.tvScore);
        tvStatus = findViewById(R.id.tvStatus);
        btnPlay = findViewById(R.id.btnPlay);
        btnRecord = findViewById(R.id.btnRecord);
        btnStop = findViewById(R.id.btnStop);
        progressBar = findViewById(R.id.progressBar);

        btnPlay.setOnClickListener(v -> playAudio());
        btnRecord.setOnClickListener(v -> startRecording());
        btnStop.setOnClickListener(v -> stopRecordingAndAnalyze());

        btnNextContent = findViewById(R.id.btnNextContent);
        btnPrev = findViewById(R.id.btnPrev);
        // R4-M8 FIX: nếu đang record mà user bấm Next/Prev → confirm dialog để
        // tránh trường hợp transcribe của câu CŨ bị compare với câu MỚI → score sai.
        if (btnNextContent != null) btnNextContent.setOnClickListener(v -> {
            if (contents.isEmpty()) return;
            confirmIfRecording(() -> {
                currentContentIndex = (currentContentIndex + 1) % contents.size();
                showContent(currentContentIndex);
            });
        });
        if (btnPrev != null) btnPrev.setOnClickListener(v -> {
            if (contents.isEmpty()) return;
            confirmIfRecording(() -> {
                currentContentIndex = (currentContentIndex - 1 + contents.size()) % contents.size();
                showContent(currentContentIndex);
            });
        });
    }

    /**
     * R4-M8 FIX: nếu đang ghi âm, hỏi user xác nhận trước khi chuyển câu.
     * Khi user xác nhận, ta abort recorder + speech recognizer để transcribe cũ
     * không fire callback so sánh với câu mới.
     */
    private void confirmIfRecording(Runnable onConfirm) {
        if (!isRecording) {
            // Không đang record — nhưng có thể đang transcribe (speechRecognizer
            // đang chạy). Nếu có, cancel speechRecognizer trước khi chuyển câu.
            cancelSpeechRecognizerIfActive();
            onConfirm.run();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Đang ghi âm")
                .setMessage("Bạn đang ghi âm câu này. Dừng và chuyển câu khác?")
                .setPositiveButton("Dừng và chuyển", (d, w) -> {
                    abortRecording();
                    onConfirm.run();
                })
                .setNegativeButton("Tiếp tục ghi", null)
                .show();
    }

    /** R4-M8 FIX: abort recorder + speech recognizer không để callback cũ fire. */
    private void abortRecording() {
        isRecording = false;
        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (Exception ignore) {}
            try { mediaRecorder.release(); } catch (Exception ignore) {}
            mediaRecorder = null;
        }
        cancelSpeechRecognizerIfActive();
        if (btnRecord != null) btnRecord.setEnabled(true);
        if (btnStop != null) btnStop.setVisibility(View.GONE);
        if (tvStatus != null) tvStatus.setText("Đã dừng ghi âm");
    }

    /**
     * R4-C2 FIX: release SpeechRecognizer instance. Trên Android 13+ system
     * limit 3 SpeechRecognizer instance per process — vượt sẽ throw
     * SecurityException "Other client already listening".
     */
    private void cancelSpeechRecognizerIfActive() {
        if (speechRecognizer != null) {
            try { speechRecognizer.cancel(); } catch (Exception ignore) {}
            try { speechRecognizer.destroy(); } catch (Exception ignore) {}
            speechRecognizer = null;
        }
    }

    /** Loads shadowing sentences from the backend.
     *  BUG B13 FIX: đọc ngôn ngữ/level từ SharedPreferences thay vì hardcode
     *  ja/N4 → trước đây user học tiếng Anh hoặc tiếng Trung vẫn thấy các câu
     *  tiếng Nhật.
     *  (BUG #19 FIX: comment tiếng Pháp → tiếng Việt.)
     */
    private void loadSentences() {
        progressBar.setVisibility(View.VISIBLE);
        tvStatus.setText("Đang tải câu shadowing...");

        android.content.SharedPreferences prefs =
                getSharedPreferences(OnboardingActivity.PREFS, MODE_PRIVATE);
        String lang = prefs.getString(OnboardingActivity.KEY_TARGET_LANG, "ja");
        String level = prefs.getString(OnboardingActivity.KEY_TARGET_LEVEL, null);
        if (level == null || level.isEmpty()) {
            switch (lang) {
                case "en": level = "B1"; break;
                case "zh": level = "HSK3"; break;
                case "ko": level = "TOPIK2"; break;
                default:   level = "N4"; break;
            }
        }
        final String langF = lang;
        final String levelF = level;
        apiService.getExampleSentences(langF, levelF, 20).enqueue(new Callback<ApiResponse<List<ExampleSentence>>>() {
            @Override public void onResponse(Call<ApiResponse<List<ExampleSentence>>> c, Response<ApiResponse<List<ExampleSentence>>> r) {
                progressBar.setVisibility(View.GONE);
                contents.clear();
                if (r.isSuccessful() && r.body() != null && r.body().getData() != null
                        && !r.body().getData().isEmpty()) {
                    contents.addAll(r.body().getData());
                } else {
                    fillFallback();
                }
                if (!contents.isEmpty()) showContent(0);
                else tvStatus.setText("Không có câu nào để luyện");
            }
            @Override public void onFailure(Call<ApiResponse<List<ExampleSentence>>> c, Throwable t) {
                progressBar.setVisibility(View.GONE);
                fillFallback();
                if (!contents.isEmpty()) showContent(0);
            }
        });
    }

    private void fillFallback() {
        contents.clear();
        for (String[] row : FALLBACK_CONTENTS) {
            ExampleSentence ex = new ExampleSentence();
            ex.languageCode = row[0];
            ex.level = row[1];
            ex.title = row[2];
            ex.sentence = row[3];
            contents.add(ex);
        }
    }

    private void showContent(int index) {
        if (index < 0 || index >= contents.size()) return;
        currentContentIndex = index;
        ExampleSentence ex = contents.get(index);
        String langCode = ex.languageCode != null ? ex.languageCode : "ja";
        String level = ex.level != null ? ex.level : "";
        String title = ex.title != null ? ex.title : ("Bài " + (index + 1));
        if (tvTitle != null) {
            // 6.16 FIX: thêm counter "x/y" để user biết tiến độ trong session
            tvTitle.setText(String.format(Locale.getDefault(), "[%s%s] %s  (%d/%d)",
                    langCode.toUpperCase(),
                    level.isEmpty() ? "" : (" - " + level),
                    title,
                    index + 1,
                    contents.size()));
        }
        tvOriginalText.setText(ex.getSentence());
        tvRecognizedText.setText("");
        tvScore.setText("Điểm: -");
        tvStatus.setText("Nhấn ▶ để nghe, rồi 🎙 để ghi âm");
        btnStop.setVisibility(View.GONE);
        // 6.16 FIX: vô hiệu hoá Prev/Next khi danh sách chỉ có 1 phần tử
        updateNavButtonsState();
    }

    /**
     * 6.16 FIX: cập nhật trạng thái enable của btnPrev / btnNextContent.
     */
    private void updateNavButtonsState() {
        boolean canNavigate = contents.size() > 1;
        if (btnPrev != null) btnPrev.setEnabled(canNavigate);
        if (btnNextContent != null) btnNextContent.setEnabled(canNavigate);
    }

    /**
     * Plays the original sentence's audio.
     * Prefers MediaPlayer (audioUrl), falls back to TTS.
     */
    private void playAudio() {
        if (contents.isEmpty()) return;
        ExampleSentence ex = contents.get(currentContentIndex);

        if (ex.audioUrl != null && !ex.audioUrl.isEmpty()) {
            try {
                if (mediaPlayer != null) { mediaPlayer.release(); }
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setDataSource(ex.audioUrl);
                mediaPlayer.prepareAsync();
                mediaPlayer.setOnPreparedListener(mp -> {
                    mp.start();
                    tvStatus.setText("🔊 Đang phát audio gốc...");
                });
                mediaPlayer.setOnCompletionListener(mp -> tvStatus.setText("Đã phát xong. Hãy ghi âm theo!"));
                return;
            } catch (Exception e) { /* fall through to TTS */ }
        }

        if (tts == null) return;
        Locale locale = bcpToLocale(ex.languageCode);
        tts.setLanguage(locale);
        tts.speak(ex.getSentence(), TextToSpeech.QUEUE_FLUSH, null, "shadowing");
        tvStatus.setText("🔊 Đang nghe...");
    }

    private Locale bcpToLocale(String langCode) {
        if (langCode == null) return Locale.JAPANESE;
        switch (langCode) {
            case "en": return Locale.ENGLISH;
            case "zh": return Locale.CHINESE;
            case "ko": return Locale.KOREAN;
            default: return Locale.JAPANESE;
        }
    }

    private void startRecording() {
        if (isRecording) return;

        // BUG #R3-M3 FIX: kiểm tra RECORD_AUDIO permission trước khi gọi
        // MediaRecorder.prepare(). Trước đây checkMicPermission() chỉ chạy
        // trong onCreate() và KHÔNG block UI. Nếu user nhanh tay bấm "🎙 Ghi
        // âm" trước khi grant permission → prepare() ném SecurityException /
        // IllegalStateException khiến activity rơi vào trạng thái lỗi. Nếu
        // user deny vĩnh viễn thì nút Record vẫn enable, cứ mỗi lần bấm là
        // crash hoặc fail silent.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Cần cấp quyền micro để ghi âm", Toast.LENGTH_LONG).show();
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }

        // R5-018 FIX: nettoyer l'enregistrement précédent avant d'en créer un nouveau.
        // Avant: chaque appel à startRecording() créait un .3gp avec timestamp
        // unique dans cacheDir sans jamais effacer les anciens. Après ~200 prises,
        // le cache se remplissait et MediaRecorder.prepare() lançait IOException
        // (disk full). On utilise maintenant un nom de fichier FIXE qui est
        // écrasé à chaque enregistrement; en plus on supprime explicitement
        // l'ancien si présent.
        java.io.File cacheDir = getExternalCacheDir();
        if (cacheDir != null) {
            // Nettoyage défensif: supprime aussi tout vieux fichier "shadowing_*.3gp"
            // laissé par d'anciennes versions de l'app.
            java.io.File[] old = cacheDir.listFiles(
                (dir, name) -> name.startsWith("shadowing_") && name.endsWith(".3gp")
            );
            if (old != null) {
                for (java.io.File f : old) {
                    try { f.delete(); } catch (Exception ignore) {}
                }
            }
        }
        recordingFilePath = (cacheDir != null ? cacheDir.getAbsolutePath() : getCacheDir().getAbsolutePath())
                + "/shadowing_current.3gp";
        // Supprime le précédent fichier "shadowing_current.3gp" si présent
        java.io.File oldFile = new java.io.File(recordingFilePath);
        if (oldFile.exists()) {
            try { oldFile.delete(); } catch (Exception ignore) {}
        }

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mediaRecorder.setOutputFile(recordingFilePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            isRecording = true;
            btnRecord.setEnabled(false);
            btnStop.setVisibility(View.VISIBLE);
            btnStop.setEnabled(true);
            tvStatus.setText("🎙 Đang ghi âm... Hãy nói ngay!");
        } catch (Exception e) {
            tvStatus.setText("Lỗi ghi âm: " + e.getMessage());
        }
    }

    private void stopRecordingAndAnalyze() {
        if (!isRecording) return;
        isRecording = false;

        try {
            mediaRecorder.stop();
            mediaRecorder.release();
            mediaRecorder = null;
        } catch (Exception e) { e.printStackTrace(); }

        btnRecord.setEnabled(true);
        btnStop.setVisibility(View.GONE);
        tvStatus.setText("🔍 Đang phân tích...");

        recognizeSpeech();
    }

    private void recognizeSpeech() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.setText("Nhận dạng giọng nói không khả dụng");
            return;
        }

        // R4-C2 FIX: release instance cũ (nếu còn) trước khi tạo cái mới.
        // Trên Android 13+ system limit 3 SpeechRecognizer/process — nếu user
        // record nhiều câu liên tiếp mà instance cũ chưa destroy, lần thứ 4
        // throw SecurityException "Other client already listening".
        cancelSpeechRecognizerIfActive();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        ExampleSentence ex = contents.get(currentContentIndex);
        String langCode = ex.languageCode != null ? ex.languageCode : "ja";
        String langBcp;
        switch (langCode) {
            case "en": langBcp = "en-US"; break;
            case "zh": langBcp = "zh-CN"; break;
            case "ko": langBcp = "ko-KR"; break;
            default: langBcp = "ja-JP"; break;
        }

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langBcp);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        final String original = ex.getSentence();
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognized = matches.get(0);
                    int score = calculateScore(original, recognized);
                    runOnUiThread(() -> {
                        tvRecognizedText.setText("🎤 Nhận dạng: " + recognized);
                        tvScore.setText(String.format(Locale.getDefault(), "🎯 Điểm: %d%%", score));
                        String feedback = score >= 90 ? "🌟 Xuất sắc!"
                                : score >= 70 ? "👍 Tốt lắm!"
                                : score >= 50 ? "📚 Cố gắng thêm!"
                                : "💪 Thử lại nhé!";
                        tvStatus.setText(feedback);
                        // 6.16 FIX: phản hồi âm thanh đúng/sai như Duolingo —
                        // ToneGenerator phát beep ngắn (đúng = nốt cao DTMF_5,
                        // sai = nốt thấp DTMF_0). Không yêu cầu asset.
                        playFeedbackTone(score >= 70);
                    });
                }
            }

            @Override public void onError(int error) {
                runOnUiThread(() -> tvStatus.setText("Lỗi nhận dạng. Hãy thử lại."));
            }
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(recognizerIntent);
    }

    /**
     * 6.16 FIX: phát beep ngắn để phản hồi đúng/sai (như Duolingo).
     * Dùng android.media.ToneGenerator nên không cần asset audio.
     */
    private void playFeedbackTone(boolean correct) {
        try {
            android.media.ToneGenerator tg = new android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_MUSIC, 80);
            int tone = correct
                    ? android.media.ToneGenerator.TONE_PROP_BEEP
                    : android.media.ToneGenerator.TONE_PROP_NACK;
            tg.startTone(tone, 200);
            // Release sau 250ms (sau khi tone kết thúc)
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(tg::release, 250);
        } catch (Exception ignore) {}
    }

    /**
     * Levenshtein-based similarity scoring (0..100).
     *
     * 6.16 FIX: normalize bằng cách strip dấu câu (。、，！？…)/whitespace dư
     * trước khi so sánh. Trước đây "食べます。" và "食べます" cho điểm < 100 do
     * khác dấu chấm — không công bằng với tiếng Nhật/Hàn/Trung (không có
     * upper/lowercase nên toLowerCase() vô tác dụng).
     */
    private int calculateScore(String original, String recognized) {
        if (original == null || recognized == null) return 0;
        original = normalizeForScoring(original);
        recognized = normalizeForScoring(recognized);
        if (original.equals(recognized)) return 100;
        int maxLen = Math.max(original.length(), recognized.length());
        if (maxLen == 0) return 100;
        int distance = levenshteinDistance(original, recognized);
        return Math.max(0, 100 - (distance * 100 / maxLen));
    }

    /**
     * 6.16 FIX: chuẩn hoá chuỗi trước khi tính Levenshtein.
     * - lowercase (vô hại với CJK)
     * - strip whitespace (cả half-width và full-width 　)
     * - strip dấu câu phổ biến (Latin + CJK: 。、，！？…「」『』())
     */
    private String normalizeForScoring(String s) {
        if (s == null) return "";
        String t = s.toLowerCase().trim();
        // Xoá whitespace (cả full-width space U+3000)
        t = t.replaceAll("[\\s\\u3000]+", "");
        // Xoá dấu câu Latin + CJK thông dụng
        t = t.replaceAll("[\\.,!?;:'\"`~\\-\\u3001\\u3002\\uFF01\\uFF1F\\uFF0C\\u2026\\u300C\\u300D\\u300E\\u300F\\uFF08\\uFF09()\\[\\]{}]", "");
        return t;
    }

    private int levenshteinDistance(String s1, String s2) {
        int m = s1.length(), n = s2.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) dp[i][j] = dp[i - 1][j - 1];
                else dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[m][n];
    }

    private void checkMicPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
        }
    }

    /**
     * BUG #R3-M3 FIX: handle permission result. Nếu user deny RECORD_AUDIO
     * thì disable nút Record và inform user thay vì để app im lặng crash khi
     * họ bấm record.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @androidx.annotation.NonNull String[] permissions,
                                           @androidx.annotation.NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MICROPHONE) {
            if (grantResults.length == 0
                    || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                if (btnRecord != null) btnRecord.setEnabled(false);
                if (tvStatus != null) {
                    tvStatus.setText("⚠️ Không có quyền micro — không thể luyện shadowing");
                }
            } else {
                if (btnRecord != null) btnRecord.setEnabled(true);
            }
        }
    }

    @Override
    public void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            tvStatus.setText("TTS không khả dụng");
        }
    }

    /**
     * R4-C2 FIX: release SpeechRecognizer + MediaPlayer trong onPause để không
     * leak khi user xoay máy / nhấn Home giữa lúc đang phân tích. Trên Android
     * 13+ system limit 3 SpeechRecognizer instance/process — instance leak
     * khiến lần recognize tiếp theo throw SecurityException.
     */
    @Override
    protected void onPause() {
        super.onPause();
        // Nếu đang ghi âm mà user rời màn hình → abort hẳn thay vì để recorder
        // tiếp tục chạy nền (chiếm micro, ngốn pin).
        if (isRecording) {
            abortRecording();
        }
        // Luôn cancel speech recognizer khi pause — tránh leak instance.
        cancelSpeechRecognizerIfActive();
        // R4-C2: tạm dừng MediaPlayer (nếu đang phát audio gốc).
        if (mediaPlayer != null) {
            try { if (mediaPlayer.isPlaying()) mediaPlayer.pause(); } catch (Exception ignore) {}
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignore) {}
            try { tts.shutdown(); } catch (Exception ignore) {}
            tts = null;
        }
        if (mediaRecorder != null) {
            try { mediaRecorder.release(); } catch (Exception ignore) {}
            mediaRecorder = null;
        }
        if (mediaPlayer != null) {
            try { mediaPlayer.release(); } catch (Exception ignore) {}
            mediaPlayer = null;
        }
        // R4-C2 FIX: dual-guard, destroy() và set null.
        cancelSpeechRecognizerIfActive();
        super.onDestroy();
    }
}
