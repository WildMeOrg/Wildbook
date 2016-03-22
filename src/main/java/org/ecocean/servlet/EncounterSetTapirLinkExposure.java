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

import org.ecocean.ActionResult;
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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Locale;


public class EncounterSetTapirLinkExposure extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
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
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link)
            .setParams(request.getParameter("number"));

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

    String action = request.getParameter("action");
    //System.out.println("Action is: "+action);
    if (action != null) {

      if ((action.equals("tapirLinkExpose"))) {
        if (!(request.getParameter("number") == null)) {
          myShepherd.beginDBTransaction();
          Encounter newenc = myShepherd.getEncounter(request.getParameter("number"));

          try {

            if (newenc.getOKExposeViaTapirLink()) {
              newenc.setOKExposeViaTapirLink(false);
            } else {
              newenc.setOKExposeViaTapirLink(true);
            }
            actionResult.addParams(newenc.getOKExposeViaTapirLink(), !newenc.getOKExposeViaTapirLink());

            //newenc.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Approved this encounter for TapirLink exposure.");
          } catch (Exception le) {
            System.out.println("Hit locked exception on action: " + action);
            locked = true;
            le.printStackTrace();
            myShepherd.rollbackDBTransaction();
            //myShepherd.closeDBTransaction();
          }


          if (!locked) {
            myShepherd.commitDBTransaction(action);
            actionResult.setMessageOverrideKey("tapirLinkExpose");
          } else {
            actionResult.setSucceeded(false).setMessageOverrideKey("locked");
          }

        } else {
          actionResult.setSucceeded(false);
        }

      } else {
        actionResult.setSucceeded(false);
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
