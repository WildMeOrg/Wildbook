/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;


public class IndividualSetSex extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Map<String, String> mapI18n = CommonConfiguration.getI18nPropertiesMap("sex", langCode, context, false);

    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/individuals.jsp?number=%s", request.getParameter("individual"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "individual.editField", true, link)
            .setParams(request.getParameter("individual"));

    String action = request.getParameter("action");
    if ((request.getParameter("individual") != null) && (request.getParameter("selectSex") != null)) {

      myShepherd.beginDBTransaction();
      MarkedIndividual changeMe = myShepherd.getMarkedIndividual(request.getParameter("individual"));
      String oldSex = "null";
      String newSex = "null";
      try {

        if (changeMe.getSex() != null) {
          oldSex = changeMe.getSex();
        }
        if(request.getParameter("selectSex")!=null){
          changeMe.setSex(request.getParameter("selectSex"));
          newSex=request.getParameter("selectSex");
          actionResult.setMessageParams(request.getParameter("individual"), mapI18n.get(newSex), mapI18n.get(oldSex));
         }
        else{
          changeMe.setSex(null);
          actionResult.setMessageParams(request.getParameter("individual"), "", mapI18n.get(oldSex));
        }
        //changeMe.setSex(request.getParameter("selectSex"));
        
        changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed sex from " + oldSex + " to " + newSex + ".</p>");
      } catch (Exception le) {
        //System.out.println("Hit locked exception on action: "+action);
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }


      if (!locked) {
        myShepherd.commitDBTransaction(action);
        actionResult.setMessageOverrideKey("sex");
      } else {
        actionResult.setSucceeded(false).setMessageOverrideKey("locked");
      }

    } else {
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
    myShepherd.closeDBTransaction();
  }
}
