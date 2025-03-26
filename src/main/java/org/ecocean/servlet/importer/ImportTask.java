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
import org.ecocean.Shepherd;
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
    private String status;
    private Task iaTask;

    public ImportTask() {
        this((User)null);
    }
    public ImportTask(User u) {
        this.creator = u;
        this.updateCreated();
        this.id = Util.generateUUID();
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
        stats.put("count", tasks.size());
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
        stats.put("count", tasks.size());
        for (Task task : tasks) {
            Map<String, Integer> tsum = task.identificationStatusSummary();
            stats = Util.mapAdd(stats, tsum);
        }
        return stats;
    }
}
