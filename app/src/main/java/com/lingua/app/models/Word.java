package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class Word {
    @SerializedName("id") private long id;
    @SerializedName("text") private String text;
    @SerializedName("reading") private String reading;
    @SerializedName("romaji") private String romaji;
    @SerializedName("pos") private String pos;
    @SerializedName("jlpt_level") private String jlptLevel;
    @SerializedName("cefr_level") private String cefrLevel;
    @SerializedName("hsk_level") private String hskLevel;
    @SerializedName("audio_url") private String audioUrl;
    @SerializedName("language_code") private String languageCode;
    @SerializedName("language_name") private String languageName;
    @SerializedName("meanings") private List<WordMeaning> meanings;

    // Getters
    public long getId() { return id; }
    public String getText() { return text; }
    public String getReading() { return reading; }
    public String getRomaji() { return romaji; }
    public String getPos() { return pos; }
    public String getJlptLevel() { return jlptLevel; }
    public String getCefrLevel() { return cefrLevel; }
    public String getHskLevel() { return hskLevel; }
    public String getAudioUrl() { return audioUrl; }
    public String getLanguageCode() { return languageCode; }
    public String getLanguageName() { return languageName; }
    public List<WordMeaning> getMeanings() { return meanings; }

    public String getLevel() {
        if (jlptLevel != null && !jlptLevel.isEmpty()) return jlptLevel;
        if (cefrLevel != null && !cefrLevel.isEmpty()) return cefrLevel;
        if (hskLevel != null && !hskLevel.isEmpty()) return hskLevel;
        return "";
    }

    public String getDisplayReading() {
        if ("zh".equals(languageCode) && romaji != null) return romaji; // pinyin
        if ("ja".equals(languageCode) && reading != null) return reading; // hiragana
        return reading != null ? reading : "";
    }

    public static class WordMeaning {
        @SerializedName("meaning") private String meaning;
        @SerializedName("example") private String example;
        @SerializedName("example_translation") private String exampleTranslation;
        @SerializedName("example_audio") private String exampleAudio;

        public String getMeaning() { return meaning; }
        public String getExample() { return example; }
        public String getExampleTranslation() { return exampleTranslation; }
        public String getExampleAudio() { return exampleAudio; }
    }
}
