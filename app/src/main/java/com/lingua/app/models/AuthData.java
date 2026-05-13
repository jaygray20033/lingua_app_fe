package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class AuthData {
    @SerializedName("userId") public long userId;
    @SerializedName("accessToken") public String accessToken;
    @SerializedName("refreshToken") public String refreshToken;
    @SerializedName("user") public User user;
}
