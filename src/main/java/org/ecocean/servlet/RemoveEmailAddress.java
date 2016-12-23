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

import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.StringTokenizer;


//Set alternateID for this encounter/sighting
public class RemoveEmailAddress extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("RemoveEmailAddress.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    if (request.getParameter("hashedEmail") != null) {

      //String oldCode="";
      myShepherd.beginDBTransaction();
      List<Encounter> al = myShepherd.getEncountersWithHashedEmailAddress(request.getParameter("hashedEmail"));
      int numMatchingEncounters = al.size();
      String removeMe = "";
      int numInstances = 0;
      for (int i = 0; i < numMatchingEncounters; i++) {
        boolean haveMadeChange = false;
        Encounter changeMe = al.get(i);
        try {

          //remove submitter
          String thisEncEmailAddresses = changeMe.getSubmitterEmail();
          StringTokenizer st = new StringTokenizer(thisEncEmailAddresses, ",");
          int numEmails = st.countTokens();
          for (int j = 0; j < numEmails; j++) {
            String address = st.nextToken().trim();
            if (!address.equals("")) {
              if (request.getParameter("hashedEmail").equals(Encounter.getHashOfEmailString(address))) {
                changeMe.setSubmitterEmail(changeMe.getSubmitterEmail().replaceAll(address, ""));
                removeMe = address;
                numInstances++;
                haveMadeChange = true;
              }
            }
          }

          //remove photographer
          thisEncEmailAddresses = changeMe.getPhotographerEmail();
          st = new StringTokenizer(thisEncEmailAddresses, ",");
          numEmails = st.countTokens();
          for (int j = 0; j < numEmails; j++) {
            String address = st.nextToken().trim();
            if (!address.equals("")) {
              if (request.getParameter("hashedEmail").equals(Encounter.getHashOfEmailString(address))) {
                changeMe.setPhotographerEmail(changeMe.getPhotographerEmail().replaceAll(address, ""));
                removeMe = address;
                numInstances++;
                haveMadeChange = true;
              }
            }
          }

          //remove informOthers
          thisEncEmailAddresses = changeMe.getInformOthers();
          st = new StringTokenizer(thisEncEmailAddresses, ",");
          numEmails = st.countTokens();
          for (int j = 0; j < numEmails; j++) {
            String address = st.nextToken().trim();
            if (!address.equals("")) {
              if (request.getParameter("hashedEmail").equals(Encounter.getHashOfEmailString(address))) {
                changeMe.setInformOthers(changeMe.getInformOthers().replaceAll(address, ""));
                removeMe = address;
                numInstances++;
                haveMadeChange = true;
              }
            }
          }

          if (haveMadeChange) {
            changeMe.addComments("<p>Removed email address " + removeMe + " upon user request.</p>");
          }

        } catch (Exception le) {
          locked = true;
          le.printStackTrace();
          myShepherd.rollbackDBTransaction();
        }
      }
      if ((!locked) && (!removeMe.equals("")) && (numInstances > 0)) {

        myShepherd.commitDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success:</strong> I removed " + numInstances + " instances of your email in our database.");
        out.println(ServletUtilities.getFooter(context));
        //String message="Encounter #"+request.getParameter("number")+" location code has been updated from "+oldCode+" to "+request.getParameter("code")+".";
        //ServletUtilities.informInterestedParties(request.getParameter("number"), message);
      } else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> I could not find your email address in the database.");
        out.println(ServletUtilities.getFooter(context));

      }
    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
      out.println(ServletUtilities.getFooter(context));

    }


    out.close();
    myShepherd.closeDBTransaction();
  }
}
  
  
