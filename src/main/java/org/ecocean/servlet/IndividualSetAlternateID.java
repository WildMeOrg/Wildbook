package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

// Set alternateID for the individual
public class IndividualSetAlternateID extends HttpServlet {
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
        request.setCharacterEncoding("UTF-8");
        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IndividualSetAlternateID.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String sharky = "None";
        sharky = request.getParameter("individual");
        String alternateID = "";
        myShepherd.beginDBTransaction();
        if (myShepherd.isMarkedIndividual(sharky)) {
            MarkedIndividual myShark = myShepherd.getMarkedIndividual(sharky);
            alternateID = request.getParameter("alternateid");
            try {
                // FIXME  how should we actually set "alternate id" in world of .names MultiValue now.
                // probably need to pass in some sort of context/hint (e.g. "are you setting it for an org, yourself, etc?")
                if (alternateID != null) {
                    System.out.println(
                        "WARNING: IndividualSetAlternateID servlet using legacy concept of alternateID, please fix this!");
                    myShark.setAlternateID(alternateID);
                    myShark.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() + "</em><br>" + "Set alternate ID: " +
                        alternateID + ".");
                }
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
                    "<strong>Success!</strong> I have successfully changed the alternate ID for individual "
                    + sharky + " to " + alternateID + ".</p>");

                // out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" +
                // sharky + "\">Return to " + sharky + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
                String message = "The alternate ID for " + sharky + " was set to " + alternateID +
                    ".";
            } else {
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure!</strong> This individual is currently being modified by another user. Please wait a few seconds before trying to modify this individual again.");

                // out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" +
                // sharky + "\">Return to " + sharky + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I was unable to set the individual alternate ID. I cannot find the individual that you intended it for in the database.");
            // out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
