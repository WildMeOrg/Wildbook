package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;


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
import org.ecocean.security.SocialAuth;



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
 public class SocialConnect extends javax.servlet.http.HttpServlet implements javax.servlet.Servlet {
   static final long serialVersionUID = 1L;
   
    /* (non-Java-doc)
	 * @see javax.servlet.http.HttpServlet#HttpServlet()
	 */
	public SocialConnect() {
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

    PrintWriter out = response.getWriter();
		String context = "context0";
		Shepherd myShepherd = new Shepherd(context);
		//myShepherd.beginDBTransaction();

		String socialType = request.getParameter("type");

		String username = null;
		if (request.getUserPrincipal() != null) username = request.getUserPrincipal().getName();
		if (username == null) username = "";
		User user = myShepherd.getUser(username);

		if (user == null) {
     	response.sendRedirect("login.jsp");
			return;
		}

		if ("facebook".equals(socialType)) {
        FacebookClient fbclient = null;
        try {
            fbclient = SocialAuth.getFacebookClient(context);
        } catch (Exception ex) {
            System.out.println("SocialAuth.getFacebookClient threw exception " + ex.toString());
        }
			WebContext ctx = new J2EContext(request, response);
			//String callbackUrl = "http://localhost.wildme.org/a/SocialConnect?type=facebook";
			String callbackUrl = "http://" + CommonConfiguration.getURLLocation(request) + "/SocialConnect?type=facebook";
			if (request.getParameter("disconnect") != null) callbackUrl += "&disconnect=1";
			fbclient.setCallbackUrl(callbackUrl);

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
if (fbuser != null) System.out.println("user = " + user.getUsername() + "; fbuser = " + fbuser.getUsername());
				if ((fbuser != null) && (fbuser.getUsername().equals(user.getUsername())) && (request.getParameter("disconnect") != null)) {
					fbuser.unsetSocial("facebook");
					//myShepherd.getPM().makePersistent(user);
					out.println("disconnected");
				} else if (fbuser != null) {
					out.println("looks like this account is already connected to a user!");
				} else {  //lets do this
					user.setSocial("facebook", facebookProfile.getId());
					//myShepherd.getPM().makePersistent(user);
					out.println("connected");
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


		out.println("ok????");
	}   	  	    

}
