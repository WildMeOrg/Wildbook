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


import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.datanucleus.api.jdo.JDOPersistenceManager;
import org.datanucleus.api.rest.RESTUtils;


public class EncounterAddUser extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  private void setDateLastModified(Encounter enc) {
    String strOutputDateTime = ServletUtilities.getDate();
    enc.setDWCDateLastModified(strOutputDateTime);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("EncounterRemoveUser.class");
    //set up for response
    response.setContentType("application/json");
    PrintWriter out = response.getWriter();
    boolean locked = false;
    boolean isOwner = true;

 
    myShepherd.beginDBTransaction();
    if ((request.getParameter("encounter") != null)&&(request.getParameter("type") != null) &&(request.getParameter("email") != null)&&(request.getParameter("email").indexOf("@")!=-1)&&(myShepherd.isEncounter(request.getParameter("encounter")))&&(ServletUtilities.isUserAuthorizedForEncounter(myShepherd.getEncounter(request.getParameter("encounter")), request))) {
      
      Encounter changeMe = myShepherd.getEncounter(request.getParameter("encounter"));
      setDateLastModified(changeMe);
      String type=request.getParameter("type").trim();



      try {
        String email=request.getParameter("email").trim();
        User user=myShepherd.getUserByEmailAddress(email);
        
        if(user==null){
          user=new User(email,Util.generateUUID());
          myShepherd.getPM().makePersistent(user);
          myShepherd.commitDBTransaction();
          myShepherd.beginDBTransaction();
        }

        if(type.equals("submitter")){
          List<User> users=changeMe.getSubmitters();
          if(!users.contains(user))users.add(user);
          changeMe.setSubmitters(users);
          changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Added user "+user.getUUID()+" of type " + type + ".</p>");
          //return the User object added
          String responseJSON=RESTUtils.getJSONObjectFromPOJO(user, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext()).toString();
          myShepherd.commitDBTransaction();
          response.setStatus(HttpServletResponse.SC_OK);
          out.println(responseJSON);
          
        }
        else if(type.equals("photographer")){
          List<User> users=changeMe.getPhotographers();
          if(!users.contains(user))users.add(user);
          changeMe.setPhotographers(users);
          changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Added user "+user.getUUID()+" of type " + type + ".</p>");
          String responseJSON=RESTUtils.getJSONObjectFromPOJO(user, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext()).toString();
          
          myShepherd.commitDBTransaction();
          response.setStatus(HttpServletResponse.SC_OK);
          out.println(responseJSON);
        }
        else if(type.equals("informOther")){
          List<User> users=changeMe.getInformOthers();
          if(!users.contains(user))users.add(user);
          changeMe.setInformOthers(users);
          changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>Added user "+user.getUUID()+" of type " + type + ".</p>");
          String responseJSON=RESTUtils.getJSONObjectFromPOJO(user, ((JDOPersistenceManager)myShepherd.getPM()).getExecutionContext()).toString();
          
          myShepherd.commitDBTransaction();
          response.setStatus(HttpServletResponse.SC_OK);
          out.println(responseJSON);
        }
        else{
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        
        
      } 
      catch (Exception le) {
        locked = true;
        le.printStackTrace();
        myShepherd.rollbackDBTransaction();
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }


      if(locked) {
        
        out.println("<strong>Failure:</strong> Encounter was NOT updated because another user is currently modifying this reconrd. Please try to reset the scarring again in a few seconds.");
        
      }
    } 
    else {

      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      myShepherd.rollbackDBTransaction();
    }


    out.close();
    myShepherd.closeDBTransaction();
  }
}
  
  
