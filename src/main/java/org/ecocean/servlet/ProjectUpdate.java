package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import com.amazonaws.util.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;

public class ProjectUpdate extends HttpServlet {
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

        System.out.println("==> In ProjectUpdate Servlet ");

        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ProjectUpdate.java");
        myShepherd.beginDBTransaction();

        JSONObject res = new JSONObject();
        try {
            res.put("success", false);
            res.put("modified", false);
            JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
            System.out.println("j from servlet is " + j.toString());

            JSONArray projectsJSONArr = j.optJSONArray("projects");
            System.out.println("projectsJSONArr is " + projectsJSONArr.toString());
            if (projectsJSONArr != null && projectsJSONArr.length() > 0) {
                for (int i = 0; i < projectsJSONArr.length(); i++) {
                    JSONObject projectJSON = projectsJSONArr.optJSONObject(i);
                    String projectUUID = projectJSON.optString("id", null);
                    System.out.println("projectUUID is " + projectUUID);
                    if (projectUUID != null && !"".equals(projectUUID)) {
                        Project project = myShepherd.getProject(projectUUID);
                        System.out.println("project is " + project.toString());
                        if (project != null) {
                            boolean canAddEncounters = isUserAuthorizedToAddEncounters(project,
                                myShepherd, request);
                            System.out.println("canAddEncounters is " + canAddEncounters);
                            JSONArray encountersToAddJSONArr = projectJSON.optJSONArray(
                                "encountersToAdd");
                            encountersToAddJSONArr = removeUnauthorizedEncounters(
                                encountersToAddJSONArr, myShepherd, request);
                            if (canAddEncounters && encountersToAddJSONArr != null &&
                                encountersToAddJSONArr.length() > 0) {
                                System.out.println(
                                    "Authorized to add and encounters exist. Adding or removing encounters from project.... ");
                                addOrRemoveEncountersFromProject(project, myShepherd,
                                    encountersToAddJSONArr, "add", res, request);
                            }
                            boolean canUpdate = isUserAuthorizedToUpdateProject(project, myShepherd,
                                request);
                            System.out.println("is authorized to update project is " + canUpdate);
                            if (canUpdate) {
                                String projectIdPrefix = projectJSON.optString("projectIdPrefix",
                                    null);
                                System.out.println("projectIdPrefix is " + projectIdPrefix);
                                System.out.println("project.getProjectIdPrefix is " +
                                    project.getProjectIdPrefix());
                                if (!StringUtils.isNullOrEmpty(projectIdPrefix) &&
                                    !project.getProjectIdPrefix().equals(projectIdPrefix)) {
                                    System.out.println(
                                        "this should not happen for mark projectIdPrefix");
                                    project.setProjectIdPrefix(projectIdPrefix);
                                    myShepherd.updateDBTransaction();
                                    res.put("modified", true);
                                    res.put("success", true);
                                }
                                String researchProjectName = projectJSON.optString(
                                    "researchProjectName", null);
                                if (!StringUtils.isNullOrEmpty(researchProjectName) &&
                                    !project.getResearchProjectName().equals(researchProjectName)) {
                                    System.out.println(
                                        "this should not happen for mark researchProjectName");
                                    project.setResearchProjectName(researchProjectName);
                                    myShepherd.updateDBTransaction();
                                    res.put("modified", true);
                                    res.put("success", true);
                                }
                                String ownerid = projectJSON.optString("ownerId", null);
                                if (!StringUtils.isNullOrEmpty(ownerid) &&
                                    !project.getOwnerId().equals(ownerid)) {
                                    // will this ever happen? looks for UUID, we can make username based or both if necessary
                                    System.out.println("this should not happen for mark ownerId");
                                    User newOwner = myShepherd.getUserByUUID(ownerid);
                                    project.setOwner(newOwner);
                                    myShepherd.updateDBTransaction();
                                    res.put("modified", true);
                                    res.put("success", true);
                                }
                                JSONArray encountersToRemoveJSONArr = projectJSON.optJSONArray(
                                    "encountersToRemove");
                                encountersToRemoveJSONArr = removeUnauthorizedEncounters(
                                    encountersToRemoveJSONArr, myShepherd, request);
                                if (encountersToRemoveJSONArr != null &&
                                    encountersToRemoveJSONArr.length() > 0) {
                                    System.out.println(
                                        "this should not happen for mark encountersToRemoveJSONArr");
                                    addOrRemoveEncountersFromProject(project, myShepherd,
                                        encountersToRemoveJSONArr, "remove", res, request);
                                }
                                JSONArray usersToAddJSONArr = projectJSON.optJSONArray(
                                    "usersToAdd");
                                if (usersToAddJSONArr != null && usersToAddJSONArr.length() > 0) {
                                    System.out.println(
                                        "this should not happen for mark usersToAddJSONArr");
                                    addOrRemoveUsersFromProject(project, myShepherd,
                                        usersToAddJSONArr, "add", res);
                                }
                                JSONArray usersToRemoveJSONArr = projectJSON.optJSONArray(
                                    "usersToRemove");
                                if (usersToRemoveJSONArr != null &&
                                    usersToRemoveJSONArr.length() > 0) {
                                    System.out.println(
                                        "this should not happen for mark usersToRemoveJSONArr");
                                    addOrRemoveUsersFromProject(project, myShepherd,
                                        usersToRemoveJSONArr, "remove", res);
                                }
                            }
                            response.setStatus(HttpServletResponse.SC_OK);
                        } else {
                            addErrorMessage(res,
                                "Exception: you have tried to update a project that does not exist.");
                        }
                    } else {
                        addErrorMessage(res,
                            "Exception: You have provided a null or empty project UUID.");
                    }
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

    private void addOrRemoveUsersFromProject(Project project, Shepherd myShepherd,
        JSONArray usersToAddJSONArr, String action, JSONObject res) {
        for (int i = 0; i < usersToAddJSONArr.length(); i++) {
            String userId = usersToAddJSONArr.getString(i);
            User user = null;
            if (Util.isUUID(userId)) {
                user = myShepherd.getUserByUUID(userId);
            } else {
                user = myShepherd.getUser(userId);
            }
            if (user != null && !StringUtils.isNullOrEmpty(action)) {
                if ("add".equals(action)) {
                    project.addUser(user);
                } else if ("remove".equals(action)) {
                    project.removeUser(user);
                }
            }
        }
        myShepherd.updateDBTransaction();
        res.put("modified", true);
        res.put("success", true);
    }

    private JSONArray removeUnauthorizedEncounters(JSONArray encountersToAddJSONArr,
        Shepherd myShepherd, HttpServletRequest request) {
        JSONArray filteredResults = new JSONArray();

        if (encountersToAddJSONArr != null) {
            for (int i = 0; i < encountersToAddJSONArr.length(); i++) {
                String encId = encountersToAddJSONArr.getString(i);
                try {
                    Encounter enc = myShepherd.getEncounter(encId);
                    if (ServletUtilities.isUserAuthorizedForEncounter(enc, request)) {
                        filteredResults.put(encountersToAddJSONArr.get(i));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return filteredResults;
    }

    private void addOrRemoveEncountersFromProject(Project project, Shepherd myShepherd,
        JSONArray encountersToAddJSONArr, String action, JSONObject res,
        HttpServletRequest request) {
        System.out.println("addOrRemoveEncountersFromProject entered");
        int additionCounter = 0;
        int removalCounnter = 0;
        for (int i = 0; i < encountersToAddJSONArr.length(); i++) {
            String encId = encountersToAddJSONArr.getString(i);
            try {
                Encounter enc = myShepherd.getEncounter(encId);
                if (enc != null) {
                    if ("add".equals(action) && !project.getEncounters().contains(enc)) { // need project.getEncounters().contains(enc) check to
                                                                                          // ensure additionCounter returns the correct number of
                                                                                          // added encounters
                        String comment = "<p><em>" + myShepherd.getUsername(request) + " on " +
                            (new java.util.Date()).toString() + "</em><br>" +
                            "added this encounter to Project " + project.getResearchProjectName() +
                            "</p>";
                        enc.addComments(comment);
                        project.addEncounter(enc);
                        myShepherd.updateDBTransaction();
                        additionCounter++;
                    } else if ("remove".equals(action)) {
                        String comment = "<p><em>" + myShepherd.getUsername(request) + " on " +
                            (new java.util.Date()).toString() + "</em><br>" +
                            "removed this encounter from Project " +
                            project.getResearchProjectName() + "</p>";
                        enc.addComments(comment);
                        MarkedIndividual currentIndividual = enc.getIndividual();
                        if (currentIndividual != null) {
                            comment = "<p><em>" + myShepherd.getUsername(request) + " on " +
                                (new java.util.Date()).toString() + "</em><br>" +
                                "removed this individual from Project " +
                                project.getResearchProjectName() + "</p>";
                            currentIndividual.addComments(comment);
                        }
                        myShepherd.updateDBTransaction();
                        project.removeEncounter(enc);
                        removalCounnter++;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("got to updateDBTransaction in addOrRemoveEncountersFromProject");
        myShepherd.updateDBTransaction();
        res.put("modified", true);
        res.put("success", true);
        if ("add".equals(action)) {
            res.put("encountersAddedForProj_" + project.getId(), additionCounter);
        }
        if ("remove".equals(action)) {
            res.put("encountersRemovedForProj_" + project.getId(), removalCounnter);
        }
    }

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }

    private boolean isUserAuthorizedToUpdateProject(Project project, Shepherd myShepherd,
        HttpServletRequest request) {
        User currentUser = myShepherd.getUser(request);

        if (project == null) { System.out.println("project is null"); }
        // System.out.println("project owner is " + project.getOwner().toString());
        System.out.println("currentUser is " + currentUser.toString());
        Boolean isAdmin = myShepherd.doesUserHaveRole(currentUser.getUsername(), "admin",
            ServletUtilities.getContext(request));
        if (currentUser != null && currentUser.equals(project.getOwner()) || isAdmin == true)
            return true;
        return false;
    }

    private boolean isUserAuthorizedToAddEncounters(Project project, Shepherd myShepherd,
        HttpServletRequest request) {
        User currentUser = myShepherd.getUser(request);

        if (currentUser == null) return false;
        if (isUserAuthorizedToUpdateProject(project, myShepherd, request)) return true;
        for (User user : project.getUsers()) {
            if (user != null && user.getUsername().equals(currentUser.getUsername())) {
                return true;
            }
        }
        return false;
    }
}
