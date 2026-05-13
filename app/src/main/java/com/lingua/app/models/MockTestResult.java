package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MockTestResult {
    @SerializedName("score_percent") public int scorePercent;
    @SerializedName("scorePercent") public int scorePercentAlt;
    @SerializedName("correct_count") public int correctCount;
    @SerializedName("total") public int total;
    @SerializedName("passed") public boolean passed;
    @SerializedName("xp_earned") public int xpEarned;
    @SerializedName("duration_sec") public int durationSec;
    @SerializedName("answers") public List<AnswerReview> answers;

    public int getScore() {
        return scorePercent > 0 ? scorePercent : scorePercentAlt;
    }

    public static class AnswerReview {
        @SerializedName("question_id") public long questionId;
        @SerializedName("user_answer") public String userAnswer;
        @SerializedName("correct_answer") public String correctAnswer;
        @SerializedName("is_correct") public boolean isCorrect;
        @SerializedName("explanation") public String explanation;
    }
}
