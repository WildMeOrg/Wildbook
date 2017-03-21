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
import java.util.List;


public class EncounterSetTapirLinkExposure extends HttpServlet {


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
    myShepherd.setAction("EncounterSetTapirLinkExposure.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false, isOwner = true;
    boolean isAssigned = false;

    /**
     if(request.getParameter("number")!=null){
     myShepherd.beginDBTransaction();
     if(myShepherd.isEncounter(request.getParameter("number"))) {
     Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
     String locCode=verifyMyOwner.getLocationCode();

     //check if the encounter is assigned
     if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
     isAssigned=true;
     }

     //if the encounter is assigned to this user, they have permissions for it...or if they're a manager
     if((request.isUserInRole("admin"))||(isAssigned)){
     isOwner=true;
     }
     //if they have general location code permissions for the encounter's location code
     else if(request.isUserInRole(locCode)){isOwner=true;}
     }
     myShepherd.rollbackDBTransaction();
     }
     */

    String action = request.getParameter("action");
    //System.out.println("Action is: "+action);
    if (action != null) {

      if ((action.equals("tapirLinkExpose"))) {
        if (!(request.getParameter("number") == null)) {
          myShepherd.beginDBTransaction();
          Encounter newenc = myShepherd.getEncounter(request.getParameter("number"));


          try {

            if (newenc.getOKExposeViaTapirLink()) {
              newenc.setOKExposeViaTapirLink(false);
            } else {
              newenc.setOKExposeViaTapirLink(true);
            }

            //newenc.addComments("<p><em>"+request.getRemoteUser()+" on "+(new java.util.Date()).toString()+"</em><br>"+"Approved this encounter for TapirLink exposure.");
          } catch (Exception le) {
            System.out.println("Hit locked exception on action: " + action);
            locked = true;
            le.printStackTrace();
            myShepherd.rollbackDBTransaction();
            //myShepherd.closeDBTransaction();
          }


          if (!locked) {
            myShepherd.commitDBTransaction(action);
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Success:</strong> I have changed encounter " + request.getParameter("number") + " TapirLink exposure status.");
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a>.</p>\n");
            List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
            int allStatesSize=allStates.size();
            if(allStatesSize>0){
              for(int i=0;i<allStatesSize;i++){
                String stateName=allStates.get(i);
                out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");   
              }
            }
            out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");

            out.println(ServletUtilities.getFooter(context));
          } else {
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Failure:</strong> I have NOT changed encounter " + request.getParameter("number") + " TapirLink status. This encounter is currently being modified by another user, or an unknown error occurred.");
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
            int allStatesSize=allStates.size();
            if(allStatesSize>0){
              for(int i=0;i<allStatesSize;i++){
                String stateName=allStates.get(i);
                out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");   
              }
            }
            out.println("<p><a href=\"individualSearchResults.jsp\">View all individual</a></font></p>");
            out.println(ServletUtilities.getFooter(context));


          }

        } else {
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Error:</strong> I don't know which new encounter you're trying to approve.");
          out.println(ServletUtilities.getFooter(context));

        }

      } else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<p>I didn't understand your command, or you are not authorized for this action.</p>");
        out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
        out.println(ServletUtilities.getFooter(context));
      }

    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<p>I did not receive enough data to process your command. No action was indicated to me.</p>");
      out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
      out.println(ServletUtilities.getFooter(context));
    }

    out.close();
    myShepherd.closeDBTransaction();
  }

}