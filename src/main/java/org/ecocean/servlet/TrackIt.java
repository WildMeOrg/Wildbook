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
import java.util.Map;
import java.util.Vector;

//import javax.jdo.*;
//import com.poet.jdo.*;


public class TrackIt extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);

  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    String context="context0";
    context=ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    
    Shepherd myShepherd = new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String email = "None", encounterNumber = "None", shark = "None";

    email = request.getParameter("email");
    encounterNumber = request.getParameter("number");
    Encounter enc = null;
    myShepherd.beginDBTransaction();
    if ((request.getParameter("number") != null) && (myShepherd.isEncounter(request.getParameter("number"))) && (email != null) && (!email.equals("")) && (email.indexOf("@") != -1)) {
      enc = myShepherd.getEncounter(encounterNumber);


      //int positionInList=0;
      try {

        Vector interested = enc.getInterestedResearchers();
        interested.add(email);

      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully added the tracking of encounter#" + encounterNumber + " for e-mail address " + email + ".");

        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

        String message = "This is a confirmation that e-mail tracking of data changes to encounter " + encounterNumber + " has now started. You should receive e-mail updates any time changes to this encounter are made.";
        Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, enc);
        NotificationMailer mailer = new NotificationMailer(context, null, email, "encounterTrackingStarted", message);
//        ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
//        es.execute(mailer);
//        es.shutdown();
      } else {

        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> This encounter is currently being modified by another user, or the database is locked. Please wait a few seconds before trying to add this e-mail address for tracking again.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + encounterNumber + "\">Return to encounter " + encounterNumber + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));


      }
    } else if ((request.getParameter("individual") != null) && (myShepherd.isMarkedIndividual(request.getParameter("individual"))) && (email != null) && (!email.equals("")) && (email.indexOf("@") != -1)) {

      shark = request.getParameter("individual");
      MarkedIndividual sharkie = myShepherd.getMarkedIndividual(shark);

      //myShepherd.beginDBTransaction();
      //int positionInList=0;
      try {

        Vector interested = sharkie.getInterestedResearchers();
        interested.add(email);

      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully added the tracking of " + shark + " for e-mail address " + email + ".");

        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + shark + "\">Return to " + shark + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
        Vector e_images = new Vector();
        String message = "This is a confirmation that e-mail tracking of data changes to " + shark + " has now started. You should receive e-mail updates any time changes to this record are made.";
        Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, enc);
        NotificationMailer mailer = new NotificationMailer(context, null, email, "encounterTrackingStarted", message);
//        ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
//        es.execute(mailer);
//        es.shutdown();
      } else {

        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> This record is currently being modified by another user, or the database is locked. Please wait a few seconds before trying to add this e-mail address for tracking again.");
        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + shark + "\">Return to " + shark + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));


      }


    } else {
      myShepherd.rollbackDBTransaction();
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to add your e-mail address to the tracking list. I cannot find the record that you indicated in the database, or your e-mail address is invalid.");
      out.println(ServletUtilities.getFooter(context));

    }
    out.close();
    myShepherd.closeDBTransaction();
  }


}
	
	
