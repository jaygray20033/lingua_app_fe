package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response for GET /api/courses/{id}/path
 * Sections → Units → Lessons.
 */
public class CoursePath {
    @SerializedName("course_id") public long courseId;
    @SerializedName("title") public String title;
    @SerializedName("level_code") public String levelCode;
    @SerializedName("is_enrolled") public int isEnrolled;
    @SerializedName("progress") public int progress;
    @SerializedName("sections") public List<PathSection> sections;

    public static class PathSection {
        @SerializedName("id") public long id;
        @SerializedName("title") public String title;
        @SerializedName("description") public String description;
        @SerializedName("order_index") public int orderIndex;
        @SerializedName("units") public List<PathUnit> units;
    }

    public static class PathUnit {
        @SerializedName("id") public long id;
        @SerializedName("title") public String title;
        @SerializedName("communication_goal") public String communicationGoal;
        @SerializedName("xp_reward") public int xpReward;
        @SerializedName("order_index") public int orderIndex;
        @SerializedName("lessons") public List<PathLesson> lessons;
    }

    public static class PathLesson {
        @SerializedName("id") public long id;
        @SerializedName("title") public String title;
        @SerializedName("type") public String type;        // PRACTICE / DIALOGUE / STORY...
        @SerializedName("xp_reward") public int xpReward;
        @SerializedName("order_index") public int orderIndex;
        @SerializedName("status") public String status;    // COMPLETED / IN_PROGRESS / LOCKED / AVAILABLE
        @SerializedName("locked") public boolean locked;
        @SerializedName("completed") public boolean completed;
        @SerializedName("score_percent") public int scorePercent;

        public String getStatusEmoji() {
            if (completed || "COMPLETED".equalsIgnoreCase(status)) return "✅";
            if (locked || "LOCKED".equalsIgnoreCase(status)) return "🔒";
            if ("IN_PROGRESS".equalsIgnoreCase(status)) return "🔓";
            return "🔓";
        }

        public boolean isLocked() {
            return locked || "LOCKED".equalsIgnoreCase(status);
        }
    }
}
