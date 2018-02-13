package org.ecocean.servlet;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.*;
import org.ecocean.movement.SurveyTrack;

public class AddOccToTrack extends HttpServlet {

  /** @author Colin Kingen
   *  2018
   */
  private static final long serialVersionUID = 1L;
  private String message = "";
  PrintWriter out = null;
  
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    try {
      doPost(request, response);      
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("AddOccToTrack.class");
    response.setContentType("text/html");

    try {
        out = response.getWriter();
    } catch (Exception e) {
        e.printStackTrace();
    }

    String occID = null;
    if (request.getParameter("occID")!=null) {
      occID = request.getParameter("occID");
    }

    String trackID = null;
    if (request.getParameter("trackID")!=null) {
      trackID = request.getParameter("trackID");
    }

    String surveyID = null;
    if (request.getParameter("surveyID")!=null) {
      surveyID = request.getParameter("surveyID");
    }

    myShepherd.beginDBTransaction();

    SurveyTrack st = null;
    if (trackID!=null) {
        try {
            st = myShepherd.getSurveyTrack(trackID);
        } catch (Exception e) {
            message += "<p><strong>Error: </strong> There was not a survey track available for the ID submitted.</p>";
            e.printStackTrace();
        }      
    } else {
        message += "<p style=\"color:red;\"><strong>Error: </strong>A valid survey track ID must be submitted.</p>";
    }

    Occurrence occ = null;
    if (occID!=null&&st!=null) {
        try {
            occ = myShepherd.getOccurrence(occID);
            st.addOccurrence(occ, myShepherd);
        } catch (Exception e) {
            message += "<p><strong>Error: </strong> There was not a occurrence with that ID to retrieve.</p>";
            e.printStackTrace();
        }      
    } else {
        message += "<p style=\"color:red;\"><strong>Error: </strong>A valid occurrence ID must be submitted.</p>";
    }
    printResultMessage(request, context, surveyID);
  }
  
  private void printResultMessage(HttpServletRequest request, String context, String surveyID) {
    try {
        out.println(ServletUtilities.getHeader(request));
        message += "<p><strong>Success: </strong>The occurrence was added to the selected survey track.</p>";
        message += "<p><strong>Back: </strong><a href=\"/surveys/survey.jsp?surveyID="+surveyID+"\">Return to Survey Page</a></p>";
        out.println(message);
        out.println(ServletUtilities.getFooter(context));
        message = "";
    } catch (Exception e)  {
        e.printStackTrace();
    }
    out.close();
  }
} 
 