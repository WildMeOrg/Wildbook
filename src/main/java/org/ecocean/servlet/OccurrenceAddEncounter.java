package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

public class OccurrenceAddEncounter extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    private void setDateLastModified(Encounter enc) {
        String strOutputDateTime = ServletUtilities.getDate();

        enc.setDWCDateLastModified(strOutputDateTime);
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
        myShepherd.setAction("OccurrenceAddEncounter.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false, isOwner = true;
        boolean isAssigned = false;
        // String action = request.getParameter("action");
        // add encounter to an existing Occurrence object
        if ((request.getParameter("number") != null) &&
            (request.getParameter("occurrence") != null)) {
            String altID = "";
            myShepherd.beginDBTransaction();
            Encounter enc2add = myShepherd.getEncounter(request.getParameter("number"));
            setDateLastModified(enc2add);
            if ((myShepherd.isOccurrence(request.getParameter("occurrence"))) &&
                (myShepherd.getOccurrenceForEncounter(request.getParameter("number")) == null)) {
                try {
                    // boolean sexMismatch = false;

                    // myShepherd.beginDBTransaction();
                    Occurrence addToMe = myShepherd.getOccurrence(request.getParameter(
                        "occurrence").trim());

                    try {
                        if (!addToMe.getEncounters().contains(enc2add)) {
                            addToMe.addEncounter(enc2add);
                        }
                        enc2add.setOccurrenceID(request.getParameter("occurrence").trim());
                        enc2add.addComments("<p><em>" + request.getRemoteUser() + " on " +
                            (new java.util.Date()).toString() + "</em><br>" +
                            "Added to occurrence " + request.getParameter("occurrence") + ".</p>");
                        addToMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                            (new java.util.Date()).toString() + "</em><br>" + "Added encounter " +
                            request.getParameter("number") + ".</p>");
                    } catch (Exception le) {
                        le.printStackTrace();
                        locked = true;
                        myShepherd.rollbackDBTransaction();
                    }
                    if (!locked) {
                        myShepherd.commitDBTransaction();
                        myShepherd.rollbackDBTransaction();

                        // print successful result notice
                        // out.println(ServletUtilities.getHeader(request));
                        response.setStatus(HttpServletResponse.SC_OK);
                        out.println("<strong>Success:</strong> Encounter " +
                            request.getParameter("number") +
                            " was successfully added to occurrence " +
                            request.getParameter("occurrence") + ".");

                        // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                        // request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + ".</a></p>\n");
                        // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                        // request.getParameter("occurrence") + "\">View occurrence " + request.getParameter("occurrence") + ".</a></p>\n");
                        // out.println(ServletUtilities.getFooter(context));
                    }
                    // if lock exception thrown
                    else {
                        // out.println(ServletUtilities.getHeader(request));
                        out.println("<strong>Failure:</strong> Encounter " +
                            request.getParameter("number") + " was NOT added to occurrence " +
                            request.getParameter("occurrence") +
                            ". Please try to add the encounter again after a few seconds.");
                        // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                        // request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + ".</a></p>\n");
                        out.println("<p><a href=\"" + request.getScheme() + "://" +
                            CommonConfiguration.getURLLocation(request) +
                            "/occurrence.jsp?number=" + request.getParameter("occurrence") +
                            "\">View occurrence " + request.getParameter("occurrence") +
                            ".</a></p>\n");
                        // out.println(ServletUtilities.getFooter(context));
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    }
                } catch (Exception e) {
                    // out.println(ServletUtilities.getHeader(request));
                    out.println("<strong>Error:</strong> No such record exists in the database.");
                    // out.println(ServletUtilities.getFooter(context));
                    myShepherd.rollbackDBTransaction();
                    e.printStackTrace();
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    // myShepherd.closeDBTransaction();
                }
            } else {
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Error:</strong> You are trying to add this encounter to a sighting ID that does not exist.");
                // out.println(ServletUtilities.getFooter(context));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                myShepherd.rollbackDBTransaction();
                // myShepherd.closeDBTransaction();
            }
        } else {
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I didn't receive enough data to add this encounter to an occurrence.");
            // out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
