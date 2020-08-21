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

            // we will allow multiple project update, though this is unlikely (?)
            JSONArray projectsJSONArr = j.optJSONArray("projects");

            if (projectsJSONArr!=null&&projectsJSONArr.length()>0) {
                for (int i=0;i<projectsJSONArr.length();i++) {
                    JSONObject projectJSON = (JSONObject) projectsJSONArr.get(i);
    
                    String researchProjectId = projectJSON.optString("researchProjectId", null);
                    String projectUUID = projectJSON.optString("id", null);
                    String ownerid = projectJSON.optString("ownerId", null);

                    //TODO handle add/remove users
    
                    JSONArray encountersToRemoveJSONArr = projectJSON.getJSONArray("encountersToAdd");
                    JSONArray encountersToAddJSONArr = projectJSON.getJSONArray("encountersToRemove");
    
                    Project project = myShepherd.getProject(projectUUID);
                    if (project!=null){
    
                        boolean canUpdate = isUserAuthorizedToUpdateProject(project, myShepherd, request);
                        boolean canAddEncounters = isUserAuthorizedToAddEncounters(project, myShepherd, request);

                        if (canAddEncounters&&encountersToAddJSONArr!=null&&encountersToAddJSONArr.length()>0) {
                            addEncountersToProject(project, myShepherd, encountersToAddJSONArr);
                        }

                        if (canUpdate) {

                            if (StringUtils.isNullOrEmpty(researchProjectId)&&!project.getResearchProjectId().equals(researchProjectId)) {
                                project.setResearchProjectId(researchProjectId);
                            }

                            if (StringUtils.isNullOrEmpty(ownerid)&&!project.getOwnerId().equals(ownerid)) {
                                // will this ever happen? looks for UUID, we can make username based or both if necessary
                                User newOwner = myShepherd.getUserByUUID(ownerid);
                                project.setOwner(newOwner);
                                myShepherd.updateDBTransaction();
                            }

                            if (encountersToRemoveJSONArr!=null&&encountersToRemoveJSONArr.length()>0) {
                                removeEncountersFromProject(project, myShepherd, encountersToAddJSONArr);
                            }

                        }
    
                    } else {
                        addErrorMessage(res, "Exception: you have tried to update a project that does not exist.");
                        throw new NullPointerException();
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

    private void addEncountersToProject(Project project, Shepherd myShepherd, JSONArray encountersToAddJSONArr) {
        int added = 0;
        for (int i=0;i<encountersToAddJSONArr.length();i++) {
            String encId = encountersToAddJSONArr.getString(i);
            try {
                Encounter enc = myShepherd.getEncounter(encId);
                if (enc!=null) {
                    project.addEncounter(enc);
                    added++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        myShepherd.updateDBTransaction();
        System.out.println("[INFO]: ProjectUpdate: "+added+" of "+encountersToAddJSONArr.length()+" sent encounter IDs were added to projectId "+project.getResearchProjectId()+".");
    }

    private void removeEncountersFromProject(Project project, Shepherd myShepherd, JSONArray encountersToRemoveJSONArr) {
        int removed = 0;
        for (int i=0;i<encountersToRemoveJSONArr.length();i++) {
            String encId = encountersToRemoveJSONArr.getString(i);
            try {
                Encounter enc = myShepherd.getEncounter(encId);
                if (enc!=null) {
                    project.removeEncounter(enc);
                    removed++;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        myShepherd.updateDBTransaction();
        System.out.println("[INFO]: ProjectUpdate: "+removed+" of "+encountersToRemoveJSONArr.length()+" sent encounter IDs were removed from projectId "+project.getResearchProjectId()+".");
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