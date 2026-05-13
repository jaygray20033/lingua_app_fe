package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class LeaderboardEntry {
    @SerializedName("id") public long id;
    @SerializedName("display_name") public String displayName;
    @SerializedName("avatar_url") public String avatarUrl;
    @SerializedName("xp") public long xp;
    @SerializedName("rank") public int rank;
}
