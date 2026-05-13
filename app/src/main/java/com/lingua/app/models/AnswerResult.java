package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class AnswerResult {
    @SerializedName("isCorrect") public boolean isCorrect;
    @SerializedName("correctAnswer") public Object correctAnswer;
    @SerializedName("noHearts") public boolean noHearts;
}
