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
import org.ecocean.Util;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.lang.reflect.Method;


public class EncounterSetString extends HttpServlet {


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
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterSetString.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false, isOwner = true;
    boolean isAssigned = false;

    String encID = request.getParameter("number");
    String fieldName = request.getParameter("field");
    String newVal = request.getParameter("newVal");
    String action = "Encounter.setString for field "+fieldName+" and value "+newVal;

    if (!Util.stringExists(encID) || !Util.stringExists(fieldName)) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
      return;
    }

    /**
     if(request.getParameter("number")!=null){
     myShepherd.beginDBTransaction();
     if(myShepherd.isEncounter(request.getParameter("number"))) {
     Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
     String locCode=verifyMyOwner.getLocationCode();

     //check if the encounter is assigned
     if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
     isAssigned=true;
     }

     //if the encounter is assigned to this user, they have permissions for it...or if they're a manager
     if((request.isUserInRole("admin"))||(isAssigned)){
     isOwner=true;
     }
     //if they have general location code permissions for the encounter's location code
     else if(request.isUserInRole(locCode)){isOwner=true;}
     }
     myShepherd.rollbackDBTransaction();
     }
     */


    Encounter changeMe = myShepherd.getEncounter(encID);
    setDateLastModified(changeMe);

    // grab the setter method
    String oldVal = "<not retrieved for logging>";
    Method setter = null;
    Class[] setterArg = new Class[1];
    setterArg[0] = String.class;
    try {
      switch(fieldName) {
        case "groupRole":
          oldVal = changeMe.getGroupRole();
          setter = Encounter.class.getMethod("setGroupRole",setterArg);
          break;
        default:
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          out.println("<strong>Error:</strong> could not find the setter for Encounter.setString for field name "+fieldName+".");
          return;
      }
    } catch (NoSuchMethodException nsme) {
      System.out.println("EncounterSetString NoSuchMethodException: could not find setter method for fieldName: " + fieldName);
      locked = true;
      myShepherd.rollbackDBTransaction();
    }

    // CENTRAL LOGIC HERE
    try {
      setter.invoke(changeMe, newVal);            
      changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed "+fieldName+" from " + oldVal + " to " + newVal + ".</p>");
    } catch (Exception le) {
      System.out.println("EncounterSetString: Hit locked exception on action: " + action);
      locked = true;
      le.printStackTrace();
      myShepherd.rollbackDBTransaction();
    }


    if (!locked) {
      myShepherd.commitDBTransaction(action);
      response.setStatus(HttpServletResponse.SC_OK);
      out.println("<strong>Success:</strong> encounter "+fieldName+" has been updated from " +oldVal+ " to "+newVal+ ".");
      String message = "The "+fieldName+" for encounter #" + request.getParameter("number") + "has been updated from " + oldVal + " to " + newVal + ".";
      ServletUtilities.informInterestedParties(request, request.getParameter("number"),message,context);
    } 


    out.close();
    myShepherd.closeDBTransaction();
  }

}