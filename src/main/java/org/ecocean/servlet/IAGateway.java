package org.ecocean.servlet;

import org.ecocean.AccessControl;
import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.identity.*;
import org.ecocean.media.*;
import org.ecocean.Occurrence;
import org.ecocean.queue.*;
import org.ecocean.Resolver;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IAGateway extends HttpServlet {
    private static Queue IAQueue = null;
    private static Queue detectionQueue = null;
    private static Queue IACallbackQueue = null;

    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        // "v2" is new IA package stuff -- so we just pass-thru and exit
        String qstr = request.getQueryString();

        // this seems to feed iaResults.jsp and is an unexpected dependency and extra URL call that shouldn't be needed
        if ((qstr != null) && (qstr.matches(".*\\bv2\\b.*"))) {
            response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
            IA.handleGet(request, response);
            return;
        }
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setHeader("Access-Control-Allow-Origin", "*"); // allow us stuff from localhost
        String qstr = request.getQueryString();
        // duplicated in both doGet and doPost
        if ((qstr != null) && (qstr.matches(".*\\bcallback\\b.*"))) {
            JSONObject rtn = queueCallback(request);
            response.setContentType("text/plain");
            PrintWriter out = response.getWriter();
            out.println(rtn.toString());
            out.close();
            return;
        }
        String context = ServletUtilities.getContext(request); // note! this *must* be run after postStream stuff above

        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
        JSONObject res = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
        String taskId = Util.generateUUID();
        res.put("taskId", taskId);
        String baseUrl = null;
        try {
            String containerName = IA.getProperty("context0", "containerName");
            baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
            if (containerName != null && containerName != "") {
                baseUrl = baseUrl.replace("localhost", containerName);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IAGateway9");
        myShepherd.beginDBTransaction();
        try {
            // v2 "forces" queueing -- onward to the glorious future!
            // sendtoIAscripts for bulk command line detection use this v2 option
            // uses detection queue
            if (j.optBoolean("enqueue", false) || j.optBoolean("v2", false)) { // short circuits and just blindly writes out to queue and is done!
                                                                               // magic?
                // TODO if queue is not active/okay, fallback to synchronous???
                // TODO could probably add other stuff (e.g. security/user etc)
                j.put("__context", context);
                j.put("__baseUrl", baseUrl);
                j.put("__enqueuedByIAGateway", System.currentTimeMillis());
                // incoming json *probably* (should have) has taskId set... but if not i guess we use the one we generated???
                if (j.optString("taskId", null) != null) {
                    taskId = j.getString("taskId");
                    res.put("taskId", taskId);
                } else {
                    j.put("taskId", taskId);
                }
                Task task = Task.load(taskId, myShepherd);
                if (task == null) task = new Task(taskId);
                JSONObject tparams = j.optJSONObject("taskParameters"); // optional
                if (tparams == null) tparams = new JSONObject(); // but we want it, to set user:
                User tuser = AccessControl.getUser(request, myShepherd);
                if (tuser == null) { // "anonymous" but we want to make sure we zero these out to prevent them from being passed in
                    tparams.remove("userId");
                    tparams.remove("username");
                } else {
                    tparams.put("userId", tuser.getUUID());
                    tparams.put("username", tuser.getUsername());
                }
                task.setParameters(tparams);
                myShepherd.storeNewTask(task);
                myShepherd.updateDBTransaction(); // hack
                // myShepherd.closeDBTransaction();

                boolean ok = false;
                if (j.optJSONArray("annotationIds") != null) {
                    // if this is just a single Encounter call, put it in the fast/detection lane to unblock small batch users
                    if (j.optBoolean("fastlane", false)) {
                        task.setQueueResumeMessage(j.toString());
                        ok = addToDetectionQueue(context, j.toString());
                    } else {
                        task.setQueueResumeMessage(j.toString());
                        ok = addToQueue(context, j.toString());
                    }
                } else {
                    task.setQueueResumeMessage(j.toString());
                    ok = addToDetectionQueue(context, j.toString());
                }
                if (ok) {
                    System.out.println("INFO: taskId=" + taskId + " enqueued successfully");
                    res.remove("error");
                } else {
                    System.out.println("ERROR: taskId=" + taskId +
                        " was NOT enqueued successfully");
                    res.put("error", "addToQueue() returned false");
                }
                res.put("success", ok);
            } else if (j.optJSONObject("detect") != null) {
                res = _doDetect(j, res, myShepherd, baseUrl);
            } else if (j.optJSONObject("identify") != null) {
                boolean fastlane = false;
                if (j.optBoolean("fastlane", false)) { fastlane = true; }
                res = _doIdentify(j, res, myShepherd, context, baseUrl, fastlane);
            }
            // bulk detection from import.jsp uses this area
            // uses detection queue
            else if (j.optJSONObject("bulkImport") != null) {
                res = handleBulkImport(j, res, myShepherd, context, baseUrl);
            } else if (j.optJSONObject("resolver") != null) {
                res = Resolver.processAPIJSONObject(j.getJSONObject("resolver"), myShepherd);
            } else {
                res.put("error", "unknown POST command");
                res.put("success", false);
            }
        } catch (Exception ex) {
            System.out.println("Hit an exception in IAGateway:IAGateway9.");
            ex.printStackTrace();
            res.put("error", "exception in handling IAGateway input: " + ex.toString());
            res.put("success", false);
        } finally {
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
        }
        res.put("_in", j);

        out.println(res.toString());
        out.close();
    }

    // TODO wedge in IA.intake here i guess? (once it exists)
    public static JSONObject _doDetect(JSONObject jin, JSONObject res, Shepherd myShepherd,
        String baseUrl)
    throws ServletException, IOException {
        if (res == null)
            throw new RuntimeException("IAGateway._doDetect() called without res passed in");
        String taskId = res.optString("taskId", null);
        if (taskId == null)
            throw new RuntimeException("IAGateway._doDetect() has no taskId passed in");
        System.out.println("PRELOADED");
        Task task = Task.load(taskId, myShepherd); // might be null in some cases, such as non-queued  ... maybe FIXME when we dump cruft?
        System.out.println("LOADED???? " + taskId + " --> " + task);
        String context = myShepherd.getContext();
        if (baseUrl == null) return res;
        if (jin == null) return res;
        JSONObject j = jin.optJSONObject("detect");
        if (j == null) return res; // "should never happen"

        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        List<MediaAsset> needOccurrences = new ArrayList<MediaAsset>();
        ArrayList<String> validIds = new ArrayList<String>();
        if (j.optJSONArray("mediaAssetIds") != null) {
            JSONArray ids = j.getJSONArray("mediaAssetIds");
            for (int i = 0; i < ids.length(); i++) {
                int id = ids.optInt(i, 0);
                if (id < 1) continue;
                myShepherd.beginDBTransaction();
                MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
                myShepherd.getPM().refresh(ma);
                if (ma != null) {
                    ma.setDetectionStatus(IBEISIA.STATUS_PROCESSING);
                    mas.add(ma);
                }
            }
        }
        /*
           else if (j.optJSONArray("mediaAssetSetIds") != null) {
            JSONArray ids = j.getJSONArray("mediaAssetSetIds");
            for (int i = 0 ; i < ids.length() ; i++) {
                MediaAssetSet set = myShepherd.getMediaAssetSet(ids.optString(i));
                if ((set != null) && (set.getMediaAssets() != null) && (set.getMediaAssets().size() > 0)) mas.addAll(set.getMediaAssets());
            }
           }
         */
        else {
            res.put("success", false);
            res.put("error", "unknown detect value");
        }
        if (mas.size() > 0) {
            if (task != null) {
                task.setObjectMediaAssets(mas);
                task.addParameter("ibeis.detection", true);
            }
            for (MediaAsset ma : mas) {
                validIds.add(Integer.toString(ma.getId()));
                if (ma.getOccurrence() == null) needOccurrences.add(ma);
            }
            boolean success = true;
            JSONObject detectArgs = jin.optJSONObject("__detect_args");
            if (detectArgs != null) detectArgs.put("jobid", taskId);
            String detectUrl = jin.optString("__detect_url");
            String detArgString = (detectArgs != null) ? detectArgs.toString() : null;
            System.out.println("_doDetect got detectUrl " + detectUrl + " and detectArgs " +
                detArgString);

            try {
                res.put("sendMediaAssets", IBEISIA.sendMediaAssetsNew(mas, context));
                JSONObject sent = IBEISIA.sendDetect(mas, baseUrl, context, myShepherd, detectArgs,
                    detectUrl);
                // JSONObject sent = IBEISIA.sendDetect(mas, baseUrl, context, myShepherd);
                res.put("sendDetect", sent);
                String jobId = null;
                if ((sent.optJSONObject("status") != null) &&
                    sent.getJSONObject("status").optBoolean("success", false))
                    jobId = sent.optString("response", null);
                res.put("jobId", jobId);
                IBEISIA.log(taskId, validIds.toArray(new String[validIds.size()]), jobId,
                    new JSONObject("{\"_action\": \"initDetect\"}"), context);
            } catch (Exception ex) {
                success = false;
                ex.printStackTrace();
                throw new IOException(ex.toString());
            }
            if (!success) {
                for (MediaAsset ma : mas) {
                    ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                }
            }
            res.remove("error");
            res.put("success", true);
        } else {
            res.put("error", "no valid MediaAssets");
        }
        return res;
    }

    // TODO not sure why we pass 'res' in but also it is the return value... potentially should be fixed; likely when we create IA package
    public static JSONObject _doIdentify(JSONObject jin, JSONObject res, Shepherd myShepherd,
        String context, String baseUrl, boolean fastlane)
    throws ServletException, IOException {
        if (res == null)
            throw new RuntimeException("IAGateway._doIdentify() called without res passed in");
        String taskId = res.optString("taskId", null);
        if (taskId == null)
            throw new RuntimeException("IAGateway._doIdentify() has no taskId passed in");
        if (baseUrl == null) return res;
        if (jin == null) return res;
        JSONObject j = jin.optJSONObject("identify");
        if (j == null) return res; // "should never happen"
/*
    TODO? right now this 'opt' is directly from IBEISIA.identOpts() ????? hmmmm....
    note then that for IBEIS this effectively gets mapped via queryConfigDict to usable values we also might consider incorporating j.opt (passed
       within identify:{} object itself, from the api/gateway) ???
 */
        JSONObject opt = jin.optJSONObject("opt");
        ArrayList<Annotation> anns = new ArrayList<Annotation>(); // what we ultimately run on.  occurrences are irrelevant now right?
        ArrayList<String> validIds = new ArrayList<String>();
        int limitTargetSize = j.optInt("limitTargetSize", -1); // really "only" for debugging/testing, so use if you know what you are doing

        // currently this implies each annotation should be sent one-at-a-time TODO later will be allow clumping (to be sent as multi-annotation
        // query lists.... *when* that is supported by IA
        JSONArray alist = j.optJSONArray("annotationIds");
        if ((alist != null) && (alist.length() > 0)) {
            for (int i = 0; i < alist.length(); i++) {
                String aid = alist.optString(i, null);
                if (aid == null) continue;
                Annotation ann = ((Annotation)(myShepherd.getPM().getObjectById(
                    myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
                if (ann == null) continue;
                anns.add(ann);
                validIds.add(aid);
            }
        }
        // i think that "in the future" co-occurring annotations should be sent together as one set of query list; but since we dont have support for
        // that
        // now, we just send these all in one at a time.  hope that is good enough!   TODO
        JSONArray olist = j.optJSONArray("occurrenceIds");
        if ((olist != null) && (olist.length() > 0)) {
            for (int i = 0; i < olist.length(); i++) {
                String oid = olist.optString(i, null);
                if (oid == null) continue;
                Occurrence occ = ((Occurrence)(myShepherd.getPM().getObjectById(
                    myShepherd.getPM().newObjectIdInstance(Occurrence.class, oid), true)));
// System.out.println("occ -> " + occ);
                if (occ == null) continue;
                List<MediaAsset> mas = occ.getAssets();
// System.out.println("mas -> " + mas);
                if ((mas == null) || (mas.size() < 1)) continue;
                for (MediaAsset ma : mas) {
                    ArrayList<Annotation> maAnns = ma.getAnnotations();
// System.out.println("maAnns -> " + maAnns);
                    if ((maAnns == null) || (maAnns.size() < 1)) continue;
                    for (Annotation ann : maAnns) {
                        if (validIds.contains(ann.getId())) continue;
                        anns.add(ann);
                        validIds.add(ann.getId());
                    }
                }
            }
        }
        System.out.println("anns -> " + anns);

        Task parentTask = Task.load(taskId, myShepherd);
        if (parentTask == null) {
            System.out.println("WARNING: IAGateway._doIdentify() could not load Task id=" + taskId +
                "; creating it... yrros");
            parentTask = new Task(taskId);
        }
        JSONArray taskList = new JSONArray();
/* currently we are sending annotations one at a time (one per query list) but later we will have to support clumped sets...
   things to consider for that - we probably have to further subdivide by species ... other considerations?   */
        List<Task> subTasks = new ArrayList<Task>();
        if (anns.size() > 1) { // need to create child Tasks
            JSONObject params = parentTask.getParameters();
            parentTask.setParameters((String)null); // reset this, kids inherit params
            for (int i = 0; i < anns.size(); i++) {
                Task newTask = new Task(parentTask);
                newTask.setParameters(params);
                newTask.addObject(anns.get(i));
                myShepherd.storeNewTask(newTask);
                myShepherd.beginDBTransaction();
                subTasks.add(newTask);
            }
            myShepherd.storeNewTask(parentTask);
            myShepherd.beginDBTransaction();
        } else { // we just use the existing "parent" task
            subTasks.add(parentTask);
        }
        for (int i = 0; i < anns.size(); i++) {
            Annotation ann = anns.get(i);
            JSONObject queryConfigDict = IBEISIA.queryConfigDict(myShepherd, opt);
            JSONObject taskRes = new JSONObject();
            Task subTask = subTasks.get(i);
            try {
                taskRes = _sendIdentificationTask(ann, context, baseUrl, queryConfigDict, null,
                    limitTargetSize, subTask, myShepherd, fastlane);
            } catch (Exception ex) { // unsure if maybe _some_ exceptions should be treated differently here?
                System.out.println("subTask failure on " + subTask + ": " + ex.toString());
                taskRes.put("success", false);
                taskRes.put("error", ex.toString());
                System.out.println(">>>>>>> parentTask: " + parentTask);
                JSONObject jobj = new JSONObject();
                jobj.put("identify", new JSONObject());
                jobj.put("opt", opt);
                jobj.getJSONObject("identify").put("annotationIds", new JSONArray());
                jobj.getJSONObject("identify").getJSONArray("annotationIds").put(ann.getId());
                jobj.put("taskId", subTask.getId());
                jobj.put("__baseUrl", baseUrl);
                jobj.put("__context", context);
                jobj.put("__queueActualRetries", jin.optInt("__queueActualRetries", 0));
                jobj.put("__queueRetries", jin.optInt("__queueRetries", 0));
                jobj.put("__queueStart", jin.optLong("__queueStart", System.currentTimeMillis()));
                System.out.println("_doIdentify() requeueing from jin=" + jin);
                System.out.println("_doIdentify() requeueing as jobj=" + jobj);
                requeueJob(jobj, true);
            }
            taskRes.put("subTaskId", subTask.getId());
            taskRes.put("subTaskIndex", i);
            taskList.put(taskRes);
            System.out.println("subTask[" + i + "] => " + taskRes.toString());
        }
        if (limitTargetSize > -1) res.put("_limitTargetSize", limitTargetSize);
        res.put("tasks", taskList);
        res.put("success", true);
        return res;
    }

    private static JSONObject _sendIdentificationTask(Annotation ann, String context,
        String baseUrl, JSONObject queryConfigDict, JSONObject userConfidence, int limitTargetSize,
        Task task, Shepherd myShepherd, boolean fastlane)
    throws IOException {
        // String iaClass = ann.getIAClass();
        boolean success = true;
        String annTaskId = "UNKNOWN_" + Util.generateUUID();

        if (task != null) annTaskId = task.getId();
        JSONObject taskRes = new JSONObject();
        taskRes.put("taskId", annTaskId);
        JSONArray jids = new JSONArray();
        jids.put(ann.getId()); // for now there is only one
        taskRes.put("annotationIds", jids);
        System.out.println("+ starting ident task " + annTaskId);
        JSONObject shortCut = IAQueryCache.tryTargetAnnotationsCache(context, ann, taskRes,
            myShepherd);
        if (shortCut != null) return shortCut;
        // Shepherd myShepherd = new Shepherd(context);
        // myShepherd.setAction("IAGateway._sendIdentificationTask");
        // myShepherd.beginDBTransaction();

        try {
            // TODO we might want to cache this examplars list (per species) yes?

            ///note: this can all go away if/when we decide not to need limitTargetSize
            ArrayList<Annotation> matchingSet = null;
            if (limitTargetSize > -1) {
                matchingSet = ann.getMatchingSet(myShepherd);
                if ((matchingSet == null) || (matchingSet.size() < 5)) {
                    System.out.println("=======> Small matching set for this Annotation id= " +
                        ann.getId());
                    System.out.println("=======> Set size is: " + matchingSet.size());
                    System.out.println("=======> Specific Epithet is: " +
                        ann.findEncounter(myShepherd).getSpecificEpithet() + "    Genus is: " +
                        ann.findEncounter(myShepherd).getGenus());
                }
                if (matchingSet.size() > limitTargetSize) {
                    System.out.println(
                        "WARNING: limited identification matchingSet list size from " +
                        matchingSet.size() + " to " + limitTargetSize);
                    matchingSet = new ArrayList<Annotation>(matchingSet.subList(0,
                        limitTargetSize));
                }
                taskRes.put("matchingSetSize", matchingSet.size());
            }
            /// end can-go-away

            ArrayList<Annotation> qanns = new ArrayList<Annotation>();
            qanns.add(ann);
            IBEISIA.waitForIAPriming();
            JSONObject sent = IBEISIA.beginIdentifyAnnotations(qanns, matchingSet, queryConfigDict,
                userConfidence, myShepherd, task, baseUrl, fastlane);
            if (!sent.optBoolean("success", false) && sent.toString().indexOf("emptyTargetAnnotations")==-1) {

                String errorMsg = sent.optString("error", "(unknown error)");
                System.out.println("beginIdentifyAnnotations() was unsuccessful due to " +
                    errorMsg + "; hopefully we requeue");
                throw new IOException("beginIdentifyAnnotations() failed due to " + errorMsg);
 
            }
            ann.setIdentificationStatus(IBEISIA.STATUS_PROCESSING);
            taskRes.put("beginIdentify", sent);
            String jobId = null;
            if ((sent.optJSONObject("status") != null) &&
                sent.getJSONObject("status").optBoolean("success", false))
                jobId = sent.optString("response", null);
            taskRes.put("jobId", jobId);
            // validIds.toArray(new String[validIds.size()])
            IBEISIA.log(annTaskId, ann.getId(), jobId,
                new JSONObject("{\"_action\": \"initIdentify\"}"), context);

            // WB-1665: log as error when we cannot send ident task
            System.out.println("WB-1665 checking for error state in sent=" + sent);
            if (!sent.optBoolean("success", false) || (sent.optJSONObject("error") != null)) {
                System.out.println("_sendIdentificationTask() unable to initiate identification: " +
                    sent);
                ann.setIdentificationStatus(IBEISIA.STATUS_ERROR);
                sent.put("_action", "error");
                IBEISIA.log(annTaskId, ann.getId(), jobId, sent, context);
                taskRes.put("error", sent.optJSONObject("error"));
            }
        } catch (Exception ex) {
            success = false;
            throw new IOException(ex.toString());
        } finally {
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
        }
/* TODO ?????????
            if (!success) {
                for (MediaAsset ma : mas) {
                    ma.setDetectionStatus(IBEISIA.STATUS_ERROR);
                }
            }
 */
        return taskRes;
    }

    /*
       public static JSONObject expandAnnotation(String annID, Shepherd myShepherd, HttpServletRequest request) {
        if (annID == null) return null;
        JSONObject rtn = new JSONObject();
        Annotation ann = null;
        try {
            ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, annID), true)));
        } catch (Exception ex) {}
        if (ann != null) {
            rtn.put("annotationID", annID);
            Encounter enc = Encounter.findByAnnotation(ann, myShepherd);
            if (enc != null) {
                JSONObject jenc = new JSONObject();
                jenc.put("catalogNumber", enc.getCatalogNumber());
                jenc.put("date", enc.getDate());
                jenc.put("sex", enc.getSex());
                jenc.put("verbatimLocality", enc.getVerbatimLocality());
                jenc.put("locationID", enc.getLocationID());
                jenc.put("individualID", enc.getIndividualID());
                jenc.put("otherCatalogNumbers", enc.getOtherCatalogNumbers());
                rtn.put("encounter", jenc);
            }
            MediaAsset ma = ann.getMediaAsset();
            if (ma != null) {
                try {
                    rtn.put("mediaAsset", new JSONObject(ma.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject()).toString()));
                } catch (Exception ex) {}
            }
        }
        return rtn;
       }
     */
    public static JSONObject taskSummary(JSONArray taskIds, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();

        if ((taskIds == null) || (taskIds.length() < 1)) return rtn;
        for (int i = 0; i < taskIds.length(); i++) {
            String annId = taskIds.optString(i);
            if (annId == null) continue;
            ArrayList<IdentityServiceLog> logs = IdentityServiceLog.summaryForAnnotationId(annId,
                myShepherd);
            if ((logs != null) && (logs.size() > 0)) {
                JSONObject tasks = new JSONObject();
                for (IdentityServiceLog l : logs) {
                    if (l.getTaskID() == null) continue;
                    JSONObject t = new JSONObject();
                    if (l.getStatus() != null) t.put("status", new JSONObject(l.getStatus()));
                    t.put("timestamp", l.getTimestamp());
                    tasks.put(l.getTaskID(), t);
                }
                rtn.put(annId, tasks);
            }
        }
        return rtn;
    }

    // yeah maybe this should be merged into MediaAsset duh
    private String mediaAssetIdToUUID(int id) {
        byte b1 = (byte)77;
        byte b2 = (byte)97;
        byte[] b = new byte[6];

        b[0] = b1;
        b[1] = b2;
        b[2] = (byte)(id >> 24);
        b[3] = (byte)(id >> 16);
        b[4] = (byte)(id >> 8);
        b[5] = (byte)(id >> 0);
        return UUID.nameUUIDFromBytes(b).toString();
    }

    // parse whether the html returned means we need to adjust the http header return code
    private void setErrorCode(HttpServletResponse response, String html)
    throws IOException {
        if (html == null) return;
        int code = 500;
        int a = html.indexOf("error-code=");
        int b = html.indexOf("class=\"response-error");
        if ((a < 0) && (b < 0)) return; // must have at least one
        if (a > -1) {
            try {
                code = Integer.parseInt(html.substring(18, 21));
            } catch (NumberFormatException ex) {}
        }
        String msg = "unknown error";
        int m = html.indexOf(">");
        if (m > -1) {
            msg = html.substring(m + 1);
            m = msg.indexOf("<");
            if (m > -1) {
                msg = msg.substring(0, m);
            }
        }
        System.out.println("ERROR: IAGateway.sendError() reporting " + code + ": " + msg);
        response.sendError(code, msg);
    }

    // resendBulkImportID.jsp from bulk import goes through here after IA.java
    // encounter.jsp send to ID goes through here
    public static boolean addToQueue(String context, String content)
    throws IOException {
        System.out.println("IAGateway.addToQueue() publishing: " + content);
        getIAQueue(context).publish(content);
        return true;
    }

    // also used by EncounterForm with new Encounter submission
    public static boolean addToDetectionQueue(String context, String content)
    throws IOException {
        System.out.println("IAGateway.addToDetectionQueue() publishing: " + content);
        getDetectionQueue(context).publish(content);
        return true;
    }

    public static Queue getIAQueue(String context)
    throws IOException {
        // if (IAQueue != null) return IAQueue;
        IAQueue = QueueUtil.getBest(context, "IA");
        return IAQueue;
    }

    public static Queue getDetectionQueue(String context)
    throws IOException {
        // if (detectionQueue != null) return detectionQueue;
        detectionQueue = QueueUtil.getBest(context, "detection");
        return detectionQueue;
    }

    public static Queue getIACallbackQueue(String context)
    throws IOException {
        // if (IACallbackQueue != null) return IACallbackQueue;
        IACallbackQueue = QueueUtil.getBest(context, "IACallback");
        return IACallbackQueue;
    }

    // TODO clean this up!  now that this is moved here, there is probably lots of redundancy with above no?
    public static void processQueueMessage(String message) {
// System.out.println("DEBUG: IAGateway.processQueueMessage -> " + message);
        if (message == null) return;
        JSONObject jobj = null;
        try {
            jobj = new JSONObject(message);
        } catch (org.json.JSONException jex) {
            System.out.println(
                "WARNING: IAGateway.processQueueMessage() failed to parse json from '" + message +
                "' - " + jex.toString());
            return;
        }
        if (jobj == null) return; // would this ever happen? #bsts
        // this must have a taskId coming in, cuz otherwise how would (detached, async) caller know what it is!
        // __context and __baseUrl should be set -- this is done automatically in IAGateway, but if getting here by some other method, do the work!
        if (jobj.optBoolean("v2", false)) { // lets "new world" ia package do its thing
            IA.handleRest(jobj);
            return;
        }
        boolean requeue = false;
        boolean requeueIncrement = false;
        if ((jobj.optJSONObject("detect") != null) && (jobj.optString("taskId", null) != null)) {
            JSONObject res = new JSONObject("{\"success\": false}");
            res.put("taskId", jobj.getString("taskId"));
            String context = jobj.optString("__context", "context0");
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("IAGateway.processQueueMessage.detect");
            myShepherd.beginDBTransaction();
            String baseUrl = jobj.optString("__baseUrl", null);
            try {
                JSONObject rtn = _doDetect(jobj, res, myShepherd, baseUrl);
                System.out.println(
                    "INFO: IAGateway.processQueueMessage() 'detect' successful --> " +
                    rtn.toString());
                if (!rtn.optBoolean("success", false)) {
                    requeueIncrement = true;
                    requeue = true;
                    myShepherd.rollbackDBTransaction();
                } else {
                    myShepherd.commitDBTransaction();
                }
            } catch (Exception ex) {
                System.out.println(
                    "ERROR: IAGateway.processQueueMessage() 'detect' threw exception: " +
                    ex.toString());
                // now for certain returns, we want to increment our retry-ticker (this is TODO research in progress!)
                if (ex.toString().contains("HTTP error code : 500")) {
                    requeueIncrement = true;
                    requeue = true;
                }
                // error - don't requeue
                else if (ex.toString().contains("HTTP error code : 502")) {
                    requeueIncrement = true;
                    requeue = true;
                }
                // error - don't requeue
                else if (ex.toString().contains("HTTP error code : 608")) {
                    requeue = false;
                } else {
                    requeueIncrement = true;
                    requeue = true;
                }
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();
        } else if ((jobj.optJSONObject("identify") != null) && (jobj.optString("taskId",
            null) != null)) {                                                                               // ditto about taskId
            System.out.println("identify TOP!");
            JSONObject res = new JSONObject("{\"success\": false}");
            res.put("taskId", jobj.getString("taskId"));
            String context = jobj.optString("__context", "context0");
            boolean fastlane = false;
            if (jobj.optBoolean("fastlane", false)) {
                fastlane = true;
            }
            System.out.println(" > context = " + context);
            System.out.println(" > taskId = " + jobj.getString("taskId"));
            System.out.println(" > fastlane = " + jobj.optBoolean("fastlane", false));
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("IAGateway.processQueueMessage.identify");
            myShepherd.beginDBTransaction();
            String baseUrl = jobj.optString("__baseUrl", null);
            System.out.println("--- BEFORE _doIdentify() ---");
            try {
                // here jobj contains queryconfigdict somehow
                JSONObject rtn = _doIdentify(jobj, res, myShepherd, context, baseUrl, fastlane);
                System.out.println(
                    "INFO: IAGateway.processQueueMessage() 'identify' from successful --> " +
                    rtn.toString());
                if (!rtn.optBoolean("success", false)) {
                    requeueIncrement = true;
                    requeue = true;
                    myShepherd.rollbackDBTransaction();
                } else {
                    myShepherd.commitDBTransaction();
                }
            } catch (javax.jdo.JDOObjectNotFoundException ex) {
                System.out.println(
                    "ERROR: IAGateway.processQueueMessage() 'identify' from threw exception: " +
                    ex.toString());
                if (ex.toString().contains("HTTP error code : 500")) requeueIncrement = false;
                myShepherd.rollbackDBTransaction();
                requeue = false;
            } catch (Exception ex) {
                System.out.println(
                    "ERROR: IAGateway.processQueueMessage() 'identify' from threw exception: " +
                    ex.toString());
                if (ex.toString().contains("HTTP error code : 500")) {
                    requeueIncrement = true;
                    requeue = true;
                } else if (ex.toString().contains("Empty target annotation list")) {
                    requeue = false;
                } else { requeue = true; }
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();
        } else {
            System.out.println(
                "WARNING: IAGateway.processQueueMessage() unable to use json data in '" + message +
                "'; ignoring");
        }
        if (requeue) requeueJob(jobj, requeueIncrement);
    }

    public static boolean requeueJob(JSONObject jobj, final boolean increment) {
        int MAX_RETRIES = 30;
        long MAX_TIME_MILLIS = 2 * 24 * 60 * 60 * 1000;
        String context = jobj.optString("__context", "context0");
        String taskId = jobj.optString("taskId", "UNKNOWN_TASKID");
        long queueStart = jobj.optLong("__queueStart", System.currentTimeMillis());
        int actualRetries = jobj.optInt("__queueActualRetries", 0);
        int retries = jobj.optInt("__queueRetries", 0);

        if (retries < 0) retries = 0;
        long elapsed = System.currentTimeMillis() - queueStart;
        if (elapsed > MAX_TIME_MILLIS) retries = MAX_RETRIES + 1; // waiting around too long
        if (retries > MAX_RETRIES) {
            System.out.println("requeueJob(): completely failed taskId=" + taskId + " after " +
                MAX_RETRIES + " retries (or max time) in queue; giving up");
            return false;
        }
        System.out.println("requeueJob(): attempting to requeue taskId=" + taskId + " for retry " +
            retries + " out of " + MAX_RETRIES + " (actualRetries=" + actualRetries + "; start=" +
            queueStart + "; elapsed=" + elapsed + "; increment=" + increment + ")");
        final long sleepMillis = 1000;
        if (increment) retries++;
        actualRetries++;
        jobj.put("__queueStart", queueStart);
        jobj.put("__queueRetries", retries);
        jobj.put("__queueActualRetries", actualRetries);

        // now we fork background thread to *wait* and then add this to queue
        Runnable r = new Runnable() {
            public void run() {
                boolean requeueSuccess = false;
                long whileSleepMillis = 1000;
                while (!requeueSuccess) {
                    try {
                        if (increment) whileSleepMillis = 30000;
                        System.out.println("requeueJob(): backgrounding taskId=" + taskId);
                        try {
                            Thread.sleep(whileSleepMillis);
                        } catch (java.lang.InterruptedException ex) {}
                        if (jobj.optJSONObject("detect") != null || jobj.optBoolean("fastlane",
                            false)) {
                            addToDetectionQueue(context, jobj.toString());
                        } else {
                            addToQueue(context, jobj.toString());
                        }
                        requeueSuccess = true;
                    } catch (Exception ex) {
                        whileSleepMillis = 30000;
                        System.out.println(
                            ".....requeueJob() looping: failed to requeue addTo_Queue() taskId=" +
                            taskId + " due to " + ex.toString());
                        ex.printStackTrace();
                    }
                } // end while
            } // end run
        }; // end Runnable
        new Thread(r).start();

        return true;
    }

    public static void processCallbackQueueMessage(String message) {
        JSONObject jmsg = Util.stringToJSONObject(message);

        if (jmsg == null) {
            System.out.println("ERROR: processCallbackQueueMessage() failed to parse JSON from " +
                message);
            return;
        }
        System.out.println("processCallbackQueueMessage got: " + message);
        IBEISIA.callbackFromQueue(jmsg);
    }

    // weirdly (via StartupWildbook) stuff put in the queue is processed by.... the method right above us!  :)  :(
    private JSONObject queueCallback(HttpServletRequest request)
    throws IOException {
        JSONObject rtn = new JSONObject();
        JSONObject qjob = new JSONObject();
        String qid = Util.generateUUID();

        qjob.put("qid", qid);
        qjob.put("queryString", request.getQueryString());
        BufferedReader br = new BufferedReader(new InputStreamReader(request.getInputStream()));
        String line;
        String raw = "";
        while ((line = br.readLine()) != null) {
            raw += line;
        }
        br.close();
        qjob.put("dataRaw", raw);
        qjob.put("dataJson", Util.stringToJSONObject(raw));
        String context = ServletUtilities.getContext(request);
        Queue queue = getIACallbackQueue(context);
        qjob.put("context", context);
        qjob.put("rootDir", request.getSession().getServletContext().getRealPath("/"));
        qjob.put("requestMethod", request.getMethod());
        qjob.put("requestUri", request.getRequestURI());
        qjob.put("timestamp", System.currentTimeMillis());
        String baseUrl = null;
        try {
            baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
        } catch (java.net.URISyntaxException ex) {}
        qjob.put("baseUrl", baseUrl);
        // real IA sends "jobid=jobid-xxxx" as body on POST, but this gives us a url-based alternative (for testing)
        String jobId = request.getParameter("jobid");
        if (raw.startsWith("jobid=") && (raw.length() > 6)) jobId = raw.substring(6);
        qjob.put("jobId", jobId);

        System.out.println("qjob => " + qjob);
        queue.publish(qjob.toString());
        rtn.put("success", true);
        rtn.put("qid", qid);
        return rtn;
    }

    public static JSONObject handleBulkImport(JSONObject jin, JSONObject res, Shepherd myShepherd,
        String context, String baseUrl)
    throws ServletException, IOException {
        if (res == null)
            throw new RuntimeException("IAGateway.handleBulkImport() called without res passed in");
        if (baseUrl == null) return res;
        if (jin == null) return res;
        JSONObject taskParameters = jin.optJSONObject("taskParameters");
        String importTaskId = null;
        if (taskParameters != null) importTaskId = taskParameters.optString("importTaskId", null);
        ImportTask itask = null;
        Task parentTask = null;
        if (importTaskId != null) itask = myShepherd.getImportTask(importTaskId);
        if (itask != null) {
            parentTask = new Task(); // root task to hold all others, to connect to ImportTask
            parentTask.setParameters(taskParameters);
            myShepherd.storeNewTask(parentTask);
            itask.setIATask(parentTask);
            System.out.println("IAGateway.handleBulkImport() created parentTask " + parentTask +
                " to link to " + itask);
        }
        // JSONObject maMap = jin.optJSONObject("bulkImport");
        // System.out.println("IAGateway.handleBulkImport() preparing to parse " + maMap.keySet().size() + " encounter detection jobs");
        // if (maMap == null) return res;  // "should never happen"
        /*
            maMap is just js_jarrs from imports.jsp, basically { encID0: [ma0, ... maN], ... encIDX: [maY, .. maZ] }
            so we need 1 detection job per element
         */
        // JSONObject mapRes = new JSONObject();
        int okCount = 0;
        JSONArray maIds = new JSONArray();
        for (MediaAsset asset : itask.getMediaAssets()) {
            maIds.put(asset.getId());
        }
        // for (Object e: maMap.keySet()) {
        // String encId = (String)e;
        // JSONArray maIds = maMap.optJSONArray(encId);
        // if (maIds == null) {
        // mapRes.put(encId, "no JSONArray");
        // System.out.println("[ERROR] IAGateway.handleBulkImport() maMap could not find JSONArray of MediaAsset ids at encId key=" + encId);
        // continue;
        // }
        Task task = new Task();
        task.setParameters(taskParameters);
        myShepherd.storeNewTask(task);
        if (parentTask != null) parentTask.addChild(task);
        myShepherd.commitDBTransaction();
        // System.out.println("[INFO] IAGateway.handleBulkImport() enc " + encId + " created and queued " + task);
        JSONObject qjob = new JSONObject(jin.toString()); // clone it to start with so we get all same content
        qjob.remove("bulkImport"); // ... but then lose this
        qjob.put("taskId", task.getId());
        qjob.put("mediaAssetIds", maIds);
        qjob.put("v2", true);
        qjob.put("__context", context);
        qjob.put("__baseUrl", baseUrl);
        qjob.put("__handleBulkImport", System.currentTimeMillis());
        task.setQueueResumeMessage(qjob.toString());
        boolean ok = addToDetectionQueue(context, qjob.toString());
        if (ok) okCount++;
        // mapRes.put(encId, "task id=" + task.getId() + " queued=" + ok);
        // }
        // res.put("encounterCount", maMap.keySet().size());
        res.put("queuedCount", okCount);
        // res.put("mapResults", mapRes);
        res.remove("error");
        res.put("success", true);
        return res;
    }
}
