package org.ecocean.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import org.ecocean.Encounter;
import org.ecocean.OpenSearch;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/**
 * Encounter.opensearchIndexPermissionsBackground() is the scheduler-facing wrapper around the
 * permissions pass. Its contract: the "needed" flag is cleared and the last-run timestamp advanced
 * ONLY when the pass reports completion, so an incomplete pass (aborted precompute, aborted
 * encounter loop, or a failed viewUsers write) is retried on the next tick instead of being
 * forgotten until the forced run.
 */
class PermissionsPassWrapperTest {
    @Test void incompletePassLeavesTheFlagAndTimestampAlone() {
        Shepherd sh = mock(Shepherd.class);
        try (MockedStatic<Encounter> enc = mockStatic(Encounter.class, Answers.CALLS_REAL_METHODS);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            // no previous run recorded (null timestamp) -> the wrapper runs the pass
            enc.when(Encounter::opensearchIndexPermissions).thenReturn(false);

            Encounter.opensearchIndexPermissionsBackground(sh);

            enc.verify(Encounter::opensearchIndexPermissions, times(1));
            os.verify(() -> OpenSearch.setPermissionsNeeded(any(Shepherd.class), anyBoolean()), never());
            os.verify(() -> OpenSearch.setPermissionsTimestamp(any(Shepherd.class)), never());
        }
    }

    @Test void completedPassClearsTheFlagAndAdvancesTheTimestamp() {
        Shepherd sh = mock(Shepherd.class);
        try (MockedStatic<Encounter> enc = mockStatic(Encounter.class, Answers.CALLS_REAL_METHODS);
            MockedStatic<OpenSearch> os = mockStatic(OpenSearch.class)) {
            enc.when(Encounter::opensearchIndexPermissions).thenReturn(true);

            Encounter.opensearchIndexPermissionsBackground(sh);

            os.verify(() -> OpenSearch.setPermissionsNeeded(eq(sh), eq(false)), times(1));
            os.verify(() -> OpenSearch.setPermissionsTimestamp(eq(sh)), times(1));
        }
    }
}
