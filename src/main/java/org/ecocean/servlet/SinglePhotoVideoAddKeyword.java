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

import org.ecocean.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;


public class SinglePhotoVideoAddKeyword extends HttpServlet {


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
    myShepherd.setAction("SinglePhotoVideoAddKeyword.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();

      if ((request.getParameter("photoName") != null) && (request.getParameter("keyword") != null) && (request.getParameter("number")!=null)) {
        boolean locked = false;
        String readableName = "";
        myShepherd.beginDBTransaction();
        
        String[] keywords=request.getParameterValues("keyword");
        int numKeywords=0;
        if(keywords!=null){numKeywords=keywords.length;}
        
        for(int i=0;i<numKeywords;i++){
          try {
            myShepherd.beginDBTransaction();
            Keyword word = myShepherd.getKeyword(keywords[i]);
            SinglePhotoVideo vid=myShepherd.getSinglePhotoVideo(request.getParameter("photoName"));
            vid.addKeyword(word);

            readableName+= (word.getReadableName()+"<br />");
            myShepherd.commitDBTransaction();
          } 
          catch (Exception le) {
            locked = true;
            myShepherd.rollbackDBTransaction();
            le.printStackTrace();
          }
        
        }
        
        if (!locked) {

          //myShepherd.commitDBTransaction();

          //confirm success
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Success:</strong> The following keywords were added to the image:<br /><i>" + readableName + "</i>");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
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
        else {
          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Failure:</strong> I have NOT added one or more of the keywords to the photo.");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + "</a></p>\n");
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
      }

  
      else {

          out.println(ServletUtilities.getHeader(request));
          out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
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



    myShepherd.closeDBTransaction();
    myShepherd = null;
    out.flush();
    out.close();
  }

}