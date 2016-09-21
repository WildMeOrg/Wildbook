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
import java.util.Locale;
import java.util.Properties;


public class UserResetPassword extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }


  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context = ServletUtilities.getContext(request);
    String langCode = ServletUtilities.getLanguageCode(request);
    Locale locale = new Locale(langCode);

    //set up the user directory
    //setup data dir
    String rootWebappPath = getServletContext().getRealPath("/");
    File webappsDir = new File(rootWebappPath).getParentFile();
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    File usersDir=new File(shepherdDataDir.getAbsolutePath()+"/users");
    if(!usersDir.exists()){usersDir.mkdirs();}
    
    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean createThisUser = false;

    String addedRoles="";

    // Prepare for user response.
    String linkHome = "http://" + CommonConfiguration.getURLLocation(request);
    ActionResult actionResult = null;

    //create a new Role from an encounter
    if ((request.getParameter("username") != null) &&  (!request.getParameter("username").trim().equals("")) && (((request.getParameter("password") != null) &&  (!request.getParameter("password").trim().equals("")) && (request.getParameter("password2") != null) &&  (!request.getParameter("password2").trim().equals(""))))) {
      
      String username=request.getParameter("username").trim();
      
      String password="";
      password=request.getParameter("password").trim();
      String password2="";
      password2=request.getParameter("password2").trim();
      
      if((password.equals(password2))){
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        if(myShepherd.getUser(username)!=null){
        
          User myUser=myShepherd.getUser(username);
          
          //OK, now check OTP, time, and username hash for validity
          String OTP=request.getParameter("OTP");
          String time=request.getParameter("time");
        
          String matchingOtpString=myUser.getPassword()+time+myUser.getSalt();
          matchingOtpString=ServletUtilities.hashAndSaltPassword(matchingOtpString, myUser.getSalt());
        
          //log it
          //about to compare
          //System.out.println("OTP is: "+OTP);
          //System.out.println("matchOTP is: "+matchingOtpString);
          
          if(matchingOtpString.equals(OTP)){
            
            //set the new password
            myUser.setPassword(ServletUtilities.hashAndSaltPassword(password2, myUser.getSalt()));
            myShepherd.commitDBTransaction();
            actionResult = new ActionResult(locale, "resetPassword", true, String.format("%s/login.jsp", linkHome));
          }
          else {
            actionResult = new ActionResult(locale, "resetPassword-validationError", false, linkHome);
            myShepherd.rollbackDBTransaction();
          }
        }
        else {
          actionResult = new ActionResult(locale, "resetPassword-badUser", false, linkHome);
          myShepherd.rollbackDBTransaction();
        }
        myShepherd.closeDBTransaction();
      }
      else {
        actionResult = new ActionResult(locale, "resetPassword-nonMatchingPasswords", false, linkHome);
      }
    }
    else {
      String link = String.format("%s/appadmin/users.jsp?context=%s", linkHome, context);
      actionResult = new ActionResult(locale, "resetPassword-insufficientInfo", false, link);
    }

    // Reply to user.
    request.getSession().setAttribute(ActionResult.SESSION_KEY, actionResult);
    getServletConfig().getServletContext().getRequestDispatcher(ActionResult.JSP_PAGE).forward(request, response);

    out.close();
  }
}


