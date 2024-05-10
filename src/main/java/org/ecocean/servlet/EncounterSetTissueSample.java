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
public class EncounterSetTissueSample extends HttpServlet {
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
        myShepherd.setAction("EncounterSetTissueSample.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String sharky = "None";

        sharky = request.getParameter("encounter");
        myShepherd.beginDBTransaction();
        if ((myShepherd.isEncounter(sharky)) && (request.getParameter("sampleID") != null) &&
            (!request.getParameter("sampleID").equals(""))) {
            String sampleID = request.getParameter("sampleID").trim();
            Encounter enc = myShepherd.getEncounter(sharky);
            try {
                TissueSample genSample = new TissueSample();
                if (myShepherd.isTissueSample(sampleID, sharky)) {
                    genSample = myShepherd.getTissueSample(sampleID, sharky);
                    genSample.resetAbstractClassParameters(request);
                } else {
                    genSample = new TissueSample(enc.getCatalogNumber(), sampleID, request);
                    enc.addTissueSample(genSample);
                }
                if (request.getParameter("tissueType") != null) {
                    genSample.setTissueType(request.getParameter("tissueType"));
                }
                if (request.getParameter("preservationMethod") != null) {
                    genSample.setPreservationMethod(request.getParameter("preservationMethod"));
                }
                if (request.getParameter("storageLabID") != null) {
                    genSample.setStorageLabID(request.getParameter("storageLabID"));
                }
                if (request.getParameter("alternateSampleID") != null) {
                    genSample.setAlternateSampleID(request.getParameter("alternateSampleID"));
                }
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br />" +
                    "Added or updated tissue sample ID " + request.getParameter("sampleID") +
                    ".<br />" + genSample.getHTMLString());
            } catch (Exception le) {
                locked = true;
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success!</strong> I have successfully set the biological sample for encounter "
                    + sharky + ".</p>");

                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/encounters/encounter.jsp?number=" + sharky + "\">Return to encounter " +
                    sharky + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            } else {
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> This encounter is currently being modified by another user or is inaccessible. Please wait a few seconds before trying to modify this encounter again.");

                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/encounters/encounter.jsp?number=" + sharky + "\">Return to encounter " +
                    sharky + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            out.println(ServletUtilities.getHeader(request));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Error:</strong> I was unable to set the biological sample. I cannot find the encounter that you intended it for in the database.");
            out.println(ServletUtilities.getFooter(context));
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
