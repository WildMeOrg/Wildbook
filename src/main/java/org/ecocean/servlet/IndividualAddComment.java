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
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;


public class IndividualAddComment extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    request.setCharacterEncoding("UTF-8");
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("IndividualAddComment.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;


    myShepherd.beginDBTransaction();
    if ((request.getParameter("individual") != null) && (request.getParameter("user") != null) && (request.getParameter("comments") != null) && (myShepherd.isMarkedIndividual(request.getParameter("individual")))) {

      MarkedIndividual commentMe = myShepherd.getMarkedIndividual(request.getParameter("individual"));
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
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Success:</strong> I have successfully added your comments.");
          out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">Return to " + request.getParameter("individual") + "</a></p>\n");
          out.println(ServletUtilities.getFooter(context));
          String message = "A new comment has been added to " + request.getParameter("individual") + ". The new comment is: \n" + request.getParameter("comments");
          ServletUtilities.informInterestedIndividualParties(request, request.getParameter("individual"), message,context);
        } else {
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Failure:</strong> I did NOT add your comments. Another user is currently modifying this record. Please try to add your comments again in a few seconds.");
          out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("shark") + "\">Return to individual " + request.getParameter("individual") + "</a></p>\n");
          out.println(ServletUtilities.getFooter(context));

        }

      } else {
        myShepherd.rollbackDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> You are not authorized to modify this database record.");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">Return to " + request.getParameter("individual") + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }
    } else {

      myShepherd.rollbackDBTransaction();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Failure:</strong> I do not have enough information to add your comments.");
      out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">Return to " + request.getParameter("individual") + "</a></p>\n");
      out.println(ServletUtilities.getFooter(context));
    }

    out.close();
    myShepherd.closeDBTransaction();
  }

}