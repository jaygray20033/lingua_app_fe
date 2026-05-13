package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class DailyQuest {
    @SerializedName("id") public long id;
    @SerializedName("quest_code") public String questCode;
    @SerializedName("description") public String description;
    @SerializedName("target") public int target;
    @SerializedName("progress") public int progress;
    @SerializedName("reward_gems") public int rewardGems;
    @SerializedName("completed") public int completed;
    @SerializedName("claimed_at") public String claimedAt;
}
