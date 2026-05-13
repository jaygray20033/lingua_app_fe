package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class Card {
    @SerializedName("id") public long id;
    @SerializedName("deck_id") public long deckId;
    @SerializedName("front_text") public String frontText;
    @SerializedName("back_text") public String backText;
    @SerializedName("audio_url") public String audioUrl;
    @SerializedName("state") public String state;
    @SerializedName("ease_factor") public float easeFactor;
    @SerializedName("interval_days") public int intervalDays;
    @SerializedName("repetitions") public int repetitions;
}
