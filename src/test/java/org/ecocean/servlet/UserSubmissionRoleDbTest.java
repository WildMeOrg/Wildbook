package org.ecocean.servlet;

import java.util.Properties;
import java.util.UUID;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class UserSubmissionRoleDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Test void persistedUsernameOwnershipAndPriorRoleCleanup() {
        TestPMFUtil.closePMF("context0");
        Properties properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateAll", "true");
        Shepherd sh = new Shepherd("context0", properties);
        try {
            sh.beginDBTransaction();
            String id = UUID.randomUUID().toString();
            User user = new User("test@example.invalid", id); user.setUsername("current");
            sh.getPM().makePersistent(user);
            Role priorAdmin = new Role("unused", "admin"); priorAdmin.setContext("context0");
            Role priorImport = new Role("unused", Role.API_SUBMISSION); priorImport.setContext("context0");
            sh.getPM().makePersistent(priorAdmin); sh.getPM().makePersistent(priorImport);
            assertTrue(sh.commitDBTransactionWithStatus());
            sh.beginDBTransaction();
            assertTrue(UserCreate.usernameAvailable(sh, "current", id));
            assertFalse(UserCreate.usernameAvailable(sh, "current", "another-account"));
            assertTrue(UserCreate.usernameAvailable(sh, "unused", id));
            UserCreate.clearUnownedRoles(sh, "unused");
            assertTrue(sh.commitDBTransactionWithStatus());
            sh.beginDBTransaction();
            assertTrue(sh.getAllRolesForUser("unused").isEmpty());
        } finally {
            sh.rollbackAndClose();
            TestPMFUtil.closePMF("context0");
        }
    }
}
