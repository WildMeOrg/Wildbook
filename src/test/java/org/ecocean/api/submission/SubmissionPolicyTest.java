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

    @Test void persistedRoleRevocationTakesEffectAndLegacyAllowlistCannotRestoreAccess() {
        org.ecocean.User user = mock(org.ecocean.User.class);
        when(user.getUsername()).thenReturn("pilot");
        javax.jdo.Query query = mock(javax.jdo.Query.class);
        when(query.execute("pilot", org.ecocean.Role.API_SUBMISSION, "context0")).thenReturn(1L, 0L);
        javax.jdo.PersistenceManager pm = mock(javax.jdo.PersistenceManager.class);
        when(pm.newQuery(eq(org.ecocean.Role.class), anyString())).thenReturn(query);
        try (org.mockito.MockedConstruction<org.ecocean.shepherd.core.Shepherd> shepherds =
                mockConstruction(org.ecocean.shepherd.core.Shepherd.class, (sh, c) -> {
                    when(sh.getUserByUUID("pilot-id")).thenReturn(user);
                    when(sh.getPM()).thenReturn(pm); when(sh.getContext()).thenReturn("context0");
                });
             MockedStatic<CommonConfiguration> config = mockStatic(CommonConfiguration.class)) {
            config.when(() -> CommonConfiguration.getApiAccessProperty("submissions.enabled", "context0")).thenReturn("true");
            config.when(() -> CommonConfiguration.getApiAccessProperty("submissions.allowedUserIds", "context0")).thenReturn("pilot-id");
            SubmissionPolicy.requireAdmission("context0", "pilot-id");
            SubmissionException denied = assertThrows(SubmissionException.class,
                () -> SubmissionPolicy.requireAdmission("context0", "pilot-id"));
            assertEquals(403, denied.status);
            for (org.ecocean.shepherd.core.Shepherd sh : shepherds.constructed()) verify(sh).rollbackAndClose();
            verify(query, times(2)).setIgnoreCache(true);
            verify(query, times(2)).closeAll();
        }
    }
    @Test void missingAccountIsNotEnrolledAndClosesTransaction() {
        try (org.mockito.MockedConstruction<org.ecocean.shepherd.core.Shepherd> shepherds =
                mockConstruction(org.ecocean.shepherd.core.Shepherd.class)) {
            assertFalse(SubmissionPolicy.enrolled("context0", "missing"));
            verify(shepherds.constructed().get(0)).rollbackAndClose();
        }
    }
}
