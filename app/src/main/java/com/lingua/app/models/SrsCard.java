package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

/**
 * Represents an SRS review item returned by GET /api/srs/due.
 * Each item is a "review row" that wraps an underlying card.
 */
public class SrsCard {
    // SRS review row id used to submit review (POST /api/srs/{reviewId}/review)
    @SerializedName("review_id") public long reviewId;
    @SerializedName("id") public long id; // alias when backend returns id == reviewId
    @SerializedName("card_id") public long cardId;
    @SerializedName("deck_id") public long deckId;

    // Card content
    @SerializedName("front_text") public String frontText;
    @SerializedName("back_text") public String backText;
    @SerializedName("front") public String front;       // alternate naming
    @SerializedName("back") public String back;
    @SerializedName("audio_url") public String audioUrl;
    @SerializedName("image_url") public String imageUrl;

    // SM-2 fields
    @SerializedName("state") public String state;       // NEW / LEARNING / REVIEW / RELEARNING
    @SerializedName("ease_factor") public float easeFactor;
    @SerializedName("interval_days") public int intervalDays;
    @SerializedName("repetitions") public int repetitions;
    @SerializedName("next_review_at") public String nextReviewAt;
    @SerializedName("due_at") public String dueAt;

    public long getReviewId() {
        return reviewId > 0 ? reviewId : id;
    }

    public String getFrontText() {
        if (frontText != null && !frontText.isEmpty()) return frontText;
        return front != null ? front : "";
    }

    public String getBackText() {
        if (backText != null && !backText.isEmpty()) return backText;
        return back != null ? back : "";
    }

    public String getDisplayState() {
        if (state == null || state.isEmpty()) return "NEW";
        return state;
    }
}
