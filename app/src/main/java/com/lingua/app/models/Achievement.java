package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class Achievement {
    @SerializedName("id") public long id;
    @SerializedName("code") public String code;
    @SerializedName("title") public String title;
    @SerializedName("description") public String description;
    @SerializedName("icon") public String icon;
    @SerializedName("rarity") public String rarity;
    @SerializedName("unlocked") public boolean unlocked;
    @SerializedName("unlockedAt") public String unlockedAt;
    @SerializedName("progress") public int progress;
}
