package org.ecocean.servlet;

import org.ecocean.*;
import org.ecocean.genetics.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;

// Set alternateID for this encounter/sighting
public class TissueSampleRemoveMicrosatelliteMarkers extends HttpServlet {
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
        myShepherd.setAction("TissueSampleRemoveMicrosatelliteMarkers.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;

        myShepherd.beginDBTransaction();
        if ((request.getParameter("analysisID") != null) &&
            (request.getParameter("encounter") != null) &&
            (request.getParameter("sampleID") != null) &&
            (!request.getParameter("sampleID").equals("")) &&
            (myShepherd.isTissueSample(request.getParameter("sampleID"),
            request.getParameter("encounter"))) &&
            (myShepherd.isEncounter(request.getParameter("encounter")))) {
            try {
                Encounter enc = myShepherd.getEncounter(request.getParameter("encounter"));
                TissueSample genSample = myShepherd.getTissueSample(request.getParameter(
                    "sampleID"), request.getParameter("encounter"));

                // MitochondrialDNAAnalysis mtDNA=myShepherd.getMitochondrialDNAAnalysis(request.getParameter("sampleID"),
                // request.getParameter("encounter"), request.getParameter("analysisID"));
                MicrosatelliteMarkersAnalysis msDNA = myShepherd.getMicrosatelliteMarkersAnalysis(
                    request.getParameter("sampleID"), request.getParameter("encounter"),
                    request.getParameter("analysisID"));
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br />" +
                    "Removed microsatellite marker analysis ID " +
                    request.getParameter("analysisID") + ".<br />" + msDNA.getHTMLString());

                genSample.removeGeneticAnalysis(msDNA);

                myShepherd.throwAwayMicrosatelliteMarkersAnalysis(msDNA);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br />" +
                    "Removed microsatellite marker analysis ID " +
                    request.getParameter("analysisID") + ".<br />");
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
                // myShepherd.closeDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                // myShepherd.closeDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success!</strong> I have successfully removed the microsatellite markers for tissue sample "
                    + request.getParameter("sampleID") + " for encounter " +
                    request.getParameter("encounter") + ".</p>");

                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/encounters/encounter.jsp?number=" + request.getParameter("encounter") +
                    "\">Return to encounter " + request.getParameter("encounter") + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            } else {
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> This encounter is currently being modified by another user or is inaccessible. Please wait a few seconds before trying to modify this encounter again.");

                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/encounters/encounter.jsp?number=" + request.getParameter("encounter") +
                    "\">Return to encounter " + request.getParameter("encounter") + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I was unable to remove the microsatellite markers. I cannot find the encounter or tissue sample that you intended it for in the database.");
            out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
