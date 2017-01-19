package org.ecocean.servlet;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Date;
import java.util.HashMap;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.IncorrectCredentialsException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.Subject;

/*
import org.pac4j.core.client.*;
import org.pac4j.core.context.*;
import org.pac4j.oauth.*;
import org.pac4j.oauth.client.*;
import org.pac4j.oauth.credentials.*;
import org.pac4j.oauth.profile.facebook.*;
*/

import org.apache.commons.io.FileUtils;

import org.apache.shiro.web.util.WebUtils;
//import org.ecocean.*;
import org.ecocean.security.SocialAuth;

import org.ecocean.CommonConfiguration;
import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.Util;
import org.pac4j.core.context.J2EContext;
import org.pac4j.core.context.WebContext;
import org.pac4j.oauth.client.FacebookClient;
//import org.pac4j.oauth.client.YahooClient;
import org.pac4j.oauth.credentials.OAuthCredentials;
import org.pac4j.oauth.profile.facebook.FacebookProfile;
//import org.pac4j.oauth.profile.yahoo.YahooProfile;

import java.io.File;
import java.net.URL;
import com.google.gson.Gson;

import org.scribe.builder.*;
import org.scribe.builder.api.*;
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
 public class SocialGrabFiles extends javax.servlet.http.HttpServlet implements javax.servlet.Servlet {
   static final long serialVersionUID = 1L;

    /* (non-Java-doc)
     * @see javax.servlet.http.HttpServlet#HttpServlet()
     */
    public SocialGrabFiles() {
        super();
    }

    /* (non-Java-doc)
     * @see javax.servlet.http.HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        doPost(request, response);
    }

    /* (non-Java-doc)
     * @see javax.servlet.http.HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(true);

        PrintWriter out = response.getWriter();
        String context = "context0";
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("SocialGrabFiles.class");
        //myShepherd.beginDBTransaction();

        String[] fileUrls = request.getParameterValues("fileUrl");
        if (fileUrls != null) {
System.out.println("(A) fileUrls.length = " + fileUrls.length);
for (int i = 0 ; i < fileUrls.length ; i++) {
    System.out.println("- " + fileUrls[i]);
}
        } else {
            Object urls = session.getAttribute("fileUrls");
            if (urls != null) fileUrls = (String[])urls;
        }

        String socialType = "facebook";
if (fileUrls != null) System.out.println("(B) fileUrls.length = " + fileUrls.length);

        response.setHeader("Content-type", "application/json");
        if ((fileUrls == null) || (fileUrls.length < 1)) {
            out.println("{\"error\": \"no fileUrls\"}");
            out.close();
            return;
        } else {
            session.setAttribute("fileUrls", fileUrls);
        }

        //note: for now we can only accept all of one type (e.g. all flickr OR all facebook) -- could expand to group by type and do each
        for (int i = 0 ; i < fileUrls.length ; i++) {
            if (fileUrls[i].indexOf("flickr") > -1) socialType = "flickr";
        }

        String username = null;
User user = null;
/*
        if (request.getUserPrincipal() != null) username = request.getUserPrincipal().getName();
        if (username == null) username = "";
        User user = myShepherd.getUser(username);

        if (user == null) {
            response.sendRedirect("login.jsp");
            return;
        }
*/

        String rootDir = getServletContext().getRealPath("/");
        String baseDir = ServletUtilities.dataDir(context, rootDir) + "/social_files";

        if ("facebook".equals(socialType)) {
            FacebookClient fbclient = null;
            try {
                fbclient = SocialAuth.getFacebookClient(context);
            } catch (Exception ex) {
                System.out.println("SocialAuth.getFacebookClient threw exception " + ex.toString());
            }
            WebContext ctx = new J2EContext(request, response);
            //String callbackUrl = "http://localhost.wildme.org/a/SocialConnect?type=facebook";
            String callbackUrl = request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/SocialGrabFiles";
            fbclient.setCallbackUrl(callbackUrl);

            OAuthCredentials credentials = null;
            try {
                credentials = fbclient.getCredentials(ctx);
            } catch (Exception ex) {
                System.out.println("caught exception on facebook credentials: " + ex.toString());
            }

            if (credentials != null) {
                HashMap rtn = new HashMap();
                rtn.put("fileUrls", fileUrls);
System.out.println("************ hey i think i am authorized???");
                String id = Util.generateUUID();
                session.setAttribute("socialFilesID", id);
                rtn.put("id", id);
                File dir = new File(baseDir, id);
                if (!dir.exists()) dir.mkdirs();
System.out.println(dir);
                HashMap fmap = new HashMap();
                for (int i = 0 ; i < fileUrls.length ; i++) {
                    String fname = "img" + i;
                    int f = fileUrls[i].lastIndexOf("/");
                    if (f > -1) fname = fileUrls[i].substring(f+1);
                    f = fname.indexOf("?");
                    if (f > -1) fname = fname.substring(0, f);
                    fmap.put(fname, fileUrls[i]);
                    File imgf = new File(dir, fname);
System.out.println(fname + ") --- " + fileUrls[i]);
                    FileUtils.copyURLToFile(new URL(fileUrls[i]), imgf);
                }
                rtn.put("files", fmap);
                String json = new Gson().toJson(rtn);
                out.println(json);
                out.close();
                return;
            } else {

System.out.println("*** trying redirect?");
                try {
                    fbclient.redirect(ctx, false, false);
                } catch (Exception ex) {
                    System.out.println("caught exception on facebook processing: " + ex.toString());
                }
                return;
            }


        } else if ("flickr".equals(socialType)) {
            //i *think* flickr image urls are not protected... (TODO confirm this) ... taking the easy way out for now!
            HashMap rtn = new HashMap();
            rtn.put("fileUrls", fileUrls);
            String id = Util.generateUUID();
            session.setAttribute("socialFilesID", id);
            rtn.put("id", id);
            File dir = new File(baseDir, id);
            if (!dir.exists()) dir.mkdirs();
System.out.println(dir);
            HashMap fmap = new HashMap();
            for (int i = 0 ; i < fileUrls.length ; i++) {
                String fname = "img" + i;
                int f = fileUrls[i].lastIndexOf("/");
                if (f > -1) fname = fileUrls[i].substring(f+1);
                f = fname.indexOf("?");
                if (f > -1) fname = fname.substring(0, f);
                fmap.put(fname, fileUrls[i]);
                File imgf = new File(dir, fname);
System.out.println(fname + ") --- " + fileUrls[i]);
                FileUtils.copyURLToFile(new URL(fileUrls[i]), imgf);
            }
            rtn.put("files", fmap);
            String json = new Gson().toJson(rtn);
            out.println(json);
            out.close();
            return;

/*  the HARD way in case we need it:
            String overif = request.getParameter("oauth_verifier");
            String otoken = request.getParameter("oauth_token");

            OAuthService service = null;
            String callbackUrl = "http://" + CommonConfiguration.getURLLocation(request) + "/SocialConnect?type=flickr";
            if (request.getParameter("disconnect") != null) callbackUrl += "&disconnect=1";
            try {
                service = SocialAuth.getFlickrOauth(context, callbackUrl);
            } catch (Exception ex) {
                System.out.println("SocialAuth.getFlickrOauth() threw exception " + ex.toString());
            }

            if (overif == null) {
                Token requestToken = service.getRequestToken();
                session.setAttribute("requestToken", requestToken);
       System.out.println("==============================================requestToken = " + requestToken);
                String authorizationUrl = service.getAuthorizationUrl(requestToken) + "&perms=read";
System.out.println(authorizationUrl);

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
System.out.println("GOT RESPONSE!!!!!!!!!!!!!!!!!!!!!!!!!!");
System.out.println(oResponse.getBody());

                String fusername = null;   //should we use <user id="XXXXXXXXX"> instead?  TODO
                int i = oResponse.getBody().indexOf("<username>");
                if (i > -1) {
                    fusername = oResponse.getBody().substring(i + 10);
                    i = fusername.indexOf("</username>");
                    if (i > -1) fusername = fusername.substring(0, i);
                }
                User fuser = myShepherd.getUserBySocialId("flickr", fusername);
   System.out.println("fusername = " + fusername + " -> user = " + fuser);
if (fuser != null) System.out.println("user = " + user.getUsername() + "; fuser = " + fuser.getUsername());
                if ((fuser != null) && (fuser.getUsername().equals(user.getUsername())) && (request.getParameter("disconnect") != null)) {
                    fuser.unsetSocial("flickr");
                    session.setAttribute("message", "disconnected from flickr");
                    response.sendRedirect("myAccount.jsp");
                    return;

                } else if (fuser != null) {
                    session.setAttribute("error", "looks like this account is already connected to an account");
                    response.sendRedirect("myAccount.jsp");
                    return;

                } else {  //lets do this
                    user.setSocial("flickr", fusername);
                    //myShepherd.getPM().makePersistent(user);
                    session.setAttribute("message", "connected to flickr");
                    response.sendRedirect("myAccount.jsp");
                    return;
                }

            }

*/
        } else {
            session.setAttribute("error", "invalid type");
         //response.sendRedirect("http://" + CommonConfiguration.getURLLocation(request) + "/login.jsp");
         response.sendRedirect("login.jsp");
            return;
        }


        //out.println("ok????");
    }
}
