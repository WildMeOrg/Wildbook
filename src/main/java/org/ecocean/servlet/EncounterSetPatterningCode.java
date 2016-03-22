package org.ecocean.servlet;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

import org.ecocean.*;


public class EncounterSetPatterningCode extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Map<String, String> mapI18n = CommonConfiguration.getI18nPropertiesMap("patterningCode", langCode, context, false);

    Shepherd myShepherd=new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link).setLinkParams(request.getParameter("number"));

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
        actionResult.setMessageOverrideKey("patterningCode").setMessageParams(request.getParameter("number"), mapI18n.get(colorCode));
      }
      else{
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
