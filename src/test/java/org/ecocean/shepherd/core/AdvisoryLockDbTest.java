package org.ecocean.shepherd.core;

import java.util.Properties;
import org.ecocean.api.submission.SubmissionJson;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AdvisoryLockDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    private static TestPostgresContext db;
    private static Properties properties;

    @BeforeAll static void setup() throws Exception {
        db = TestPostgresContext.open(postgres);
        properties = db.properties;
    }
    @AfterAll static void cleanup() {
        if (db != null) db.close();
    }

    private static Shepherd begin() {
        Shepherd sh = new Shepherd("context0", properties);
        sh.beginDBTransaction();
        return sh;
    }

    @Test void secondTransactionCannotTakeHeldLock() throws Exception {
        long k = AdvisoryLock.key("bulk-import:" + java.util.UUID.randomUUID());
        Shepherd a = begin();
        Shepherd b = begin();
        try {
            assertTrue(AdvisoryLock.tryXact(a, k));
            assertFalse(AdvisoryLock.tryXact(b, k), "held by A's open transaction");
            assertTrue(a.commitDBTransactionWithStatus());
            b.rollbackDBTransaction();
            b.beginDBTransaction();
            assertTrue(AdvisoryLock.tryXact(b, k), "released when A's transaction ended");
        } finally {
            a.rollbackAndClose();
            b.rollbackAndClose();
        }
    }

    @Test void keyMatchesSubmissionHash() {
        for (String value : new String[] { "x", "submission:é漢字🐋" })
            assertEquals(Long.parseUnsignedLong(SubmissionJson.hash(value).substring(0, 16), 16),
                AdvisoryLock.key(value), value);
    }
}
