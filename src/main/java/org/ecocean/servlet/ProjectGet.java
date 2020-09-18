package org.ecocean.servlet;

import org.ecocean.*;

import javax.jdo.JDOException;
import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class ProjectGet extends HttpServlet {

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

        System.out.println("==> In ProjectGet Servlet ");

        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ProjectGet.java");
        myShepherd.beginDBTransaction();

        JSONObject res = new JSONObject();
        try {
            res.put("success","false");
            JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

            boolean getEncounterMetadata = false;
            String getEncounterMetadataOpt = j.optString("getEncounterMetadata", null);
            if ("true".equals(getEncounterMetadataOpt)) {
                getEncounterMetadata = true;
            }
            boolean getUserIncrementalIds = false;
            String getUserIncrementalIdsOpt = j.optString("getUserIncrementalIds", null);
            if ("true".equals(getUserIncrementalIdsOpt)) {
                getUserIncrementalIds = true;
            }

            String researchProjectId = null;
            String projectUUID = null;
            String ownerId = null;
            String participantId = null;
            String encounterId = null;

            researchProjectId = j.optString("researchProjectId", null);

            boolean complete = false;

            String annotInProject = j.optString("annotInProject", null);
            if ("true".equals(annotInProject)) {
                String acmId = j.optString("acmId", null);
                if (Util.stringExists(acmId)&&Util.stringExists(researchProjectId)) {
                    res.put("inProject", "false");
                    Project project = myShepherd.getProjectByResearchProjectId(researchProjectId.trim());
                    if (project!=null) {
                        ArrayList<Annotation> anns = myShepherd.getAnnotationsWithACMId(acmId);
                        List<Encounter> encs = project.getEncounters();
                        for (Annotation ann : anns) {
                            Encounter enc = ann.findEncounter(myShepherd);
                            if (encs.contains(enc)) {
                                res.put("inProject","true");
                                break;
                            }
                        }
                    }
                    res.put("success","true");
                    complete = true;
                }
            }

            //get simple JSON for autocomplete
            String username = j.optString("username", null);
            if (getUserIncrementalIds&&Util.stringExists(username)) {
                String currentInput = j.optString("currentInput", null);
                User user = myShepherd.getUser(username);
                if (user!=null) {
                    ArrayList<Project> userProjects = myShepherd.getProjectsForUser(user);
                    JSONArray autocompleteArr = new JSONArray();
                    for (Project project : userProjects) {
                        researchProjectId = project.getResearchProjectId();

                        //lets avoid the rest of the query chain if the current input doesn't match the researchProjectId
                        boolean useProject = true;
                        if (Util.stringExists(currentInput)) {
                            if (currentInput.length()>researchProjectId.length()) {
                                currentInput = currentInput.substring(0, researchProjectId.length());
                            }
                            if (!researchProjectId.equals(currentInput)&&!researchProjectId.contains(currentInput)) {
                                useProject = false;
                            }
                        }
                        if (useProject) {
                            List<MarkedIndividual> individuals = project.getAllIndividualsForProject();
                            for (MarkedIndividual individual : individuals) {
                                String projectIncrementalId = individual.getName(researchProjectId);
                                if (projectIncrementalId!=null && projectIncrementalId.contains(currentInput)) {
                                    JSONObject individualData = new JSONObject();
                                    individualData.put("projectIncrementalId", projectIncrementalId);
                                    individualData.put("individualId", individual.getId());
                                    individualData.put("individualDisplayName", individual.getDisplayName());
                                    autocompleteArr.put(individualData);
                                }
                            }
                        }
                    }
                    res.put("autocompleteArr", autocompleteArr);
                    res.put("success","true");
                    complete = true;
                }
            }


            // get all projects for an encounter
            encounterId = j.optString("encounterId", null);
            if (Util.stringExists(encounterId)) {
                Encounter enc = myShepherd.getEncounter(encounterId);
                if (enc!=null) {
                    List<Project> projects = myShepherd.getAllProjectsForEncounter(enc);
                    JSONArray projectArr = new JSONArray();
                    if (projects!=null) {
                        for (Project project : projects) {
                            projectArr.put(project.asJSONObjectWithEncounterMetadata(myShepherd));
                        }
                    }
                    res.put("projects", projectArr);
                    res.put("success","true");
                    complete = true;
                }
            }

            // get all projects for owner or participant
            ownerId = j.optString("ownerId", null);
            participantId = j.optString("participantId", null);
            if (Util.stringExists(ownerId)||Util.stringExists(participantId)) {
                List<Project> allUserProjects = null;
                if (Util.stringExists(ownerId)) {
                    allUserProjects = myShepherd.getOwnedProjectsForUserId(ownerId);
                } else if (Util.stringExists(participantId)) {
                    allUserProjects = myShepherd.getParticipatingProjectsForUserId(participantId);
                }
                JSONArray projectArr = new JSONArray();
                if (allUserProjects!=null) {
                    for (Project project : allUserProjects) {
                        projectArr.put(project.asJSONObject());
                    }
                }
                res.put("projects", projectArr);
                res.put("success","true");
                complete = true;
            }

            //get specific project
            researchProjectId = j.optString("researchProjectId", null);
            projectUUID = j.optString("projectUUID", null);
            if ((Util.stringExists(researchProjectId)||Util.stringExists(projectUUID))&&!complete) {
                Project project = null;
                if (Util.stringExists(researchProjectId)) {
                    project = myShepherd.getProjectByResearchProjectId(researchProjectId);
                }
                if (Util.stringExists(projectUUID)) {
                    project = myShepherd.getProject(projectUUID);
                }

                JSONArray projectArr = new JSONArray();
                if (project!=null) {
                    if (getEncounterMetadata) {
                        projectArr.put(project.asJSONObjectWithEncounterMetadata(myShepherd));
                    } else {
                        projectArr.put(project.asJSONObject());
                    }
                }
                res.put("projects", projectArr);
                res.put("success","true");
                complete = true;
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

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }


}
