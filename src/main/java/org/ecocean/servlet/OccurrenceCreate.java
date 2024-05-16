package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

public class OccurrenceCreate extends HttpServlet {
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
        myShepherd.setAction("OccurrenceCreate.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String myOccurrenceID = "";
        if (request.getParameter("occurrence") != null) {
            myOccurrenceID = request.getParameter("occurrence");

            // remove special characters
            myOccurrenceID = ServletUtilities.cleanFileName(myOccurrenceID);
        }
        System.out.println("Here's a new Occurrence ID " + myOccurrenceID +
            " that I'm creating for " + request.getParameter("number") + ".");
        // Create a new Occurrence from an encounter
        if ((myOccurrenceID != null) && (request.getParameter("number") != null) &&
            (!myOccurrenceID.trim().equals(""))) {
            myShepherd.beginDBTransaction();
            Encounter enc2make = myShepherd.getEncounter(request.getParameter("number"));
            setDateLastModified(enc2make);
            System.out.println("New Occ Id In Create : " + myOccurrenceID);

            boolean ok2add = true;
            if (!(myShepherd.isOccurrence(myOccurrenceID))) {
                if ((myShepherd.getOccurrenceForEncounter(enc2make.getCatalogNumber()) == null) &&
                    (myOccurrenceID != null)) {
                    try {
                        Occurrence newOccur = new Occurrence(myOccurrenceID.trim(), enc2make);
                        newOccur.addComments("<p><em>" + request.getRemoteUser() + " on " +
                            (new java.util.Date()).toString() + "</em><br>" + "Created " +
                            myOccurrenceID + " from encounter " + request.getParameter("number") +
                            ".</p>");
                        newOccur.setDateTimeCreated(ServletUtilities.getDate());
                        myShepherd.storeNewOccurrence(newOccur);

                        enc2make.addComments("<p><em>" + request.getRemoteUser() + " on " +
                            (new java.util.Date()).toString() + "</em><br>" +
                            "Added to new occurrence " + myOccurrenceID + ".</p>");
                        enc2make.setOccurrenceID(myOccurrenceID.trim());
                    } catch (Exception le) {
                        locked = true;
                        le.printStackTrace();
                        myShepherd.rollbackDBTransaction();
                        myShepherd.closeDBTransaction();
                        System.out.println("Failed to create a new Occurrence.: ");
                    }
                    if (!locked && ok2add) {
                        myShepherd.commitDBTransaction();
                        myShepherd.closeDBTransaction();

                        // output success statement
                        // out.println(ServletUtilities.getHeader(request));
                        response.setStatus(HttpServletResponse.SC_OK);
                        out.println("<strong>Success:</strong> Encounter " +
                            request.getParameter("number") +
                            " was successfully used to create occurrence <strong>" +
                            myOccurrenceID + "</strong>.");
                        // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                        // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + ".</a></p>\n");
                        // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                        // myOccurrenceID + "\">View <strong>" + myOccurrenceID + ".</strong></a></p>\n");
                        // out.println(ServletUtilities.getFooter(context));
                    } else {
                        // out.println(ServletUtilities.getHeader(request));
                        out.println("<strong>Failure:</strong> Encounter " +
                            request.getParameter("number") +
                            " was NOT used to create a new occurrence. This encounter is currently being modified by another user. Please go back and try to create the new occurrence again in a few seconds.");
                        // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                        // request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + ".</a></p>\n");
                        // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/occurrence.jsp?number=" +
                        // myOccurrenceID + "\">View <strong>" + myOccurrenceID + "</strong></a></p>\n");
                        // out.println(ServletUtilities.getFooter(context));
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    }
                } else {
                    System.out.println(
                        "Failed to find a lack of Occ ID on the encounter, and to find a good Occ Id for a new one.");
                    myShepherd.rollbackDBTransaction();
                    myShepherd.closeDBTransaction();
                }
            } else if ((myShepherd.isOccurrence(myOccurrenceID))) {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Error:</strong> You are trying to create a new sighting ID, but the sighting ID already exists.");
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                // request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + ".</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } else {
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Error:</strong> You cannot make a new occurrence from this encounter because it is already assigned to another occurrence. Remove it from its previous occurrence if you want to re-assign it elsewhere.");
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                // request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + ".</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } else {
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I didn't receive enough data to create a new occurrence from this encounter.");
            // out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            myShepherd.closeDBTransaction();
        }
        out.close();
    }
}
