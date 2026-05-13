package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class Exercise {
    @SerializedName("id") public long id;
    @SerializedName("type") public String type;
    @SerializedName("prompt_json") public Object promptJson;
    @SerializedName("answer_json") public Object answerJson;
    @SerializedName("hint_json") public Object hintJson;
    @SerializedName("audio_url") public String audioUrl;
    @SerializedName("image_url") public String imageUrl;
}
