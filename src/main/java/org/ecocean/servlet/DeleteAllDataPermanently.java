/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2013 Jason Holmberg
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
import org.ecocean.grid.*;
import org.ecocean.genetics.*;
import org.ecocean.social.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.Iterator;

public class DeleteAllDataPermanently extends HttpServlet {


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
    myShepherd.setAction("DeleteAllDataPermanently.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;


    myShepherd.beginDBTransaction();


      try {
        
       
        myShepherd.getPM().newQuery(Adoption.class).deletePersistentAll();
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        
        myShepherd.getPM().newQuery(MarkedIndividual.class).deletePersistentAll();
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        
        myShepherd.getPM().newQuery(Occurrence.class).deletePersistentAll();
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        
        Iterator<Encounter> allEncounters=myShepherd.getAllEncountersNoFilter();
        while(allEncounters.hasNext()){
          Encounter enc=allEncounters.next();
          myShepherd.throwAwayEncounter(enc);
          myShepherd.commitDBTransaction();
          myShepherd.beginDBTransaction();
        }
        
        myShepherd.getPM().newQuery(Keyword.class).deletePersistentAll();
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        
        myShepherd.getPM().newQuery(ScanTask.class).deletePersistentAll();
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        
        myShepherd.getPM().newQuery(TissueSample.class).deletePersistentAll();
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        
        myShepherd.getPM().newQuery(GeneticAnalysis.class).deletePersistentAll();
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        
        myShepherd.getPM().newQuery(SocialUnit.class).deletePersistentAll();
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
        
        myShepherd.getPM().newQuery(Relationship.class).deletePersistentAll();
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();

      } 
      catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
      }

      if (!locked) {
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success!</strong> I have successfully removed all data.");

        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
      } 
      else {

        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure!</strong> I failed to delete all data.");

        out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/admin.jsp\">Return to the Administration page.</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }


    out.close();
  }


}
  
  
