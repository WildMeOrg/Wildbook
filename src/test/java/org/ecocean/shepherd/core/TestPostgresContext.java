package org.ecocean.shepherd.core;

import java.util.Map;
import java.util.Properties;

import org.ecocean.CommonConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Points "context0" at a Testcontainers PostgreSQL database for one test class and puts the
 * previous configuration back afterwards. open() in @BeforeAll, close() in @AfterAll; Shepherds
 * are built with {@code new Shepherd("context0", properties)}.
 */
public final class TestPostgresContext {
    public static final String CONTEXT = "context0";
    public final Properties properties;
    private final Map<String, Properties> configCache;
    private final Properties priorConfig;

    private TestPostgresContext(Properties properties, Map<String, Properties> configCache,
        Properties priorConfig) {
        this.properties = properties;
        this.configCache = configCache;
        this.priorConfig = priorConfig;
    }

    @SuppressWarnings("unchecked")
    public static TestPostgresContext open(PostgreSQLContainer<?> postgres)
    throws Exception {
        // everything that can fail runs before the global configuration is replaced
        Properties properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateAll", "true");
        java.lang.reflect.Field field = CommonConfiguration.class.getDeclaredField(
            "contextToPropsCache");
        field.setAccessible(true);
        Map<String, Properties> configCache = (Map<String, Properties>)field.get(null);
        Properties priorConfig = configCache.get(CONTEXT);
        TestPMFUtil.closePMF(CONTEXT);
        CommonConfiguration.initialize(CONTEXT, new Properties());
        return new TestPostgresContext(properties, configCache, priorConfig);
    }

    /** The live configuration of the context, for tests that change a setting and put it back. */
    public Properties configuration() {
        return configCache.get(CONTEXT);
    }

    public void close() {
        TestPMFUtil.closePMF(CONTEXT);
        if (priorConfig == null) configCache.remove(CONTEXT);
        else configCache.put(CONTEXT, priorConfig);
    }
}
