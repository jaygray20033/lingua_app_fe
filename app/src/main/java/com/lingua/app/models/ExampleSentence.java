package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class ExampleSentence {
    @SerializedName("id") public long id;
    @SerializedName("word_id") public long wordId;
    @SerializedName("sentence") public String sentence;
    @SerializedName("text") public String text;            // alt
    @SerializedName("translation") public String translation;
    @SerializedName("translation_vi") public String translationVi;
    @SerializedName("translation_en") public String translationEn;
    @SerializedName("audio_url") public String audioUrl;
    @SerializedName("language_code") public String languageCode;
    // FIX: backend examples.controller returns 'lang_code' not 'language_code'
    @SerializedName("lang_code") public String langCode;
    @SerializedName("level") public String level;
    // FIX: backend examples.controller returns 'level_code' field
    @SerializedName("level_code") public String levelCode;
    @SerializedName("title") public String title;
    // FIX: backend returns 'word' and 'word_reading' for shadowing display
    @SerializedName("word") public String word;
    @SerializedName("word_reading") public String wordReading;

    /**
     * FIX: get the language code from whichever field is populated.
     * Backend /api/examples returns 'lang_code', local fallback uses 'languageCode'.
     */
    public String getLanguageCode() {
        if (languageCode != null && !languageCode.isEmpty()) return languageCode;
        return langCode != null ? langCode : "ja";
    }

    /**
     * FIX: get level from whichever field is populated.
     */
    public String getLevel() {
        if (level != null && !level.isEmpty()) return level;
        return levelCode != null ? levelCode : "";
    }

    public String getSentence() {
        if (sentence != null && !sentence.isEmpty()) return sentence;
        return text != null ? text : "";
    }

    public String getTranslation() {
        if (translation != null && !translation.isEmpty()) return translation;
        if (translationVi != null && !translationVi.isEmpty()) return translationVi;
        return translationEn != null ? translationEn : "";
    }
}
