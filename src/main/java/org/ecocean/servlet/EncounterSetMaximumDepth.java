package org.ecocean.servlet;

import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

public class EncounterSetMaximumDepth extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        doPost(request, response);
    }

    private void setDateLastModified(Encounter enc) {
        String strOutputDateTime = ServletUtilities.getDate();

        enc.setDWCDateLastModified(strOutputDateTime);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSetMaximumDepth.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean isOwner = true;
        String newDep = "null";
        // reset encounter depth in meters
        if (request.getParameter("number") != null) {
            myShepherd.beginDBTransaction();
            Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));
            setDateLastModified(changeMe);
            String oldDepth = "null";

            try {
                if (changeMe.getMaximumDepthInMeters() != null) {
                    oldDepth = changeMe.getMaximumDepthInMeters().toString();
                }
                if ((request.getParameter("depth") != null) &&
                    (!request.getParameter("depth").equals(""))) {
                    Double theDepth = new Double(request.getParameter("depth"));
                    changeMe.setDepth(theDepth);
                    newDep = request.getParameter("depth") + " meters";
                } else {
                    changeMe.setDepth(null);
                }
                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br>Changed encounter depth from " +
                    oldDepth + " meters to " + newDep + ".</p>");
            } catch (NumberFormatException nfe) {
                System.out.println(
                    "Bad numeric input on attempt to change depth for the encounter.");
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
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_OK);
                out.println("<strong>Success:</strong> Encounter depth has been updated from " +
                    oldDepth + " meters to " + newDep + ".");
                // out.println(ServletUtilities.getFooter(context));
                String message = "The size of encounter#" + request.getParameter("number") +
                    " has been updated from " + oldDepth + " meters to " +
                    request.getParameter("depth") + " meters.";
                ServletUtilities.informInterestedParties(request, request.getParameter("number"),
                    message, context);
            } else {
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure:</strong> Encounter depth was NOT updated because another user is currently modifying the record for this encounter or the value input does not translate to a valid depth number.");
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");

                // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            // out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Error:</strong> I don't have enough information to complete your request.");
            // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
            // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            // out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
