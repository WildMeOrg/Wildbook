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


import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Properties;

import org.ecocean.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


//Set alternateID for this encounter/sighting
public class EncounterSetGPS extends HttpServlet {
  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
    doPost(request, response);
  }
  
  
  private void setDateLastModified(Encounter enc){
    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }
    

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);
    Properties encprops = ShepherdProperties.getProperties("encounter.properties", langCode, context);
    Shepherd myShepherd=new Shepherd(context);
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;
    boolean isOwner=true;

    // Prepare for user response.
    String link = "#";
    try {
      link = CommonConfiguration.getServerURL(request, request.getContextPath()) + String.format("/encounters/encounter.jsp?number=%s", request.getParameter("number"));
    }
    catch (URISyntaxException ex) {
    }
    ActionResult actionResult = new ActionResult(locale, "encounter.editField", true, link).setLinkParams(request.getParameter("number"));

    //reset GPS coordinates

        //System.out.println("Trying to resetGPS...");
        if ((request.getParameter("number")!=null)&&(request.getParameter("lat")!=null)&&(request.getParameter("longitude")!=null)) {
          myShepherd.beginDBTransaction();
          Encounter changeMe=myShepherd.getEncounter(request.getParameter("number"));
          setDateLastModified(changeMe);
          String longitude=request.getParameter("longitude");
          String lat=request.getParameter("lat");
          
          String oldGPS="";
          
          if((changeMe.getDecimalLatitude()==null)||(changeMe.getDecimalLongitude()==null)){
            oldGPS = encprops.getProperty("noValue");
          }
          else{
            oldGPS = String.format("(%s° %s, %s° %s)", changeMe.getDecimalLatitude(), encprops.getProperty("latitude"), changeMe.getDecimalLongitude(), encprops.getProperty("longitude"));
          }
          String newGPS = String.format("(%s° %s, %s° %s)", lat, encprops.getProperty("latitude"), longitude, encprops.getProperty("longitude"));
          
          try{
          
            if (!lat.equals("") && !longitude.equals("")) {
              //changeMe.setGPSLatitude(lat+"&deg; "+gpsLatitudeMinutes+"\' "+gpsLatitudeSeconds+"\" "+latDirection);
            
              
                try {
                  double degrees=(new Double(lat)).doubleValue();
                  
                    changeMe.setDWCDecimalLatitude(degrees);
                    double degrees2=(new Double(longitude)).doubleValue();
                    
                    changeMe.setDWCDecimalLongitude(degrees2);
                  
                }
                catch(Exception e) {
                  System.out.println("EncounterSetGPS: problem setting decimal latitude!");
                  e.printStackTrace();
                }
              
              
            }

            
            //if one is not set, set all to null
            else {

              changeMe.setDecimalLatitude(null);
              changeMe.setDecimalLongitude(null);

              //changeMe.setDWCDecimalLatitude(-9999.0);
              //changeMe.setDWCDecimalLongitude(-9999.0);
              newGPS = encprops.getProperty("noValue");
              
            }
            changeMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Changed encounter GPS coordinates from "+oldGPS+" to "+newGPS+".</p>");
          
          
          
          }catch(Exception le){
            locked=true;
            le.printStackTrace();
            myShepherd.rollbackDBTransaction();
          }
          
          if(!locked){
          
            myShepherd.commitDBTransaction();
            actionResult.setMessageOverrideKey("gps").setMessageParams(request.getParameter("number"), newGPS, oldGPS);

            String message="The recorded GPS location for encounter #"+request.getParameter("number")+" has been updated from "+oldGPS+" to "+newGPS+".";
            ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
          }
          else {
            actionResult.setSucceeded(false).setMessageOverrideKey("locked");
          }

        }
          
        else {
          actionResult.setSucceeded(false);
        }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
    myShepherd.closeDBTransaction();
  }
}
	
