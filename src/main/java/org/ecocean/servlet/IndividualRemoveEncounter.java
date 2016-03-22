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

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Locale;


public class IndividualRemoveEncounter extends HttpServlet {


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
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false, isOwner = true;
    boolean isAssigned = false;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "individual.editField", true, link);
    actionResult.setLinkOverrideKey("removeEncounter").setLinkParams(request.getParameter("number"));

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

    //remove encounter from MarkedIndividual

    if ((request.getParameter("number") != null)) {
      myShepherd.beginDBTransaction();
      Encounter enc2remove = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(enc2remove);
      if (!enc2remove.isAssignedToMarkedIndividual().equals("Unassigned")) {
        String old_name = enc2remove.isAssignedToMarkedIndividual();
        boolean wasRemoved = false;
        String name_s = "";
        try {
          MarkedIndividual removeFromMe = myShepherd.getMarkedIndividual(old_name);
          name_s = removeFromMe.getName();
          while (removeFromMe.getEncounters().contains(enc2remove)) {
            removeFromMe.removeEncounter(enc2remove, context);
          }
          while (myShepherd.getUnidentifiableEncountersForMarkedIndividual(old_name).contains(enc2remove)) {
            removeFromMe.removeEncounter(enc2remove, context);
          }
          enc2remove.assignToMarkedIndividual("Unassigned");
          enc2remove.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Removed from " + old_name + ".</p>");
          removeFromMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Removed encounter#" + request.getParameter("number") + ".</p>");

          if ((removeFromMe.totalEncounters() + removeFromMe.totalLogEncounters()) == 0) {
            myShepherd.throwAwayMarkedIndividual(removeFromMe);
            wasRemoved = true;
          }

        } catch (java.lang.NullPointerException npe) {
          npe.printStackTrace();
          locked = true;
          myShepherd.rollbackDBTransaction();

        } catch (Exception le) {
          le.printStackTrace();
          locked = true;
          myShepherd.rollbackDBTransaction();

        }


        if (!locked) {
          myShepherd.commitDBTransaction();
          actionResult.setMessageOverrideKey("removeEncounter").setMessageParams(old_name, request.getParameter("number"));
          if (wasRemoved) {
            actionResult.setCommentOverrideKey("removeEncounter-lastEnc").setCommentParams(old_name, request.getParameter("number"));
          }
          String message = "Encounter #" + request.getParameter("number") + " was removed from " + old_name + ".";
          ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
          if (!wasRemoved) {
            ServletUtilities.informInterestedIndividualParties(request, old_name, message,context);
          }
        } else {
          actionResult.setSucceeded(false).setMessageOverrideKey("locked");
        }

      } else {
        myShepherd.rollbackDBTransaction();
        actionResult.setSucceeded(false).setMessageOverrideKey("removeEncounter-unassigned").setLinkOverrideKey("removeEncounter-unassigned");
      }


    } else {
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
    myShepherd.closeDBTransaction();
  }
}
