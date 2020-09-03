package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.MarkedIndividual;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.*;

public class IndividualCreateForProject extends HttpServlet {

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
        
        System.out.println("==> In IndividualCreateForProject Servlet ");
        
        String context= ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IndividualCreateForProject.java");
        myShepherd.beginDBTransaction();
        
        JSONObject res = new JSONObject();
        JSONObject j = null;

        try {
            j = ServletUtilities.jsonFromHttpServletRequest(request);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            addErrorMessage(res, "IOException unpacking JSON from request");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        
        try {            
            res.put("success","false");
            String encounterId = j.optString("encounterId", null);
            String projectId = j.optString("projectId", null);

            if (Util.stringExists(encounterId)&&Util.stringExists(projectId)) {

                Encounter enc = myShepherd.getEncounter(encounterId);
                if (enc!=null) {
                    Project project = myShepherd.getProject(projectId);
                    if (project==null) {
                        project = myShepherd.getProjectByResearchProjectId(projectId);
                    }
                    if (project!=null) {

                        MarkedIndividual individual = new MarkedIndividual(enc);
                        myShepherd.storeNewMarkedIndividual(individual);
                        individual.addIncrementalProjectId(project);
                        myShepherd.updateDBTransaction();

                        res.put("newIndividualId", individual.getId());
                        res.put("newIndividualName", individual.getName(projectId));
                        res.put("success","true");
                    } else {
                        addErrorMessage(res, "there was not a valid project for the Id or researchProjectId provided");
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    }
                } else {
                    addErrorMessage(res, "there was not a valid encounter for the Id provided");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
            } else {
                addErrorMessage(res, "not enough information was sent to the server to create a new project individual");
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