package org.ecocean.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.datastore.JDOConnection;

import org.ecocean.Organization;
import org.ecocean.User;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Creates, lists and revokes an organization's service keys. Every method works inside the
 * caller's transaction; the caller commits. Reads bypass the caches, because the token exchange
 * stamps lastUsedAt with a native UPDATE that DataNucleus never sees.
 */
public final class ServiceKeyStore {
    public static final int MAX_LIVE_KEYS = 2;
    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 365;
    public static final int DEFAULT_DAYS = 90;
    public static final int MAX_LABEL_LENGTH = 255;
    /** Canonical storage order of the scopes. */
    static final List<String> SCOPE_ORDER = Arrays.asList("data:read", "submissions:read", "submissions:write");

    private static final long DAY_MILLIS = 24L * 3600L * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int USERNAME_TRIES = 3;
    /** The random part of a service account username; package-visible so tests can force collisions. */
    static Supplier<String> usernameSuffix = ServiceKeyStore::randomSuffix;

    private ServiceKeyStore() {}

    /**
     * Issues a new key for {@code org}, provisioning the organization's service user (as a member)
     * on first use and reusing it after. Throws ServiceKeyException 400 for bad input, and 409 when
     * the organization already has MAX_LIVE_KEYS live keys or its service user is no longer a member.
     */
    public static ServiceKeyCredential.Issued create(Shepherd sh, Organization org, String label,
        Set<String> scopes, int days, String createdBy) {
        // createdBy / revokedBy are user ids (36-char UUIDs), never usernames
        if (org == null || org.getId() == null) throw new ServiceKeyException(404, "organization not found");
        if (days < MIN_DAYS || days > MAX_DAYS)
            throw new ServiceKeyException(400, "days must be between " + MIN_DAYS + " and " + MAX_DAYS);
        String scopeList = canonicalScopes(scopes);
        if (label != null) {
            label = label.trim();
            if (label.isEmpty()) label = null;
            else if (label.length() > MAX_LABEL_LENGTH)
                throw new ServiceKeyException(400, "label must be at most " + MAX_LABEL_LENGTH + " characters");
        }
        String orgId = org.getId();
        PersistenceManager pm = sh.getPM();
        // serializes creates (and service user provisioning) per organization until commit
        lock(sh, "servicekey:" + orgId);
        long now = System.currentTimeMillis();
        if (countLive(pm, orgId, now) >= MAX_LIVE_KEYS)
            throw new ServiceKeyException(409, "organization already has " + MAX_LIVE_KEYS + " active keys");

        pm.refresh(org);
        User svc = serviceUser(sh, org);
        ServiceKeyCredential.Issued issued = ServiceKeyCredential.issue();
        ServiceKey key = new ServiceKey(issued.keyId, orgId, svc.getId(), label, issued.secretHash, scopeList,
            now, createdBy, now + days * DAY_MILLIS);
        pm.makePersistent(key);
        System.out.println("ServiceKeyStore created key=" + issued.keyId + " org=" + orgId + " user="
            + svc.getId());
        return issued;
    }

    /** Every key of the organization, newest first, read fresh from the datastore. */
    public static List<ServiceKey> list(Shepherd sh, String orgId) {
        return freshKeys(sh.getPM(), "organizationId == :org", orgId);
    }

    /** The key with {@code keyId} under {@code orgId} in this transaction (including one just created), or null. */
    public static ServiceKey get(Shepherd sh, String orgId, String keyId) {
        if (orgId == null || keyId == null) return null;
        try {
            ServiceKey key = (ServiceKey)sh.getPM().getObjectById(ServiceKey.class, keyId);
            return orgId.equals(key.getOrganizationId()) ? key : null;
        } catch (JDOObjectNotFoundException e) {
            return null;
        }
    }

