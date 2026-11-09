package org.ecocean.shepherd.core;

import java.util.Properties;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.User;
import org.ecocean.security.WildbookTokenAuthenticationFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Shepherd.getUser(request) resolves a token request by its verified UUID subject, not its username. */
@Testcontainers
class ShepherdTokenSubjectDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static Properties properties;

    @BeforeAll static void schema() {
        TestPMFUtil.closePMF("context0");
        properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateAll", "true");
    }

    @AfterAll static void closePmf() { TestPMFUtil.closePMF("context0"); }

    private static String persistUser(String username) {
        Shepherd sh = new Shepherd("context0", properties);
        sh.beginDBTransaction();
        try {
            String id = UUID.randomUUID().toString();
            User u = new User(null, id);
            u.setUsername(username);
            sh.getPM().makePersistent(u);
            assertTrue(sh.commitDBTransactionWithStatus(), "user committed");
            return id;
        } finally { sh.closeDBTransaction(); }
    }

    private static HttpServletRequest request(String principal, Object subject) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        // Shepherd.getUsername reads principal.toString(), as the token filter's principal provides
        java.security.Principal p = (principal == null) ? null : new java.security.Principal() {
            public String getName() { return principal; }
            public String toString() { return principal; }
        };
        when(req.getUserPrincipal()).thenReturn(p);
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_SUBJECT_ATTR)).thenReturn(subject);
        return req;
    }

    private static User resolve(HttpServletRequest req) {
        Shepherd sh = new Shepherd("context0", properties);
        sh.beginDBTransaction();
        try {
            return sh.getUser(req);
        } finally { sh.rollbackAndClose(); }
    }

    @Test void subjectWinsOverPrincipalUsername() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        persistUser("alice-" + suffix);
        String bobId = persistUser("bob-" + suffix);
        // the principal names alice (e.g. bob's old username, since reassigned); the verified subject is bob
        User u = resolve(request("alice-" + suffix, bobId));
        assertNotNull(u, "subject resolves");
        assertEquals(bobId, u.getId(), "request runs as the verified UUID, not the username");
    }

    @Test void unknownSubjectNeverFallsBackToUsername() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        persistUser("carol-" + suffix);
        assertNull(resolve(request("carol-" + suffix, UUID.randomUUID().toString())),
            "a deleted or unknown subject resolves to nobody");
    }

    @Test void malformedSubjectResolvesToNobody() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        persistUser("dave-" + suffix);
        assertNull(resolve(request("dave-" + suffix, "  ")), "blank subject");
        assertNull(resolve(request("dave-" + suffix, Boolean.TRUE)), "non-string subject");
    }

    @Test void subjectIsReadFreshAfterARenameElsewhere() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String frankId = persistUser("frank-" + suffix);
        assertEquals("frank-" + suffix, resolve(request("frank-" + suffix, frankId)).getUsername(),
            "first resolve (warms any cache)");
        // another transaction renames frank and hands his old username to someone else
        Shepherd other = new Shepherd("context0", properties);
        other.beginDBTransaction();
        try {
            other.getUserByUUID(frankId).setUsername("frank-renamed-" + suffix);
            assertTrue(other.commitDBTransactionWithStatus(), "rename committed");
        } finally { other.closeDBTransaction(); }
        persistUser("frank-" + suffix);

        Shepherd live = new Shepherd("context0", properties);
        live.beginDBTransaction();
        try {
            User u = live.getUser(request("frank-" + suffix, frankId));
            assertNotNull(u, "subject resolves after the rename");
            assertEquals(frankId, u.getId(), "still the verified UUID, not the new holder of the old name");
            assertEquals("frank-renamed-" + suffix, u.getUsername(), "fresh state, not a cached copy");
            assertFalse(u.isAdmin(live), "role lookups work on the loaded user inside the transaction");
        } finally { live.rollbackAndClose(); }
    }

    @Test void sessionRequestStillResolvesByUsername() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String erinId = persistUser("erin-" + suffix);
        User u = resolve(request("erin-" + suffix, null));
        assertNotNull(u, "no token subject: username path unchanged");
        assertEquals(erinId, u.getId(), "session user resolved by username");
    }
}
