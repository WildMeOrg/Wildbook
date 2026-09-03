package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.security.Collaboration;
import org.ecocean.shepherd.core.Shepherd;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Issue #1722: taxonomy was the only field on individuals.jsp with no way to edit it, and an
 * individual never picked up a taxonomy its encounters were corrected to afterwards.
 *
 * These pin the two things about this servlet that are easy to get wrong and silent when wrong:
 * how a chosen taxonomy is split into genus and specific epithet, and the fact that the encounters
 * have to be asked what they say *before* the individual is touched.
 *
 * No DB and no container: the Shepherd is mocked at construction and the individual is a real
 * MarkedIndividual behind a spy, so the assertions read genuine field state rather than verify().
 */
class IndividualSetTaxonomyTest {
    private static final String INDIV_ID = "4a5f0f34-1722-4d1e-9b41-1f0e3c2d5a67";

    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;
    private StringWriter responseOut;
    private IndividualSetTaxonomy servlet;
    private Shepherd usedShepherd;

    @BeforeEach void setUp()
    throws Exception {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        responseOut = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseOut));
        when(mockRequest.getRemoteUser()).thenReturn("tester");
        servlet = new IndividualSetTaxonomy();
    }

    private Encounter encounter(String genus, String specificEpithet) {
        Encounter enc = new Encounter();

        enc.setGenus(genus);
        enc.setSpecificEpithet(specificEpithet);
        return enc;
    }

    private MarkedIndividual individual(String genus, String specificEpithet, Encounter... encs) {
        MarkedIndividual mi = spy(new MarkedIndividual());

        mi.setGenus(genus);
        mi.setSpecificEpithet(specificEpithet);
        doReturn(new Vector<Encounter>(Arrays.asList(encs))).when(mi).getEncounters();
        return mi;
    }

    /** Runs one request. A null individual stands for "no such individual in the database". */
    private void post(String taxonomyParam, MarkedIndividual indiv, boolean canEdit,
        boolean validName)
    throws Exception {
        when(mockRequest.getParameter("individual")).thenReturn(INDIV_ID);
        when(mockRequest.getParameter("taxonomy")).thenReturn(taxonomyParam);
        try (MockedStatic<ServletUtilities> utils = mockStatic(ServletUtilities.class);
        MockedStatic<Collaboration> collab = mockStatic(Collaboration.class);
        MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class,
                (mockShepherd, ctx) -> {
            when(mockShepherd.getMarkedIndividual(anyString())).thenReturn(indiv);
            when(mockShepherd.isValidTaxonomyName(anyString())).thenReturn(validName);
        })) {
            utils.when(() -> ServletUtilities.getContext(mockRequest)).thenReturn("context0");
            collab.when(() -> Collaboration.canUserFullyEditMarkedIndividual(any(),
                any())).thenReturn(canEdit);
            servlet.doPost(mockRequest, mockResponse);
            usedShepherd = shepherds.constructed().isEmpty() ? null : shepherds.constructed().get(
                0);
        }
    }

    private String body() {
        return responseOut.toString();
    }

    // ---- how a chosen taxonomy is split -------------------------------------------------------

    // Anything after the genus is the specific epithet, so a subspecies survives being chosen.
    @Test void keepsSubspeciesIntact()
    throws Exception {
        MarkedIndividual mi = individual(null, null);

        post("Canis lupus familiaris", mi, true, true);
        assertEquals("Canis", mi.getGenus(), "genus is everything up to the first space");
        assertEquals("lupus familiaris", mi.getSpecificEpithet(),
            "everything after the first space is the epithet, subspecies included");
    }

    // MarkedIndividual.setTaxonomyString() would read a lone word as the *epithet* and leave the
    // old genus in place, leaving this individual reading "Orcinus Delphinus" -- a species nobody
    // recorded. A genus-only choice has to clear the epithet instead.
    @Test void genusOnlyChoiceClearsTheEpithet()
    throws Exception {
        MarkedIndividual mi = individual("Orcinus", "orca");

        post("Delphinus", mi, true, true);
        assertEquals("Delphinus", mi.getGenus(), "a lone word is the genus");
        assertNull(mi.getSpecificEpithet(), "the previous epithet must not survive");
        assertEquals("Delphinus", mi.getTaxonomyString(), "no invented species");
    }

    @Test void collapsesStrayWhitespaceBeforeStoring()
    throws Exception {
        MarkedIndividual mi = individual(null, null);

        post("  Orcinus   orca ", mi, true, true);
        assertEquals("Orcinus", mi.getGenus(), "genus is trimmed");
        assertEquals("orca", mi.getSpecificEpithet(), "epithet does not keep a leading space");
    }

    @Test void treatsUnderscoresAsSpaces()
    throws Exception {
        MarkedIndividual mi = individual(null, null);

        post("Tursiops_truncatus", mi, true, true);
        assertEquals("Tursiops", mi.getGenus());
        assertEquals("truncatus", mi.getSpecificEpithet());
    }

    // ---- copying the taxonomy from the encounters ---------------------------------------------

    // The ordering guard. setTaxonomyFromEncounters() returns getTaxonomyString() from every one
    // of its exits, so an implementation that inferred failure from its return value -- or from
    // comparing the taxonomy before and after -- would report "your encounters disagree" here,
    // where they agree perfectly and simply have nothing new to say.
    @Test void encountersAgreeingWithTheStoredValueIsNotAFailure()
    throws Exception {
        MarkedIndividual mi = individual("Orcinus", "orca", encounter("Orcinus", "orca"),
            encounter("Orcinus", "orca"));

        post(IndividualSetTaxonomy.FROM_ENCOUNTERS, mi, true, true);
        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
        assertEquals("Orcinus orca", mi.getTaxonomyString(), "the stored value stands");
        assertFalse(body().contains("do not agree"),
            "encounters that agree must never be reported as disagreeing");
    }

    @Test void copiesTheTaxonomyTheEncountersAgreeOn()
    throws Exception {
        MarkedIndividual mi = individual("Orcinus", "orca", encounter("Delphinus", "delphis"),
            encounter("Delphinus", "delphis"));

        post(IndividualSetTaxonomy.FROM_ENCOUNTERS, mi, true, true);
        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
        assertEquals("Delphinus delphis", mi.getTaxonomyString());
        verify(usedShepherd).commitDBTransaction();
    }

    @Test void reportsWhatDisagreeingEncountersActuallySay()
    throws Exception {
        MarkedIndividual mi = individual("Orcinus", "orca", encounter("Orcinus", "orca"),
            encounter("Delphinus", "delphis"));

        post(IndividualSetTaxonomy.FROM_ENCOUNTERS, mi, true, true);
        verify(mockResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertTrue(body().contains("Orcinus orca") && body().contains("Delphinus delphis"),
            "name the conflicting values, so the curator knows what to correct");
        assertEquals("Orcinus orca", mi.getTaxonomyString(), "nothing is written on a conflict");
        verify(usedShepherd, never()).commitDBTransaction();
    }

    // "we could not derive one" must never be turned into "so store nothing".
    @Test void encountersStatingNothingLeaveTheValueAlone()
    throws Exception {
        MarkedIndividual mi = individual("Orcinus", "orca", encounter(null, null),
            encounter("", ""));

        post(IndividualSetTaxonomy.FROM_ENCOUNTERS, mi, true, true);
        verify(mockResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals("Orcinus orca", mi.getTaxonomyString(), "an existing taxonomy is not cleared");
        verify(usedShepherd, never()).commitDBTransaction();
    }

    // ---- refusals -----------------------------------------------------------------------------

    @Test void refusesWithoutEditRightsOverEveryEncounter()
    throws Exception {
        MarkedIndividual mi = individual("Orcinus", "orca");

        post("Delphinus delphis", mi, false, true);
        verify(mockResponse).setStatus(HttpServletResponse.SC_FORBIDDEN);
        assertEquals("Orcinus orca", mi.getTaxonomyString());
        assertFalse(mi.getComments().contains("Changed taxonomy"),
            "a refused request leaves no audit trail");
        verify(usedShepherd, never()).commitDBTransaction();
    }

    // IndividualSetSex looks up the individual inside its try/catch, so an id that matches nothing
    // NPEs and the user is told the record is being edited by someone else. Say what went wrong.
    @Test void unknownIndividualIsNotReportedAsAWriteConflict()
    throws Exception {
        post("Delphinus delphis", null, true, true);
        verify(mockResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertFalse(body().contains("another user"), "a missing individual is not a lock conflict");
    }

    // IndividualSetSex writes this message but leaves the status at 200, so the caller's .fail()
    // handler never runs and the page reports success.
    @Test void missingParametersActuallySetAnErrorStatus()
    throws Exception {
        post(null, individual("Orcinus", "orca"), true, true);
        verify(mockResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertFalse(body().isEmpty());
    }

    @Test void refusesATaxonomyTheSiteDoesNotRecognize()
    throws Exception {
        MarkedIndividual mi = individual("Orcinus", "orca");

        post("Nonsuchus fictus", mi, true, false);
        verify(mockResponse).setStatus(HttpServletResponse.SC_BAD_REQUEST);
        assertEquals("Orcinus orca", mi.getTaxonomyString());
        verify(usedShepherd, never()).commitDBTransaction();
    }

    // individuals.jsp offers the individual's own taxonomy even when the site no longer configures
    // it, so that opening the editor cannot quietly replace it. Saving it back must therefore be a
    // no-op rather than a rejection -- note validName is false here and it still succeeds.
    @Test void savingTheStoredValueBackIsANoOpEvenWhenUnlisted()
    throws Exception {
        MarkedIndividual mi = individual("Giraffa", "camelopardalis rothschildi");

        post("Giraffa camelopardalis rothschildi", mi, true, false);
        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
        assertEquals("Giraffa camelopardalis rothschildi", mi.getTaxonomyString());
        assertFalse(mi.getComments().contains("Changed taxonomy"),
            "an unchanged value writes no audit comment");
        verify(usedShepherd, never()).commitDBTransaction();
    }

    // ---- the happy path -----------------------------------------------------------------------

    @Test void commitsAndRecordsWhatChanged()
    throws Exception {
        MarkedIndividual mi = individual("Orcinus", "orca");

        post("Delphinus delphis", mi, true, true);
        verify(mockResponse).setStatus(HttpServletResponse.SC_OK);
        verify(usedShepherd).commitDBTransaction();
        assertNotNull(mi.getComments());
        assertTrue(mi.getComments().contains("Orcinus orca") &&
            mi.getComments().contains("Delphinus delphis"),
            "the audit comment records both the old and the new value");
    }
}
