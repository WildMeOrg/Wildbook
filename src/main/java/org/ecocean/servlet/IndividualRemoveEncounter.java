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
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;
import org.ecocean.social.SocialUnit;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;


public class IndividualRemoveEncounter extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  private void setDateLastModified(Encounter enc) {

    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    request.setCharacterEncoding("UTF-8");
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("IndividualRemoveEncounter.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false, isOwner = true;

    //remove encounter from MarkedIndividual

    if ((request.getParameter("number") != null)) {
      myShepherd.beginDBTransaction();
      Encounter enc2remove = myShepherd.getEncounter(request.getParameter("number"));
    

      MarkedIndividual removeFromMe = enc2remove.getIndividual();
      if (removeFromMe!=null) {
        String old_name = enc2remove.getIndividualID();
        boolean wasRemoved = false;
        String name_s = "";
        try {
          
          //while (myShepherd.getUnidentifiableEncountersForMarkedIndividual(old_name).contains(enc2remove)) {
          //  removeFromMe.removeEncounter(enc2remove, context);
          //}
          enc2remove.setIndividual(null);
          

          // Why were we ever nulling the OccurrenceID on IndividualRemoveEncounter????
          //enc2remove.setOccurrenceID(null);

          enc2remove.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Removed from " + old_name + ".</p>");
          setDateLastModified(enc2remove);

          if(removeFromMe!=null){
            name_s = removeFromMe.getName();

            while (removeFromMe.getEncounters().contains(enc2remove)) {
            System.out.println("IndividualRemoveEncounter checkpoint B.1 has enc="+enc2remove+" and ind="+removeFromMe);
              removeFromMe.removeEncounter(enc2remove);
            System.out.println("IndividualRemoveEncounter checkpoint B.2 has enc="+enc2remove+" and ind="+removeFromMe);
            }
            removeFromMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Removed encounter#" + request.getParameter("number") + ".</p>");
            if (removeFromMe.totalEncounters() == 0) {
                
                //remove from names cache
                removeFromMe.removeFromNamesCache();  //so name no longer appears in auto-complete
              
                //check for social unit membership and remove
                List<SocialUnit> units=myShepherd.getAllSocialUnitsForMarkedIndividual(removeFromMe);
                if(units!=null && units.size()>0) {
                  for(SocialUnit unit:units) {
                    boolean worked=unit.removeMember(removeFromMe, myShepherd);
                    if(worked)myShepherd.beginDBTransaction();
                  }
                }
                
                //now delete the individual
                myShepherd.throwAwayMarkedIndividual(removeFromMe);
              wasRemoved = true;
            }
          }


        } catch (java.lang.NullPointerException npe) {
          npe.printStackTrace();
          locked = true;
          myShepherd.rollbackDBTransaction();

        } catch (Exception le) {
          le.printStackTrace();
          locked = true;
          myShepherd.rollbackDBTransaction();

        }


        if (!locked) {
          myShepherd.commitDBTransaction();
          out.println(ServletUtilities.getHeader(request));
          response.setStatus(HttpServletResponse.SC_OK);
          out.println("<strong>Success:</strong> Encounter #" + request.getParameter("number") + " was successfully removed from " + old_name + ".");
          out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
          if (wasRemoved) {
            out.println("Record <strong>" + name_s + "</strong> was also removed because it contained no encounters.");
          }
          out.println(ServletUtilities.getFooter(context));
          String message = "Encounter #" + request.getParameter("number") + " was removed from " + old_name + ".";
          ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
          if (!wasRemoved) {
            ServletUtilities.informInterestedIndividualParties(request, old_name, message,context);
          }
        } 
        else {
          out.println(ServletUtilities.getHeader(request));
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          out.println("<strong>Failure:</strong> Encounter #" + request.getParameter("number") + " was NOT removed from " + old_name + ". Another user is currently modifying this record entry. Please try again in a few seconds.");
          out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
          out.println(ServletUtilities.getFooter(context));

        }

      } 
      else {
        myShepherd.rollbackDBTransaction();
        out.println(ServletUtilities.getHeader(request));
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        out.println("<strong>Error:</strong> You can't remove this encounter from a marked individual because that individual does not exist.");
        out.println(ServletUtilities.getFooter(context));
      }


    } 
    else {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      out.println("I did not receive enough data to remove this encounter from a marked individual.");
    }


    out.close();
    myShepherd.closeDBTransaction();
  }

}
