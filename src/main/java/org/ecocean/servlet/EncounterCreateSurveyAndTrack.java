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

import org.ecocean.*;
import org.ecocean.movement.Path;
import org.ecocean.movement.SurveyTrack;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.ThreadPoolExecutor;


public class EncounterCreateSurveyAndTrack extends HttpServlet {

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
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterCreateSurveyAndTrack.class");
    
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String surveyID = null;
    String surveyTrackID = null;
    String encID = null;
    
    Encounter enc = null;
    Survey svy = null;
    SurveyTrack st = null;
    
    if (request.getParameter("surveyID") != null) {
      surveyID = ServletUtilities.cleanFileName(request.getParameter("surveyID"));              
    }
    
    if(request.getParameter("number") != null){
      String id = request.getParameter("number");
      try {
        myShepherd.beginDBTransaction();
        enc = myShepherd.getEncounter(id);
        myShepherd.commitDBTransaction();
        encID = id;        
      } catch (Exception e) {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        e.printStackTrace();
        System.out.println("Did not find encounter for ID : "+id+" when creating survey.");
      }
    }
    
    if (!myShepherd.isSurvey(surveyID) || surveyID == null) {
      setDateLastModified(enc);
      try {
        svy = new Survey(enc.getDate());
        if (surveyID != null) {
          svy.setID(surveyID);       
        } else {
          surveyID = svy.getID();
        }
        myShepherd.beginDBTransaction();
        myShepherd.storeNewSurvey(svy);
        myShepherd.commitDBTransaction();
      } catch (Exception e) {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        locked = true;
        e.printStackTrace();
        System.out.println("Failed to create new Survey from ID : "+surveyID);
      }
      
      try {
        st = new SurveyTrack(svy);
        myShepherd.beginDBTransaction();
        myShepherd.storeNewSurveyTrack(st);
        myShepherd.commitDBTransaction();
      } catch (Exception e) {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        locked = true;
        e.printStackTrace();
        System.out.println("Failed to create new SurveyTrack from Survey ID : "+surveyID);
      }
      
    } else {
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      //out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> You cannot make a new occurrence from this encounter because it is already assigned to another occurrence. Remove it from its previous occurrence if you want to re-assign it elsewhere.");
      //out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + ".</a></p>\n");
      //out.println(ServletUtilities.getFooter(context));
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
    }
    out.close();
  }
}


