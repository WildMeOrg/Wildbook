package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Properties;
import org.ecocean.genetics.TissueSample;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shepherd.getTissueSample through real DataNucleus and Postgres: the sample ID and the encounter
 * ID are query parameters, so any text (a double quote included) is just a value.
 */
@Testcontainers
class TissueSampleLookupDbTest {
    static final String ENC = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

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
        sh.beginDBTransaction();
        try {
            sh.getPM().makePersistent(new TissueSample(ENC, "S1"));
            sh.getPM().makePersistent(new TissueSample(ENC, "S\"2"));
            sh.commitDBTransaction();
        } finally {
            sh.closeDBTransaction();
        }
    }

    @AfterAll
    static void tearDown() {
        TestPMFUtil.closePMF("context0");
    }

    @Test void sampleFoundByIdAndEncounter() {
        Shepherd sh = new Shepherd("context0");
        sh.beginDBTransaction();
        try {
            assertEquals("S1", sh.getTissueSample("S1", ENC).getSampleID());
            assertNull(sh.getTissueSample("S1", "other"));
            assertNull(sh.getTissueSample("s1", ENC), "the match is exact");
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void quoteInSampleIdIsJustAValue() {
        Shepherd sh = new Shepherd("context0");
        sh.beginDBTransaction();
        try {
            // a stored ID with a quote is found (the old inline literal could not even parse it)
            assertEquals("S\"2", sh.getTissueSample("S\"2", ENC).getSampleID());
            for (String id : new String[] { "S1\"", "\" || true || \"", "x\\\"" }) {
                assertNull(assertDoesNotThrow(() -> sh.getTissueSample(id, ENC)), id);
                assertNull(assertDoesNotThrow(() -> sh.getTissueSample("S1", id)), id);
            }
        } finally {
            sh.rollbackAndClose();
        }
    }
}
