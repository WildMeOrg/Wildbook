/*
    idea here is "simple": a gateway to all IA calls, mostly (now) cleaned up from identity/IBEISIA.java (guessing)

    THIS IS A WORK-IN-PROGRESS

    proposed key concepts:
 * can handle multiple IA frameworks (not just historic-IBEIS)
      - likely a base abstract class with a "isEnabled() / init()" concept
      - classes would allow for instances of each IA framework?

 * no idea how to handle crazy (and configurable!?) workflow!

 * probably should "leverage" Queue stuff where applicable?
      - possibly there is a NEED for both variations (as suggested by drew): an asynchronous (queued) and synchronous (not)

 * simply entry point for: MediaAsset and Annotation???
 */
package org.ecocean.ia;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Embedding;
import org.ecocean.Encounter;
import org.ecocean.identity.IBEISIA;
import org.ecocean.IAJsonProperties;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdProperties;
import org.ecocean.Taxonomy;
import org.ecocean.Util;
import org.json.JSONArray;
import org.json.JSONObject;

public class IA {
    private static final String PROP_FILE = "IA.properties";

    /*  NOTE: methods for both intaking a single element or a list.  thoughts:
        - these should be treated as different in that an IA framework might batch together the list in some way (i.e. difference between sending as
           list vs iterating over list with intake(each element)
        - you only get one task ID for the list/group, is this a bad idea?
     */
    public static Task intake(Shepherd myShepherd, MediaAsset ma) {
        return intakeMediaAssets(myShepherd, new ArrayList<MediaAsset>(Arrays.asList(ma)));
    }

    // Annotations *may or may not* already be on an Encounter  #neverforget
    public static Task intake(Shepherd myShepherd, Annotation ann) {
        return intakeAnnotations(myShepherd, new ArrayList<Annotation>(Arrays.asList(ann)));
    }

/*  these have same erasure types so cant co-exist. :( another reason for a common baseclass.. sigh?
    hence the overly-inclusive Object version below!
    public static Task intake(Shepherd myShepherd, List<MediaAsset> mas) {
        if ((mas == null) || (mas.size() < 1)) return null;
        Task task = new Task();
        return task;
    }
    public static Task intake(Shepherd myShepherd, List<Annotation> anns) {
        if ((anns == null) || (anns.size() < 1)) return null;
        Task task = new Task();
        return task;
    }
 */

