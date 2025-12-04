package org.ecocean.ia;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.ecocean.Util;

public class MatchResultProspect implements java.io.Serializable, Comparable<MatchResultProspect> {
    private Annotation annotation;
    private double score = 0.0d;
    private String scoreType;
    private MediaAsset asset;
    private MatchResult matchResult;

    public MatchResultProspect() {}

    public MatchResultProspect(Annotation ann) {
        this();
        this.annotation = ann;
    }

    public MatchResultProspect(Annotation ann, double score, String type) {
        this();
        this.annotation = ann;
        this.score = score;
        this.scoreType = type;
    }

    public double getScore() {
        return score;
    }

    public String getType() {
        return scoreType;
    }

    public boolean isType(String type) {
        if (type == null) return (this.scoreType == null);
        return type.equals(this.scoreType);
    }

    public String toString() {
        return scoreType + ": " + score + " on " + annotation;
    }

    public JSONObject jsonForApiGet() {
        JSONObject rtn = new JSONObject();
        JSONObject annj = new JSONObject();

        // TODO really fill out all we need for annotation! (shepherd)
        annj.put("id", annotation.getId());
        rtn.put("annotation", annj);
        rtn.put("score", score);
        // skipping scoreType since this is currently only used filtered by scoreType already
        return rtn;
    }

    @Override public int compareTo(MatchResultProspect other) {
        // we invert this so higher score is first
        return Double.compare(other.score, this.score);
    }
}
