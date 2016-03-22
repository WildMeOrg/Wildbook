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


public class EncounterSetSubmitterPhotographerContactInfo extends HttpServlet {

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
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    boolean isOwner = true;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link)
            .setParams(request.getParameter("number"));

    //reset photographer/submitter contact info
    if ((request.getParameter("number") != null) && (request.getParameter("contact") != null)) {
      myShepherd.beginDBTransaction();
      Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));
      setDateLastModified(changeMe);
      String oldName = "";
      String oldEmail = "";
      String oldAddress = "";
      String oldPhone = "";
      String oldSubmitterOrg="";
      String oldSubmitterProj="";
      String oldContact = "";
      String newContact = "";


      try {

        if (request.getParameter("contact").equals("submitter")) {
          if(changeMe.getSubmitterName()!=null){oldName = changeMe.getSubmitterName();}
          if(changeMe.getSubmitterEmail()!=null){oldEmail = changeMe.getSubmitterEmail();}
          if(changeMe.getSubmitterAddress()!=null){oldAddress = changeMe.getSubmitterAddress();}
          if(changeMe.getSubmitterPhone()!=null){oldPhone = changeMe.getSubmitterPhone();}
          if(changeMe.getSubmitterOrganization()!=null){oldSubmitterOrg = changeMe.getSubmitterOrganization();}
		  if(changeMe.getSubmitterProject()!=null){oldSubmitterProj = changeMe.getSubmitterProject();}



          oldContact = oldName + ", " + oldEmail + ", " + oldAddress + ", " + oldPhone + ", "+oldSubmitterOrg+", "+oldSubmitterProj;

          if(request.getParameter("name")!=null){changeMe.setSubmitterName(request.getParameter("name"));}
          if(request.getParameter("email")!=null){changeMe.setSubmitterEmail(request.getParameter("email"));}
          if(request.getParameter("phone")!=null){changeMe.setSubmitterPhone(request.getParameter("phone"));}
          if(request.getParameter("address")!=null){changeMe.setSubmitterAddress(request.getParameter("address"));}
          if(request.getParameter("submitterOrganization")!=null){changeMe.setSubmitterOrganization(request.getParameter("submitterOrganization"));}
          if(request.getParameter("submitterProject")!=null){changeMe.setSubmitterProject(request.getParameter("submitterProject"));}


          if (request.getParameter("name") != null) {
            newContact += request.getParameter("name") + ", ";
          }
          if (request.getParameter("email") != null) {
            newContact += request.getParameter("email") + ", ";
          }
          if (request.getParameter("address") != null) {
            newContact += request.getParameter("address") + ", ";
          }
          if (request.getParameter("phone") != null) {
            newContact += request.getParameter("phone");
          }

          if (request.getParameter("submitterOrganization") != null) {
		    newContact += request.getParameter("submitterOrganization")+ ", ";
          }
          if (request.getParameter("submitterProject") != null) {
		  	newContact += request.getParameter("submitterProject");
          }


          changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed submitter contact info from<br>" + oldContact + "<br>to<br>" + newContact + ".</p>");
        } else {
          oldName = changeMe.getPhotographerName();
          oldEmail = changeMe.getPhotographerEmail();
          oldAddress = changeMe.getPhotographerAddress();
          oldPhone = changeMe.getPhotographerPhone();
          oldContact = oldName + ", " + oldEmail + ", " + oldAddress + ", " + oldPhone;
          changeMe.setPhotographerName(request.getParameter("name"));
          changeMe.setPhotographerEmail(request.getParameter("email"));
          changeMe.setPhotographerPhone(request.getParameter("phone"));
          changeMe.setPhotographerAddress(request.getParameter("address"));
          if (request.getParameter("name") != null) {
            newContact += request.getParameter("name") + ", ";
          }
          if (request.getParameter("email") != null) {
            newContact += request.getParameter("email") + ", ";
          }
          if (request.getParameter("address") != null) {
            newContact += request.getParameter("address") + ", ";
          }
          if (request.getParameter("phone") != null) {
            newContact += request.getParameter("phone");
          }
          actionResult.addParams(newContact, oldContact);

          changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed photographer contact info from<br>" + oldContact + "<br>to<br>" + newContact + ".</p>");
        }

      } catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }


      if (!locked) {
        myShepherd.commitDBTransaction();
        actionResult.setMessageOverrideKey("contactInfo");

        String message = "The photographer or submitter contact information for encounter #" + request.getParameter("number") + "has been updated from " + oldContact + " to " + newContact + ".";
        ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
      } else {
        actionResult.setSucceeded(false).setMessageOverrideKey("locked");
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
