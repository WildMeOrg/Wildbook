/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2017 Jason Holmberg
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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Observation;
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;

public class OccurrenceSetObservation extends HttpServlet {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    doPost(request, response);
  }
  
  private void setDateLastModified(Object obj) {
    
    Encounter enc = null;
    Occurrence occ = null;
    String dateString = ServletUtilities.getDate();
    
    // From generified code for baseclass in Read lab...
    if (obj.getClass().isInstance(occ)) {
      occ = (Occurrence) obj;
      occ.setDWCDateLastModified(dateString);
    }
  }
  
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("OccurrenceSetObservation.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    String redirectURL = "/occurrence.jsp";
    if ((request.getParameter("number") != null) && (request.getParameter("name") != null)) {
      myShepherd.beginDBTransaction();
      String name = request.getParameter("name").trim();
      String id = request.getParameter("number");
      //String value = request.getParameter("value");
      
      Occurrence occ = myShepherd.getOccurrence(id);
      Observation obs = null;

      String newValue = "null";
      String oldValue = "null";

      if (occ.getObservationByName(name) != null) {
        oldValue = occ.getObservationByName(name).getValue();
      } 

      if ((request.getParameter("value") != null) && (!request.getParameter("value").equals(""))) {
        newValue = request.getParameter("value").trim();
        System.out.println("Updating observation value to: "+newValue);
      }
            
      try {
        if (newValue.equals("null")||newValue.equals("")||newValue==null) {
          occ.removeObservation(name);
          System.out.println("Removing Observation "+name);
        } else {
          if (occ.getObservationByName(name) != null && newValue != null) {
            Observation existing = occ.getObservationByName(name);
            existing.setValue(newValue);
            System.out.println("Resetting value for Observation named "+name+" with new value "+newValue);
            System.out.println("Did the value stick??? Name:"+existing.getName()+" Value: "+existing.getValue());
          } else {
            obs = new Observation(name, newValue, occ.getClass().getSimpleName(), occ.getId());
            occ.addObservation(obs);
            System.out.println("Success setting Observation!");       
          }
        }
        myShepherd.commitDBTransaction();
      } catch (Exception le) {
        System.out.println("Hit locked exception.");
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }

      if (!locked) {
        setDateLastModified(occ);

        out.println(ServletUtilities.getHeader(request));

        if (!newValue.equals("")&&!oldValue.equals("null")) {
          out.println("<strong>Success:</strong> Occurrence Observation " + name + " has been updated to <i>" + newValue + "</i>.");
        } else if (oldValue.equals("null")) {
          out.println("<strong>Success:</strong> Occurrence Observation " + name + " has been created with a value of <i>" + newValue + "</i>.");
        } else {
          out.println("<strong>Success:</strong> Occurrence Observation " + name + " was removed. The old value was <i>" + oldValue + "</i>.");
        }

        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request)+redirectURL+"?number="+request.getParameter("number")+"\">Return to Occurrence "+ request.getParameter("number") + "</a></p>\n");
        List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
        int allStatesSize=allStates.size();
        out.println("<p><a href=\"individualSearchResults.jsp\">View all marked individuals</a></font></p>");
        out.println(ServletUtilities.getFooter(context));
        String message = "Occurrence " + request.getParameter("number") + " Observation " + name + " has been updated from \"" + oldValue + "\" to \"" + newValue + "\".";
        ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
      } else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> Occurrence Observation " + name + " was NOT updated because another user is currently modifying this record. Please try to reset the value again in a few seconds.");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) +redirectURL+"?number=" + request.getParameter("number") + "\">Return to survey " + request.getParameter("number") + "</a></p>\n");
        List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
        int allStatesSize=allStates.size();
        out.println("<p><a href=\"individualSearchResults.jsp\">View all marked individuals</a></font></p>");
        out.println(ServletUtilities.getFooter(context));

      }
    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
      out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request)+redirectURL+"?number=" + request.getParameter("number") + "\">Return to survey #" + request.getParameter("number") + "</a></p>\n");
      List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
      int allStatesSize=allStates.size();
      if(allStatesSize>0){
        for(int i=0;i<allStatesSize;i++){
          String stateName=allStates.get(i);
          out.println("<p><a href=\""+redirectURL+"?state="+stateName+"\">View all "+stateName+" surveys.</a></font></p>");   
        }
      }out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
      out.println(ServletUtilities.getFooter(context));

    }
    out.close();
    myShepherd.closeDBTransaction();
  }
  
}






