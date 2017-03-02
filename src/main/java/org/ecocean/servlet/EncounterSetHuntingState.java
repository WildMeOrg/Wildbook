package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;

import org.ecocean.*;


public class EncounterSetHuntingState extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction("EncounterSetHuntingState.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;

    String encNum = request.getParameter("encounter");
    String huntingState = request.getParameter("huntingState");
    myShepherd.beginDBTransaction();

    if (!myShepherd.isEncounter(encNum)) {

      System.out.println("encounter #"+encNum+" was not found in the database");

      myShepherd.rollbackDBTransaction();
      //out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to set the huntingState. I cannot find encounter"+encNum+" that you intended in the database.");
      //out.println(ServletUtilities.getFooter(context));
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      out.close();
      myShepherd.closeDBTransaction();
      return;

    } // all that follows assumes we have an encounter

    Encounter enc = myShepherd.getEncounter(encNum);

    if (huntingState.equals("")) enc.setHuntingState(null);
    else enc.setHuntingState(huntingState);

    enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br />Changed huntingState to " + huntingState + ".</p>");
    System.out.println("Encounter "+encNum+" added huntingState "+huntingState);

    try {
      myShepherd.commitDBTransaction();
      out.println("<strong>Success!</strong> I have successfully changed the reported huntingState for encounter "+encNum+" to "+huntingState+".</p>");
      response.setStatus(HttpServletResponse.SC_OK);
    }

    catch (Exception e) {
      System.out.println("Exception called on encounter "+encNum+" .setHuntingState("+huntingState+")");
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();

      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      out.println("<strong>Failure!</strong> This encounter is currently being modified by another user, or an exception occurred. Please wait a few seconds before trying to modify this encounter again.");
    }

    finally {
      out.close();
      myShepherd.closeDBTransaction();
    }
  }
}
