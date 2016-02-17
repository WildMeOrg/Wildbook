package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.Locale;

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

    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);

    Shepherd myShepherd=new Shepherd(context);
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
        String link = ServletUtilities.getEncounterURL(request, context, enc.getCatalogNumber());
        ActionResult actRes = new ActionResult_Encounter(locale, "encounter.setPatterningCode", true, link)
                .setLinkParams(enc.getCatalogNumber())
                .setMessageParams(enc.getCatalogNumber(), colorCode);
        request.getSession().setAttribute(ActionResult.SESSION_KEY, actRes);
        getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);
      }
      else{

        String link = ServletUtilities.getEncounterURL(request, context, enc.getCatalogNumber());
        ActionResult actRes = new ActionResult_Encounter(locale, "encounter.setPatterningCode", false, link)
                .setLinkParams(enc.getCatalogNumber())
                .setCommentParams(enc.getCatalogNumber());
        request.getSession().setAttribute(ActionResult.SESSION_KEY, actRes);
        getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);
      }
                  }
                else {
                  myShepherd.rollbackDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println("<strong>Error:</strong> I was unable to set the colorCode. I cannot find the encounter that you intended in the database.");
                out.println(ServletUtilities.getFooter(context));

                  }
                out.close();
                myShepherd.closeDBTransaction();
                }





    }


