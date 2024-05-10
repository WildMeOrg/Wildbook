package org.ecocean.servlet;

import org.ecocean.*;

import com.oreilly.servlet.multipart.FilePart;
import com.oreilly.servlet.multipart.MultipartParser;
import com.oreilly.servlet.multipart.ParamPart;
import com.oreilly.servlet.multipart.Part;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Properties;

public class UserSelfUpdate extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";
        // context=ServletUtilities.getContext(request);

        // set up the user directory
        // setup data dir
        String rootWebappPath = getServletContext().getRealPath("/");
        File webappsDir = new File(rootWebappPath).getParentFile();
        File shepherdDataDir = new File(webappsDir,
            CommonConfiguration.getDataDirectoryName(context));

        if (!shepherdDataDir.exists()) { shepherdDataDir.mkdirs(); }
        File usersDir = new File(shepherdDataDir.getAbsolutePath() + "/users");
        if (!usersDir.exists()) { usersDir.mkdirs(); }
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean createThisUser = false;

        // String addedRoles="";
        boolean isEdit = true;
        String username = request.getUserPrincipal().getName();
        String password = "";
        if (request.getParameter("password") != null)
            password = request.getParameter("password").trim();
        String password2 = "";
        if (request.getParameter("password2") != null)
            password2 = request.getParameter("password2").trim();
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("UserSelfUpdate.class");

        User newUser = myShepherd.getUser(username);
        if (newUser != null) {
            myShepherd.beginDBTransaction();
            // set password
            if ((!password.trim().equals("")) && (!password2.trim().equals("")) &&
                (password.equals(password2))) {
                String salt = ServletUtilities.getSalt().toHex();
                String hashedPassword = ServletUtilities.hashAndSaltPassword(password, salt);
                // System.out.println("hashed password: "+hashedPassword+" with salt "+salt + " from source password "+password);
                newUser.setPassword(hashedPassword);
                newUser.setSalt(salt);
            }
            // here handle all of the other User fields (e.g., email address, etc.)
            if ((request.getParameter("fullName") != null) &&
                (!request.getParameter("fullName").trim().equals(""))) {
                newUser.setFullName(request.getParameter("fullName").trim());
            } else if (isEdit && (request.getParameter("fullName") != null) &&
                (request.getParameter("fullName").trim().equals(""))) { newUser.setFullName(null); }
            if (request.getParameter("receiveEmails") != null) {
                newUser.setReceiveEmails(true);
            } else { newUser.setReceiveEmails(false); }
            if ((request.getParameter("emailAddress") != null) &&
                (!request.getParameter("emailAddress").trim().equals(""))) {
                newUser.setEmailAddress(request.getParameter("emailAddress").trim());
            } else if (isEdit && (request.getParameter("emailAddress") != null) &&
                (request.getParameter("emailAddress").trim().equals(""))) {
                newUser.setEmailAddress(null);
            }
            if ((request.getParameter("affiliation") != null) &&
                (!request.getParameter("affiliation").trim().equals(""))) {
                newUser.setAffiliation(request.getParameter("affiliation").trim());
            } else if (isEdit && (request.getParameter("affiliation") != null) &&
                (request.getParameter("affiliation").trim().equals(""))) {
                newUser.setAffiliation(null);
            }
            if ((request.getParameter("userProject") != null) &&
                (!request.getParameter("userProject").trim().equals(""))) {
                newUser.setUserProject(request.getParameter("userProject").trim());
            } else if (isEdit && (request.getParameter("userProject") != null) &&
                (request.getParameter("userProject").trim().equals(""))) {
                newUser.setUserProject(null);
            }
            if ((request.getParameter("userStatement") != null) &&
                (!request.getParameter("userStatement").trim().equals(""))) {
                newUser.setUserStatement(request.getParameter("userStatement").trim());
            } else if (isEdit && (request.getParameter("userStatement") != null) &&
                (request.getParameter("userStatement").trim().equals(""))) {
                newUser.setUserStatement(null);
            }
            if ((request.getParameter("userURL") != null) &&
                (!request.getParameter("userURL").trim().equals(""))) {
                newUser.setUserURL(request.getParameter("userURL").trim());
            } else if (isEdit && (request.getParameter("userURL") != null) &&
                (request.getParameter("userURL").trim().equals(""))) { newUser.setUserURL(null); }
            newUser.RefreshDate();

            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
            myShepherd = null;

            // output success statement
            out.println(ServletUtilities.getHeader(request));

            out.println("<strong>Success:</strong> User '" + username +
                "' was successfully updated.");
            out.println("<p><a href=\"" + request.getScheme() + "://" +
                CommonConfiguration.getURLLocation(request) + "/myAccount.jsp" +
                "\">Return to Your Account" + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
        } else {
            myShepherd.closeDBTransaction();
            // output failure statement
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Failure:</strong> User was NOT found.");
            out.println("<p><a href=\"" + request.getScheme() + "://" +
                CommonConfiguration.getURLLocation(request) + "/myAccount.jsp" +
                "\">Return to our Account" + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
    }
}
