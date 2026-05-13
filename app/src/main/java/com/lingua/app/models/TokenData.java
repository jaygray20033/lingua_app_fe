package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class TokenData {
    @SerializedName("accessToken") public String accessToken;
    @SerializedName("refreshToken") public String refreshToken;
}
