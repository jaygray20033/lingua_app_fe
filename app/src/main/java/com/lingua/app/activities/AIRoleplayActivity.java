package com.lingua.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
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
        spinnerLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedLanguage = langCodes[position];
                loadScenarios();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnStartSession.setOnClickListener(v -> startSession());
        btnSend.setOnClickListener(v -> sendMessage());
        btnMic.setOnClickListener(v -> toggleMic());
    }

    private void loadScenarios() {
        apiService.getScenarios(selectedLanguage, null).enqueue(new Callback<ApiResponse<List<RoleplayScenario>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<RoleplayScenario>>> call, Response<ApiResponse<List<RoleplayScenario>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    scenarios.clear();
                    scenarios.addAll(response.body().getData());
                    runOnUiThread(() -> updateScenarioSpinner());
                }
            }
            @Override public void onFailure(Call<ApiResponse<List<RoleplayScenario>>> call, Throwable t) {}
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
        progressBar.setVisibility(View.VISIBLE);
        Map<String, Object> body = new HashMap<>();
        body.put("language", selectedLanguage);
        body.put("type", "ROLEPLAY");
        if (selectedScenarioId != null) body.put("scenarioId", Integer.parseInt(selectedScenarioId));

        apiService.startSession(body).enqueue(new Callback<ApiResponse<AISession>>() {
            @Override
            public void onResponse(Call<ApiResponse<AISession>> call, Response<ApiResponse<AISession>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    sessionId = response.body().getData().sessionId;
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
                }
            }
            @Override public void onFailure(Call<ApiResponse<AISession>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                tvStatus.setText("Lỗi: " + t.getMessage());
            }
        });
    }

    private String getWelcomeMessage() {
        switch (selectedLanguage) {
            case "ja": return "こんにちは！今日は何について話しましょうか？😊";
            case "zh": return "你好！今天我们聊什么？😊";
            case "ko": return "안녕하세요! 오늘 무엇을 이야기할까요? 😊";
            default: return "Hello! What would you like to talk about today? 😊";
        }
    }

    private void sendMessage() {
        String text = etMessage.getText().toString().trim();
        if (text.isEmpty() || sessionId == -1) return;

        addMessage("USER", text);
        etMessage.setText("");
        // U7 FIX: French → Vietnamese.
        tvStatus.setText("💭 AI đang suy nghĩ…");

        // Use SSE via OkHttp for streaming
        streamAIResponse(text);
    }

    private void streamAIResponse(String userMessage) {
        String url = ApiClient.getClient(this).baseUrl().toString() + "ai/sessions/" + sessionId + "/chat";
        String token = SessionManager.getInstance(this).getAccessToken();

        OkHttpClient sseClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

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
        factory.newEventSource(request, new EventSourceListener() {
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
                runOnUiThread(() -> {
                    if (aiResponse.length() == 0) {
                        // U7 FIX: French → Vietnamese.
                        addMessage("AI", "Xin lỗi, đã có lỗi xảy ra. Hãy thử lại nhé.");
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
            // Rounded bubble background via GradientDrawable, no extra XML asset needed.
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(28f);
            // 6.13 FIX: dark-mode-aware bubble colors. Đọc UI mode hiện tại để
            // chọn màu phù hợp thay vì hardcode (trước đây bubble AI luôn xanh nhạt
            // gây chữ trắng-trên-trắng khi dark mode).
            int uiMode = container.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            boolean isDark = uiMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            int aiBg = isDark ? 0xFF2E3B2E : 0xFFE8F5E9;
            int userBg = isDark ? 0xFF0D47A1 : 0xFF1976D2;
            int aiText = isDark ? 0xFFE0E0E0 : 0xFF1B1B1B;
            int userText = 0xFFFFFFFF;
            bg.setColor(isAi ? aiBg : userBg);
            tv.setBackground(bg);
            tv.setTextColor(isAi ? aiText : userText);
            container.setGravity(isAi ? android.view.Gravity.START : android.view.Gravity.END);
        }

        @Override
        public int getItemCount() { return msgs.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(android.view.View v) { super(v); }
        }
    }
}
