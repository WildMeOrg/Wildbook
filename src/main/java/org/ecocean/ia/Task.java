/*
    an instance of an ia.Task can be persisted and represents the state of that task
    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
 */
package org.ecocean.ia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.jdo.Query;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.identity.IdentityServiceLog;

public class Task implements java.io.Serializable {
    private String id = null;
    private long created = -1;
    private long modified = -1;
    // private List<Object> objects = null;  //in some perfect world i could figure out how to persist this.  :/  oh, for a wb base class.
    private List<MediaAsset> objectMediaAssets = null;
    private List<Annotation> objectAnnotations = null;
    private Task parent = null;
    private List<Task> children = null;
    private String parameters = null;
    private String status;
    private Long completionDateInMilliseconds;
    private String queueResumeMessage;

    public Task() {
        this(Util.generateUUID());
    }
    public Task(String id) {
        this.id = id;
        created = System.currentTimeMillis();
        modified = System.currentTimeMillis();
    }
    // makes a child of the passed Task (and inherits the parameters!!)
    public Task(Task p) {
        this();
        this.setParameters(p.getParameters());
        this.setParent(p);
    }

    public String getId() {
        return id;
    }

    public long getCreatedLong() {
        return created;
    }

    public long getModifiedLong() {
        return modified;
    }

/*
    // not really convinced these are accurate enough to use
    //   actual computation of these things is complicated
    //   leaving these for future potential exploration, if needed.

    public boolean isTypeDetection() {
        if (this.hasObjectMediaAssets()) return true;
        if (this.hasObjectAnnotations()) return false;
        if (this.parameters == null) return false;
        if (this.getParameters().optJSONObject("ibeis.identification") != null) return false;
        if (this.getParameters().optBoolean("ibeis.detection", false)) return true;
        return false;
    }
    public boolean isTypeIdentification() {
        if (this.isTypeDetection()) return false;  // we trust this a little more if (this.hasObjectAnnotations()) return true;
        if (this.parameters == null) return false;
        if (this.getParameters().optJSONObject("ibeis.identification") != null) return true;
        return false;
    }

    public boolean initiatedWithDetection() {
        if (this.parameters == null) return false;
        return this.getParameters().optBoolean("ibeis.detection", false);
    }
    public boolean initiatedWithIdentification() {
        if (this.parameters == null) return false;  // not sure how i feel about this return !this.getParameters().optBoolean("skipIdent", false);
    }
 */
    public int countObjectMediaAssets() {
        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
    }

    public int countObjectAnnotations() {
        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
    }

    public int countObjects() {
        return countObjectMediaAssets() + countObjectAnnotations();
    }

    // not sure if these two are mutually exclusive by definition, but lets assume not (wtf would that even mean? i dunno)
    public boolean hasObjectMediaAssets() {
        return (countObjectMediaAssets() > 0);
    }

    public boolean hasObjectAnnotations() {
        return (countObjectAnnotations() > 0);
    }

    public boolean hasObjects() {
        return (countObjects() > 0);
    }

    public void setObjectMediaAssets(List<MediaAsset> mas) {
        objectMediaAssets = mas;
    }

    public void setObjectAnnotations(List<Annotation> anns) {
        objectAnnotations = anns;
    }

    public List<MediaAsset> getObjectMediaAssets() {
        return objectMediaAssets;
    }

    public List<Annotation> getObjectAnnotations() {
        return objectAnnotations;
    }

    // kinda for convenience?
    public boolean addObject(MediaAsset ma) {
        if (ma == null) return false;
        if (objectMediaAssets == null) objectMediaAssets = new ArrayList<MediaAsset>();
        if (!objectMediaAssets.contains(ma)) {
            objectMediaAssets.add(ma);
            return true;
        }
        return false;
    }

    public boolean addObject(Annotation ann) {
        if (ann == null) return false;
        if (objectAnnotations == null) objectAnnotations = new ArrayList<Annotation>();
        if (!objectAnnotations.contains(ann)) {
            objectAnnotations.add(ann);
            return true;
        }
        return false;
    }

    public boolean removeObject(Annotation ann) {
        if (ann != null && objectAnnotations != null && objectAnnotations.contains(ann)) {
            objectAnnotations.remove(ann);
            return true;
        }
        return false;
    }

    public boolean removeObject(MediaAsset ma) {
        if (ma != null && objectMediaAssets != null && objectMediaAssets.contains(ma)) {
            objectMediaAssets.remove(ma);
            return true;
        }
        return false;
    }

