package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class LessonData {
    @SerializedName("lesson") public LessonInfo lesson;
    @SerializedName("exercises") public List<Exercise> exercises;

    public static class LessonInfo {
        @SerializedName("id") public long id;
        @SerializedName("title") public String title;
        @SerializedName("xp_reward") public int xpReward;
        @SerializedName("heart_cost_per_error") public int heartCostPerError;
    }
}
