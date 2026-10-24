package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectIA extends HttpServlet {
    private static final long serialVersionUID = 1L;

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
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        PrintWriter out = response.getWriter();

        System.out.println("==> In ProjectIA Servlet ");

        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ProjectIA.java");
        myShepherd.beginDBTransaction();

        JSONObject res = new JSONObject();
        res.put("success", false);
        String projectIdPrefix = null;
        String queryEncounterId = null;

        try {
            // parsed inside the try: the Shepherd is already open, so a malformed body must not
            // throw past the finally that releases the PersistenceManager
            JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
            projectIdPrefix = j.optString("projectIdPrefix", null);
            queryEncounterId = j.optString("queryEncounterId", null);
            res = initiateProjectMatch(myShepherd, projectIdPrefix, queryEncounterId);
            // a well-formed outcome stays 200 even when nothing matched: project.jsp drives its
            // UI off `success`, and its ajax error handler leaves the "starting" spinner up
            if (res.optBoolean("success", false)) {
                response.setStatus(HttpServletResponse.SC_OK);
            }
        } catch (NullPointerException npe) {
            npe.printStackTrace();
            addErrorMessage(res, "NullPointerException npe");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (JSONException je) {
            je.printStackTrace();
            addErrorMessage(res, "JSONException je");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            addErrorMessage(res, "Exception e");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            // initiateProjectMatch() owns the commit, so this only unwinds a run that failed
            // before it (rollback of an inactive transaction is a no-op).
            myShepherd.rollbackAndClose();
            out.println(res);
            out.close();
        }
    }

    static JSONObject initiateProjectMatch(Shepherd myShepherd, String projectIdPrefix,
        String queryEncounterId) {
        JSONObject res = new JSONObject();

        res.put("success", false);
        if (!Util.stringExists(queryEncounterId) || !Util.stringExists(projectIdPrefix)) return res;
        Project project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix);
        Encounter queryEnc = myShepherd.getEncounter(queryEncounterId);
        if ((project == null) || (queryEnc == null)) return res;
        List<Encounter> targetEncs = project.getEncounters();
        List<Annotation> targetAnns = new ArrayList<>();
        JSONArray initiatedJobs = new JSONArray();
        JSONArray failedAnnotations = new JSONArray();
        for (Annotation queryAnn : queryEnc.getAnnotations()) {
            if (!IBEISIA.validForIdentification(queryAnn)) continue;
            if (targetAnns.isEmpty()) {
                targetAnns = getAnnotationList(targetEncs);
            }
            try {
                List<Annotation> anns = new ArrayList<>();
                anns.add(0, queryAnn);
                Task parentTask = new Task();
                JSONObject tp = new JSONObject();
                JSONObject mf = new JSONObject();
                mf.put("projectId", project.getId());
                tp.put("matchingSetFilter", mf);
                parentTask.setParameters(tp);
                myShepherd.storeNewTask(parentTask);

                Task childTask = IA.intakeAnnotations(myShepherd, anns, parentTask, true);
                // IA.intakeAnnotations() runs a vector (MiewID) match INLINE on this Shepherd:
                // the per-annotation subtasks, the MatchResult and the terminal task status are
                // only makePersistent()ed, and it is the caller that has to commit them. Without
                // this commit the servlet's rollback discarded all of it and left a childless
                // task with a null status, which Task.getStatus() reports forever as the
                // non-terminal "waiting to queue" -- the match-results page then polls and spins
                // indefinitely (issue #1761). commitDBTransaction() would not do: it swallows
                // failures, so an unpersisted match could still be reported as started.
                // Committing per annotation keeps one annotation's failure from discarding the
                // matches that already ran for its siblings.
                if (!myShepherd.commitDBTransactionWithStatus()) {
                    failedAnnotations.put(queryAnn.getId());
                    continue;
                }
                JSONObject jobJSON = new JSONObject();
                jobJSON.put("topTaskId", parentTask.getId());
                jobJSON.put("childTaskId", childTask.getId());
                jobJSON.put("queryAnnId", queryAnn.getId());
                initiatedJobs.put(jobJSON);
            } catch (Exception ex) {
                // a failure here can leave the transaction aborted, which would poison every
                // later annotation; unwind it so the remaining ones start clean
                ex.printStackTrace();
                myShepherd.rollbackDBTransaction();
                failedAnnotations.put(queryAnn.getId());
            }
        }
        res.put("success", failedAnnotations.length() == 0);
        res.put("initiatedJobs", initiatedJobs);
        if (failedAnnotations.length() > 0) res.put("failedAnnotations", failedAnnotations);
        return res;
    }

    private static ArrayList<Annotation> getAnnotationList(List<Encounter> encs) {
        ArrayList<Annotation> anns = new ArrayList<>();
        Set<Annotation> annHash = new HashSet<>();

        for (Encounter enc : encs) {
            annHash.addAll(enc.getAnnotations());
        }
        anns.addAll(annHash);
        return anns;
    }

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }
}