    public boolean contains(Annotation ann) {
        if (objectAnnotations == null) return false;
        return objectAnnotations.contains(ann);
    }

    public boolean contains(MediaAsset ma) {
        if (objectMediaAssets == null) return false;
        return objectMediaAssets.contains(ma);
    }

    public List<Task> getChildren() {
        return children;
    }

    public void setChildren(List<Task> kids) {
        if (kids == null) {
            children = null;
            return;
        }
        children = new ArrayList<Task>();
        for (Task kid : kids) {
            this.addChild(kid); // let this do the work
        }
    }

    public List<Task> addChild(Task kid) {
        if (children == null) children = new ArrayList<Task>();
        if (kid == null) return children;
        if (kid.getId().equals(this.getId())) return children; // dont add ourself to children
        if (!children.contains(kid)) children.add(kid);
        return children;
    }

    public void setParent(Task t) {
        parent = t;
        t.addChild(this);
    }

    public Task getParent() {
        return parent;
    }

    public String getParentId() {
        if (parent == null) return null;
        return parent.getId();
    }

    public int numChildren() {
        return (children == null) ? 0 : children.size();
    }

    public boolean hasChildren() {
        return (this.numChildren() > 0);
    }

    // omg i am going to assume no looping
    public List<Task> getLeafTasks() {
        List<Task> leaves = new ArrayList<Task>();

        if (!this.hasChildren()) {
            leaves.add(this);
            return leaves;
        }
        for (Task kid : children) {
            leaves.addAll(kid.getLeafTasks());
        }
        return leaves;
    }

    public Task getRootTask() {
        if (parent == null) return this;
        return parent.getRootTask();
    }

    public Task deepContains(Annotation ann) {
        if (this.contains(ann)) return this;
        if (!this.hasChildren()) return null;
        for (Task kid : children) {
            Task found = kid.deepContains(ann);
            if (found != null) return found;
        }
        return null;
    }

    public Task deepContains(MediaAsset ma) {
        if (this.contains(ma)) return this;
        if (!this.hasChildren()) return null;
        for (Task kid : children) {
            Task found = kid.deepContains(ma);
            if (found != null) return found;
        }
        return null;
    }

    public List<Task> findNodesWithMediaAssets() {
        List<Task> found = new ArrayList<Task>();

        if (this.hasObjectMediaAssets()) found.add(this);
        if (this.hasChildren())
            for (Task kid : this.children) {
                found.addAll(kid.findNodesWithMediaAssets());
            }
        return found;
    }

    public List<Task> findNodesWithAnnotations() {
        List<Task> found = new ArrayList<Task>();

        if (this.hasObjectAnnotations()) found.add(this);
        if (this.hasChildren())
            for (Task kid : this.children) {
                found.addAll(kid.findNodesWithAnnotations());
            }
        return found;
    }

    public Map<String, Integer> detectionStatusSummary() {
        Map<String, Integer> cts = new HashMap<String, Integer>();

        if (!this.hasObjectMediaAssets()) return cts;
        for (MediaAsset ma : this.getObjectMediaAssets()) {
            String status = ma.getDetectionStatus();
            if (status == null) status = "";
            cts.put(status, cts.getOrDefault(status, 0) + 1);
        }
        return cts;
    }

    public Map<String, Integer> identificationStatusSummary() {
        Map<String, Integer> cts = new HashMap<String, Integer>();

        if (!this.hasObjectAnnotations()) return cts;
        for (Annotation ann : this.getObjectAnnotations()) {
            String status = ann.getIdentificationStatus();
            if (status == null) status = "";
            cts.put(status, cts.getOrDefault(status, 0) + 1);
        }
        return cts;
    }

    public JSONObject getParameters() { // only return as JSONObject!
        if (parameters == null) return null;
        return Util.stringToJSONObject(parameters);
    }

    // see comment above: should this even be public?  (or exist)
    public void setParameters(String s) { // best be json, yo
        parameters = s;
    }

    public void setParameters(JSONObject j) {
        if (j == null) {
            parameters = null;
        } else {
            parameters = j.toString();
        }
    }

    // convenience method to construct the JSONObject from key/value
    public void setParameters(String key, Object value) {
        if (key == null) return; // nope
        JSONObject j = new JSONObject();
        j.put(key, value); // value object type better be kosher for JSONObject.  :/
        parameters = j.toString();
    }

    // like above, but doesnt (re)set .parameters, will only append/alter the key'ed one
    public void addParameter(String key, Object value) {
        if (key == null) return;
        JSONObject j = this.getParameters();
        if (j == null) j = new JSONObject();
        j.put(key, value);
        parameters = j.toString();
    }

