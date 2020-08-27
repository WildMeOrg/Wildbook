package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.amazonaws.util.StringUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.*;

public class ProjectUpdate extends HttpServlet {

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
        
        System.out.println("==> In ProjectUpdate Servlet ");
        
        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ProjectUpdate.java");
        myShepherd.beginDBTransaction();
        
        JSONObject res = new JSONObject();
        try {      
            res.put("success","false");
            JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

            JSONArray projectsJSONArr = j.optJSONArray("projects");

            if (projectsJSONArr!=null&&projectsJSONArr.length()>0) {
                for (int i=0;i<projectsJSONArr.length();i++) {
                    JSONObject projectJSON = (JSONObject) projectsJSONArr.get(i);
    
                    String projectUUID = projectJSON.optString("id", null);
                    
                    
                    if (projectUUID!=null||"".equals(projectUUID)) {
                        Project project = myShepherd.getProject(projectUUID);
                        if (project!=null){
                            
                            boolean canAddEncounters = isUserAuthorizedToAddEncounters(project, myShepherd, request);
                            JSONArray encountersToAddJSONArr = projectJSON.optJSONArray("encountersToAdd");
                            if (canAddEncounters&&encountersToAddJSONArr!=null&&encountersToAddJSONArr.length()>0) {
                                addOrRemoveEncountersFromProject(project, myShepherd, encountersToAddJSONArr, "add");
                            }
                            
                            boolean canUpdate = isUserAuthorizedToUpdateProject(project, myShepherd, request);
                            if (canUpdate) {
                                
                                String researchProjectId = projectJSON.optString("researchProjectId", null);
                                if (StringUtils.isNullOrEmpty(researchProjectId)&&!project.getResearchProjectId().equals(researchProjectId)) {
                                    project.setResearchProjectId(researchProjectId);
                                    myShepherd.updateDBTransaction();
                                }
                                
                                String researchProjectName = projectJSON.optString("researchProjectName", null);
                                if (StringUtils.isNullOrEmpty(researchProjectName)&&!project.getResearchProjectName().equals(researchProjectName)) {
                                    project.setResearchProjectName(researchProjectName);
                                    myShepherd.updateDBTransaction();
                                }
                                
                                String ownerid = projectJSON.optString("ownerId", null);
                                if (StringUtils.isNullOrEmpty(ownerid)&&!project.getOwnerId().equals(ownerid)) {
                                    // will this ever happen? looks for UUID, we can make username based or both if necessary
                                    User newOwner = myShepherd.getUserByUUID(ownerid);
                                    project.setOwner(newOwner);
                                    myShepherd.updateDBTransaction();
                                }
                                
                                JSONArray encountersToRemoveJSONArr = projectJSON.optJSONArray("encountersToRemove");
                                if (encountersToRemoveJSONArr!=null&&encountersToRemoveJSONArr.length()>0) {
                                    addOrRemoveEncountersFromProject(project, myShepherd, encountersToRemoveJSONArr, "remove");
                                }

                                JSONArray usersToAddJSONArr = projectJSON.optJSONArray("usersToAdd");
                                if (usersToAddJSONArr!=null&&usersToAddJSONArr.length()>0) {
                                    addOrRemoveUsersFromProject(project, myShepherd, usersToAddJSONArr, "add");
                                }
                                
                                JSONArray usersToRemoveJSONArr = projectJSON.optJSONArray("usersToRemove");
                                if (usersToRemoveJSONArr!=null&&usersToRemoveJSONArr.length()>0) {
                                    addOrRemoveUsersFromProject(project, myShepherd, usersToRemoveJSONArr, "remove");
                                }

                            }
        
                        } else {
                            addErrorMessage(res, "Exception: you have tried to update a project that does not exist.");
                        }
                    } else {
                        addErrorMessage(res, "Exception: You have provided a null or empty project UUID.");
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

    private void addOrRemoveUsersFromProject(Project project, Shepherd myShepherd, JSONArray usersToAddJSONArr, String action) {
        for (int i=0;i<usersToAddJSONArr.length();i++) {
            String userId = usersToAddJSONArr.getString(i);
            User user = null;
            if (Util.isUUID(userId)) {
                user = myShepherd.getUserByUUID(userId);
            } else {
                user = myShepherd.getUser(userId);
            }
            if (user!=null&&!StringUtils.isNullOrEmpty(action)) {
                if ("add".equals(action)) {
                    project.addUser(user);
                } else if ("remove".equals(action)) {
                    project.removeUser(user);
                }
            }
        }
        myShepherd.updateDBTransaction();
    }

    private void addOrRemoveEncountersFromProject(Project project, Shepherd myShepherd, JSONArray encountersToAddJSONArr, String action) {
        for (int i=0;i<encountersToAddJSONArr.length();i++) {
            String encId = encountersToAddJSONArr.getString(i);
            try {
                Encounter enc = myShepherd.getEncounter(encId);
                if (enc!=null) {
                    if ("add".equals(action)) {
                        project.addEncounter(enc);
                    } else if ("remove".equals(action)) {
                        project.removeEncounter(enc);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        myShepherd.updateDBTransaction();
    }

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }

    private boolean isUserAuthorizedToUpdateProject(Project project, Shepherd myShepherd, HttpServletRequest request) {
        User currentUser = myShepherd.getUser(request);
        if (currentUser!=null&&currentUser.equals(project.getOwner())) return true;
        return false;
    }

    private boolean isUserAuthorizedToAddEncounters(Project project, Shepherd myShepherd, HttpServletRequest request) {
        User currentUser = myShepherd.getUser(request);
        if (isUserAuthorizedToUpdateProject(project, myShepherd, request)) return true;
        for (User user : project.getUsers()) {
            if (user!=null&&user.equals(currentUser)) {
                return true;
            }
        }
        return false;
    }


}