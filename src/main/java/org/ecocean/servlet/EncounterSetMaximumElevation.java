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


public class EncounterSetMaximumElevation extends HttpServlet {

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
    //boolean isOwner = true;
    String newElev = "null";

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link).setLinkParams(request.getParameter("number"));

    //reset encounter elevation in meters

    if (request.getParameter("number") != null ) {
      myShepherd.beginDBTransaction();
      Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(changeMe);
      String oldElev = "null";


      try {
        if(changeMe.getMaximumElevationInMeters()!=null){
          oldElev = changeMe.getMaximumElevationInMeters().toString()+" meters";
        }

        if((request.getParameter("elevation")!=null)&&(!request.getParameter("elevation").equals(""))){
          Double elevation = new Double(request.getParameter("elevation"));
          changeMe.setMaximumElevationInMeters(elevation);
          newElev = elevation.toString()+" meters";
        }
        else{
          changeMe.setMaximumElevationInMeters(null);
        }

        changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed reported elevation from " + oldElev + " to " + newElev + ".</p>");
      }
      catch (NumberFormatException nfe) {
        System.out.println("Bad numeric input on attempt to change elevation for the encounter.");
        locked = true;
        nfe.printStackTrace();
        myShepherd.rollbackDBTransaction();
      } catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }


      if (!locked) {
        myShepherd.commitDBTransaction();
        actionResult.setMessageOverrideKey("maximumElevation").setMessageParams(request.getParameter("number"), newElev, oldElev);

        String message = "The elevation of encounter " + request.getParameter("number") + " has been updated from " + oldElev + " meters to " + newElev + " meters.";
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
