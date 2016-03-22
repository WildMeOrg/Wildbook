package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.ActionResult;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.joda.time.DateTime;
import org.joda.time.format.*;

public class EncounterSetReleaseDate extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Shepherd myShepherd=new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("encounter"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link).setLinkParams(request.getParameter("encounter"));

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
          
          
          /*
          String pattern = CommonConfiguration.getProperty("releaseDateFormat",context);
          SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
          try {
            Date releaseDate = simpleDateFormat.parse(releaseDateStr);
            enc.setReleaseDate(releaseDate.getTime());
            sb.append(MessageFormat.format("Release Date set to {0}", releaseDateStr));
          } 
          catch (Exception e) {
            sb.append(MessageFormat.format("Error reading release date {0}. Expecting value in format {1}", releaseDateStr, pattern));
            badFormat = true;
          }
          */
          
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
        actionResult.setSucceeded(false);
      }
      else if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        actionResult.setSucceeded(false).setMessageOverrideKey("releaseDate").setMessageParams(encNum, newReleaseDate);
      }
      else {
        actionResult.setSucceeded(false).setMessageOverrideKey("locked");
      }
      
    }
    else {
      myShepherd.rollbackDBTransaction();
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
    myShepherd.closeDBTransaction();
  }
}
