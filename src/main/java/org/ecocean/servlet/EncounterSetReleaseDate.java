package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.joda.time.DateTime;
import org.joda.time.format.*;

public class EncounterSetReleaseDate extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction("EncounterSetReleaseDate.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;

    String encNum="None";

    encNum=request.getParameter("encounter");
    myShepherd.beginDBTransaction();
    StringBuilder sb = new StringBuilder();
    String oldReleaseDate="NULL";
    String newReleaseDate="NULL";
    if (myShepherd.isEncounter(encNum)) {
      boolean badFormat = false;
      Encounter enc=myShepherd.getEncounter(encNum);
      try {
        String releaseDateStr = request.getParameter("releasedatepicker");
        if (releaseDateStr != null && releaseDateStr.trim().length() > 0) {
          
          
          
         if(enc.getReleaseDateLong()!=null){
         
           DateTime jodaTime = new DateTime(enc.getReleaseDateLong().longValue());
           DateTimeFormatter parser1 = DateTimeFormat.forPattern("yyyy-MM-dd");
           
           oldReleaseDate=parser1.print(jodaTime);
         
         
         
         }
          
          DateTimeFormatter parser1 = ISODateTimeFormat.dateParser();
          DateTime reportedDateTime=parser1.parseDateTime(request.getParameter("releasedatepicker"));
          //System.out.println("Day of month is: "+reportedDateTime.getDayOfMonth()); 
          Long newLong=new Long(reportedDateTime.getMillis());
          enc.setReleaseDate(newLong.longValue());
          
          newReleaseDate=request.getParameter("releasedatepicker");
          
          
          enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed encounter release date from " + oldReleaseDate + " to " + newReleaseDate + ".</p>");
          
        }
        else {
          enc.setReleaseDate(null);
          sb.append("Release Date set to null");
        }
      } catch(Exception ex) {
        ex.printStackTrace();
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      if (badFormat) {
        myShepherd.rollbackDBTransaction();
        out.println(sb.toString());
        out.println("No changes were made.");
        out.println("<p><a href=\""+request.getScheme()+"://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));
      }
      else if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        //out.println(ServletUtilities.getHeader(request));
        response.setStatus(HttpServletResponse.SC_OK);
        out.println("<p><strong>Success!</strong> I have successfully set the following new release date: " +newReleaseDate);
        out.println(sb.toString());
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));
      }
      else {
        //out.println(ServletUtilities.getHeader(request));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user, or an exception occurred. Please wait a few seconds before trying to modify this encounter again.");

        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));
      }
      
    }
    else {
      myShepherd.rollbackDBTransaction();
      //out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to set the tag. I cannot find the encounter that you intended in the database.");
      //out.println(ServletUtilities.getFooter(context));
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

    }
    out.close();
    myShepherd.closeDBTransaction();
    
    
  }

}
