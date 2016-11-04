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


//Set verbatimEventDate for this encounter/sighting
public class EncounterSetVerbatimEventDate extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterSetVerbatimEventDate.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String sharky = "None";


    sharky = request.getParameter("encounter");
    String verbatimEventDate = "";
    myShepherd.beginDBTransaction();
    if(myShepherd.isEncounter(sharky)) {
      Encounter myShark = myShepherd.getEncounter(sharky);
      if(request.getParameter("verbatimEventDate") != null){
  	    verbatimEventDate = request.getParameter("verbatimEventDate");
  	  }
      try {
		if(!verbatimEventDate.equals("")){ myShark.setVerbatimEventDate(verbatimEventDate);}
		else{ myShark.setVerbatimEventDate(null);}

        myShark.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Set verbatim event date: " + verbatimEventDate + ".");

      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully changed the verbatim event date for encounter " + sharky + " to " + verbatimEventDate + ".</p>");
        response.setStatus(HttpServletResponse.SC_OK);
        //out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + sharky + "\">Return to encounter " + sharky + "</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));
        String message = "The alternate ID for encounter " + sharky + " was set to " + verbatimEventDate + ".";
      } 
      else {

        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to modify this encounter again.");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        //out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + sharky + "\">Return to encounter " + sharky + "</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));

      }
    } 
    else {
      myShepherd.rollbackDBTransaction();
      //out.println(ServletUtilities.getHeader(request));
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      out.println("<strong>Error:</strong> I was unable to set the verbatim event date. I cannot find the encounter that you intended it for in the database.");
      //out.println(ServletUtilities.getFooter(context));

    }
    out.close();
    myShepherd.closeDBTransaction();
  }


}


