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

public class OccurrenceSetIndividualCount extends HttpServlet {
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
        myShepherd.setAction("OccurrenceSetIndividualCount.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean isOwner = true;
        String newIndieCount = "null";
        // reset encounter depth in meters
        if (request.getParameter("number") != null) {
            myShepherd.beginDBTransaction();
            Occurrence changeMe = myShepherd.getOccurrence(request.getParameter("number"));
            setDateLastModified(changeMe);
            String oldIndieCount = "null";

            try {
                if (changeMe.getIndividualCount() != null) {
                    oldIndieCount = changeMe.getIndividualCount().toString();
                }
                if ((request.getParameter("count") != null) &&
                    (!request.getParameter("count").equals(""))) {
                    Integer theCount = new Integer(request.getParameter("count"));
                    changeMe.setIndividualCount(theCount);
                    newIndieCount = request.getParameter("count");
                } else {
                    changeMe.setIndividualCount(null);
                }
                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() +
                    "</em><br>Changed occurrence individual count from " + oldIndieCount + " to " +
                    newIndieCount + ".</p>");
            } catch (NumberFormatException nfe) {
                System.out.println("Bad numeric input on attempt to change individual count.");
                locked = true;
                nfe.printStackTrace();
                myShepherd.rollbackDBTransaction();
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_OK);
                out.println("<strong>Success:</strong> Individual count has been updated from " +
                    oldIndieCount + " to " + newIndieCount + ".");
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                    request.getParameter("number") + "\">Return to occcurence " +
                    request.getParameter("number") + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            } else {
                out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure:</strong> Individual count was NOT updated because another user is currently modifying the record for this occurrence or the value input does not translate to a valid integer count.");
                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                    request.getParameter("number") + "\">Return to occurrence " +
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
