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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;


public class OccurrenceAddEncounter extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  private void setDateLastModified(Encounter enc) {

    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false, isOwner = true;
    boolean isAssigned = false;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link).setLinkParams(request.getParameter("number"));

    //String action = request.getParameter("action");

    //add encounter to an existing Occurrence object

    if ((request.getParameter("number") != null) && (request.getParameter("occurrence") != null) ) {

      String altID = "";
      myShepherd.beginDBTransaction();
      Encounter enc2add = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(enc2add);
      //String tempName = enc2add.isAssignedToMarkedIndividual();
      if ((myShepherd.isOccurrence(request.getParameter("occurrence")))&&(myShepherd.getOccurrenceForEncounter(request.getParameter("number"))==null)) {
        try {


          //boolean sexMismatch = false;

          //myShepherd.beginDBTransaction();
          Occurrence addToMe = myShepherd.getOccurrence(request.getParameter("occurrence").trim());

          try {
            if (!addToMe.getEncounters().contains(enc2add)) {
              addToMe.addEncounter(enc2add);
              
            }
            enc2add.setOccurrenceID(request.getParameter("occurrence").trim());
            enc2add.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added to occurrence " + request.getParameter("occurrence") + ".</p>");
            addToMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added encounter " + request.getParameter("number") + ".</p>");

            
          } catch (Exception le) {
            le.printStackTrace();
            locked = true;
            myShepherd.rollbackDBTransaction();

          }


          if (!locked) {

            myShepherd.commitDBTransaction();
            myShepherd.rollbackDBTransaction();
            actionResult.setMessageOverrideKey("addToOccurrence").setMessageParams(request.getParameter("number"), request.getParameter("occurrence"));
          }

          //if lock exception thrown
          else {
            actionResult.setSucceeded(false).setMessageOverrideKey("locked");
          }


        } catch (Exception e) {
          actionResult.setSucceeded(false);
        }
      } 
      else {
        actionResult.setSucceeded(false).setMessageOverrideKey("addToOccurrence-assigned");
      }


    } 
    else {
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
    myShepherd.closeDBTransaction();
  }
}
