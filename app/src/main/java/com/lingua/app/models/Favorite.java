package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class Favorite {
    @SerializedName("id") public long id;
    @SerializedName("type") public String type;            // WORD | GRAMMAR | SENTENCE
    @SerializedName("item_id") public long itemId;
    @SerializedName("created_at") public String createdAt;

    // Word fields (when type=WORD)
    @SerializedName("text") public String text;
    @SerializedName("reading") public String reading;
    @SerializedName("romaji") public String romaji;
    @SerializedName("meaning") public String meaning;
    @SerializedName("level") public String level;
    @SerializedName("language_code") public String languageCode;
    @SerializedName("audio_url") public String audioUrl;

    // Grammar fields (when type=GRAMMAR)
    @SerializedName("title") public String title;
    @SerializedName("structure") public String structure;
    @SerializedName("explanation") public String explanation;

    public String getDisplayTitle() {
        if (text != null && !text.isEmpty()) return text;
        if (title != null && !title.isEmpty()) return title;
        return "Item #" + itemId;
    }

    public String getDisplaySubtitle() {
        if ("WORD".equalsIgnoreCase(type)) {
            StringBuilder sb = new StringBuilder();
            if (reading != null && !reading.isEmpty()) sb.append(reading);
            if (meaning != null && !meaning.isEmpty()) {
                if (sb.length() > 0) sb.append(" — ");
                sb.append(meaning);
            }
            return sb.toString();
        }
        if ("GRAMMAR".equalsIgnoreCase(type)) {
            return structure != null ? structure : (explanation != null ? explanation : "");
        }
        return "";
    }
}
