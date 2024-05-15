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

// Set alternateID for this encounter/sighting
public class EncounterSetLivingStatus extends HttpServlet {
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

        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSetLivingStatus.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String sharky = "None";

        sharky = request.getParameter("encounter");
        String livingStatus = "";
        myShepherd.beginDBTransaction();
        if ((myShepherd.isEncounter(sharky)) && (request.getParameter("livingStatus") != null)) {
            Encounter myShark = myShepherd.getEncounter(sharky);
            livingStatus = request.getParameter("livingStatus");
            try {
                myShark.setLivingStatus(livingStatus);
            } catch (Exception le) {
                locked = true;
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success!</strong> I have successfully changed the living status for encounter "
                    + sharky + " to " + livingStatus + ".</p>");
                response.setStatus(HttpServletResponse.SC_OK);
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + sharky +
                // "\">Return to encounter " + sharky + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
                String message = "The living status (allive/dead) for encounter " + sharky +
                    " was set to " + livingStatus + ".";
            } else {
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> This encounter is currently being modified by another user or is inaccessible. Please wait a few seconds before trying to modify this encounter again.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + sharky +
                // "\">Return to encounter " + sharky + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I was unable to set the living status. I cannot find the encounter that you intended it for in the database.");
            // out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
