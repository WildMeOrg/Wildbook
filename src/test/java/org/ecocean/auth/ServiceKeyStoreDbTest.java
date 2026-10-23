package org.ecocean.auth;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.ecocean.Organization;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONArray;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ServiceKeyStoreDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private static final Set<String> READ = Set.of("data:read");
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

    private static String newOrg() {
        Shepherd sh = open();
        try {
            Organization org = new Organization("Store org");
            sh.getPM().makePersistent(org);
            assertTrue(sh.commitDBTransactionWithStatus(), "org committed");
            return org.getId();
        } finally { sh.rollbackAndClose(); }
    }

    /** One create in its own committed transaction. */
    private static ServiceKeyCredential.Issued create(String orgId, Set<String> scopes, int days) {
        Shepherd sh = open();
        try {
            ServiceKeyCredential.Issued issued = ServiceKeyStore.create(sh, sh.getOrganization(orgId), "label",
                scopes, days, "site-admin");
            assertTrue(sh.commitDBTransactionWithStatus(), "create committed");
            return issued;
        } finally { sh.rollbackAndClose(); }
    }

    private static ServiceKeyException createFails(String orgId, Set<String> scopes, int days) {
        Shepherd sh = open();
        try {
            return assertThrows(ServiceKeyException.class, () -> ServiceKeyStore.create(sh,
                sh.getOrganization(orgId), "label", scopes, days, "site-admin"), "create must be refused");
        } finally { sh.rollbackAndClose(); }
    }

    private static List<ServiceKey> list(String orgId) {
        Shepherd sh = open();
        try {
            List<ServiceKey> keys = new java.util.ArrayList<>();
            for (ServiceKey k : ServiceKeyStore.list(sh, orgId)) keys.add((ServiceKey)sh.getPM().detachCopy(k));
            return keys;
        } finally { sh.rollbackAndClose(); }
    }

    private static ServiceKey listed(String orgId, String keyId) {
        return list(orgId).stream().filter(k -> k.getId().equals(keyId)).findFirst().orElse(null);
    }

    private static void jdbc(String sql, Object... args) throws Exception {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             PreparedStatement st = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) st.setObject(i + 1, args[i]);
            st.executeUpdate();
        }
    }

    @Test void createProvisionsServiceUserOnce() {
        String orgId = newOrg();
        ServiceKeyCredential.Issued first = create(orgId, READ, 30);
        ServiceKeyCredential.Issued second = create(orgId, Set.of("submissions:write"), 30);
        assertNotEquals(first.keyId, second.keyId, "two distinct keys");
        List<ServiceKey> keys = list(orgId);
        assertEquals(2, keys.size(), "both keys listed");
        assertEquals(keys.get(0).getServiceUserId(), keys.get(1).getServiceUserId(), "one service user reused");
        Shepherd sh = open();
        try {
            User svc = sh.getUserByUUID(keys.get(0).getServiceUserId());
            assertNotNull(svc, "service user exists");
            assertEquals("svc-" + orgId, svc.getUsername(), "username");
            assertEquals(orgId, svc.getServiceOrganizationId(), "bound to the org");
            assertTrue(svc.isServiceAccount(), "service account");
            assertNull(svc.getEmailAddress(), "no email");
            assertNotNull(svc.getPassword(), "a discarded password hash, never null");
            assertTrue(sh.getOrganization(orgId).hasMember(svc), "member of the org");
            assertTrue(sh.getAllRolesForUserInContext(svc.getUsername(), "context0").isEmpty(), "no roles");
            assertFalse(svc.checkPassword(""), "cannot log in");
        } finally { sh.rollbackAndClose(); }
        // the credential works end to end
        Shepherd ex = open();
        try {
            assertNotNull(ServiceKeyAuth.authenticate(ex, first.plaintext, System.currentTimeMillis()), "grant");
        } finally { ex.rollbackAndClose(); }
    }

    @Test void removedServiceUserIsReAddedOnCreate() {
        String orgId = newOrg();
        create(orgId, READ, 30);
        String userId = list(orgId).get(0).getServiceUserId();
        Shepherd sh = open();
        try {
            sh.getOrganization(orgId).removeMember(sh.getUserByUUID(userId));
            assertTrue(sh.commitDBTransactionWithStatus(), "removed");
        } finally { sh.rollbackAndClose(); }
        create(orgId, READ, 30);
        sh = open();
        try {
            assertTrue(sh.getOrganization(orgId).hasMember(sh.getUserByUUID(userId)), "re-added");
        } finally { sh.rollbackAndClose(); }
    }

    @Test void twoKeyCapUnderConcurrency() throws Exception {
        String orgId = newOrg();
        create(orgId, READ, 30);
        CountDownLatch created = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        // A creates the second key and holds its transaction open
        CompletableFuture<String> a = CompletableFuture.supplyAsync(() -> {
            Shepherd sh = open();
            try {
                ServiceKeyCredential.Issued issued = ServiceKeyStore.create(sh, sh.getOrganization(orgId), "a",
                    READ, 30, "site-admin");
                created.countDown();
                release.await(30, TimeUnit.SECONDS);
                assertTrue(sh.commitDBTransactionWithStatus(), "A committed");
                return issued.keyId;
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            } finally { created.countDown(); sh.rollbackAndClose(); }
        });
        assertTrue(created.await(30, TimeUnit.SECONDS), "A created its key");
        // B must wait on the organization lock while A is open
        CompletableFuture<String> b = CompletableFuture.supplyAsync(() -> {
            Shepherd sh = open();
            try {
                String keyId = ServiceKeyStore.create(sh, sh.getOrganization(orgId), "b", READ, 30,
                    "site-admin").keyId;
                assertTrue(sh.commitDBTransactionWithStatus(), "B committed");
                return keyId;
            } finally { sh.rollbackAndClose(); }
        });
        Thread.sleep(1000);
        assertFalse(b.isDone(), "B is blocked behind A's lock");
        release.countDown();
        assertNotNull(a.get(30, TimeUnit.SECONDS), "A succeeded");
        ExecutionException failure = assertThrows(ExecutionException.class, () -> b.get(30, TimeUnit.SECONDS),
            "B refused");
        assertTrue(failure.getCause() instanceof ServiceKeyException, "ServiceKeyException: " + failure.getCause());
        ServiceKeyException ex = (ServiceKeyException)failure.getCause();
        assertEquals(409, ex.status, "status");
        assertEquals("organization already has 2 active keys", ex.getMessage(), "message");
        assertEquals(2, list(orgId).size(), "exactly two keys");
    }

    @Test void expiredOrRevokedKeysFreeASlot() throws Exception {
        String orgId = newOrg();
        ServiceKeyCredential.Issued one = create(orgId, READ, 30);
        ServiceKeyCredential.Issued two = create(orgId, READ, 30);
        assertEquals(409, createFails(orgId, READ, 30).status, "cap reached");
        Shepherd sh = open();
        try {
            assertTrue(ServiceKeyStore.revoke(sh, orgId, one.keyId, "site-admin"), "revoked");
            assertTrue(sh.commitDBTransactionWithStatus(), "revoke committed");
        } finally { sh.rollbackAndClose(); }
        ServiceKeyCredential.Issued three = create(orgId, READ, 30);
        assertEquals(409, createFails(orgId, READ, 30).status, "cap reached again");
        jdbc("UPDATE \"SERVICE_KEY\" SET \"EXPIRESAT\" = ? WHERE \"ID\" = ?", System.currentTimeMillis() - 1000L,
            two.keyId);
        assertNotNull(create(orgId, READ, 30), "an expired key frees a slot");
        assertEquals(4, list(orgId).size(), "history is kept");
        assertNotNull(three.keyId, "three");
    }

    @Test void daysOutOfRangeRejected() {
        String orgId = newOrg();
        for (int days : new int[] { 0, 366, -1 }) {
            ServiceKeyException ex = createFails(orgId, READ, days);
            assertEquals(400, ex.status, "status for days=" + days);
        }
        long before = System.currentTimeMillis();
        ServiceKeyCredential.Issued oneDay = create(orgId, READ, 1);
        ServiceKeyCredential.Issued maxDays = create(orgId, READ, 365);
        long day = 24L * 3600L * 1000L;
        long oneDayExpiry = listed(orgId, oneDay.keyId).getExpiresAt();
        long maxExpiry = listed(orgId, maxDays.keyId).getExpiresAt();
        assertTrue(oneDayExpiry >= before + day && oneDayExpiry <= System.currentTimeMillis() + day, "1 day");
        assertTrue(maxExpiry >= before + 365 * day && maxExpiry <= System.currentTimeMillis() + 365 * day,
            "365 days");
    }

    @Test void unknownScopeRejected() {
        String orgId = newOrg();
        ServiceKeyException ex = createFails(orgId, Set.of("data:read", "admin"), 30);
        assertEquals(400, ex.status, "status");
        assertEquals(400, createFails(orgId, java.util.Collections.singleton(null), 30).status, "null scope");
        assertTrue(list(orgId).isEmpty(), "nothing stored");
    }

    @Test void emptyScopesRejected() {
        String orgId = newOrg();
        assertEquals(400, createFails(orgId, Set.of(), 30).status, "empty");
        assertEquals(400, createFails(orgId, null, 30).status, "null");
        assertTrue(list(orgId).isEmpty(), "nothing stored");
    }

    @Test void scopesStoredInCanonicalOrder() {
        String orgId = newOrg();
        ServiceKeyCredential.Issued issued = create(orgId,
            Set.of("submissions:write", "data:read", "submissions:read"), 30);
        assertEquals("data:read,submissions:read,submissions:write", listed(orgId, issued.keyId).getScopes(),
            "canonical order");
    }

    @Test void revokeIsIdempotentAndOrgScoped() {
        String orgA = newOrg();
        String orgB = newOrg();
        create(orgA, READ, 30);
        ServiceKeyCredential.Issued keyB = create(orgB, READ, 30);
        Shepherd sh = open();
        try {
            assertFalse(ServiceKeyStore.revoke(sh, orgA, keyB.keyId, "site-admin"), "B's key is not under A");
            assertFalse(ServiceKeyStore.revoke(sh, orgA, "no-such-key", "site-admin"), "unknown key");
            assertEquals(1, ServiceKeyStore.revokeAll(sh, orgA, "site-admin"), "only A's key");
            assertTrue(sh.commitDBTransactionWithStatus(), "committed");
        } finally { sh.rollbackAndClose(); }
        assertTrue(listed(orgB, keyB.keyId).isLive(System.currentTimeMillis()), "B's key stays live");

        sh = open();
        try {
            assertTrue(ServiceKeyStore.revoke(sh, orgB, keyB.keyId, "first-admin"), "revoked");
            assertTrue(sh.commitDBTransactionWithStatus(), "committed");
        } finally { sh.rollbackAndClose(); }
        ServiceKey revoked = listed(orgB, keyB.keyId);
        sh = open();
        try {
            assertTrue(ServiceKeyStore.revoke(sh, orgB, keyB.keyId, "second-admin"), "idempotent");
            assertEquals(0, ServiceKeyStore.revokeAll(sh, orgB, "second-admin"), "nothing left to revoke");
            assertTrue(sh.commitDBTransactionWithStatus(), "committed");
        } finally { sh.rollbackAndClose(); }
        ServiceKey again = listed(orgB, keyB.keyId);
        assertEquals(revoked.getRevokedAt(), again.getRevokedAt(), "first revocation time kept");
        assertEquals("first-admin", again.getRevokedBy(), "first revoker kept");
    }

    @Test void listReadsLastUsedAtFresh() throws Exception {
        String orgId = newOrg();
        ServiceKeyCredential.Issued issued = create(orgId, READ, 30);
        assertNull(listed(orgId, issued.keyId).getLastUsedAt(), "unused");
        Shepherd sh = open();
        try {
            ServiceKeyAuth.authenticate(sh, issued.plaintext, 12345L);
            assertTrue(sh.commitDBTransactionWithStatus(), "exchange committed");
        } finally { sh.rollbackAndClose(); }
        assertEquals(Long.valueOf(12345L), listed(orgId, issued.keyId).getLastUsedAt(), "exchange's write seen");
        jdbc("UPDATE \"SERVICE_KEY\" SET \"LASTUSEDAT\" = ? WHERE \"ID\" = ?", 999L, issued.keyId);
        assertEquals(Long.valueOf(999L), listed(orgId, issued.keyId).getLastUsedAt(), "native write seen");
    }

    @Test void listNeverReturnsHash() {
        String orgId = newOrg();
        ServiceKeyCredential.Issued issued = create(orgId, Set.of("data:read", "submissions:read"), 30);
        Shepherd sh = open();
        String json;
        try {
            JSONArray keys = new JSONArray();
            for (ServiceKey k : ServiceKeyStore.list(sh, orgId)) keys.put(ServiceKeyStore.toJson(k));
            json = keys.toString();
        } finally { sh.rollbackAndClose(); }
        assertFalse(json.contains("secretHash"), "no secretHash field: " + json);
        assertFalse(json.contains(issued.secretHash), "no hash value");
        assertFalse(json.contains(issued.plaintext.substring(issued.plaintext.indexOf('.') + 1)), "no secret");
        assertTrue(json.contains(issued.keyId), "key id listed");
        for (String field : new String[] { "keyId", "label", "scopes", "createdAt", "createdBy", "expiresAt",
                "revokedAt", "lastUsedAt" }) {
            assertTrue(json.contains("\"" + field + "\""), "field " + field + ": " + json);
        }
    }
}
