package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;


import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;

import org.pac4j.core.context.*;
import org.pac4j.oauth.client.*;
import org.pac4j.oauth.credentials.*;
import org.pac4j.oauth.profile.facebook.*;

import org.apache.shiro.web.util.WebUtils;
import org.ecocean.*;
import org.ecocean.security.SocialAuth;

import org.scribe.model.*;
import org.scribe.oauth.*;


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
 public class UserCreateSocial extends javax.servlet.http.HttpServlet implements javax.servlet.Servlet {
   static final long serialVersionUID = 1L;
   
    /* (non-Java-doc)
	 * @see javax.servlet.http.HttpServlet#HttpServlet()
	 */
	public UserCreateSocial() {
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

/*
----------
*/
    PrintWriter out = response.getWriter();
		String context = "context0";
		Shepherd myShepherd = new Shepherd(context);
		myShepherd.setAction("UserCreateSocial.class");
		//myShepherd.beginDBTransaction();

		String socialType = request.getParameter("type");

		if (request.getUserPrincipal() != null) {
     	out.println("logout first. you cannot create a user when logged in.");
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
			
			//String callbackUrl = "http://localhost.wildme.org/a/UserCreateSocial?type=facebook";
			String callbackUrl = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/UserCreateSocial?type=facebook";
			fbclient.setCallbackUrl(callbackUrl);

			OAuthCredentials credentials = null;
			try {
				credentials = fbclient.getCredentials(ctx);
			} catch (Exception ex) {
				System.out.println("caught exception on facebook credentials: " + ex.toString());
			}
			FacebookProfile facebookProfile = null;
			if (credentials != null) {
			  try {
			    facebookProfile = fbclient.getUserProfile(credentials, ctx);			    
			  } catch (Exception e) {
			    e.printStackTrace();
			  }
			  
				User fbuser = myShepherd.getUserBySocialId("facebook", facebookProfile.getId());
				System.out.println("getId() = " + facebookProfile.getId() + " -> user = " + fbuser);

				if (fbuser != null) {
            session.setAttribute("error", "There is already a user associated with this Facebook account.");
            WebUtils.redirectToSavedRequest(request, response, "login.jsp");

				} else {
					String username = facebookProfile.getDisplayName().replaceAll(" ", "").toLowerCase();  //TODO handle this better!
System.out.println("username: " + facebookProfile.getUsername());
System.out.println("displayname: " + facebookProfile.getDisplayName());
System.out.println("firstname: " + facebookProfile.getFirstName());
System.out.println("familyname: " + facebookProfile.getFamilyName());
System.out.println("email: " + facebookProfile.getEmail());
//TODO other fields?  --> https://pac4j.github.io/pac4j/apidocs/pac4j/org/pac4j/oauth/profile/facebook/FacebookProfile.html
					fbuser = createUser(username, facebookProfile.getEmail(), facebookProfile.getFirstName() + " " + facebookProfile.getFamilyName(), context);
					fbuser.setSocial("facebook", facebookProfile.getId());
					//myShepherd.getPM().makePersistent(fbuser);
					System.out.println("account " + fbuser.getUsername() + " created via facebook!");
          session.setAttribute("message", "new account created");

            //account created, now lets try to log them in
            UsernamePasswordToken token = new UsernamePasswordToken(fbuser.getUsername(), fbuser.getPassword());
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
			} else {

//System.out.println("*** trying redirect?");
				try {
					fbclient.redirect(ctx);
				} catch (Exception ex) {
					System.out.println("caught exception on facebook processing: " + ex.toString());
				}
				return;
			}


        } else if ("flickr".equals(socialType)) {
            String overif = request.getParameter("oauth_verifier");
            String otoken = request.getParameter("oauth_token");

            OAuthService service = null;
            String callbackUrl = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/UserCreateSocial?type=flickr";
            try {
                service = SocialAuth.getFlickrOauth(context, callbackUrl);
            } catch (Exception ex) {
                System.out.println("SocialAuth.getFlickrOauth() threw exception " + ex.toString());
            }

            if (overif == null) {
                Token requestToken = service.getRequestToken();
                session.setAttribute("requestToken", requestToken);
       //System.out.println("==============================================requestToken = " + requestToken);
                String authorizationUrl = service.getAuthorizationUrl(requestToken) + "&perms=read";
                response.sendRedirect(authorizationUrl);
                return;

            } else {
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
                User fuser = myShepherd.getUserBySocialId("flickr", fusername);
   System.out.println("fusername = " + fusername + " -> user = " + fuser);

				if (fuser != null) {
            session.setAttribute("error", "There is already a user associated with this Flickr account.");
            WebUtils.redirectToSavedRequest(request, response, "login.jsp");

				} else {
            //TODO handle creating new username better?
            fuser = createUser(fusername.toLowerCase(), "", "", context);
            fuser.setSocial("flickr", fusername);
            System.out.println("account " + fuser.getUsername() + " created via flickr!");
            session.setAttribute("message", "new account created");

            //account created, now lets try to log them in
            UsernamePasswordToken token = new UsernamePasswordToken(fuser.getUsername(), fuser.getPassword());
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


		} else {
			out.println("invalid type");
			return;
		}


		out.println("ok????");
	}   	  	    


	private User createUser(String username, String emailAddress, String fullName, String context) {
		String salt = ServletUtilities.getSalt().toHex();
		String hashedPassword = ServletUtilities.hashAndSaltPassword(Util.generateUUID(), salt);  //good luck guessing that password
		Shepherd myShepherd = new Shepherd(context);

    String origUsername = username;
    int count = 0;
    User already = myShepherd.getUser(username);
    while (already != null) {
        count++;
System.out.println("UserCreateSocial.createUser: username " + username + " already exists, appending " + count);
        username = origUsername + count;
        already = myShepherd.getUser(username);
    }

		User user = new User(username, hashedPassword, salt);
    if (emailAddress != null) user.setEmailAddress(emailAddress);
    if (fullName != null) user.setFullName(fullName);
		myShepherd.getPM().makePersistent(user);
		Role role = new Role(username, "fromSocial");
		role.setContext(context);
		myShepherd.getPM().makePersistent(role);
		return user;
	}

}