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

//import com.poet.jdo.*;


public class EncounterSetMatchedBy extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);


  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    // Here we forward to the appropriate page using the request dispatcher

    //getServletContext().getRequestDispatcher("/Noget.html").forward(req, res);
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    //initialize shepherd
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterSetMatchedBy.class");

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    //setup variables
    String encounterNumber = "None";
    String matchedBy = "Unknown", prevMatchedBy = "";

    myShepherd.beginDBTransaction();


    encounterNumber = request.getParameter("number");
    if (request.getParameter("matchedBy") != null) matchedBy = request.getParameter("matchedBy");
    if ((myShepherd.isEncounter(encounterNumber)) && (request.getParameter("number") != null)) {
      Encounter sharky = myShepherd.getEncounter(encounterNumber);


      try {


        if (sharky.getMatchedBy() != null) {
          prevMatchedBy = sharky.getMatchedBy();
        }

        sharky.setMatchedBy(matchedBy);
        sharky.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Changed matched by type from " + prevMatchedBy + " to " + matchedBy + ".</p>");

      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();

      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        //myShepherd.closeDBTransaction();
        //out.println(ServletUtilities.getHeader(request));
        response.setStatus(HttpServletResponse.SC_OK);
        out.println("<strong>Success!</strong> I have successfully changed the matched by type for encounter " + encounterNumber + " from " + prevMatchedBy + " to " + matchedBy + ".</p>");

        //out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));
        String message = "The matched by type for encounter " + encounterNumber + " was changed from " + prevMatchedBy + " to " + matchedBy + ".";
        ServletUtilities.informInterestedParties(request, encounterNumber, message,context);
      } 
      else {

        //out.println(ServletUtilities.getHeader(request));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to remove this data file again.");

        //out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));

      }
    } 
    else {

      //out.println(ServletUtilities.getHeader(request));
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      out.println("<strong>Error:</strong> I was unable to set the matched by type. I cannot find the marked individual that you intended it for in the database, or I wasn't sure what file you wanted to remove.");
      //out.println(ServletUtilities.getFooter(context));

    }
    out.close();

    myShepherd.closeDBTransaction();
  }


}
	
	
