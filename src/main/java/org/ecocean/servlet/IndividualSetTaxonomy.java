package org.ecocean.servlet;

import org.ecocean.MarkedIndividual;
import org.ecocean.security.Collaboration;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

import org.apache.commons.lang3.StringEscapeUtils;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

// Set the taxonomy of an individual, either to a stated value or to whatever its encounters agree on.
public class IndividualSetTaxonomy extends HttpServlet {
    // A <select> submits a single value, so "copy the taxonomy from the encounters" has to travel
    // in the same parameter as a literal taxonomy would. It is matched before anything else and
    // never reaches setGenus()/setSpecificEpithet().
    public static final String FROM_ENCOUNTERS = "__FROM_ENCOUNTERS__";

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
        request.setCharacterEncoding("UTF-8");
        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IndividualSetTaxonomy.class");
        // set up for response
        response.setContentType("text/html; charset=UTF-8");
        PrintWriter out = response.getWriter();
        String indivID = request.getParameter("individual");
        String requested = request.getParameter("taxonomy");
        if (Util.stringIsEmptyOrNull(indivID) || (requested == null)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.println(
                "<strong>Error:</strong> I don't have enough information to complete your request.");
            out.close();
            return;
        }
        try {
            myShepherd.beginDBTransaction();
            MarkedIndividual changeMe = myShepherd.getMarkedIndividual(indivID);
            if (changeMe == null) {
                myShepherd.rollbackDBTransaction();
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Error:</strong> I was unable to set the taxonomy. I cannot find that individual in the database.");
                return;
            }
            // Look up and authorize outside the mutation try/catch below: everything caught in
            // there is reported as a write conflict, which would be the wrong thing to tell
            // someone whose real problem is a bad id or missing permission. The check needs the
            // transaction open because it walks the individual's lazily loaded encounters.
            if (!Collaboration.canUserFullyEditMarkedIndividual(changeMe, request)) {
                myShepherd.rollbackDBTransaction();
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                out.println(
                    "<strong>Failure:</strong> You do not have permission to change the taxonomy of this individual. Taxonomy is shared by every one of its encounters, so it may only be changed by someone who can edit all of them.");
                return;
            }
            String oldTaxonomy = changeMe.getTaxonomyString();
            String displayOld = (oldTaxonomy == null) ? "none" : oldTaxonomy;
            String newGenus = null;
            String newEpithet = null;
            if (FROM_ENCOUNTERS.equals(requested)) {
                // Ask the encounters what they say before touching the individual.
                // setTaxonomyFromEncounters() returns getTaxonomyString() from every one of its
                // exits, so neither its return value nor a before/after comparison can separate
                // "the encounters agreed on nothing" from "they agreed on what is already
                // stored" -- and only the first of those is a failure worth reporting.
                String[] derived = MarkedIndividual.unanimousTaxonomyFromEncounters(
                    changeMe.getEncounters());
                if (derived == null) {
                    myShepherd.rollbackDBTransaction();
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println(noDerivationMessage(changeMe));
                    return;
                }
                newGenus = derived[0];
                newEpithet = derived[1];
            } else {
                // Normalize once, and use the same string to validate and to store.
                // isValidTaxonomyName() maps underscores itself but neither trims nor collapses
                // whitespace, so without this a value could be rejected here and yet, had it
                // passed, have been stored with a space at the front of the epithet.
                String normalized = requested.trim().replaceAll("_", " ").replaceAll("\\s+", " ");
                if (!Util.stringExists(normalized)) {
                    myShepherd.rollbackDBTransaction();
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println("<strong>Error:</strong> Please choose a taxonomy.");
                    return;
                }
                // Re-submitting the stored value is a no-op, and is checked before validation on
                // purpose: individuals.jsp offers the individual's own taxonomy even when the
                // site's configuration no longer lists it, and refusing to save what is already
                // saved would make that option a trap.
                if (normalized.equals(oldTaxonomy)) {
                    myShepherd.rollbackDBTransaction();
                    response.setStatus(HttpServletResponse.SC_OK);
                    out.println("<strong>Success:</strong> Taxonomy is unchanged.");
                    return;
                }
                if (!myShepherd.isValidTaxonomyName(normalized)) {
                    myShepherd.rollbackDBTransaction();
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    out.println("<strong>Error:</strong> &quot;" +
                        StringEscapeUtils.escapeHtml4(normalized) +
                        "&quot; is not a taxonomy this site recognizes.");
                    return;
                }
                // Deliberately not MarkedIndividual.setTaxonomyString(): given a single word it
                // assigns that word as the specific epithet and leaves the genus untouched, so
                // choosing a genus-only taxonomy would splice it onto the old genus and assert a
                // species nobody recorded. Util splits at the first space instead, which reads a
                // lone word as the genus and keeps a subspecies intact.
                String[] parts = Util.stringToGenusSpecificEpithet(normalized);
                newGenus = parts[0];
                newEpithet = (parts.length > 1) ? parts[1] : null;
            }
            String newTaxonomy = Util.taxonomyString(newGenus, newEpithet);
            if ((newTaxonomy == null) || newTaxonomy.equals(oldTaxonomy)) {
                myShepherd.rollbackDBTransaction();
                response.setStatus(HttpServletResponse.SC_OK);
                out.println("<strong>Success:</strong> Taxonomy is unchanged.");
                return;
            }
            boolean locked = false;
            try {
                // Both parts, always. A genus-only taxonomy has to clear whatever epithet was
                // there before, or the individual is left reading as a species nobody chose.
                changeMe.setGenus(newGenus);
                changeMe.setSpecificEpithet(newEpithet);
                // Comments are rendered as HTML on the individual page. This one is safe to build
                // unescaped only because everything reaching it has either passed
                // isValidTaxonomyName() or come from the encounters -- keep those checks above.
                changeMe.addComments("<p><em>" + request.getRemoteUser() + " on " +
                    (new java.util.Date()).toString() + "</em><br>Changed taxonomy from " +
                    displayOld + " to " + newTaxonomy + ".</p>");
            } catch (Exception le) {
                locked = true;
                le.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
            if (!locked) {
                myShepherd.commitDBTransaction();
                response.setStatus(HttpServletResponse.SC_OK);
                out.println("<strong>Success:</strong> Taxonomy has been updated from " +
                    displayOld + " to " + newTaxonomy + ".");
                // after the commit: this opens a Shepherd of its own, and would otherwise report
                // the value we just replaced
                String message = "The taxonomy for " + indivID + " has been updated from " +
                    displayOld + " to " + newTaxonomy + ".";
                ServletUtilities.informInterestedIndividualParties(request, indivID, message,
                    context);
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.println(
                    "<strong>Failure:</strong> Taxonomy was NOT updated. This record is currently being modified by another user. Please try this operation again in a few seconds.");
            }
        } finally {
            out.close();
            myShepherd.closeDBTransaction();
        }
    }

    // The encounters settled nothing: either they disagree, or none of them states a taxonomy at
    // all. Which of the two it is only distinctTaxonomiesFromEncounters() can say, and what they
    // do state is the useful thing to report back.
    private static String noDerivationMessage(MarkedIndividual indiv) {
        Set<String> stated = MarkedIndividual.distinctTaxonomiesFromEncounters(
            indiv.getEncounters());

        if (stated.isEmpty())
            return
                    "<strong>Failure:</strong> None of this individual's encounters records a taxonomy, so there is nothing to copy from them.";
        StringBuilder sb = new StringBuilder(
            "<strong>Failure:</strong> This individual's encounters do not agree on a taxonomy: ");
        boolean first = true;
        for (String tax : stated) {
            if (!first) sb.append(", ");
            sb.append(StringEscapeUtils.escapeHtml4(tax));
            first = false;
        }
        sb.append(". Correct the encounters, or choose a taxonomy directly.");
        return sb.toString();
    }
}
