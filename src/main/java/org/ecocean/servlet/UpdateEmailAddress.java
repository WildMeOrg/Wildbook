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

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;


//adds spots to a new encounter
public class UpdateEmailAddress extends HttpServlet {




  public void init(ServletConfig config) throws ServletException {
    super.init(config);

  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {


    doPost(request, response);


  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
   
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    
    String context="context0";
    context=ServletUtilities.getContext(request);
    
    //open a shepherd
    Shepherd myShepherd=new Shepherd(context);
    myShepherd.setAction("UpdateEmailAddress.class");
    

    boolean madeChanges = false;
    boolean ok2proceed = true;
    int numChanges = 0;
    String findEmail = "";
    String replaceEmail = "";
    if (request.getParameter("replaceEmail") != null) {
      replaceEmail = request.getParameter("replaceEmail").trim();
    } else {
      ok2proceed = false;
    }
    if ((request.getParameter("findEmail") != null) && (!request.getParameter("findEmail").equals(""))) {
      findEmail = request.getParameter("findEmail").trim();

    }

    myShepherd.beginDBTransaction();
    try {


      
      Iterator<Encounter> it = myShepherd.getAllEncounters();
      while (it.hasNext()) {

        Encounter tempEnc = it.next();
        if (tempEnc.getSubmitterEmail().indexOf(findEmail) != -1) {
          String newSubmitterEmail = tempEnc.getSubmitterEmail().replaceAll(findEmail, replaceEmail);
          tempEnc.setSubmitterEmail(newSubmitterEmail);
          madeChanges = true;
          numChanges++;
        }
        if (tempEnc.getPhotographerEmail().indexOf(findEmail) != -1) {
          String newPhotographerEmail = tempEnc.getPhotographerEmail().replaceAll(findEmail, replaceEmail);
          tempEnc.setPhotographerEmail(newPhotographerEmail);
          madeChanges = true;
          numChanges++;
        }
        if ((tempEnc.getInformOthers() != null) && (tempEnc.getInformOthers().indexOf(findEmail) != -1)) {
          String newPhotographerEmail = tempEnc.getInformOthers().replaceAll(findEmail, replaceEmail);
          tempEnc.setInformOthers(newPhotographerEmail);
          madeChanges = true;
          numChanges++;
        }
        if (madeChanges) {
          myShepherd.commitDBTransaction();
          myShepherd.beginDBTransaction();
        }
      }

        myShepherd.rollbackDBTransaction();

      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Success!</strong> I successfully replaced " + numChanges + " instance(s) of email address " + findEmail + " with " + replaceEmail + ".");
      out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/admin.jsp\">Return to Administration</a></p>\n");
      out.println(ServletUtilities.getFooter(context));

    } 
    catch (Exception e) {
      myShepherd.rollbackDBTransaction();
      //System.out.println("You really screwed this one up!");
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I encountered an exception trying to replace this email address. The exception is listed below.");
      out.println("<pre>" + e.getMessage() + "</pre>");
      out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/admin.jsp\">Return to Administration</a></p>\n");
      out.println(ServletUtilities.getFooter(context));
      e.printStackTrace();
    }
    finally{
      myShepherd.closeDBTransaction();
    }

    out.close();

  }
}