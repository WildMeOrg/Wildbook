package org.ecocean.ia;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.api.UploadedFiles;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.identity.IdentityServiceLog;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.URLAssetStore;
import org.ecocean.MarkedIndividual;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

public class MatchResult implements java.io.Serializable {
    private String id;
    private long created;
    private Task task;
    private Set<MatchResultProspect> prospects;
    private Annotation queryAnnotation;
    private int numberCandidates = 0;
    // not sure we really *need* true fk link to these annots
    // they might be gone now and will we ever use this?
    // so for now we just populate numberCandidates
    private Set<Annotation> candidates;
    // fallback number to cutoff number of prospects to return
    public static final int DEFAULT_PROSPECTS_CUTOFF = 100;

    public MatchResult() {
        id = Util.generateUUID();
        created = System.currentTimeMillis();
    }

    public MatchResult(Task task) {
        this();
        this.task = task;
    }

    public MatchResult(IdentityServiceLog isLog, Shepherd myShepherd)
    throws IOException {
        this();
        this.createFromIdentityServiceLog(isLog, myShepherd);
    }

    public int getNumberCandidates() {
        return numberCandidates;
    }

    public void createFromIdentityServiceLog(IdentityServiceLog isLog, Shepherd myShepherd)
    throws IOException {
        if (isLog == null) throw new IOException("log passed is null");
        String taskId = isLog.getTaskID();
        this.task = myShepherd.getTask(taskId);
        if (this.task == null) throw new IOException("task is null for taskId=" + taskId);
        JSONObject res = isLog.getJsonResult();
        if (res == null) {
            System.out.println("ERROR: getJsonResult() failed on " + isLog + " with status=" +
                isLog.getStatusJson());
            throw new IOException("could not get json result");
        }
        if (res.optJSONArray("query_annot_uuid_list") == null)
            throw new IOException("no query annot list");
        if (res.getJSONArray("query_annot_uuid_list").length() < 1)
            throw new IOException("empty query annot list");
        // for now we are assuming a single query annot. sorrynotsorry.
        String queryAnnotId = IBEISIA.fromFancyUUID(res.getJSONArray(
            "query_annot_uuid_list").optJSONObject(0));
        this.queryAnnotation = getAnnotationFromAcmId(queryAnnotId, myShepherd);
        if (this.queryAnnotation == null)
            throw new IOException("failed to load query annot from id=" + queryAnnotId);
        if (res.optJSONObject("cm_dict") == null)
            throw new IOException("no cm_dict found in " + res);
        // results is the real scores (etc) we are looking for.... finally!
        JSONObject results = res.getJSONObject("cm_dict").optJSONObject(queryAnnotId);
        if (results == null) throw new IOException("no actual results found");
        // see note at top about true annot list of candidates vs number
        if (res.optJSONArray("database_annot_uuid_list") != null)
            this.numberCandidates = res.getJSONArray("database_annot_uuid_list").length();
/*
        annot_score_list <=> dannot_uuid_list
        score_list is for indiv scores but on dannot_uuid_list (same length)
        name_score_list <=> unique_name_uuid_list ???
 */
        this.prospects = new HashSet<MatchResultProspect>();
        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
            results.optJSONArray("annot_score_list"), results.optJSONArray("dannot_extern_list"),
            results.optString("dannot_extern_reference", null), myShepherd);
        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
            results.optJSONArray("score_list"), results.optJSONArray("dannot_extern_list"),
            results.optString("dannot_extern_reference", null), myShepherd);
        System.out.println("[DEBUG] createFromIdentityServiceLog() created " + this);
    }

    // must initialize this.propsects first!!
    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
        JSONArray externs, String externRef, Shepherd myShepherd)
    throws IOException {
        if ((annotIds == null) || (scores == null))
            throw new IOException("null annotIds or scores");
        if (annotIds.length() != scores.length())
            throw new IOException("mismatch in size of annotIds/scores");
        int num = 0;
        for (int i = 0; i < annotIds.length(); i++) {
            double score = scores.optDouble(i, -Double.MAX_VALUE);
            String id = IBEISIA.fromFancyUUID(annotIds.optJSONObject(i));
            Annotation ann = getAnnotationFromAcmId(id, myShepherd);
            if (ann == null) {
                System.out.println("WARNING: populateProspect failed to load annotId=" + id +
                    "; skipping; score=" + score);
                continue;
            }
            MediaAsset ma = null;
            // we only try if we have a true value in externs[i]
            if ((externs != null) && (externs.length() > i) && externs.optBoolean(i, false))
                ma = createInspectionHeatmapAsset(externRef, id, myShepherd);
            this.prospects.add(new MatchResultProspect(ann, score, type, ma));
            num++;
        }
        return num;
    }

    private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {
        if (acmId == null) return null;
        List<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmId, true);
        if ((anns == null) || (anns.size() < 1)) return null;
        return anns.get(0);
    }

    // if it exists, we just return the thing, other wise we attempt to create it
    public MediaAsset createInspectionHeatmapAsset(String externRef, String annotId,
        Shepherd myShepherd) {
        if (externRef == null) return null;
        String url = "/api/query/graph/match/thumb/?extern_reference=" + externRef;
        url += "&query_annot_uuid=" + this.queryAnnotation.getId();
        url += "&database_annot_uuid=" + annotId;
        url += "&version=heatmap";
        URL fullUrl = IBEISIA.iaURL(myShepherd.getContext(), url);
        File tmpFile = new File("/tmp/extern-" + this.id + "-" + externRef + "-" +
            this.queryAnnotation.getId() + "-" + annotId + ".jpg");
        System.out.println("[DEBUG] trying extern fetch url=" + fullUrl + " => " + tmpFile);
        MediaAsset ma = null;
        try {
            URLAssetStore.fetchFileFromURL(fullUrl, tmpFile);
            ma = UploadedFiles.makeMediaAsset(this.id, tmpFile, myShepherd);
            ma.addLabel("matchInspectionHeatmap");
            System.out.println("[INFO] createInspectionHeatmapAsset() fetched " + fullUrl +
                " and created " + ma);
            tmpFile.delete();
        } catch (Exception ex) {
            System.out.println(
                "[ERROR] createInspectionHeatmapAsset() asset creation failed using " + fullUrl +
                " => " + tmpFile + ": " + ex);
            ex.printStackTrace();
        }
        return ma;
    }

    public JSONObject getTaskParameters() {
        if (task == null) return null;
        return task.getParameters();
    }

    public JSONObject getTaskMatchingSetFilter() {
        if (task == null) return null;
        JSONObject params = task.getParameters();
        if (params == null) return null;
        return params.optJSONObject("matchingSetFilter");
    }

