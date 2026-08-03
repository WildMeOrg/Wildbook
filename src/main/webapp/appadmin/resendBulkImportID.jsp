
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.cache.*,org.ecocean.ia.*,
org.json.*,org.ecocean.servlet.importer.ImportTask,org.ecocean.identity.IBEISIA,
java.io.*,java.util.*, java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>
<%@ page import="org.ecocean.shepherd.core.Shepherd" %>

<%
response.setContentType("application/json");
response.setCharacterEncoding("UTF-8");
response.setHeader("Access-Control-Allow-Origin", "*");

String context = ServletUtilities.getContext(request);
Shepherd myShepherd = null;   // opened inside try so a setup failure still hits the JSON catch

JSONObject res = new JSONObject();
res.put("success", false);
int httpStatus = HttpServletResponse.SC_OK;

String importIdTask = request.getParameter("importIdTask");
String algorithm = request.getParameter("algorithm");        // null or "hotspotter"
boolean unidentifiedOnly = "true".equals(request.getParameter("unidentifiedOnly"));
boolean hotspotterOnly = "hotspotter".equals(algorithm);
// nonblank check done directly: Util.stringExists() treats "none"/"unknown" as absent,
// which would let algorithm=none skip the 400 and run as an ordinary first pass.
boolean algorithmProvided = (algorithm != null) && (algorithm.trim().length() > 0);

List<String> locationIDs = new ArrayList<String>();
if (request.getParameterValues("locationID") != null) {
    locationIDs = Arrays.asList(request.getParameterValues("locationID"));
}

