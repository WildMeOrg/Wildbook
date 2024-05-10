package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;
import org.ecocean.User;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

// import javax.jdo.*;
// import com.poet.jdo.*;

public class EncounterSetSubmitterID extends HttpServlet {
    public void init(ServletConfig config)
    throws ServletException {
        super.init(config);
    }

    public void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        // Here we forward to the appropriate page using the request dispatcher
        doPost(request, response);
    }

    public void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        String context = "context0";

        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("EncounterSetSubmitterID.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean authorized = false;
        String encounterNumber = "None", submitter = "N/A";
        String prevSubmitter = "null";

        myShepherd.beginDBTransaction();
        try {
            // check if we have our needed fields and that this is actually an encounter in the database
            if ((request.getParameter("number") != null) &&
                (request.getParameter("submitter") != null) &&
                (myShepherd.isEncounter(request.getParameter("number")))) {
                encounterNumber = request.getParameter("number");
                submitter = request.getParameter("submitter");

                Encounter enc = myShepherd.getEncounter(encounterNumber);
                User encOwner = null;
                if (enc.getSubmitterID() != null)
                    encOwner = myShepherd.getUser(enc.getSubmitterID());
                if ((encOwner == null && request.isUserInRole("orgAdmin") || // case 1
                    ((encOwner != null) &&
                    ServletUtilities.isCurrentUserOrgAdminOfTargetUser(encOwner, request,
                    myShepherd)) ||                                                                                             // case 2
                    ServletUtilities.isUserAuthorizedForEncounter(enc,
                    request)) && CommonConfiguration.isCatalogEditable(context)
                    ) { // all cases required
                    authorized = true;
                }
                try {
                    if (enc.getSubmitterID() != null) {
                        prevSubmitter = enc.getSubmitterID();
                    }
                    if (submitter.trim().equals("")) { enc.setSubmitterID(null); } else {
                        enc.setSubmitterID(submitter);
                    }
                    enc.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() + "</em><br>" +
                        "Changed Library submitter ID from " + prevSubmitter + " to " + submitter +
                        ".</p>");
                } catch (Exception le) {
                    locked = true;
                    myShepherd.rollbackDBTransaction();
                }
                if (!locked && authorized) {
                    myShepherd.commitDBTransaction();
                    out.println(ServletUtilities.getHeader(request));
                    out.println(
                        "<strong>Success!</strong> I have successfully changed the Library submitter ID for encounter "
                        + encounterNumber + " from " + prevSubmitter + " to " + submitter +
                        ".</p>");
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.println("<p><a href=\"" + request.getScheme() + "://" +
                        CommonConfiguration.getURLLocation(request) +
                        "/encounters/encounter.jsp?number=" + encounterNumber +
                        "\">Return to encounter " + encounterNumber + "</a></p>\n");
                    out.println(ServletUtilities.getFooter(context));
                    String message = "The submitter ID for encounter " + encounterNumber +
                        " was changed from " + prevSubmitter + " to " + submitter + ".";
                    ServletUtilities.informInterestedParties(request, encounterNumber, message,
                        context);
                } else if (authorized) {
                    myShepherd.rollbackDBTransaction();
                    out.println(ServletUtilities.getHeader(request));
                    out.println(
                        "<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to remove this data file again.");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println("<p><a href=\"" + request.getScheme() + "://" +
                        CommonConfiguration.getURLLocation(request) +
                        "/encounters/encounter.jsp?number=" + encounterNumber +
                        "\">Return to encounter " + encounterNumber + "</a></p>\n");
                    out.println(ServletUtilities.getFooter(context));
                } else {
                    myShepherd.rollbackDBTransaction();
                    out.println(ServletUtilities.getHeader(request));
                    out.println(
                        "<strong>Failure!</strong> You must be the encounter owner or have elevated user roles to reassign submitter ID.");
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println("<p><a href=\"" + request.getScheme() + "://" +
                        CommonConfiguration.getURLLocation(request) +
                        "/encounters/encounter.jsp?number=" + encounterNumber +
                        "\">Return to encounter " + encounterNumber + "</a></p>\n");
                    out.println(ServletUtilities.getFooter(context));
                }
            } else {
                myShepherd.rollbackDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Error:</strong> I was unable to set the submitter ID. I cannot find the encounter that you intended it for in the database, or I wasn't sure what file you wanted to remove.");
                out.println(ServletUtilities.getFooter(context));
            }
        } catch (Exception e) {
            e.printStackTrace();
            myShepherd.rollbackDBTransaction();
        } finally {
            myShepherd.closeDBTransaction();
        }
        out.close();
    }
}
