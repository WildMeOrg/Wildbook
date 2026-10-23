/*
    an instance of an ia.Task can be persisted and represents the state of that task
    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
 */
package org.ecocean.ia;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jdo.Query;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.identity.IdentityServiceLog;

public class Task implements java.io.Serializable {
    public static long TIMEOUT_INACTIVE_MILLIS = 7l * 24l * 60l * 60l * 1000l;
    // ---- issue #1787: one identifier for one identification run (spec §4.0) ----
    /** Parameter key: the umbrella id of the identification run every task of that run carries. */
    public static final String PARAM_RUN_ID = "identificationInvocationId";
    /** Parameter key: marks a per-option task, or the umbrella when it is the single option. */
    public static final String PARAM_OPTION = "identificationOption";
    /** Parameter key: vector (ml-service) option tasks and their per-annotation leaves. */
    public static final String PARAM_V2_MATCH = "mlServiceV2Match";
    /** Parameter key: the bulk import that stamped this task (PR #1625). */
    public static final String PARAM_IMPORT_TASK_ID = "importTaskId";
    /** Stored status of a HotSpotter task whose send failed while a retry may still be scheduled (§4.4). Non-terminal. */
    public static final String STATUS_REQUEUED = "requeued";
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
    // general use, but notably will contain error details when status=error
    private String statusDetails = null;
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

    // test fixtures only: lets a hand-built tree carry distinct creation times
    void setCreated(long millis) {
        created = millis;
    }

    public long getModifiedLong() {
        return modified;
    }

    public long timeInactive() {
        long now = System.currentTimeMillis();

        if (modified > 0) return (now - modified);
        if (created > 0) return (now - created);
        // weird or inconclusive:
        return -1l;
    }

    public boolean timedOutDueToInactivity() {
        return (timeInactive() > TIMEOUT_INACTIVE_MILLIS);
    }

    public boolean statusInEndState() {
        if ("completed".equals(status)) return true;
        if ("error".equals(status)) return true;
        // ml-service migration v2: "dropped-stale" is terminal — the task's
        // target was deleted before the queued job ran. Neither success nor
        // error; the inactivity-timeout watchdog must not flip it to error.
        if ("dropped-stale".equals(status)) return true;
        return false;
    }

    public void setModified() {
        modified = System.currentTimeMillis();
    }

