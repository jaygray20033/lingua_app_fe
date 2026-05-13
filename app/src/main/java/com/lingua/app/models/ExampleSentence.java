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
    @SerializedName("level") public String level;
    @SerializedName("title") public String title;

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
