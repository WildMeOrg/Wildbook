package org.ecocean.servlet;

import org.ecocean.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

// Set alternateID for this encounter/sighting
public class EncounterSetIdentifiable extends HttpServlet {
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
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean isOwner = true;
        /**
           if(request.getParameter("number")!=null){
           myShepherd.beginDBTransaction();
           if(myShepherd.isEncounter(request.getParameter("number"))) {
           Encounter verifyMyOwner=myShepherd.getEncounter(request.getParameter("number"));
           String locCode=verifyMyOwner.getLocationCode();

           //check if the encounter is assigned
              if((verifyMyOwner.getSubmitterID()!=null)&&(request.getRemoteUser()!=null)&&(verifyMyOwner.getSubmitterID().equals(request.getRemoteUser()))){
           isOwner=true;
           }

           //if the encounter is assigned to this user, they have permissions for it...or if they're a manager else
              if((request.isUserInRole("admin"))){
           isOwner=true;
           }
           //if they have general location code permissions for the encounter's location code else if(request.isUserInRole(locCode)){isOwner=true;}
           }
           myShepherd.rollbackDBTransaction();
           }
         */
        if (request.getParameter("number") != null) {
            myShepherd.beginDBTransaction();
            Encounter enc2reaccept = myShepherd.getEncounter(request.getParameter("number"));
            setDateLastModified(enc2reaccept);
            myShepherd.rollbackDBTransaction();
            myShepherd.beginDBTransaction();
            try {
                // enc2reaccept.reaccept();
                enc2reaccept.setState("unapproved");
                enc2reaccept.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() +
                    "</em><br>Reaccepted into visual database.</p>");
            } catch (Exception le) {
                // System.out.println("Hit locked exception on action: "+action);
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println("<strong>Success:</strong> Encounter #" +
                    request.getParameter("number") +
                    " was successfully added back into the visual database.");
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
                response.setStatus(HttpServletResponse.SC_OK);

                // out.println(ServletUtilities.getFooter(context));
                String message = "Encounter #" + request.getParameter("number") +
                    " was accepted back into the visual database.";
                ServletUtilities.informInterestedParties(request, request.getParameter("number"),
                    message, context);
            } else {
                // out.println(ServletUtilities.getHeader(request));
                out.println("<strong>Failure:</strong> Encounter #" +
                    request.getParameter("number") +
                    " was NOT successfully added back into the visual database. This encounter is currently being modified by another user. Please try this operation again in a few seconds.");
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

                // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");

                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I don't know which encounter you're trying to reaccept.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

            // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");

            // out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