    /**
     * Revokes one key of the organization. Idempotent: an already-revoked key keeps its first
     * revocation and still counts as found. A key of another organization is not found.
     */
    public static boolean revoke(Shepherd sh, String orgId, String keyId, String by) {
        if (orgId == null || keyId == null) return false;
        List<ServiceKey> keys = freshKeys(sh.getPM(), "id == :id", keyId);
        if (keys.size() != 1 || !orgId.equals(keys.get(0).getOrganizationId())) return false;
        ServiceKey key = keys.get(0);
        if (!key.isRevoked()) {
            key.setRevokedAt(System.currentTimeMillis());
            key.setRevokedBy(by);
            System.out.println("ServiceKeyStore revoked key=" + keyId + " org=" + orgId);
        }
        return true;
    }

    /**
     * Revokes every unrevoked key of the organization; returns how many it revoked. Takes the
     * organization lock first, so a create still in flight commits before the keys are read and is revoked too.
     */
    public static int revokeAll(Shepherd sh, String orgId, String by) {
        if (orgId == null) return 0;
        lock(sh, "servicekey:" + orgId);
        long now = System.currentTimeMillis();
        int n = 0;
        for (ServiceKey key : list(sh, orgId)) {
            if (key.isRevoked()) continue;
            key.setRevokedAt(now);
            key.setRevokedBy(by);
            n++;
        }
        System.out.println("ServiceKeyStore revoked all keys org=" + orgId + " count=" + n);
        return n;
    }

    /** Looks up an organization by id with a parameterized, cache-bypassing query; null if none. */
    public static Organization organization(Shepherd sh, String orgId) {
        if (orgId == null || orgId.isEmpty()) return null;
        return fresh(sh.getPM(), Organization.class, "id == :id", orgId);
    }

    /**
     * Management view of a key. Never carries the secret or its hash. createdBy/revokedBy are the
     * acting admins' user ids; the *Name fields are their current usernames (null if gone).
     */
    public static JSONObject toJson(Shepherd sh, ServiceKey key) {
        JSONObject out = new JSONObject();
        out.put("keyId", key.getId());
        out.put("label", orNull(key.getLabel()));
        out.put("scopes", new JSONArray(new ArrayList<>(key.getScopeSet())));
        out.put("createdAt", key.getCreatedAt());
        out.put("createdBy", orNull(key.getCreatedBy()));
        out.put("createdByName", orNull(username(sh, key.getCreatedBy())));
        out.put("expiresAt", key.getExpiresAt());
        out.put("revokedAt", orNull(key.getRevokedAt()));
        out.put("revokedBy", orNull(key.getRevokedBy()));
        out.put("revokedByName", orNull(username(sh, key.getRevokedBy())));
        out.put("lastUsedAt", orNull(key.getLastUsedAt()));
        return out;
    }

    private static Object orNull(Object value) {
        return value == null ? JSONObject.NULL : value;
    }

    /** The current username of the user with this id, or null. */
    private static String username(Shepherd sh, String userId) {
        if (userId == null) return null;
        User user = sh.getUserByUUID(userId);
        return user == null ? null : user.getUsername();
    }