    public JSONObject toJSONObject() {
        return this.toJSONObject(false);
    }

    public JSONObject toJSONObject(boolean includeChildren) {
        JSONObject j = new JSONObject();

        j.put("id", id);
        j.put("parameters", this.getParameters());
        j.put("created", created);
        j.put("modified", modified);
        j.put("createdDate", new DateTime(created));
        j.put("modifiedDate", new DateTime(modified));
        if ((objectMediaAssets != null) && (objectMediaAssets.size() > 0)) {
            JSONArray jo = new JSONArray();
            for (MediaAsset ma : this.objectMediaAssets) {
                jo.put(ma.getId());
            }
            j.put("mediaAssetIds", jo);
        }
        if ((objectAnnotations != null) && (objectAnnotations.size() > 0)) {
            JSONArray jo = new JSONArray();
            for (Annotation ann : this.objectAnnotations) {
                jo.put(ann.getId());
            }
            j.put("annotationIds", jo);
        }
        if (includeChildren && this.hasChildren()) {
            JSONArray jc = new JSONArray();
            for (Task kid : this.children) {
                jc.put(kid.toJSONObject(true)); // we once again assume no looping!  bon chance.
            }
            j.put("children", jc);
        }
        return j;
    }

    // need these two so we can use things like List.contains() on tasks
    public boolean equals(final Object t2) {
        if (t2 == null) return false;
        if (!(t2 instanceof Task)) return false;
        Task two = (Task)t2;
        if ((this.id == null) || (two == null) || (two.getId() == null)) return false;
        return this.id.equals(two.getId());
    }

    public int hashCode() {
        if (id == null) return Util.generateUUID().hashCode(); // random(ish) so we dont get two users with no uuid equals! :/
        return id.hashCode();
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append(id)
                   .append("(" + new DateTime(created) + "|" + new DateTime(modified) + ")")
                   .append(numChildren() + "Kids")
                   .append(countObjectMediaAssets() + "MA")
                   .append(countObjectAnnotations() + "Ann")
                   .append("params=" + ((this.getParameters() ==
                null) ? "(none)" : this.getParameters().toString()))
                   .toString();
    }