    // i think objects ingested here must(?) be persisted (and committed), as we have to assume (or we know)
    // that these processes will use queues which operate in different (Shepherd) threads and will thus try
    // to find the objects via the db.  :/
    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
        return intakeMediaAssets(myShepherd, mas, null);
    }

    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas,
        Task parentTask) {
        List<List<MediaAsset> > assetsBySpecies = binAssetsBySpecies(mas, myShepherd);
        int numSpecies = assetsBySpecies.size();

        // System.out.println("IA.java.numSpecies="+numSpecies);
        // in one-species case we don't need to create an extra layer of tasks
        if (numSpecies == 1)
            return intakeMediaAssetsOneSpecies(myShepherd, assetsBySpecies.get(0), parentTask);
        // in multi-species case we make sure we have a parent task and add each species task as a child
        if (parentTask == null) parentTask = new Task();
        for (List<MediaAsset> masOneSpecies : assetsBySpecies) {
            Task thisTask = intakeMediaAssetsOneSpecies(myShepherd, masOneSpecies, parentTask);
            parentTask.addChild(thisTask);
        }
        return parentTask;
    }

    public static List<List<MediaAsset> > binAssetsBySpecies(List<MediaAsset> mas,
        Shepherd myShepherd) {
        Map<String, List<MediaAsset> > assetsBySpecies = new HashMap<String, List<MediaAsset> >();

        for (MediaAsset ma : mas) {
            Taxonomy taxy = ma.getTaxonomy(myShepherd);
            String scientificName = "null";
            if (taxy != null && taxy.getScientificName() != null)
                scientificName = taxy.getScientificName();
            // System.out.println("     MA ID "+ma.getId()+" has taxy "+scientificName);
            if (!assetsBySpecies.containsKey(scientificName))
                assetsBySpecies.put(scientificName, new ArrayList<MediaAsset>());
            assetsBySpecies.get(scientificName).add(ma);
            // System.out.println("       Taxy size: "+assetsBySpecies.get(scientificName).size());
        }
        return new ArrayList<List<MediaAsset> >(assetsBySpecies.values());
    }

    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
        final Task parentTask) {
        if ((mas == null) || (mas.size() < 1)) return null;
        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask);
    }

    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
        Taxonomy taxy, final Task parentTask) {
        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
    }

    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas,
        Taxonomy taxy, final Task parentTask, int tweetAssetId) {
        System.out.println("intakeMediaAssetsOneSpecies called for " + mas.size() +
            " media assets:");
        handleMissingAcmids(mas, myShepherd);
        for (MediaAsset ma : mas) {
            System.out.println("intakeMediaAssetsOneSpecies incl. ma " + ma.getId());
            System.out.println("acmid is: " + ma.getAcmId());
        }
        JSONArray maArr = new JSONArray();
        for (MediaAsset ma : mas) {
            maArr.put(ma.getId());
        }
        System.out.println("intakeMediaAssetsOneSpecies constructed maArr " + maArr.toString());

        Task topTask = new Task();
        // issue #1787 (spec §4.0): a detection task never carries a run's keys, even when the
        // root it descends from is a reused umbrella (handleRest)
        if ((parentTask != null) && (parentTask.getParameters() != null))
            topTask.setParameters(scrubRunParams(parentTask.getParameters()));
        topTask.setObjectMediaAssets(mas);
        myShepherd.storeNewTask(topTask);

        String context = myShepherd.getContext();
        String baseUrl = getBaseURL(context);

        // Ia configs are keyed off taxonomies
        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();

        // Migration plan v2 §commit #10b: routing reroute.
        // If the species' _id_conf.default.pipeline_root is "vector" AND
        // _mlservice_conf is configured, route per-asset through the
        // MlServiceProcessor lifecycle. Otherwise fall through to the legacy
        // WBIA path below — production deployments without _mlservice_conf
        // see no behavior change at all.
        //
        // Per-asset CHILD tasks under topTask (vs v1's shared topTask) so
        // child finalization is local; no first-finisher-wins. The topTask
        // remains as the aggregator for the caller contract (and so legacy
        // summary code that reads topTask.objectMediaAssets keeps working).
        if (iaConfig != null && taxy != null &&
            iaConfig.getActiveMlServiceConfigs(taxy) != null) {
            return intakeMediaAssetsOneSpeciesMlService(myShepherd, mas, taxy, topTask,
                context, baseUrl);
        }

        // what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
        JSONObject dj = new JSONObject();
        dj.put("mediaAssetIds", maArr);
        // mimicking intakeAnnotations, we assume the first mediaAsset is representative of all of them wrt Taxonomies, configs etc.
        int numDetectAlgos = iaConfig.numDetectionAlgos(taxy);
        Boolean[] sent = new Boolean[numDetectAlgos];
        for (int i = 0; i < numDetectAlgos; i++) {
            // task for this job (only create new (child) tasks if multiple detect algos)
            Task task = (numDetectAlgos == 1) ? topTask : new Task();
            task.setObjectMediaAssets(mas);
            task.setParameters(topTask.getParameters());

            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
            task.addParameter("detectArgs", detectArgs);

            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
            task.addParameter("__detect_url", detectionUrl);

            JSONObject qjob = new JSONObject();
            qjob.put("detect", dj);
            qjob.put("__detect_args", detectArgs);
            qjob.put("__detect_url", detectionUrl);
            // task is queued here
            qjob.put("taskId", topTask.getId());
            qjob.put("__context", context);
            qjob.put("__baseUrl", baseUrl);
            System.out.println("intakeMediaAssetsOneSpecies about to add additionalArgs to query");
            if (tweetAssetId != -1) {
                qjob.put("tweetAssetId", tweetAssetId);
            }
            System.out.println(
                "intakeMediaAssetsOneSpecies successfully added additionalArgs to query");
            sent[i] = false;
            try {
                // job is queued here
                sent[i] = org.ecocean.servlet.IAGateway.addToDetectionQueue(context,
                    qjob.toString());
            } catch (java.io.IOException iox) {
                System.out.println("ERROR: IA.intakeMediaAssets() hit exception on taxonomy " +
                    taxy.toString() + ", detectArgs = " + detectArgs.toString());
                System.out.println("ERROR: IA.intakeMediaAssets() addToQueue() threw " +
                    iox.toString());
            }
        }
        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() +
            " assets; queued? = " + sent + "; " + topTask);
        return topTask;
    }

    /**
     * ml-service migration v2 §commit #10b: per-asset job enqueue for the
     * vector pipeline. Each MediaAsset gets its own child Task under
     * topTask; each emits a {@code mlServiceV2:true} payload to the
     * detection queue. MlServiceProcessor.processQueueJob (commit #9)
     * picks them up via the IAGateway dispatcher (commit #10a).
     *
     * <p>Per-asset child Tasks avoid v1's first-finisher-wins on the shared
     * topTask. The topTask itself remains as the aggregator that holds the
     * full MediaAsset list for caller-side summary code.</p>
     *
     * <p>encounterId is derived best-effort from the MediaAsset's existing
     * trivial annotation (every Encounter.addMediaAsset call creates one).
     * If null, MlServiceProcessor persists annotations without explicit
     * Encounter linkage and downstream MediaAsset.assignEncounters handles
     * the assignment per the legacy IBEISIA detect-callback pattern.</p>
     */
    private static Task intakeMediaAssetsOneSpeciesMlService(Shepherd myShepherd,
        List<MediaAsset> mas, Taxonomy taxy, Task topTask, String context, String baseUrl) {
        int queued = 0;
        for (MediaAsset ma : mas) {
            if (enqueueOneAssetForMlService(myShepherd, ma, taxy, topTask, context, baseUrl)) {
                queued++;
            }
        }
        System.out.println("INFO: IA.intakeMediaAssetsOneSpeciesMlService accepted " +
            mas.size() + " assets; queued=" + queued + "; topTask=" + topTask);
        return topTask;
    }

    /**
     * Build and enqueue one v2 ml-service job for a single MediaAsset.
     * Returns {@code true} iff the FileQueue write succeeded.
     *
     * <p>Used by both {@link #intakeMediaAssetsOneSpeciesMlService} (the
     * normal intake path) and the startup stale-mlservice reconciler in
     * {@code StartupWildbook}. The reconciler relies on the boolean
     * return to decide whether to commit accompanying state changes; the
     * normal intake path tolerates the swallowed-failure behavior.</p>
     *
     * <p><b>Task persistence note:</b> {@link Shepherd#storeNewTask}
     * internally commits/reopens the transaction, so the child Task row
     * is persisted before this method enqueues. On enqueue failure the
     * child Task remains in the DB as an orphan — there is no queued
     * job that will ever drive it. The orphan IS still discoverable
     * via {@link org.ecocean.media.MediaAsset#getRootIATasks} (since
     * the task references the MediaAsset through objectMediaAssets),
     * so it may surface in operator-facing task listings until cleaned
     * up by an out-of-band path. Callers that need cleanup should
     * delete the orphan explicitly; the default posture here is to
     * accept it since FileQueue write failures are rare.</p>
     *
     * <p>If {@code topTask} is null a fresh root task is created inside
     * this method. This matches the reconciler's use case where there is
     * no caller-side aggregator umbrella.</p>
     */
    public static boolean enqueueOneAssetForMlService(Shepherd myShepherd, MediaAsset ma,
        Taxonomy taxy, Task topTask, String context, String baseUrl) {
        Task childTask = (topTask == null) ? new Task() : new Task(topTask);
        ArrayList<MediaAsset> singleton = new ArrayList<MediaAsset>();
        singleton.add(ma);
        childTask.setObjectMediaAssets(singleton);
        // Tag as a detection task so the /metrics detection gauges
        // (MetricsBot.addTasksToCsv + appadmin/wildbookIAQueueStats.jsp) count
        // ml-service v2 detection volume. Mirrors the legacy path, which sets
        // task.addParameter("ibeis.detection", true) in IAGateway. Must be set
        // before storeNewTask so it persists with the new row. The downstream
        // match task is created via new Task(parent) and would inherit this
        // flag, so MlServiceProcessor.runMatchProspects strips it back off
        // (mirror of IBEISIA's params.remove("ibeis.detection")).
        childTask.addParameter("ibeis.detection", true);
        myShepherd.storeNewTask(childTask);

        // Best-effort encounterId via existing annotations on the MA.
        String encounterId = null;
        ArrayList<Annotation> existing = ma.getAnnotations();
        if (existing != null) {
            for (Annotation a : existing) {
                Encounter enc = a.findEncounter(myShepherd);
                if (enc != null) {
                    encounterId = enc.getId();
                    break;
                }
            }
        }

        JSONObject qjob = new JSONObject();
        qjob.put("mlServiceV2", true);
        qjob.put("mediaAssetId", ma.getId());
        qjob.put("taxonomyString", taxy.getScientificName());
        qjob.put("taskId", childTask.getId());
        qjob.put("__context", context);
        qjob.put("__baseUrl", baseUrl);
        if (Util.stringExists(encounterId)) {
            qjob.put("encounterId", encounterId);
        }

        try {
            return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, qjob.toString());
        } catch (java.io.IOException iox) {
            System.out.println("ERROR: IA.enqueueOneAssetForMlService() " +
                "addToDetectionQueue threw on ma " + ma.getId() + ": " + iox);
            return false;
        }
    }

    public static void handleMissingAcmids(List<MediaAsset> mediaAssets, Shepherd myShepherd) {
        int count = 0;
        int stopAfter = 200000;
        int batchThreshold = 50;
        int batchesSoFar = 0;
        ArrayList<MediaAsset> assetsWithMissingAcmids = new ArrayList<MediaAsset>();

        try {
            for (MediaAsset ma : mediaAssets) {
                count++;
                if (count > stopAfter) {
                    break;
                }
                if (ma != null && !ma.hasAcmId()) {
                    assetsWithMissingAcmids.add(ma);
                }
                if ((assetsWithMissingAcmids.size() >= batchThreshold) ||
                    count == mediaAssets.size()) {
                    if (assetsWithMissingAcmids.size() > 0) { // if count gets to the end and assetsWithMissingAcmids is still empty, no need to do any of this
                        try {
                            IBEISIA.sendMediaAssetsNew(assetsWithMissingAcmids,
                                myShepherd.getContext());
                        } catch (Exception e) {
                            System.out.println(
                                "Error sending media asset to IA in handleMissingAcmids method in IA.java");
                            e.printStackTrace();
                        }
                    }
                    batchesSoFar++;
                    assetsWithMissingAcmids = new ArrayList<MediaAsset>();
                    myShepherd.updateDBTransaction();
                }
            }
        } catch (Exception e) {
            System.out.println("Error in handleMissingAcmids in IA.java");
            e.printStackTrace();
            myShepherd.rollbackDBTransaction();
        }
    }

    // similar behavior to above: basically fake /ia api call, but via queue
    // parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
        return intakeAnnotations(myShepherd, anns, null, false);
    }

    // ---- issue #1787 (spec §4.2): outcome record and dispatcher seam ----

    /** One option task of an intake run and what happened to its dispatch. */
    public static final class OptionResult {
        public final Task task;
        public final boolean vector;
        public boolean published = false;
        public String failureCode = null;

        OptionResult(Task task, boolean vector) {
            this.task = task;
            this.vector = vector;
        }
    }

    /** What {@link #intakeAnnotationsUnder} built, persisted and dispatched. */
    public static final class IntakeOutcome {
        public Task umbrella = null;
        public final List<OptionResult> options = new ArrayList<OptionResult>();
        public final List<String> skippedIaClasses = new ArrayList<String>();
        public boolean noWork = false;
        public boolean persistFailed = false;
    }

    /** Seam between the core and its two dispatch targets, so tests run with no OpenSearch, ml-service or file queue. */
    interface Dispatcher {
        /** Runs a vector option inline (Embedding.findMatchProspects); may throw. */
        void runVector(JSONObject opt, Task optionTask, Shepherd myShepherd);

        /** Publishes a HotSpotter job; false or an exception means "not published". */
        boolean publish(String context, String job, boolean fastlane) throws java.io.IOException;
    }

    private static final Dispatcher REAL_DISPATCHER = new Dispatcher() {
        @Override public void runVector(JSONObject opt, Task optionTask, Shepherd myShepherd) {
            Embedding.findMatchProspects(opt, optionTask, myShepherd);
        }

        @Override public boolean publish(String context, String job, boolean fastlane)
        throws java.io.IOException {
            if (fastlane) return org.ecocean.servlet.IAGateway.addToDetectionQueue(context, job);
            return org.ecocean.servlet.IAGateway.addToQueue(context, job);
        }
    };
    private static volatile Dispatcher dispatcher = REAL_DISPATCHER;

    /** Test hook; null restores the real dispatcher. */
    static void setDispatcherForTests(Dispatcher d) {
        dispatcher = (d == null) ? REAL_DISPATCHER : d;
    }

    /**
     * Existing entry point, unchanged signature. The umbrella is NOT linked to
     * {@code parentTask} here (callers attach it afterwards, as before); the parent only
     * supplies the inherited parameters. Returns null when the run could not be persisted
     * (today's code continued blind after a swallowed commit failure).
     */
    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns,
        final Task parentTask, boolean fastlane) {
        JSONObject inherited = (parentTask == null) ? null : parentTask.getParameters();
        IntakeOutcome o = intakeCore(myShepherd, anns, null, inherited, fastlane);

        if ((o == null) || o.persistFailed) return null;
        return o.umbrella;
    }

    /**
     * Issue #1787 entry point for the ml-service match step (spec §4.2.1). When {@code parent}
     * is non-null the umbrella is linked under it BEFORE the persist commit. Returns null only
     * for an empty annotation list.
     */
    public static IntakeOutcome intakeAnnotationsUnder(Shepherd myShepherd, List<Annotation> anns,
        Task parent, JSONObject inheritedParams, boolean fastlane) {
        return intakeCore(myShepherd, anns, parent, inheritedParams, fastlane);
    }

    /** A childless umbrella without an option: no class had a default identification option. */
    public static boolean intakeProducedNoWork(Task umbrella) {
        if (umbrella == null) return false;
        if (umbrella.hasChildren()) return false;
        JSONObject p = umbrella.getParameters();
        return (p == null) || !p.has("ibeis.identification");
    }

    /**
     * Inherited parameters minus the keys that belong to a run (the new run sets its own).
     * Also applied where a root's parameters are copied into detection tasks
     * (intakeMediaAssetsOneSpecies) and onto a substituted handleRest root, so a run id can
     * never leak across a pipeline boundary (spec §4.0). Never returns null; never mutates.
     */
    static JSONObject scrubRunParams(JSONObject inherited) {
        JSONObject p = (inherited == null) ? new JSONObject() : new JSONObject(inherited.toString());
        p.remove(Task.PARAM_RUN_ID);
        p.remove(Task.PARAM_V2_MATCH);
        p.remove(Task.PARAM_OPTION);
        p.remove("ibeis.identification");
        return p;
    }

    private static void markTaskError(Task t, String code, String message) {
        t.setStatus("error");
        t.setStatusDetailsAddError(code, message);
        t.setCompletionDateInMilliseconds();
    }

    // §4.2.3 step 3: a vector dispatch that threw may have attached unfinished leaves
    private static void terminalizeUnfinishedChildren(Task option, String code, String message) {
        if (option.getChildren() == null) return;
        for (Task kid : option.getChildren()) {
            if (!kid.statusInEndState()) markTaskError(kid, code, message);
        }
    }

    /*
     * The core: build the whole tree, persist it with a checked commit, then dispatch each
     * option through the Dispatcher seam. Never throws after the persist step; the outcome
     * always lists every option with its publication result (spec §4.2.3).
     */
    private static IntakeOutcome intakeCore(Shepherd myShepherd, List<Annotation> anns,
        Task parent, JSONObject inheritedParams, boolean fastlane) {
        if ((anns == null) || (anns.size() < 1)) return null;
        String context = myShepherd.getContext();
        IntakeOutcome outcome = new IntakeOutcome();

        // ---- build ----
        Task topTask = new Task();
        outcome.umbrella = topTask;
        JSONObject runParams = scrubRunParams(inheritedParams);
        runParams.put(Task.PARAM_RUN_ID, topTask.getId());   // every task of this run carries it
        topTask.setParameters(runParams);
        topTask.setObjectAnnotations(anns);
        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
        List<List<Annotation> > annotsByIaClass = binAnnotsByIaClass(anns);
        List<JSONObject> plannedOpts = new ArrayList<JSONObject>();
        List<Task> plannedTasks = new ArrayList<Task>();
        List<List<Annotation> > plannedAnns = new ArrayList<List<Annotation> >();
        for (List<Annotation> annsOneIAClass : annotsByIaClass) {
            String iaClass = annsOneIAClass.get(0).getIAClass();
            List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));
            // drop default=false options (a matchingAlgorithms parameter may re-add them below)
            if (opts != null) {
                Iterator<JSONObject> itr = opts.iterator();
                while (itr.hasNext()) {
                    if (!itr.next().optBoolean("default", true)) itr.remove();
                }
            }
            System.out.println("identOpts: " + opts);
            // a fresh copy of the inherited parameters per iaClass, as before
            JSONObject newTaskParams = new JSONObject(runParams.toString());
            if (newTaskParams.optJSONArray("matchingAlgorithms") != null) {
                JSONArray matchingAlgorithms = newTaskParams.optJSONArray("matchingAlgorithms");
                System.out.println("matchingAlgorithms1: " + matchingAlgorithms.toString());
                ArrayList<JSONObject> newOpts = new ArrayList<JSONObject>();
                int maLength = matchingAlgorithms.length();
                for (int y = 0; y < maLength; y++) {
                    newOpts.add(matchingAlgorithms.getJSONObject(y));
                }
                System.out.println("matchingAlgorithms2: " + newOpts.toString());
                if (newOpts.size() > 0) {
                    opts = newOpts;
                    System.out.println("Swapping opts for newOpts!!");
                }
            }
            if ((opts == null) || (opts.size() < 1)) {   // no ID for this iaClass
                outcome.skippedIaClasses.add(iaClass);
                continue;
            }
            if (fastlane) {
                newTaskParams.put("fastlane", true);
                newTaskParams.put("lane", "fast");
            }
            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
                // one IA class, one algorithm: the umbrella is *the* (only) option task
                JSONObject opt = opts.get(0);
                newTaskParams.put("ibeis.identification", (opt == null) ? "DEFAULT" : opt);
                newTaskParams.put(Task.PARAM_OPTION, true);
                if (Embedding.isVectorConfig(opt)) newTaskParams.put(Task.PARAM_V2_MATCH, true);
                topTask.setParameters(newTaskParams);
                plannedOpts.add(opt);
                plannedTasks.add(topTask);
                plannedAnns.add(annsOneIAClass);
            } else {
                for (int i = 0; i < opts.size(); i++) {
                    JSONObject opt = opts.get(i);
                    Task t = new Task();
                    t.setObjectAnnotations(annsOneIAClass);
                    JSONObject tp = new JSONObject(newTaskParams.toString());
                    tp.put("ibeis.identification", (opt == null) ? "DEFAULT" : opt);
                    tp.put(Task.PARAM_OPTION, true);
                    if (Embedding.isVectorConfig(opt)) tp.put(Task.PARAM_V2_MATCH, true);
                    t.setParameters(tp);
                    topTask.addChild(t);
                    plannedOpts.add(opt);
                    plannedTasks.add(t);
                    plannedAnns.add(annsOneIAClass);
                }
            }
        } // end for each iaClass
        if (plannedTasks.isEmpty()) {   // §4.2.3 step 4: terminal, for every caller
            outcome.noWork = true;
            markTaskError(topTask, "INVALID_MATCH_CONFIG",
                "no default identification options for iaClass(es) " + outcome.skippedIaClasses);
        }
        if (parent != null) topTask.setParent(parent);   // also parent.addChild(topTask)

        // ---- outcome record: every option is listed before anything is durable or dispatched ----
        // Scalar ids are snapshotted here, before the commit: post-commit logging never touches
        // a Task (Task.toString() reads persistent fields and lazily fetched collections, which
        // can throw on a broken PersistenceManager).
        String umbrellaId = topTask.getId();
        List<String> optionIds = new ArrayList<String>();
        for (int i = 0; i < plannedTasks.size(); i++) {
            outcome.options.add(new OptionResult(plannedTasks.get(i),
                Embedding.isVectorConfig(plannedOpts.get(i))));
            optionIds.add(plannedTasks.get(i).getId());
        }

        // ---- persist (checked) ----
        myShepherd.beginDBTransaction();   // no-op when already active
        myShepherd.getPM().makePersistent(topTask);   // children by reachability
        if (!myShepherd.commitDBTransactionWithStatus()) {
            System.out.println("ERROR: IA.intakeAnnotations() could not persist run " +
                topTask.getId() + "; nothing dispatched");
            outcome.persistFailed = true;
            return outcome;
        }
        // From here on the run is durable and this method never throws (spec §4.2.3): every
        // failure lands on the option task (best effort) and on the outcome, which the caller
        // always receives.
        try {
            myShepherd.beginDBTransaction();
        } catch (RuntimeException ex) {
            System.out.println("ERROR: IA.intakeAnnotations() could not restart the transaction after persisting run " +
                umbrellaId + ": " + ex + "; nothing dispatched");
            for (OptionResult r : outcome.options) r.failureCode = r.vector ? "DISPATCH_FAILED" : "QUEUE_FAILED";
            return outcome;   // the caller's checked commit fails (inactive transaction) and its recovery marks these tasks
        }
        if (outcome.noWork) {
            System.out.println("INFO: IA.intakeAnnotations() finished run " + umbrellaId + " with no work");
            return outcome;
        }

        // ---- dispatch: never throws; every failure lands on the outcome and, best effort, on the task ----
        try {
            String baseUrl = null;
            String baseUrlError = null;
            try {
                baseUrl = getBaseURL(context);
            } catch (RuntimeException ex) {
                baseUrlError = ex.toString();
                System.out.println("ERROR: IA.intakeAnnotations() getBaseURL() threw " + ex + " for run " + umbrellaId);
            }
            for (int i = 0; i < outcome.options.size(); i++) {
                OptionResult r = outcome.options.get(i);
                JSONObject opt = plannedOpts.get(i);
                Task t = r.task;
                String optionId = optionIds.get(i);
                try {
                    if (r.vector) {
                        dispatcher.runVector(opt, t, myShepherd);
                        r.published = true;
                        continue;
                    }
                    if (baseUrlError != null) {
                        r.failureCode = "QUEUE_FAILED";
                        markTaskError(t, "QUEUE_FAILED", "no base URL for the queue job: " + baseUrlError);
                        continue;
                    }
                    JSONArray annArr = new JSONArray();
                    for (Annotation ann : plannedAnns.get(i)) {
                        annArr.put(ann.getId());
                    }
                    JSONObject aj = new JSONObject();
                    aj.put("annotationIds", annArr);
                    JSONObject qjob = new JSONObject();
                    qjob.put("identify", aj);
                    qjob.put("taskId", optionId);
                    qjob.put("__context", context);
                    qjob.put("__baseUrl", baseUrl);
                    if (opt != null) qjob.put("opt", opt);
                    if (fastlane) {
                        qjob.put("fastlane", true);
                        qjob.put("lane", "fast");
                    }
                    boolean sent = false;
                    try {
                        t.setQueueResumeMessage(qjob.toString());
                        sent = dispatcher.publish(context, qjob.toString(), fastlane);
                    } catch (Exception ex) {
                        System.out.println("ERROR[" + i + "]: IA.intakeAnnotations() addToQueue() threw " + ex +
                            " for option " + optionId);
                    }
                    if (sent) {
                        r.published = true;
                    } else {
                        r.failureCode = "QUEUE_FAILED";
                        markTaskError(t, "QUEUE_FAILED", "identification job could not be published to the queue");
                    }
                    System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " +
                        plannedAnns.get(i).size() + " annots; queued? = " + sent + "; option " + optionId);
                } catch (RuntimeException ex) {
                    // a vector dispatch that threw, or anything unexpected in the HotSpotter branch
                    r.failureCode = r.vector ? "DISPATCH_FAILED" : "QUEUE_FAILED";
                    System.out.println("ERROR[" + i + "]: IA.intakeAnnotations() dispatch threw " + ex +
                        " on option " + optionId);
                    ex.printStackTrace();
                    try {
                        markTaskError(t, r.failureCode, "dispatch threw: " + ex);
                        terminalizeUnfinishedChildren(t, r.failureCode, "option dispatch threw before this leaf finished");
                    } catch (RuntimeException ex2) {   // e.g. a detached object after a broken transaction
                        System.out.println("ERROR[" + i + "]: IA.intakeAnnotations() could not mark option " +
                            optionId + ": " + ex2);
                    }
                }
            }
        } catch (RuntimeException ex) {   // anything outside the per-option guards (e.g. a dead PersistenceManager)
            System.out.println("ERROR: IA.intakeAnnotations() dispatch phase aborted for run " + umbrellaId + ": " + ex);
            ex.printStackTrace();
            for (OptionResult r : outcome.options) {
                if (!r.published && (r.failureCode == null)) r.failureCode = r.vector ? "DISPATCH_FAILED" : "QUEUE_FAILED";
            }
        }
        System.out.println("INFO: IA.intakeAnnotations() finished run " + umbrellaId + " with " +
            outcome.options.size() + " option(s)");
        return outcome;
    }

    public static List<List<Annotation> > binAnnotsByIaClass(List<Annotation> anns) {
        System.out.println("binAnnotsByIaClass called on " + anns.size() + " annots");
        Map<String, List<Annotation> > iaClassToAnns = new HashMap<String, List<Annotation> >();
        for (Annotation ann : anns) {
            String iaClass = ann.getIAClass();
            // Treat blank (not just null) as classless: a "" iaClass would
            // otherwise form a degenerate "" bin that resolves no ident opts.
            if (!Util.stringExists(iaClass)) continue;
            List<Annotation> iaClassList = iaClassToAnns.getOrDefault(iaClass,
                new ArrayList<Annotation>());
            iaClassList.add(ann);
            iaClassToAnns.put(iaClass, iaClassList);
        }
        System.out.println("binAnnotsByIaClass binned them into " + iaClassToAnns.keySet().size() +
            " bins: " + iaClassToAnns.keySet());
        return new ArrayList<List<Annotation> >(iaClassToAnns.values());
    }

    // possibly (should?) have .taskId, and *definitely* should have .__context and .__baseUrl
    // note: this is processed *from the queue* and as such does not have "output"
    public static void handleRest(JSONObject jin) {
        System.out.println("JIN JIN JIN: " + jin);
        if (jin == null) return;
        String context = jin.optString("__context", null);
        if (context == null)
            throw new RuntimeException("IA.handleRest(): passed data has no __context");
        Shepherd myShepherd = new Shepherd(context);

        // check if these should be directed through the fastlane
        boolean fastlane = false;
        if (jin.optBoolean("fastlane", false)) { fastlane = true; }
        myShepherd.setAction("IA.handleRest");
        myShepherd.beginDBTransaction();
        try {
            String taskId = jin.optString("taskId", Util.generateUUID());
            Task topTask = Task.load(taskId, myShepherd);
            if (topTask == null) topTask = new Task(taskId);
            if (fastlane) topTask.addParameter("fastlane", true);
            myShepherd.storeNewTask(topTask);
            JSONObject opt = jin.optJSONObject("opt"); // should use this to decide how to branch differently than "default"
            JSONArray mlist = jin.optJSONArray("mediaAssetIds");
            if ((mlist != null) && (mlist.length() > 0)) {
                System.out.println("MLIST: " + mlist);
                List<MediaAsset> mas = new ArrayList<MediaAsset>();
                for (int i = 0; i < mlist.length(); i++) {
                    int mid = mlist.optInt(i, -1);
                    if (mid < 1) continue;
                    MediaAsset ma = MediaAssetFactory.load(mid, myShepherd);
                    System.out.println(i + " -> " + ma);
                    if (ma == null) continue;
                    mas.add(ma);
                }
                Task mtask = intakeMediaAssets(myShepherd, mas, topTask);
                System.out.println("INFO: IA.handleRest() just intook MediaAssets as " + mtask +
                    " for (parent) " + topTask);
                topTask.addChild(mtask);
            }
            JSONArray alist = jin.optJSONArray("annotationIds");
            if ((alist != null) && (alist.length() > 0)) {
                List<Annotation> anns = new ArrayList<Annotation>();
                for (int i = 0; i < alist.length(); i++) {
                    String aid = alist.optString(i, null);
                    if (aid == null) continue;
                    Annotation ann = ((Annotation)(myShepherd.getPM().getObjectById(
                        myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
                    if (ann == null) continue;
                    anns.add(ann);
                }
                // okay, if we are sending another ID job from the hburger menu, the media asset needs to be added to your top level 'root' task,
                // or else you will link to the original root task
                List<MediaAsset> masForNewRoot = new ArrayList<>();
                for (Annotation ann : anns) {
                    MediaAsset ma = ann.getMediaAsset();
                    if (ma != null && !masForNewRoot.contains(ma)) {
                        masForNewRoot.add(ma);
                    }
                }
                // i cant think of a scenario where we would get here and accidently double-add mas... but jic
                for (MediaAsset ma : masForNewRoot) {
                    if (!topTask.getObjectMediaAssets().contains(ma)) {
                        topTask.addObject(ma);
                    }
                }
                Task atask = intakeAnnotations(myShepherd, anns, topTask, fastlane);
                System.out.println("INFO: IA.handleRest() just intook Annotations as " + atask +
                    " for " + topTask);
                myShepherd.getPM().refresh(topTask);
                topTask.addChild(atask);
                topTask.setModified();
                myShepherd.getPM().makePersistent(atask);
            }
            myShepherd.commitDBTransaction();
        } catch (Exception e) {
            e.printStackTrace();
            myShepherd.rollbackDBTransaction();
        } finally {
            myShepherd.closeDBTransaction();
        }
    }

    // via IAGateway servlet, we handle the work
    public static void handleGet(HttpServletRequest request, HttpServletResponse response)
    throws java.io.IOException {
        // JSONObject rtn = queueCallback(request);
        JSONObject rtn = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("IA.handleGet");
        myShepherd.beginDBTransaction();
        String taskId = request.getParameter("taskId");
        if (taskId != null) {
            Task task = Task.load(taskId, myShepherd);
            if (task == null) {
                response.sendError(404, "Not found: taskId=" + taskId);
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                return;
            }
            rtn.put("success", true);
            rtn.remove("error");
            rtn.put("task",
                task.toJSONObject(Util.requestParameterSet(request.getParameter(
                    "includeChildren"))));
        }
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        out.println(rtn.toString());
        out.close();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return;
    }

    public static String getBaseURL(String context) {
        String url = CommonConfiguration.getServerURL(context);
        String containerName = CommonConfiguration.getProperty("containerName", context);

        if (containerName != null && !"".equals(containerName)) {
            containerName = containerName.trim();
            System.out.println("INFO: Wildbook is containerized: Server getBaseURL is returning: " +
                containerName + "");
            url = url.replace("localhost", containerName);
        }
        System.out.println("INFO: Server getBaseURL is returning " + url);
        return url;
    }

    // (optional!) Taxonomy will append "_Scientific_name" to label and try that.  if not available, then try just label.
    public static String getProperty(String context, String label, Taxonomy tax, String def) {
        if ((tax != null) && (tax.getScientificName() != null)) {
            String propKey = label + "_".concat(tax.getScientificName()).replaceAll(" ", "_");
            System.out.println("[INFO] IA.getProperty() using propKey=" + propKey + " based on " +
                tax);
            String val = getProperty(context, propKey, (String)null);
            if (val != null) return val;
        }
        return IA.getProperty(context, label, def);
    }

    public static String getProperty(String context, String label, Taxonomy tax) { // no-default version
        return getProperty(context, label, tax, null);
    }

    public static String getProperty(String context, String label) { // no-default, no-taxonomy
        return getProperty(context, label, (String)null);
    }

    public static String getProperty(String context, String label, String def) {
        Properties p = getProperties(context);

        if (p == null) {
            System.out.println("IA.getProperty(" + label +
                ") has no properties; IA.properties unavailable?");
            return null;
        }
        return p.getProperty(label, def);
    }

    private static Properties getProperties(String context) {
        try {
            return ShepherdProperties.getProperties(PROP_FILE, "", context);
        } catch (Exception ex) {
            return null;
        }
    }

    public static void log(String msg) {
        System.out.println(new org.joda.time.DateTime() + " " + msg);
    }
}
