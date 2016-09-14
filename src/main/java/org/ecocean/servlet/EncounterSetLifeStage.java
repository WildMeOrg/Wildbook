package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;

import org.ecocean.*;


public class EncounterSetLifeStage extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction("EncounterSetLifeStage.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;

    String encNum="None";



    encNum=request.getParameter("encounter");
    String lifeStage="";
    myShepherd.beginDBTransaction();
    if ((myShepherd.isEncounter(encNum))&&(request.getParameter("lifeStage")!=null)) {
      Encounter enc=myShepherd.getEncounter(encNum);
      lifeStage=request.getParameter("lifeStage").trim();
      try{

        if(lifeStage.equals("")){enc.setLifeStage(null);}
        else{
        	enc.setLifeStage(lifeStage);
		}
        enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed life stage to " + request.getParameter("lifeStage") + ".</p>");
        

      }
      catch(Exception le){
        locked=true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

      if(!locked){
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully changed the lifeStage for encounter "+encNum+" to "+lifeStage+".</p>");
        response.setStatus(HttpServletResponse.SC_OK);
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));
        String message="The lifeStage for encounter "+encNum+" was set to "+lifeStage+".";
        
        
      }
      else{

        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user, or an exception occurred. Please wait a few seconds before trying to modify this encounter again.");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));

      }
                  }
                else {
                  myShepherd.rollbackDBTransaction();
                //out.println(ServletUtilities.getHeader(request));
                out.println("<strong>Error:</strong> I was unable to set the lifeStage. I cannot find the encounter that you intended in the database.");
                //out.println(ServletUtilities.getFooter(context));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                  }
                out.close();
                myShepherd.closeDBTransaction();
                }





    }


