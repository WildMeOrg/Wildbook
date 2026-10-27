package org.ecocean.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Properties;
import java.util.UUID;
import org.ecocean.Organization;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ServiceKeyAuthDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final long DAY = 24L * 3600L * 1000L;
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

    private static Shepherd open() {
        Shepherd sh = new Shepherd("context0", properties);
        sh.beginDBTransaction();
        return sh;
    }

    /** Organization + bound member service user + one key; returns [orgId, userId, keyId, plaintext, username]. */
    private static String[] fixture(long expiresAt, Long revokedAt) {
        Shepherd sh = open();
        try {
            Organization org = new Organization("Fixture org");
            sh.getPM().makePersistent(org);
            String userId = UUID.randomUUID().toString();
            User svc = new User(null, userId);
            String username = "svc-" + org.getId();
            svc.setUsername(username);
            svc.setServiceOrganizationId(org.getId());
            sh.getPM().makePersistent(svc);
            org.addMember(svc);
            ServiceKeyCredential.Issued issued = ServiceKeyCredential.issue();
            ServiceKey key = new ServiceKey(issued.keyId, org.getId(), userId, "fixture",
                issued.secretHash, "data:read,submissions:write", System.currentTimeMillis(), "admin-id", expiresAt);
            key.setRevokedAt(revokedAt);
            sh.getPM().makePersistent(key);
            assertTrue(sh.commitDBTransactionWithStatus(), "fixture committed");
            return new String[] { org.getId(), userId, issued.keyId, issued.plaintext, username };
        } finally { sh.rollbackAndClose(); }
    }

    private static String[] liveFixture() { return fixture(System.currentTimeMillis() + 30 * DAY, null); }

    private static ServiceKeyException rejected(String credential) {
        Shepherd sh = open();
        try {
            return assertThrows(ServiceKeyException.class,
                () -> ServiceKeyAuth.authenticate(sh, credential, System.currentTimeMillis()), "must be rejected");
        } finally { sh.rollbackAndClose(); }
    }

    private static void assertRejected(String credential, String message) {
        ServiceKeyException ex = rejected(credential);
        assertEquals(401, ex.status, "status");
        assertEquals(message, ex.getMessage(), "message");
    }

    private static String wrongSecret(String plaintext) {
        String keyId = plaintext.substring(ServiceKeyCredential.PREFIX.length(), plaintext.indexOf('.'));
        String other = ServiceKeyCredential.issue().plaintext;
        return ServiceKeyCredential.PREFIX + keyId + other.substring(other.indexOf('.'));
    }

    private static ServiceKey reload(String keyId) {
        Shepherd sh = open();
        try {
            ServiceKey key = (ServiceKey)sh.getPM().getObjectById(ServiceKey.class, keyId);
            sh.getPM().refresh(key);
            return (ServiceKey)sh.getPM().detachCopy(key);
        } finally { sh.rollbackAndClose(); }
    }

    private static void jdbc(String sql, String... args) throws Exception {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement st = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) st.setString(i + 1, args[i]);
            st.executeUpdate();
        }
    }

    @Test void liveKeyAuthenticates() {
        String[] f = liveFixture();
        long now = System.currentTimeMillis();
        Shepherd sh = open();
        ServiceKeyAuth.Grant grant;
        try {
            grant = ServiceKeyAuth.authenticate(sh, f[3], now);
            assertTrue(sh.commitDBTransactionWithStatus(), "exchange committed");
        } finally { sh.rollbackAndClose(); }
        assertEquals(f[1], grant.serviceUserId, "service user");
        assertEquals(f[2], grant.keyId, "key id");
        assertEquals(f[0], grant.organizationId, "organization");
        assertEquals(java.util.Set.of("data:read", "submissions:write"), grant.scopes, "scopes");
        ServiceKey key = reload(f[2]);
        assertEquals(key.getExpiresAt(), grant.keyExpiresAt, "key expiry");
        assertEquals(Long.valueOf(now), key.getLastUsedAt(), "lastUsedAt recorded");
        assertNull(key.getRevokedAt(), "still live");
    }

    @Test void expiredIs401WithExpiredMessage() {
        String[] f = fixture(System.currentTimeMillis() - 1000L, null);
        assertRejected(f[3], "service key expired");
        assertNull(reload(f[2]).getLastUsedAt(), "failed exchange records nothing");
    }

    @Test void revokedIs401WithRevokedMessage() {
        String[] revoked = fixture(System.currentTimeMillis() + DAY, System.currentTimeMillis() - 1000L);
        assertRejected(revoked[3], "service key revoked");
        String[] both = fixture(System.currentTimeMillis() - 1000L, System.currentTimeMillis() - 2000L);
        assertRejected(both[3], "service key revoked");
    }

    @Test void wrongSecretOnExpiredKeyIsGeneric() {
        String[] expired = fixture(System.currentTimeMillis() - 1000L, null);
        assertRejected(wrongSecret(expired[3]), "invalid service key");
        String[] revoked = fixture(System.currentTimeMillis() + DAY, System.currentTimeMillis() - 1000L);
        assertRejected(wrongSecret(revoked[3]), "invalid service key");
    }

    @Test void wrongSecretIs401() {
        String[] f = liveFixture();
        assertRejected(wrongSecret(f[3]), "invalid service key");
        assertRejected("wbsvc_garbage", "invalid service key");
        assertRejected(null, "invalid service key");
        assertNull(reload(f[2]).getLastUsedAt(), "wrong secret records nothing");
    }

    @Test void unknownKeyIdIs401() {
        liveFixture();
        assertRejected(ServiceKeyCredential.issue().plaintext, "invalid service key");
    }

    @Test void uppercaseKeyIdFailsClosed() {
        String[] f = liveFixture();
        String upper = ServiceKeyCredential.PREFIX + f[2].toUpperCase() + f[3].substring(f[3].indexOf('.'));
        assertRejected(upper, "invalid service key");
    }

    @Test void serviceUserNotMemberIs401() {
        String[] f = liveFixture();
        Shepherd sh = open();
        try {
            Organization org = sh.getOrganization(f[0]);
            User svc = sh.getUserByUUID(f[1]);
            org.removeMember(svc);
            assertTrue(sh.commitDBTransactionWithStatus(), "membership removed");
        } finally { sh.rollbackAndClose(); }
        assertRejected(f[3], "service key unavailable; contact your Wildbook administrator");
    }

    @Test void serviceAccountInTwoOrgsIsUnavailable() throws Exception {
        String[] f = liveFixture();
        String[] other = liveFixture();
        Shepherd sh = open();
        try {
            sh.getOrganization(other[0]).addMember(sh.getUserByUUID(f[1]));
            assertTrue(sh.commitDBTransactionWithStatus(), "added to a second org");
        } finally { sh.rollbackAndClose(); }
        assertRejected(f[3], "service key unavailable; contact your Wildbook administrator");
        // the same holds when the extra membership is written behind DataNucleus' back
        String[] g = liveFixture();
        Shepherd warm = open();
        try { ServiceKeyAuth.authenticate(warm, g[3], System.currentTimeMillis()); }
        finally { warm.rollbackAndClose(); }
        jdbc("INSERT INTO \"ORGANIZATION_MEMBERS\" (\"USER_ID\", \"ORGANIZATION_ID\") VALUES (?, ?)",
            g[1], other[0]);
        assertRejected(g[3], "service key unavailable; contact your Wildbook administrator");
        // the other org's own service account is unaffected
        Shepherd ok = open();
        try { assertNotNull(ServiceKeyAuth.authenticate(ok, other[3], System.currentTimeMillis()), "grant"); }
        finally { ok.rollbackAndClose(); }
    }

    @Test void membershipRemovedOutsideJdoIsSeen() throws Exception {
        String[] f = liveFixture();
        // warm the level-2 cache with the membership, then remove it behind DataNucleus' back
        Shepherd warm = open();
        try { ServiceKeyAuth.authenticate(warm, f[3], System.currentTimeMillis()); }
        finally { warm.rollbackAndClose(); }
        jdbc("DELETE FROM \"ORGANIZATION_MEMBERS\" WHERE \"USER_ID\" = ?", f[1]);
        assertRejected(f[3], "service key unavailable; contact your Wildbook administrator");
    }

    @Test void revokedOutsideJdoIsSeen() throws Exception {
        String[] f = liveFixture();
        Shepherd warm = open();
        try { ServiceKeyAuth.authenticate(warm, f[3], System.currentTimeMillis()); }
        finally { warm.rollbackAndClose(); }
        jdbc("UPDATE \"SERVICE_KEY\" SET \"REVOKEDAT\" = 1 WHERE \"ID\" = ?", f[2]);
        assertRejected(f[3], "service key revoked");
    }

    @Test void orgBindingMismatchIs401() {
        String[] f = liveFixture();
        String[] other = liveFixture();
        Shepherd sh = open();
        try {
            // rebind the first service user to another organization while it stays a member of its own
            User otherSvc = sh.getUserByUUID(other[1]);
            otherSvc.setServiceOrganizationId(null);
            assertTrue(sh.commitDBTransactionWithStatus(), "free the other binding");
        } finally { sh.rollbackAndClose(); }
        sh = open();
        try {
            sh.getUserByUUID(f[1]).setServiceOrganizationId(other[0]);
            assertTrue(sh.commitDBTransactionWithStatus(), "rebound");
        } finally { sh.rollbackAndClose(); }
        assertRejected(f[3], "service key unavailable; contact your Wildbook administrator");
        // an unbound (ordinary) account is refused the same way
        sh = open();
        try {
            sh.getUserByUUID(f[1]).setServiceOrganizationId(null);
            assertTrue(sh.commitDBTransactionWithStatus(), "unbound");
        } finally { sh.rollbackAndClose(); }
        assertRejected(f[3], "service key unavailable; contact your Wildbook administrator");
    }

    @Test void missingOrganizationOrUserIs401() throws Exception {
        String[] f = liveFixture();
        jdbc("DELETE FROM \"ORGANIZATION_MEMBERS\" WHERE \"USER_ID\" = ?", f[1]);
        jdbc("DELETE FROM \"USERS\" WHERE \"UUID\" = ?", f[1]);
        assertRejected(f[3], "service key unavailable; contact your Wildbook administrator");
        String[] g = liveFixture();
        jdbc("DELETE FROM \"ORGANIZATION_MEMBERS\" WHERE \"ORGANIZATION_ID\" = ?", g[0]);
        jdbc("DELETE FROM \"ORGANIZATION\" WHERE \"ID\" = ?", g[0]);
        assertRejected(g[3], "service key unavailable; contact your Wildbook administrator");
    }

    @Test void privilegedServiceUserIs401() {
        for (String rolename : new String[] { Organization.ROLE_MANAGER, Organization.ROLE_ADMIN, "admin" }) {
            String[] f = liveFixture();
            Shepherd sh = open();
            try {
                Role role = new Role(f[4], rolename);
                role.setContext("context0");
                sh.getPM().makePersistent(role);
                assertTrue(sh.commitDBTransactionWithStatus(), "role granted");
            } finally { sh.rollbackAndClose(); }
            assertRejected(f[3], "service key unavailable; contact your Wildbook administrator");
        }
    }

    @Test void unprivilegedRoleOrOtherContextIsFine() {
        String[] f = liveFixture();
        Shepherd sh = open();
        try {
            Role sub = new Role(f[4], Role.API_SUBMISSION); sub.setContext("context0");
            Role elsewhere = new Role(f[4], "admin"); elsewhere.setContext("context1");
            sh.getPM().makePersistent(sub); sh.getPM().makePersistent(elsewhere);
            assertTrue(sh.commitDBTransactionWithStatus(), "roles granted");
        } finally { sh.rollbackAndClose(); }
        Shepherd ex = open();
        try { assertNotNull(ServiceKeyAuth.authenticate(ex, f[3], System.currentTimeMillis()), "grant"); }
        finally { ex.rollbackAndClose(); }
    }

    @Test void revokeDuringUseNotOverwritten() {
        String[] f = liveFixture();
        long now = System.currentTimeMillis();
        Shepherd sh = open();
        try {
            ServiceKey read = ServiceKeyAuth.verify(sh, f[3], now);
            assertEquals(f[2], read.getId(), "checks passed before the revoke");
            // a management request revokes the key between the checks and the lastUsedAt write
            Shepherd other = open();
            try {
                ServiceKey key = (ServiceKey)other.getPM().getObjectById(ServiceKey.class, f[2]);
                key.setRevokedAt(now - 5L);
                key.setRevokedBy("admin-id");
                assertTrue(other.commitDBTransactionWithStatus(), "revoked");
            } finally { other.rollbackAndClose(); }
            ServiceKeyException ex = assertThrows(ServiceKeyException.class,
                () -> ServiceKeyAuth.recordUse(sh, f[2], now), "use after revoke is refused");
            assertEquals(401, ex.status, "status");
            assertEquals("service key revoked", ex.getMessage(), "message");
            sh.commitDBTransactionWithStatus();
        } finally { sh.rollbackAndClose(); }
        ServiceKey after = reload(f[2]);
        assertEquals(Long.valueOf(now - 5L), after.getRevokedAt(), "revokedAt kept");
        assertNull(after.getLastUsedAt(), "no use recorded on a revoked key");
    }
}
