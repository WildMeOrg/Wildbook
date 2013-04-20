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


public class UserCreate extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    Shepherd myShepherd = new Shepherd();
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String addedRoles="";

    //create a new Role from an encounter

    if ((request.getParameterValues("rolename") != null) && (request.getParameter("username") != null) &&  (!request.getParameter("username").trim().equals("")) && (request.getParameter("password") != null) &&  (!request.getParameter("password").trim().equals(""))) {
      
      String username=request.getParameter("username").trim();
      String password=request.getParameter("password").trim();
      
      myShepherd.beginDBTransaction();
      
      if(myShepherd.getUser(username)==null){

        User newUser=new User(username,password);
        
        //add more details in the future to the User object
        
        myShepherd.getPM().makePersistent(newUser);
        
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();

        String[] roles=request.getParameterValues("rolename");
        int numRoles=roles.length;
        for(int i=0;i<numRoles;i++){

          String thisRole=roles[i].trim();

          Role role=new Role();
          if(myShepherd.getRole(thisRole,username)==null){
            
            role.setRolename(thisRole);
            role.setUsername(username);
            myShepherd.getPM().makePersistent(role);
            addedRoles+=(roles[i]+" ");
            //System.out.println(addedRoles);
            myShepherd.commitDBTransaction();
            myShepherd.beginDBTransaction();
          }
        
          
        }

        myShepherd.commitDBTransaction();    
        myShepherd.closeDBTransaction();
       

            //output success statement
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Success:</strong> User '" + username + "' was successfully created with added roles: " + addedRoles);
            out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/appadmin/users.jsp" + "\">Return to User Administration" + "</a></p>\n");
            out.println(ServletUtilities.getFooter());
            
    }
}


   



    out.close();
    
  }
}


