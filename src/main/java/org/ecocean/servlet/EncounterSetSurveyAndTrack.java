/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2017 Jason Holmberg
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

public class EncounterSetSurveyAndTrack extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterSetSurveyAndTrack.class");
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String encID = null;
    String surveyID = null;
    String surveyTrackID = null;
    
    try {
      encID = request.getParameter("encID");
      surveyID = request.getParameter("surveyID");
      surveyTrackID = request.getParameter("surveyTrackID");   
    } catch (Exception e) {
      e.printStackTrace();
      out.println("Error grabbing parameters for change in Survey or ID for this Encounter!");
    }
    
    myShepherd.beginDBTransaction();
    if ((myShepherd.isEncounter(encID)) && (surveyID != null) && (surveyTrackID != null)) {
      Encounter thisEnc = myShepherd.getEncounter(encID);
      try {
        thisEnc.setSurveyID(surveyID);
        thisEnc.setSurveyTrackID(surveyTrackID);
      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        out.println("Failed to change surveyID and surveyTrackID");
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println("The Survey/Track ID's for encounter " + encID + " are now " + surveyID + " and "+ surveyTrackID);
        response.setStatus(HttpServletResponse.SC_OK);
      } 
      else {
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user or is inaccessible. Please wait a few seconds before trying to modify this encounter again.");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
    } else {
      myShepherd.rollbackDBTransaction();
      out.println("<strong>Error:</strong> I was unable to set survey information. I cannot find the encounter that you intended it for in the database.");
      out.println("Enc ID : "+encID+" SurveyID : "+surveyID+" SurveyTrackID : "+surveyTrackID);
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
    out.close();
    myShepherd.closeDBTransaction();
  }
}
  
  