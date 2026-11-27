package org.ecocean.shepherd.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.jdo.datastore.JDOConnection;

/**
 * PostgreSQL transaction-level advisory locks taken on a Shepherd's open transaction. Both
 * calls need an active transaction on {@code sh}; the lock lasts until that transaction
 * commits or rolls back.
 */
public final class AdvisoryLock {
    private AdvisoryLock() {}

    /** A stable 64-bit key for {@code value}: the first 8 bytes of SHA-256 of its UTF-8, big-endian. */
    public static long key(String value) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            long key = 0;
            for (int i = 0; i < 8; i++) key = (key << 8) | (d[i] & 0xffL);
            return key;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** pg_try_advisory_xact_lock: true when acquired, false when another transaction holds it. */
    public static boolean tryXact(Shepherd sh, long key) throws SQLException {
        return run(sh, key, true);
    }

    /** pg_advisory_xact_lock: waits until acquired. */
    public static void xact(Shepherd sh, long key) throws SQLException {
        run(sh, key, false);
    }

    private static boolean run(Shepherd sh, long key, boolean immediate) throws SQLException {
        JDOConnection connection = sh.getPM().getDataStoreConnection();
        try (PreparedStatement statement = ((Connection)connection.getNativeConnection())
                .prepareStatement(immediate ? "SELECT pg_try_advisory_xact_lock(?)" : "SELECT pg_advisory_xact_lock(?)")) {
            statement.setLong(1, key);
            statement.setQueryTimeout(10);
            try (ResultSet result = statement.executeQuery()) {
                // the blocking form returns void; it has the lock once the query returns
                return !immediate || (result.next() && result.getBoolean(1));
            }
        } finally { connection.close(); }
    }
}
