package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

public class EncounterSetReleaseDate extends HttpServlet {

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    Shepherd myShepherd=new Shepherd();
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;

    String encNum="None";

    encNum=request.getParameter("encounter");
    myShepherd.beginDBTransaction();
    StringBuilder sb = new StringBuilder();
    if (myShepherd.isEncounter(encNum)) {
      boolean badFormat = false;
      Encounter enc=myShepherd.getEncounter(encNum);
      try {
        String releaseDateStr = request.getParameter("releaseDate");
        if (releaseDateStr != null && releaseDateStr.trim().length() > 0) {
          String pattern = CommonConfiguration.getProperty("releaseDateFormat");
          SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
          try {
            Date releaseDate = simpleDateFormat.parse(releaseDateStr);
            enc.setReleaseDate(releaseDate);
            sb.append(MessageFormat.format("Release Date set to {0}", releaseDateStr));
          } catch (Exception e) {
            sb.append(MessageFormat.format("Error reading release date {0}. Expecting value in format {1}", releaseDateStr, pattern));
            badFormat = true;
          }
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
        out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        out.println(ServletUtilities.getFooter());
      }
      else if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<p><strong>Success!</strong> I have successfully set the following tag values:");
        out.println(sb.toString());
        out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        out.println(ServletUtilities.getFooter());
      }
      else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user, or an exception occurred. Please wait a few seconds before trying to modify this encounter again.");

        out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        out.println(ServletUtilities.getFooter());
      }
      
    }
    else {
      myShepherd.rollbackDBTransaction();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to set the tag. I cannot find the encounter that you intended in the database.");
      out.println(ServletUtilities.getFooter());

    }
    out.close();
    myShepherd.closeDBTransaction();
    
    
  }

}
