package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Occurrence;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

public class OccurrenceSetGroupBehavior extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    protected void setDateLastModified(Occurrence enc) {
        String strOutputDateTime = ServletUtilities.getDate();

        enc.setDWCDateLastModified(strOutputDateTime);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("OccurrenceSetGroupBehavior.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        // --------------------------------
        // edit behavior note
        if ((request.getParameter("behaviorComment") != null)) {
            myShepherd.beginDBTransaction();
            Occurrence changeMe = myShepherd.getOccurrence(request.getParameter("number"));
            setDateLastModified(changeMe);
            String comment = request.getParameter("behaviorComment");
            String oldComment = "None";

            try {
                oldComment = changeMe.getGroupBehavior();
                changeMe.setGroupBehavior(comment);
                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() +
                    "</em><br>Changed group behavior observation from:<br><i>" + oldComment +
                    "</i><br>to:<br><i>" + comment + "</i></p>");
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_OK);
                out.println(
                    "<strong>Success:</strong> Occurrence group behavior observation was updated from:<br><i>"
                    + oldComment + "</i><br>to:<br><i>" + comment + "</i>");
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                    request.getParameter("number") + "\">Return to occurrence " +
                    request.getParameter("number") + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            } else {
                out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure:</strong> Occurrence group behavior observation was NOT updated because another user is currently modifying this record. Please press the Back button in your browser and try to edit the comments again in a few seconds.");
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                    request.getParameter("number") + "\">Return to occurrence" +
                    request.getParameter("number") + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Error:</strong> I don't have enough information to complete your request.");
            out.println("<p><a href=\"" + request.getScheme() + "://" +
                CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                request.getParameter("number") + "\">Return to occurrence " +
                request.getParameter("number") + "</a></p>\n");
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
