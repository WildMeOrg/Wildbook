package org.ecocean.servlet.importer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.media.MediaAsset;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.Project;
import org.ecocean.security.Collaboration;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.social.SocialUnit;
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

    public List<Encounter> getEncountersOrderByCreated() {
        if (encounters == null) return null;
        List<Encounter> sorted = new ArrayList<Encounter>(encounters);
        Collections.sort(sorted, new Comparator<Encounter>() {
            @Override public int compare(Encounter encA, Encounter encB) {
                Long longA = encA.getDWCDateAddedLong();
                Long longB = encB.getDWCDateAddedLong();
                if ((longA == null) || (longB == null)) return 0;
                return Long.compare(longA, longB);
            }
        });
        return sorted;
    }

    public void setEncounters(List<Encounter> encs) {
        encounters = encs;
    }

    public void addEncounter(Encounter enc) {
        if (enc == null) return;
        if (encounters == null) encounters = new ArrayList<Encounter>();
        if (!encounters.contains(enc)) encounters.add(enc);
    }

    public Set<Annotation> getAnnotations() {
        Set<Annotation> anns = new HashSet<Annotation>();

        if (encounters != null)
            for (Encounter enc : encounters) {
                if (enc.getAnnotations() != null)
                    for (Annotation ann : enc.getAnnotations()) {
                        anns.add(ann);
                    }
            }
        return anns;
    }

    public Map<Annotation, List<Task> > getAnnotationTaskMap(Shepherd myShepherd) {
        Map<Annotation, List<Task> > atm = new HashMap<Annotation, List<Task> >();

        for (Annotation ann : this.getAnnotations()) {
            atm.put(ann, Task.getTasksFor(ann, myShepherd, "created DESC"));
        }
        return atm;
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

    public JSONArray getMatchingLocations() {
        if (getParameters() == null) return null;
        JSONObject passed = getParameters().optJSONObject("_passedParameters");
        if (passed == null) return null;
        return passed.optJSONArray("matchingLocations");
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

    public JSONObject statsAnnotations(Shepherd myShepherd) {
        JSONObject sa = new JSONObject();
        // List<Task> is ordered 'created desc'
        Map<Annotation, List<Task> > atm = this.getAnnotationTaskMap(myShepherd);
        int numTasks = 0;
        int numLatestTasks = 0;
        Map<String, Set<Task> > encTasks = new HashMap<String, Set<Task> >();
        Map<String, String> taskTmp = new HashMap<String, String>();

        for (Annotation ann : atm.keySet()) {
            Encounter enc = ann.findEncounter(myShepherd);
            if ((enc != null) && !encTasks.containsKey(enc.getId()))
                encTasks.put(enc.getId(), new HashSet<Task>());
            // trivial annots will not be sent correctly to ident (no iaClass etc)
            // so we skip them in counts as if not sent
            if (ann.isTrivial()) {
                sa.put(ann.getId(), 0);
                continue;
            }
            sa.put(ann.getId(), Util.collectionSize(atm.get(ann)));
            boolean latestTask = true; // only for first (most recent) task
            for (Task atask : atm.get(ann)) {
                String status = atask.getStatus(myShepherd);
                if (sa.has(status)) {
                    sa.put(status, sa.optInt(status, 0) + 1);
                } else {
                    sa.put(status, 1);
                }
                numTasks++;
                // this records only most recent task statuses like: numLatestTask_complete
                if (latestTask) {
                    String latestStatus = "numLatestTask_" + atask.getStatus(myShepherd);
                    if (sa.has(latestStatus)) {
                        sa.put(latestStatus, sa.optInt(latestStatus, 0) + 1);
                    } else {
                        sa.put(latestStatus, 1);
                    }
                    numLatestTasks++;
                }
                if (enc != null) {
                    // this is temporary storage to use to populate encounterTaskInfo later
                    // this status is wrong: needs to be "overall status"
                    // taskTmp.put(atask.getId() + ".status", status);
                    taskTmp.put(atask.getId() + ".iaClass", ann.getIAClass());
                    // the logic for deciding when to add a task is based on
                    // mystical knowledge found originally in import.jsp
                    if ((atask.getParent() != null) &&
                        (atask.getParent().getChildren().size() == 1) &&
                        (atask.getParameters() != null) &&
                        atask.getParameters().has("ibeis.identification")) {
                        // task with only one algorithm
                        encTasks.get(enc.getId()).add(atask);
                    } else if ((atask.getChildren() != null) && (atask.getChildren().size() > 0) &&
                        (atask.getParent() != null) &&
                        (atask.getParent().getChildren().size() <= 1)) {
                        // task with child ident tasks
                        encTasks.get(enc.getId()).add(atask);
                    } else if ((atask.getChildren() != null) && (atask.getChildren().size() > 2) &&
                        (atask.getParent() == null)) {
                        // task with child ident tasks (also?)
                        encTasks.get(enc.getId()).add(atask);
                    }
                }
                latestTask = false;
            }
        }
        sa.put("numTasks", numTasks);
        sa.put("numLatestTasks", numLatestTasks);

        // now we do the work to create encounterTaskInfo
        JSONObject encData = new JSONObject();
        for (String encId : encTasks.keySet()) {
            List<Task> tasks = new ArrayList<Task>();
            tasks.addAll(encTasks.get(encId));
            // order to put newest on top
            Collections.sort(tasks, new Comparator<Task>() {
                @Override public int compare(Task taskA, Task taskB) {
                    return Long.compare(taskB.getCreatedLong(), taskA.getCreatedLong());
                }
            });
            JSONArray tasksArr = new JSONArray();
            for (Task task : tasks) {
                JSONArray taskArr = new JSONArray();
                taskArr.put(task.getId());
                // we have to compute the kind of expensive "overall status" here
                taskArr.put(task.getOverallStatus(myShepherd));
                taskArr.put(taskTmp.get(task.getId() + ".iaClass"));
                tasksArr.put(taskArr);
            }
            encData.put(encId, tasksArr);
        }
        sa.put("encounterTaskInfo", encData);
        return sa;
    }

/*
    this likely is doing the wrong thing; using the above logic, which
    was ported from import.jsp

    public Map<String, Integer> statsAnnotationsBROKEN() {
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
 */

/*
    this is a slightly modified version of DeleteImportTask.java, but has all shepherd commits commented out,
    with the idea that a single commit outside (in the caller) should do the job. note also that in the servlet version,
    an exception does a rollback, but very likely many of the steps up until that point has been commited, so not sure
    what state that leaves things in the actual db
 */
    public static void deleteWithRelated(String id, User user, Shepherd myShepherd)
    throws IOException {
        if ((id == null) || (user == null)) throw new IOException("must provide id and user");
        ImportTask itask = myShepherd.getImportTask(id);
        if (itask == null) throw new IOException("invalid ImportTask id=" + id);
        if (!Collaboration.canUserAccessImportTask(itask, myShepherd.getContext(),
            user.getUsername()))
            throw new IOException("user does not have privileges to delete task");
        Util.mark("ImportTask.deleteWithRelated(" + id + ") started");
        try {
            List<Encounter> allEncs = new ArrayList<Encounter>(itask.getEncounters());
            int total = allEncs.size();
            for (int i = 0; i < allEncs.size(); i++) {
                Encounter enc = allEncs.get(i);
                Occurrence occ = myShepherd.getOccurrence(enc);
                MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(enc.getIndividualID());
                List<Project> projects = myShepherd.getProjectsForEncounter(enc);
                ArrayList<Annotation> anns = enc.getAnnotations();
                for (Annotation ann : anns) {
                    enc.removeAnnotation(ann);
                    // myShepherd.updateDBTransaction();
                    List<Task> iaTasks = Task.getTasksFor(ann, myShepherd);
                    if (iaTasks != null && !iaTasks.isEmpty()) {
                        for (Task iaTask : iaTasks) {
                            iaTask.removeObject(ann);
                            // myShepherd.updateDBTransaction();
                        }
                    }
                    myShepherd.throwAwayAnnotation(ann);
                    // myShepherd.updateDBTransaction();
                }
                // handle occurrences
                if (occ != null) {
                    occ.removeEncounter(enc);
                    // myShepherd.updateDBTransaction();
                    if (occ.getEncounters().size() == 0) {
                        myShepherd.throwAwayOccurrence(occ);
                        // myShepherd.updateDBTransaction();
                    }
                }
                // handle markedindividual
                if (mark != null) {
                    mark.removeEncounter(enc);
                    // myShepherd.updateDBTransaction();
                    if (mark.getEncounters().size() == 0) {
                        // check for social unit membership and remove
                        List<SocialUnit> units = myShepherd.getAllSocialUnitsForMarkedIndividual(
                            mark);
                        if (units != null && units.size() > 0) {
                            for (SocialUnit unit : units) {
                                boolean worked = unit.removeMember(mark, myShepherd);
                                // if (worked) myShepherd.updateDBTransaction();
                            }
                        }
                        myShepherd.throwAwayMarkedIndividual(mark);
                        // myShepherd.updateDBTransaction();
                    }
                }
                // handle projects
                if (projects != null && projects.size() > 0) {
                    for (Project project : projects) {
                        project.removeEncounter(enc);
                        // myShepherd.updateDBTransaction();
                    }
                }
                itask.removeEncounter(enc);
                itask.addLog("Servlet DeleteImportTask removed Encounter: " +
                    enc.getCatalogNumber());
                // myShepherd.updateDBTransaction();
                try {
                    myShepherd.throwAwayEncounter(enc);
                } catch (Exception e) {
                    System.out.println("Exception on throwAwayEncounter!!");
                    e.printStackTrace();
                }
                // myShepherd.updateDBTransaction();
            }
            myShepherd.getPM().deletePersistent(itask);
            // myShepherd.commitDBTransaction();
        } catch (Exception ex) {
            throw new IOException("general exception on ImportTask delete: " + ex);
        }
        Util.mark("ImportTask.deleteWithRelated(" + id + ") completed");
    }

    // this is hobbled together from some complex code in import.jsp
    // some of this is only necessary to handle legacy (non-api) uploads
    // may the gods have mercy on our soul
    // FIXME this can be OUTRAGEOUSLY slow for tasks with 100s of annotations
    // for the GET api for listing tasks we very likely want to move this
    // to detailed=true so it is not called for every task -- but this currently
    // messes up the status :(
    public JSONObject iaSummaryJson(Shepherd myShepherd) {
        int numDetectionComplete = 0;
        int numAcmId = 0;
        int numAllowedIA = 0;
        int numAssets = 0;
        int numAnnotations = 0;
        boolean pipelineStarted = false;
        Map<String, Integer> statsMA = this.statsMediaAssets();
        JSONObject statsAnn = this.statsAnnotations(myShepherd);

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
                if (numAssets > 0) pj.put("detectionPercent", new Double(numDetectionComplete) / new Double(numAssets));
                pj.put("detectionStatus", "sent");
            }
            if (this.iaTaskRequestedIdentification()) {
                int numIdentificationComplete = 0;
                int numIdentificationTotal = 0;
                // getOverallStatus() in imports.jsp is a nightmare. attempt to replicate here.
                if (statsAnn.optInt("numLatestTasks", -1) >= 0)
                    numIdentificationTotal = statsAnn.optInt("numLatestTasks");
                // who is the genius who made this be 'completed' versus the (seemingly universal?) 'complete'
                // (it may well have been me)
                if (statsAnn.optInt("numLatestTask_completed", -1) >= 0)
                    numIdentificationComplete = statsAnn.optInt("numLatestTask_completed");
                // TODO do we have to deal with errors as "completed" somehow?
                pj.put("identificationNumberComplete", numIdentificationComplete);
                pj.put("identificationNumTotal", numIdentificationTotal);
                if (numIdentificationTotal == 0) {
                    pj.put("identificationStatus", "identification not started");
                    pj.put("identificationPercent", 0.0);
                } else if (numIdentificationComplete >= numIdentificationTotal) {
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
