package org.ecocean.servlet;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Date;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;

import org.apache.shiro.web.util.WebUtils;
import org.ecocean.*;

/**
 * Uses JSecurity to authenticate a user
 * If user can be authenticated successfully
 * forwards user to /welcome.jsp
 * 
 * If user cannot be authenticated then forwards
 * user to the /login.jsp which will display
 * an error message
  */
 public class LoginUser extends javax.servlet.http.HttpServlet implements javax.servlet.Servlet {

 	static final long serialVersionUID = 1L;
	public LoginUser() {
		super();
	}   	
	
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}  	
	
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		String url = "/login.jsp";
		System.out.println("Starting LoginUser servlet...");
		
		//see /login.jsp for these form fields
		String username = request.getParameter("username").trim();
		String password = request.getParameter("password").trim();
		
		String salt="";
		String context=ServletUtilities.getContext(request);
		Shepherd myShepherd=new Shepherd(context);
		myShepherd.setAction("LoginUser.class");
		myShepherd.beginDBTransaction();
		
		try {
	    User user = myShepherd.getUser(username);
	    salt = user.getSalt();
	    user.setAcceptedUserAgreement(true);
	    myShepherd.commitDBTransaction();
		}
		catch(Exception e){
		  myShepherd.rollbackDBTransaction();
		}
		
    String hashedPassword=ServletUtilities.hashAndSaltPassword(password, salt);
		UsernamePasswordToken token = new UsernamePasswordToken(username, hashedPassword);
		boolean redirectUser=false;
	
		try {
			
			// get the user (aka subject) associated with this request.
			Subject subject = SecurityUtils.getSubject();			
			subject.login(token);
	
		  myShepherd.beginDBTransaction();
		  
		  if(myShepherd.getUser(username)!=null){
		  	User user=myShepherd.getUser(username);
		  	// if we need to show the user agreement
		  	if((CommonConfiguration.getProperty("showUserAgreement",context)!=null)&&(CommonConfiguration.getProperty("userAgreementURL",context)!=null)&&(CommonConfiguration.getProperty("showUserAgreement",context).equals("true"))&&(!user.getAcceptedUserAgreement())){
		      System.out.println("LoginUser: user "+username+" needs to sign User Agreement. Redirecting...");
	        subject.logout();
	        redirectUser=true;
	        url = CommonConfiguration.getProperty("userAgreementURL",context);
	      } else {
		      System.out.println("LoginUser: user "+username+" has signed agreement. Redirecting...");
	      	user.setLastLogin((new Date()).getTime());
	      	url = "/welcome.jsp";}
	      }
		    
		    myShepherd.commitDBTransaction();
				//clear the information stored in the token
				token.clear();
			
		} 
		catch (UnknownAccountException ex) {
			// username not found
			ex.printStackTrace();
			request.setAttribute("error", ex.getMessage() );
		} 
		catch (IncorrectCredentialsException ex) {
			// wrong password
			ex.printStackTrace();
			request.setAttribute("error", ex.getMessage());
		}
		catch (Exception ex) {
			ex.printStackTrace();
			request.setAttribute("error", "Login NOT SUCCESSFUL - cause not known!");
		}
		finally {
		  myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
		}
		
		if (redirectUser) {
		  // forward the request and response to the view
		  RequestDispatcher dispatcher = getServletContext().getRequestDispatcher(url);
		  dispatcher.forward(request, response);   
		  return;
		}

		WebUtils.redirectToSavedRequest(request, response, url);
	}   	  	    
}