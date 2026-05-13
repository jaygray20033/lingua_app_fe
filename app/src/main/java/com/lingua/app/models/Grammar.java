package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class Grammar {
    @SerializedName("id") public long id;
    @SerializedName("title") public String title;
    @SerializedName("structure") public String structure;
    @SerializedName("meaning") public String meaning;
    @SerializedName("explanation") public String explanation;
    @SerializedName("level") public String level;          // N4, A1, HSK2…
    @SerializedName("level_code") public String levelCode;
    @SerializedName("language_code") public String languageCode;
    @SerializedName("lang_code") public String langCode;
    @SerializedName("flag_emoji") public String flagEmoji;
    @SerializedName("favorite") public boolean favorite;

    public String getDisplayLevel() {
        if (level != null && !level.isEmpty()) return level;
        return levelCode != null ? levelCode : "";
    }

    public String getLanguage() {
        if (languageCode != null) return languageCode;
        return langCode != null ? langCode : "";
    }
}
