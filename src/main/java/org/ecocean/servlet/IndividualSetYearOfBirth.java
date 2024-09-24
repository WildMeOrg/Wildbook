package org.ecocean.servlet;

import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

import org.joda.time.DateTime;

// Set alternateID for the individual
public class IndividualSetYearOfBirth extends HttpServlet {
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
        myShepherd.setAction("IndividualSetYearOfBorth.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String sharky = "None";
        sharky = request.getParameter("individual");

        String timeOfBirth = "";
        long longTime = -1;
        if ((request.getParameter("timeOfBirth") != null) &&
            (!request.getParameter("timeOfBirth").equals(""))) {
            timeOfBirth = request.getParameter("timeOfBirth");
            longTime = (new DateTime(timeOfBirth)).getMillis();
        }
        myShepherd.beginDBTransaction();
        if (myShepherd.isMarkedIndividual(sharky)) {
            MarkedIndividual myShark = myShepherd.getMarkedIndividual(sharky);

            try {
                // Long myTime=new Long(longTime);
                myShark.setTimeOfBirth(longTime);
                myShark.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br>" + "Set time of birth to " +
                    timeOfBirth + ".");
            } catch (Exception le) {
                locked = true;
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_OK);
                out.println(
                    "<strong>Success!</strong> I have successfully changed the time of birth for individual "
                    + sharky + " to " + timeOfBirth + ".</p>");

                // out.println("<p><a href=\""+request.getScheme()+"://" + CommonConfiguration.getURLLocation(request) + "/individuals.jsp?number=" +
                // sharky + "#birthdate\">Return to " + sharky + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
                String message = "The time of birth for " + sharky + " was set to " + timeOfBirth +
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
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I was unable to set the individual's time of birth. I cannot find the individual that you intended it for in the database, or the time was not specified.");
            // out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
