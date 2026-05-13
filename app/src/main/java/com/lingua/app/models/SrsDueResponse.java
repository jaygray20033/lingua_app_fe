package com.lingua.app.models;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response for GET /api/srs/due
 * Backend may return either a flat list of reviews or grouped by state.
 */
public class SrsDueResponse {
    @SerializedName("dueCards") public List<SrsCard> dueCards;
    @SerializedName("newCards") public List<SrsCard> newCards;
    @SerializedName("learningCards") public List<SrsCard> learningCards;
    @SerializedName("reviews") public List<SrsCard> reviews;   // alternative shape
    @SerializedName("totalDue") public int totalDue;
    @SerializedName("totalNew") public int totalNew;
    @SerializedName("totalLearning") public int totalLearning;
}
