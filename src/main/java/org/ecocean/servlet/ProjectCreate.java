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
        String researchProjectId = null;
        String researchProjectName = null;
        try {            
            res.put("success","false");
            encsJSON = j.optJSONArray("encounterIds");
            researchProjectId = j.optString("researchProjectId", null);
            researchProjectName = j.optString("researchProjectName", null);

            if (researchProjectId!=null&&!"".equals(researchProjectId)&&myShepherd.getProjectByResearchProjectId(researchProjectId)==null) {
                
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

                Project newProject = new Project(researchProjectId);
                myShepherd.storeNewProject(newProject);
                if (researchProjectName!=null) {
                    newProject.setResearchProjectName(researchProjectName);
                }

                // should we automatically set owner as current logged in user? 
                User currentUser = myShepherd.getUser(request);
                newProject.setOwner(currentUser.getId());

                if (encs.size()>0) {
                    newProject.addEncounters(encs);
                }
                myShepherd.updateDBTransaction();
                res.put("success","true");
            } else { 
              res.put("error","null ID or Project already exists");
              response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
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