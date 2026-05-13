package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class DeckCards {
    @SerializedName("dueCards") public List<Card> dueCards;
    @SerializedName("newCards") public List<Card> newCards;
    @SerializedName("totalDue") public int totalDue;
    @SerializedName("totalNew") public int totalNew;
}
