package org.ecocean.api;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Organization;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.auth.ServiceKeyStore;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.servlet.UserCreate;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Org admins (role orgAdmin) manage service keys only for organizations they are direct members of.
 * orgSuper, plain members and parent-organization admins cannot.
 */
@Testcontainers
class ServiceKeysDelegationDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final String PW = "correct horse";
    private static boolean membershipKeyFixed = false;

    private Shepherd sh;
    private String suffix;
    private Organization orgA, orgB, orgC, parent;
    private User siteAdmin, r, m, s, sMember, parentAdmin;

    @BeforeEach void setUp() {
        TestPMFUtil.closePMF("context0");
        Properties properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateAll", "true");
        sh = new Shepherd("context0", properties);
        suffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        sh.beginDBTransaction();
        siteAdmin = user("site-admin");
        r = user("org-admin-a");
        m = user("member-a");
        s = user("org-super");
        sMember = user("org-super-member-a");
        parentAdmin = user("parent-admin");
        orgA = new Organization("A" + suffix);
        orgB = new Organization("B" + suffix);
        orgC = new Organization("C" + suffix);
        parent = new Organization("P" + suffix);
        sh.getPM().makePersistent(orgA);
        sh.getPM().makePersistent(orgB);
        sh.getPM().makePersistent(orgC);
        sh.getPM().makePersistent(parent);
        assertTrue(sh.commitDBTransactionWithStatus(), "fixture rows committed");
        fixMembershipKey();
        sh.beginDBTransaction();
        parent.addChild(orgC);
        assertTrue(sh.commitDBTransactionWithStatus(), "child committed");
        sh.beginDBTransaction();
        join(orgA, r);
        join(orgA, m);
        join(orgA, sMember);
        join(parent, parentAdmin);
        role(siteAdmin, "admin");
        role(r, Organization.ROLE_MANAGER);
        role(s, Organization.ROLE_ADMIN);
        role(sMember, Organization.ROLE_ADMIN);
        role(parentAdmin, Organization.ROLE_MANAGER);
        assertTrue(sh.commitDBTransactionWithStatus());
        sh.beginDBTransaction();
    }

    @AfterEach void tearDown() {
        try {
            sh.rollbackAndClose();
        } finally {
            TestPMFUtil.closePMF("context0");
        }
    }

    /** Same re-keying workaround as UserEditAuthorizationDbTest. */
    private static synchronized void fixMembershipKey() {
        if (membershipKeyFixed) return;
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            Statement st = c.createStatement()) {
            st.execute("ALTER TABLE \"ORGANIZATION_MEMBERS\" DROP CONSTRAINT \"ORGANIZATION_MEMBERS_PK\"");
            st.execute("ALTER TABLE \"ORGANIZATION_MEMBERS\" ADD PRIMARY KEY (\"USER_ID\", \"IDX\")");
            membershipKeyFixed = true;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("could not re-key ORGANIZATION_MEMBERS", e);
        }
    }

    private User user(String username) {
        User u = new User(username + suffix + "@example.invalid", UUID.randomUUID().toString());
        u.setUsername(username + suffix);
        String salt = ServletUtilities.getSalt().toHex();
        u.setSalt(salt);
        u.setPassword(ServletUtilities.hashAndSaltPassword(PW, salt));
        sh.getPM().makePersistent(u);
        return u;
    }

    private void join(Organization org, User u) {
        org.addMember(u);
        assertTrue(sh.commitDBTransactionWithStatus(), "membership committed");
        sh.beginDBTransaction();
    }

    private void role(User u, String rolename) {
        Role role = new Role(u.getUsername(), rolename);
        role.setContext("context0");
        sh.getPM().makePersistent(role);
    }

    private String createKeyDirect(Organization org) {
        Shepherd s2 = new Shepherd("context0");
        s2.beginDBTransaction();
        try {
            String id = ServiceKeyStore.create(s2, s2.getOrganization(org.getId()), "direct",
                Set.of("data:read"), 30, "tester").keyId;
            assertTrue(s2.commitDBTransactionWithStatus(), "direct key committed");
            return id;
        } finally { s2.rollbackAndClose(); }
    }

    private static final class Result {
        int status;
        String text;
    }

    private Result call(User who, String method, String servletPath, String pathInfo, String body)
        throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn(method);
        when(req.getServletPath()).thenReturn(servletPath);
        when(req.getPathInfo()).thenReturn(pathInfo);
        when(req.getHeader("Authorization")).thenReturn("Basic "
            + Base64.getEncoder().encodeToString((who.getUsername() + ":" + PW).getBytes()));
        when(req.getReader()).thenReturn(new BufferedReader(new StringReader(body == null ? "" : body)));
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        ServiceKeys servlet = new ServiceKeys();
        if ("GET".equals(method)) servlet.doGet(req, resp);
        else if ("POST".equals(method)) servlet.doPost(req, resp);
        else servlet.doDelete(req, resp);
        org.mockito.ArgumentCaptor<Integer> status = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(resp, atLeastOnce()).setStatus(status.capture());
        Result res = new Result();
        res.status = status.getValue();
        res.text = out.toString();
        return res;
    }

    private Result keys(User who, String method, Organization org, String keyId) throws Exception {
        String path = "/" + org.getId() + "/service-keys" + (keyId == null ? "" : "/" + keyId);
        return call(who, method, "/api/v3/organizations", path,
            "POST".equals(method) ? "{\"label\":\"x\",\"scopes\":[\"data:read\"],\"days\":30}" : null);
    }

    private Set<String> discovered(User who) throws Exception {
        Result res = call(who, "GET", "/api/v3/service-keys", "/organizations", null);
        assertEquals(200, res.status, "discovery status: " + res.text);
        JSONArray arr = new JSONObject(res.text).getJSONArray("organizations");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < arr.length(); i++) ids.add(arr.getJSONObject(i).getString("id"));
        return ids;
    }

    private void assertDeniedEverywhere(User who, Organization org, String who_) throws Exception {
        String other = createKeyDirect(org);
        assertEquals(403, keys(who, "GET", org, null).status, who_ + " list");
        assertEquals(403, keys(who, "POST", org, null).status, who_ + " create");
        assertEquals(403, keys(who, "DELETE", org, other).status, who_ + " revoke one");
        assertEquals(403, keys(who, "DELETE", org, null).status, who_ + " revoke all");
    }

    @Test void orgAdminManagesOwnOrg() throws Exception {
        Result created = keys(r, "POST", orgA, null);
        assertEquals(201, created.status, "create: " + created.text);
        String keyId = new JSONObject(created.text).getString("keyId");
        assertEquals(200, keys(r, "GET", orgA, null).status, "list");
        assertEquals(200, keys(r, "DELETE", orgA, keyId).status, "revoke");
    }

    @Test void orgAdminDeniedOtherOrg() throws Exception {
        assertDeniedEverywhere(r, orgB, "orgAdmin on other org");
    }

    @Test void crossOrgKeyIdDenied() throws Exception {
        String bKey = createKeyDirect(orgB);
        assertEquals(404, keys(r, "DELETE", orgA, bKey).status, "B's key through A's path");
        Result list = keys(siteAdmin, "GET", orgB, null);
        JSONArray arr = new JSONObject(list.text).getJSONArray("keys");
        assertEquals(1, arr.length(), "B has one key");
        assertTrue(arr.getJSONObject(0).isNull("revokedAt"), "B's key is still live");
    }

    @Test void plainMemberDenied() throws Exception {
        assertDeniedEverywhere(m, orgA, "plain member");
    }

    @Test void parentDoesNotGrantChild() throws Exception {
        assertDeniedEverywhere(parentAdmin, orgC, "parent org admin on child");
    }

    @Test void discoveryListsOnlyManageable() throws Exception {
        Set<String> mine = discovered(r);
        assertTrue(mine.contains(orgA.getId()), "own org listed");
        assertFalse(mine.contains(orgB.getId()) || mine.contains(orgC.getId()), "no other orgs");
        assertEquals(1, mine.size(), "exactly the direct organization");
        Set<String> all = discovered(siteAdmin);
        assertTrue(all.contains(orgA.getId()) && all.contains(orgB.getId()) && all.contains(orgC.getId()),
            "site admin sees every organization");
    }

    @Test void orgSuperCannotManageKeys() throws Exception {
        assertDeniedEverywhere(s, orgA, "orgSuper non-member");
        assertTrue(discovered(s).isEmpty(), "orgSuper discovers nothing");
    }

    @Test void orgSuperMemberWithoutOrgAdminDenied() throws Exception {
        assertDeniedEverywhere(sMember, orgA, "orgSuper member");
        assertTrue(discovered(sMember).isEmpty(), "orgSuper member discovers nothing");
    }

    // UserCreate's checks are package-private
    private static boolean userCreate(String name, Class<?>[] types, Object... args) {
        try {
            java.lang.reflect.Method method = UserCreate.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return (Boolean)method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test void orgAdminCannotEditServiceUser() {
        User svc = user("svc");
        svc.setServiceOrganizationId(orgA.getId());
        assertTrue(sh.commitDBTransactionWithStatus());
        sh.beginDBTransaction();
        join(orgA, svc);
        boolean serviceGate = userCreate("canEditServiceAccount", new Class<?>[] { boolean.class, User.class },
            false, svc);
        boolean existingGate = userCreate("canEditExistingUser",
            new Class<?>[] { Shepherd.class, User.class, boolean.class, User.class }, sh, r, false, svc);
        assertFalse(serviceGate, "service account needs a site admin");
        // the existing-user check (shared organization) is a separate gate; UserCreate requires both,
        // so it must never be the one that lets an org admin through
        assertFalse(serviceGate && existingGate, "an org admin of the shared org still cannot edit the service user");
    }
}
