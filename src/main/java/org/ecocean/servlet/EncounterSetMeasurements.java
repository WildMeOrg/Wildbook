package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

public class EncounterSetMeasurements extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    doPost(req, resp);
  }

  @Override
  protected void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
//    Shepherd myShepherd=new Shepherd();
//    //set up for response
//    response.setContentType("text/html");
//    PrintWriter out = response.getWriter();
//    boolean locked=false;
//
//    String encNum="None";
//
//
//
//    encNum=request.getParameter("encounter");
//    myShepherd.beginDBTransaction();
//    if ((myShepherd.isEncounter(encNum))&&(request.getParameter("measurements")!=null)) {
//      Encounter enc=myShepherd.getEncounter(encNum);
//      lifeStage=request.getParameter("lifeStage").trim();
//      try{
//
//        if(lifeStage.equals("")){enc.setLifeStage(null);}
//        else{
//          enc.setLifeStage(lifeStage);
//        }
//
//
//      }
//      catch(Exception le){
//        locked=true;
//        myShepherd.rollbackDBTransaction();
//        myShepherd.closeDBTransaction();
//      }
//
//      if(!locked){
//        myShepherd.commitDBTransaction();
//        myShepherd.closeDBTransaction();
//        out.println(ServletUtilities.getHeader(request));
//        out.println("<strong>Success!</strong> I have successfully changed the lifeStage for encounter "+encNum+" to "+lifeStage+".</p>");
//
//        out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
//        out.println(ServletUtilities.getFooter());
//        String message="The lifeStage for encounter "+encNum+" was set to "+lifeStage+".";
//      }
//      else{
//
//        out.println(ServletUtilities.getHeader(request));
//        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user, or an exception occurred. Please wait a few seconds before trying to modify this encounter again.");
//
//        out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
//        out.println(ServletUtilities.getFooter());
//
//      }
//    }
//    else {
//      myShepherd.rollbackDBTransaction();
//      out.println(ServletUtilities.getHeader(request));
//      out.println("<strong>Error:</strong> I was unable to set the lifeStage. I cannot find the encounter that you intended in the database.");
//      out.println(ServletUtilities.getFooter());
//
//    }
//    out.close();
//    myShepherd.closeDBTransaction();
  }    

  
}
