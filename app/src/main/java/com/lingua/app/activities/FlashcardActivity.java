package com.lingua.app.activities;

import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import com.lingua.app.R;
import com.lingua.app.api.ApiClient;
import com.lingua.app.api.LinguaApiService;
import com.lingua.app.models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import java.util.*;

/**
 * SRS-based flashcard review session.
 *
 * FIX (TODO 1.5 / 2.3): Now correctly uses GET /api/srs/due and POST
 * /api/srs/{reviewId}/review (SM-2 spaced repetition) instead of the old
 * /flashcards/decks endpoints.
 *
 * Features:
 *   - Loads due reviews from /api/srs/due (optionally filtered by deckId).
 *   - Submits ratings (AGAIN / HARD / GOOD / EASY) to /api/srs/{id}/review.
 *   - Shows session counters: New / Learning / Review.
 *   - 3D flip animation between front and back.
 *   - Triggers SRS_REVIEW quest event after each successful review.
 */
public class FlashcardActivity extends AppCompatActivity {

    private TextView tvFront, tvBack, tvProgress, tvNextReview, tvState, tvSessionStats;
    private View cardFront, cardBack, layoutCard;
    private Button btnAgain, btnHard, btnGood, btnEasy;
    private ProgressBar progressBar;
    private LinguaApiService apiService;

    private final List<SrsCard> cards = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isFlipped = false;
    private long selectedDeckId; // 0 = all decks
    private float cameraScale;

    // BUG B17 FIX: keep a single instance Handler so we can cancel any pending
    // postDelayed callbacks in onDestroy / onPause. Previously we created a new
    // Handler() in advanceAfterReview() which leaked the Activity and could
    // crash if the user left the screen before the 400ms delay elapsed.
    private final Handler advanceHandler = new Handler(Looper.getMainLooper());

    // session counters
    private int statNew = 0, statLearning = 0, statReview = 0;
    private int reviewedCount = 0;

    // LF-3 FIX: track thời điểm hiển thị card hiện tại để tính elapsed time thực
    // tế khi submit review. Trước đây timeMs bị hardcode 5000ms — thuật toán
    // SM-2 dựa vào thời gian phản hồi để điều chỉnh interval sẽ hoạt động sai.
    private long cardShownMs = 0;

