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
public class TissueSampleSetHaplotype extends HttpServlet {
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
        myShepherd.setAction("TissueSampleSetHaplotype.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;

        myShepherd.beginDBTransaction();
        if ((request.getParameter("analysisID") != null) &&
            (request.getParameter("sampleID") != null) &&
            (request.getParameter("number") != null) &&
            (request.getParameter("haplotype") != null) &&
            (!request.getParameter("haplotype").equals("")) &&
            (myShepherd.isTissueSample(request.getParameter("sampleID"),
            request.getParameter("number"))) &&
            (myShepherd.isEncounter(request.getParameter("number")))) {
            String sampleID = request.getParameter("sampleID");
            String encounterNumber = request.getParameter("number");
            String haplotype = request.getParameter("haplotype");
            String analysisID = request.getParameter("analysisID");
            MitochondrialDNAAnalysis mtDNA = new MitochondrialDNAAnalysis();

            try {
                Encounter enc = myShepherd.getEncounter(encounterNumber);
                TissueSample sample = myShepherd.getTissueSample(sampleID, encounterNumber);
                if (myShepherd.isGeneticAnalysis(sampleID, encounterNumber, analysisID,
                    "MitochondrialDNA")) {
                    mtDNA = myShepherd.getMitochondrialDNAAnalysis(sampleID, encounterNumber,
                        analysisID);

                    // now set the new values
                    mtDNA.setHaplotype(request.getParameter("haplotype"));
                } else {
                    mtDNA = new MitochondrialDNAAnalysis(analysisID, haplotype, encounterNumber,
                        sampleID);
                }
                if (request.getParameter("processingLabTaskID") != null) {
                    mtDNA.setProcessingLabTaskID(request.getParameter("processingLabTaskID"));
                }
                if (request.getParameter("processingLabName") != null) {
                    mtDNA.setProcessingLabName(request.getParameter("processingLabName"));
                }
                if (request.getParameter("processingLabContactName") != null) {
                    mtDNA.setProcessingLabContactName(request.getParameter(
                        "processingLabContactName"));
                }
                if (request.getParameter("processingLabContactDetails") != null) {
                    mtDNA.setProcessingLabContactDetails(request.getParameter(
                        "processingLabContactDetails"));
                }
                sample.addGeneticAnalysis(mtDNA);

                enc.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br />" +
                    "Added or updated mitochondrial DNA analysis (haplotype) " +
                    request.getParameter("analysisID") + " for tissue sample " +
                    request.getParameter("sampleID") + ".<br />" + mtDNA.getHTMLString());
                // check if this affects the MarkedIndividual.localHaplotypeReflection
                if (enc.getIndividual() != null) {
                    MarkedIndividual indie = myShepherd.getMarkedIndividual(
                        enc.getIndividual().getIndividualID());
                    indie.doNotSetLocalHaplotypeReflection(mtDNA.getHaplotype());
                }
            } catch (Exception le) {
                locked = true;
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success!</strong> I have successfully set the haplotype for tissue sample "
                    + request.getParameter("sampleID") + " for encounter " + encounterNumber +
                    ".</p>");

                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/encounters/encounter.jsp?number=" + encounterNumber +
                    "\">Return to encounter " + encounterNumber + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            } else {
                out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> This encounter is currently being modified by another user or is inaccessible. Please wait a few seconds before trying to modify this encounter again.");

                out.println("<p><a href=\"" + request.getScheme() + "://" +
                    CommonConfiguration.getURLLocation(request) +
                    "/encounters/encounter.jsp?number=" + encounterNumber +
                    "\">Return to encounter " + encounterNumber + "</a></p>\n");
                out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I was unable to set the haplotype. I cannot find the encounter or tissue sample that you intended it for in the database.");
            out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
