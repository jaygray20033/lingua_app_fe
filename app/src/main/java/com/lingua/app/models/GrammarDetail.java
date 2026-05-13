package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class GrammarDetail {
    @SerializedName("id") public long id;
    @SerializedName("title") public String title;
    @SerializedName("structure") public String structure;
    @SerializedName("meaning") public String meaning;
    @SerializedName("explanation") public String explanation;
    @SerializedName("level") public String level;
    @SerializedName("level_code") public String levelCode;
    @SerializedName("language_code") public String languageCode;
    @SerializedName("examples") public List<GrammarExample> examples;
    @SerializedName("exercises") public List<Exercise> exercises;
    @SerializedName("favorite") public boolean favorite;

    public static class GrammarExample {
        @SerializedName("id") public long id;
        @SerializedName("sentence") public String sentence;
        @SerializedName("translation") public String translation;
        @SerializedName("audio_url") public String audioUrl;
        @SerializedName("note") public String note;
    }
}
