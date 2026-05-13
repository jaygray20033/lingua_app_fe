package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class GamificationStats {
    @SerializedName("total_xp") public long totalXp;
    @SerializedName("level") public int level;
    @SerializedName("gems") public int gems;
    @SerializedName("hearts") public int hearts;
    @SerializedName("current_streak") public int currentStreak;
    @SerializedName("streak_count") public int streakCount;
    @SerializedName("longest_streak") public int longestStreak;
    @SerializedName("todayXp") public int todayXp;
    @SerializedName("today_xp") public int todayXpAlt;
    @SerializedName("goalMet") public int goalMet;
    @SerializedName("daily_goal") public int dailyGoal;
    @SerializedName("dailyGoal") public int dailyGoalAlt;
    @SerializedName("streak_freeze_count") public int streakFreezeCount;
    @SerializedName("streakFreezeCount") public int streakFreezeCountAlt;

    // ===== Aggregated metrics for the Statistics screen =====
    @SerializedName("words_learned") public int wordsLearned;
    @SerializedName("wordsLearned") public int wordsLearnedAlt;
    @SerializedName("lessons_completed") public int lessonsCompleted;
    @SerializedName("lessonsCompleted") public int lessonsCompletedAlt;
    @SerializedName("accuracy_percent") public int accuracyPercent;
    @SerializedName("accuracyPercent") public int accuracyPercentAlt;
    @SerializedName("srs_reviewed") public int srsReviewed;
    @SerializedName("srsReviewed") public int srsReviewedAlt;

    // 7-day XP series. Server may use either snake_case or camelCase.
    @SerializedName("weekly_xp") public int[] weeklyXp;
    @SerializedName("weeklyXp") public int[] weeklyXpAlt;
    @SerializedName("daily_xp_history") public int[] dailyXpHistory;

    // 30-day streak calendar. activity[0] = today, activity[29] = 30 days ago.
    @SerializedName("streak_calendar") public int[] streakCalendar;
    @SerializedName("streakCalendar") public int[] streakCalendarAlt;
    @SerializedName("activity") public int[] activity;

    /** Helper: returns whichever streak field is populated */
    public int getStreak() {
        return currentStreak > 0 ? currentStreak : streakCount;
    }

    public int getBestStreak() {
        return Math.max(longestStreak, getStreak());
    }

    public int getDailyGoal() {
        if (dailyGoal > 0) return dailyGoal;
        return dailyGoalAlt;
    }

    public int getTodayXp() {
        return todayXp > 0 ? todayXp : todayXpAlt;
    }

    public int getStreakFreezeCount() {
        return streakFreezeCount > 0 ? streakFreezeCount : streakFreezeCountAlt;
    }

    public int getWordsLearned() {
        return wordsLearned > 0 ? wordsLearned : wordsLearnedAlt;
    }

    public int getLessonsCompleted() {
        return lessonsCompleted > 0 ? lessonsCompleted : lessonsCompletedAlt;
    }

    public int getAccuracyPercent() {
        return accuracyPercent > 0 ? accuracyPercent : accuracyPercentAlt;
    }

    public int getSrsReviewed() {
        return srsReviewed > 0 ? srsReviewed : srsReviewedAlt;
    }

    /** Returns the 7-day XP series, picking whichever field the server populated. */
    public int[] getWeeklyXp() {
        if (weeklyXp != null && weeklyXp.length > 0) return weeklyXp;
        if (weeklyXpAlt != null && weeklyXpAlt.length > 0) return weeklyXpAlt;
        if (dailyXpHistory != null && dailyXpHistory.length > 0) return dailyXpHistory;
        return null;
    }

    /** Returns the 30-day streak calendar, picking whichever field the server populated. */
    public int[] getStreakCalendar() {
        if (streakCalendar != null && streakCalendar.length > 0) return streakCalendar;
        if (streakCalendarAlt != null && streakCalendarAlt.length > 0) return streakCalendarAlt;
        if (activity != null && activity.length > 0) return activity;
        return null;
    }
}
