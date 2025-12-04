package org.ecocean.ia;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/*
   import org.ecocean.Base;
   import org.ecocean.Encounter;
   import org.ecocean.media.AssetStore;
   import org.ecocean.media.MediaAsset;
   import org.ecocean.media.MediaAssetFactory;
   import org.ecocean.MarkedIndividual;
   import org.ecocean.Occurrence;
   import org.ecocean.OpenSearch;
   import org.ecocean.resumableupload.UploadServlet;
   import org.ecocean.servlet.ReCAPTCHA;
   import org.ecocean.servlet.ServletUtilities;
   import org.ecocean.shepherd.core.ShepherdPMF;
   import org.ecocean.User;
 */

import org.ecocean.Annotation;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.identity.IdentityServiceLog;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

public class MatchResult implements java.io.Serializable {
    private String id;
    private long created;
    private Task task;
    private Set<MatchResultProspect> prospects;
    private Annotation queryAnnotation;
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
        // TODO load candidates from "database_annot_uuid_list" but maybe after prospects since there is overlap there???
/*
        annot_score_list <=> dannot_uuid_list
        score_list is for indiv scores but on dannot_uuid_list (same length)
        name_score_list <=> unique_name_uuid_list ???
 */
        this.prospects = new HashSet<MatchResultProspect>();
        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
            results.optJSONArray("annot_score_list"), myShepherd);
        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
            results.optJSONArray("score_list"), myShepherd);
        System.out.println("[DEBUG] createFromIdentityServiceLog() created " + this);
    }

    // must initialize this.propsects first!!
    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
        Shepherd myShepherd)
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
            this.prospects.add(new MatchResultProspect(ann, score, type));
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

    public int numberCandidates() {
        return Util.collectionSize(candidates);
    }

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

    public JSONObject prospectsForApiGet(int cutoff) {
        JSONObject sj = new JSONObject();

        for (String type : prospectScoreTypes()) {
            JSONArray jarr = new JSONArray();
            for (MatchResultProspect mrp : prospectsSorted(type, cutoff)) {
                jarr.put(mrp.jsonForApiGet());
            }
            sj.put(type, jarr);
        }
        return sj;
    }

    public JSONObject jsonForApiGet(int cutoff) {
        JSONObject rtn = new JSONObject();

        rtn.put("id", id);
        rtn.put("numberTotalProspects", numberProspects());
        rtn.put("created", Util.millisToISO8601String(created));
        rtn.put("prospects", prospectsForApiGet(cutoff));
        return rtn;
    }

    public String toString() {
        String s = "MatchResult " + id;

        s += " [" + Util.millisToISO8601String(created) + "]";
        s += " query " + queryAnnotation;
        s += "; numCandidates=" + this.numberCandidates();
        s += "; numProspects=" + this.numberProspects();
        s += "; " + task;
        return s;
    }
}
