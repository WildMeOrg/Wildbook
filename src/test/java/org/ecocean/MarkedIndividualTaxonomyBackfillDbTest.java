package org.ecocean;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the taxonomy backfill's candidate query through real DataNucleus against real Postgres.
 * The mock-based sibling (MarkedIndividualTaxonomyBackfillTest) can never catch what this pins:
 * DataNucleus parses the SQL *text* at execute time -- it once misread the POSIX classes'
 * :space/:cntrl in a regex literal as named parameters and threw JDOUserException before the
 * query ever reached Postgres, so every run of individualTaxonomyBackfill.jsp failed.
 */
@Testcontainers
class MarkedIndividualTaxonomyBackfillDbTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static String blankId;
    static String paddedId;
    static String noSourceId;
    static String identifiedId;

    @BeforeAll
    static void setUp() throws Exception {
        CommonConfiguration.initialize("context0", new Properties());

        Properties props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        TestPMFUtil.closePMF("context0");

        Shepherd sh = new Shepherd("context0", props);
        try {
            sh.beginDBTransaction();

            // the #1113 zombie: taxonomy fields hold "" while the encounter knows the species
            Encounter encBlank = encounter(sh, "Delphinus", "delphis");
            blankId = storeIndividual(sh, "BlankIndividual", encBlank, "", "");

            // a padded placeholder only the whitespace/control-stripping regex sees as blank
            Encounter encPadded = encounter(sh, "Orcinus", "orca");
            paddedId = storeIndividual(sh, "PaddedIndividual", encPadded, " Unknown\t", "none");

            // blank with nothing to inherit: stays a candidate but yields no update
            Encounter encEmpty = encounter(sh, null, null);
            noSourceId = storeIndividual(sh, "NoSourceIndividual", encEmpty, null, null);

            // a real identification must never be offered to the backfill
            Encounter encReal = encounter(sh, "Rhincodon", "typus");
            identifiedId = storeIndividual(sh, "IdentifiedIndividual", encReal, "Rhincodon",
                "typus");

            sh.commitDBTransaction();
        } catch (Exception e) {
            sh.rollbackDBTransaction();
            throw e;
        } finally {
            sh.closeDBTransaction();
        }
    }

    @AfterAll
    static void tearDown() {
        TestPMFUtil.closePMF("context0");
    }

    private static Encounter encounter(Shepherd sh, String genus, String specificEpithet) {
        Encounter enc = new Encounter();

        // keep postStore from enqueueing IndexingManager work: its worker PMs hold open
        // transactions that make tearDown's closePMF fail and leak this class's PMF (pointing at
        // a stopped container) into whatever Testcontainers class runs next
        enc.setSkipAutoIndexing(true);
        enc.setGenus(genus);
        enc.setSpecificEpithet(specificEpithet);
        sh.storeNewEncounter(enc);
        return enc;
    }

    // the constructor inherits the encounter's taxonomy, so overwrite afterwards to recreate the
    // legacy rows the backfill exists for
    private static String storeIndividual(Shepherd sh, String name, Encounter enc, String genus,
        String specificEpithet) {
        MarkedIndividual indiv = new MarkedIndividual(name, enc);

        indiv.setSkipAutoIndexing(true);
        indiv.setGenus(genus);
        indiv.setSpecificEpithet(specificEpithet);
        sh.storeNewMarkedIndividual(indiv);
        return indiv.getId();
    }

    @Test void candidateQueryExecutesAndSelectsExactlyTheBlankRows() {
        Shepherd sh = new Shepherd("context0");

        sh.setAction("MarkedIndividualTaxonomyBackfillDbTest");
        sh.beginDBTransaction();
        try {
            // dry run with an explicit cursor: exercises the real query without touching
            // SystemValue, and throws JDOUserException here if DataNucleus misparses the SQL
            JSONObject res = MarkedIndividual.backfillTaxonomyFromEncounters(sh, "", 100, false);

            assertEquals(3, res.getInt("_examined"),
                "the two blank rows and the no-source row are candidates; the identified row is not");
            JSONObject updates = res.getJSONObject("updates");
            assertEquals("Delphinus delphis", updates.getString(blankId),
                "the empty-string zombie inherits its encounter's taxonomy");
            assertEquals("Orcinus orca", updates.getString(paddedId),
                "the padded placeholder counts as blank and inherits too");
            assertFalse(updates.has(identifiedId),
                "an individual with a real taxonomy is left alone");
            assertEquals(1, res.getInt("_noTaxonomyOnEncounters"),
                "the blank individual with a taxonomy-less encounter has nothing to inherit");
        } finally {
            sh.rollbackDBTransaction();
            sh.closeDBTransaction();
        }
    }
}