    public static Task load(String taskId, Shepherd myShepherd) {
        Task t = null;

        try {
            t = ((Task)(myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(
                Task.class, taskId), true)));
        } catch (Exception ex) {}; // swallow jdo not found noise
        return t;
    }

    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd) {
        return getTasksFor(ann, myShepherd, null);
    }

    // TODO: evaluate if we should support versions for multiple objects (when needed)
    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd, String ordering) {
        String qstr =
            "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \""
            + ann.getId() + "\" VARIABLES org.ecocean.Annotation obj";
        Query query = myShepherd.getPM().newQuery(qstr);

        query.setIgnoreCache(true);
        if (ordering == null) {
            query.setOrdering("created");
        } else {
            query.setOrdering(ordering);
        }
        Collection c = (Collection)query.execute();
        List<Task> listy = new ArrayList<Task>(c);
        query.closeAll();
        return listy;
    }

    public static List<Task> getRootTasksFor(Annotation ann, Shepherd myShepherd) {
        return onlyRoots(getTasksFor(ann, myShepherd));
    }

    public static List<Task> getTasksFor(MediaAsset ma, Shepherd myShepherd) {
        String qstr =
            "SELECT FROM org.ecocean.ia.Task WHERE objectMediaAssets.contains(obj) && obj.id == " +
            ma.getId() + " VARIABLES org.ecocean.media.MediaAsset obj";
        Query query = myShepherd.getPM().newQuery(qstr);

        query.setIgnoreCache(true);
        query.setOrdering("created");
        Collection c = (Collection)query.execute();
        List<Task> listy = new ArrayList<Task>(c);
        query.closeAll();
        return listy;
    }

    public static List<Task> getRootTasksFor(MediaAsset ma, Shepherd myShepherd) {
        return onlyRoots(getTasksFor(ma, myShepherd));
    }

    // takes a bunch of tasks and returns only roots (without duplication)
    public static List<Task> onlyRoots(List<Task> all) {
        List<Task> roots = new ArrayList<Task>();

        for (Task t : all) {
            Task r = t.getRootTask();
            if (!roots.contains(r)) roots.add(r);
        }
        return roots;
    }

    public boolean areSelfAndOrAllChildrenComplete() {
        boolean complete = false;

        if (!hasChildren() && completionDateInMilliseconds != null) {
            complete = true;
        } else if (hasChildren()) {
            List<Task> children = getChildren();
            complete = true;
            for (Task t : children) {
                if (!t.areSelfAndOrAllChildrenComplete()) complete = false;
            }
        }
        return complete;
    }

    public String getStatus(Shepherd myShepherd) {
        // if status is not null, just send it
        if (status != null) return status;
        // otherwise
        String status = "waiting to queue";
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
            myShepherd);
        if (logs != null && logs.size() > 0) {
            Collections.reverse(logs); // so it has newest first like mostRecent above
            IdentityServiceLog l = logs.get(0);
            JSONObject islObj = l.toJSONObject();
            if (islObj.optString("status") != null &&
                islObj.optString("status").equals("completed")) {
                status = islObj.optString("status");
            } else if (islObj.optJSONObject("status") != null &&
                (islObj.optJSONObject("status").optJSONObject("needReview") != null)) {
                status = "completed";
            } else if (logs.toString().indexOf("score") > -1) {
                status = "completed";
            } else if (islObj.toString().indexOf("HTTP error code") > -1) {
                status = "error";
            } else if (!islObj.optString("queueStatus").equals("")) {
                status = islObj.optString("queueStatus");
            } else if (islObj.opt("status") != null &&
                islObj.opt("status").toString().indexOf("initIdentify") > -1) {
                status = "queuing";
            }
            // if(islObj.optString("queueStatus").equals("queued")){sendIdentify=false;}
            // if(status.equals("waiting to queue"))System.out.println("islObj: "+islObj.toString());
        }
        return status;
    }

    // this is stitched together from import.jsp. godspeed.
    // "resumeStalledTasks" functionality was stripped from this. if needed, revisit original method in import.jsp
    // also the original building/modification of (passed-in) idStatusMap is dropped
    public String getOverallStatus(Shepherd myShepherd) {
        String status = "unknown";

        if (this.hasChildren()) {
            // accumulate status across children
            HashMap<String, String> map = new HashMap<String, String>();
            // this should only ever be two layers deep
            for (Task childTask : this.getChildren()) {
                if (childTask.hasChildren()) {
                    for (Task childTask2 : childTask.getChildren()) {
                        if ((childTask2.getObjectAnnotations() != null) &&
                            (childTask2.getObjectAnnotations().size() > 0) &&
                            childTask2.getObjectAnnotations().get(0).getMatchAgainst() &&
                            (childTask2.getObjectAnnotations().get(0).getIAClass() != null)) {
                            map.put(childTask2.getId(), childTask2.getStatus(myShepherd));
                        }
                    }
                } else {
                    if ((childTask.getObjectAnnotations() != null) &&
                        (childTask.getObjectAnnotations().size() > 0) &&
                        childTask.getObjectAnnotations().get(0).getMatchAgainst() &&
                        (childTask.getObjectAnnotations().get(0).getIAClass() != null)) {
                        map.put(childTask.getId(), childTask.getStatus(myShepherd));
                    }
                }
            }
            // now, how do we report these?
            HashMap<String, Integer> resultsMap = new HashMap<String, Integer>();
            for (String key : map.values()) {
                // task results
                if (!resultsMap.containsKey(key)) {
                    resultsMap.put(key, new Integer(1));
                } else {
                    resultsMap.put(key, new Integer(resultsMap.get(key) + 1));
                }
            }
            status = resultsMap.toString();
        } else { // childless
            status = this.getStatus(myShepherd);
        }
        return status;
    }

    public boolean isFastlane(Shepherd myShepherd) {
        String status = "waiting to queue";
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(getId(), "IBEISIA",
            myShepherd);

        if (getParameters() != null && getParameters().optBoolean("fastlane", false)) {
            return true;
        }
        return false;
    }

    public void setStatus(String newStatus) {
        if (newStatus == null) status = null;
        else { status = newStatus; }
    }

    public Long getCompletionDateInMilliseconds() { return completionDateInMilliseconds; }

    // this will set all date stuff based on ms since epoch
    public void setCompletionDateInMilliseconds(Long ms) {
        if (ms == null) { this.completionDateInMilliseconds = null; } else {
            this.completionDateInMilliseconds = ms;
        }
    }

    // capture original queue message to make this Task more easily resumeable
    public String getQueueResumeMessage() { return queueResumeMessage; }
    public void setQueueResumeMessage(String message) {
        if (message == null) { queueResumeMessage = null; } else {
            queueResumeMessage = message;
        }
    }

    public JSONObject getMatchingSetFilter() {
        if (getParameters() == null) return null;
        return getParameters().optJSONObject("matchingSetFilter");
    }

    public JSONObject getIdentificationMethodInfo() {
        if (getParameters() == null) return null;
        if (getParameters().optJSONObject("ibeis.identification") == null) return null;
        // it seems both of these are in most logs (and are identical), but being safe in case there are
        // examples in the wild with only one
        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
            "query_config_dict");
        if (conf == null)
            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
                "queryConfigDict");
        JSONObject rtn = new JSONObject();
        if (conf != null) rtn.put("name", conf.optString("pipeline_root", null)); // null conf means that we have no name
        rtn.put("description",
            getParameters().getJSONObject("ibeis.identification").optString("description",
            "unknown algorith/method"));
        return rtn;
    }

    // convenience
    public List<MatchResult> getMatchResults(Shepherd myShepherd) {
        return myShepherd.getMatchResults(this);
    }

    public MatchResult getLatestMatchResult(Shepherd myShepherd) {
        List<MatchResult> all = myShepherd.getMatchResults(this);

        if (Util.collectionIsEmptyOrNull(all)) return null;
        return all.get(0);
    }

    // logs are returned in chronological order here, so if the latest is desired, take the LAST one
    public List<MatchResult> generateMatchResults(Shepherd myShepherd) {
        List<MatchResult> mrs = new ArrayList<MatchResult>();
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(this.id, "IBEISIA",
            myShepherd);

        if (logs == null) return mrs;
        for (IdentityServiceLog log : logs) {
            JSONObject res = log.getJsonResult();
            // in theory this is how we can tell if it is an ident result log versus detection
            if ((res != null) && (res.optJSONObject("cm_dict") != null)) {
                try {
                    MatchResult mr = new MatchResult(log, myShepherd);
                    System.out.println("[INFO] generateMatchResults() [log t=" +
                        log.getTimestamp() + "] on " + this + " generated: " + mr);
                    myShepherd.getPM().makePersistent(mr);
                    mrs.add(mr);
                } catch (java.io.IOException ex) {
                    System.out.println("[ERROR] generateMatchResults() [log t=" +
                        log.getTimestamp() + "] on " + this + " failed: " + ex);
                    ex.printStackTrace();
                }
            }
        }
        return mrs;
    }

    public JSONObject matchResultsJson(int cutoff, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        rtn.put("id", getId());
        rtn.put("parentTaskId", getParentId());
        rtn.put("dateCreated", Util.millisToISO8601String(getCreatedLong()));
        rtn.put("dateCompleted", Util.millisToISO8601String(getCompletionDateInMilliseconds()));
        // TODO theory is that we might not need to use/store queryAnnotation on MatchResult as
        // we should have it here, hence this debugging value ... possible optimization for later
        if (hasObjectAnnotations()) {
            JSONArray annotArr = new JSONArray();
            for (Annotation ann : getObjectAnnotations()) {
                if (ann != null) annotArr.put(ann.getId());
            }
            rtn.put("__taskAnnotations", annotArr);
        }
        JSONObject methodInfo = getIdentificationMethodInfo();
        // we basically use this to determine if we are "identification-like" enough
        // to display extended details
        if (methodInfo != null) {
            rtn.put("method", getIdentificationMethodInfo());
            rtn.put("matchingSetFilter", getMatchingSetFilter());
            // unsure which of these two things is more accurate or useful; thus including both
            rtn.put("status", getStatus(myShepherd));
            rtn.put("statusOverall", getOverallStatus(myShepherd));
            // we only care about (and importantly try to generate) MatchResults for ident type too
            MatchResult mr = getLatestMatchResult(myShepherd);
            if (mr == null) {
                System.out.println(
                    "[DEBUG] matchResultsJson() found no MatchResults; generating on " + this);
                List<MatchResult> mrs = generateMatchResults(myShepherd);
                rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
                if (mrs.size() > 0) {
                    mr = mrs.get(mrs.size() - 1);
                    rtn.put("_commitShepherd", true);
                }
            }
            if (mr != null) rtn.put("matchResults", mr.jsonForApiGet(cutoff, myShepherd));
        }
        // now we recurse thru children if applicable
        if (hasChildren()) {
            JSONArray charr = new JSONArray();
            for (Task child : children) {
                // TODO decide if we need to process child????
                JSONObject childJson = child.matchResultsJson(cutoff, myShepherd);
                // we have to bubble this up all the way to the toplevel  :/
                if (childJson.optBoolean("_commitShepherd", false))
                    rtn.put("_commitShepherd", true);
                charr.put(childJson);
            }
            rtn.put("children", charr);
        }
        return rtn;
    }
}
