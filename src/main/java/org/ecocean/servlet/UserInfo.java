package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.*;

public class UserInfo extends HttpServlet {
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
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("UserInfo.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        // boolean locked = false;

        myShepherd.beginDBTransaction();
        try {
            String username = request.getParameter("username").trim();
            User user = myShepherd.getUser(username);
            if (!CommonConfiguration.showUsersToPublic(context)) {
                out.println("<p>invalid</p>");
            } else if (user == null) {
                // out.println(ServletUtilities.getHeader(request));
                out.println("Error: bad username " + username);
                // out.println(ServletUtilities.getFooter(context));
            } else {
                File baseDir = new File(getServletContext().getRealPath("/"));
                String profilePhotoUrl = "/" + baseDir.getName() + "/images/empty_profile.jpg";
                if (user.getUserImage() != null)
                    profilePhotoUrl = "/" + CommonConfiguration.getDataDirectoryName(context) +
                        "/users/" + username + "/" + user.getUserImage().getFilename();
                String displayName = username;
                if (user.getFullName() != null) displayName = user.getFullName();
                String h = "<div id=\"popup-bio-" + username + "\" title=\"" + displayName +
                    "\" class=\"popup-bio\">";
                h += "<div class=\"photo-name-wrapper\"><img src=\"" + profilePhotoUrl +
                    "\" /><div class=\"name\">" + displayName + "</div></div>";
                if (user.getAffiliation() != null)
                    h += "<div class=\"bio-attribute affiliation\"><strong>Affiliation:</strong> " +
                        user.getAffiliation() + "</div>";
                if (user.getUserProject() != null)
                    h +=
                        "<div class=\"bio-attribute project\"><strong>Research Project:</strong> " +
                        user.getUserProject() + "</div>";
                if (user.getUserURL() != null)
                    h += "<div class=\"bio-attribute url\"><strong>Web site:</strong> <a href=\"" +
                        user.getUserURL() + "\">" + user.getUserURL() + "</a></div>";
                if (user.getUserStatement() != null)
                    h += "<div class=\"user-statement\">" + user.getUserStatement() + "</div>";
                h += "</div>";
                out.println(h);
            }
        } catch (Exception e) { e.printStackTrace(); } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
        out.close();
    }
}
