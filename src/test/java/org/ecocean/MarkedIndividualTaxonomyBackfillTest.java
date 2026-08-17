package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * The backfill that gives already-stored individuals the taxonomy their encounters agree on (issue
 * #1113). Its cursor is what makes repeated runs progress: the individuals it cannot fix -- no source
 * taxonomy, or encounters that disagree -- stay in the candidate set, so without a cursor every run
 * would re-examine the same rows and never reach the rest of the table.
 */
class MarkedIndividualTaxonomyBackfillTest {
    private Query query;
    private Shepherd shepherd;

    private Shepherd shepherdReturning(List<MarkedIndividual> candidates) {
        PersistenceManager pm = mock(PersistenceManager.class);

        query = mock(Query.class);
        shepherd = mock(Shepherd.class);
        when(shepherd.getPM()).thenReturn(pm);
        when(pm.newQuery(anyString(), anyString())).thenReturn(query);
        when(query.executeWithArray(any(), any(), any())).thenReturn(candidates);
        return shepherd;
    }

    private MarkedIndividual individual(String id, Encounter... encs) {
        MarkedIndividual mi = spy(new MarkedIndividual());

        doReturn(id).when(mi).getId();
        doReturn(new Vector<Encounter>(Arrays.asList(encs))).when(mi).getEncounters();
        return mi;
    }

    private Encounter encounter(String genus, String specificEpithet) {
        Encounter enc = new Encounter();

        enc.setGenus(genus);
        enc.setSpecificEpithet(specificEpithet);
        return enc;
    }

    @Test void candidateSqlContainsNoColonForDataNucleusToMistakeForANamedParameter() {
        // DataNucleus scans the whole SQL text for :name tokens without respecting string
        // literals, so a POSIX class like [:space:] in a regex literal is parsed as a named
        // parameter and execution fails with "SQL query has parameter ... yet not found"
        String sql = MarkedIndividual.backfillTaxonomySql(5);

        assertEquals(-1, sql.indexOf(':'),
            "any colon in the SQL text is misparsed as a named parameter: " + sql);
        assertEquals(3, sql.length() - sql.replace("?", "").length(),
            "cursor plus one bound regex per taxonomy column: " + sql);
    }

    @Test void committingRunWritesFixablesAndAdvancesThePastEverythingExamined() {
        MarkedIndividual fixable = individual("ind-1", encounter("Delphinus", "delphis"));
        MarkedIndividual disagreeing = individual("ind-2", encounter("Delphinus", "delphis"),
            encounter("Orcinus", "orca"));
        MarkedIndividual noSource = individual("ind-3", encounter(null, null));
        Shepherd sh = shepherdReturning(Arrays.asList(fixable, disagreeing, noSource));

        try (MockedStatic<SystemValue> sv = mockStatic(SystemValue.class)) {
            sv.when(() -> SystemValue.getString(any(), anyString())).thenReturn(null);
            JSONObject res = MarkedIndividual.backfillTaxonomyFromEncounters(sh, null, 100, true);
            assertEquals("Delphinus delphis", fixable.getTaxonomyString(),
                "an individual whose encounters agree gets their taxonomy");
            assertNull(disagreeing.getTaxonomyString(),
                "an individual whose encounters disagree is left alone");
            assertEquals(1, res.getInt("_updated"), "one individual was fixable");
            assertEquals(1, res.getInt("_conflicted"), "one had encounters that disagree");
            assertEquals(1, res.getInt("_noTaxonomyOnEncounters"),
                "one had no taxonomy to inherit");
            assertEquals(Arrays.asList("Delphinus delphis", "Orcinus orca"),
                res.getJSONObject("conflicts").getJSONArray("ind-2").toList(),
                "the conflict report says what the encounters actually claim");
            sv.verify(() -> SystemValue.set(sh, MarkedIndividual.TAXONOMY_BACKFILL_CURSOR, "ind-3"));
        }
    }

    @Test void writesOnlyWhatASightingActuallyStates() {
        MarkedIndividual crossPaired = individual("ind-1", encounter("Balaenoptera", null),
            encounter(null, "orca"));
        Shepherd sh = shepherdReturning(Arrays.asList(crossPaired));

        try (MockedStatic<SystemValue> sv = mockStatic(SystemValue.class)) {
            sv.when(() -> SystemValue.getString(any(), anyString())).thenReturn(null);
            JSONObject res = MarkedIndividual.backfillTaxonomyFromEncounters(sh, null, 100, true);
            assertEquals("Balaenoptera", res.getJSONObject("updates").getString("ind-1"),
                "the backfill must not pair one sighting's genus with another's epithet");
            assertEquals("Balaenoptera", crossPaired.getGenus(), "the supported genus is written");
            assertNull(crossPaired.getSpecificEpithet(), "the unattached epithet is not");
        }
    }

    @Test void replacesAStoredPlaceholderRatherThanSkippingIt() {
        // the candidate query selects a padded placeholder, so the Java side has to agree it is blank
        // -- otherwise the row is skipped, the cursor moves past it, and it stays uncorrected
        MarkedIndividual placeholder = individual("ind-1", encounter("Delphinus", "delphis"));

        placeholder.setGenus(" unknown ");
        Shepherd sh = shepherdReturning(Arrays.asList(placeholder));

        try (MockedStatic<SystemValue> sv = mockStatic(SystemValue.class)) {
            sv.when(() -> SystemValue.getString(any(), anyString())).thenReturn(null);
            JSONObject res = MarkedIndividual.backfillTaxonomyFromEncounters(sh, null, 100, true);
            assertEquals(0, res.getInt("_skippedHasStoredTaxonomy"),
                "a placeholder is not a stored taxonomy");
            assertEquals("Delphinus delphis", placeholder.getTaxonomyString(),
                "the placeholder is replaced by what the encounters say");
        }
    }

    @Test void dryRunReportsWithoutWritingOrMovingTheCursor() {
        MarkedIndividual fixable = individual("ind-1", encounter("Delphinus", "delphis"));
        Shepherd sh = shepherdReturning(Arrays.asList(fixable));

        try (MockedStatic<SystemValue> sv = mockStatic(SystemValue.class)) {
            sv.when(() -> SystemValue.getString(any(), anyString())).thenReturn(null);
            JSONObject res = MarkedIndividual.backfillTaxonomyFromEncounters(sh, null, 100, false);
            assertEquals(1, res.getInt("_updated"), "a dry run still reports what it would change");
            assertEquals("Delphinus delphis", res.getJSONObject("updates").getString("ind-1"),
                "and which taxonomy it would write");
            assertNull(fixable.getTaxonomyString(), "but must not write it");
            sv.verify(() -> SystemValue.set(any(Shepherd.class), anyString(), anyString()),
                never());
            sv.verify(() -> SystemValue.set(any(Shepherd.class), anyString(), (String)isNull()),
                never());
        }
    }

    @Test void resumesFromTheStoredCursor() {
        Shepherd sh = shepherdReturning(new ArrayList<MarkedIndividual>());

        try (MockedStatic<SystemValue> sv = mockStatic(SystemValue.class)) {
            sv.when(() -> SystemValue.getString(any(), anyString())).thenReturn("ind-500");
            JSONObject res = MarkedIndividual.backfillTaxonomyFromEncounters(sh, null, 100, false);
            assertEquals("ind-500", res.getString("_startId"),
                "a run with no explicit startId picks up where the last committing run stopped");
            verify(query).executeWithArray(eq("ind-500"), any(), any());
        }
    }

    @Test void explicitStartIdOverridesTheStoredCursor() {
        Shepherd sh = shepherdReturning(new ArrayList<MarkedIndividual>());

        try (MockedStatic<SystemValue> sv = mockStatic(SystemValue.class)) {
            sv.when(() -> SystemValue.getString(any(), anyString())).thenReturn("ind-500");
            MarkedIndividual.backfillTaxonomyFromEncounters(sh, "ind-100", 100, false);
            verify(query).executeWithArray(eq("ind-100"), any(), any());
        }
    }

    @Test void firstRunStartsAtTheBeginningOfTheTable() {
        Shepherd sh = shepherdReturning(new ArrayList<MarkedIndividual>());

        try (MockedStatic<SystemValue> sv = mockStatic(SystemValue.class)) {
            sv.when(() -> SystemValue.getString(any(), anyString())).thenReturn(null);
            JSONObject res = MarkedIndividual.backfillTaxonomyFromEncounters(sh, null, 100, false);
            assertEquals("", res.getString("_startId"),
                "with no stored cursor every id sorts after the starting point");
            verify(query).executeWithArray(eq(""),
                eq(MarkedIndividual.TAXONOMY_BACKFILL_BLANK_REGEX),
                eq(MarkedIndividual.TAXONOMY_BACKFILL_BLANK_REGEX));
        }
    }

    @Test void emptyBatchClearsTheCursorSoALaterRunReportsAfresh() {
        Shepherd sh = shepherdReturning(new ArrayList<MarkedIndividual>());

        try (MockedStatic<SystemValue> sv = mockStatic(SystemValue.class)) {
            sv.when(() -> SystemValue.getString(any(), anyString())).thenReturn("ind-900");
            JSONObject res = MarkedIndividual.backfillTaxonomyFromEncounters(sh, null, 100, true);
            assertTrue(res.getBoolean("_finished"),
                "nothing left to examine means the sweep is done");
            assertFalse(res.has("_lastId"), "and there is no row to report as last");
            // reaching the end clears the cursor rather than parking at it
            sv.verify(() -> SystemValue.set(eq(sh),
                eq(MarkedIndividual.TAXONOMY_BACKFILL_CURSOR), (String)isNull()));
        }
    }
}
