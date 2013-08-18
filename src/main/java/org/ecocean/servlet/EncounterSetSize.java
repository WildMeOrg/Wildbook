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
import java.util.ArrayList;


public class EncounterSetSize extends HttpServlet {


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
    Shepherd myShepherd = new Shepherd();
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false, isOwner = true;
    boolean isAssigned = false;
    String newValue="null";

    String action = request.getParameter("action");
    System.out.println("Action is: " + action);
    if (action != null) {


      if (action.equals("setEncounterSize")) {
        if ((request.getParameter("number") != null) && (request.getParameter("lengthUnits") != null) && (request.getParameter("guessList") != null)) {
          myShepherd.beginDBTransaction();
          Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));
          setDateLastModified(changeMe);

          String oldSize = "null";
          String oldUnits = "";
          String oldGuess = "";
          boolean okNumberFormat = true;
          try {
            if(changeMe.getSizeAsDouble()!=null){
            	  oldSize = changeMe.getSizeAsDouble().toString();
             }
            oldUnits = changeMe.getMeasureUnits();
            oldGuess = changeMe.getSizeGuess();
            changeMe.setMeasureUnits(request.getParameter("lengthUnits"));
            changeMe.setSizeGuess(request.getParameter("guessList"));

            
            if((request.getParameter("lengthField")!=null)&&(!request.getParameter("lengthField").equals(""))){
              Double inputSize = new Double(request.getParameter("lengthField"));
              changeMe.setSize(inputSize);
              newValue=request.getParameter("lengthField") + " " + request.getParameter("lengthUnits") + " (" + request.getParameter("guessList") + ")";
            }
            else{
              changeMe.setSize(null);
            }
              
              
              
              changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed encounter size from " + oldSize + " " + oldUnits + " (" + oldGuess + ")" + " to " + newValue+".</p>");
          }
          catch (NumberFormatException nfe) {
            System.out.println("User tried to enter improper number format when editing encounter length.");
            okNumberFormat = false;
            nfe.printStackTrace();
            myShepherd.rollbackDBTransaction();
          } catch (Exception le) {
            System.out.println("Hit locked exception on action: " + action);
            locked = true;
            le.printStackTrace();
            myShepherd.rollbackDBTransaction();
          }


          if (!locked && okNumberFormat) {
            myShepherd.commitDBTransaction(action);
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Success:</strong> Encounter size has been updated from " + oldSize + " " + oldUnits + " (" + oldGuess + ")" + " to "+newValue+".");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            ArrayList<String> allStates=CommonConfiguration.getSequentialPropertyValues("encounterState");
            int allStatesSize=allStates.size();
            if(allStatesSize>0){
              for(int i=0;i<allStatesSize;i++){
                String stateName=allStates.get(i);
                out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");   
              }
            }
            out.println("<p><a href=\"individualSearchResults.jsp\">View all sharks</a></font></p>");
            out.println(ServletUtilities.getFooter());
            String message = "Encounter #" + request.getParameter("number") + " size has been updated from " + oldSize + " " + oldUnits + "(" + oldGuess + ")" + " to " + request.getParameter("lengthField") + " " + request.getParameter("lengthUnits") + "(" + request.getParameter("guessList") + ").";
            ServletUtilities.informInterestedParties(request, request.getParameter("number"),
              message);
          } else if (!okNumberFormat) {
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Failure:</strong> Encounter size was NOT updated because I did not understand the value that you entered. The value must be zero or greater. A value of zero indicates an unknown length.");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            ArrayList<String> allStates=CommonConfiguration.getSequentialPropertyValues("encounterState");
            int allStatesSize=allStates.size();
            if(allStatesSize>0){
              for(int i=0;i<allStatesSize;i++){
                String stateName=allStates.get(i);
                out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");   
              }
            }
            out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
            out.println(ServletUtilities.getFooter());
          } else {
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Failure:</strong> Encounter size was NOT updated because another user is currently modifying the record for this encounter.");
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            ArrayList<String> allStates=CommonConfiguration.getSequentialPropertyValues("encounterState");
            int allStatesSize=allStates.size();
            if(allStatesSize>0){
              for(int i=0;i<allStatesSize;i++){
                String stateName=allStates.get(i);
                out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");   
              }
            }
            out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
            out.println(ServletUtilities.getFooter());


          }
        } else {
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
          ArrayList<String> allStates=CommonConfiguration.getSequentialPropertyValues("encounterState");
          int allStatesSize=allStates.size();
          if(allStatesSize>0){
            for(int i=0;i<allStatesSize;i++){
              String stateName=allStates.get(i);
              out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");   
            }
          }
          out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
          out.println(ServletUtilities.getFooter());

        }

      } else {
        out.println(ServletUtilities.getHeader(request));
        out.println("<p>I didn't understand your command, or you are not authorized for this action.</p>");
        out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
        out.println(ServletUtilities.getFooter());
      }

    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<p>I did not receive enough data to process your command. No action was indicated to me.</p>");
      out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
      out.println(ServletUtilities.getFooter());
    }

    out.close();
    myShepherd.closeDBTransaction();
  }

}