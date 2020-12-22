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
            res.put("success",false);
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

            String projectUUID = null;
            String ownerId = null;
            String participantId = null;
            String encounterId = null;

            //should add this parameter to all calls at some point
            String action = null;
            action = j.optString("action", null);

            String projectIdPrefix = null;
            projectIdPrefix = j.optString("projectIdPrefix", null);

            String getEditPermission = null;
            getEditPermission = j.optString("getEditPermission", null);

            boolean complete = false;

            if (Util.stringExists(action)&&"getNextIdForProject".equals(action)) {
                Project project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix);
                // we don't want to advance the counter until the next ID is added to an individual successfully
                if (project!=null) {
                    String nextId = project.getNextIncrementalIndividualId();
                    res.put("nextId", nextId);
                    res.put("success",true);
                }
                complete = true;
            }

            String annotInProject = j.optString("annotInProject", null);
            if ("true".equals(annotInProject)) {
                String acmId = j.optString("acmId", null);
                if (Util.stringExists(acmId)&&Util.stringExists(projectIdPrefix)) {
                    res.put("inProject", "false");
                    Project project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix.trim());
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
                    res.put("success",true);
                    complete = true;
                }
            }

            //get incrementalIds for individuals
            JSONArray individualIdJSONArr = j.optJSONArray("individualIds");
            JSONArray returnArr = new JSONArray();
            Boolean successStatus = false;
            if(individualIdJSONArr != null && individualIdJSONArr.length()>0){
              for (int i=0;i<individualIdJSONArr.length();i++) {
                JSONObject individualIdObj = individualIdJSONArr.getJSONObject(i);
                String individualId = individualIdObj.optString("indId", null);
                if (Util.isUUID(individualId) && Util.stringExists(projectIdPrefix)) {
                  JSONObject individualData = new JSONObject();
                  individualData.put("projectIdPrefix", projectIdPrefix);
                  successStatus = true;
                  Project project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix);
                  String projName = project.getResearchProjectName();
                  String projUuid = project.getId();
                  User projOwner = project.getOwner();
                  if(projOwner!=null){
                    String projOwnerName = projOwner.getUsername();
                    if(Util.stringExists(projOwnerName)){individualData.put("projectOwner", projOwnerName);}
                  }
                  if(Util.stringExists(projName)){individualData.put("projectName", projName);}
                  if(Util.stringExists(projUuid)){individualData.put("projectUuid", projUuid);}
                  if(project != null){
                    String researchProjId = project.getProjectIdPrefix();
                    if(Util.stringExists(researchProjId)){
                      MarkedIndividual individual = myShepherd.getMarkedIndividual(individualId);
                      if(individual != null){
                        List<String> namesList = individual.getNamesList(researchProjId);
                        String projectIncrementalId = individual.getName(researchProjId);
                        if(Util.stringExists(projectIncrementalId)){
                          individualData.put("projectIncrementalId", projectIncrementalId);
                          returnArr.put(individualData);
                        }else{
                          individualData.put("projectIncrementalId", "");
                          returnArr.put(individualData);
                        }
                      }
                    }
                  }
                }
              }
              res.put("incrementalIdArr", returnArr);
              res.put("success",successStatus);
              complete = true;
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
                        projectIdPrefix = project.getProjectIdPrefix();

                        //lets avoid the rest of the query chain if the current input doesn't match the projectIdPrefix
                        boolean useProject = true;
                        if (Util.stringExists(currentInput)) {
                            if (currentInput.length()>projectIdPrefix.length()) {
                                currentInput = currentInput.substring(0, projectIdPrefix.length());
                            }
                            if (!projectIdPrefix.equals(currentInput)&&!projectIdPrefix.contains(currentInput)) {
                                useProject = false;
                            }
                        }
                        if (useProject) {
                            List<MarkedIndividual> individuals = myShepherd.getMarkedIndividualsFromProject(project);
                            for (MarkedIndividual individual : individuals) {
                                String projectIncrementalId = individual.getName(projectIdPrefix);
                                if (projectIncrementalId!=null && projectIncrementalId.contains(currentInput)) {
                                    JSONObject individualData = new JSONObject();
                                    individualData.put("projectIncrementalId", projectIncrementalId);
                                    individualData.put("individualId", individual.getId());
                                    individualData.put("individualDisplayName", individual.getDisplayName(request, myShepherd));
                                    autocompleteArr.put(individualData);
                                }
                            }
                        }
                    }
                    res.put("autocompleteArr", autocompleteArr);
                    res.put("success",true);
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
                            projectArr.put(project.asJSONObjectWithEncounterMetadata(myShepherd, request));
                        }
                    }
                    res.put("projects", projectArr);
                    res.put("success",true);
                    complete = true;
                }
            }

            // get all projects for a markedIndividual
            String individualId = j.optString("individualId", null);
            if (Util.stringExists(individualId)) {
                MarkedIndividual ind = myShepherd.getMarkedIndividual(individualId);
                if (ind!=null) {
                    List<Project> projects = myShepherd.getAllProjectsForMarkedIndividual(ind);
                    JSONArray projectArr = new JSONArray();
                    if (projects!=null) {
                        for (Project project : projects) {
                            projectArr.put(project.asJSONObjectWithEncounterMetadata(myShepherd, request));
                        }
                    }
                    res.put("projects", projectArr);
                    res.put("success",true);
                    complete = true;
                }
            }

            // get all projects for an array of markedIndividuals
            JSONArray individualIdsArr = j.optJSONArray("individualIdsForProj");
            JSONArray returnProjArr = new JSONArray();
            successStatus = false;
            if(individualIdsArr != null && individualIdsArr.length()>0){
              for (int i=0;i<individualIdsArr.length();i++) {
                JSONObject individualIdObj = individualIdsArr.getJSONObject(i);
                individualId = individualIdObj.optString("indId", null);
                if (Util.stringExists(individualId)) {
                  MarkedIndividual ind = myShepherd.getMarkedIndividual(individualId);
                  if (ind!=null) {
                    List<Project> projects = myShepherd.getAllProjectsForMarkedIndividual(ind);
                    JSONArray projectArr = new JSONArray();
                    if (projects!=null) {
                      for (Project project : projects) {
                        projectArr.put(project.asJSONObjectWithEncounterMetadata(myShepherd, request));
                      }
                    }
                    returnProjArr.put(projectArr);
                  }
              }
            }
            res.put("projectByIndividArr",returnProjArr);
            res.put("success",true);
            complete = true;
          }


            // get all projects for owner or participant
            ownerId = j.optString("ownerId", null);
            participantId = j.optString("participantId", null);
            if (Util.stringExists(ownerId)||Util.stringExists(participantId)) {
                List<Project> allUserProjects = null;
                if (Util.stringExists(ownerId)) {
                    allUserProjects = myShepherd.getOwnedProjectsForUserId(ownerId);
                } else if (Util.stringExists(participantId)) {
                    User user = myShepherd.getUser(participantId);
                    if(user!=null){
                      allUserProjects = myShepherd.getProjectsForUser(user);
                    }
                }
                JSONArray projectArr = new JSONArray();
                if (allUserProjects!=null) {
                    for (Project project : allUserProjects) {
                        projectArr.put(project.asJSONObject());
                    }
                }
                res.put("projects", projectArr);
                res.put("success",true);
                complete = true;
            }

            //get specific project
            projectIdPrefix = j.optString("projectIdPrefix", null);
            projectUUID = j.optString("projectUUID", null);
            if ((Util.stringExists(projectIdPrefix)|| Util.stringExists(projectUUID))&&!complete) {
                Project project = null;
                if (Util.stringExists(projectIdPrefix)) {
                    project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix);
                }
                if (Util.stringExists(projectUUID)) {
                    project = myShepherd.getProject(projectUUID);
                }

                JSONArray projectArr = new JSONArray();
                if (project!=null) {
                    if (getEncounterMetadata) {
                        projectArr.put(project.asJSONObjectWithEncounterMetadata(myShepherd, request));
                    } else {
                        projectArr.put(project.asJSONObject());
                    }
                }
                if (getEditPermission!=null&&"true".equals(getEditPermission)) {
                    User user = myShepherd.getUser(request);
                    res.put("userCanEdit","false");
                    if (user!=null&&(user.hasRoleByName("admin", myShepherd)||user.getId().equals(project.getOwnerId()))) {
                        res.put("userCanEdit","true");
                    }
                }
                res.put("projects", projectArr);
                response.setStatus(HttpServletResponse.SC_OK);
                res.put("success",true);
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
