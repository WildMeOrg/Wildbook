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
import org.ecocean.FoundationalPropertiesBase;
import org.ecocean.Observation;
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;
import org.ecocean.Survey;

public class SurveySetObservation extends HttpServlet {

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
  
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    String context = ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("SurveySetObservation.class");
    System.out.println("Reached Observation setting servlet...");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    String redirectURL = "/survey.jsp";
    String typeLower = null;
    String type = null;
    if ((request.getParameter("number") != null) && (request.getParameter("name") != null)) {
      myShepherd.beginDBTransaction();
      type = request.getParameter("type");
      String name = request.getParameter("name");
      String id = request.getParameter("number");
      String value = request.getParameter("value");
      System.out.println("Setting Observation... Name : "+name+" ID : "+id+" Value : "+value);
      
      Survey sv = null;
      try {
        myShepherd.getSurvey(id);
      } catch (Exception e) {
        System.out.println("NPE trying to retrieve survey from shepherd.");
        e.printStackTrace();
      }
      Observation obs = null;
      
      String newValue = "null";
      String oldValue = "null";

      if (sv.getObservationByName(name) != null) {
        oldValue = sv.getObservationByName(name).getValue();
      } 

      if ((request.getParameter("value") != null) && (!request.getParameter("value").equals(""))) {
        newValue = request.getParameter("value");
      }
      
      try {
        if (newValue.equals("null")) {
          sv.removeObservation(name);
          System.out.println("Servlet trying to remove Observation "+name);
        } else {
          if (sv.getObservationByName(name) != null && value != null) {
            Observation existing = sv.getObservationByName(name);
            existing.setValue(value);
          } else {
            obs = new Observation(name, newValue, sv.getClass().getSimpleName(), sv.getID());
            sv.addObservation(obs);
            System.out.println("Success setting Observation!");         
          }
        }
      } catch (Exception le) {
        System.out.println("Hit locked exception.");
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();

      }

      if (!locked) {
        sv.setDWCDateLastModified();
        myShepherd.commitDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        if (!newValue.equals("")) {
          out.println("<strong>Success:</strong> "+type+" Observation " + name + " has been updated from <i>" + oldValue + "</i> to <i>" + newValue + "</i>.");
        } else {
          out.println("<strong>Success:</strong> "+type+" Observation " + name + " was removed. The old value was <i>" + oldValue + "</i>.");
        }
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request)+redirectURL+"?surveyID="+request.getParameter("number")+"\">Return to "+type+" "+ request.getParameter("number") + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
        String message = type+" " + request.getParameter("number") + " Observation " + name + " has been updated from \"" + oldValue + "\" to \"" + newValue + "\".";
        ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
      } else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> "+type+" Observation " + name + " was NOT updated because another user is currently modifying this reconrd. Please try to reset the value again in a few seconds.");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) +redirectURL+"?surveyID=" + request.getParameter("number") + "\">Return to "+typeLower+" " + request.getParameter("number") + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));

      }
    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
      out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request)+redirectURL+"?surveyID=" + request.getParameter("number") + "\">Return to "+typeLower+" #" + request.getParameter("number") + "</a></p>\n");
      out.println(ServletUtilities.getFooter(context));
    }
    out.close();
    myShepherd.closeDBTransaction();
  }
  
}