    public boolean canUserAccess(User user, Shepherd myShepherd) {
        if (user == null) return false;
        if (user.isAdmin(myShepherd)) return true;
        Encounter enc = null;
        // if we have annotations, use first to determine encounter
        if (this.countObjectAnnotations() > 0) {
            enc = this.getObjectAnnotations().get(0).findEncounter(myShepherd);
        } else if (this.countObjectMediaAssets() > 0) { // no annots, use asset instead
            MediaAsset ma = this.getObjectMediaAssets().get(0);
            // we iterate over all annots on this asset til we find an encounter.
            // it might be better to find *all* encounters and return access based on each;
            // however the main use for userHasAccess() revolves around *annotation-based* tasks (matching)
            // so i think this means asset-based access of tasks will be rare or unused anyway
            for (Annotation ann : ma.getAnnotations()) {
                if (ann != null) enc = ann.findEncounter(myShepherd);
                if (enc != null) break;
            }
        }
        if (enc == null) return false;
        if (enc.isPubliclyReadable()) return true;
        // note: we also have enc.canUserView() and enc.canUserEdit() !!! :(
        return enc.canUserAccess(user, myShepherd.getContext());
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

    public int numberMediaAssets() {
        return Util.collectionSize(objectMediaAssets);
    }

    public int numberAnnotations() {
        return Util.collectionSize(objectAnnotations);
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

    /** Membership by annotation id (Annotation.equals is id-based, but this never depends on it). */
    public boolean holdsAnnotationId(String annId) {
        if ((annId == null) || (objectAnnotations == null)) return false;
        for (Annotation a : objectAnnotations) {
            if ((a != null) && annId.equals(a.getId())) return true;
        }
        return false;
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

    public JSONObject getStatusDetails() {
        return Util.stringToJSONObject(statusDetails);
    }

    public void setStatusDetails(String s) {
        statusDetails = s;
    }

    public void setStatusDetails(JSONObject j) {
        if (j == null) {
            statusDetails = null;
        } else {
            statusDetails = j.toString();
        }
    }

    public void setStatusDetailsAddError(String code, String message) {
        JSONObject add = new JSONObject();

        add.put("code", code);
        add.put("message", message);
        setStatusDetailsAddToSection("errors", add);
    }

    public void setStatusDetailsAddLog(String message) {
        JSONObject add = new JSONObject();

        add.put("message", message);
        setStatusDetailsAddToSection("log", add);
    }

    // internal utility method for above
    private void setStatusDetailsAddToSection(String section, JSONObject add) {
        if (add == null) return;
        add.put("timestamp", System.currentTimeMillis());
        JSONObject sd = getStatusDetails();
        if (sd == null) sd = new JSONObject();
        if (sd.optJSONArray(section) == null) sd.put(section, new JSONArray());
        sd.getJSONArray(section).put(add);
        setStatusDetails(sd);
    }

    public JSONObject getParameters() { // only return as JSONObject!
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

    /**
     * Pick the single best task to surface as "Match Results" for an
     * annotation. Used by both the encounter page and the bulk-import
     * page so they cannot disagree about which task they link to.
     *
     * Loads the annotation's direct tasks (created-DESC) and delegates the
     * selection to {@link #selectPreferredMatchTask(List, String)} — see that
     * method's javadoc for the full precedence, the {@code importTaskId}
     * handling (issue #1624 legacy imports and the v2 ml-service fix), and the
     * known cross-import limitation.
     *
     * Returns null if no renderable or root task is usable.
     */
    public static Task getPreferredMatchResultsTaskForAnnotation(Annotation ann,
        String importTaskId, Shepherd myShepherd) {
        return getPreferredMatchResultsTaskForAnnotation(ann, importTaskId, null, myShepherd);
    }

    /**
     * Encounter-scoped variant (issue #1787, spec §4.3): {@code encounterAnnotationIds} is the
     * id set of every annotation on the annotation's encounter. Non-null opts in to run
     * selection and the structural hoist; null keeps today's behaviour.
     */
    public static Task getPreferredMatchResultsTaskForAnnotation(Annotation ann,
        String importTaskId, Set<String> encounterAnnotationIds, Shepherd myShepherd) {
        List<Task> tasks = getTasksFor(ann, myShepherd, "created DESC");

        return selectPreferredMatchTask(tasks, importTaskId, encounterAnnotationIds);
    }

    /**
     * Pure (no datastore) selection logic behind
     * {@link #getPreferredMatchResultsTaskForAnnotation}, extracted so it can be
     * unit-tested with hand-built task graphs. {@code tasks} must be the
     * annotation's direct tasks in created-DESC (newest-first) order.
     *
     * <p>Renderability is evaluated FIRST, across all candidates. A renderable
     * match task (or v2 match task) is the only kind that owns / leads to a
     * MatchResult, so {@code importTaskId} must NOT pre-filter the candidate set:
     * a v2 ml-service import stamps {@code importTaskId} onto its
     * detection/umbrella task — which is not renderable and owns no results —
     * while the real per-annotation match tasks are unstamped. Pre-filtering by
     * {@code importTaskId} (the behavior before this fix) discarded those match
     * tasks and fell back to the resultless detection root, which made the
     * bulk-import "Class" column render {@code "{}"} and the Match Results link
     * open an empty page. See
     * docs/plans/2026-06-18-bulk-import-match-task-selection-fix.md.</p>
     *
     * <p>Precedence:
     * <ol>
     *   <li>Renderable match tasks only. Within them, when {@code importTaskId}
     *       is given: the newest task stamped with THIS import wins; else the
     *       newest UNSTAMPED task (the v2 case); a task stamped with a DIFFERENT
     *       import's id is never returned. When {@code importTaskId} is null the
     *       newest renderable task wins.</li>
     *   <li>No usable renderable task: root fallback, applying the same
     *       scoped/legacy {@code importTaskId} partition as before (PR #1625 /
     *       issue #1624 legacy-import behavior, unchanged).</li>
     * </ol></p>
     *
     * <p>Known limitation: because v2 match tasks are unstamped, an annotation
     * that participates in more than one import cannot be fully isolated by
     * {@code importTaskId}; the newest unstamped renderable task is used. A later
     * re-run from the encounter page likewise supersedes the import's task — same
     * as the encounter page (unscoped) and the documented legacy behavior. A true
     * freeze-on-import guarantee would require stamping the match tasks (separate
     * follow-up).</p>
     *
     * <p>Returns null if no renderable or root task is usable.</p>
     */
    static Task selectPreferredMatchTask(List<Task> tasks, String importTaskId) {
        return selectPreferredMatchTask(tasks, importTaskId, null);
    }

    /**
     * Issue #1787 (spec §4.3). With a null {@code encounterAnnotationIds} this is exactly the
     * two-argument behaviour above. With a set:
     * <ol>
     *   <li>new tree: the run's umbrella from {@link #selectRun}, scoped to the encounter;</li>
     *   <li>new tree whose newest umbrella spans encounters: today's selection restricted to
     *       that run's tasks (no hoist);</li>
     *   <li>old tree: three ordered phases (renderable candidates minus orchestration nodes
     *       under the import partition; else the newest {@code ibeis.identification} task under
     *       the same partition; else today's root fallback, returned as is), then a structural
     *       climb from the phase-1/2 winner with explicit stop rules.</li>
     * </ol>
     */
    static Task selectPreferredMatchTask(List<Task> tasks, String importTaskId,
        Set<String> encounterAnnotationIds) {
        if (Util.collectionIsEmptyOrNull(tasks)) return null;
        if ((importTaskId != null) && importTaskId.trim().isEmpty()) importTaskId = null;
        if (encounterAnnotationIds == null) return selectLegacy(tasks, importTaskId);
        Task run = selectRun(tasks, importTaskId, encounterAnnotationIds);
        if (run != null) return run;
        Task newestUmbrella = selectRun(tasks, importTaskId, null);
        if (newestUmbrella != null) {
            List<Task> ofRun = new ArrayList<Task>();
            for (Task t : tasks) {
                if (newestUmbrella.getId().equals(runIdOf(t))) ofRun.add(t);
            }
            return selectLegacy(ofRun, importTaskId);
        }
        List<Task> oldTree = new ArrayList<Task>();
        for (Task t : tasks) {
            if (runIdOf(t) == null) oldTree.add(t);
        }
        if (oldTree.isEmpty()) return selectLegacy(tasks, importTaskId);  // only foreign-stamped runs
        return selectOldTree(oldTree, importTaskId, encounterAnnotationIds);
    }

    // Today's two-argument behaviour, verbatim (renderable phase, then root fallback).
    private static Task selectLegacy(List<Task> tasks, String importTaskId) {
        if (Util.collectionIsEmptyOrNull(tasks)) return null;
        List<Task> renderable = new ArrayList<Task>();
        for (Task t : tasks) {
            if (isRenderableMatchTask(t) || isV2MatchTask(t)) renderable.add(t);
        }
        if (!renderable.isEmpty()) {
            if (importTaskId == null) return renderable.get(0);
            Task newestUnstamped = null;
            for (Task t : renderable) {  // newest-first
                String tid = importTaskIdOf(t);
                if (importTaskId.equals(tid)) return t;
                if ((tid == null) && (newestUnstamped == null)) newestUnstamped = t;
            }
            if (newestUnstamped != null) return newestUnstamped;
        }
        return rootFallback(tasks, importTaskId);
    }

    // PR #1625's scoped/legacy root partition, verbatim from the previous selector body.
    private static Task rootFallback(List<Task> tasks, String importTaskId) {
        List<Task> pool = tasks;
        if (importTaskId != null) {
            List<Task> scoped = new ArrayList<Task>();
            List<Task> legacy = new ArrayList<Task>();
            for (Task t : tasks) {
                String tid = importTaskIdOf(t);
                if (importTaskId.equals(tid)) {
                    scoped.add(t);
                } else if (tid == null) {
                    legacy.add(t);
                }
            }
            if (!scoped.isEmpty()) {
                pool = scoped;
            } else if (!legacy.isEmpty()) {
                pool = legacy;
            } else {
                return null;
            }
        }
        List<Task> roots = onlyRoots(pool);
        if (!roots.isEmpty()) return roots.get(0);
        return null;
    }

    // §4.3 old tree: phases 1-3, then the climb.
    private static Task selectOldTree(List<Task> tasks, String importTaskId,
        Set<String> encounterAnnotationIds) {
        List<Task> phase1 = new ArrayList<Task>();
        for (Task t : tasks) {
            if ((isRenderableMatchTask(t) || isV2MatchTask(t)) && !isOrchestrationNode(t))
                phase1.add(t);
        }
        Task winner = newestInPartition(phase1, importTaskId);
        if (winner == null) {
            List<Task> phase2 = new ArrayList<Task>();
            for (Task t : tasks) {
                JSONObject p = t.getParameters();
                if ((p != null) && p.has("ibeis.identification") && !isOrchestrationNode(t))
                    phase2.add(t);
            }
            winner = newestInPartition(phase2, importTaskId);
        }
        if (winner == null) return rootFallback(tasks, importTaskId);  // phase 3: as is, never climbed
        return hoistToRunRoot(winner, encounterAnnotationIds);
    }

    // candidates are newest-first; scoped: this import's stamp, else unstamped, never foreign
    private static Task newestInPartition(List<Task> candidates, String importTaskId) {
        if (candidates.isEmpty()) return null;
        if (importTaskId == null) return candidates.get(0);
        Task newestUnstamped = null;
        for (Task t : candidates) {
            String tid = importTaskIdOf(t);
            if (importTaskId.equals(tid)) return t;
            if ((tid == null) && (newestUnstamped == null)) newestUnstamped = t;
        }
        return newestUnstamped;
    }

    // detection / extraction orchestration, or a pre-fix umbrella a later handleRest loaded with media assets
    private static boolean isOrchestrationNode(Task t) {
        if (t.numberMediaAssets() > 0) return true;
        JSONObject p = t.getParameters();
        return (p != null) && (p.has("ibeis.detection") || p.has("embeddingExtraction"));
    }

    private static final int HOIST_MAX_HOPS = 20;

    // §4.3 structural climb with the stop rules; falls back to the best task found so far on any throw
    private static Task hoistToRunRoot(Task winner, Set<String> encounterAnnotationIds) {
        Task best = winner;
        try {
            String winnerStamp = importTaskIdOf(winner);
            Set<String> visited = new HashSet<String>();
            visited.add(winner.getId());
            Task up = winner.getParent();
            int hops = 0;
            while ((up != null) && (up.getId() != null) && (hops++ < HOIST_MAX_HOPS) && visited.add(up.getId())) {
                if (up.numberAnnotations() == 0) break;                                   // roots, sub-parents
                if (isOrchestrationNode(up)) break;                                       // detection / extraction
                if ((up.getParent() == null) && (up.getParameters() == null)) break;       // parameterless root (createFromApi E)
                if (!allAnnotationsIn(up, encounterAnnotationIds)) break;                 // #1744 boundary
                String upStamp = importTaskIdOf(up);
                if ((upStamp != null) && !upStamp.equals(winnerStamp)) break;             // #1624 boundary
                if ((upStamp == null) && (winnerStamp != null) && !allChildrenStamped(up, winnerStamp)) break;
                best = up;
                up = up.getParent();
            }
        } catch (RuntimeException ex) {
            System.out.println("WARNING: Task.hoistToRunRoot() stopped at " + best.getId() + " due to " + ex);
        }
        return best;
    }

    // unstamped bridge: every direct child carries the winner's stamp (an empty child list never bridges)
    private static boolean allChildrenStamped(Task up, String stamp) {
        List<Task> kids = up.getChildren();
        if ((kids == null) || kids.isEmpty()) return false;
        for (Task kid : kids) {
            if (!stamp.equals(importTaskIdOf(kid))) return false;
        }
        return true;
    }

    // The task's importTaskId parameter, or null when absent/blank.
    private static String importTaskIdOf(Task t) {
        JSONObject p = t.getParameters();
        String tid = (p == null) ? null : p.optString(PARAM_IMPORT_TASK_ID, null);
        if ((tid != null) && tid.trim().isEmpty()) tid = null;  // blank == un-stamped
        return tid;
    }

    // ---- issue #1787 run vocabulary ----

    /** The run id a task carries, or null for a task outside any run (null task / parameters / blank). */
    public static String runIdOf(Task t) {
        if (t == null) return null;
        JSONObject p = t.getParameters();
        String rid = (p == null) ? null : p.optString(PARAM_RUN_ID, null);
        if ((rid != null) && rid.trim().isEmpty()) rid = null;
        return rid;
    }

    /** A task is the umbrella of a run iff its own id equals its run id (§4.0). */
    public static boolean isUmbrella(Task t) {
        if ((t == null) || (t.getId() == null)) return false;
        return t.getId().equals(runIdOf(t));
    }

    /** Option tasks carry {@link #PARAM_OPTION}; leaves and fan-out children never do (§4.0). */
    public static boolean isOptionTask(Task t) {
        if (t == null) return false;
        JSONObject p = t.getParameters();
        return (p != null) && p.optBoolean(PARAM_OPTION, false);
    }

    public static boolean anyRunId(List<Task> tasks) {
        if (tasks == null) return false;
        for (Task t : tasks) {
            if (runIdOf(t) != null) return true;
        }
        return false;
    }

    /**
     * The keys that identify a task within its run (issue #1787): kept on the option parent
     * when {@code IAGateway._doIdentify} clears its parameters before fanning out. Returns
     * null when nothing applies, so a legacy parent still ends up with null parameters.
     */
    public static JSONObject identityParams(JSONObject params) {
        if (params == null) return null;
        JSONObject keep = new JSONObject();
        for (String key : new String[] { PARAM_IMPORT_TASK_ID, PARAM_RUN_ID, PARAM_OPTION }) {
            if (params.has(key)) keep.put(key, params.get(key));
        }
        return (keep.length() == 0) ? null : keep;
    }

    /** A copy of {@code params} without the option marker (fan-out children are leaves). */
    public static JSONObject withoutOptionMarker(JSONObject params) {
        if (params == null) return null;
        JSONObject copy = new JSONObject(params.toString());
        copy.remove(PARAM_OPTION);
        return copy;
    }

    /**
     * Spec §4.0 run selection, shared by the Match Results link and the import status.
     * Returns the newest ELIGIBLE umbrella among the annotation's direct tasks, comparing
     * umbrella creation times (never leaf times, so a late HotSpotter fan-out under an older
     * run cannot outrank a newer run). Eligible: the task is an umbrella; its stamp satisfies
     * the import rule (scoped: this import's stamp first, else unstamped, never foreign;
     * unscoped: any); and, when {@code encounterAnnotationIds} is non-null, every annotation
     * on it is in that set. {@code direct} may be in any order; it is sorted (stably) here.
     */
    public static Task selectRun(List<Task> direct, String importTaskId,
        Set<String> encounterAnnotationIds) {
        if (Util.collectionIsEmptyOrNull(direct)) return null;
        if ((importTaskId != null) && importTaskId.trim().isEmpty()) importTaskId = null;
        List<Task> sorted = new ArrayList<Task>(direct);
        Collections.sort(sorted, new Comparator<Task>() {  // stable: equal times keep caller order
            @Override public int compare(Task a, Task b) {
                return Long.compare(b.getCreatedLong(), a.getCreatedLong());
            }
        });
        Task newestStamped = null;
        Task newestUnstamped = null;
        for (Task t : sorted) {
            if (!isUmbrella(t)) continue;
            if (!allAnnotationsIn(t, encounterAnnotationIds)) continue;
            if (importTaskId == null) return t;   // unscoped: newest eligible umbrella wins
            String tid = importTaskIdOf(t);
            if (importTaskId.equals(tid)) {
                if (newestStamped == null) newestStamped = t;
            } else if ((tid == null) && (newestUnstamped == null)) {
                newestUnstamped = t;
            }
        }
        if (newestStamped != null) return newestStamped;
        return newestUnstamped;
    }

    // every annotation on the task is in the set (a null set means "no encounter check")
    private static boolean allAnnotationsIn(Task t, Set<String> encounterAnnotationIds) {
        if (encounterAnnotationIds == null) return true;
        if (t.getObjectAnnotations() == null) return true;
        for (Annotation a : t.getObjectAnnotations()) {
            if ((a == null) || (a.getId() == null) || !encounterAnnotationIds.contains(a.getId()))
                return false;
        }
        return true;
    }

    // A bare mlServiceV2Match=true flag isn't enough — Task(Task p)
    // inherits the parent's parameters into the child, so every sub-
    // task (faea174f post-processing, pairx enricher, etc.) carries
    // the flag too. Guard by parent==null so we only match the root
    // v2 request task, never an inherited child. Round-2 Codex
    // Blocker.
    private static boolean isV2MatchTask(Task t) {
        JSONObject p = t.getParameters();

        return (t.getParent() == null) &&
               (p != null) && p.optBoolean("mlServiceV2Match", false);
    }

    public static Task getPreferredMatchResultsTaskForAnnotation(Annotation ann,
        Shepherd myShepherd) {
        return getPreferredMatchResultsTaskForAnnotation(ann, null, myShepherd);
    }

    // Structural criteria mirroring ImportTask.statsAnnotations:446-461 — a
    // task is "renderable" as a match result if it is shaped like a task
    // that owns prospects: single-algo legacy WBIA task, task with child
    // algorithm sub-tasks under a thin parent, or a 3+-children root — plus
    // (issue #1744) a single-annotation identified branch under a
    // multi-annotation parent; see isSingleAnnotationBranch.
    private static boolean isRenderableMatchTask(Task t) {
        Task parent = t.getParent();
        List<Task> children = t.getChildren();
        JSONObject params = t.getParameters();

        if ((parent != null) && (params != null) && params.has("ibeis.identification") &&
            (((parent.getChildren() != null) && (parent.getChildren().size() == 1)) ||
            isSingleAnnotationBranch(t, parent))) {
            return true;
        }
        if ((children != null) && !children.isEmpty() &&
            (parent != null) && (parent.getChildren() != null) &&
            (parent.getChildren().size() <= 1)) {
            return true;
        }
        if ((children != null) && (children.size() > 2) && (parent == null)) {
            return true;
        }
        return false;
    }

    // Issue #1744: when one image holds several annotations, both pipelines put
    // ALL of them on one umbrella task and then branch one identified child per
    // annotation (v2: Embedding.findMatchProspects subtasks under the
    // MlServiceProcessor.runMatchProspects match task; legacy:
    // IAGateway._doIdentify children, IA.intakeAnnotations per-iaClass
    // children). The child owns that annotation's MatchResult, so it — not the
    // umbrella, whose matchResultsJson recursion would render every
    // annotation's prospects on one page — is the task to surface for that
    // annotation. Per-ALGORITHM siblings hold the same annotation set as their
    // parent and therefore do not qualify, so multi-algorithm trees keep their
    // existing selection.
    private static boolean isSingleAnnotationBranch(Task t, Task parent) {
        return (t.numberAnnotations() == 1) && (parent.numberAnnotations() > 1);
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

    /**
     * Read the raw persisted status field without the timed-out-task
     * mutation side-effect that {@link #getStatus(Shepherd)} performs.
     * Callers gating read-only decisions on status (e.g., the
     * empty-match-prospects Track 2 batch gate) need this so a read of
     * a sibling task's status doesn't mutate that task as a side
     * effect.
     *
     * <p>(Empty-match-prospects design Track 2 C7.)</p>
     */
    public String getStoredStatus() {
        return this.status;
    }

    public String getStatus(Shepherd myShepherd) {
        // see if we might be dead in the water
        // TODO skipping status==null cuz i cant figure out what this means and there are so many of them
        if (!statusInEndState() && timedOutDueToInactivity() && !(this.status == null)) {
            this.status = "error";
            long ti = timeInactive();
            setStatusDetailsAddError("TIMEOUT",
                "this task is likely timed out; no activity for " + Util.millisToHumanApprox(ti));
            return this.status;
        }
        // if status is not null, just send it
        if (status != null) return status;
        // otherwise
        // note: this is LOCAL status :(  so it is not changing this.status, only returning the value
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
            } else if (islObj.optJSONObject("status") != null &&
                islObj.optJSONObject("status").optJSONObject("error") != null &&
                islObj.optJSONObject("status").optJSONObject("error").optBoolean(
                    "emptyTargetAnnotations", false)) {
                // No target annotations to match against is a terminal state, not a failure.
                // Treating it as completed lets import progress reach 100%.
                System.out.println("[Task.getStatus] emptyTargetAnnotations detected for task " + getId() + ", marking completed");
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
            if(status.equals("waiting to queue"))System.out.println("[Task.getStatus DEBUG] waiting to queue, islObj: "+islObj.toString());
        }
        System.out.println("[DEBUG] getStatus() fell through to status='" + status + "' on Task " +
            this.getId());
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
        setModified();
        if (newStatus == null) status = null;
        else { status = newStatus; }
    }

    public Long getCompletionDateInMilliseconds() { return completionDateInMilliseconds; }

    // this will set all date stuff based on ms since epoch
    public void setCompletionDateInMilliseconds(Long ms) {
        this.completionDateInMilliseconds = ms;
    }

    // no arg = set to now
    public void setCompletionDateInMilliseconds() {
        this.completionDateInMilliseconds = Long.valueOf(System.currentTimeMillis());
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
        JSONObject rtn = new JSONObject();
        // vector/embed flavor
        if (getParameters().getJSONObject("ibeis.identification").optString("api_endpoint",
            null) != null) {
            String modelId = getParameters().getJSONObject("ibeis.identification").optString(
                "model_id", null);
            if (modelId == null) {
                rtn.put("description", "Vector embedding match");
            } else {
                rtn.put("description", "Vector embedding match (model: " + modelId + ")");
                rtn.put("modelId", modelId);
            }
            return rtn;
        }
        // it seems both of these are in most logs (and are identical), but being safe in case there are
        // examples in the wild with only one
        JSONObject conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
            "query_config_dict");
        if (conf == null)
            conf = getParameters().getJSONObject("ibeis.identification").optJSONObject(
                "queryConfigDict");
        // we set HotSpotter if pipeline_root is not set here
        if (conf != null) rtn.put("name", conf.optString("pipeline_root", "HotSpotter"));
        rtn.put("description",
            getParameters().getJSONObject("ibeis.identification").optString("description",
            "unknown algorithm/method"));
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
                        log.getTimestamp() + "] on Task " + this.getId() + " generated: " + mr);
                    myShepherd.getPM().makePersistent(mr);
                    mrs.add(mr);
                    setStatusDetailsAddLog("Created " + mr + " from IdentityServiceLog " +
                        log.getTimestamp());
                } catch (java.io.IOException ex) {
                    System.out.println("[ERROR] generateMatchResults() [log t=" +
                        log.getTimestamp() + "] on Task " + this.getId() + " failed: " + ex);
                    ex.printStackTrace();
                    setStatusDetailsAddError("UNKNOWN",
                        "Creation of MatchResult from IdentityServiceLog " + log.getTimestamp() +
                        " failed due to: " + ex);
                }
            }
        }
        return mrs;
    }

    public JSONObject matchResultsJson(int cutoff, Set<String> projectIds, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        rtn.put("id", getId());
        rtn.put("parentTaskId", getParentId());
        rtn.put("dateCreated", Util.millisToISO8601String(getCreatedLong()));
        rtn.put("dateCompleted", Util.millisToISO8601String(getCompletionDateInMilliseconds()));
        rtn.put("timeInactiveMillis", timeInactive());
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
        // methodInfo gates the "identification-like" extras (method label,
        // matchingSetFilter, legacy WBIA log-based MR generation). The v2
        // ml-service path doesn't persist `ibeis.identification` on its
        // match tasks, so methodInfo is null even though the MatchResult
        // is correctly persisted in the DB. Without the decoupling below,
        // the API silently drops the matchResults field from the JSON
        // tree and the React match-results page renders empty
        // (empty-match-prospects design Track 2 C15).
        if (methodInfo != null) {
            rtn.put("method", methodInfo);
            rtn.put("matchingSetFilter", getMatchingSetFilter());
        }
        // Always serialize an existing MatchResult regardless of methodInfo.
        // Vector (v2) results generate their MatchResult eagerly during
        // matching, so getLatestMatchResult will find one whenever the
        // pipeline ran successfully. Legacy WBIA results still rely on
        // generateMatchResults (log-based) to construct the MR on demand,
        // and that path stays gated by methodInfo since it interprets
        // identification-method-specific log JSON.
        MatchResult mr = getLatestMatchResult(myShepherd);
        if ((mr == null) && (methodInfo != null) && !hasChildren()) {
            System.out.println(
                "[DEBUG] matchResultsJson() found no MatchResults; generating on (leaf) Task " +
                this.getId());
            List<MatchResult> mrs = generateMatchResults(myShepherd);
            rtn.put("_generatedMatchResultsSize", mrs.size()); // leave a clue that we did the work!
            if (mrs.size() > 0) {
                mr = mrs.get(mrs.size() - 1);
                // this hack is important cuz it forces a db commit even though we are a GET api call sorrynotsorry
                rtn.put("_commitShepherd", true);
            }
        }
        if (mr != null)
            rtn.put("matchResults", mr.jsonForApiGet(cutoff, projectIds, myShepherd));
        // now we recurse thru children if applicable
        if (hasChildren()) {
            JSONArray charr = new JSONArray();
            for (Task child : children) {
                // TODO decide if we need to process child????
                JSONObject childJson = child.matchResultsJson(cutoff, projectIds, myShepherd);
                // we have to bubble this up all the way to the toplevel  :/
                if (childJson.optBoolean("_commitShepherd", false))
                    rtn.put("_commitShepherd", true);
                charr.put(childJson);
            }
            rtn.put("children", charr);
            // if we dont have children (leaf nodes) we get the status
        } else {
            // unsure which of these two things is more accurate or useful; thus including both
            rtn.put("status", getStatus(myShepherd));
            rtn.put("statusOverall", getOverallStatus(myShepherd));
            rtn.put("statusDetails", getStatusDetails());
        }
        return rtn;
    }
}
