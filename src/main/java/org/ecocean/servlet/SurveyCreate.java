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

public class SurveyCreate extends HttpServlet {

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
    myShepherd.setAction("SurveyCreate.class");
    
    // Set up for routing to result info.
    response.setContentType("text/html");
    
    
    try {
      out = response.getWriter();
    } catch (Exception e) {
      e.printStackTrace();
    }

    String date = null;
    if (request.getParameter("date")!=null) {
      date = request.getParameter("date");
    }

    myShepherd.beginDBTransaction();
    
    Survey sv = null;
    if (date!=null) {
      try {
        sv = new Survey(date);
      } catch (Exception e) {
        message += "<p><strong>Error: </strong> This survey did not recieve a valid date.</p>";
        e.printStackTrace();
      }      
    } else {
      message += "<p style=\"color:red;\"><strong>Error: </strong>Survey must be submitted with a date.</p>";
    }
    
    try {
      if (date!=null) {
        String project = null;
        if (request.getParameter("project")!=null) {
          project = request.getParameter("project");
          sv.setProjectName(project);
        }
        
        String organization = null;
        if (request.getParameter("organization")!=null) {
          organization = request.getParameter("organization");
          sv.setOrganization(organization);
        }
        
        if (request.getParameter("startTime")!=null&&!request.getParameter("startTime").equals("")) {
          String startTime = request.getParameter("startTime");
          long startTimeMilli = dateTimeToLong(date,startTime);
          sv.setStartTimeMilli(startTimeMilli);
          System.out.println("Endtime : "+startTimeMilli);
        }
        
        if (request.getParameter("endTime")!=null&&!request.getParameter("startTime").equals("")) {
          String endTime = request.getParameter("endTime");
          long endTimeMilli = dateTimeToLong(date,endTime);
          sv.setEndTimeMilli(endTimeMilli);
          System.out.println("Endtime : "+endTimeMilli);
        }
        
        String effort = null;
        if (request.getParameter("effort")!=null&&!request.getParameter("effort").equals("")) {
          effort = request.getParameter("effort");
          Double effNum = Double.valueOf(effort);
          Measurement eff = new Measurement("","",effNum,"HHmm","Observed");
          sv.setEffort(eff);
        }
        
        String comments = null;
        if (request.getParameter("comments")!=null) {
          comments = request.getParameter("comments");
          sv.addComments(comments);
        }
        
        String surveyType = null;
        if (request.getParameter("surveyType")!=null) {
          surveyType = request.getParameter("surveyType");
          sv.setProjectType(surveyType);
        }
     
        myShepherd.getPM().makePersistent(sv);
        myShepherd.commitDBTransaction();
        
        if (request.getParameter("getsTrack")!=null) {
          System.out.println("getsTrack says: "+request.getParameter("getsTrack"));
          if (request.getParameter("getsTrack").equals("true")) {
            SurveyTrack st = null;
            try {
              myShepherd.beginDBTransaction();
              st = createSurveyTrack(request, sv);
              myShepherd.commitDBTransaction();
              st.setParentSurveyID(sv.getID());
              sv.addSurveyTrack(st);
              System.out.println("Created Survey Track : "+st.getID());
              System.out.println("Did the survey get it? "+sv.getAllSurveyTracks().toString());
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
          }
          
        }
        
        message += "<p><strong>Success: </strong> A new survey on "+date+" was created.</p>";
        message += "<p><strong>View Survey Page: </strong><a href=\"/surveys/survey.jsp?surveyID="+sv.getID()+"\">Survey ID: "+sv.getID()+"</a></p>";
        message += "<p>From the Survey home page you can add occurrences and other info.</p>";      
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

  private SurveyTrack createSurveyTrack(HttpServletRequest request, Survey sv) {
    SurveyTrack st = new SurveyTrack(sv.getID());  
    if (request.getParameter("vessel")!=null) {
      String vessel = null;
      vessel = request.getParameter("vessel");
      st.setVesselID(vessel);
    }
    if (request.getParameter("locationID")!=null) {
      String locationID = null;
      locationID = request.getParameter("locationID");
      st.setLocationID(locationID);
    }
    if (request.getParameter("type")!=null) {
      String type = null;
      type = request.getParameter("type");
      st.setType(type);
    }
    return st;
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








