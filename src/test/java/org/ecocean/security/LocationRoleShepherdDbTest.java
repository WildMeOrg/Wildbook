package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.ecocean.CommonConfiguration;
import org.ecocean.Role;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The location-role lookups run parameterized JDOQL (":names.contains(this.rolename)") through
 * real DataNucleus 5.2.7 against real Postgres. Mocks cannot prove the binding, the distinct
 * projection, apostrophe handling, or that a Role stored with a NULL context never matches a
 * context query (the same rule ShepherdRealm applies for Shiro).
 */
@Testcontainers
class LocationRoleShepherdDbTest {
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
            sh.beginDBTransaction();
            role(sh, "bob", "Indonesia", "context0");
            role(sh, "bob", "O'Brien Bay", "context0"); // apostrophe must bind, not break
            role(sh, "bob", "Komodo", "otherctx"); // right name, wrong context
            role(sh, "amy", "Flores Sea", "context0");
            role(sh, "nullctx", "Indonesia", null); // stored NULL context never matches
            role(sh, "sys", "admin", "context0");
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

    private static void role(Shepherd sh, String username, String rolename, String context) {
        Role r = new Role(username, rolename);
        r.setContext(context);
        sh.getPM().makePersistent(r);
    }

    private static Shepherd open() {
        Shepherd sh = new Shepherd("context0");
        sh.setAction("LocationRoleShepherdDbTest");
        sh.beginDBTransaction();
        return sh;
    }

    @Test void doesUserHaveAnyRole_matchesOneOfSeveralNamesInContext() {
        Shepherd sh = open();
        try {
            assertTrue(sh.doesUserHaveAnyRole("bob",
                Arrays.asList("Komodo", "Flores Sea", "Indonesia"), "context0"),
                "bob holds Indonesia in context0");
            assertTrue(sh.doesUserHaveAnyRole("amy", Arrays.asList("Flores Sea"), "context0"));
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void doesUserHaveAnyRole_ignoresRolesFromAnotherContext() {
        Shepherd sh = open();
        try {
            assertFalse(sh.doesUserHaveAnyRole("bob", Arrays.asList("Komodo"), "context0"),
                "bob's Komodo role lives in otherctx");
            assertTrue(sh.doesUserHaveAnyRole("bob", Arrays.asList("Komodo"), "otherctx"));
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void doesUserHaveAnyRole_bindsApostrophes() {
        Shepherd sh = open();
        try {
            assertTrue(sh.doesUserHaveAnyRole("bob", Arrays.asList("O'Brien Bay"), "context0"));
            assertFalse(sh.doesUserHaveAnyRole("amy", Arrays.asList("O'Brien Bay"), "context0"));
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void doesUserHaveAnyRole_storedNullContextNeverMatches() {
        Shepherd sh = open();
        try {
            assertFalse(sh.doesUserHaveAnyRole("nullctx", Arrays.asList("Indonesia"), "context0"),
                "a Role row with NULL context must not satisfy a context0 query (Shiro parity)");
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void doesUserHaveAnyRole_emptyOrNullInputsAreFalseWithoutQuerying() {
        Shepherd sh = open();
        try {
            assertFalse(sh.doesUserHaveAnyRole("bob", Collections.<String>emptyList(), "context0"));
            assertFalse(sh.doesUserHaveAnyRole("bob", null, "context0"));
            assertFalse(sh.doesUserHaveAnyRole(null, Arrays.asList("Indonesia"), "context0"));
            assertFalse(sh.doesUserHaveAnyRole("bob", Arrays.asList("Indonesia"), null));
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void getUsernamesWithAnyRole_distinctUsernamesInContextOnly() {
        Shepherd sh = open();
        try {
            List<String> names = sh.getUsernamesWithAnyRole(
                Arrays.asList("Indonesia", "Flores Sea", "Indonesia", "Komodo"), "context0");
            assertEquals(new HashSet<String>(Arrays.asList("bob", "amy")),
                new HashSet<String>(names), "bob via Indonesia, amy via Flores Sea; nullctx and otherctx excluded");
            assertEquals(2, names.size(), "distinct: duplicate names must not duplicate users");
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void getUsernamesWithAnyRole_emptyInputsYieldEmpty() {
        Shepherd sh = open();
        try {
            assertTrue(sh.getUsernamesWithAnyRole(Collections.<String>emptyList(), "context0").isEmpty());
            assertTrue(sh.getUsernamesWithAnyRole(null, "context0").isEmpty());
            assertTrue(sh.getUsernamesWithAnyRole(Arrays.asList("Indonesia"), null).isEmpty());
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void getRolesInContext_returnsOnlyThatContextsRows() {
        Shepherd sh = open();
        try {
            List<Role> roles = sh.getRolesInContext("context0");
            Set<String> pairs = new HashSet<String>();
            for (Role r : roles) pairs.add(r.getUsername() + "/" + r.getRolename());
            assertEquals(new HashSet<String>(Arrays.asList("bob/Indonesia", "bob/O'Brien Bay",
                "amy/Flores Sea", "sys/admin")), pairs,
                "otherctx and NULL-context rows are excluded");
            assertTrue(sh.getRolesInContext("nowhere").isEmpty());
            assertTrue(sh.getRolesInContext(null).isEmpty());
        } finally {
            sh.rollbackAndClose();
        }
    }
}
