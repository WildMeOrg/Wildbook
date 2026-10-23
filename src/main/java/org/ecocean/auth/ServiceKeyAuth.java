package org.ecocean.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.datastore.JDOConnection;

import org.ecocean.Organization;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;

/**
 * Turns a service key credential into (service user, allowed scopes). Every check reads the
 * database fresh and fails closed. The secret is checked first, so only its holder learns
 * anything beyond the generic "invalid service key".
 *
 * This is the seam a future client-assertion check (private_key_jwt) would replace.
 */
public final class ServiceKeyAuth {
    static final String INVALID = "invalid service key";
    static final String EXPIRED = "service key expired";
    static final String REVOKED = "service key revoked";
    static final String UNAVAILABLE = "service key unavailable; contact your Wildbook administrator";

    /** Hard ceiling on the lifetime of a token minted from a service key. */
    public static final long MAX_EXCHANGE_TTL_MILLIS = 900_000L;

    private static final String CONTEXT = "context0";
    private static final String[] PRIVILEGED_ROLES = {
        "admin", Organization.ROLE_MANAGER, Organization.ROLE_ADMIN
    };

    private ServiceKeyAuth() {}

    public static final class Grant {
        public final String serviceUserId;
        public final String keyId;
        public final String organizationId;
        public final Set<String> scopes;
        public final long keyExpiresAt;

        public Grant(String serviceUserId, String keyId, String organizationId, Set<String> scopes,
            long keyExpiresAt) {
            this.serviceUserId = serviceUserId; this.keyId = keyId; this.organizationId = organizationId;
            this.scopes = Collections.unmodifiableSet(new LinkedHashSet<>(scopes));
            this.keyExpiresAt = keyExpiresAt;
        }
    }

    /**
     * Authenticates {@code credential} and records lastUsedAt in the caller's transaction; the caller
     * commits only if it goes on to mint. Throws ServiceKeyException on any failure.
     */
    public static Grant authenticate(Shepherd sh, String credential, long now) {
        ServiceKey key = verify(sh, credential, now);
        recordUse(sh, key.getId(), now);
        return new Grant(key.getServiceUserId(), key.getId(), key.getOrganizationId(),
            key.getScopeSet(), key.getExpiresAt());
    }

    /** min(configured, 900 s, time until the key expires). */
    public static long exchangeTtlMillis(long configuredTtlMillis, long keyExpiresAt, long now) {
        return Math.min(Math.min(configuredTtlMillis, MAX_EXCHANGE_TTL_MILLIS), keyExpiresAt - now);
    }

    /** All checks except recording the use. Package-visible so tests can interleave a revoke. */
    static ServiceKey verify(Shepherd sh, String credential, long now) {
        Optional<String[]> parsed = ServiceKeyCredential.parse(credential);
        if (!parsed.isPresent()) throw fail(INVALID);
        PersistenceManager pm = sh.getPM();
        // exact id: an id that is not stored byte-for-byte (e.g. upper-cased) is simply unknown
        ServiceKey key = fresh(pm, ServiceKey.class, "id == :id", parsed.get()[0]);
        if (key == null || !ServiceKeyCredential.matches(parsed.get()[1], key.getSecretHash()))
            throw fail(INVALID);
        // only the holder of the real secret gets past this point
        if (key.isRevoked()) throw fail(REVOKED);
        if (key.getExpiresAt() <= now) throw fail(EXPIRED);

        User user = fresh(pm, User.class, "uuid == :id", key.getServiceUserId());
        Organization org = fresh(pm, Organization.class, "id == :id", key.getOrganizationId());
        if (user == null || org == null) throw fail(UNAVAILABLE);
        if (!key.getOrganizationId().equals(user.getServiceOrganizationId())) throw fail(UNAVAILABLE);
        if (!org.hasMember(user)) throw fail(UNAVAILABLE);
        // a service account belongs to its own organization only
        if (!Collections.singleton(key.getOrganizationId()).equals(directOrganizationIds(pm, user.getId())))
            throw fail(UNAVAILABLE);
        if (user.getUsername() == null || isPrivileged(pm, user.getUsername())) throw fail(UNAVAILABLE);
        return key;
    }

    /**
     * Stamps lastUsedAt in one statement that cannot resurrect a key revoked since verify() read it:
     * zero rows updated means the key was revoked in the meantime.
     */
    static void recordUse(Shepherd sh, String keyId, long now) {
        PersistenceManager pm = sh.getPM();
        int updated;
        JDOConnection connection = pm.getDataStoreConnection();
        try (PreparedStatement statement = ((Connection)connection.getNativeConnection()).prepareStatement(
                "UPDATE \"SERVICE_KEY\" SET \"LASTUSEDAT\" = ? WHERE \"ID\" = ? AND \"REVOKEDAT\" IS NULL")) {
            statement.setLong(1, now);
            statement.setString(2, keyId);
            statement.setQueryTimeout(10);
            updated = statement.executeUpdate();
        } catch (SQLException ex) {
            throw new ServiceKeyException(503, "service key check unavailable");
        } finally {
            connection.close();
        }
        if (updated != 1) throw fail(REVOKED);
        // the row changed behind DataNucleus; drop any cached copy so later reads see lastUsedAt
        try {
            pm.getPersistenceManagerFactory().getDataStoreCache().evict(
                pm.newObjectIdInstance(ServiceKey.class, keyId));
        } catch (RuntimeException ignore) {}
    }

    /** Ids of the organizations {@code userId} is a direct member of, read from the datastore (no cache). */
    static Set<String> directOrganizationIds(PersistenceManager pm, String userId) {
        Set<String> ids = new LinkedHashSet<>();
        if (userId == null) return ids;
        JDOConnection connection = pm.getDataStoreConnection();
        try (PreparedStatement statement = ((Connection)connection.getNativeConnection()).prepareStatement(
                "SELECT \"ORGANIZATION_ID\" FROM \"ORGANIZATION_MEMBERS\" WHERE \"USER_ID\" = ?")) {
            statement.setString(1, userId);
            statement.setQueryTimeout(10);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) ids.add(rows.getString(1));
            }
        } catch (SQLException ex) {
            throw new ServiceKeyException(503, "service key check unavailable");
        } finally {
            connection.close();
        }
        return ids;
    }

    private static boolean isPrivileged(PersistenceManager pm, String username) {
        Query<?> query = pm.newQuery(Role.class,
            "username == :username && context == :context && (rolename == :a || rolename == :b || rolename == :c)");
        try {
            query.setIgnoreCache(true);
            query.setResult("count(this)");
            Object n = query.executeWithArray(username, CONTEXT, PRIVILEGED_ROLES[0], PRIVILEGED_ROLES[1],
                PRIVILEGED_ROLES[2]);
            return ((Number)n).longValue() > 0;
        } finally { query.closeAll(); }
    }

    /** Loads one object by a single-parameter filter, bypassing caches, then reloads its state from the datastore. */
    @SuppressWarnings("unchecked")
    private static <T> T fresh(PersistenceManager pm, Class<T> cls, String filter, String value) {
        if (value == null) return null;
        Query<T> query = pm.newQuery(cls, filter);
        try {
            query.setIgnoreCache(true);
            Collection<T> rows = (Collection<T>)query.execute(value);
            if (rows.size() != 1) return null;
            T obj = rows.iterator().next();
            try {
                pm.refresh(obj);
            } catch (JDOObjectNotFoundException deleted) {
                // removed between the query and the reload: treat it as absent
                return null;
            }
            return obj;
        } finally { query.closeAll(); }
    }

    private static ServiceKeyException fail(String message) {
        return new ServiceKeyException(401, message);
    }
}
