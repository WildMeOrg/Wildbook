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

import org.ecocean.ActionResult;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

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


//Set alternateID for this encounter/sighting
public class EncounterSetDynamicProperty extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    doPost(request, response);
  }

  private void setDateLastModified(Encounter enc) {

    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link).setLinkParams(request.getParameter("number"));

    //-------------------------------
    //set a dynamic property

    if ((request.getParameter("number") != null) && (request.getParameter("name") != null)) {
      myShepherd.beginDBTransaction();
      Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));

      String name = request.getParameter("name");
      String newValue = "null";
      String oldValue = "null";

      if (changeMe.getDynamicPropertyValue(name) != null) {
        oldValue = changeMe.getDynamicPropertyValue(name);
      }


      if ((request.getParameter("value") != null) && (!request.getParameter("value").equals(""))) {
        newValue = request.getParameter("value");
      }


      try {

        if (newValue.equals("null")) {
          changeMe.removeDynamicProperty(name);
          changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Removed dynamic property <em>" + name + "</em>. The old Value was <em>" + oldValue + ".</em></p>");
        } else {
          changeMe.setDynamicProperty(name, newValue);
          changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Set dynamic property <em>" + name + "</em> from <em>" + oldValue + "</em> to <em>" + newValue + "</em>.</p>");

        }


      } catch (Exception le) {
        System.out.println("Hit locked exception.");
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();

      }


      if (!locked) {
        setDateLastModified(changeMe);
        myShepherd.commitDBTransaction();
        actionResult.setMessageOverrideKey("dynamicProperty").setMessageParams(request.getParameter("number"), name, newValue, oldValue);
        if (newValue.equals("")) {
          actionResult.setMessageOverrideKey("dynamicProperty-remove");
        }

        String message = "Encounter " + request.getParameter("number") + " dynamic property " + name + " has been updated from \"" + oldValue + "\" to \"" + newValue + "\".";
        ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
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
