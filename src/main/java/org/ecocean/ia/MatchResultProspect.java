package org.ecocean.ia;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.ecocean.Util;

public class MatchResultProspect implements java.io.Serializable {
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
}
