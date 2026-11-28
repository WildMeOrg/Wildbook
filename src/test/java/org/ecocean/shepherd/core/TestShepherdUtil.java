package org.ecocean.shepherd.core;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Shared helpers for Testcontainers tests that need a Shepherd in a particular state. */
public final class TestShepherdUtil {
    private TestShepherdUtil() {}

    /** Aborts sh's open transaction with a failed statement, and checks Postgres now refuses it. */
    public static void abortTransaction(Shepherd sh) {
        assertThrows(Exception.class, () -> sh.getPM().newQuery("javax.jdo.query.SQL",
            "SELECT * FROM no_such_table_1789").execute());
        assertThrows(Exception.class, () -> sh.getPM().newQuery("javax.jdo.query.SQL",
            "SELECT 1").execute(), "Postgres refuses every statement in an aborted transaction");
    }
}
