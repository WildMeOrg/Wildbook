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
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;


public class IndividualSetSex extends HttpServlet {


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
    myShepherd.setAction("IndividualSetSex.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;


    String action = request.getParameter("action");

    if ((request.getParameter("individual") != null) && (request.getParameter("selectSex") != null)) {

      myShepherd.beginDBTransaction();
      MarkedIndividual changeMe = myShepherd.getMarkedIndividual(request.getParameter("individual"));
      String oldSex = "null";
      String newSex = "null";
      try {

        if (changeMe.getSex() != null) {
          oldSex = changeMe.getSex();
        }
        if(request.getParameter("selectSex")!=null){
          changeMe.setSex(request.getParameter("selectSex"));
          newSex=request.getParameter("selectSex");
         }
        else{changeMe.setSex(null);}
        //changeMe.setSex(request.getParameter("selectSex"));
        
        changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Changed sex from " + oldSex + " to " + newSex + ".</p>");
      } catch (Exception le) {
        //System.out.println("Hit locked exception on action: "+action);
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
      }


      if (!locked) {
        myShepherd.commitDBTransaction(action);
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success:</strong> Sex has been updated from " + oldSex + " to " + request.getParameter("selectSex") + ".");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">Return to <strong>" + request.getParameter("individual") + "</strong></a></p>\n");
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
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
        String message = "The sex for " + request.getParameter("individual") + " has been updated from " + oldSex + " to " + request.getParameter("selectSex") + ".";
        ServletUtilities.informInterestedIndividualParties(request, request.getParameter("individual"), message,context);
      } else {

        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> Sex was NOT updated. This record is currently being modified by another user. Please try this operation again in a few seconds.");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">Return to <strong>" + request.getParameter("individual") + "</strong></a></p>\n");
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return to encounter #"+request.getParameter("number")+"</a></p>\n");
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

      }

    } else {
      out.println(ServletUtilities.getHeader(request));
      out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
      out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" + request.getParameter("individual") + "\">Return to <strong>" + request.getParameter("individual") + "</strong></a></p>\n");
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

    }


    out.close();
    myShepherd.closeDBTransaction();
  }

}