package org.ecocean.servlet.importer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.media.MediaAsset;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

public class ImportTask implements java.io.Serializable {
    private String id;
    private User creator;
    private DateTime created;
    private List<Encounter> encounters;
    private String parameters;
    private List<String> log;
    private String errors;
    private String status;
    private Task iaTask;
    // processingProgress is really used for IMPORT progress only (0.0 thru 1.0)
    private Double processingProgress;

    public ImportTask() {
        this((User)null);
    }

    public ImportTask(User u) {
        this(u, Util.generateUUID());
    }

    public ImportTask(User u, String id) {
        this.creator = u;
        this.updateCreated();
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void updateCreated() {
        created = new DateTime();
    }

    public DateTime getCreated() {
        return created;
    }

    public int numberEncounters() {
        return Util.collectionSize(encounters);
    }

    public Double getProcessingProgress() {
        return processingProgress;
    }

    public void setProcessingProgress(Double prog) {
        processingProgress = prog;
    }

    public List<Encounter> getEncounters() {
        return encounters;
    }

    public void setEncounters(List<Encounter> encs) {
        encounters = encs;
    }

    public void addEncounter(Encounter enc) {
        if (enc == null) return;
        if (encounters == null) encounters = new ArrayList<Encounter>();
        if (!encounters.contains(enc)) encounters.add(enc);
    }

    public void setCreator(User u) {
        creator = u;
    }

    public User getCreator() {
        return creator;
    }

    // classically the excel filename the user uploaded, generalized for api usage
    public String getSourceName() {
        if (getParameters() == null) return null;
        JSONObject passed = getParameters().optJSONObject("_passedParameters");
        if (passed == null) return null;
        String name = passed.optString("sourceName", null);
        if (name != null) return name;
        // for some reason (!???) these are arrays with 1 element
        JSONArray nameArr = passed.optJSONArray("originalFilename");
        if (nameArr == null) nameArr = passed.optJSONArray("filename");
        if ((nameArr != null) && (nameArr.length() > 0)) return nameArr.optString(0, null);
        return null;
    }

    // this means was NOT sent via api
    // NOTE this logic may end up being flaky; adjust accordingly
    public boolean isLegacy() {
        if (getParameters() == null) return true; // ????
        JSONObject passed = getParameters().optJSONObject("_passedParameters");
        if (passed == null) return true; // ?????
        if (passed.optString("bulkImportId", null) != null) return false;
        return true;
    }

    public List<MarkedIndividual> getMarkedIndividuals() {
        if (encounters == null) return null;
        List<MarkedIndividual> all = new ArrayList<MarkedIndividual>();
        for (Encounter enc : encounters) {
            MarkedIndividual indiv = enc.getIndividual();
            if ((indiv != null) && !all.contains(indiv)) all.add(indiv);
        }
        return all;
    }

    public List<MediaAsset> getMediaAssets() {
        if (encounters == null) return null;
        List<MediaAsset> mas = new ArrayList<MediaAsset>();
        for (Encounter enc : encounters) {
            ArrayList<MediaAsset> encMAs = enc.getMedia();
            if (Util.collectionSize(encMAs) > 0)
                for (MediaAsset ma : encMAs) {
                    if (!mas.contains(ma)) mas.add(ma); // dont want duplicates
                }
        }
        return mas;
    }

    public List<Occurrence> getOccurrences(Shepherd myShepherd) {
        if (encounters == null) return null;
        List<Occurrence> occs = new ArrayList<Occurrence>();
        for (Encounter enc : encounters) {
            String occId = enc.getOccurrenceID();
            if (occId == null) continue;
            Occurrence occ = myShepherd.getOccurrence(occId);
            if (occ != null) occs.add(occ);
        }
        return occs;
    }

    public void setParameters(String s) {
        parameters = s;
    }

    public void setParameters(JSONObject j) {
        if (j == null) {
            parameters = null;
        } else {
            parameters = j.toString();
        }
    }

    public String getParametersAsString() {
        return parameters;
    }

    public JSONObject getParameters() {
        return Util.stringToJSONObject(parameters);
    }

    public void setPassedParameters(HttpServletRequest request) {
        JSONObject p = getParameters();

        if (p == null) p = new JSONObject();
        p.put("_passedParameters", Util.requestParametersToJSONObject(request));
        parameters = p.toString();
    }

    public void setPassedParameters(JSONObject passed) {
        JSONObject p = getParameters();

        if (p == null) p = new JSONObject();
        p.put("_passedParameters", passed);
        parameters = p.toString();
    }

    public JSONObject getPassedParameters() {
        if (this.getParameters() == null) return null;
        return this.getParameters().optJSONObject("_passedParameters");
    }

    public JSONArray getErrors() {
        return Util.stringToJSONArray(errors);
    }

    public void setErrors(JSONArray err) {
        if (err == null) {
            errors = null;
        } else {
            errors = err.toString();
        }
    }

    public JSONArray addError(JSONObject err) {
        JSONArray errs = this.getErrors();

        if (err == null) return errs;
        if (errs == null) errs = new JSONArray();
        errs.put(err);
        return errs;
    }

    // note: this auto-timestamps
    public void addLog(String l) {
        if (l == null) return;
        if (log == null) log = new ArrayList<String>();
        log.add(Long.toString(System.currentTimeMillis()) + " " + l);
    }

    public List<String> getLog() {
        return log;
    }

    public JSONArray getLogJSONArray() {
        JSONArray larr = new JSONArray();

        if (Util.collectionIsEmptyOrNull(log)) return larr;
        for (String l : log) {
            JSONObject jl = new JSONObject();
            if (l.matches("^\\d{13} .*$")) { // has timestamp
                String ts = l.substring(0, 13);
                try {
                    jl.put("t", Long.parseLong(ts));
                } catch (NumberFormatException ex) {
                    jl.put("t", ts); // meh?
                }
                jl.put("l", l.substring(14));
            } else {
                jl.put("l", l);
            }
            larr.put(jl);
        }
        return larr;
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append("id", id)
                   .append("created", created)
                   .append("creator", (creator == null) ? (String)null : creator.getDisplayName())
                   .append("numEncs", Util.collectionSize(encounters))
                   .toString();
    }

    public void removeEncounter(Encounter enc) {
        if (enc == null) return;
        if (encounters == null) return;
        if (encounters.contains(enc)) encounters.remove(enc);
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public void setId(String id) { this.id = id; }

    public Task getIATask() {
        return this.iaTask;
    }

    public void setIATask(Task t) {
        this.iaTask = t;
    }

    // some (potentially) useful methods about our IA Task

    public boolean iaTaskStarted() {
        if (iaTask == null) return false;
        return true;
    }

    public boolean iaTaskRequestedIdentification() {
        if (iaTask == null) return false;
        if (iaTask.getParameters() == null) return true; // has no skipIdent, so i guess?
        return !iaTask.getParameters().optBoolean("skipIdent", false);
    }

    // for sake of this value, skipping detection is also good enough
    public boolean skippedIdentification() {
        if (skippedDetection()) return true;
        return !iaTaskRequestedIdentification();
    }

    public boolean skippedDetection() {
        if (this.getPassedParameters() == null) return false;
        return this.getPassedParameters().optBoolean("skipDetection", false);
    }

    public Map<String, Integer> stats() {
        if (iaTask == null) return null;
        Map<String, Integer> stats = new HashMap<String, Integer>();
        stats.put("countLeafTasks", iaTask.getLeafTasks().size());
        stats.put("countChildTasks", iaTask.numChildren());
        return stats;
    }

    public Map<String, Integer> statsMediaAssets() {
        if (iaTask == null) return null;
        List<Task> tasks = iaTask.findNodesWithMediaAssets();
        Map<String, Integer> stats = new HashMap<String, Integer>();
        stats.put("numTasks", tasks.size());
        for (Task task : tasks) {
            Map<String, Integer> tsum = task.detectionStatusSummary();
            stats = Util.mapAdd(stats, tsum);
        }
        return stats;
    }

    public Map<String, Integer> statsAnnotations() {
        if (iaTask == null) return null;
        List<Task> tasks = iaTask.findNodesWithAnnotations();
        Map<String, Integer> stats = new HashMap<String, Integer>();
        stats.put("numTasks", tasks.size());
        for (Task task : tasks) {
            Map<String, Integer> tsum = task.identificationStatusSummary();
            stats = Util.mapAdd(stats, tsum);
        }
        return stats;
    }

    // this is hobbled together from some complex code in import.jsp
    // some of this is only necessary to handle legacy (non-api) uploads
    // may the gods have mercy on our soul
    public JSONObject iaSummaryJson() {
        int numDetectionComplete = 0;
        int numAcmId = 0;
        int numAllowedIA = 0;
        int numAssets = 0;
        int numAnnotations = 0;
        boolean pipelineStarted = false;
        Map<String, Integer> statsMA = this.statsMediaAssets();
        Map<String, Integer> statsAnn = this.statsAnnotations();

        if (this.getMediaAssets() != null)
            numAssets = this.getMediaAssets().size();
        for (MediaAsset ma : this.getMediaAssets()) {
            numAnnotations += ma.numAnnotations();
            if (ma.getAcmId() != null) numAcmId++;
            // check if we can get validity off the image before the expensive check of hitting the AssetStore
            if (ma.isValidImageForIA() != null) {
                if (ma.isValidImageForIA().booleanValue()) numAllowedIA++;
            } else if (ma.validateSourceImage()) {
                numAllowedIA++;
            }
/*
                if ((ma.isValidImageForIA() == null) || !ma.isValidImageForIA().booleanValue()) {
                    invalidMediaAssets.add(asset);
                }
 */
            if ((ma.getDetectionStatus() != null) &&
                (ma.getDetectionStatus().equals("complete") ||
                ma.getDetectionStatus().equals("pending"))) numDetectionComplete++;
        }
        JSONObject pj = new JSONObject();
        pj.put("statsMediaAssets", statsMA);
        pj.put("statsAnnotations", statsAnn);
        pj.put("numberMediaAssets", numAssets);
        pj.put("numberAnnotations", numAnnotations);
        pj.put("numberMediaAssetACMIds", numAcmId);
        pj.put("numberMediaAssetValidIA", numAllowedIA);
        pj.put("detectionNumberComplete", numDetectionComplete);
        // non-legacy flavor
        if ((this.getIATask() != null) && this.iaTaskStarted()) {
            pipelineStarted = true;
            if (numDetectionComplete == numAllowedIA) {
                pj.put("detectionPercent", 1.0);
                pj.put("detectionStatus", "complete");
            } else {
                if (numAssets > 0) pj.put("detectionPercent", numDetectionComplete / numAssets);
                pj.put("detectionStatus", "sent");
            }
            if (this.iaTaskRequestedIdentification()) {
                int numIdentificationComplete = 0;
                int numIdentificationTotal = 0;
                // getOverallStatus() in imports.jsp is a nightmare. attempt to replicate here.
                if (statsAnn.get("numTasks") != null)
                    numIdentificationTotal = statsAnn.get("numTasks");
                if (statsAnn.get("complete") != null)
                    numIdentificationComplete = statsAnn.get("complete");
                // TODO do we have to deal with errors as "complete" somehow?
                pj.put("identificationNumberComplete", numIdentificationComplete);
                pj.put("identificationNumTotal", numIdentificationTotal);
                if (numIdentificationTotal == 0) {
                    pj.put("identificationStatus", "waiting for detection");
                    pj.put("identificationPercent", 0.0);
                } else if (numIdentificationComplete == numIdentificationTotal) {
                    pj.put("identificationStatus", "complete");
                    pj.put("identificationPercent", 1.0);
                } else {
                    pj.put("identificationStatus", "sent");
                    pj.put("identificationPercent",
                        new Double(numIdentificationComplete) / new Double(numIdentificationTotal));
                }
            }
            // legacy flavor
        } else if ((this.getIATask() == null) && (numDetectionComplete > 0)) {
            pipelineStarted = true;
            if (numDetectionComplete == numAssets) {
                pj.put("detectionPercent", 1.0);
                pj.put("detectionStatus", "complete");
            } else {
                if (numAssets > 0)
                    pj.put("detectionPercent",
                        new Double(numDetectionComplete) / new Double(numAssets));
                pj.put("detectionStatus", "sent");
            }
            pj.put("identificationStatus", "unknown");
        }
        if (this.skippedDetection()) pj.put("detectionStatus", "skipped");
        if (this.skippedIdentification()) pj.put("identificationStatus", "skipped");
        String ds = pj.optString("detectionStatus");
        String is = pj.optString("identificationStatus");
        pj.put("pipelineStarted", pipelineStarted);
        boolean pipelineComplete = ((ds.equals("complete") || ds.equals("skipped")) &&
            (is.equals("complete") || is.equals("skipped")));
        pj.put("pipelineComplete", pipelineComplete);
        return pj;
    }
}
