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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Vector;

public class UserRemoveProfileImage extends HttpServlet {


  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    String context="context0";
    //context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("UserRemoveProfileImage.class");
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    //boolean assigned=false;
    String fileName = "None", username = "None";
    //int positionInList = -1;
    String dcID="";

    //fileName=request.getParameter("filename").replaceAll("%20"," ");
    username = request.getParameter("username");
    
    if(request.getRequestURL().indexOf("MyAccount")!=-1){
      username=request.getUserPrincipal().getName();
    }

    myShepherd.beginDBTransaction();
    if (myShepherd.getUser(username)!=null) {
      User user = myShepherd.getUser(username);
      if(user.getUserImage()!=null){
        SinglePhotoVideo sid=user.getUserImage();
        user.setUserImage(null);
        myShepherd.getPM().deletePersistent(sid);
        
      }
    }
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
    myShepherd=null;
    
     
      out.println(ServletUtilities.getHeader(request));
      
      if(!username.equals("None")){
        out.println("<strong>Success!</strong> I have successfully removed the profile photo.");
      }
      else{
        out.println("<strong>Failure:</strong> No such user exists in the library.");
        
      }
      
      if(request.getRequestURL().indexOf("MyAccount")!=-1){
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/myAccount.jsp\">Return to My Account.</a></p>\n");
        
      }
      else{
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/users.jsp?context=context0&isEdit=true&username=" + username + "#editUser\">Return to User Management</a></p>\n");
      }
      
      out.println(ServletUtilities.getFooter(context));
      //String message = "An image file named " + fileName + " has been removed from encounter#" + encounterNumber + ".";
      //ServletUtilities.informInterestedParties(request, encounterNumber, message);
        

    out.close();
  }


}
  
  
