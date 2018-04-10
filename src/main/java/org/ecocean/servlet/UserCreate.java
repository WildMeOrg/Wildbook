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

import org.apache.commons.lang3.StringEscapeUtils;
import org.ecocean.*;

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import java.util.Properties;


public class UserCreate extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    request.setCharacterEncoding("UTF-8");
    String context="context0";
    //context=ServletUtilities.getContext(request);
    
    //set up the user directory
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File usersDir=new File(shepherdDataDir.getAbsolutePath()+"/users");
    if(!usersDir.exists()){usersDir.mkdirs();}
    
    //set up for response
    response.setContentType("text/html; charset=UTF-8");
    PrintWriter out = response.getWriter();
    boolean createThisUser = false;

    String addedRoles="";
    boolean isEdit=false;
    if(request.getParameter("isEdit")!=null){
      isEdit=true;
      //System.out.println("isEdit is TRUE in UserCreate!");
    }

    //create a new Role from an encounter

    if ((request.getParameter("username") != null) &&  (!request.getParameter("username").trim().equals("")) && (((request.getParameter("password") != null) &&  (!request.getParameter("password").trim().equals("")) && (request.getParameter("password2") != null) &&  (!request.getParameter("password2").trim().equals(""))) || (request.getParameter("isEdit")!=null))) {
      
      String username=request.getParameter("username").trim();
      
      String password="";
      if(!isEdit)password=request.getParameter("password").trim();
      String password2="";
      if(!isEdit)password2=request.getParameter("password2").trim();
      
      if((password.equals(password2))||(isEdit)){
        
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("UserCreate.class");
        
        User newUser=new User();
      
        myShepherd.beginDBTransaction();
      
        if(myShepherd.getUser(username)==null){
          
          
          String salt=ServletUtilities.getSalt().toHex();
          String hashedPassword=ServletUtilities.hashAndSaltPassword(password, salt);
          //System.out.println("hashed password: "+hashedPassword+" with salt "+salt + " from source password "+password);
          newUser=new User(username,hashedPassword,salt);
          myShepherd.getPM().makePersistent(newUser);
          createThisUser=true;
        }
        else{
          newUser=myShepherd.getUser(username);
        }
        
        //here handle all of the other User fields (e.g., email address, etc.)
        if((request.getParameter("fullName")!=null)&&(!request.getParameter("fullName").trim().equals(""))){
          newUser.setFullName(request.getParameter("fullName").trim());
        }
        else if(isEdit&&(request.getParameter("fullName")!=null)&&(request.getParameter("fullName").trim().equals(""))){newUser.setFullName(null);}
        
        if(request.getParameter("receiveEmails")!=null){
          newUser.setReceiveEmails(true);
        }
        else{newUser.setReceiveEmails(false);}
        
        if((request.getParameter("emailAddress")!=null)&&(!request.getParameter("emailAddress").trim().equals(""))){
          newUser.setEmailAddress(request.getParameter("emailAddress").trim());
        }
        else if(isEdit&&(request.getParameter("emailAddress")!=null)&&(request.getParameter("emailAddress").trim().equals(""))){newUser.setEmailAddress(null);}
        
        if((request.getParameter("affiliation")!=null)&&(!request.getParameter("affiliation").trim().equals(""))){
          newUser.setAffiliation(request.getParameter("affiliation").trim());
        }
        else if(isEdit&&(request.getParameter("affiliation")!=null)&&(request.getParameter("affiliation").trim().equals(""))){newUser.setAffiliation(null);}
        
        if((request.getParameter("userProject")!=null)&&(!request.getParameter("userProject").trim().equals(""))){
          newUser.setUserProject(request.getParameter("userProject").trim());
        }
        else if(isEdit&&(request.getParameter("userProject")!=null)&&(request.getParameter("userProject").trim().equals(""))){newUser.setUserProject(null);}
        
        if((request.getParameter("userStatement")!=null)&&(!request.getParameter("userStatement").trim().equals(""))){
          newUser.setUserStatement(request.getParameter("userStatement").trim());
        }
        else if(isEdit&&(request.getParameter("userStatement")!=null)&&(request.getParameter("userStatement").trim().equals(""))){newUser.setUserStatement(null);}
        
        if((request.getParameter("userURL")!=null)&&(!request.getParameter("userURL").trim().equals(""))){
          newUser.setUserURL(request.getParameter("userURL").trim());
        }
        else if(isEdit&&(request.getParameter("userURL")!=null)&&(request.getParameter("userURL").trim().equals(""))){newUser.setUserURL(null);}
        
        newUser.RefreshDate();
        
        
        
        //now handle roles
        
        //if this is not a new user, we need to blow away all old roles
        List<Role> preexistingRoles=new ArrayList<Role>();
        if(!createThisUser){
          //get existing roles for this existing user
          preexistingRoles=myShepherd.getAllRolesForUser(username);
          myShepherd.getPM().deletePersistentAll(preexistingRoles);
        }
        
        
        //start role processing
        
        List<String> contexts=ContextConfiguration.getContextNames();
        int numContexts=contexts.size();
        //System.out.println("numContexts is: "+numContexts);
        for(int d=0;d<numContexts;d++){
        
          String[] roles=request.getParameterValues("context"+d+"rolename");
          if(roles!=null){
          int numRoles=roles.length;
          //System.out.println("numRoles in context"+d+" is: "+numRoles);
          for(int i=0;i<numRoles;i++){

            String thisRole=roles[i].trim();
             if(!thisRole.trim().equals("")){
            Role role=new Role();
            if(myShepherd.getRole(thisRole,username,("context"+d))==null){
            
              role.setRolename(thisRole);
              role.setUsername(username);
              role.setContext("context"+d);
              myShepherd.getPM().makePersistent(role);
              addedRoles+=("SEPARATORSTART"+ContextConfiguration.getNameForContext("context"+d)+":"+roles[i]+"SEPARATOREND");
              //System.out.println(addedRoles);
              myShepherd.commitDBTransaction();
              myShepherd.beginDBTransaction();
              //System.out.println("Creating role: context"+d+thisRole);
            }
          }
          }
          }
        }
        //end role processing
        
        

        myShepherd.commitDBTransaction();    
        myShepherd.closeDBTransaction();
        myShepherd=null;
       

            //output success statement
            out.println(ServletUtilities.getHeader(request));
            if(createThisUser){
              out.println("<strong>Success:</strong> User '" + StringEscapeUtils.escapeHtml4(username) + "' was successfully created with added roles: <ul>" + addedRoles.replaceAll("SEPARATORSTART", "<li>").replaceAll("SEPARATOREND", "</li>")+"</ul>");
            }
            else{
              out.println("<strong>Success:</strong> User '" + StringEscapeUtils.escapeHtml4(username) + "' was successfully updated and has assigned roles: <ul>" + addedRoles.replaceAll("SEPARATORSTART", "<li>").replaceAll("SEPARATOREND", "</li>")+"</ul>");
              
            }
            out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/users.jsp?context=context0" + "\">Return to User Administration" + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
            
    }
    else{
        //output failure statement
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Failure:</strong> User was NOT successfully created. Your passwords did not match.");
        out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/users.jsp?context=context0" + "\">Return to User Administration" + "</a></p>\n");
        out.println(ServletUtilities.getFooter(context));
        
      }
      
      
}
else{
  //output failure statement
  out.println(ServletUtilities.getHeader(request));
  out.println("<strong>Failure:</strong> User was NOT successfully created. I did not have all of the username and password information I needed.");
  out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/appadmin/users.jsp?context=context0" + "\">Return to User Administration" + "</a></p>\n");
  out.println(ServletUtilities.getFooter(context));
  
}


   



    out.close();
    
  }
}


