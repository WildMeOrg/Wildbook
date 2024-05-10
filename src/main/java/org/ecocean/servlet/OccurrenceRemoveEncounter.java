package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

public class OccurrenceRemoveEncounter extends HttpServlet {
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
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        // remove Encounter from Occurrence
        if ((request.getParameter("number") != null)) {
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("OccurrenceRemoveEncounter.class");

            myShepherd.beginDBTransaction();
            Encounter enc2remove = myShepherd.getEncounter(request.getParameter("number"));
            if (enc2remove != null &&
                myShepherd.getOccurrenceForEncounter(enc2remove.getCatalogNumber()) != null) {
                setDateLastModified(enc2remove);
                String old_name = myShepherd.getOccurrenceForEncounter(
                    enc2remove.getCatalogNumber()).getOccurrenceID();
                boolean wasRemoved = false;
                String name_s = "";
                try {
                    Occurrence removeFromMe = myShepherd.getOccurrenceForEncounter(
                        enc2remove.getCatalogNumber());
                    name_s = removeFromMe.getOccurrenceID();
                    if (removeFromMe.getEncounters() != null) {
                        while (removeFromMe.getEncounters().contains(enc2remove)) {
                            removeFromMe.removeEncounter(enc2remove);
                        }
                    }
                    enc2remove.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() + "</em><br>" +
                        "Removed from occurrence " + old_name + ".</p>");
                    removeFromMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() + "</em><br>" + "Removed encounter " +
                        request.getParameter("number") + ".</p>");
                    enc2remove.setOccurrenceID(null);
                    if (removeFromMe.getEncounters().size() == 0) {
                        myShepherd.throwAwayOccurrence(removeFromMe);
                        wasRemoved = true;
                    }
                } catch (java.lang.NullPointerException npe) {
                    npe.printStackTrace();
                    locked = true;
                    myShepherd.rollbackDBTransaction();
                } catch (Exception le) {
                    le.printStackTrace();
                    locked = true;
                    myShepherd.rollbackDBTransaction();
                }
                if (!locked) {
                    myShepherd.commitDBTransaction();
                    // out.println(ServletUtilities.getHeader(request));
                    out.println("<strong>Success:</strong> Encounter " +
                        request.getParameter("number") +
                        " was successfully removed from occurrence " + old_name + ".");
                    response.setStatus(HttpServletResponse.SC_OK);
                    // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                    // request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + ".</a></p>\n");
                    if (wasRemoved) {
                        out.println("Occurrence <strong>" + name_s +
                            "</strong> was also removed because it contained no encounters.");
                    }
                    // out.println(ServletUtilities.getFooter(context));
                } else {
                    // out.println(ServletUtilities.getHeader(request));
                    out.println("<strong>Failure:</strong> Encounter " +
                        request.getParameter("number") + " was NOT removed from occurrence " +
                        old_name +
                        ". Another user is currently modifying this record entry. Please try again in a few seconds.");
                    // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                    // request.getParameter("number") + "\">Return to encounter " + request.getParameter("number") + ".</a></p>\n");
                    // out.println(ServletUtilities.getFooter(context));
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
            } else {
                myShepherd.rollbackDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Error:</strong> You can't remove this encounter from an occurrence because it is not assigned to one.");
                // out.println(ServletUtilities.getFooter(context));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            myShepherd.closeDBTransaction();
        } else {
            out.println(
                "I did not receive enough data to remove this encounter from an occurrence.");
        }
        out.close();
    }
}
