package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.amazonaws.util.json.JSONArray;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectIA extends HttpServlet {

    private static final long serialVersionUID = 1L;

    public void init(ServletConfig config) throws ServletException {
        super.init(config);
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ServletUtilities.doOptions(request, response);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Access-Control-Allow-Origin", "*");
        PrintWriter out = response.getWriter();

        System.out.println("==> In ProjectIA Servlet ");

        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ProjectIA.java");
        myShepherd.beginDBTransaction();

        JSONObject res = new JSONObject();
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
        String projectIdPrefix = null;
        String queryEncounterId = null;
        
        try {
            res.put("success","false");

            projectIdPrefix = j.optString("projectIdPrefix", null);
            queryEncounterId = j.optString("queryEncounterId", null);

            if (Util.stringExists(queryEncounterId)&&Util.stringExists(projectIdPrefix)) {

                Project project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix);
                Encounter queryEnc = myShepherd.getEncounter(queryEncounterId);
                if (project!=null&&queryEnc!=null) {


                    List<Encounter> targetEncs = project.getEncounters();
                    List<Annotation> targetAnns = new ArrayList<>();
                    JSONArray initiatedJobs = new JSONArray();

                    for (Annotation queryAnn : queryEnc.getAnnotations()) {
                        if (IBEISIA.validForIdentification(queryAnn)) {
                            if (targetAnns.isEmpty()) {
                                targetAnns = getAnnotationList(targetEncs);
                            }
                            List<Annotation> anns = new ArrayList<>();  
                            anns.add(0, queryAnn);
                            Task parentTask = new Task();
                            JSONObject tp = new JSONObject();
                            JSONObject mf = new JSONObject();
                            mf.put("projectId", project.getId());
                            tp.put("matchingSetFilter", mf);
                            parentTask.setParameters(tp);
                            myShepherd.storeNewTask(parentTask);

                            Task childTask = IA.intakeAnnotations(myShepherd, anns, parentTask);
                            JSONObject jobJSON = new JSONObject();
                            jobJSON.put("topTaskId", parentTask.getId());
                            jobJSON.put("childTaskId", childTask.getId());
                            jobJSON.put("queryAnnId", queryAnn.getId());
                            initiatedJobs.put(jobJSON);
                        }
                    }
                    res.put("success","true");
                    res.put("initiatedJobs", initiatedJobs);
                    response.setStatus(HttpServletResponse.SC_OK);
                    //JSONObject rtnIA = IBEISIA.sendIdentify(qanns, tanns, queryConfigDict, userConfidence, baseUrl, context); 
                }
            }

            out.println(res);
            out.close();

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
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(res);
        }
    }

    private ArrayList<Annotation> getAnnotationList(List<Encounter> encs) {
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
