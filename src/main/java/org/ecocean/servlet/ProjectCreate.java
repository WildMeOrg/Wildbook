package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
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
        response.setHeader("Access-Control-Allow-Origin", "*");
        PrintWriter out = response.getWriter();
        
        System.out.println("==> In ProjectCreate Servlet ");
        
        JSONObject researcherProjectId = null;
        JSONObject researcherProjectName = null;
        JSONArray encsJSON = null;
        
        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ProjectCreate.java");
        myShepherd.beginDBTransaction();
        
        JSONObject res = new JSONObject();
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);

        try {            
            res.put("success","false");
            encsJSON = j.optJSONArray("encounterIds");
            researcherProjectId = j.optJSONObject("researchProjectId");
            researcherProjectName = j.optJSONObject("researchProjectName");

            if (researcherProjectId.toString()!=null&&myShepherd.getProjectByResearchProjectId(researcherProjectId.toString())==null) {
                
                response.setStatus(HttpServletResponse.SC_OK);

                String researcherProjectIdString = null;
                if (researcherProjectId.toString()!=null&&!"".equals(researcherProjectId.toString())) {
                    researcherProjectIdString = researcherProjectId.toString();
                }

                String researcherProjectNameString = null;
                if (researcherProjectName.toString()!=null&&!"".equals(researcherProjectName.toString())) {
                    researcherProjectNameString = researcherProjectName.toString();
                }

                List<Encounter> encs = new ArrayList<>();
                if (researcherProjectName.toString()!=null&&!"".equals(researcherProjectName.toString())) {
                    for (int i=0;i<encsJSON.length();i++) {
                        if (encsJSON.getString(i)!=null&&!"".equals(encsJSON.getString(i))) {
                            Encounter enc = myShepherd.getEncounter(encsJSON.getString(i));
                            if (enc!=null) {
                                encs.add(enc);
                            }
                        }
                    }
                }

                Project newProject = new Project(researcherProjectIdString);
                if (researcherProjectNameString!=null) {
                    newProject.setResearchProjectName(researcherProjectNameString);
                }
                if (encs.size()>0) {
                    newProject.addEncounters(encs);
                }
                myShepherd.storeNewProject(newProject);
                res.put("success","true");
            } else { 
              res.put("error","Project already exists");
              response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
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