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
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.util.Locale;


public class IndividualAddComment extends HttpServlet {


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
    boolean locked = false;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/individuals.jsp?number=%s", request.getParameter("individual"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "individual.editField", true, link)
            .setParams(request.getParameter("individual"));

    myShepherd.beginDBTransaction();

    if ((request.getParameter("individual") != null) && (request.getParameter("user") != null) && (request.getParameter("comments") != null) && (myShepherd.isMarkedIndividual(request.getParameter("individual")))) {

      MarkedIndividual commentMe = myShepherd.getMarkedIndividual(request.getParameter("individual"));
      actionResult.setParams(request.getParameter("individual"), request.getParameter("comments"), commentMe.getComments());
      if (ServletUtilities.isUserAuthorizedForIndividual(commentMe, request)) {

        try {
          commentMe.addComments("<p><em>" + request.getParameter("user") + " on " + (new java.util.Date()).toString() + "</em><br>" + request.getParameter("comments") + "</p>");
        } catch (Exception le) {
          locked = true;
          le.printStackTrace();
          myShepherd.rollbackDBTransaction();
        }

        if (!locked) {
          myShepherd.commitDBTransaction();
          actionResult.setMessageOverrideKey("addComment");
          String message = "A new comment has been added to " + request.getParameter("individual") + ". The new comment is: \n" + request.getParameter("comments");
          ServletUtilities.informInterestedIndividualParties(request, request.getParameter("individual"), message,context);
        } else {
          actionResult.setSucceeded(false).setMessageOverrideKey("locked");
        }

      } else {
        myShepherd.rollbackDBTransaction();
        actionResult.setSucceeded(false);
      }
    } else {
      myShepherd.rollbackDBTransaction();
      actionResult.setSucceeded(false);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
    myShepherd.closeDBTransaction();
  }
}
