package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;

import edu.stanford.nlp.util.StringUtils;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class ProjectCreate extends HttpServlet {

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

        System.out.println("==> In ProjectCreate Servlet ");


        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ProjectCreate.java");
        myShepherd.beginDBTransaction();

        JSONObject res = new JSONObject();
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

        JSONArray encsJSON = null;
        JSONArray projectUserIds = null;
        JSONArray orgsForUsers = null;
        String projectIdPrefix = null;
        String researchProjectName = null;
        
        //TODO handle add users by organization
        
        try {
            res.put("success","false");
            encsJSON = j.optJSONArray("encounterIds");
            projectUserIds = j.optJSONArray("projectUserIds");
            orgsForUsers = j.optJSONArray("organizationAccess");
            projectIdPrefix = j.optString("projectIdPrefix", null);
            researchProjectName = j.optString("researchProjectName", null);

            if (!Util.stringExists(projectIdPrefix)) {
                projectIdPrefix = researchProjectName;
            }

            if (projectIdPrefix!=null&&!"".equals(projectIdPrefix)&&myShepherd.getProjectByProjectIdPrefix(projectIdPrefix)==null) {
                response.setStatus(HttpServletResponse.SC_OK);
                List<Encounter> encs = new ArrayList<>();
                if (encsJSON!=null&&encsJSON.length()>0) {
                    for (int i=0;i<encsJSON.length();i++) {
                        if (encsJSON.optString(i)!=null&&!"".equals(encsJSON.optString(i, null))) {
                            Encounter enc = myShepherd.getEncounter(encsJSON.optString(i));
                            if (enc!=null) {
                                encs.add(enc);
                            }
                        }
                    }
                }

                Project newProject = new Project(projectIdPrefix);
                myShepherd.storeNewProject(newProject);
                res.put("newProjectUUID", newProject.getId());
                if (researchProjectName!=null) {
                    newProject.setResearchProjectName(researchProjectName);
                }

                List<String> projectUserIdStrings = new ArrayList<>();
                for (int i=0;i<projectUserIds.length();i++) {
                    String thisUserId = projectUserIds.getString(i);
                    if (!projectUserIdStrings.contains(thisUserId)) {
                        projectUserIdStrings.add(thisUserId);
                    }
                }

                for (int i=0;i<orgsForUsers.length();i++) {
                    String orgId = orgsForUsers.getString(i);
                    if (Util.stringExists(orgId)) {
                        Organization org = myShepherd.getOrganization(orgId);
                        if (org!=null) {
                            List<User> orgUsers = org.getMembers();
                            for (User orgUser : orgUsers) {    
                                if (!projectUserIdStrings.contains(orgUser.getId())) {
                                    projectUserIds.put(orgUser.getId());
                                    projectUserIdStrings.add(orgUser.getId());
                                } 
                            }
                        }
                    }
                }

                if (projectUserIds!=null&&projectUserIds.length()>0) {
                    for (int i=0;i<projectUserIds.length();i++) {
                        String userIdentifier = projectUserIds.getString(i);
                        if (!StringUtils.isNullOrEmpty(userIdentifier)) {
                            User user = null;
                            if (Util.isUUID(userIdentifier)) {
                                user = myShepherd.getUserByUUID(userIdentifier);
                            } else {
                                user = myShepherd.getUser(userIdentifier);
                            }
                            if (user!=null) {
                                newProject.addUser(user);
                            }
                        }
                    }
                    myShepherd.updateDBTransaction();
                }

                // should we automatically set owner as current logged in user?
                User currentUser = myShepherd.getUser(request);
                newProject.setOwner(currentUser);

                if (!encs.isEmpty()) {
                    newProject.addEncounters(encs);
                }
                myShepherd.updateDBTransaction();
                response.setStatus(HttpServletResponse.SC_OK);
                res.put("success","true");
            } else {
                addErrorMessage(res,"null ID or Project already exists");
              response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
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
