package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class OpenSearchConfigurationTest {
    @Test void booleanConfigurationOnlyAcceptsExplicitTrueOrFalse() {
        Properties props = new Properties();
        try (MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class, CALLS_REAL_METHODS)) {
            os.when(() -> OpenSearch.getConfigurationProperties("test")).thenReturn(props);
            for (boolean fallback : new boolean[] { true, false }) {
                for (String value : new String[] { "true", " TRUE ", "false", " False " }) {
                    props.setProperty("backgroundPermissionsRunner", value);
                    assertEquals(Boolean.valueOf(value.trim()), OpenSearch.getConfigurationValue(
                        "test", "backgroundPermissionsRunner", fallback));
                }
                for (String value : new String[] { "ture", "yes", "1", "", "   " }) {
                    props.setProperty("backgroundPermissionsRunner", value);
                    assertEquals(fallback, OpenSearch.getConfigurationValue(
                        "test", "backgroundPermissionsRunner", fallback), value);
                }
                props.remove("backgroundPermissionsRunner");
                assertEquals(fallback, OpenSearch.getConfigurationValue(
                    "test", "backgroundPermissionsRunner", fallback));
            }
        }
    }
}
