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

import java.util.concurrent.ThreadPoolExecutor;
import java.util.Vector;

import org.joda.time.LocalDateTime;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class UserResetPasswordSendEmail extends HttpServlet {
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
        String addedRoles = "";
        boolean isEdit = false;
        if (request.getParameter("isEdit") != null) {
            isEdit = true;
            // System.out.println("isEdit is TRUE in UserCreate!");
        }
        // create a new Role from an encounter
        if ((request.getParameter("username") != null) &&
            (!request.getParameter("username").trim().equals(""))) {
            // output success statement
            out.println(ServletUtilities.getHeader(request));

            String username = request.getParameter("username").trim();
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("UserResetPasswordSendEmail.class");

            myShepherd.beginDBTransaction();

            try {
                User myUser = null;
                // let's see if we have a user
                if (myShepherd.getUser(username) != null) {
                    myUser = myShepherd.getUser(username);
                } else if (myShepherd.getUserByEmailAddress(username) != null) {
                    myUser = myShepherd.getUserByEmailAddress(username);
                }
                if ((myUser != null) && (myUser.getEmailAddress() != null)) {
                    // time
                    LocalDateTime dt = new LocalDateTime();
                    long time = dt.toDateTime().getMillis();

                    // OTP string
                    String otpString = myUser.getPassword() + time + myUser.getSalt();
                    otpString = ServletUtilities.hashAndSaltPassword(otpString, myUser.getSalt());

                    // Build the link and send email.
                    final String npLink = String.format(request.getScheme() +
                        "://%s/setNewPassword.jsp?username=%s&time=%s&OTP=%s",
                        CommonConfiguration.getURLLocation(request), myUser.getUsername(), time,
                        otpString);
                    ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
                    Map<String, String> tagMap = new HashMap<String, String>() {{
                        put("@RESET_LINK@", npLink);
                    }};
                    String mailTo = myUser.getEmailAddress();
                    NotificationMailer mailer = new NotificationMailer(context, null, mailTo,
                        "passwordReset", tagMap, true);                                                                // override user.receiveEmails
                    es.execute(mailer);
                    es.shutdown();

                    out.println(
                        "If a user with that username or email address was found, we just sent them an email. Please check your Inbox and follow the link in the email to reset your password. If you don't see an email, don't forget to check your spam folder. Thank you!");
                } else {
                    out.println(
                        "No email address was registered with that username. Please contact a system administrator to reset your password.");
                }
            } catch (Exception e) {
                e.printStackTrace();
                out.println(
                    "An unknown error occurred. Please contact your Wildbook administrator.");
            } finally {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                myShepherd = null;
            }
            out.println("<p><a href=\"" + request.getScheme() + "://" +
                CommonConfiguration.getURLLocation(request) + "\">Return to homepage" +
                "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
        } else {
            // output failure statement
            out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Failure:</strong> Username was NOT successfully reset. I did not have all of the username information I needed.");
            out.println("<p><a href=\"" + request.getScheme() + "://" +
                CommonConfiguration.getURLLocation(request) +
                "/appadmin/users.jsp?context=context0" + "\">Return to login" + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
    }
}