    private static String canonicalScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) throw new ServiceKeyException(400, "at least one scope is required");
        for (String s : scopes) {
            if (s == null || !ServiceKeyCredential.SCOPES.contains(s))
                throw new ServiceKeyException(400, "unknown scope; allowed: " + String.join(", ", SCOPE_ORDER));
        }
        List<String> ordered = new ArrayList<>();
        for (String s : SCOPE_ORDER) if (scopes.contains(s)) ordered.add(s);
        return String.join(",", ordered);
    }

    /**
     * The organization's service user. A first-time provision creates it and makes it a member; an
     * existing service user that is no longer a direct member is refused with 409 and left as is.
     * Runs under the org lock.
     */
    private static User serviceUser(Shepherd sh, Organization org) {
        PersistenceManager pm = sh.getPM();
        String orgId = org.getId();
        User svc = fresh(pm, User.class, "serviceOrganizationId == :org", orgId);
        if (svc != null) {
            if (!ServiceKeyAuth.directOrganizationIds(pm, svc.getId()).contains(orgId))
                throw new ServiceKeyException(409,
                    "service account was removed from this organization; a Wildbook administrator must restore it");
            return svc;
        }
        String username = availableUsername(sh, orgId);
        // a hashed random password nobody ever sees; checkPassword refuses service accounts anyway
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String salt = ServletUtilities.getSalt().toHex();
        String hashed = ServletUtilities.hashAndSaltPassword(
            Base64.getUrlEncoder().withoutPadding().encodeToString(raw), salt);
        svc = new User(username, hashed, salt);
        svc.setServiceOrganizationId(orgId);
        pm.makePersistent(svc);
        System.out.println("ServiceKeyStore provisioned service user=" + svc.getId() + " org=" + orgId);
        org.addMember(svc);
        return svc;
    }

    /** svc-<orgId>-<8 random hex>, retried on a taken name; 409 after USERNAME_TRIES taken names. */
    private static String availableUsername(Shepherd sh, String orgId) {
        for (int i = 0; i < USERNAME_TRIES; i++) {
            String username = "svc-" + orgId + "-" + usernameSuffix.get();
            if (sh.getUser(username) == null) return username;
        }
        throw new ServiceKeyException(409, "service account username is already taken");
    }

    private static String randomSuffix() {
        byte[] raw = new byte[4];
        RANDOM.nextBytes(raw);
        StringBuilder sb = new StringBuilder(8);
        for (byte b : raw) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    private static long countLive(PersistenceManager pm, String orgId, long now) {
        Query<?> query = pm.newQuery(ServiceKey.class,
            "organizationId == :org && revokedAt == null && expiresAt > :now");
        try {
            query.setIgnoreCache(true);
            query.setResult("count(this)");
            return ((Number)query.execute(orgId, now)).longValue();
        } finally { query.closeAll(); }
    }

    @SuppressWarnings("unchecked")
    private static List<ServiceKey> freshKeys(PersistenceManager pm, String filter, String value) {
        if (value == null) return new ArrayList<>();
        Query<ServiceKey> query = pm.newQuery(ServiceKey.class, filter);
        try {
            query.setIgnoreCache(true);
            query.setOrdering("createdAt descending");
            List<ServiceKey> out = new ArrayList<>();
            for (ServiceKey key : (Collection<ServiceKey>)query.execute(value)) {
                try {
                    pm.refresh(key);
                } catch (JDOObjectNotFoundException deleted) {
                    continue; // removed between the query and the reload
                }
                out.add(key);
            }
            return out;
        } finally { query.closeAll(); }
    }

    /** Loads one object by a single-parameter filter, bypassing caches, then reloads its state. */
    @SuppressWarnings("unchecked")
    private static <T> T fresh(PersistenceManager pm, Class<T> cls, String filter, String value) {
        Query<T> query = pm.newQuery(cls, filter);
        try {
            query.setIgnoreCache(true);
            Collection<T> rows = (Collection<T>)query.execute(value);
            if (rows.size() != 1) return null;
            T obj = rows.iterator().next();
            try {
                pm.refresh(obj);
            } catch (JDOObjectNotFoundException deleted) {
                return null; // removed between the query and the reload
            }
            return obj;
        } finally { query.closeAll(); }
    }

    /** pg_advisory_xact_lock on a stable 64-bit hash of {@code value}; released at commit or rollback. */
    private static void lock(Shepherd sh, String value) {
        JDOConnection connection = sh.getPM().getDataStoreConnection();
        try (PreparedStatement statement = ((Connection)connection.getNativeConnection())
                .prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, lockKey(value));
            statement.setQueryTimeout(10);
            statement.executeQuery().close();
        } catch (SQLException ex) {
            throw new ServiceKeyException(503, "service key lock unavailable");
        } finally { connection.close(); }
    }

    static long lockKey(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            long key = 0;
            for (int i = 0; i < 8; i++) key = (key << 8) | (d[i] & 0xffL);
            return key;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
