/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2012 Jason Holmberg
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
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;


public class OccurrenceSetGroupBehavior extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  private void setDateLastModified(Occurrence enc) {
    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("OccurrenceSetGroupBehavior.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    


    //--------------------------------
    //edit behavior note

    if ((request.getParameter("behaviorComment") != null)) {
      myShepherd.beginDBTransaction();
      Occurrence changeMe = myShepherd.getOccurrence(request.getParameter("number"));
      setDateLastModified(changeMe);
      String comment = request.getParameter("behaviorComment");
      String oldComment = "None";


      try {

        oldComment = changeMe.getGroupBehavior();
        changeMe.setGroupBehavior(comment);
        changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed group behavior observation from:<br><i>" + oldComment + "</i><br>to:<br><i>" + comment + "</i></p>");
      }
      catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }


      if (!locked) {
        myShepherd.commitDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success:</strong> Occurrence group behavior observation was updated from:<br><i>" + oldComment + "</i><br>to:<br><i>" + comment + "</i>");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" + request.getParameter("number") + "\">Return to occurrence " + request.getParameter("number") + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
        } 
      else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> Occurrence group behavior observation was NOT updated because another user is currently modifying this record. Please press the Back button in your browser and try to edit the comments again in a few seconds.");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" + request.getParameter("number") + "\">Return to occurrence" + request.getParameter("number") + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }
    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
      out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" + request.getParameter("number") + "\">Return to occurrence " + request.getParameter("number") + "</a></p>\n");
      out.println(ServletUtilities.getFooter(context));

    }


    out.close();
    myShepherd.closeDBTransaction();
  }
}