/*
    see note at top about candidates vs numberCandidates
    public int numberCandidates() {
        return Util.collectionSize(candidates);
    }
 */
    public int numberProspects() {
        return Util.collectionSize(prospects);
    }

    public Set<String> prospectScoreTypes() {
        Set<String> types = new HashSet<String>();

        if (numberProspects() == 0) return types;
        for (MatchResultProspect mrp : prospects) {
            types.add(mrp.getType());
        }
        return types;
    }

    // if cutoff < 0 then it will not be truncated at all
    public List<MatchResultProspect> prospectsSorted(String type, int cutoff) {
        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();

        if (numberProspects() == 0) return pros;
        for (MatchResultProspect mrp : prospects) {
            if (mrp.isType(type)) pros.add(mrp);
        }
        Collections.sort(pros);
        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
        return pros;
    }

    public JSONObject prospectsForApiGet(int cutoff, Shepherd myShepherd) {
        JSONObject sj = new JSONObject();

        for (String type : prospectScoreTypes()) {
            JSONArray jarr = new JSONArray();
            for (MatchResultProspect mrp : prospectsSorted(type, cutoff)) {
                jarr.put(mrp.jsonForApiGet(myShepherd));
            }
            sj.put(type, jarr);
        }
        return sj;
    }

    public JSONObject jsonForApiGet(int cutoff, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        rtn.put("id", id);
        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
        rtn.put("numberTotalProspects", numberProspects());
        rtn.put("numberCandidates", getNumberCandidates());
        rtn.put("created", Util.millisToISO8601String(created));
        rtn.put("prospects", prospectsForApiGet(cutoff, myShepherd));
        return rtn;
    }

    public static JSONObject annotationDetails(Annotation ann, Shepherd myShepherd) {
        JSONObject aj = new JSONObject();

        if (ann == null) return aj;
        MediaAsset ma = ann.getMediaAsset();
        // populate bounding box stuff (note: it may reset aj so must be done first)
        if (ann.getFeatures() != null) {
            for (Feature ft : ann.getFeatures()) {
                if (ft.isUnity()) {
                    aj.put("trivial", true);
                    aj.put("x", 0);
                    aj.put("y", 0);
                    // would be weird to be null, but.....
                    if (ma != null) {
                        aj.put("width", (int)ma.getWidth());
                        aj.put("height", (int)ma.getHeight());
                    }
                } else {
                    // basically if we have more than one feature, only one wins
                    if (ft.getParameters() != null) aj = ft.getParameters();
                }
            }
        }
        if (ma != null) {
            JSONObject mj = ma.toSimpleJSONObject();
            mj.put("rotationInfo", ma.getRotationInfo());
            aj.put("asset", mj);
        }
        Encounter enc = ann.findEncounter(myShepherd);
        if (enc != null) {
            JSONObject ej = new JSONObject();
            // TODO add "access" permission value if needed?
            ej.put("id", enc.getId());
            ej.put("taxonomy", enc.getTaxonomyString());
            aj.put("encounter", ej);
            MarkedIndividual indiv = enc.getIndividual();
            if (indiv != null) {
                JSONObject ij = new JSONObject();
                ij.put("id", indiv.getId());
                ij.put("taxonomy", indiv.getTaxonomyString());
                ij.put("displayName", indiv.getDisplayName());
                ij.put("nickname", indiv.getNickName());
                ij.put("sex", indiv.getSex());
                ij.put("numberEncounters", indiv.getNumEncounters());
                aj.put("individual", ij);
            }
        }
        aj.put("id", ann.getId());
        return aj;
    }

    public String toString() {
        String s = "MatchResult " + id;

        s += " [" + Util.millisToISO8601String(created) + "]";
        s += " query " + queryAnnotation;
        s += "; numCandidates=" + this.getNumberCandidates();
        s += "; numProspects=" + this.numberProspects();
        s += "; " + task;
        return s;
    }
}
