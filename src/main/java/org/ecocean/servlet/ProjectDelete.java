package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONException;
import java.util.*;

import java.io.*;

public class ProjectDelete extends HttpServlet {

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

        System.out.println("==> In ProjectDelete Servlet ");

        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("ProjectDelete.java");
        myShepherd.beginDBTransaction();

        JSONObject res = new JSONObject();
        JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
        String projectIdPrefix = null;
        try {
            res.put("success","false");
            projectIdPrefix = j.optString("projectIdPrefix", null);

            System.out.println("ProjectDelete received JSON : "+j.toString());
            if (projectIdPrefix!=null&&!"".equals(projectIdPrefix)&&myShepherd.getProjectByProjectIdPrefix(projectIdPrefix)!=null) {

                Project project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix);
                List<Encounter> encounters = project.getEncounters();
                List<MarkedIndividual> individuals = new ArrayList<MarkedIndividual>();
                for(Encounter enc: encounters){
                  String comment = "<p><em>" + myShepherd.getUsername(request) + " on " + (new java.util.Date()).toString() + "</em><br>" + "removed this encounter from Project " + project.getResearchProjectName() + "</p>";
                  enc.addComments(comment);
                  MarkedIndividual currentIndividual = enc.getIndividual();
                  if(currentIndividual!= null && !individuals.contains(currentIndividual)){
                    comment = "<p><em>" + myShepherd.getUsername(request) + " on " + (new java.util.Date()).toString() + "</em><br>" + "removed this individual from Project " + project.getResearchProjectName() + "</p>";
                    currentIndividual.addComments(comment);
                    individuals.add(currentIndividual);
                  }
                  myShepherd.updateDBTransaction();
                }
                project.clearAllEncounters();
                myShepherd.throwAwayProject(project);
                myShepherd.updateDBTransaction();
                response.setStatus(HttpServletResponse.SC_OK);
                res.put("success","true");
            } else {
              res.put("error","null ID for project to delete");
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
