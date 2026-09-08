package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import org.junit.jupiter.api.Test;

/**
 * MarkedIndividual is meant to inherit its taxonomy from its encounters when it has none of its own
 * -- both encounter-taking constructors and addEncounter() call setTaxonomyFromEncounters(). Issue
 * #1113: that inheritance never happened for a newly built individual, because the guard tested
 * genus for null while the field defaults to the empty string, so individuals created from
 * encounters were left with no taxonomy and the header quicksearch had none to display.
 */
class MarkedIndividualTaxonomyTest {
    private MarkedIndividual individualWithEncounters(Encounter... encs) {
        MarkedIndividual mi = spy(new MarkedIndividual());

        doReturn(new Vector<Encounter>(Arrays.asList(encs))).when(mi).getEncounters();
        return mi;
    }

    private Encounter encounter(String genus, String specificEpithet) {
        Encounter enc = new Encounter();

        enc.setGenus(genus);
        enc.setSpecificEpithet(specificEpithet);
        return enc;
    }

    @Test void inheritsTaxonomyFromEncounters() {
        MarkedIndividual mi = individualWithEncounters(encounter("Balaenoptera", "musculus"),
            encounter("Balaenoptera", "musculus"));

        assertEquals("Balaenoptera musculus", mi.setTaxonomyFromEncounters(),
            "individual with no taxonomy of its own must take it from its encounters");
        assertEquals("Balaenoptera musculus", mi.getTaxonomyString(),
            "derived taxonomy must be stored on the individual, not just returned");
    }

    @Test void inheritsWhenStoredTaxonomyPartsAreNull() {
        // a row persisted before the empty-string default loads with both parts null
        MarkedIndividual mi = individualWithEncounters(encounter("Balaenoptera", "musculus"));

        mi.setGenus(null);
        mi.setSpecificEpithet(null);
        assertEquals("Balaenoptera musculus", mi.setTaxonomyFromEncounters(),
            "null-valued taxonomy parts count as unset, same as the empty-string default");
    }

    @Test void doesNotOverwriteTaxonomyAlreadySet() {
        MarkedIndividual mi = individualWithEncounters(encounter("Balaenoptera", "musculus"));

        mi.setTaxonomyString("Orcinus orca");
        assertEquals("Orcinus orca", mi.setTaxonomyFromEncounters(),
            "a taxonomy set on the individual wins over its encounters");
    }

    @Test void doesNotCompletePartialTaxonomyAlreadySet() {
        MarkedIndividual mi = individualWithEncounters(encounter("Delphinus", "delphis"));

        mi.setGenus("Delphinus");
        assertEquals("Delphinus", mi.setTaxonomyFromEncounters(),
            "a genus already on the individual is a value, so inheritance stays out of the way");
    }

    @Test void disagreeingEncountersDeriveNothing() {
        MarkedIndividual mi = individualWithEncounters(encounter("Balaenoptera", "musculus"),
            encounter("Orcinus", "orca"));

        assertNull(mi.setTaxonomyFromEncounters(),
            "encounters identified as different species must not stamp an arbitrary one on the individual");
        assertNull(mi.getTaxonomyString(), "nothing derivable means nothing stored");
    }

    @Test void placeholderTaxonomyValuesAreNotIdentifications() {
        MarkedIndividual mi = individualWithEncounters(encounter("unknown", "unknown"),
            encounter("none", null));

        assertNull(mi.setTaxonomyFromEncounters(),
            "placeholder values are not a species identification");
    }

    @Test void placeholdersStayPlaceholdersWithSpaceAroundThem() {
        // Util.stringExists() compares against "unknown"/"none" without trimming, so a padded
        // placeholder must be recognized before it can conflict with, or be written instead of, a
        // real identification
        MarkedIndividual padded = individualWithEncounters(encounter(" unknown ", " none "));

        assertNull(padded.setTaxonomyFromEncounters(),
            "a padded placeholder is still a placeholder");
        MarkedIndividual mixed = individualWithEncounters(encounter(" unknown ", null),
            encounter("Balaenoptera", "musculus"));
        assertEquals("Balaenoptera musculus", mixed.setTaxonomyFromEncounters(),
            "and it must not read as a genus that disagrees with the real identification");
    }

    @Test void partlyFilledEncounterDoesNotBlockAFullIdentification() {
        // the genus of the first sighting was never filled in; it must not read as a different animal
        MarkedIndividual mi = individualWithEncounters(encounter("", "musculus"),
            encounter("Balaenoptera", "musculus"));

        assertEquals("Balaenoptera musculus", mi.setTaxonomyFromEncounters(),
            "an encounter missing its genus agrees with the one that has it");
    }

