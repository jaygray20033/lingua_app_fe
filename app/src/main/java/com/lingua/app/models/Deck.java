package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;

public class Deck {
    @SerializedName("id") public long id;
    @SerializedName("name") public String name;
    @SerializedName("description") public String description;
    @SerializedName("language_code") public String languageCode;
    @SerializedName("flag_emoji") public String flagEmoji;
    @SerializedName("card_count") public int cardCount;
    @SerializedName("reviewed_count") public int reviewedCount;
    @SerializedName("is_public") public int isPublic;
    @SerializedName("owner_id") public long ownerId;
}
