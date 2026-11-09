package org.ecocean.api;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Organization;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.auth.ServiceKeyAuth;
import org.ecocean.auth.ServiceKeyException;
import org.ecocean.servlet.ServletUtilities;
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
import static org.mockito.Mockito.*;

/**
 * Keys whose data tokens read as their creator: who may create one, what the exchange reads as, and
 * the creator's own listing and revocation after they stop managing the organization.
 */
@Testcontainers
class ServiceKeysReadAsDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final String PW = "correct horse";

    private Shepherd sh;
    private String suffix;
    private Organization orgA, orgB;
    private User siteAdmin, r, m;

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
        r = user("org-admin");
        m = user("member");
        orgA = new Organization("A" + suffix);
        orgB = new Organization("B" + suffix);
        sh.getPM().makePersistent(orgA);
        sh.getPM().makePersistent(orgB);
        assertTrue(sh.commitDBTransactionWithStatus(), "fixture rows committed");
        sh.beginDBTransaction();
        join(orgA, r);
        join(orgB, r);
        join(orgA, m);
        role(siteAdmin, "admin");
        role(r, Organization.ROLE_MANAGER);
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

    private void leave(Organization org, User u) {
        org.removeMember(u);
        assertTrue(sh.commitDBTransactionWithStatus(), "membership removal committed");
        sh.beginDBTransaction();
    }

    private void role(User u, String rolename) {
        Role role = new Role(u.getUsername(), rolename);
        role.setContext("context0");
        sh.getPM().makePersistent(role);
    }

    @SuppressWarnings("unchecked")
    private void dropRoles(User u) {
        Query q = sh.getPM().newQuery(Role.class, "username == :u");
        try {
            sh.getPM().deletePersistentAll((Collection<Role>)q.execute(u.getUsername()));
        } finally { q.closeAll(); }
        assertTrue(sh.commitDBTransactionWithStatus(), "roles removed");
        sh.beginDBTransaction();
    }

    private static final class Result {
        int status;
        String text;
        JSONObject json() { return new JSONObject(text); }
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
        else if ("PATCH".equals(method)) servlet.doPatch(req, resp);
        else if ("DELETE".equals(method)) servlet.doDelete(req, resp);
        else throw new IllegalArgumentException(method);
        org.mockito.ArgumentCaptor<Integer> status = org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(resp, atLeastOnce()).setStatus(status.capture());
        Result res = new Result();
        res.status = status.getValue();
        res.text = out.toString();
        return res;
    }

    private Result create(User who, Organization org, String body) throws Exception {
        return call(who, "POST", "/api/v3/organizations", "/" + org.getId() + "/service-keys", body);
    }

    private Result createBound(User who, Organization org) throws Exception {
        return create(who, org, "{\"label\":\"bound\",\"scopes\":[\"data:read\"],\"days\":30,\"readAsCreator\":true}");
    }

    private Result revoke(User who, Organization org, String keyId) throws Exception {
        return call(who, "DELETE", "/api/v3/organizations",
            "/" + org.getId() + "/service-keys" + (keyId == null ? "" : "/" + keyId), null);
    }

    private Result mine(User who) throws Exception {
        return call(who, "GET", "/api/v3/service-keys", "/mine", null);
    }

    /** The user id a data token from this plaintext key would carry, from a fresh transaction. */
    private String dataSubject(String plaintext) {
        Shepherd s2 = new Shepherd("context0");
        s2.beginDBTransaction();
        try {
            ServiceKeyAuth.Grant grant = ServiceKeyAuth.authenticate(s2, plaintext, System.currentTimeMillis());
            return ServiceKeyAuth.dataSubject(s2, grant);
        } finally { s2.rollbackAndClose(); }
    }

    @Test void boundKeyReadsAsCreatorAndStopsWhenTheyLeave() throws Exception {
        Result created = createBound(r, orgA);
        assertEquals(201, created.status, created.text);
        assertTrue(created.json().getBoolean("readAsCreator"), "flag in the create response");
        assertEquals(r.getId(), created.json().getString("createdBy"), "creator identifies whose access");
        String plaintext = created.json().getString("key");
        assertEquals(r.getId(), dataSubject(plaintext), "data tokens read as the creator");

        leave(orgA, r);
        ServiceKeyException gone = assertThrows(ServiceKeyException.class, () -> dataSubject(plaintext),
            "creator left the organization");
        assertEquals(401, gone.status, "refused like any unavailable key");

        join(orgA, r);
        assertEquals(r.getId(), dataSubject(plaintext), "rejoining makes the unexpired key work again");
    }

    @Test void unboundKeyReadsAsServiceAccount() throws Exception {
        Result created = create(r, orgA, "{\"label\":\"plain\",\"scopes\":[\"data:read\"],\"days\":30}");
        assertEquals(201, created.status, created.text);
        assertFalse(created.json().getBoolean("readAsCreator"), "flag off by default");
        String subject = dataSubject(created.json().getString("key"));
        assertNotEquals(r.getId(), subject, "not the creator");
        assertNotNull(sh.getUserByUUID(subject), "subject exists");
        assertTrue(sh.getUserByUUID(subject).isServiceAccount(), "the organization's service account");
    }

    @Test void createRules() throws Exception {
        Result noDataRead = create(r, orgA,
            "{\"label\":\"x\",\"scopes\":[\"submissions:read\"],\"days\":30,\"readAsCreator\":true}");
        assertEquals(400, noDataRead.status, "needs data:read: " + noDataRead.text);

        Result notBoolean = create(r, orgA,
            "{\"label\":\"x\",\"scopes\":[\"data:read\"],\"days\":30,\"readAsCreator\":\"yes\"}");
        assertEquals(400, notBoolean.status, "must be a boolean: " + notBoolean.text);

        Result adminNotMember = createBound(siteAdmin, orgA);
        assertEquals(403, adminNotMember.status, "site admin outside the organization: " + adminNotMember.text);

        join(orgA, siteAdmin);
        Result adminMember = createBound(siteAdmin, orgA);
        assertEquals(201, adminMember.status, "site admin who is a member: " + adminMember.text);
        assertEquals(siteAdmin.getId(), dataSubject(adminMember.json().getString("key")),
            "an admin's bound key reads as the admin");
    }

    @Test void creatorListsAndRevokesOwnKeysAfterLosingOrgAdmin() throws Exception {
        String keyA = createBound(r, orgA).json().getString("keyId");
        String keyB = createBound(r, orgB).json().getString("keyId");
        String unbound = create(r, orgA, "{\"label\":\"plain\",\"scopes\":[\"data:read\"],\"days\":30}")
            .json().getString("keyId");
        dropRoles(r);

        Result listed = mine(r);
        assertEquals(200, listed.status, listed.text);
        JSONArray keys = listed.json().getJSONArray("keys");
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < keys.length(); i++) {
            JSONObject k = keys.getJSONObject(i);
            ids.add(k.getString("keyId"));
            assertTrue(k.has("organizationId"), "organization id on every entry");
            assertTrue(k.getBoolean("readAsCreator"), "only bound keys");
        }
        assertEquals(Set.of(keyA, keyB), ids, "both organizations' bound keys, not the unbound one");

        assertEquals(403, revoke(m, orgA, keyA).status, "another member cannot revoke it");
        assertEquals(403, revoke(r, orgA, unbound).status, "an unbound key needs management rights");
        assertEquals(403, revoke(r, orgA, null).status, "revoke-all needs management rights");
        assertEquals(403, call(r, "GET", "/api/v3/organizations", "/" + orgA.getId() + "/service-keys", null)
            .status, "listing the organization needs management rights");
        assertEquals(403, revoke(r, orgB, keyA).status, "the key must belong to the organization in the path");

        assertEquals(200, revoke(r, orgA, keyA).status, "creator revokes their key in A");
        assertEquals(200, revoke(r, orgB, keyB).status, "creator revokes their key in B");
        assertEquals(0, mine(r).json().getJSONArray("keys").length(), "nothing live left");
    }

    private void commitFixture() {
        assertTrue(sh.commitDBTransactionWithStatus(), "fixture change committed");
        sh.beginDBTransaction();
    }

    @Test void creatorDeletedOrTurnedIntoServiceAccountStopsTheKey() throws Exception {
        User d = user("deleted");
        User c = user("converted");
        role(d, Organization.ROLE_MANAGER);
        role(c, Organization.ROLE_MANAGER);
        commitFixture();
        join(orgA, d);
        join(orgA, c);
        String dKey = createBound(d, orgA).json().getString("key");
        String cKey = createBound(c, orgA).json().getString("key");
        assertEquals(d.getId(), dataSubject(dKey), "works before");

        sh.getPM().refresh(orgA);
        orgA.removeMember(d);
        sh.getPM().deletePersistent(sh.getUserByUUID(d.getId()));
        sh.getUserByUUID(c.getId()).setServiceOrganizationId(orgB.getId());
        commitFixture();

        assertEquals(401, assertThrows(ServiceKeyException.class, () -> dataSubject(dKey)).status,
            "deleted (or consolidated-away) creator");
        assertEquals(401, assertThrows(ServiceKeyException.class, () -> dataSubject(cKey)).status,
            "creator turned into a service account");
    }

    @Test void renamedCreatorKeepsTheBinding() throws Exception {
        String key = createBound(r, orgA).json().getString("key");
        sh.getUserByUUID(r.getId()).setUsername("renamed" + suffix);
        commitFixture();
        assertEquals(r.getId(), dataSubject(key), "the binding is the UUID, not the username");
    }

    @Test void parentMembershipIsNotEnough() throws Exception {
        Organization parent = new Organization("P" + suffix);
        sh.getPM().makePersistent(parent);
        User p = user("parent-member");
        commitFixture();
        parent.addChild(orgB);
        commitFixture();
        join(parent, p);
        role(p, "admin"); // may manage orgB's keys, so only the membership rule can refuse
        commitFixture();
        Result res = createBound(p, orgB);
        assertEquals(403, res.status, "a parent organization's member is not a direct member");
        assertTrue(res.text.contains("direct member"), "refused by the read-as rule: " + res.text);
    }

    @Test void selfRevokeWorksAfterLeavingAndUnknownLooksLikeForeign() throws Exception {
        String key = createBound(r, orgA).json().getString("keyId");
        String foreign = createBound(siteAdminMember(orgB), orgB).json().getString("keyId");
        leave(orgA, r);
        dropRoles(r);
        Result unknown = revoke(m, orgA, UUID.randomUUID().toString());
        Result notMine = revoke(m, orgA, key);
        assertEquals(403, unknown.status, "unknown key");
        assertEquals(notMine.status, unknown.status, "same answer for an unknown and another's key");
        assertEquals(notMine.text, unknown.text, "same body");
        assertEquals(403, revoke(r, orgB, foreign).status, "another creator's key in another organization");
        assertEquals(200, revoke(r, orgA, key).status, "the creator withdraws it after leaving");
    }

    private User siteAdminMember(Organization org) {
        join(org, siteAdmin);
        return siteAdmin;
    }

    @Test void mineSkipsExpiredKeys() throws Exception {
        String keyId = createBound(r, orgA).json().getString("keyId");
        org.ecocean.auth.ServiceKey key = sh.getPM().getObjectById(org.ecocean.auth.ServiceKey.class, keyId);
        sh.getPM().refresh(key);
        // no setter for expiry: age the row directly
        javax.jdo.datastore.JDOConnection conn = sh.getPM().getDataStoreConnection();
        try (java.sql.PreparedStatement st = ((java.sql.Connection)conn.getNativeConnection()).prepareStatement(
                "UPDATE \"SERVICE_KEY\" SET \"EXPIRESAT\" = 1 WHERE \"ID\" = ?")) {
            st.setString(1, keyId);
            assertEquals(1, st.executeUpdate(), "expired the key");
        } finally { conn.close(); }
        commitFixture();
        assertEquals(0, mine(r).json().getJSONArray("keys").length(), "expired keys are not listed");
    }

    @Test void patchIsRejectedEverywhere() throws Exception {
        assertEquals(405, call(r, "PATCH", "/api/v3/service-keys", "/mine", "{}").status, "mine");
        assertEquals(405, call(r, "PATCH", "/api/v3/organizations", "/" + orgA.getId() + "/service-keys", "{}")
            .status, "organization keys");
    }

    @Test void mineIsGetOnlyAndEmptyForOthers() throws Exception {
        createBound(r, orgA);
        Result other = mine(m);
        assertEquals(200, other.status, other.text);
        assertEquals(0, other.json().getJSONArray("keys").length(), "another user's keys are not listed");
        assertEquals(405, call(r, "POST", "/api/v3/service-keys", "/mine", "{}").status, "GET only");
    }
}
