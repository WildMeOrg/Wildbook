package org.ecocean.ia;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

    public MatchResult(Task task, JSONObject jsonResult, Shepherd myShepherd)
    throws IOException {
        this();
        this.task = task;
        this.createFromJsonResult(jsonResult, myShepherd);
    }

    // opensearch doc scores with vectors seem to be 0.0 but the ordering seems best-to-worst,
    // so we generally trust the score *for annot/image* however scoreByIndividual will weight
    // scores by how many times the individual appears
    public MatchResult(Task task, List<Annotation> annots, int numberCandidates,
        Shepherd myShepherd)
    throws IOException {
        this();
        this.task = task;
        this.numberCandidates = numberCandidates;
        this.setQueryAnnotationFromTask();
        // we populate prospects with both annot and indiv (per legacy) and it gets seperated out later
        this.populateProspects(annots, false, myShepherd);
        this.populateProspects(annots, true, myShepherd);
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
        createFromJsonResult(res, myShepherd);
    }

    public Annotation setQueryAnnotationFromTask()
    throws IOException {
        if (this.task == null)
            throw new IOException("setQueryAnnotationFromTask() failed as task is null");
        int numAnns = this.task.countObjectAnnotations();
        if (numAnns < 1)
            throw new IOException("setQueryAnnotationFromTask() failed as task has no annotations");
        if (numAnns > 1)
            System.out.println("WARNING: setQueryAnnotationFromTask() has " + numAnns +
                " annotations; using first");
        this.queryAnnotation = this.task.getObjectAnnotations().get(0);
        return this.queryAnnotation;
    }

    // json_result section should be passed here
    public void createFromJsonResult(JSONObject res, Shepherd myShepherd)
    throws IOException {
        if (res == null) throw new IOException("null json_result passed");
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
        this.populateProspects("annot", results.optJSONArray("dannot_uuid_list"),
            results.optJSONArray("annot_score_list"), results.optJSONArray("dannot_extern_list"),
            results.optString("dannot_extern_reference", null), myShepherd);
        this.populateProspects("indiv", results.optJSONArray("dannot_uuid_list"),
            results.optJSONArray("score_list"), results.optJSONArray("dannot_extern_list"),
            results.optString("dannot_extern_reference", null), myShepherd);
        System.out.println("[DEBUG] createFromJsonResult() created " + this);
    }

    private int populateProspects(String type, JSONArray annotIds, JSONArray scores,
        JSONArray externs, String externRef, Shepherd myShepherd)
    throws IOException {
        if ((annotIds == null) || (scores == null))
            throw new IOException("null annotIds or scores");
        if (annotIds.length() != scores.length())
            throw new IOException("mismatch in size of annotIds/scores");
        if (this.prospects == null)
            this.prospects = new HashSet<MatchResultProspect>();
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

    // we just have a list of annots which matched (e.g. via vectors in opensearch)
    private int populateProspects(List<Annotation> annots, boolean scoreByIndividual,
        Shepherd myShepherd)
    throws IOException {
        if (Util.collectionIsEmptyOrNull(annots)) return 0;
        if (this.prospects == null)
            this.prospects = new HashSet<MatchResultProspect>();
        if (scoreByIndividual) {
            _populateProspectsByIndividual(annots, myShepherd);
        } else {
            for (Annotation ann : annots) {
                this.prospects.add(new MatchResultProspect(ann, 0.0d, "annot", null));
            }
        }
        return this.prospects.size();
    }

    private void _populateProspectsByIndividual(List<Annotation> annots, Shepherd myShepherd) {
        Map<MarkedIndividual, List<Annotation> > tally = new HashMap<MarkedIndividual,
            List<Annotation> >();

        for (Annotation ann : annots) {
            Encounter enc = ann.findEncounter(myShepherd);
            // i think we just ignore if no enc/indiv
            if (enc == null) continue;
            MarkedIndividual indiv = enc.getIndividual();
            if (indiv == null) continue;
            if (!tally.containsKey(indiv)) tally.put(indiv, new ArrayList<Annotation>());
            tally.get(indiv).add(ann);
        }
        if (tally.size() < 1) return; // no individuals i guess?
        System.out.println(">>>>>>>> tally=" + tally);

        // this sorts by most annots (per indiv) highest to lowest
        List<Map.Entry<MarkedIndividual,
            List<Annotation> > > sorted = new ArrayList<>(tally.entrySet());
        System.out.println(">>>>>>>> sorted[pre]=" + sorted);
        // Collections.sort(sorted, new Comparator<Map.Entry<MarkedIndividual, List<Annotation>>>() {
        sorted.sort(new Comparator<Map.Entry<MarkedIndividual, List<Annotation> > >() {
            public int compare(Map.Entry<MarkedIndividual, List<Annotation> > one,
            Map.Entry<MarkedIndividual, List<Annotation> > two) {
                // we reverse order here so we get largest first
                System.out.println(">>>>>>> two.key=" + two.getKey());
                System.out.println(">>>>>>> two.value=" + two.getValue());
                System.out.println(">>>>>>> one.key=" + one.getKey());
                System.out.println(">>>>>>> one.value=" + one.getValue());
                return Integer.compare(two.getValue().size(), one.getValue().size());
            }
        });
        System.out.println(">>>>>>>> sorted[post]=" + sorted);
        int most = sorted.get(0).getValue().size(); // top num of annots
        System.out.println(">>>>>>>> most=" + most);
        for (Map.Entry<MarkedIndividual, List<Annotation> > ent : sorted) {
            System.out.println(">>>>>>>> mapEntry key=" + ent.getKey());
            System.out.println(">>>>>>>> mapEntry value=" + ent.getValue());
            double score = ent.getValue().size() / most;
            // the ent value (annot List) should always have at least one annot, so we use first one
            this.prospects.add(new MatchResultProspect(ent.getValue().get(0), score, "indiv",
                null));
        }
    }

    private Annotation getAnnotationFromAcmId(String acmId, Shepherd myShepherd) {
        if (acmId == null) return null;
        Annotation found = findAcmIdInTaskAnnotations(acmId);
        if (found != null) return found;
        List<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmId, true);
        System.out.println("[WARNING] getAnnotationFromAcmId() failed to find " + acmId +
            " in task annots; loaded by acmId " + Util.collectionSize(anns) + " annot(s)");
        if ((anns == null) || (anns.size() < 1)) return null;
        return anns.get(0);
    }

    private Annotation findAcmIdInTaskAnnotations(String acmId) {
        if ((this.task == null) || (acmId == null)) return null;
        if (!this.task.hasObjectAnnotations()) return null;
        for (Annotation ann : this.task.getObjectAnnotations()) {
            if (acmId.equals(ann.getAcmId())) return ann;
        }
        return null;
    }

    // if it exists, we just return the thing, other wise we attempt to create it
    public MediaAsset createInspectionHeatmapAsset(String externRef, String annotId,
        Shepherd myShepherd) {
        if (externRef == null) return null;
        String url = "/api/query/graph/match/thumb/?extern_reference=" + externRef;
        url += "&query_annot_uuid=" + this.queryAnnotation.getAcmId();
        url += "&database_annot_uuid=" + annotId;
        url += "&version=heatmask";
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
    public List<MatchResultProspect> prospectsSorted(String type, int cutoff,
        Set<String> projectIds, Shepherd myShepherd) {
        List<MatchResultProspect> pros = new ArrayList<MatchResultProspect>();

        if (numberProspects() == 0) return pros;
        for (MatchResultProspect mrp : prospects) {
            if (mrp.isType(type) && mrp.isInProjects(projectIds, myShepherd)) pros.add(mrp);
        }
        Collections.sort(pros);
        if ((cutoff > 0) && (pros.size() > cutoff)) return pros.subList(0, cutoff);
        return pros;
    }

    public JSONObject prospectsForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
        JSONObject sj = new JSONObject();

        for (String type : prospectScoreTypes()) {
            JSONArray jarr = new JSONArray();
            for (MatchResultProspect mrp : prospectsSorted(type, cutoff, projectIds, myShepherd)) {
                jarr.put(mrp.jsonForApiGet(myShepherd));
            }
            sj.put(type, jarr);
        }
        return sj;
    }

    public JSONObject jsonForApiGet(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        rtn.put("id", id);
        rtn.put("queryAnnotation", annotationDetails(queryAnnotation, myShepherd));
        rtn.put("numberTotalProspects", numberProspects());
        rtn.put("numberCandidates", getNumberCandidates());
        rtn.put("created", Util.millisToISO8601String(created));
        rtn.put("prospects", prospectsForApiGet(cutoff, projectIds, myShepherd));
        rtn.put("projectIds", projectIds);
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
            ej.put("locationId", enc.getLocationID());
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
