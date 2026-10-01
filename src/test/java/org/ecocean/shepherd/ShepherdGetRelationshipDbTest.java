package org.ecocean.shepherd;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Properties;
import javax.jdo.JDOHelper;
import org.ecocean.CommonConfiguration;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.ecocean.social.Relationship;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Pins Shepherd.getRelationship(persistenceID) against real DataNucleus + Postgres.
 *
 * Relationship uses JDO datastore identity (a bigint surrogate key), and the social UI
 * (individuals.jsp) submits the FULL identity string -- "1011[OID]org.ecocean.social.Relationship"
 * -- as the persistenceID parameter. The old servlet shorthand
 * pm.getObjectById(Relationship.class, thatString) treated the whole string as the numeric key,
 * so every relationship delete/edit died with NumberFormatException at the bigint bind. A mock
 * test can't catch this: the bug lives in how DataNucleus interprets the id argument.
 */
@Testcontainers
@ResourceLock("wildbook.context0.pmf") // all classes share the static context0 PMF cache
class ShepherdGetRelationshipDbTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static String oidString; // "<n>[OID]org.ecocean.social.Relationship", as individuals.jsp sends
    static String bareKey;   // "<n>"

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
            Relationship rel = new Relationship();
            rel.setType("familial");
            rel.setMarkedIndividualName1("indiv-one");
            rel.setMarkedIndividualName2("indiv-two");
            sh.getPM().makePersistent(rel);
            oidString = JDOHelper.getObjectId(rel).toString();
            sh.commitDBTransaction();
        } catch (Exception e) {
            sh.rollbackDBTransaction();
            throw e;
        } finally {
            sh.closeDBTransaction();
        }
        assertTrue(oidString.endsWith("[OID]org.ecocean.social.Relationship"),
            "datastore identity toString has the [OID] form the JSP reconstructs: " + oidString);
        bareKey = oidString.substring(0, oidString.indexOf("[OID]"));
    }

    @AfterAll
    static void tearDown() {
        TestPMFUtil.closePMF("context0");
    }

    // Returns null if not found, else {type, markedIndividualName1}. Fields are read INSIDE the
    // transaction: after rollback+close the instance is hollow and its fields read back null.
    private String[] lookup(String persistenceID) {
        Shepherd sh = new Shepherd("context0");

        sh.setAction("ShepherdGetRelationshipDbTest");
        sh.beginDBTransaction();
        try {
            Relationship rel = sh.getRelationship(persistenceID);
            if (rel == null) return null;
            return new String[] { rel.getType(), rel.getMarkedIndividualName1() };
        } finally {
            sh.rollbackDBTransaction();
            sh.closeDBTransaction();
        }
    }

    @Test void findsByFullOidStringTheUiSubmits() {
        String[] found = lookup(oidString);

        assertNotNull(found, "the exact persistenceID individuals.jsp posts must resolve");
        assertEquals("familial", found[0]);
        assertEquals("indiv-one", found[1]);
    }

    @Test void findsByBareNumericKey() {
        String[] found = lookup(bareKey);

        assertNotNull(found, "a bare datastore key must also resolve");
        assertEquals("familial", found[0]);
    }

    @Test void returnsNullForMissingRow() {
        assertNull(lookup("999999999[OID]org.ecocean.social.Relationship"),
            "a deleted/unknown relationship returns null instead of throwing");
    }

    @Test void returnsNullForGarbageAndNull() {
        assertNull(lookup("not-a-number"), "garbage input returns null instead of throwing");
        assertNull(lookup(null), "null input returns null");
    }

    @Test void toleratesSurroundingWhitespace() {
        assertNotNull(lookup("  " + oidString + " \t"),
            "a whitespace-padded but otherwise valid id resolves");
        assertNull(lookup("   "), "a whitespace-only id returns null");
    }

    @Test void rejectsOidStringForAnotherClass() {
        assertNull(lookup(bareKey + "[OID]org.ecocean.Encounter"),
            "an [OID] string naming a different class is not accepted as a Relationship key");
    }
}
