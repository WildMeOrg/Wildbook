package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;

import org.ecocean.*;


public class EncounterSetPatterningCode extends HttpServlet {

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
    myShepherd.setAction("EncounterSetPatterningCode.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;

    String encNum="None";



    encNum=request.getParameter("number");
    String colorCode="";
    myShepherd.beginDBTransaction();
    if ((myShepherd.isEncounter(encNum))&&(request.getParameter("patterningCode")!=null)) {
      Encounter enc=myShepherd.getEncounter(encNum);
      colorCode=request.getParameter("patterningCode").trim();
      try{

        if(colorCode.equals("None")){enc.setPatterningCode(null);}
        else{
        	enc.setPatterningCode(colorCode);
		}


      }
      catch(Exception le){
        le.printStackTrace();
        locked=true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

      if(!locked){
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully changed the colorCode for encounter "+encNum+" to "+colorCode+".</p>");
        response.setStatus(HttpServletResponse.SC_OK);
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));
        String message="The colorCode for encounter "+encNum+" was set to "+colorCode+".";
      }
      else{

        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> An exception occurred during processing. Please ask the webmaster to check the log for more information.");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+encNum+"\">Return to encounter "+encNum+"</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));

      }
                  }
                else {
                  myShepherd.rollbackDBTransaction();
                //out.println(ServletUtilities.getHeader(request));
                out.println("<strong>Error:</strong> I was unable to set the colorCode. I cannot find the encounter that you intended in the database.");
               // out.println(ServletUtilities.getFooter(context));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                  }
                out.close();
                myShepherd.closeDBTransaction();
                }





    }


