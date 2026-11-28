package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Properties;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shepherd.getProjectByProjectIdPrefix / getProjectByProjectIdPrefixPrefix through real
 * DataNucleus and Postgres: the prefix is a query parameter (trimmed; the second lookup is a
 * case-insensitive startsWith, ordered by the lowercased prefix), so any text is just a value.
 */
@Testcontainers
class ProjectPrefixLookupDbTest {
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
        try {
            sh.storeNewProject(new Project("FOO-", "Foo project"));
            sh.storeNewProject(new Project("Bar-B", "Bar B"));
            sh.storeNewProject(new Project("bar-a", "Bar A"));
            sh.storeNewProject(new Project("NOAA_X", "Noaa underscore"));
            sh.storeNewProject(new Project("NOAAZX", "Noaa z"));
            sh.storeNewProject(new Project("NOABZQ", "Noab z"));
        } finally {
            sh.closeDBTransaction();
        }
    }

    @AfterAll
    static void tearDown() {
        TestPMFUtil.closePMF("context0");
    }

    @Test void prefixLookupsMatchAsBefore() {
        Shepherd sh = new Shepherd("context0");
        sh.beginDBTransaction();
        try {
            assertEquals("Foo project", sh.getProjectByProjectIdPrefix(" FOO- ").getResearchProjectName());
            assertNull(sh.getProjectByProjectIdPrefix("foo-"), "the exact lookup is case-sensitive");
            assertEquals("FOO-", sh.getProjectByProjectIdPrefixPrefix("fo").getProjectIdPrefix());
            // ordered by the lowercased prefix
            assertEquals("bar-a", sh.getProjectByProjectIdPrefixPrefix(" BAR").getProjectIdPrefix());
            assertNull(sh.getProjectByProjectIdPrefixPrefix(null));
        } finally {
            sh.rollbackDBTransaction();
            sh.closeDBTransaction();
        }
    }

    @Test void likeWildcardsInPrefixAreLiteral() {
        // startsWith on a parameter is a LIKE whose pattern DataNucleus escapes, as it did for the
        // old inline literal: _ and % match only themselves
        Shepherd sh = new Shepherd("context0");
        sh.beginDBTransaction();
        try {
            assertEquals("NOAA_X", sh.getProjectByProjectIdPrefixPrefix("NOAA_").getProjectIdPrefix());
            assertNull(sh.getProjectByProjectIdPrefixPrefix("NOAB_"), "_ is not a one-character wildcard");
            assertNull(sh.getProjectByProjectIdPrefixPrefix("NOAA_Z"), "NOAAZX is not NOAA_Z...");
            assertNull(sh.getProjectByProjectIdPrefixPrefix("NO%Q"), "% is not a wildcard");
            assertNull(sh.getProjectByProjectIdPrefixPrefix("NOAA\\"), "backslash is just a value");
        } finally {
            sh.rollbackDBTransaction();
            sh.closeDBTransaction();
        }
    }

    @Test void quoteInPrefixIsJustAValue() {
        Shepherd sh = new Shepherd("context0");
        sh.beginDBTransaction();
        try {
            for (String prefix : new String[] { "FOO-\"", "\" || true || \"", "x\\\"" }) {
                assertNull(assertDoesNotThrow(() -> sh.getProjectByProjectIdPrefix(prefix)), prefix);
                assertNull(assertDoesNotThrow(() -> sh.getProjectByProjectIdPrefixPrefix(prefix)),
                    prefix);
            }
        } finally {
            sh.rollbackDBTransaction();
            sh.closeDBTransaction();
        }
    }
}
