package org.ecocean.servlet;

import java.io.IOException;



//import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

//import java.util.Collection;
//import java.util.Collections;
//import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
//import java.util.function.BiConsumer;
//import java.util.function.Consumer;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;

import org.pac4j.core.config.Config;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.context.session.J2ESessionStore;
import org.pac4j.oauth.client.FacebookClient;
import org.pac4j.oauth.credentials.OAuth10Credentials;
import org.pac4j.oauth.credentials.OAuth20Credentials;
import org.pac4j.oauth.credentials.OAuthCredentials;
import org.pac4j.oauth.profile.facebook.FacebookProfile;
import org.apache.shiro.web.util.WebUtils;
import org.ecocean.*;
import org.ecocean.security.SocialAuth;
import org.geotools.util.logging.LoggerFactory;
import org.scribe.builder.*;
import org.scribe.builder.api.*;
import org.scribe.model.*;
import org.scribe.oauth.*;

import common.Logger;


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


		String socialType = request.getParameter("type");
		String username = "";
		String hashedPassword = "";
		Properties authProps = null;
		if ("facebook".equals(socialType)) {
		  authProps = SocialAuth.authProps(context);
      FacebookClient fbclient = null;       
      try {
          String appID = authProps.getProperty("social.facebook.auth.appid");
          String secret = authProps.getProperty("social.facebook.auth.secret");

          fbclient = new FacebookClient(appID,secret);
      } catch (Exception ex) {
          System.out.println("SocialAuth.getFacebookClient threw exception " + ex.toString());
      }
			J2EContext ctx = new J2EContext(request, response);
			session = renewSession(ctx);
			fbclient.setCallbackUrl(request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/LoginUserSocial?type=facebook");
			System.out.println("Callback URL : "+fbclient.getCallbackUrl());
			fbclient.init(ctx);
			fbclient.setConnectTimeout(10000);
			System.out.println("Whole client : "+fbclient.toString());

			OAuthCredentials credentials = null;
			ctx.setSessionAttribute(socialType, fbclient); 
			try {
				credentials = (OAuthCredentials)fbclient.getCredentials(ctx);
				System.out.println("Credentials :"+credentials);
			} catch (Exception ex) {
				System.out.println("caught exception on facebook credentials: " + ex.toString());
				ex.printStackTrace();
				System.out.println(ctx.toString());
			}

			if (credentials != null) {
			   Shepherd myShepherd = new Shepherd(context);
			    myShepherd.setAction("LoginUserSocial.class1");
			    myShepherd.beginDBTransaction();
			    try{
			      
    				FacebookProfile facebookProfile = fbclient.getUserProfile(credentials, ctx);
    				User fbuser = myShepherd.getUserBySocialId("facebook", facebookProfile.getId());
    				System.out.println("getId() = " + facebookProfile.getId() + " -> user = " + fbuser);
    				if (fbuser == null) {
    					session.setAttribute("error", "don't have a user associated with this Facebook account");
            	//response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/login.jsp");
            	response.sendRedirect("login.jsp");
            	
    					return;
    				} 
    				else {  //we found a matching user!
    					username = fbuser.getUsername();
    					hashedPassword = fbuser.getPassword();
    					System.out.println("found a user that matched fb id: " + username);
    					//System.out.println("Hello: " + facebookProfile.getDisplayName() + " born the " + facebookProfile.getBirthday());
    				}
    			    }
			    catch(Exception e){
			      e.printStackTrace();
			    }
			    finally{
			      myShepherd.rollbackDBTransaction();
			      myShepherd.closeDBTransaction();
          }
				
			} else {

			  System.out.println("*** trying redirect?");
				try {
					fbclient.redirect(ctx);
				} catch (Exception ex) {
					System.out.println("caught exception on facebook processing: " + ex.toString());
				}
				//myShepherd.rollbackDBTransaction();
        //myShepherd.closeDBTransaction();
				return;
			}



    } 
		
		
		else if ("flickr".equals(socialType)) {
        String overif = request.getParameter("oauth_verifier");
        String otoken = request.getParameter("oauth_token");

        OAuthService service = null;
        String callbackUrl = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/LoginUserSocial?type=flickr";
        try {
            service = SocialAuth.getFlickrOauth(context, callbackUrl);
        } catch (Exception ex) {
            System.out.println("SocialAuth.getFlickrOauth() threw exception " + ex.toString());
        }

        if (overif == null) {
            Token requestToken = service.getRequestToken();
            session.setAttribute("requestToken", requestToken);
            String authorizationUrl = service.getAuthorizationUrl(requestToken) + "&perms=read";
            response.sendRedirect(authorizationUrl);
            //myShepherd.rollbackDBTransaction();
            //myShepherd.closeDBTransaction();
            return;

        } 
        else {
            System.out.println("verifier -> " + overif);
            Token requestToken = (Token)session.getAttribute("requestToken");
            Verifier verifier = new Verifier(overif);
            Token accessToken = service.getAccessToken(requestToken, verifier);
            System.out.println("==============================================requestToken = " + requestToken);
            System.out.println("=- - - - - - - - - - - - - -==================accessToken = " + accessToken);
            System.out.println("-----------------------------------------otoken= " + otoken);
            System.out.println("verifier = " + verifier);

            OAuthRequest oRequest = new OAuthRequest(Verb.GET, SocialAuth.FLICKR_URL);
            oRequest.addQuerystringParameter("method", "flickr.test.login");
            service.signRequest(accessToken, oRequest);
            Response oResponse = oRequest.send();
//System.out.println("GOT RESPONSE!!!!!!!!!!!!!!!!!!!!!!!!!!");
//System.out.println(oResponse.getBody());

            String fusername = null;   //should we use <user id="XXXXXXXXX"> instead?  TODO
            int i = oResponse.getBody().indexOf("<username>");
            if (i > -1) {
                fusername = oResponse.getBody().substring(i + 10);
                i = fusername.indexOf("</username>");
                if (i > -1) fusername = fusername.substring(0, i);
            }
            
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("LoginUserSocial.class2");
            myShepherd.beginDBTransaction();
            User fuser = myShepherd.getUserBySocialId("flickr", fusername);
            System.out.println("fusername = " + fusername + " -> user = " + fuser);
            if (fuser == null) {
                session.setAttribute("error", "don't have a user associated with this Flickr account");
                response.sendRedirect("login.jsp");
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                return;
            } 
            else {  //we found a matching user!
                username = fuser.getUsername();
                hashedPassword = fuser.getPassword();
                System.out.println("found a user that matched flickr id: " + username);
              myShepherd.rollbackDBTransaction();
              myShepherd.closeDBTransaction();
            }
        }


		} 
		else {
			session.setAttribute("error", "invalid type");
     	//response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/login.jsp");
     	response.sendRedirect("login.jsp");
     	//myShepherd.rollbackDBTransaction();
      //myShepherd.closeDBTransaction();
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
		finally{
		  //myShepherd.rollbackDBTransaction();
      //myShepherd.closeDBTransaction();
		}


		WebUtils.redirectToSavedRequest(request, response, "welcome.jsp");
	}   	  
	
	
  public HttpSession renewSession(final J2EContext context) {
    final HttpServletRequest request = context.getRequest();
    final HttpSession session = request.getSession();
    
    final Map<String, Object> attributes = new HashMap<>();
    session.setMaxInactiveInterval(10000);
    try {
      
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("BROKE AT RENEW SESSION");
    }
    Enumeration atts = session.getAttributeNames();
    while (atts.hasMoreElements()) {
      attributes.put(atts.nextElement().toString(), session.getAttribute(atts.nextElement().toString()));    
    }
    session.invalidate();
    final HttpSession newSession = request.getSession(true);
    
    for (String k : attributes.keySet()) {
      newSession.setAttribute(k, attributes.get(k)); 
    }
    return newSession;
  }	
	
	
	
	
	

}