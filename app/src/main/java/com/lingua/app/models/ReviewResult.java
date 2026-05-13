package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class ReviewResult {
    @SerializedName("ease_factor") public float easeFactor;
    @SerializedName("interval_days") public int intervalDays;
    @SerializedName("state") public String state;
    @SerializedName("next_review_at") public String nextReviewAt;
}
