package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

public class EncounterSetBehavior extends HttpServlet {
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
        myShepherd.setAction("EncounterSetBehavior.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean isOwner = true;
        // --------------------------------
        // edit behavior note
        if ((request.getParameter("behaviorComment") != null)) {
            myShepherd.beginDBTransaction();
            Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));
            setDateLastModified(changeMe);
            String comment = request.getParameter("behaviorComment");
            String oldComment = "None";

            try {
                oldComment = changeMe.getBehavior();
                if (request.getParameter("behaviorComment").trim().equals("")) {
                    changeMe.setBehavior(null);
                } else {
                    changeMe.setBehavior(request.getParameter("behaviorComment"));
                }
                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() +
                    "</em><br>Changed behavior observation from:<br><i>" + oldComment +
                    "</i><br>to:<br><i>" + comment + "</i></p>");
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success:</strong> Encounter behavior observation was updated from:<br><i>"
                    + oldComment + "</i><br>to:<br><i>" + comment + "</i>");
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
                response.setStatus(HttpServletResponse.SC_OK);
                // out.println(ServletUtilities.getFooter(context));
                String message = "Encounter #" + request.getParameter("number") +
                    " submitted comments have been updated from \"" + oldComment + "\" to \"" +
                    comment + "\".";
                ServletUtilities.informInterestedParties(request, request.getParameter("number"),
                    message, context);
            } else {
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure:</strong> Encounter behavior observation was NOT updated because another user is currently modifying this record. Please press the Back button in your browser and try to edit the comments again in a few seconds.");
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I don't have enough information to complete your request.");
            // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
            // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
            // out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
