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
import org.ecocean.grid.GridManagerFactory;
import org.json.JSONObject;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.ConcurrentHashMap;

import org.ecocean.grid.EncounterLite;
import org.ecocean.grid.GridManager;


public class EncounterRemoveSpots extends HttpServlet {

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
    myShepherd.setAction("EncounterRemoveSpots.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    boolean isOwner = true;
    
    GridManager gm = GridManagerFactory.getGridManager();
    //ConcurrentHashMap<String,EncounterLite> chm= gm.getMatchGraph();

    /*
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


    if (request.getParameter("number") != null) {
      String side = "left";
      myShepherd.beginDBTransaction();

      boolean assigned = false;

      try {
        Encounter despotMe = myShepherd.getEncounter(request.getParameter("number"));
        if (despotMe.getIndividualID()==null) {

          if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("true"))) {
            despotMe.removeRightSpots();
            despotMe.removeRightSpotMediaAssets(myShepherd);
            //despotMe.hasRightSpotImage = false;
            despotMe.rightSpotImageFileName = null;
            side = "right";
            despotMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Removed " + side + "-side spot data.</p>");
            //despotMe.setNumRightSpots(0);
          } 
          else if ((request.getParameter("rightSide") != null) && (request.getParameter("rightSide").equals("false"))) {

            despotMe.removeSpots();
            despotMe.removeLeftSpotMediaAssets(myShepherd);
            //despotMe.hasSpotImage = false;
            despotMe.spotImageFileName = null;
            despotMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Removed " + side + "-side spot data.</p>");
            //despotMe.setNumLeftSpots(0);
          }
          gm.addMatchGraphEntry(request.getParameter("number"), new EncounterLite(despotMe));
          myShepherd.commitDBTransaction();
        } 
        else {
          locked = true;
          myShepherd.rollbackDBTransaction();
          assigned = true;
        }

      } 
      catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }
      finally{
        myShepherd.closeDBTransaction();
      }


      out.println(ServletUtilities.getHeader(request));
      if (!locked) {
        
        out.println("<strong>Success:</strong> I have removed spot data for encounter " + request.getParameter("number") + ".");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
        String message = "The spot-matching data for encounter " + request.getParameter("number") + " was removed.";
        ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
      } 
      else {

        if (assigned) {
          out.println("<strong>Failure:</strong> I was NOT able to remove the spot data because the encounter has been assigned to a marked individual. Please try to remove the spot data again after removing the encounter from the individual if appropriate.");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
        } 
        else {

          out.println("<strong>Failure:</strong> I was NOT able to remove the spot data because another user is currently modifying this encounter, or you did not specify a side to remove spot data from. Please try to remove the spot data again in a few seconds.");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
        }
      }


      out.println(ServletUtilities.getFooter(context));
    } 
    else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
      out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
      out.println(ServletUtilities.getFooter(context));
    }

    out.close();
    
  }
}
	
	
