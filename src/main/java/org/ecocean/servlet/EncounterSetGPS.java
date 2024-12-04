package org.ecocean.servlet;

import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import org.ecocean.*;

// Set alternateID for this encounter/sighting
public class EncounterSetGPS extends HttpServlet {
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
        myShepherd.setAction("EncounterSetGPS.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        boolean isOwner = true;
        // reset GPS coordinates
        // System.out.println("Trying to resetGPS...");
        if ((request.getParameter("number") != null) && (request.getParameter("lat") != null) &&
            (request.getParameter("longitude") != null)) {
            myShepherd.beginDBTransaction();
            Encounter changeMe = myShepherd.getEncounter(request.getParameter("number"));
            setDateLastModified(changeMe);
            String longitude = request.getParameter("longitude");
            String lat = request.getParameter("lat");
            String oldGPS = "";
            if ((changeMe.getDecimalLatitude() == null) ||
                (changeMe.getDecimalLongitude() == null)) {
                oldGPS = "NO VALUE";
            } else {
                oldGPS = "(" + changeMe.getDecimalLatitude() + " latitude, " +
                    changeMe.getDecimalLongitude() + " longitude)";
            }
            String newGPS = "(" + lat + " latitude, " + longitude + " longitude)";

            try {
                if (!lat.equals("") && !longitude.equals("")) {
                    // changeMe.setGPSLatitude(lat+"&deg; "+gpsLatitudeMinutes+"\' "+gpsLatitudeSeconds+"\" "+latDirection);

                    try {
                        double degrees = (new Double(lat)).doubleValue();

                        changeMe.setDWCDecimalLatitude(degrees);
                        double degrees2 = (new Double(longitude)).doubleValue();

                        changeMe.setDWCDecimalLongitude(degrees2);
                    } catch (Exception e) {
                        System.out.println("EncounterSetGPS: problem setting decimal latitude!");
                        e.printStackTrace();
                    }
                }
                // if one is not set, set all to null
                else {
                    changeMe.setDecimalLatitude(null);
                    changeMe.setDecimalLongitude(null);

                    // changeMe.setDWCDecimalLatitude(-9999.0);
                    // changeMe.setDWCDecimalLongitude(-9999.0);
                    newGPS = "NO VALUE";
                }
                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() +
                    "</em><br>Changed encounter GPS coordinates from " + oldGPS + " to " + newGPS +
                    ".</p>");
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success:</strong> The encounter's recorded GPS location has been updated from "
                    + oldGPS + " to " + newGPS + ".");
                // out.println("<p><a
                // href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return
                // to encounter <strong>"+request.getParameter("number")+"</strong></a></p>\n");
                response.setStatus(HttpServletResponse.SC_OK);

                out.println(
                    "<p><a href=\"individualSearchResults.jsp\">View all individuals</a></font></p>");
                // out.println(ServletUtilities.getFooter(context));
                String message = "The recorded GPS location for encounter #" +
                    request.getParameter("number") + " has been updated from " + oldGPS + " to " +
                    newGPS + ".";
                ServletUtilities.informInterestedParties(request, request.getParameter("number"),
                    message, context);
            } else {
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure:</strong> Encounter GPS location was NOT updated. An error was encountered. Please try this operation again in a few seconds. If this condition persists, contact the webmaster.");
                // out.println("<p><a
                // href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return
                // to encounter <strong>"+request.getParameter("number")+"</strong></a></p>\n");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } else {
            ///out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I don't have enough information to complete your request.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            // out.println("<p><a
            // href=\"http://"+CommonConfiguration.getURLLocation(request)+"/encounters/encounter.jsp?number="+request.getParameter("number")+"\">Return
            // to encounter <strong>"+request.getParameter("number")+"</strong></a></p>\n");
            out.println(ServletUtilities.getFooter(context));
        }
// end GPS reset
        out.close();
        myShepherd.closeDBTransaction();
    }
}
