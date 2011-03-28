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
import org.ecocean.*;


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
    Shepherd myShepherd=new Shepherd();
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked=false;
    boolean isOwner=true;
    
    /**
    if(request.getParameter("number")!=null){
      myShepherd.beginDBTransaction();
      if(myShepherd.isEncounter(request.getParameter("number"))) {
        Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
        String locCode=verifyMyOwner.getLocationCode();
        
        //check if the encounter is assigned
        if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
          isOwner=true;
        }
        
        //if the encounter is assigned to this user, they have permissions for it...or if they're a manager
        else if((request.isUserInRole("admin"))){
          isOwner=true;
        }
        //if they have general location code permissions for the encounter's location code
        else if(request.isUserInRole(locCode)){isOwner=true;}
      }
      myShepherd.rollbackDBTransaction(); 
    }
    */


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
            oldGPS="NO VALUE";
          }
          else{
            oldGPS="("+changeMe.getDecimalLatitude()+" latitude, "+changeMe.getDecimalLongitude()+" longitude)";
            
          }
          String newGPS="("+lat+" latitude, "+longitude+" longitude)";
          
          try{
          
            if (!(lat.equals(""))) {
              //changeMe.setGPSLatitude(lat+"&deg; "+gpsLatitudeMinutes+"\' "+gpsLatitudeSeconds+"\" "+latDirection);
            
              
                try {
                  double degrees=(new Double(lat)).doubleValue();
                  
                    changeMe.setDWCDecimalLatitude(degrees);

                  
                }
                catch(Exception e) {
                  System.out.println("EncounterSetGPS: problem setting decimal latitude!");
                  e.printStackTrace();
                }
              
              
            }
            if (!(longitude.equals(""))) {
              //changeMe.setGPSLongitude(longitude+"&deg; "+gpsLongitudeMinutes+"\' "+gpsLongitudeSeconds+"\" "+longDirection);
            
              try {
                double degrees=(new Double(longitude)).doubleValue();
                  
                  changeMe.setDWCDecimalLongitude(degrees);

                
              }
              catch(Exception e) {
                System.out.println("EncounterSetGPS: problem setting decimal longitude!");
                e.printStackTrace();
              }
            }
            
            //if one is not set, set all to null
            if((longitude.equals(""))||(lat.equals(""))){

              changeMe.setDecimalLatitude(null);
              changeMe.setDecimalLongitude(null);

              //changeMe.setDWCDecimalLatitude(-9999.0);
              //changeMe.setDWCDecimalLongitude(-9999.0);
              newGPS="NO VALUE";
              
            }
            changeMe.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>Changed encounter GPS coordinates from "+oldGPS+" to "+newGPS+".</p>");
          
          
          
          }catch(Exception le){
            locked=true;
            le.printStackTrace();
            myShepherd.rollbackDBTransaction();
          }
          
          if(!locked){
          
            myShepherd.commitDBTransaction();
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Success:</strong> The encounter's recorded GPS location has been updated from "+oldGPS+" to "+newGPS+".");
            out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter <strong>"+request.getParameter("number")+"</strong></a></p>\n");
            out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
                out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
                out.println(ServletUtilities.getFooter());
            String message="The recorded GPS location for encounter #"+request.getParameter("number")+" has been updated from "+oldGPS+" to "+newGPS+".";
            ServletUtilities.informInterestedParties(request, request.getParameter("number"), message);
            }
          else{
            
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Failure:</strong> Encounter GPS location was NOT updated. This encounter is currently being modified by another user. Please try this operation again in a few seconds. If this condition persists, contact the webmaster.");
            out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter <strong>"+request.getParameter("number")+"</strong></a></p>\n");
            out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
                out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
                out.println(ServletUtilities.getFooter());
            
            }
          
        }   
          
        else {
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
          out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter <strong>"+request.getParameter("number")+"</strong></a></p>\n");
          out.println("<p><a href=\"encounters/allEncounters.jsp\">View all encounters</a></font></p>");
              out.println("<p><a href=\"allIndividuals.jsp\">View all individuals</a></font></p>");
              out.println(ServletUtilities.getFooter());  
            
          }
        
        
//end GPS reset
      out.close();
      myShepherd.closeDBTransaction();
      }
}
	
