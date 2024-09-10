package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.movement.SurveyTrack;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

public class EncounterCreateSurvey extends HttpServlet {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    private void setDateLastModified(Encounter enc) {
        String strOutputDateTime = ServletUtilities.getDate();

        enc.setDWCDateLastModified(strOutputDateTime);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterCreateSurvey.class");

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        String surveyID = null;
        Encounter enc = null;
        Survey svy = null;
        SurveyTrack st = null;
        if (request.getParameter("surveyID") != null) {
            surveyID = ServletUtilities.cleanFileName(request.getParameter("surveyID"));
        }
        if (request.getParameter("number") != null) {
            String id = request.getParameter("number");
            try {
                myShepherd.beginDBTransaction();
                enc = myShepherd.getEncounter(id);
                myShepherd.commitDBTransaction();
            } catch (Exception e) {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                e.printStackTrace();
                out.println("Error retrieving encounter number " + id + " when creating survey.");
            }
        }
        if (!myShepherd.isSurvey(surveyID) && surveyID != null && enc.getSurveyID() == null) {
            setDateLastModified(enc);
            try {
                svy = new Survey(enc.getDate());
                if (surveyID != null) {
                    svy.setID(surveyID);
                }
                myShepherd.beginDBTransaction();
                myShepherd.storeNewSurvey(svy);
                myShepherd.commitDBTransaction();
            } catch (Exception e) {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                e.printStackTrace();
                out.println("Failed to create new Survey from ID : " + surveyID +
                    ". The survey could not be saved.");
            }
            try {
                st = new SurveyTrack();
                svy.addSurveyTrack(st);
                myShepherd.beginDBTransaction();
                myShepherd.getPM().makePersistent(svy);
                // myShepherd.storeNewSurveyTrack(st);
                myShepherd.commitDBTransaction();
                out.println("Success!");
            } catch (Exception e) {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                e.printStackTrace();
                out.println("Failed to create new SurveyTrack from Survey ID : " + surveyID +
                    ". The SurveyTrack could not be saved.");
            }
        } else {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> You cannot make a new survey for this encounter because it is already assigned to one. Remove it from its previous survey if you want to re-assign it.");
            // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
            // request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + ".</a></p>\n");
            // out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
    }
}
