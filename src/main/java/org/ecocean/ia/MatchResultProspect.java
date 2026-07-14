package org.ecocean.ia;

import java.util.HashSet;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
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

    public MatchResultProspect(Annotation ann, double score, String type, MediaAsset asset) {
        this();
        this.annotation = ann;
        this.score = score;
        this.scoreType = type;
        this.asset = asset;
    }

    /**
     * Attach a PairX inspection MediaAsset to this prospect. Used by
     * {@link MatchInspectionPairxEnricher} in Phase C to enrich
     * prospects after the MatchResult has been persisted (empty-match-
     * prospects design Track 2 C13: PairX is now non-blocking and
     * runs without holding the outer Shepherd across HTTP).
     */
    public void setAsset(MediaAsset asset) {
        this.asset = asset;
    }

    public MediaAsset getAsset() {
        return asset;
    }

    public Annotation getAnnotation() {
        return annotation;
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

    public boolean isInProjects(Set<String> projectIds, Shepherd myShepherd) {
        // if we have no projects to filter on, we consider this to be in it
        if (Util.collectionIsEmptyOrNull(projectIds)) return true;
        if (annotation == null) return false;
        Encounter enc = annotation.findEncounter(myShepherd);
        if (enc == null) return false;
        return enc.isInProjects(projectIds, myShepherd);
    }

    public String toString() {
        return scoreType + "=" + score + " on " + annotation + " for " + matchResult;
    }

    public JSONObject jsonForApiGet(Shepherd myShepherd) {
        return jsonForApiGet(myShepherd, null);
    }

    public JSONObject jsonForApiGet(Shepherd myShepherd,
        java.util.Map<String, org.ecocean.Encounter> encByAnnId) {
        JSONObject rtn = new JSONObject();

        rtn.put("annotation", MatchResult.annotationDetails(annotation, myShepherd, encByAnnId));
        rtn.put("score", score);
        // skipping scoreType since this is currently only used filtered by scoreType already
        if (asset != null) {
            JSONObject aj = asset.toSimpleJSONObject();
            aj.put("url", asset.webURL()); // we have no "safe" url
            rtn.put("asset", aj);
        }
        return rtn;
    }

    // used in sorting
    @Override public int compareTo(MatchResultProspect other) {
        // we invert this so higher score is first
        int comp = Double.compare(other.score, this.score);
        // if the scores are the same (comp == 0), we want to ensure consistent/deterministic
        // ordering (otherwise tied scores come back random order), so we use annot id
        if ((comp == 0) && (this.annotation != null) && (this.annotation.getId() != null) && (other.annotation != null))
            return this.annotation.getId().compareTo(other.annotation.getId());
        // scores are *not* equal, so we just let comparison stand as-is
        return comp;
    }
}
