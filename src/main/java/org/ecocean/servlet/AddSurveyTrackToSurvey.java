package org.ecocean.servlet;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.*;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.ecocean.CommonConfiguration;
import org.ecocean.movement.Path;
import org.ecocean.movement.SurveyTrack;

public class AddSurveyTrackToSurvey extends HttpServlet {

  /** @author Colin Kingen
   *  2017
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
    myShepherd.setAction("AddSurveyTrackToSurvey.class");
    response.setContentType("text/html");
    try {
      out = response.getWriter();
    } catch (Exception e) {
      e.printStackTrace();
    }

    String surveyID = null;
    if (request.getParameter("surveyID")!=null) {
      surveyID = request.getParameter("surveyID");
    }

    myShepherd.beginDBTransaction();
    
    Survey sv = null;
    if (surveyID!=null) {
      try {
        sv = myShepherd.getSurvey(surveyID);
      } catch (Exception e) {
        message += "<p><strong>Error: </strong> There was not a survey available for the ID submitted.</p>";
        e.printStackTrace();
      }      
    } else {
      message += "<p style=\"color:red;\"><strong>Error: </strong>Survey must be submitted with a date.</p>";
    }
    
    try {
      if (sv!=null) {
        
        SurveyTrack st = new SurveyTrack(surveyID);
        myShepherd.getPM().makePersistent(st);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        
        String locationID = null;
        if (request.getParameter("locationID")!=null) {
          locationID = request.getParameter("locationID");
          st.setLocationID(locationID);
        }
        
        String vessel = null;
        if (request.getParameter("vessel")!=null) {
          vessel = request.getParameter("vessel");
          st.setVesselID(locationID);
        }
        
        String effort = null;
        if (request.getParameter("effort")!=null&&!request.getParameter("effort").equals("")) {
          effort = request.getParameter("effort");
          Double effNum = Double.valueOf(effort);
          
          Measurement oldEffort = sv.getEffort();
          Double oldValue = oldEffort.getValue();
          
          Double newValue = oldValue + effNum;
          if (newValue!=null&&!newValue.equals(oldValue)) {
            Measurement eff = new Measurement("","",newValue,"HHmm","Observed");
            sv.setEffort(eff);
            
          }
        }
        
        String type = null;
        if (request.getParameter("type")!=null) {
          type = request.getParameter("type");
          st.setType(type);
        }
        
        try {
          st.setParentSurveyID(sv.getID());
          sv.addSurveyTrack(st);
        } catch (Exception e) {
          myShepherd.rollbackDBTransaction();
          e.printStackTrace();
        }
        
        try {
          myShepherd.beginDBTransaction();
          Path pth = createPath(request, st);
          myShepherd.getPM().makePersistent(pth);
          myShepherd.commitDBTransaction();
          System.out.println("Persisted Path "+pth.getID());
        } catch (Exception e) {
          myShepherd.rollbackDBTransaction();
          System.out.println("Failed to persist a Path for this survey.");
          e.printStackTrace();
        }
        
        message += "<p>The SurveyTrack was successfully created.</p>";      
        message += "<p><strong>Back To Survey: </strong><a href=\"/surveys/survey.jsp?surveyID="+sv.getID()+"\">Survey ID: "+sv.getID()+"</a></p>";
      }
    } catch (Exception e) {
      message += "<p><strong>Error: </strong>There was data included in the submission that the server could not process.</p>";
      myShepherd.rollbackDBTransaction();
      e.printStackTrace();
    }
    printResultMessage(request, context);
    out.close();
  }
  
  private Long dateTimeToLong(String dateString, String timeString) {
    try {
      dateString = dateString.replaceAll("/","-");
      DateTimeFormatter in = DateTimeFormat.forPattern("MM-dd-yyyy HH:mm"); 
      DateTime mtFormatted = in.parseDateTime(dateString+" "+timeString); 
      return mtFormatted.getMillis();     
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
  
  private void printResultMessage(HttpServletRequest request, String context) {
    out.println(ServletUtilities.getHeader(request));
    message += "<p><strong>Back: </strong><a href=\"/surveys/createSurvey.jsp\">Return to Survey Creation Page</a></p>";
    out.println(message);
    out.println(ServletUtilities.getFooter(context));
    message = "";
  }
  
  private Path createPath(HttpServletRequest request, SurveyTrack st) {
    Path pth = new Path(st);
    return pth;
  }
  
} 
 