try {
    myShepherd = new Shepherd(context);
    myShepherd.setAction("resendBulkImportID.jsp");
    myShepherd.beginDBTransaction();

    // --- validation + authorization, BEFORE any write ---
    if (!Util.stringExists(importIdTask)) {
        res.put("error", "missing importIdTask");
        httpStatus = HttpServletResponse.SC_BAD_REQUEST;
    } else if (algorithmProvided && !hotspotterOnly) {
        res.put("error", "unknown algorithm: " + algorithm);
        httpStatus = HttpServletResponse.SC_BAD_REQUEST;
    } else if (hotspotterOnly && !request.isUserInRole("admin")) {
        res.put("error", "hotspotter second-pass requires admin");
        httpStatus = HttpServletResponse.SC_FORBIDDEN;
    } else {
        ImportTask itask = myShepherd.getImportTask(importIdTask);
        if (itask == null) {
            res.put("error", "no such import task: " + importIdTask);
            httpStatus = HttpServletResponse.SC_NOT_FOUND;
        } else {
            // --- build task parameters ---
            JSONObject taskParameters = new JSONObject();
            taskParameters.put("importTaskId", itask.getId());
            JSONObject mf = new JSONObject();
            if (locationIDs != null && locationIDs.size() > 0) mf.put("locationIds", locationIDs);
            taskParameters.put("matchingSetFilter", mf);
            if (hotspotterOnly) taskParameters.put("matchingAlgorithmFilter", "hotspotter");
            if (unidentifiedOnly) taskParameters.put("unidentifiedOnly", true);

            // --- build target encounters (re-fetch when filtering by individual) ---
            List<Encounter> targetEncs = new ArrayList<Encounter>();
            for (Encounter e : itask.getEncounters()) {
                if (e == null) continue;
                if (unidentifiedOnly) {
                    Encounter fresh = myShepherd.getEncounter(e.getId());
                    if (fresh == null || fresh.hasMarkedIndividual()) continue;
                    targetEncs.add(fresh);
                } else {
                    targetEncs.add(e);
                }
            }

            // --- static early-out: is there any eligible annotation at all? ---
            boolean anyEligible = false;
            for (Encounter e : targetEncs) {
                if (e.getAnnotations() == null) continue;
                for (Annotation a : e.getAnnotations()) {
                    if (!IBEISIA.validForIdentification(a)) continue;
                    if (hotspotterOnly && !IA.annotationHasHotspotterOpt(myShepherd, a)) continue;
                    anyEligible = true;
                    break;
                }
                if (anyEligible) break;
            }

            if (!anyEligible) {
                res.put("error", unidentifiedOnly
                    ? "no unidentified encounters with eligible annotations"
                    : "no eligible annotations to identify");
                // httpStatus stays 200; success stays false; nothing persisted
            } else {
                // --- attach the root up front, then dispatch (today's ordering) ---
                Task parentTask = new Task();
                parentTask.setParameters(taskParameters);
                myShepherd.storeNewTask(parentTask);
                myShepherd.updateDBTransaction();
                itask.setIATask(parentTask);
                myShepherd.updateDBTransaction();

                JSONArray initiatedJobs = new JSONArray();

                int iaMatchThreads = 1;
                try {
                    String mtCfg = CommonConfiguration.getProperty("iaMatchThreads", context);
                    if (Util.stringExists(mtCfg)) iaMatchThreads = Integer.parseInt(mtCfg.trim());
                } catch (NumberFormatException nfe) { iaMatchThreads = 1; }

                if (iaMatchThreads > 1) {
                    List<String> encIds = new ArrayList<String>();
                    for (Encounter qe : targetEncs) { if (qe != null) encIds.add(qe.getId()); }
                    initiatedJobs = ParallelIdentify.identifyEncounters(context,
                        parentTask.getId(), encIds, taskParameters, iaMatchThreads);
                } else {
                    for (Encounter queryEncRaw : targetEncs) {
                        // re-check at the point of use (matches ParallelIdentify.processOne): the
                        // encounter may have been assigned an individual since targetEncs was built.
                        Encounter queryEnc = queryEncRaw;
                        if (unidentifiedOnly) {
                            queryEnc = myShepherd.getEncounter(queryEncRaw.getId());
                            if (queryEnc == null || queryEnc.hasMarkedIndividual()) continue;
                        }
                        List<Annotation> matchMeAnns = new ArrayList<Annotation>();
                        for (Annotation queryAnn : queryEnc.getAnnotations()) {
                            if (!IBEISIA.validForIdentification(queryAnn)) continue;
                            if (hotspotterOnly && !IA.annotationHasHotspotterOpt(myShepherd, queryAnn)) continue;
                            matchMeAnns.add(queryAnn);
                        }
                        if (matchMeAnns.isEmpty()) continue;   // create subtask only if work exists

                        Task subParentTask = new Task();
                        subParentTask.setParameters(taskParameters);
                        myShepherd.storeNewTask(subParentTask);
                        myShepherd.updateDBTransaction();

                        System.out.println("BulkImport:" + importIdTask + " sending "
                            + matchMeAnns.size() + " annots for Encounter " + queryEnc.getCatalogNumber());

                        Task childTask = IA.intakeAnnotations(myShepherd, matchMeAnns, subParentTask, false);
                        myShepherd.storeNewTask(childTask);
                        myShepherd.updateDBTransaction();
                        subParentTask.addChild(childTask);
                        myShepherd.updateDBTransaction();

                        JSONObject jobJSON = new JSONObject();
                        jobJSON.put("topTaskId", parentTask.getId());
                        jobJSON.put("childTaskId", childTask.getId());
                        initiatedJobs.put(jobJSON);
                        myShepherd.updateDBTransaction();
                    }
                }

                res.put("success", true);
                res.put("initiatedJobs", initiatedJobs);
                httpStatus = HttpServletResponse.SC_OK;
            }
        }
    }
} catch (Exception e) {
    e.printStackTrace();
    res = new JSONObject();
    res.put("success", false);
    res.put("error", "server error");
    httpStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
} finally {
    if (myShepherd != null) {
        try { myShepherd.rollbackAndClose(); } catch (Exception ex) { ex.printStackTrace(); }
    }
    response.setStatus(httpStatus);
    out.println(res);   // single write, after cleanup, regardless of cleanup outcome
}
%>
