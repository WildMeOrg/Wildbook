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


public class EncounterResetDate extends HttpServlet {

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
    Shepherd myShepherd = new Shepherd();
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    boolean isOwner = true;

    /**
     if(request.getParameter("number")!=null){
     myShepherd.beginDBTransaction();
     if(myShepherd.isEncounter(request.getParameter("number"))) {
     Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
     String locCode=verifyMyOwner.getLocationCode();

     //check if the encounter is assigned
     if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
     isOwner=true;
     }

     //if the encounter is assigned to this user, they have permissions for it...or if they're a manager
     else if((request.isUserInRole("admin"))){
     isOwner=true;
     }
     //if they have general location code permissions for the encounter's location code
     else if(request.isUserInRole(locCode)){isOwner=true;}
     }
     myShepherd.rollbackDBTransaction();
     }
     */
    if ((request.getParameter("number") != null) && (request.getParameter("day") != null) && (request.getParameter("month") != null) && (request.getParameter("year") != null) && (request.getParameter("hour") != null) && (request.getParameter("minutes") != null)) {
      myShepherd.beginDBTransaction();
      Encounter fixMe = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(fixMe);
      String oldDate = "";
      String newDate = "";


      try {

        oldDate = fixMe.getDate();
        fixMe.setDay(Integer.parseInt(request.getParameter("day")));
        fixMe.setMonth(Integer.parseInt(request.getParameter("month")));
        fixMe.setYear(Integer.parseInt(request.getParameter("year")));
        fixMe.setHour(Integer.parseInt(request.getParameter("hour")));
        fixMe.setMinutes(request.getParameter("minutes"));
        newDate = fixMe.getDate();
        fixMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed encounter date from " + oldDate + " to " + newDate + ".</p>");

      } catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }


      out.println(ServletUtilities.getHeader(request));
      if (!locked) {

        myShepherd.commitDBTransaction();
        out.println("<strong>Success:</strong> I have changed the encounter date from " + oldDate + " to " + newDate + ".");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
        String message = "The date of encounter #" + request.getParameter("number") + " was changed from " + oldDate + " to " + newDate + ".";
        ServletUtilities.informInterestedParties(request, request.getParameter("number"), message);
      } else {

        out.println("<strong>Failure:</strong> I have NOT changed the encounter date because another user is currently modifying this encounter. Please try this operation again in a few seconds.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");


      }
      out.println(ServletUtilities.getFooter());

    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
      out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
      out.println(ServletUtilities.getFooter());
    }


    out.close();
    myShepherd.closeDBTransaction();
  }
}
	
	
