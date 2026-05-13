package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Character {
    @SerializedName("id") public long id;
    @SerializedName("char") public String character;
    @SerializedName("type") public String type;
    @SerializedName("stroke_count") public int strokeCount;
    @SerializedName("reading") public String reading;
    @SerializedName("meaning_vi") public String meaningVi;
    // 3.1 Stroke Order: danh sách path SVG (KanjiVG) — backend trả về mảng string
    @SerializedName("strokes") public List<String> strokes;
    @SerializedName("relatedWords") public List<RelatedWord> relatedWords;

    public static class RelatedWord {
        @SerializedName("id") public long id;
        @SerializedName("word") public String word;
        @SerializedName("reading") public String reading;
        @SerializedName("meaning_vi") public String meaningVi;
    }
}
