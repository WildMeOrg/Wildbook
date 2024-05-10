package org.ecocean.servlet;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.LocationID;
import org.ecocean.MarkedIndividual;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

// Set alternateID for this encounter/sighting
public class EncounterSetLocationID extends HttpServlet {
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
        myShepherd.setAction("EncounterSetLocationID.class");
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
        if (request.getParameter("code") != null) {
            String oldCode = "";
            myShepherd.beginDBTransaction();
            String encNum = request.getParameter("number").trim();
            Encounter changeMe = myShepherd.getEncounter(encNum);
            setDateLastModified(changeMe);
            try {
                oldCode = changeMe.getLocationCode();
                changeMe.setLocationCode(request.getParameter("code").trim());
                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br>Changed location code from " +
                    oldCode + " to " + request.getParameter("code") + ".</p>");
                // update numberLocations on  a dependent MarkedIndividual too
                if (changeMe.getIndividual() != null) {
                    MarkedIndividual indy = changeMe.getIndividual();
                    indy.refreshDependentProperties();
                }
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                // out.println("<strong>Success:</strong> Encounter location has been updated from " + oldCode + " to " + request.getParameter("code")
                // + ".");
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" +
                // request.getParameter("number") + "\">Return to encounter #" + request.getParameter("number") + "</a></p>\n");
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("application/json");
                out.println("{\"locationID\":\"" + request.getParameter("code") + "\",\"name\":\"" +
                    LocationID.getNameForLocationID(request.getParameter("code"), null) + "\"}");

                // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
                // out.println(ServletUtilities.getFooter(context));
                // String message = "Encounter #" + request.getParameter("number") + " location code has been updated from " + oldCode + " to " +
                // request.getParameter("code") + ".";
                // ServletUtilities.informInterestedParties(request, request.getParameter("number"), message,context);
            } else {
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure:</strong> Encounter location code was NOT updated because the record for this encounter is currently being modified by another user. Please try to add the location code again in a few seconds.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
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

            // out.println("<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
            // out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
