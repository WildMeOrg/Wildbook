package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.Principal;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Status-code tests for GET /api/v3/individuals/info/social-data.
 *
 * Uses Testcontainers for real Postgres + real ACL evaluation; Shepherd is
 * intercepted via MockedConstruction so that direct doGet() works without
 * a live Wildbook deployment, while all ACL-path methods delegate to a real
 * Shepherd backed by the test container.
 */
@Testcontainers
class MarkedIndividualInfoTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("wildbook_test")
            .withUsername("wildbook")
            .withPassword("wildbook");

    static Properties props;
    static String seededIndivId;
    static User seededOwner;
    static String seededOwnerUsername;
    static User stranger;
    static String strangerUsername;

    @BeforeAll
    static void setUp() throws Exception {
        Properties cc = new Properties();
        cc.setProperty("collaborationSecurityEnabled", "true");
        CommonConfiguration.initialize("context0", cc);

        props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
        TestPMFUtil.closePMF("context0");

        // Seed data: owner user, an encounter they own, and an individual.
        // Also seed a stranger user with no access.
        Shepherd sh = new Shepherd("context0", props);
        try {
            sh.beginDBTransaction();

            // Create owner user
            seededOwnerUsername = "owner_user";
            String salt = ServletUtilities.getSalt().toHex();
            String hashed = ServletUtilities.hashAndSaltPassword("password", salt);
            seededOwner = new User(seededOwnerUsername, hashed, salt);
            sh.getPM().makePersistent(seededOwner);
            // Assign researcher role
            Role ownerRole = new Role(seededOwnerUsername, "researcher");
            sh.getPM().makePersistent(ownerRole);

            // Create stranger user (no collaboration with owner)
            strangerUsername = "stranger_user";
            String salt2 = ServletUtilities.getSalt().toHex();
            String hashed2 = ServletUtilities.hashAndSaltPassword("password", salt2);
            stranger = new User(strangerUsername, hashed2, salt2);
            sh.getPM().makePersistent(stranger);
            Role strangerRole = new Role(strangerUsername, "researcher");
            sh.getPM().makePersistent(strangerRole);

            // Create encounter owned by owner
            Encounter enc = new Encounter();
            enc.setSubmitterID(seededOwnerUsername);
            sh.storeNewEncounter(enc);

            // Create individual with that encounter
            MarkedIndividual indiv = new MarkedIndividual("TestIndividual", enc);
            sh.storeNewMarkedIndividual(indiv);
            seededIndivId = indiv.getId();

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

    /**
     * Invokes doGet with a mocked request/response pair. The Shepherd
     * constructed inside doGet is intercepted via MockedConstruction so that
     * direct doGet() works without a live Wildbook deployment. ACL-relevant
     * methods are pre-fetched from a real Shepherd backed by the test container
     * BEFORE entering MockedConstruction (to avoid recursive interception of
     * inner {@code new Shepherd(...)} calls), then returned as pre-loaded values.
     * This drives real ACL evaluation on real domain objects (canUserView runs
     * against the real MarkedIndividual + Encounter data).
     *
     * @param authUser the User that should appear authenticated (null = anonymous)
     * @param idParam  the ?id= query parameter value
     */
    static JSONObject invoke(User authUser, String idParam) throws Exception {
        // Determine the username from the authUser object. We use seededOwnerUsername /
        // strangerUsername (stored before JDO PM close) when possible to avoid JDO
        // detached-field access issues on the closed-PM User objects.
        final String lookupUsername;
        if (authUser == null) {
            lookupUsername = null;
        } else if (authUser == seededOwner) {
            lookupUsername = seededOwnerUsername;
        } else if (authUser == stranger) {
            lookupUsername = strangerUsername;
        } else {
            lookupUsername = authUser.getUsername();
        }

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        when(req.getRequestURI()).thenReturn("/api/v3/individuals/info/social-data");
        when(req.getParameter("id")).thenReturn(idParam);
        // getContext() falls back to "context0" when servletContext is null
        when(req.getServletContext()).thenReturn(null);
        when(req.getContextPath()).thenReturn("");
        when(req.getCookies()).thenReturn(null);
        when(req.getServerName()).thenReturn("localhost");
        when(req.getParameter("context")).thenReturn(null);

        // Wire getUserPrincipal so that Shepherd.getUsername(request) resolves correctly.
        // We use the pre-resolved lookupUsername to avoid JDO detachment issues.
        if (authUser != null && lookupUsername != null) {
            Principal principal = mock(Principal.class);
            when(principal.toString()).thenReturn(lookupUsername);
            when(req.getUserPrincipal()).thenReturn(principal);
        } else {
            when(req.getUserPrincipal()).thenReturn(null);
        }

        // Open a real Shepherd BEFORE MockedConstruction and keep it open for the duration
        // of doGet() so that JDO-managed objects (MarkedIndividual.encounters, Encounter fields)
        // are still accessible when canUserView() traverses them.
        // We must NOT call new Shepherd() inside the MockedConstruction configurator lambda
        // because MockedConstruction intercepts ALL new Shepherd() calls in the JVM.
        final Shepherd backingShepherd = new Shepherd("context0", props);
        backingShepherd.beginDBTransaction();
        try {
            final User resolvedUser = (lookupUsername == null) ? null
                : backingShepherd.getUser(lookupUsername);
            final java.util.List<Role> resolvedRoles =
                (resolvedUser == null) ? java.util.Collections.emptyList()
                : backingShepherd.getAllRolesForUserInContext(lookupUsername, "context0");

            // Intercept new Shepherd("context0") so doGet can run without the real app DB.
            // All ACL-relevant methods delegate to the open backingShepherd.
            try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                    (mock, ctx) -> {
                        // no-op lifecycle (backingShepherd manages the real transaction)
                        doNothing().when(mock).setAction(anyString());
                        doNothing().when(mock).beginDBTransaction();
                        doNothing().when(mock).rollbackDBTransaction();
                        doNothing().when(mock).closeDBTransaction();
                        when(mock.getContext()).thenReturn("context0");

                        // Return user from the open backing shepherd
                        when(mock.getUser(any(HttpServletRequest.class))).thenReturn(resolvedUser);

                        // Delegate individual lookup to the open backing shepherd
                        // (JDO objects are live because backingShepherd's PM is still open)
                        when(mock.getMarkedIndividual(anyString())).thenAnswer(inv -> {
                            String id = (String) inv.getArgument(0);
                            if (id == null) return null;
                            return backingShepherd.getMarkedIndividual(id.trim());
                        });

                        // Return pre-fetched roles (drives user.isAdmin(shepherd))
                        when(mock.getAllRolesForUserInContext(anyString(), anyString()))
                            .thenReturn(resolvedRoles);

                        // Expose the backing PM so Collaboration.collaborationBetweenUsers()
                        // (called via Encounter.canUserAccess -> canCollaborate) can run
                        // its JDO queries without a null PM.
                        when(mock.getPM()).thenReturn(backingShepherd.getPM());

                        // Delegate doesUserHaveRole (used inside collaborationBetweenUsers)
                        when(mock.doesUserHaveRole(anyString(), anyString(), anyString()))
                            .thenAnswer(inv -> backingShepherd.doesUserHaveRole(
                                inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
                    })) {

                new MarkedIndividualInfo().doGet(req, resp);
            }
        } finally {
            backingShepherd.rollbackDBTransaction();
            backingShepherd.closeDBTransaction();
        }

        String body = out.toString();
        assertFalse(body.isEmpty(), "Response body must not be empty");
        return new JSONObject(body);
    }

    @Test
    void anonymousIs401() throws Exception {
        JSONObject j = invoke(null, "any-id");
        assertEquals(401, j.optInt("statusCode", -1),
            "Anonymous request must return 401; got: " + j);
    }

    @Test
    void blankIdIs400() throws Exception {
        JSONObject j = invoke(seededOwner, "");
        assertEquals(400, j.optInt("statusCode", -1),
            "Blank id must return 400; got: " + j);
    }

    @Test
    void unknownIdIs404() throws Exception {
        JSONObject j = invoke(seededOwner, "00000000-0000-0000-0000-000000000000");
        assertEquals(404, j.optInt("statusCode", -1),
            "Unknown id must return 404; got: " + j);
    }

    @Test
    void notViewableIs403() throws Exception {
        // stranger has no collaboration with owner, so cannot view the seeded individual
        JSONObject j = invoke(stranger, seededIndivId);
        assertEquals(403, j.optInt("statusCode", -1),
            "Non-viewable individual must return 403; got: " + j);
    }

    @Test
    void viewableReturns200WithArrays() throws Exception {
        JSONObject j = invoke(seededOwner, seededIndivId);
        assertEquals(200, j.optInt("statusCode", -1),
            "Owner must get 200; got: " + j);
        assertTrue(j.has("encounters"), "Response must have 'encounters' array");
        assertTrue(j.has("relationships"), "Response must have 'relationships' array");
    }
}
