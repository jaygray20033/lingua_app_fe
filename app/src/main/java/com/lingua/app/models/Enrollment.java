package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class Enrollment {
    @SerializedName("id") public long id;
    @SerializedName("course_id") public long courseId;
    @SerializedName("user_id") public long userId;
    @SerializedName("course_title") public String courseTitle;
    @SerializedName("title") public String title;          // alternate
    @SerializedName("level_code") public String levelCode;
    @SerializedName("flag_emoji") public String flagEmoji;
    @SerializedName("lang_code") public String langCode;
    @SerializedName("thumbnail_url") public String thumbnailUrl;
    @SerializedName("progress") public int progress;       // 0..100
    @SerializedName("completed_lessons") public int completedLessons;
    @SerializedName("total_lessons") public int totalLessons;
    @SerializedName("next_lesson_id") public Long nextLessonId;
    @SerializedName("next_lesson_title") public String nextLessonTitle;
    @SerializedName("enrolled_at") public String enrolledAt;
    @SerializedName("last_accessed_at") public String lastAccessedAt;

    public String getDisplayTitle() {
        if (courseTitle != null && !courseTitle.isEmpty()) return courseTitle;
        return title != null ? title : ("Course #" + courseId);
    }
}
