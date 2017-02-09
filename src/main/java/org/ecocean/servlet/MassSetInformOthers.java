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

import javax.jdo.Extent;
import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

//import java.io.FileReader;
//import java.io.BufferedReader;
//import java.io.File;
//import java.util.Vector;

//import com.oreilly.servlet.multipart.*;
//import java.lang.StringBuffer;


public class MassSetInformOthers extends HttpServlet {

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
    myShepherd.setAction("MassSetInformOthers.class");

    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    boolean madeChanges = false;
    int count = 0;

    String informEmail = "", matchString = "";
    matchString = request.getParameter("matchString").toLowerCase();
    informEmail = request.getParameter("informEmail");
    Extent encClass = myShepherd.getPM().getExtent(Encounter.class, true);
    Query query = myShepherd.getPM().newQuery(encClass);

    if ((informEmail != null) && (matchString != null) && (!matchString.equals("")) && (!informEmail.equals(""))) {
      myShepherd.beginDBTransaction();
      try {
        Iterator<Encounter> it = myShepherd.getAllEncounters(query);

        while (it.hasNext()) {
          Encounter tempEnc = it.next();
          String previousInform = "";
          if (tempEnc.getInformOthers() != null) {
            previousInform = tempEnc.getInformOthers();
          }

          if (tempEnc.getSubmitterName().toLowerCase().indexOf(matchString) != -1) {
            tempEnc.setInformOthers(informEmail + "," + previousInform);
            madeChanges = true;
            count++;
          } else if (tempEnc.getSubmitterEmail().toLowerCase().indexOf(matchString) != -1) {
            tempEnc.setInformOthers(informEmail + "," + previousInform);
            madeChanges = true;
            count++;
          } else if (tempEnc.getPhotographerEmail().toLowerCase().indexOf(matchString) != -1) {
            tempEnc.setInformOthers(informEmail + "," + previousInform);
            madeChanges = true;
            count++;
          } else if (tempEnc.getPhotographerName().toLowerCase().indexOf(matchString) != -1) {
            tempEnc.setInformOthers(informEmail + "," + previousInform);
            madeChanges = true;
            count++;
          }


        } //end while
      } catch (Exception le) {
        locked = true;
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }
      if (!madeChanges && !locked) {
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      } else if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
      }
      //success!!!!!!!!
      query.closeAll();
      if (!locked) {

        out.println(ServletUtilities.getHeader(request));
        out.println(("<strong>Success!</strong> I have successfully set the Inform Others field for " + count + " encounters based on the submitter/photographer string: " + matchString + "."));
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
      }
      //failure due to exception
      else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> An encounter is currently being modified by another user. Please wait a few seconds before trying to execute this operation again.");
        out.println(ServletUtilities.getFooter(context));
      }
    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I was unable to set others to inform as requested due to missing parameter values.");
      out.println(ServletUtilities.getFooter(context));
    }
    out.close();
  }

}


