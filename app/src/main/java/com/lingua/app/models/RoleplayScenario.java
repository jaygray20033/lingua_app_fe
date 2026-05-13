package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class RoleplayScenario {
    @SerializedName("id") public int id;
    @SerializedName("title") public String title;
    @SerializedName("level_code") public String levelCode;
    @SerializedName("goal") public String goal;
    @SerializedName("is_premium") public int isPremium;
    @SerializedName("flag_emoji") public String flagEmoji;
    @SerializedName("lang_code") public String langCode;
}
