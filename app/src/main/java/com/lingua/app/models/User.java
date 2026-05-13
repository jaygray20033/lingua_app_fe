package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class User {
    @SerializedName("id") public long id;
    @SerializedName("email") public String email;
    @SerializedName("display_name") public String displayName;
    @SerializedName("avatar_url") public String avatarUrl;
    @SerializedName("role") public String role;
    @SerializedName("status") public String status;
    @SerializedName("native_language_code") public String nativeLanguageCode;
}
