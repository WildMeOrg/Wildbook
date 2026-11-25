package org.ecocean.shepherd.core;

import java.util.Properties;
import org.ecocean.CommonConfiguration;
import org.ecocean.api.submission.SubmissionJson;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AdvisoryLockDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    private static Properties properties;
    private static java.util.Map<String, Properties> configCache;
    private static Properties priorConfig;

    @BeforeAll static void setup() throws Exception {
        TestPMFUtil.closePMF("context0");
        java.lang.reflect.Field field = CommonConfiguration.class.getDeclaredField("contextToPropsCache"); field.setAccessible(true);
        configCache = (java.util.Map<String, Properties>)field.get(null); priorConfig = configCache.get("context0");
        CommonConfiguration.initialize("context0", new Properties());
        properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateAll", "true");
    }
    @AfterAll static void cleanup() {
        TestPMFUtil.closePMF("context0");
        if (configCache != null) { if (priorConfig == null) configCache.remove("context0"); else configCache.put("context0", priorConfig); }
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
