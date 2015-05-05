package org.ecocean.servlet;

import java.io.IOException;



import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Date;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;

import org.pac4j.core.client.*;
import org.pac4j.core.context.*;
import org.pac4j.oauth.*;
import org.pac4j.oauth.client.*;
import org.pac4j.oauth.credentials.*;
import org.pac4j.oauth.profile.facebook.*;

import org.apache.shiro.web.util.WebUtils;
import org.ecocean.*;



/**
 * Uses JSecurity to authenticate a user
 * If user can be authenticated successfully
 * forwards user to /secure/index.jsp
 * 
 * If user cannot be authenticated then forwards
 * user to the /login.jsp which will display
 * an error message
 *
 */
 public class LoginUserSocial extends javax.servlet.http.HttpServlet implements javax.servlet.Servlet {
   static final long serialVersionUID = 1L;
   
    /* (non-Java-doc)
	 * @see javax.servlet.http.HttpServlet#HttpServlet()
	 */
	public LoginUserSocial() {
		super();
	}   	
	
	/* (non-Java-doc)
	 * @see javax.servlet.http.HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		doPost(request, response);
	}  	
	
	/* (non-Java-doc)
	 * @see javax.servlet.http.HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    HttpSession session = request.getSession(true);

		String url = "/login.jsp";
		
		System.out.println("Starting LoginUserSocial servlet...");
		
		String context = "context0";
		Shepherd myShepherd = new Shepherd(context);
		//myShepherd.beginDBTransaction();

		String socialType = request.getParameter("type");
		String username = "";
		String hashedPassword = "";

		if ("facebook".equals(socialType)) {
			FacebookClient fbclient = new FacebookClient("363791400412043", "719b2c0b21cc5e53bdc9086a283dc589");
			WebContext ctx = new J2EContext(request, response);
			//fbclient.setCallbackUrl("http://localhost.wildme.org/a/auth-test-return.jsp");
			fbclient.setCallbackUrl("http://localhost.wildme.org/a/LoginUserSocial?type=facebook");
			//fbclient.setCallbackUrl("http://localhost.wildme.org/a/LoginUserSocial");
			//fbclient.setCallbackUrl("http://" + CommonConfiguration.getURLLocation(request) + "/LoginUser");

			OAuthCredentials credentials = null;
			try {
				credentials = fbclient.getCredentials(ctx);
			} catch (Exception ex) {
				System.out.println("caught exception on facebook credentials: " + ex.toString());
			}

			if (credentials != null) {
				FacebookProfile facebookProfile = fbclient.getUserProfile(credentials, ctx);
				User fbuser = myShepherd.getUserBySocialId("facebook", facebookProfile.getId());
				System.out.println("getId() = " + facebookProfile.getId() + " -> user = " + fbuser);
				if (fbuser == null) {
					session.setAttribute("error", "don't have a user associated with this Facebook account");
        	//response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/login.jsp");
        	response.sendRedirect("login.jsp");
					return;
				} else {  //we found a matching user!
					username = fbuser.getUsername();
					hashedPassword = fbuser.getPassword();
System.out.println("found a user that matched fb id: " + username);
					//System.out.println("Hello: " + facebookProfile.getDisplayName() + " born the " + facebookProfile.getBirthday());
				}
			} else {

System.out.println("*** trying redirect?");
				try {
					fbclient.redirect(ctx, false, false);
				} catch (Exception ex) {
					System.out.println("caught exception on facebook processing: " + ex.toString());
				}
				return;
			}


		} else {
			session.setAttribute("error", "invalid type");
     	//response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/login.jsp");
     	response.sendRedirect("login.jsp");
			return;
		}


		UsernamePasswordToken token = new UsernamePasswordToken(username, hashedPassword);
		
	
		try {
			Subject subject = SecurityUtils.getSubject();
			subject.login(token);
			token.clear();
		} catch (UnknownAccountException ex) {
			//username provided was not found
			ex.printStackTrace();
			session.setAttribute("error", ex.getMessage() );
		} catch (IncorrectCredentialsException ex) {
			//password provided did not match password found in database
			//for the username provided
			ex.printStackTrace();
			session.setAttribute("error", ex.getMessage());
		} catch (Exception ex) {
			ex.printStackTrace();
			session.setAttribute("error", "Login NOT SUCCESSFUL - cause not known!");
		}


		WebUtils.redirectToSavedRequest(request, response, "welcome.jsp");
	}   	  	    

}
