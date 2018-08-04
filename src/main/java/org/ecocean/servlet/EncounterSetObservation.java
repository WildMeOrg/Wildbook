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

public class EncounterSetObservation extends HttpServlet {

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
    
    // What's my class again? What's my class again?
    if (obj.getClass().isInstance(occ)) {
      occ = (Occurrence) obj;
      occ.setDWCDateLastModified(dateString);
    }
    if (obj.getClass().isInstance(enc)) {
      enc = (Encounter) obj;
      enc.setDWCDateLastModified(dateString);
    }  
  }
  
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterSetObservation.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    String redirectURL = redirectURL = "/encounters/encounter.jsp";
    if ((request.getParameter("number") != null) && (request.getParameter("name") != null)) {
      myShepherd.beginDBTransaction();
      String name = request.getParameter("name");
      String id = request.getParameter("number");
      String value = request.getParameter("value");
      System.out.println("Setting Observation... Name : "+name+" ID : "+id+" Value: "+value);
      
      Encounter changeMe = myShepherd.getEncounter(id);
      Observation obs = null;
      
      String newValue = "null";
      String oldValue = "null";

      if (changeMe.getObservationByName(name) != null) {
        oldValue = changeMe.getObservationByName(name).getValue();
      } 


      if ((request.getParameter("value") != null) && (!request.getParameter("value").equals(""))) {
        newValue = request.getParameter("value");
      }
      
      
      try {
        if (newValue.equals("null")) {
          changeMe.removeObservation(name);
          System.out.println("Removing Observation "+name);
          changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Removed observation <em>" + name + "</em>. The Value was <em>" + oldValue + ".</em></p>");
        } else {
          if (changeMe.getObservationByName(name) != null && value != null) {
            Observation existing = changeMe.getObservationByName(name);
            existing.setValue(value);
          } else {
            obs = new Observation(name, newValue, changeMe.getClass().getSimpleName(), changeMe.getCatalogNumber());
            changeMe.addObservation(obs);
            System.out.println("Success setting Observation!");
            //changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Set dynamic property <em>" + name + "</em> from <em>" + oldValue + "</em> to <em>" + newValue + "</em>.</p>");            
          }

        }

      } catch (Exception le) {
        System.out.println("Hit locked exception.");
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();

      }

      if (!locked) {
        setDateLastModified(changeMe);
        myShepherd.commitDBTransaction();
        out.println(ServletUtilities.getHeader(request));

        if (!newValue.equals("")) {
          out.println("<strong>Success:</strong> Encounter Observation " + name + " has been updated from <i>" + oldValue + "</i> to <i>" + newValue + "</i>.");
        } else {
          out.println("<strong>Success:</strong> Encounter Observation " + name + " was removed. The old value was <i>" + oldValue + "</i>.");

        }

        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request)+redirectURL+"?number="+request.getParameter("number")+"\">Return to Encounter "+ request.getParameter("number") + "</a></p>\n");
        List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
        int allStatesSize=allStates.size();
        if(allStatesSize>0){
          for(int i=0;i<allStatesSize;i++){
            String stateName=allStates.get(i);
            out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" Encounters</a></font></p>");   
          }
        }
        out.println(ServletUtilities.getFooter(context));
        String message = "Encounter " + request.getParameter("number") + " Observation " + name + " has been updated from \"" + oldValue + "\" to \"" + newValue + "\".";
        ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
      } else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> Encounter Observation " + name + " was NOT updated because another user is currently modifying this reconrd. Please try to reset the value again in a few seconds.");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) +redirectURL+"?number=" + request.getParameter("number") + "\">Return to Encounter " + request.getParameter("number") + "</a></p>\n");
        List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
        int allStatesSize=allStates.size();
        if(allStatesSize>0){
          for(int i=0;i<allStatesSize;i++){
            String stateName=allStates.get(i);
            out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");   
          }
        }
        out.println(ServletUtilities.getFooter(context));

      }
    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
      out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request)+redirectURL+"?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
      List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
      int allStatesSize=allStates.size();
      if(allStatesSize>0){
        for(int i=0;i<allStatesSize;i++){
          String stateName=allStates.get(i);
          out.println("<p><a href=\""+redirectURL+"?state="+stateName+"\">View all "+stateName+" encounter</a></font></p>");   
        }
      }
      out.println(ServletUtilities.getFooter(context));
    }


    out.close();
    myShepherd.closeDBTransaction();
  }


  
}















