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


public class EncounterSetInformOthers extends HttpServlet {

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
    myShepherd.setAction("EncounterSetInformOthers.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String sharky = "None";


    sharky = request.getParameter("encounter");
    String informers = "";
    myShepherd.beginDBTransaction();
    if ((myShepherd.isEncounter(sharky)) && (request.getParameter("informothers") != null)) {
      Encounter myShark = myShepherd.getEncounter(sharky);
      informers = request.getParameter("informothers").trim();
      try {
        myShark.setInformOthers(informers);
      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully changed the others to inform for encounter " + sharky + " to " + informers + ".</p>");
        response.setStatus(HttpServletResponse.SC_OK);
        //out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + sharky + "\">Return to encounter " + sharky + "</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));
        String message = "The others to inform for encounter " + sharky + " was set to " + informers + ".";
      } 
      else {

        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to modify this encounter again.");

        //out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + sharky + "\">Return to encounter " + sharky + "</a></p>\n");
        //out.println(ServletUtilities.getFooter(context));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

      }
    } 
    else {
      myShepherd.rollbackDBTransaction();
      //out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to set the others to inform. I cannot find the encounter that you intended in the database.");
      //out.println(ServletUtilities.getFooter(context));
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

    }
    out.close();
    myShepherd.closeDBTransaction();
  }


}
	
	
