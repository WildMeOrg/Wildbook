package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class IndividualAddIncrementalProjectId extends HttpServlet {
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
        System.out.println("==> In IndividualAddIncrementalProjectId Servlet ");
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IndividualAddIncrementalProjectId.java");
        myShepherd.beginDBTransaction();
        PrintWriter out = response.getWriter();
        JSONObject res = new JSONObject();
        try {
            res.put("success", false);
            res.put("newProjectIdForIndividual", "");
            JSONObject j = ServletUtilities.jsonFromHttpServletRequest(request);
            String projectIdPrefix = j.optString("projectIdPrefix", null);
            String individualId = j.optString("individualId", null);
            if (Util.stringExists(projectIdPrefix) && Util.stringExists(individualId)) {
                Project project = myShepherd.getProjectByProjectIdPrefix(projectIdPrefix);
                MarkedIndividual individual = myShepherd.getMarkedIndividual(individualId);
                if (individual != null) {
                    individual.addIncrementalProjectId(project);
                    if (individual.hasNameKey(projectIdPrefix)) {
                        String newProjectIdForIndividual = individual.getName(projectIdPrefix);
                        res.put("success", true);
                        res.put("newProjectIdForIndividual", newProjectIdForIndividual);
                        System.out.println("adding comment in IndividualAddIncrementalProjectId");
                        individual.addComments("<p><em>" + myShepherd.getUsername(request) +
                            " on " + (new java.util.Date()).toString() + "</em><br>" +
                            "added incremental ID " + newProjectIdForIndividual +
                            " to this individual</p>");
                    } else {
                        addErrorMessage(res,
                            "the projectIdPrefix was not successfully added to " + individualId +
                            ", but no exception was thrown");
                        res.put("success", false);
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }
                    myShepherd.updateDBTransaction();
                } else {
                    addErrorMessage(res, "invalid individual Id");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
            } else {
                addErrorMessage(res, "not enough information was sent to the server");
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
        System.out.println("addErrorMessage entered");
        res.put("error", error);
        System.out.println("addErrorMessage put is done");
        System.out.println("res is: " + res.toString());
    }
}