    @Test void genusOnlySightingsYieldGenusOnly() {
        MarkedIndividual mi = individualWithEncounters(encounter("Delphinus", null),
            encounter("Delphinus", null));

        assertEquals("Delphinus", mi.setTaxonomyFromEncounters(),
            "sightings identified only to genus still give the individual its genus");
        assertEquals("Delphinus", mi.getGenus(), "the genus must be stored as the genus");
        assertNull(mi.getSpecificEpithet(),
            "a lone genus must not be stored as the specific epithet");
    }

    @Test void partialSightingsDoNotContradictAFullIdentification() {
        // genus-only, epithet-only and full-binomial sightings of the same animal, in every order:
        // the parts are judged separately, so none of the six may report a disagreement
        Encounter[] encs = {
            encounter("Delphinus", null), encounter("", "delphis"),
            encounter("Delphinus", "delphis")
        };

        for (int[] order : new int[][] {
                 { 0, 1, 2 }, { 0, 2, 1 }, { 1, 0, 2 }, { 1, 2, 0 }, { 2, 0, 1 }, { 2, 1, 0 }
             }) {
            MarkedIndividual mi = individualWithEncounters(encs[order[0]], encs[order[1]],
                encs[order[2]]);
            assertEquals("Delphinus delphis", mi.setTaxonomyFromEncounters(),
                "encounter order must not change the derived taxonomy, but did for order "
                + Arrays.toString(order));
        }
    }

    @Test void partialSightingsAreNotPairedIntoASpeciesNobodyRecorded() {
        // one sighting states only a genus, the other only an epithet: taking a part from each would
        // assert "Balaenoptera orca", which is not a species and which no encounter claims
        for (boolean genusFirst : new boolean[] { true, false }) {
            MarkedIndividual mi = genusFirst
                ? individualWithEncounters(encounter("Balaenoptera", null), encounter(null, "orca"))
                : individualWithEncounters(encounter(null, "orca"), encounter("Balaenoptera", null));
            assertEquals("Balaenoptera", mi.setTaxonomyFromEncounters(),
                "only the genus is actually supported by a sighting (genusFirst=" + genusFirst + ")");
            assertNull(mi.getSpecificEpithet(),
                "the unattached epithet must not be adopted (genusFirst=" + genusFirst + ")");
        }
    }

    @Test void anEpithetFromASightingWithNoGenusStillCounts() {
        // the middle sighting says capensis; the individual must not end up as delphis regardless.
        // This is the case a candidate-only derivation gets wrong: the genus-only sighting is chosen
        // first, the epithet-only one supersedes nothing, and then the binomial replaces the candidate
        // outright -- so capensis would never be compared against delphis at all.
        Encounter[] encs = {
            encounter("Delphinus", null), encounter(null, "capensis"),
            encounter("Delphinus", "delphis")
        };

        for (int[] order : new int[][] { { 0, 1, 2 }, { 2, 1, 0 }, { 1, 2, 0 } }) {
            MarkedIndividual mi = individualWithEncounters(encs[order[0]], encs[order[1]],
                encs[order[2]]);
            assertNull(mi.setTaxonomyFromEncounters(),
                "a partly-identified sighting can still disagree, order " + Arrays.toString(order));
        }
    }

    @Test void capitalizationAndWhitespaceAreNotDisagreements() {
        for (boolean capitalizedFirst : new boolean[] { true, false }) {
            MarkedIndividual mi = capitalizedFirst
                ? individualWithEncounters(encounter("Delphinus", "delphis"),
                encounter(" delphinus ", "delphis"))
                : individualWithEncounters(encounter(" delphinus ", "delphis"),
                encounter("Delphinus", "delphis"));
            assertEquals("Delphinus delphis", mi.setTaxonomyFromEncounters(),
                "the same taxonomy written differently is one taxonomy, and the stored form must not"
                + " depend on encounter order (capitalizedFirst=" + capitalizedFirst + ")");
        }
    }

    @Test void differentSpeciesWithinAGenusStillDisagree() {
        MarkedIndividual mi = individualWithEncounters(encounter("Delphinus", "delphis"),
            encounter("Delphinus", "capensis"));

        assertNull(mi.setTaxonomyFromEncounters(),
            "same genus, different species is a real disagreement");
    }

    @Test void subspeciesIsNotTheSameIdentificationAsTheSpecies() {
        MarkedIndividual mi = individualWithEncounters(encounter("Canis", "lupus"),
            encounter("Canis", "lupus familiaris"));

        assertNull(mi.setTaxonomyFromEncounters(),
            "a subspecies is a different identification from the species, not a refinement of it");
    }

