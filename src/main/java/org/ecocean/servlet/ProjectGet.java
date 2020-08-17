package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.amazonaws.util.json.JSONArray;

import org.json.JSONObject;
import org.json.JSONException;

import java.io.*;
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
            String researchProjectId = null;
            String ownerId = null;

            boolean complete = false;

            // get all projects for owner
            ownerId = j.optString("ownerId", null);
            if (ownerId!=null&&!"".equals(ownerId)) {
                List<Project> allUserProjects = myShepherd.getProjectsForUserId(ownerId);
                JSONArray projectArr = new JSONArray();
                for (Project project : allUserProjects) {
                    JSONObject userProjectJSON = new JSONObject();
                    userProjectJSON.put(project.getId(), project.toString());
                    projectArr.put(userProjectJSON);
                }
                res.put("success","true");
                complete = true;
            }

            //get specific project
            researchProjectId = j.optString("researchProjectId", null);
            if (researchProjectId!=null&&!"".equals(researchProjectId)&&!complete) {
                Project project = myShepherd.getProjectByResearchProjectId(researchProjectId);
                JSONObject singleProjectJSON = new JSONObject();
                singleProjectJSON.put(project.getId(), project.toString());
                JSONArray projectArr = new JSONArray();
                projectArr.put(singleProjectJSON);
                res.put("projects", projectArr);
                res.put("success","true");
            } 

            out.println(res);
            out.close();

        } catch (NullPointerException npe) {
            npe.printStackTrace();
            res.put("error","NullPointerException npe");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (JSONException je) {
            je.printStackTrace();
            res.put("error","JSONException je");
          response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace();
            res.put("error","Exception e");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            out.println(res);
        }


    }


}