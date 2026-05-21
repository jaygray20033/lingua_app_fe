package com.lingua.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.*;
import com.lingua.app.utils.SessionManager;
import okhttp3.*;
import okhttp3.sse.*;
import org.json.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AIRoleplayActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    private RecyclerView recyclerMessages;
    private EditText etMessage;
    private ImageButton btnSend, btnMic;
    private Button btnStartSession;
    // R5-016 FIX: garde anti-spam. Avant, double/triple tap sur btnSend
    // empilait N appels SSE + N bulles AI vides (placeholder "..."). Maintenant
    // on bloque jusqu'à ce que la réponse soit complète ou que la SSE échoue.
    private volatile boolean isSending = false;
    private Spinner spinnerLanguage, spinnerScenario;
    private TextView tvStatus, tvScenarioDesc;
    private ProgressBar progressBar;
    private LinguaApiService apiService;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;

    private List<ChatMessage> messages = new ArrayList<>();
    private ChatAdapter chatAdapter;
    private long sessionId = -1;
    private String selectedLanguage = "ja";
    private String selectedScenarioId = null;
    private List<RoleplayScenario> scenarios = new ArrayList<>();
    private boolean isMicActive = false;

    // BUG-001 FIX: Constant for RECORD_AUDIO permission request code so we can
    // route the result back to startMic() once the user grants the permission.
    private static final int REQ_RECORD_AUDIO = 102;

    // BUG #8 FIX: giữ reference EventSource để có thể cancel trong onDestroy.
    // Nếu user thoát màn hình AI giữa chừng khi đang streaming response, OkHttp
    // SSE connection sẽ tiếp tục chạy nền → memory leak + callback reference
    // vào Activity đã destroy + giữ network socket.
    private EventSource activeEventSource;
    private OkHttpClient activeSseClient;

    // R4-H6 FIX: cờ đồng bộ (synchronous) để chặn double-tap trên btnStartSession
    // trước khi setEnabled(false) kịp áp dụng (UI thread có độ trễ
    // 50-100ms trên thiết bị low-end → 2 session tạo song song).
    private boolean startingSession = false;

    // R4-L5 FIX: cache 2 GradientDrawable cho user + AI bubble để ChatAdapter
    // không tạo new GradientDrawable() mỗi onBindViewHolder → giảm GC pressure
    // trên thiết bị low-end khi conversation > 100 messages.
    private static android.graphics.drawable.GradientDrawable sUserBubbleBg;
    private static android.graphics.drawable.GradientDrawable sAiBubbleBg;
    private static boolean sBubbleCacheIsDark = false;

    // U6 FIX: keys used to restore chat history across rotation / process death.
    private static final String STATE_MESSAGES_ROLE = "chat_roles";
    private static final String STATE_MESSAGES_CONTENT = "chat_contents";
    private static final String STATE_SESSION_ID = "session_id";
    private static final String STATE_LANGUAGE = "lang";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_roleplay);

        apiService = ApiClient.getService(this);
        tts = new TextToSpeech(this, this);

        initViews();
        loadScenarios();

        // U6 FIX: restore previous chat messages so we don't lose context on rotate.
        if (savedInstanceState != null) {
            ArrayList<String> roles = savedInstanceState.getStringArrayList(STATE_MESSAGES_ROLE);
            ArrayList<String> contents = savedInstanceState.getStringArrayList(STATE_MESSAGES_CONTENT);
            sessionId = savedInstanceState.getLong(STATE_SESSION_ID, -1);
            String lang = savedInstanceState.getString(STATE_LANGUAGE);
            if (lang != null) selectedLanguage = lang;
            if (roles != null && contents != null && roles.size() == contents.size()) {
                for (int i = 0; i < roles.size(); i++) {
                    messages.add(new ChatMessage(roles.get(i), contents.get(i)));
                }
                chatAdapter.notifyDataSetChanged();
                if (!messages.isEmpty()) recyclerMessages.scrollToPosition(messages.size() - 1);
            }
            if (sessionId > 0) {
                btnStartSession.setVisibility(View.GONE);
                spinnerLanguage.setEnabled(false);
                spinnerScenario.setEnabled(false);
                etMessage.setEnabled(true);
                btnSend.setEnabled(true);
                btnMic.setEnabled(true);
                tvStatus.setText("\u2705 Phi\u00ean \u0111ang ho\u1ea1t \u0111\u1ed9ng");
            }
        }
    }

    @Override
    protected void onSaveInstanceState(@androidx.annotation.NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // R4-C3 FIX: cancel SSE EventSource TRƯỚC khi configuration change —
        // nếu không, sau rotation Activity mới sẽ start SSE thứ hai (nếu user
        // gõ tin nhắn) trong khi SSE cũ vẫn streaming → 2 callback chạy song
        // song → updateLastMessage() interleave/scrambled text.
        if (activeEventSource != null) {
            try { activeEventSource.cancel(); } catch (Exception ignore) {}
            activeEventSource = null;
        }
        if (activeSseClient != null) {
            try {
                activeSseClient.dispatcher().cancelAll();
            } catch (Exception ignore) {}
            // Không shutdown executor / evict pool ở đây: Activity mới sau
            // configuration change vẫn cần build client mới nhưng ít nhất SSE
            // cũ đã dừng.
            activeSseClient = null;
        }

        // U6 FIX: serialize chat history before configuration change.
        ArrayList<String> roles = new ArrayList<>();
        ArrayList<String> contents = new ArrayList<>();
        for (ChatMessage m : messages) {
            roles.add(m.role);
            contents.add(m.content);
        }
        outState.putStringArrayList(STATE_MESSAGES_ROLE, roles);
        outState.putStringArrayList(STATE_MESSAGES_CONTENT, contents);
        outState.putLong(STATE_SESSION_ID, sessionId);
        outState.putString(STATE_LANGUAGE, selectedLanguage);
    }

    private void initViews() {
        recyclerMessages = findViewById(R.id.recyclerMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnMic = findViewById(R.id.btnMic);
        btnStartSession = findViewById(R.id.btnStartSession);
        spinnerLanguage = findViewById(R.id.spinnerLanguage);
        spinnerScenario = findViewById(R.id.spinnerScenario);
        tvStatus = findViewById(R.id.tvStatus);
        tvScenarioDesc = findViewById(R.id.tvScenarioDesc);
        progressBar = findViewById(R.id.progressBar);

        recyclerMessages.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(messages);
        recyclerMessages.setAdapter(chatAdapter);

        // Language selector
        String[] langs = {"🇯🇵 Nhật Bản", "🇬🇧 English", "🇨🇳 中文", "🇰🇷 한국어"};
        String[] langCodes = {"ja", "en", "zh", "ko"};
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, langs);
        langAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLanguage.setAdapter(langAdapter);

        // LF-6 FIX: pre-select spinner theo target_language của user (đã lưu khi
        // onboarding hoặc Settings). Trước đây spinner mặc định "Nhật Bản" bất
        // kể user đang học ngôn ngữ nào → user dễ bấm "Bắt đầu phiên" mà không
        // để ý → AI nói nhầm ngôn ngữ.
        String savedLang = getSharedPreferences("LinguaPrefs", MODE_PRIVATE)
                .getString(OnboardingActivity.KEY_TARGET_LANG, "ja");
        int langIdx = -1;
        for (int i = 0; i < langCodes.length; i++) {
            if (langCodes[i].equals(savedLang)) { langIdx = i; break; }
        }
        if (langIdx >= 0) {
            spinnerLanguage.setSelection(langIdx);
            selectedLanguage = langCodes[langIdx];
        }

        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLanguage = langCodes[position];
                // BUG #R3-M5 FIX: reset scenario state khi đổi ngôn ngữ.
                // Trước đây selectedScenarioId vẫn giữ id của scenario JP
                // sau khi user đổi sang EN — nếu user bấm "Bắt đầu phiên"
                // trước khi loadScenarios callback về thì backend nhận id
                // không tồn tại trong scenarios EN → 400 Bad Request.
                selectedScenarioId = null;
                scenarios.clear();
                loadScenarios();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnStartSession.setOnClickListener(v -> startSession());
        btnSend.setOnClickListener(v -> sendMessage());
        btnMic.setOnClickListener(v -> toggleMic());
    }

    private void loadScenarios() {
        // BUG-018 FIX: hiển thị progress bar + disable scenario spinner trong khi
        // network call đang chạy. Trước đây user đổi ngôn ngữ nhưng spinner vẫn
        // hiển thị scenarios cũ → bấm "Bắt đầu phiên" với scenarioId thuộc ngôn
        // ngữ khác → backend 400.
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (spinnerScenario != null) spinnerScenario.setEnabled(false);
        if (tvStatus != null) tvStatus.setText("⏳ Đang tải kịch bản…");
        apiService.getScenarios(selectedLanguage, null).enqueue(new Callback<ApiResponse<List<RoleplayScenario>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<RoleplayScenario>>> call, Response<ApiResponse<List<RoleplayScenario>>> response) {
                if (isFinishing() || isDestroyed()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    scenarios.clear();
                    scenarios.addAll(response.body().getData());
                    runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (spinnerScenario != null) spinnerScenario.setEnabled(true);
                        updateScenarioSpinner();
                        if (tvStatus != null && sessionId == -1) tvStatus.setText("Chọn kịch bản và bắt đầu phiên");
                    });
                } else {
                    runOnUiThread(() -> {
                        if (progressBar != null) progressBar.setVisibility(View.GONE);
                        if (spinnerScenario != null) spinnerScenario.setEnabled(true);
                        if (tvStatus != null) tvStatus.setText("⚠️ Không tải được kịch bản (mã " + response.code() + ")");
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<List<RoleplayScenario>>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) return;
                runOnUiThread(() -> {
                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                    if (spinnerScenario != null) spinnerScenario.setEnabled(true);
                    if (tvStatus != null) tvStatus.setText("⚠️ Lỗi kết nối khi tải kịch bản");
                });
            }
        });
    }

    private void updateScenarioSpinner() {
        List<String> titles = new ArrayList<>();
        // U7 FIX: French → Vietnamese.
        titles.add("🆓 Trò chuyện tự do");
        for (RoleplayScenario s : scenarios) {
            titles.add((s.isPremium == 1 ? "👑 " : "") + s.title);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, titles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerScenario.setAdapter(adapter);
        spinnerScenario.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    selectedScenarioId = null;
                    // U7 FIX: Vietnamese description for free-mode.
                    tvScenarioDesc.setText("Chế độ tự do — bạn có thể nói về bất kỳ chủ đề nào!");
                } else {
                    RoleplayScenario scenario = scenarios.get(position - 1);
                    selectedScenarioId = String.valueOf(scenario.id);
                    tvScenarioDesc.setText("🎯 " + scenario.goal);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void startSession() {
        // R4-H6 FIX: chặn double-tap đồng bộ NGAY đầu method — setEnabled(false)
        // là view update và có thể mất 50-100ms mới thực sự disable. Nếu user
        // double-tap nhanh trước khi setEnabled() áp dụng → 2 session tạo
        // song song → sessionId thứ 2 ghi đè thứ 1 → mất context.
        if (startingSession) return;
        startingSession = true;
        // Disable button TRƯỚC khi set progressBar (đổi thứ tự so với bản cũ).
        btnStartSession.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        // BUG-013 FIX: disable nút "Bắt đầu phiên" trong khi request đang inflight
        // để tránh user spam tap → nhiều session được tạo song song.
        Map<String, Object> body = new HashMap<>();
        body.put("language", selectedLanguage);
        body.put("type", "ROLEPLAY");
        if (selectedScenarioId != null) body.put("scenarioId", Integer.parseInt(selectedScenarioId));

        apiService.startSession(body).enqueue(new Callback<ApiResponse<AISession>>() {
            @Override
            public void onResponse(Call<ApiResponse<AISession>> call, Response<ApiResponse<AISession>> response) {
                if (isFinishing() || isDestroyed()) { startingSession = false; return; }
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    sessionId = response.body().getData().sessionId;
                    startingSession = false; // R4-H6: cho phép restart sau khi success/fail
                    runOnUiThread(() -> {
                        btnStartSession.setVisibility(View.GONE);
                        spinnerLanguage.setEnabled(false);
                        spinnerScenario.setEnabled(false);
                        etMessage.setEnabled(true);
                        btnSend.setEnabled(true);
                        btnMic.setEnabled(true);
                        // U7 FIX: French → Vietnamese status messages.
                        tvStatus.setText("✅ Đã bắt đầu phiên! Hãy nhắn tin…");
                        addMessage("AI", getWelcomeMessage());
                    });
                } else {
                    // BUG-013 FIX: hiển thị thông báo lỗi rõ ràng cho user khi
                    // backend trả 4xx/5xx (vd 403 cho scenario PREMIUM). Trước đây
                    // không có else branch → user thấy progress bar biến mất mà
                    // không hiểu chuyện gì.
                    final String msg;
                    if (response.body() != null && response.body().getMessage() != null
                            && !response.body().getMessage().isEmpty()) {
                        msg = response.body().getMessage();
                    } else if (response.code() == 403) {
                        msg = "Kịch bản này dành cho thành viên Premium";
                    } else {
                        msg = "Không bắt đầu được phiên (mã " + response.code() + ")";
                    }
                    startingSession = false; // R4-H6
                    runOnUiThread(() -> {
                        tvStatus.setText("⚠️ " + msg);
                        btnStartSession.setEnabled(true);
                    });
                }
            }
            @Override public void onFailure(Call<ApiResponse<AISession>> call, Throwable t) {
                if (isFinishing() || isDestroyed()) { startingSession = false; return; }
                startingSession = false; // R4-H6
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    btnStartSession.setEnabled(true);
                    tvStatus.setText("⚠️ Lỗi: " + t.getMessage());
                });
            }
        });
    }

    private String getWelcomeMessage() {
        // BUG-026 FIX: thống nhất phong cách câu hỏi — tất cả các ngôn ngữ đều
        // dùng dấu hỏi (?) thay vì lẫn lộn dấu ! / ? giữa các locale.
        switch (selectedLanguage) {
            case "ja": return "こんにちは！今日は何について話しましょうか？😊";
            case "zh": return "你好！今天我们聊什么？😊";
            case "ko": return "안녕하세요? 오늘 무엇을 이야기할까요? 😊";
            default: return "Hello! What would you like to talk about today? 😊";
        }
    }

    private void sendMessage() {
        // R5-016 FIX: idempotency guard. Si une réponse AI est encore en cours
        // (SSE pas terminé), on ignore le tap. Sinon l'user pouvait spammer le
        // bouton → 10 bulles AI "..." + 10 connections SSE simultanées.
        if (isSending) return;

        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || sessionId == -1) return;

        isSending = true;
        if (btnSend != null) btnSend.setEnabled(false);

        addMessage("USER", text);
        etMessage.setText("");
        // U7 FIX: French → Vietnamese.
        tvStatus.setText("💭 AI đang suy nghĩ…");

        // Use SSE via OkHttp for streaming
        streamAIResponse(text);
    }

    /** R5-016 helper: ré-active l'envoi quand la réponse AI est terminée
     *  (succès ou échec). Appelée depuis onEvent(done=true) et onFailure(). */
    private void resetSendingState() {
        isSending = false;
        runOnUiThread(() -> {
            if (btnSend != null) btnSend.setEnabled(true);
        });
    }

    private void streamAIResponse(String userMessage) {
        String url = ApiClient.getClient(this).baseUrl().toString() + "ai/sessions/" + sessionId + "/chat";
        String token = SessionManager.getInstance(this).getAccessToken();

        // BUG #8 FIX: cancel previous SSE if still streaming, để tránh nhiều
        // connection chồng chéo khi user gửi nhiều tin nhắn liên tiếp.
        if (activeEventSource != null) {
            try { activeEventSource.cancel(); } catch (Exception ignore) {}
            activeEventSource = null;
        }

        OkHttpClient sseClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();
        activeSseClient = sseClient;

        // BUG B16 FIX: build the JSON body via JSONObject instead of manual string
        // concatenation. The previous code only escaped double-quotes, so any newline,
        // backslash, tab, or control character in the user message would produce
        // invalid JSON and the backend would 400 the request.
        String jsonBody;
        try {
            JSONObject payload = new JSONObject();
            payload.put("message", userMessage);
            jsonBody = payload.toString();
        } catch (JSONException e) {
            jsonBody = "{}";
        }
        RequestBody body = RequestBody.create(
            jsonBody,
            MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Authorization", "Bearer " + token)
            .addHeader("Content-Type", "application/json")
            .build();

        StringBuilder aiResponse = new StringBuilder();
        final String[] msgId = {null};

        EventSource.Factory factory = EventSources.createFactory(sseClient);
        // BUG #8 FIX: lưu reference vào activeEventSource thay vì discard.
        activeEventSource = factory.newEventSource(request, new EventSourceListener() {
            @Override
            public void onEvent(EventSource eventSource, String id, String type, String data) {
                try {
                    JSONObject json = new JSONObject(data);
                    if (json.has("content")) {
                        String chunk = json.getString("content");
                        aiResponse.append(chunk);
                        runOnUiThread(() -> updateLastMessage(aiResponse.toString()));
                    }
                    if (json.has("done") && json.getBoolean("done")) {
                        // R5-016 FIX: réactive btnSend dès que la SSE est complète.
                        resetSendingState();
                        String fullText = aiResponse.toString();
                        runOnUiThread(() -> {
                            // U7 FIX: French → Vietnamese.
                            tvStatus.setText("✅ Đã nhận phản hồi");
                            speakAI(fullText);
                        });
                    }
                } catch (Exception e) {}
            }

            @Override
            public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                // R5-016 FIX: réactive btnSend en cas d'échec SSE pour permettre un retry.
                resetSendingState();
                runOnUiThread(() -> {
                    if (aiResponse.length() == 0) {
                        // U7 FIX: French → Vietnamese.
                        addMessage("AI", "Xin lỗi, đã có lỗi xảy ra. Hãy thử lại nhé.");
                    } else {
                        // BUG #R3-L3 FIX: SSE fail giữa chừng (đã nhận một
                        // phần response rồi mất kết nối) — nếu chỉ giữ
                        // nguyên text dở dang user không biết là kết nối
                        // ngắt hay AI thực sự dừng ở đó. Append ghi chú để
                        // họ biết và có thể gửi lại tin nhắn.
                        String partial = aiResponse.toString().trim();
                        updateLastMessage(partial + " … ⚠️ (kết nối bị ngắt)");
                    }
                    tvStatus.setText("Phiên đang hoạt động");
                });
            }
        });

        // Add placeholder message
        runOnUiThread(() -> addMessage("AI", "..."));
    }

    private void updateLastMessage(String content) {
        if (!messages.isEmpty() && "AI".equals(messages.get(messages.size()-1).role)) {
            messages.get(messages.size()-1).content = content;
            chatAdapter.notifyItemChanged(messages.size()-1);
            recyclerMessages.scrollToPosition(messages.size()-1);
        }
    }

    private void addMessage(String role, String content) {
        messages.add(new ChatMessage(role, content));
        chatAdapter.notifyItemInserted(messages.size()-1);
        recyclerMessages.scrollToPosition(messages.size()-1);
    }

    private void speakAI(String text) {
        if (tts == null) return;
        java.util.Locale locale;
        switch (selectedLanguage) {
            case "en": locale = java.util.Locale.ENGLISH; break;
            case "zh": locale = java.util.Locale.CHINESE; break;
            case "ko": locale = java.util.Locale.KOREAN; break;
            default: locale = java.util.Locale.JAPANESE; break;
        }
        tts.setLanguage(locale);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void toggleMic() {
        if (isMicActive) {
            stopMic();
        } else {
            startMic();
        }
    }

    private void startMic() {
        // BUG-001 FIX: kiểm tra RECORD_AUDIO permission TRƯỚC khi tạo
        // SpeechRecognizer. Nếu chưa có quyền, request runtime permission và
        // return — không setText "🎙 Hãy nói…" để tránh user tưởng đang nghe.
        // Trên Android 13+ và một số OEM, gọi startListening() mà chưa có quyền
        // sẽ raise ERROR_INSUFFICIENT_PERMISSIONS im lặng (hoặc SecurityException).
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            Toast.makeText(this, "Cần cấp quyền micro để dùng tính năng này",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            // U7 FIX: French → Vietnamese.
            tvStatus.setText("Thiết bị không hỗ trợ nhận dạng giọng nói");
            return;
        }
        isMicActive = true;
        btnMic.setImageResource(android.R.drawable.ic_btn_speak_now);
        tvStatus.setText("🎙 Hãy nói…");

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLocaleForLanguage(selectedLanguage));

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onResults(Bundle results) {
                isMicActive = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    runOnUiThread(() -> {
                        etMessage.setText(text);
                        sendMessage();
                    });
                }
            }
            @Override public void onError(int error) {
                isMicActive = false;
                runOnUiThread(() -> tvStatus.setText("Lỗi nhận dạng giọng nói"));
            }
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}
        });

        speechRecognizer.startListening(intent);
    }

    private void stopMic() {
        isMicActive = false;
        if (speechRecognizer != null) speechRecognizer.stopListening();
        tvStatus.setText("Phiên đang hoạt động");
    }

    // BUG-001 FIX: handle permission result for RECORD_AUDIO. Nếu user grant
    // thì khởi động lại mic flow ngay; nếu từ chối thì disable btnMic và hiển
    // thị thông báo giải thích.
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMic();
            } else {
                if (btnMic != null) btnMic.setEnabled(false);
                if (tvStatus != null) tvStatus.setText("⚠️ Cần quyền micro để dùng giọng nói");
            }
        }
    }

    private String getLocaleForLanguage(String lang) {
        switch (lang) {
            case "en": return "en-US";
            case "zh": return "zh-CN";
            case "ko": return "ko-KR";
            default: return "ja-JP";
        }
    }

    @Override
    public void onInit(int status) {}

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        // BUG #8 FIX: cancel SSE connection để tránh memory leak + crash khi
        // streaming response trả về sau khi Activity đã destroy.
        if (activeEventSource != null) {
            try { activeEventSource.cancel(); } catch (Exception ignore) {}
            activeEventSource = null;
        }
        if (activeSseClient != null) {
            try {
                activeSseClient.dispatcher().cancelAll();
                activeSseClient.dispatcher().executorService().shutdown();
                activeSseClient.connectionPool().evictAll();
            } catch (Exception ignore) {}
            activeSseClient = null;
        }
        super.onDestroy();
    }

    // Simple chat model
    public static class ChatMessage {
        public String role, content;
        ChatMessage(String role, String content) { this.role = role; this.content = content; }
    }

    // Simple chat adapter
    // U12 FIX: differentiate USER vs AI bubbles — user bubbles right-aligned blue,
    // AI bubbles left-aligned green. Previously every bubble looked the same on
    // the left, so it was hard to follow the conversation.
    static class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.VH> {
        private final List<ChatMessage> msgs;
        ChatAdapter(List<ChatMessage> msgs) { this.msgs = msgs; }

        @Override
        public VH onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            LinearLayout ll = new LinearLayout(parent.getContext());
            ll.setOrientation(LinearLayout.HORIZONTAL);
            ll.setLayoutParams(new RecyclerView.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            ll.setPadding(24, 12, 24, 12);
            TextView tv = new TextView(parent.getContext());
            tv.setTag("msg");
            tv.setPadding(28, 16, 28, 16);
            tv.setMaxWidth((int) (parent.getResources().getDisplayMetrics().widthPixels * 0.75));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            tv.setLayoutParams(lp);
            ll.addView(tv);
            return new VH(ll);
        }

        @Override
        public void onBindViewHolder(VH holder, int position) {
            ChatMessage msg = msgs.get(position);
            LinearLayout container = (LinearLayout) holder.itemView;
            TextView tv = holder.itemView.findViewWithTag("msg");
            boolean isAi = "AI".equals(msg.role);
            tv.setText((isAi ? "🤖 " : "👤 ") + msg.content);
            // 6.13 FIX: dark-mode-aware bubble colors. Đọc UI mode hiện tại để
            // chọn màu phù hợp thay vì hardcode.
            int uiMode = container.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            boolean isDark = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            int aiText = isDark ? 0xFFE0E0E0 : 0xFF1B1B1B;
            int userText = 0xFFFFFFFF;
            // R4-L5 FIX: lấy từ cache thay vì new GradientDrawable() mỗi onBind.
            // Với conversation 100+ msg + scroll nhanh, GC pressure trên thiết
            // bị low-end (RAM 2GB) giảm đáng kể.
            android.graphics.drawable.GradientDrawable bg = getBubbleBackground(isAi, isDark);
            tv.setBackground(bg);
            tv.setTextColor(isAi ? aiText : userText);
            container.setGravity(isAi ? android.view.Gravity.START : android.view.Gravity.END);
        }

        /**
         * R4-L5 FIX: lấy GradientDrawable từ cache; re-build cache khi day/night
         * mode đổi. Mỗi Activity recreate sẽ kiểm tra sBubbleCacheIsDark và
         * tiêu hủy cache cũ nếu cần.
         */
        private static synchronized android.graphics.drawable.GradientDrawable getBubbleBackground(boolean isAi, boolean isDark) {
            if (sUserBubbleBg == null || sAiBubbleBg == null || sBubbleCacheIsDark != isDark) {
                int aiBg = isDark ? 0xFF2E3B2E : 0xFFE8F5E9;
                int userBg = isDark ? 0xFF0D47A1 : 0xFF1976D2;
                sAiBubbleBg = new android.graphics.drawable.GradientDrawable();
                sAiBubbleBg.setCornerRadius(28f);
                sAiBubbleBg.setColor(aiBg);
                sUserBubbleBg = new android.graphics.drawable.GradientDrawable();
                sUserBubbleBg.setCornerRadius(28f);
                sUserBubbleBg.setColor(userBg);
                sBubbleCacheIsDark = isDark;
            }
            // GradientDrawable mutable; clone constant-state để mỗi bubble có phên
            // bản riêng nhưng share underlying pixels — vẫn nhẹ hơn new() hoàn toàn.
            return (android.graphics.drawable.GradientDrawable)
                    (isAi ? sAiBubbleBg : sUserBubbleBg).getConstantState().newDrawable().mutate();
        }

        @Override
        public int getItemCount() { return msgs.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(android.view.View v) { super(v); }
        }
    }
}
