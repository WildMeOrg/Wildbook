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
import org.ecocean.Keyword;
import org.ecocean.Shepherd;
import org.ecocean.media.MediaAsset;

import javax.jdo.Query;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

//import javax.jdo.*;
//import com.poet.jdo.*;


//handles operations to sharks. possible operations include, create new, add encounter, remove encounter from
public class KeywordHandler extends HttpServlet {


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
    myShepherd.setAction("KeywordHandler.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();

    //System.out.println(request.getQueryString());
    String action = request.getParameter("action");
    System.out.println("Action is: " + action);
    //System.out.println(request.getCharacterEncoding());
    if (action != null) {

      if ((action.equals("addNewWord")) && (request.getParameter("readableName") != null)) {
        String readableName = request.getParameter("readableName");
        Keyword newword = new Keyword(readableName);
        String newkw = myShepherd.storeNewKeyword(newword);

        //confirm success
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success:</strong> The new image indexing keyword <em>" + readableName + "</em> has been added.");
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to shark <strong>"+request.getParameter("shark")+"</strong></a></p>\n");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/kwAdmin.jsp\">Return to keyword administration page.</a></p>\n");
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
      else if ((action.equals("removeWord")) && (request.getParameter("keyword") != null)) {
        myShepherd.beginDBTransaction();
        Keyword word = myShepherd.getKeyword(request.getParameter("keyword").trim());
        String desc = word.getReadableName();
        
        //need to first delete the keyword from all SinglePhotoVIdeos it is assigned to
        List<MediaAsset> photos=myShepherd.getAllMediAssetsWithKeyword(word);
        int numPhotos=photos.size();
        for(int i=0;i<numPhotos;i++){
        	MediaAsset spv=photos.get(i);
        	spv.removeKeyword(word);
        	myShepherd.commitDBTransaction();
        	myShepherd.beginDBTransaction();
        }
        
        //now we can safely delete the Keyword object
        myShepherd.getPM().deletePersistent(word);
        
        
        myShepherd.commitDBTransaction();

        //confirm success
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success:</strong> The image indexing keyword <i>" + desc + "</i> has been removed.");
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to shark <strong>"+request.getParameter("shark")+"</strong></a></p>\n");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/kwAdmin.jsp\">Return to keyword administration page.</a></p>\n");
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
   

      //edit the text of a keyword
      else if ((action.equals("rename")) && (request.getParameter("keyword") != null) && (request.getParameter("newName") != null)) {
        myShepherd.beginDBTransaction();
        Keyword word = myShepherd.getKeyword(request.getParameter("keyword"));
        String oldName = word.getReadableName();
        word.setReadableName(request.getParameter("newName"));

        myShepherd.commitDBTransaction();

        //confirm success
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Success:</strong> The keyword <i>" + oldName + "</i> has been changed to <i>" + request.getParameter("newName") + "</i>.");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/kwAdmin.jsp\">Return to keyword administration.</a></font></p>");
        out.println(ServletUtilities.getFooter(context));
      } 
      
      else {

        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I don't have enough information to complete your request.");
        //out.println("<p><a href=\"http://"+CommonConfiguration.getURLLocation()+"/individuals.jsp?number="+request.getParameter("shark")+"\">Return to shark <strong>"+request.getParameter("shark")+"</strong></a></p>\n");
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
      out.println("<p>I did not receive enough data to process your command. No action was indicated to me.</p>");
      out.println("<p>Please try again or <a href=\"welcome.jsp\">login here</a>.");
      out.println(ServletUtilities.getFooter(context));
      //npe2.printStackTrace();
    }
    myShepherd.closeDBTransaction();
    myShepherd = null;
    out.flush();
    out.close();
  }

}