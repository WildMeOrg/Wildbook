package org.ecocean.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.ecocean.Organization;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Upgrading an install whose SERVICE_KEY table predates READASCREATOR: autoCreateAll adds the column
 * and existing keys load as unbound.
 */
@Testcontainers
class ServiceKeyReadAsUpgradeDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @AfterAll static void closePmf() { TestPMFUtil.closePMF("context0"); }

    private static Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateAll", "true");
        return properties;
    }

    private static Connection jdbc() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static boolean hasColumn() throws Exception {
        try (Connection c = jdbc(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM information_schema.columns WHERE "
                 + "upper(table_name) = 'SERVICE_KEY' AND upper(column_name) = 'READASCREATOR'")) {
            return rs.next();
        }
    }

    @Test void legacyTableGainsColumnAndKeysStayUnbound() throws Exception {
        TestPMFUtil.closePMF("context0");
        String keyId;
        Shepherd sh = new Shepherd("context0", properties());
        sh.beginDBTransaction();
        try {
            Organization org = new Organization("Upgrade org");
            sh.getPM().makePersistent(org);
            User creator = new User(null, UUID.randomUUID().toString());
            creator.setUsername("creator-" + UUID.randomUUID().toString().substring(0, 8));
            sh.getPM().makePersistent(creator);
            assertTrue(sh.commitDBTransactionWithStatus(), "fixture committed");
            sh.beginDBTransaction();
            keyId = ServiceKeyStore.create(sh, org, "legacy", Set.of("data:read"), 30, creator.getId()).keyId;
            assertTrue(sh.commitDBTransactionWithStatus(), "key committed");
        } finally { sh.closeDBTransaction(); }
        TestPMFUtil.closePMF("context0");

        // make it look like a table created before this change
        try (Connection c = jdbc(); Statement st = c.createStatement()) {
            st.executeUpdate("ALTER TABLE \"SERVICE_KEY\" DROP COLUMN \"READASCREATOR\"");
        }
        assertFalse(hasColumn(), "legacy table has no READASCREATOR");

        Shepherd upgraded = new Shepherd("context0", properties());
        upgraded.beginDBTransaction();
        try {
            ServiceKey key = FreshObjects.load(upgraded.getPM(), ServiceKey.class, "id == :id", keyId);
            assertNotNull(key, "legacy key loads after the upgrade");
            assertFalse(key.isReadAsCreator(), "legacy keys are unbound");
        } finally { upgraded.rollbackAndClose(); }
        assertTrue(hasColumn(), "autoCreateAll added READASCREATOR");
    }
}
