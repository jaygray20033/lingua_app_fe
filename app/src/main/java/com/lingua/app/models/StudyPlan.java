package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class StudyPlan {
    @SerializedName("weeklyPlan") public List<DayPlan> weeklyPlan;

    public static class DayPlan {
        @SerializedName("day") public String day;
        @SerializedName("focus") public String focus;
        @SerializedName("dailyTasks") public List<String> dailyTasks;
    }
}
