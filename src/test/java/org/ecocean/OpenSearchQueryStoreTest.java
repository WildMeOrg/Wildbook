package org.ecocean;

import java.util.Properties;

import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durability contract for stored search queries (issue #1500): OpenSearch.queryStore must
 * persist through the real DB path (confirmed commit + re-begun caller transaction) and
 * OpenSearch.queryLoad must read the merged stored shape back from a separate Shepherd.
 */
@Testcontainers public class OpenSearchQueryStoreTest {

    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    @BeforeAll static void setUp() {
        Properties commonConfiguration = new Properties();
        commonConfiguration.setProperty("htmlTitle", "Unit Test");
        CommonConfiguration.initialize("context0", commonConfiguration);
        // evict any PMF a previous test class bound to ITS OWN (now stopped) container
        org.ecocean.shepherd.core.TestPMFUtil.closePMF("context0");
    }

    @AfterAll static void tearDown() {
        // our container stops with this class; don't leave a dead PMF for later classes
        org.ecocean.shepherd.core.TestPMFUtil.closePMF("context0");
    }

    private static Properties dnProperties() {
        Properties properties = new Properties();

        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateTables", "true");
        return properties;
    }

    @Test void queryStoreRoundtripAndPrune() {
        // seed a real user (queryStore reads getUUID())
        Shepherd seeder = new Shepherd("context0", dnProperties());

        seeder.setAction("OpenSearchQueryStoreTest.seed");
        String creatorUuid;
        seeder.beginDBTransaction();
        try {
            String salt = ServletUtilities.getSalt().toHex();
            User user = new User("osqtester",
                ServletUtilities.hashAndSaltPassword("password123", salt), salt);
            seeder.getPM().makePersistent(user);
            creatorUuid = user.getUUID(); // capture while attached
            seeder.commitDBTransaction();
        } finally {
            seeder.rollbackAndClose();
        }
        assertNotNull(creatorUuid, "seeded user has a uuid");

        // store through the REAL path: persist + confirmed commit + re-begin
        Shepherd writer = new Shepherd("context0", dnProperties());
        writer.setAction("OpenSearchQueryStoreTest.write");
        writer.beginDBTransaction();
        String id;
        try {
            JSONObject query = new JSONObject().put("query",
                new JSONObject().put("match_all", new JSONObject()));
            User writerUser = writer.getUser("osqtester");
            id = OpenSearch.queryStore(query, "encounter", writerUser, writer);
            assertNotNull(id, "queryStore returns id after confirmed commit");
            assertTrue(writer.isDBTransactionActive(),
                "queryStore re-begins the caller transaction");
        } finally {
            writer.rollbackAndClose();
        }

        // load from a SEPARATE Shepherd: durability + merged stored shape
        Shepherd reader = new Shepherd("context0", dnProperties());
        reader.setAction("OpenSearchQueryStoreTest.read");
        reader.beginDBTransaction();
        try {
            JSONObject loaded = OpenSearch.queryLoad(id, reader);
            assertNotNull(loaded, "stored query loads back from a different Shepherd");
            assertEquals("encounter", loaded.optString("indexName"), "indexName survives");
            assertEquals(creatorUuid, loaded.optString("creator"), "creator survives");
            assertEquals(id, loaded.optString("id"), "id embedded in stored doc");
            assertNotNull(loaded.optJSONObject("query"), "query body survives");
            assertNull(OpenSearch.queryLoad(Util.generateUUID(), reader),
                "unknown id loads null");
        } finally {
            reader.rollbackAndClose();
        }

        // prune: older-than-cutoff rows deleted, newer kept
        Shepherd pruner = new Shepherd("context0", dnProperties());
        pruner.setAction("OpenSearchQueryStoreTest.prune");
        pruner.beginDBTransaction();
        try {
            pruner.getPM().makePersistent(new OpenSearchQuery("old-stored-query",
                new JSONObject().put("query", new JSONObject()), 1000L));
            pruner.commitDBTransaction();
            pruner.beginDBTransaction();
            long deleted = OpenSearch.queryPrune(pruner, 2000L);
            pruner.commitDBTransaction();
            pruner.beginDBTransaction();
            assertEquals(1, deleted, "exactly the old row pruned");
            assertNull(OpenSearchQuery.load(pruner, "old-stored-query"), "old row pruned");
            assertNotNull(OpenSearchQuery.load(pruner, id), "fresh row kept");
        } finally {
            pruner.rollbackAndClose();
        }
    }
}
