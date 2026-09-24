package org.ecocean.api.submission;

import org.ecocean.CommonConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionPolicyTest {
    @Test void controlsReadFreshInstallationPropertiesAndIgnoreRequestConfigurationCache() {
        try (MockedStatic<CommonConfiguration> config = mockStatic(CommonConfiguration.class)) {
            config.when(() -> CommonConfiguration.getProperty("submissions.enabled", "context0")).thenReturn("true");
            config.when(() -> CommonConfiguration.getApiAccessProperty("submissions.enabled", "context0")).thenReturn("true", "false");
            config.when(() -> CommonConfiguration.getApiAccessProperty("submissions.commitEnabled", "context0")).thenReturn("true", "false");
            config.when(() -> CommonConfiguration.getApiAccessProperty("submissions.workerEnabled", "context0")).thenReturn("true", "false");
            assertTrue(SubmissionPolicy.enabled("context0")); assertFalse(SubmissionPolicy.enabled("context0"));
            assertTrue(SubmissionPolicy.commitEnabled("context0")); assertFalse(SubmissionPolicy.commitEnabled("context0"));
            assertTrue(SubmissionPolicy.workerEnabled("context0")); assertFalse(SubmissionPolicy.workerEnabled("context0"));
            config.verify(() -> CommonConfiguration.getProperty("submissions.enabled", "context0"), never());
        }
    }
}
