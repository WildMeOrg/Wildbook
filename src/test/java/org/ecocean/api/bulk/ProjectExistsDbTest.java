package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.ecocean.CommonConfiguration;
import org.ecocean.Project;
import org.ecocean.api.bulk.BulkImportUtil;
import org.ecocean.api.bulk.BulkValidator;
import org.ecocean.api.bulk.GroupCollapse;
import org.ecocean.api.bulk.GroupedRows;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * BulkImportUtil.projectExists through real DataNucleus and Postgres (#1789): the grouped-row
 * project check runs in its own transaction, so neither an aborted request transaction nor a
 * failed lookup affects the other. The last test drops the project table, so it runs last.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ProjectExistsDbTest {
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
        Shepherd sh = new Shepherd("context0", props);
        try {
            sh.storeNewProject(new Project("FOO-", "Foo project"));
        } finally {
            sh.closeDBTransaction();
        }
    }

    @AfterAll
    static void tearDown() {
        TestPMFUtil.closePMF("context0");
    }

    // the request's transaction aborted by a failed statement
    private static void abortTransaction(Shepherd sh) {
        assertThrows(Exception.class, () -> sh.getPM().newQuery("javax.jdo.query.SQL",
            "SELECT * FROM no_such_table_1789").execute());
        assertThrows(Exception.class, () -> sh.getPM().newQuery("javax.jdo.query.SQL",
            "SELECT 1").execute(), "Postgres refuses every statement in an aborted transaction");
    }

    // the request's transaction still answers a statement
    private static void assertUsable(Shepherd sh) {
        assertDoesNotThrow(() -> sh.getPM().newQuery("javax.jdo.query.SQL", "SELECT 1").execute());
    }

    @Test @Order(1) void lookupRunsOutsideTheRequestTransaction() {
        Shepherd request = new Shepherd("context0", props);
        request.beginDBTransaction();
        try {
            abortTransaction(request);
            assertTrue(BulkImportUtil.projectExists("FOO-", request));
            assertTrue(BulkImportUtil.projectExists("fo", request), "the fuzzy fallback");
            assertFalse(BulkImportUtil.projectExists("BAR-", request));
        } finally {
            request.rollbackAndClose();
        }
    }

    @Test @Order(2) void failingLookupLeavesTheRequestTransactionUsable() throws Exception {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
            Statement st = c.createStatement()) {
            List<String> tables = new ArrayList<String>();
            try (ResultSet rs = st.executeQuery("SELECT table_name FROM " +
                "information_schema.tables WHERE lower(table_name) = 'project'")) {
                while (rs.next()) tables.add(rs.getString(1));
            }
            assertEquals(1, tables.size(), tables.toString());
            st.execute("DROP TABLE \"" + tables.get(0) + "\" CASCADE");
        }
        Shepherd request = new Shepherd("context0", props);
        request.beginDBTransaction();
        try {
            assertUsable(request);
            assertThrows(RuntimeException.class, () -> BulkImportUtil.projectExists("P", request));
            assertUsable(request);
            // a grouped row with an ownerless project: the failed lookup is a diagnostic on the
            // prefix, and the rest of validation (taxonomy reads the request's transaction) runs
            JSONObject row = new JSONObject();
            row.put(GroupCollapse.GROUP_KEY, "G");
            row.put("Encounter.genus", "Genus");
            row.put("Encounter.specificEpithet", "species");
            row.put("Encounter.year", 2020);
            row.put("Encounter.mediaAsset0", "a.jpg");
            row.put("Encounter.project0.projectIdPrefix", "P");
            row.put("Encounter.project0.researchProjectName", "Proj");
            GroupedRows g = assertDoesNotThrow(() -> GroupCollapse.collapse(List.of(row), request,
                false, BulkValidator.MAX_INDEX + 1));
            assertEquals(1, g.issues().size(), g.issues().toString());
            assertEquals("Encounter.project0.projectIdPrefix", g.issues().get(0).field);
            assertEquals("Could not check whether this project exists",
                g.issues().get(0).error.getMessage());
            assertUsable(request);
        } finally {
            request.rollbackAndClose();
        }
    }
}
