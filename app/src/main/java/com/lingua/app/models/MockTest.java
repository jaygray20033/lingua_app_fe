package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class MockTest {
    @SerializedName("id") public long id;
    @SerializedName("title") public String title;
    @SerializedName("description") public String description;
    @SerializedName("total_questions") public int totalQuestions;
    @SerializedName("pass_score") public int passScore;
    @SerializedName("flag_emoji") public String flagEmoji;
    @SerializedName("lang_code") public String langCode;
}
