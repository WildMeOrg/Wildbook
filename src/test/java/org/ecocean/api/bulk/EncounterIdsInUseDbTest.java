package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.api.bulk.BulkImportUtil;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BulkImportUtil.encounterIdsInUse through real DataNucleus and Postgres (#1789): a stored
 * catalogNumber is found case-insensitively from a lookup of its lowercase form, the lookup is
 * chunked, and it runs in its own transaction rather than the request's.
 */
@Testcontainers
class EncounterIdsInUseDbTest {
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static Properties props;

    @BeforeAll
    static void setUp() {
        CommonConfiguration.initialize("context0", new Properties());
        props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        TestPMFUtil.closePMF("context0");
    }

    @AfterAll
    static void tearDown() {
        TestPMFUtil.closePMF("context0");
    }

    private static void store(String id) {
        Shepherd sh = new Shepherd("context0", props);
        try {
            Encounter enc = new Encounter();
            enc.setSkipAutoIndexing(true);
            sh.storeNewEncounter(enc, id);
        } finally {
            sh.closeDBTransaction();
        }
    }

    private static Set<String> lookup(List<String> ids) throws Exception {
        Shepherd sh = new Shepherd("context0", props);
        sh.beginDBTransaction();
        try {
            return BulkImportUtil.encounterIdsInUse(ids, sh);
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void findsUppercaseStoredId() throws Exception {
        String lower = UUID.randomUUID().toString();
        store(lower.toUpperCase());
        assertEquals(Set.of(lower), lookup(List.of(lower, UUID.randomUUID().toString())),
            "a stored uppercase id is found by its lowercase form and returned lowercase");
    }

    @Test void chunksOverThousand() throws Exception {
        String stored = UUID.randomUUID().toString();
        store(stored);
        List<String> ids = new ArrayList<String>();
        for (int i = 0; i < 1000; i++) ids.add(UUID.randomUUID().toString());
        ids.add(stored); // 1001st, so it lands in the second chunk
        assertEquals(Set.of(stored), lookup(ids));
    }

    // the request's transaction aborted by a failed statement
    private static void abortTransaction(Shepherd sh) {
        assertThrows(Exception.class, () -> sh.getPM().newQuery("javax.jdo.query.SQL",
            "SELECT * FROM no_such_table_1789").execute());
        assertThrows(Exception.class, () -> sh.getPM().newQuery("javax.jdo.query.SQL",
            "SELECT 1").execute(), "Postgres refuses every statement in an aborted transaction");
    }

    @Test void lookupRunsOutsideTheRequestTransaction() throws Exception {
        // the lookup never touches the request's transaction: it answers even when that
        // transaction is already aborted, so a failed lookup cannot abort the request's either
        String id = UUID.randomUUID().toString();
        store(id);
        Shepherd request = new Shepherd("context0", props);
        request.beginDBTransaction();
        try {
            abortTransaction(request);
            assertEquals(Set.of(id), BulkImportUtil.encounterIdsInUse(List.of(id), request));
        } finally {
            request.rollbackAndClose();
        }
    }

    @Test void duplicateInputsReturnedOnceAndEmptyInputEmpty() throws Exception {
        String id = UUID.randomUUID().toString();
        store(id);
        assertEquals(Set.of(id), lookup(List.of(id, id)));
        assertEquals(Set.of(), lookup(List.of()));
    }
}
