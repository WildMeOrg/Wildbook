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


public class EncounterSetCharterOperator extends HttpServlet {

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
    myShepherd.setAction("EncounterSetCharterOperator.class");

    //System.out.println(" ---- Hit Charter Operator Servlet ---- ");

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String encID = request.getParameter("number");
    String co = "";
    myShepherd.beginDBTransaction();
    if (encID != null && myShepherd.isEncounter(encID) && request.getParameter("charterOperator") != null) {
        Encounter enc = myShepherd.getEncounter(encID);
        co = request.getParameter("charterOperator").trim();
        //System.out.println("+++++++++++++++++ Set Charter Operator: "+co);
      try {
        enc.setCharterOperator(co);
      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully changed the charter operator for encounter " + encID + " to " + co + ".</p>");
        response.setStatus(HttpServletResponse.SC_OK);

        String message = "The charter operator for encounter " + encID + " was set to " + co + ".";
      } else {

        //out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to modify this encounter again.");

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

      }
    } else {
      myShepherd.rollbackDBTransaction();
      //out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to set the charter operator. I cannot find the encounter that you intended in the database.");
      //out.println(ServletUtilities.getFooter(context));
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

    }
    out.close();
    myShepherd.closeDBTransaction();
  }


}
	
	