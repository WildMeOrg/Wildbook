package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

public class OccurrenceAddComment extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    private void setDateLastModified(Occurrence enc) {
        String strOutputDateTime = ServletUtilities.getDate();

        enc.setDWCDateLastModified(strOutputDateTime);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("OccurrenceAddComment.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean isOwner = true;

        myShepherd.beginDBTransaction();
        if ((request.getParameter("number") != null) && (request.getParameter("user") != null) &&
            (request.getParameter("comments") != null) &&
            (myShepherd.isOccurrence(request.getParameter("number")))) {
            Occurrence commentMe = myShepherd.getOccurrence(request.getParameter("number"));
            setDateLastModified(commentMe);
            try {
                commentMe.addComments("<p><em>" + request.getParameter("user") + " on " +
                    (new java.util.Date()).toString() + "</em><br>" +
                    request.getParameter("comments") + "</p>");
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            out.println(ServletUtilities.getHeader(request));
            if (!locked) {
                myShepherd.commitDBTransaction();
                response.setStatus(HttpServletResponse.SC_OK);
                out.println("<strong>Success:</strong> I have successfully added your comments.");
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                    request.getParameter("number") + "\">Return to eoccurrence " +
                    request.getParameter("number") + "</a></p>\n");
                String message = "A new comment has been added to occurrence " +
                    request.getParameter("number") + ". The new comment is: \n" +
                    request.getParameter("comments");
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure:</strong> I did NOT add your comments. Another user is currently modifying the entry. Please try to add your comments again in a few seconds.");
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                    request.getParameter("number") + "\">Return to occurrence" +
                    request.getParameter("number") + "</a></p>\n");
            }
            out.println(ServletUtilities.getFooter(context));
        } else {
            out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Error:</strong> I don't have enough information to add your comments.");
            out.println("<p><a href=\"" + request.getScheme() + "://" +
                CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                request.getParameter("number") + "\">Return to occurrence " +
                request.getParameter("number") + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
        }
        myShepherd.closeDBTransaction();

        out.close();
        myShepherd.closeDBTransaction();
    }
}
