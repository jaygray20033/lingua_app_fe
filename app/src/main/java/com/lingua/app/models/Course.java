package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Course {
    @SerializedName("id") public long id;
    @SerializedName("title") public String title;
    @SerializedName("description") public String description;
    @SerializedName("level_code") public String levelCode;
    @SerializedName("certification") public String certification;
    @SerializedName("thumbnail_url") public String thumbnailUrl;
    @SerializedName("lang_code") public String langCode;
    @SerializedName("flag_emoji") public String flagEmoji;
    @SerializedName("total_lessons") public int totalLessons;
    @SerializedName("rating") public float rating;
    @SerializedName("is_enrolled") public int isEnrolled;
    @SerializedName("sections") public List<CourseSection> sections;

    public static class CourseSection {
        @SerializedName("id") public long id;
        @SerializedName("title") public String title;
        @SerializedName("units") public List<CourseUnit> units;
    }
    public static class CourseUnit {
        @SerializedName("id") public long id;
        @SerializedName("title") public String title;
        @SerializedName("xp_reward") public int xpReward;
        @SerializedName("lesson_count") public int lessonCount;
        @SerializedName("completed_lessons") public int completedLessons;
    }
}
