package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class ExplanationResult {
    @SerializedName("explanation") public String explanation;
    @SerializedName("tokensUsed") public int tokensUsed;
}