    @Test void forceDoesNotClearAnExistingTaxonomy() {
        MarkedIndividual mi = individualWithEncounters(encounter(null, null));

        mi.setTaxonomyString("Orcinus orca");
        assertEquals("Orcinus orca", mi.setTaxonomyFromEncounters(true),
            "recomputing with nothing derivable must leave the existing taxonomy alone");
    }

    @Test void forceRederivesFromEncounters() {
        MarkedIndividual mi = individualWithEncounters(encounter("Balaenoptera", "musculus"));

        mi.setTaxonomyString("Orcinus orca");
        assertEquals("Balaenoptera musculus", mi.setTaxonomyFromEncounters(true),
            "force is how importers re-derive taxonomy after adding encounters");
    }

    @Test void distinctTaxonomiesReportsEveryValueForConflictReporting() {
        Vector<Encounter> encs = new Vector<Encounter>(Arrays.asList(
            encounter("Balaenoptera", "musculus"), encounter("Orcinus", "orca"),
            encounter("Balaenoptera", "musculus"), encounter("", "")));

        assertEquals(Arrays.asList("Balaenoptera musculus", "Orcinus orca"),
            new ArrayList<String>(MarkedIndividual.distinctTaxonomiesFromEncounters(encs)),
            "the backfill reports which taxonomies an individual's encounters disagree on");
    }

    @Test void encounterCollectionsAreToleratedEmptyOrNull() {
        assertTrue(MarkedIndividual.distinctTaxonomiesFromEncounters(null).isEmpty(),
            "null encounter collection must not throw");
        assertNull(MarkedIndividual.unanimousTaxonomyFromEncounters(new Vector<Encounter>()),
            "an individual with no encounters has nothing to inherit");
    }

    @Test void storedPlaceholdersCountAsHavingNoTaxonomy() {
        MarkedIndividual mi = individualWithEncounters();

        assertTrue(mi.hasNoTaxonomyOfItsOwn(), "the empty-string field default is not a taxonomy");
        mi.setGenus(" unknown ");
        assertTrue(mi.hasNoTaxonomyOfItsOwn(),
            "nor is a padded placeholder, which Util.stringExists() accepts because it compares"
            + " 'unknown' before trimming");
        mi.setGenus("\t");
        assertTrue(mi.hasNoTaxonomyOfItsOwn(), "nor is whitespace");
        mi.setGenus("Delphinus");
        assertFalse(mi.hasNoTaxonomyOfItsOwn(), "a genus on its own is a taxonomy");
    }

    @Test void backfillWalksTheTableByCursor() {
        String sql = MarkedIndividual.backfillTaxonomySql(37);

        assertTrue(sql.contains("\"INDIVIDUALID\" > ?"),
            "the candidate query must resume from a cursor, or individuals it cannot fix would pin"
            + " every run to the same rows: " + sql);
        assertTrue(sql.contains("ORDER BY \"INDIVIDUALID\""),
            "cursor paging needs the matching order: " + sql);
        assertTrue(sql.endsWith("LIMIT 37"), "the batch size must bound the run: " + sql);
        for (String placeholder : new String[] { "''", "'none'", "'unknown'" }) {
            assertTrue(sql.contains(placeholder),
                "individuals holding " + placeholder +
                " display as blank, so they are candidates too: " + sql);
        }
        // A row this query misses can never be fixed, so it must not be narrower than
        // hasNoTaxonomyOfItsOwn(): btrim() would leave the tabs String.trim() removes. The regex
        // is bound as a parameter, not written into the SQL, because DataNucleus misparses the
        // POSIX classes' colons as named parameters (and a literal backslash pattern would be
        // eaten on a connection with standard_conforming_strings off).
        assertTrue(sql.contains("regexp_replace(\"GENUS\", ?") &&
            sql.contains("regexp_replace(\"SPECIFICEPITHET\", ?"),
            "the query must treat as blank everything hasNoTaxonomyOfItsOwn() does: " + sql);
        assertEquals("[[:space:][:cntrl:]]", MarkedIndividual.TAXONOMY_BACKFILL_BLANK_REGEX,
            "the bound pattern strips every space and control character");
        assertFalse(sql.contains("\\"), "the SQL must not depend on backslash escaping: " + sql);
    }

    @Test void backfillBatchSizeIsBounded() {
        assertEquals(100, MarkedIndividual.backfillBatchSize(0), "a bad batch size falls back");
        assertEquals(37, MarkedIndividual.backfillBatchSize(37), "a sane batch size is honored");
        assertEquals(1000, MarkedIndividual.backfillBatchSize(100000),
            "one run must not open an unbounded transaction or flood the indexing queue");
    }
}
