package org.ecocean.servlet;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.Shepherd;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.StringTokenizer;

// Set alternateID for this encounter/sighting
public class EncounterSetGenusSpecies extends HttpServlet {
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
        myShepherd.setAction("EncounterSetGenusSpecies.class");
        // set up for response
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        boolean locked = false;
        String sharky = "None";

        sharky = request.getParameter("encounter");
        String genusSpecies = "";

        myShepherd.beginDBTransaction();
        if ((request.getParameter("encounter") != null) && (myShepherd.isEncounter(sharky)) &&
            (request.getParameter("genusSpecies") != null)) {
            Encounter myShark = myShepherd.getEncounter(sharky);
            genusSpecies = request.getParameter("genusSpecies");
            // specificEpithet = request.getParameter("specificEpithet");
            try {
                String genus = "";
                String specificEpithet = "";

                // now we have to break apart genus species
                StringTokenizer tokenizer = new StringTokenizer(genusSpecies, " ");
                if (tokenizer.countTokens() >= 2) {
                    genus = tokenizer.nextToken();
                    myShark.setGenus(genus);
                    specificEpithet = tokenizer.nextToken();

                    myShark.setSpecificEpithet(specificEpithet.replaceAll(",", "").replaceAll("_",
                        " "));
                    myShark.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() + "</em><br>" +
                        "Set genus and species to: " + genus + " " + specificEpithet.replaceAll(",",
                        "").replaceAll("_", " "));
                } else if (genusSpecies.equals("unknown")) {
                    myShark.setGenus(null);
                    myShark.setSpecificEpithet(null);
                    myShark.addComments("<p><em>" + request.getRemoteUser() + " on " +
                        (new java.util.Date()).toString() +
                        "</em><br />Set genus and species to null.");
                }
                // handle malformed Genus Species formats
                else {
                    throw new Exception(
                              "The format of the genusSpecies parameter in servlet EncounterSetGenusSpecies did not have two tokens delimited by a space (e.g., \"Rhincodon typus\").");
                }
            } catch (Exception le) {
                locked = true;
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
            }
            if (!locked) {
                if (myShark.getAnnotations() != null) { // need to persist annots as well, since they have changed as well
                    for (Annotation ann : myShark.getAnnotations()) {
                        myShepherd.getPM().makePersistent(ann);
                    }
                }
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Success!</strong> I have successfully changed the genus and species for encounter "
                    + sharky + " to " + genusSpecies + ".</p>");
                response.setStatus(HttpServletResponse.SC_OK);
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + sharky +
                // "\">Return to encounter " + sharky + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
                // String message = "The alternate ID for encounter " + sharky + " was set to " + alternateID + ".";
            } else {
                // out.println(ServletUtilities.getHeader(request));
                out.println(
                    "<strong>Failure!</strong> This encounter is currently being modified by another user. Please wait a few seconds before trying to modify this encounter again.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                // out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + sharky +
                // "\">Return to encounter " + sharky + "</a></p>\n");
                // out.println(ServletUtilities.getFooter(context));
            }
        } else {
            myShepherd.rollbackDBTransaction();
            // out.println(ServletUtilities.getHeader(request));
            out.println(
                "<strong>Error:</strong> I was unable to set the genus and species. I cannot find the encounter that you intended it for in the database, or your information request did not include all of the required parameters.");
            // out.println(ServletUtilities.getFooter(context));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.close();
        myShepherd.closeDBTransaction();
    }
}