    // UX-6 FIX: khóa button rating khi đang gửi review để user không bấm 2 lần
    // (đặc biệt với legacy endpoint).
    private boolean reviewLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flashcard);

        apiService = ApiClient.getService(this);
        selectedDeckId = getIntent().getLongExtra("deckId", 0);

        tvFront = findViewById(R.id.tvFront);
        tvBack = findViewById(R.id.tvBack);
        tvProgress = findViewById(R.id.tvProgress);
        tvNextReview = findViewById(R.id.tvNextReview);
        tvState = findViewById(R.id.tvState);
        tvSessionStats = findViewById(R.id.tvSessionStats);
        cardFront = findViewById(R.id.cardFront);
        cardBack = findViewById(R.id.cardBack);
        layoutCard = findViewById(R.id.layoutCard);
        btnAgain = findViewById(R.id.btnAgain);
        btnHard = findViewById(R.id.btnHard);
        btnGood = findViewById(R.id.btnGood);
        btnEasy = findViewById(R.id.btnEasy);
        progressBar = findViewById(R.id.progressBar);

        // Increase camera distance for nicer 3D flip
        float scale = getResources().getDisplayMetrics().density;
        cameraScale = 8000 * scale;
        cardFront.setCameraDistance(cameraScale);
        cardBack.setCameraDistance(cameraScale);

        layoutCard.setOnClickListener(v -> flipCard());
        hideRatingButtons();

        btnAgain.setOnClickListener(v -> submitReview("AGAIN"));
        btnHard.setOnClickListener(v -> submitReview("HARD"));
        btnGood.setOnClickListener(v -> submitReview("GOOD"));
        btnEasy.setOnClickListener(v -> submitReview("EASY"));

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("📚 SRS Review");
        loadDueCards();
    }

    /** Loads SRS due reviews from /api/srs/due. */
    private void loadDueCards() {
        progressBar.setVisibility(View.VISIBLE);
        Long deckFilter = selectedDeckId > 0 ? selectedDeckId : null;
        apiService.getSrsDue(deckFilter, 50).enqueue(new Callback<ApiResponse<SrsDueResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<SrsDueResponse>> call, Response<ApiResponse<SrsDueResponse>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    SrsDueResponse data = response.body().getData();
                    cards.clear();
                    if (data != null) {
                        // Backend may return either grouped lists or a flat reviews list
                        if (data.reviews != null) cards.addAll(data.reviews);
                        if (data.dueCards != null) cards.addAll(data.dueCards);
                        if (data.learningCards != null) cards.addAll(data.learningCards);
                        if (data.newCards != null) cards.addAll(data.newCards);
                        statNew = data.totalNew;
                        statLearning = data.totalLearning;
                        statReview = data.totalDue;
                        // Fallback: count by state if totals missing
                        if (statNew + statLearning + statReview == 0) recomputeStatsFromList();
                    }
                    runOnUiThread(() -> {
                        updateSessionStats();
                        if (cards.isEmpty()) showEmptyState();
                        else showCard(0);
                    });
                } else {
                    Toast.makeText(FlashcardActivity.this, "Không tải được thẻ ôn tập", Toast.LENGTH_SHORT).show();
                    showEmptyState();
                }
            }
            @Override
            public void onFailure(Call<ApiResponse<SrsDueResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                // U20 FIX: Snackbar với action "Thử lại" thay vì Toast im lặng.
                showRetrySnackbar("Lỗi tải dữ liệu: " + t.getMessage(), v -> loadDueCards());
            }
        });
    }

    /**
     * U20 FIX: helper Snackbar với action "Thử lại" để user có thể retry tải
     * dữ liệu SRS ngay tại màn hình thay vì phải thoát ra/vào lại.
     */
    private void showRetrySnackbar(String message, android.view.View.OnClickListener onRetry) {
        android.view.View root = findViewById(android.R.id.content);
        if (root == null) return;
        try {
            Snackbar.make(root, message, Snackbar.LENGTH_INDEFINITE)
                    .setAction("Thử lại", onRetry)
                    .show();
        } catch (Throwable t) {
            Toast.makeText(FlashcardActivity.this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void recomputeStatsFromList() {
        statNew = statLearning = statReview = 0;
        for (SrsCard c : cards) {
            String s = c.getDisplayState();
            if ("NEW".equalsIgnoreCase(s)) statNew++;
            else if ("LEARNING".equalsIgnoreCase(s) || "RELEARNING".equalsIgnoreCase(s)) statLearning++;
            else statReview++;
        }
    }

    private void updateSessionStats() {
        if (tvSessionStats == null) return;
        tvSessionStats.setText(String.format(Locale.getDefault(),
                "🟦 New %d   🟧 Learning %d   🟩 Review %d", statNew, statLearning, statReview));
    }

    private void showCard(int index) {
        if (index >= cards.size()) {
            showCompletionState();
            return;
        }
        currentIndex = index;
        isFlipped = false;
        SrsCard card = cards.get(index);

        tvFront.setText(card.getFrontText());
        tvBack.setText(card.getBackText());
        tvProgress.setText(String.format(Locale.getDefault(), "%d / %d", index + 1, cards.size()));
        tvState.setText(card.getDisplayState());
        tvNextReview.setVisibility(View.GONE);

        // Reset card visibility for the new card
        cardFront.setVisibility(View.VISIBLE);
        cardBack.setVisibility(View.GONE);
        cardFront.setRotationY(0f);
        cardBack.setRotationY(0f);
        hideRatingButtons();

        // LF-3 FIX: ghi nhớ thời điểm bắt đầu hiển thị card để tính elapsed time
        // khi user rate. Đồng bộ với pattern ở PracticeActivity.
        cardShownMs = System.currentTimeMillis();
        // UX-6 FIX: mở lại khóa review cho card mới.
        reviewLocked = false;
    }

    /** 3D flip animation using ObjectAnimator XML resource (or graceful fallback). */
    private void flipCard() {
        if (isFlipped) return;
        isFlipped = true;
        try {
            AnimatorSet flipOut = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.card_flip_out);
            AnimatorSet flipIn = (AnimatorSet) AnimatorInflater.loadAnimator(this, R.animator.card_flip_in);
            flipOut.setTarget(cardFront);
            flipIn.setTarget(cardBack);
            flipOut.start();
            cardBack.setVisibility(View.VISIBLE);
            flipIn.start();
            cardFront.postDelayed(() -> cardFront.setVisibility(View.GONE), 200);
        } catch (Exception e) {
            // Fallback: simple swap
            cardFront.setVisibility(View.GONE);
            cardBack.setVisibility(View.VISIBLE);
        }
        showRatingButtons();
    }

    /** Submits an SRS rating to /api/srs/{reviewId}/review. */
    private void submitReview(String rating) {
        if (currentIndex >= cards.size()) return;
        // UX-6 FIX: chặn double-submit — user có thể bấm AGAIN rồi GOOD nhanh
        // sẽ gửi 2 review cho 1 card.
        if (reviewLocked) return;
        reviewLocked = true;

        SrsCard card = cards.get(currentIndex);
        long reviewId = card.getReviewId();

        // LF-3 FIX: tính elapsed time thực tế từ lúc showCard() đến lúc user rate.
        // Nếu vì lý do gì đó cardShownMs chưa được set (=0), fallback về 0L để
        // backend biết không đo được, hơn là gửi 5000ms giả.
        long elapsedMs = cardShownMs > 0 ? (System.currentTimeMillis() - cardShownMs) : 0L;

        if (reviewId <= 0) {
            // No review id — fallback to legacy endpoint by card id.
            // UX-6 FIX: Ẩn rating buttons NGAY trước khi gửi (trước đây chỉ ẩn ở
            // nhánh SRS endpoint → với legacy endpoint user có thể bấm 2 lần).
            hideRatingButtons();
            progressBar.setVisibility(View.VISIBLE);
            Map<String, String> legacy = new HashMap<>();
            legacy.put("rating", rating);
            // LF-3: legacy endpoint nhận String map — không có timeMs nguyên, nên
            // ta vẫn đóng gói như chuỗi để backend log nếu cần.
            legacy.put("timeMs", String.valueOf(elapsedMs));
            apiService.reviewCard(card.cardId > 0 ? card.cardId : card.id, legacy)
                    .enqueue(new Callback<ApiResponse<ReviewResult>>() {
                        @Override public void onResponse(Call<ApiResponse<ReviewResult>> c, Response<ApiResponse<ReviewResult>> r) {
                            progressBar.setVisibility(View.GONE);
                            advanceAfterReview(r.isSuccessful() && r.body() != null && r.body().getData() != null
                                    ? r.body().getData().intervalDays : 0, null);
                        }
                        @Override public void onFailure(Call<ApiResponse<ReviewResult>> c, Throwable t) {
                            progressBar.setVisibility(View.GONE);
                            advanceAfterReview(0, null);
                        }
                    });
            return;
        }

        hideRatingButtons();
        progressBar.setVisibility(View.VISIBLE);

        Map<String, Object> body = new HashMap<>();
        body.put("rating", rating);
        // LF-3 FIX: gửi elapsed time thực tế thay vì hardcode 5000ms — đồng bộ
        // với PracticeActivity (BUG 6) để thuật toán SM-2 tính interval chính xác.
        body.put("timeMs", elapsedMs);

        apiService.submitSrsReview(reviewId, body).enqueue(new Callback<ApiResponse<SrsReviewResult>>() {
            @Override
            public void onResponse(Call<ApiResponse<SrsReviewResult>> call, Response<ApiResponse<SrsReviewResult>> response) {
                progressBar.setVisibility(View.GONE);
                SrsReviewResult result = null;
                if (response.isSuccessful() && response.body() != null) {
                    result = response.body().getData();
                }
                advanceAfterReview(result != null ? result.intervalDays : 0,
                        result != null ? result.state : null);
                triggerSrsQuestEvent();
            }
            @Override
            public void onFailure(Call<ApiResponse<SrsReviewResult>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                advanceAfterReview(0, null);
            }
        });
    }

    private void advanceAfterReview(int intervalDays, String newState) {
        reviewedCount++;
        if (intervalDays > 0) {
            tvNextReview.setText(String.format(Locale.getDefault(), "⏰ Ôn lại sau: %d ngày", intervalDays));
            tvNextReview.setVisibility(View.VISIBLE);
        }
        // BUG B17 FIX: use the instance-level handler so we can cancel it in onDestroy.
        advanceHandler.removeCallbacksAndMessages(null);
        advanceHandler.postDelayed(() -> showCard(currentIndex + 1), 400);
    }

    @Override
    protected void onPause() {
        // BUG #14 FIX: cancel pending advance khi user nhấn Home / chuyển ra
        // khỏi activity. Trước đây nếu user rời màn hình sau khi rate card,
        // sau khi delay 400ms hết hạn card vẫn nhảy tự động → behavior khó hiểu
        // khi user quay lại sau một thời gian dài.
        advanceHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // BUG B17 FIX: cancel any pending advance callbacks to avoid leaking
        // this Activity and crashing if the user rotates / leaves the screen.
        advanceHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Triggers a quest progress event (SRS_REVIEW) on the backend.
     *  BUG B4 FIX: backend đọc `event` / `value` (chứ không phải `type` / `amount`).
     *  (BUG #19 FIX: comment tiếng Pháp → tiếng Việt.)
     */
    private void triggerSrsQuestEvent() {
        Map<String, Object> body = new HashMap<>();
        body.put("event", "SRS_REVIEW");
        body.put("value", 1);
        apiService.triggerQuestEvent(body).enqueue(new Callback<ApiResponse<Object>>() {
            @Override public void onResponse(Call<ApiResponse<Object>> c, Response<ApiResponse<Object>> r) {}
            @Override public void onFailure(Call<ApiResponse<Object>> c, Throwable t) {}
        });
    }

    private void showRatingButtons() {
        btnAgain.setVisibility(View.VISIBLE);
        btnHard.setVisibility(View.VISIBLE);
        btnGood.setVisibility(View.VISIBLE);
        btnEasy.setVisibility(View.VISIBLE);
    }

    private void hideRatingButtons() {
        btnAgain.setVisibility(View.GONE);
        btnHard.setVisibility(View.GONE);
        btnGood.setVisibility(View.GONE);
        btnEasy.setVisibility(View.GONE);
    }

    private void showEmptyState() {
        tvFront.setText("🎉 Không còn thẻ nào cần ôn!\nQuay lại sau nhé.");
        tvProgress.setText("0 / 0");
        cardFront.setVisibility(View.VISIBLE);
        cardBack.setVisibility(View.GONE);
        // 6.5 FIX: thay vì để user kẹt ở màn hình trắng, dialog cho phép
        // quay về deck picker / home khi hết thẻ.
        hideRatingButtons();
        showFinishOptions("Phiên ôn đã kết thúc",
                "Không còn thẻ nào để ôn tại thời điểm này.");
    }

    private void showCompletionState() {
        tvFront.setText(String.format(Locale.getDefault(),
                "✅ Đã hoàn thành phiên ôn!\n%d thẻ đã ôn", reviewedCount));
        tvProgress.setText(String.format(Locale.getDefault(), "%d thẻ", reviewedCount));
        cardFront.setVisibility(View.VISIBLE);
        cardBack.setVisibility(View.GONE);
        hideRatingButtons();
        Toast.makeText(this, "Tuyệt vời! Phiên ôn SRS đã xong 🎉", Toast.LENGTH_LONG).show();
        // 6.5 FIX: hỏi user muốn về đâu thay vì để ở màn hình trống.
        showFinishOptions("Hoàn thành phiên ôn!",
                String.format(Locale.getDefault(), "Bạn đã ôn %d thẻ. 🎉", reviewedCount));
    }

    /**
     * 6.5 FIX: dialog cuối phiên SRS với 2 lựa chọn: quay về deck picker
     * hoặc về MainActivity. Tránh trường hợp user kẹt ở màn hình "hết thẻ".
     */
    private void showFinishOptions(String title, String message) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("📚 Chọn bộ thẻ khác", (d, w) -> {
                    startActivity(new android.content.Intent(this, SrsDeckPickerActivity.class));
                    finish();
                })
                .setNegativeButton("🏠 Về trang chủ", (d, w) -> {
                    android.content.Intent i = new android.content.Intent(this, MainActivity.class);
                    i.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                    finish();
                })
                .setCancelable(false)
                .show();
    }
}
