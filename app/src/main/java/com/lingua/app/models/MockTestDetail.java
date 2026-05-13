package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MockTestDetail {
    @SerializedName("id") public long id;
    @SerializedName("title") public String title;
    @SerializedName("type") public String type;
    @SerializedName("level_code") public String levelCode;
    @SerializedName("duration_min") public int durationMin;
    @SerializedName("total_questions") public int totalQuestions;
    @SerializedName("pass_score") public int passScore;
    @SerializedName("flag_emoji") public String flagEmoji;
    @SerializedName("sections") public List<TestSection> sections;

    public static class TestSection {
        @SerializedName("id") public long id;
        @SerializedName("title") public String title;
        @SerializedName("description") public String description;
        @SerializedName("order_index") public int orderIndex;
        @SerializedName("questions") public List<TestQuestion> questions;
    }

    public static class TestQuestion {
        @SerializedName("id") public long id;
        @SerializedName("type") public String type;        // MCQ / FILL / LISTEN ...
        @SerializedName("prompt") public String prompt;
        @SerializedName("question") public String question;
        @SerializedName("audio_url") public String audioUrl;
        @SerializedName("image_url") public String imageUrl;
        @SerializedName("options") public List<String> options;
        @SerializedName("order_index") public int orderIndex;

        public String getDisplayPrompt() {
            if (prompt != null && !prompt.isEmpty()) return prompt;
            return question != null ? question : "";
        }
    }
}
