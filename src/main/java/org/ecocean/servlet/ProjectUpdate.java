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
    
                    JSONArray encountersToRemoveJSONArr = projectJSON.getJSONArray("encountersToAdd");
                    JSONArray encountersToAddJSONArr = projectJSON.getJSONArray("encountersToRemove");
    
                    Project project = myShepherd.getProject(projectUUID);
                    if (project!=null){
    
                        // need lower security for addEncounters only- standard user allowed. anything else must be owner
                        boolean userAuthorized = isUserAuthorizedToUpdateProject(project, myShepherd, request);
                        if (userAuthorized) {
    
                            if (StringUtils.isNullOrEmpty(researchProjectId)&&!project.getResearchProjectId().equals(researchProjectId)) {
                                // update all project based id's on Marked individuals on encounters! blech!
                            }

                            if (StringUtils.isNullOrEmpty(ownerid)&&!project.getOwnerId().equals(ownerid)) {
                                // update ownerId on project.. unlikely but i guess we should support
                            }

                            if (encountersToAddJSONArr!=null&&encountersToAddJSONArr.length()>0) {
                                //add all encounters with ID not already present in project
                            }

                            if (encountersToRemoveJSONArr!=null&&encountersToRemoveJSONArr.length()>0) {
                                //remove all encounters with ID not already present in project
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

    private void addErrorMessage(JSONObject res, String error) {
        res.put("error", error);
    }

    private boolean isUserAuthorizedToUpdateProject(Project project, Shepherd myShepherd, HttpServletRequest request) {
        User currentUser = myShepherd.getUser(request);
        if (currentUser!=null&&currentUser.equals(project.getOwner())) return true;
        for (User user : project.getUsers()) {
            if (user!=null&&user.equals(currentUser)) {
                return true;
            }
        }
        return false;
    }


}