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
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;


public class OccurrenceCreate extends HttpServlet {

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

    String myOccurrenceID="";
    if(request.getParameter("occurrence") != null){
      myOccurrenceID=request.getParameter("occurrence");
      
      //remove special characters
      myOccurrenceID=ServletUtilities.cleanFileName(myOccurrenceID);
      
    }

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link).setLinkParams(request.getParameter("number"));
    actionResult.setMessageParams(request.getParameter("number"), request.getParameter("occurrence"));

    //Create a new Occurrence from an encounter

    if ((myOccurrenceID != null) && (request.getParameter("number") != null) &&  (!myOccurrenceID.trim().equals(""))) {
      myShepherd.beginDBTransaction();
      Encounter enc2make = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(enc2make);


      boolean ok2add=true;

      if (!(myShepherd.isOccurrence(myOccurrenceID))) {


        if ((myShepherd.getOccurrenceForEncounter(enc2make.getCatalogNumber())==null) && (myOccurrenceID != null)) {
          try {
            Occurrence newOccur = new Occurrence(myOccurrenceID.trim(), enc2make);
            newOccur.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Created " + myOccurrenceID + " from encounter "+request.getParameter("number")+".</p>");
            newOccur.setDateTimeCreated(ServletUtilities.getDate());
            myShepherd.storeNewOccurrence(newOccur);
            
            enc2make.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Added to new occurrence " + myOccurrenceID + ".</p>");
            enc2make.setOccurrenceID(myOccurrenceID.trim());
          } 
          catch (Exception le) {
            locked = true;
            le.printStackTrace();
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
          }

          if (!locked&&ok2add) {
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();

            //output success statement
            actionResult.setMessageOverrideKey("createOccurrence");
            String linkEnc = String.format("http://%s/encounters/encounter.jsp?number=%s", CommonConfiguration.getURLLocation(request), request.getParameter("number"));
            String linkOcc = String.format("http://%s/occurrence.jsp?number=%s", CommonConfiguration.getURLLocation(request), myOccurrenceID);
            actionResult.setLinkOverrideKey("createOccurrence").setLinkParams(request.getParameter("number"), linkEnc, request.getParameter("occurrence"), linkOcc);
          }
          else {
            actionResult.setSucceeded(false).setMessageOverrideKey("locked");
          }


        } else {

          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          actionResult.setSucceeded(false);

        }

      } 
      else if ((myShepherd.isOccurrence(myOccurrenceID))) {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        actionResult.setSucceeded(false).setMessageOverrideKey("createOccurrence-exists");
      }
      else {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        actionResult.setSucceeded(false).setMessageOverrideKey("createOccurrence-assigned");
      }


    } 
    else {
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
  }
}
