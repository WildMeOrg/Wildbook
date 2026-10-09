package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ScheduledExecutorService;
import javax.jdo.PersistenceManager;
import org.ecocean.OpenSearch;
import org.ecocean.SystemValue;
import org.ecocean.security.PermissionsAudit.Result;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * The permissions audit is scheduled, not signalled: it runs on a fixed delay after each
 * completion, sooner after an incomplete run (bounded), only in the JVM configured as the runner,
 * and its executor stops on undeploy. Nothing is acknowledged; the last-run timestamp is for the log.
 */
class PermissionsAuditSchedulingTest {
    @AfterEach void tearDown() {
        OpenSearch.BACKGROUND_PERMISSIONS_RUNNER = true;
        OpenSearch.shutdownBackground();
    }

    @Test void completedAuditWaitsTheNormalDelay() {
        assertEquals(OpenSearch.BACKGROUND_PERMISSIONS_MINUTES,
            OpenSearch.nextPermissionsDelayMinutes(true, 0));
        assertEquals(OpenSearch.BACKGROUND_PERMISSIONS_MINUTES,
            OpenSearch.nextPermissionsDelayMinutes(true, 7), "a completion resets the streak");
    }

    @Test void incompleteAuditRetriesSoonerButNotForever() {
        for (int streak = 1; streak <= 4; streak++) {
            assertEquals(OpenSearch.BACKGROUND_PERMISSIONS_RETRY_MINUTES,
                OpenSearch.nextPermissionsDelayMinutes(false, streak), "streak " + streak);
        }
        assertEquals(OpenSearch.BACKGROUND_PERMISSIONS_MINUTES,
            OpenSearch.nextPermissionsDelayMinutes(false, 5),
            "five incomplete audits in a row fall back to the normal delay (no tight loop)");
        assertEquals(OpenSearch.BACKGROUND_PERMISSIONS_MINUTES,
            OpenSearch.nextPermissionsDelayMinutes(false, 12));
    }

    @Test void runStampsTheTimestampWhateverTheOutcomeAndReportsCompletion() {
        for (boolean completed : new boolean[] { true, false }) {
            final Result result = new Result();
            result.completed = completed;
            try (MockedStatic<PermissionsAudit> audit = mockStatic(PermissionsAudit.class);
                MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class, (mock, ctx) -> {
                    PersistenceManager pm = mock(PersistenceManager.class);
                    when(mock.getPM()).thenReturn(pm);
                })) {
                audit.when(() -> PermissionsAudit.run(anyString(), any())).thenReturn(result);

                boolean rtn = OpenSearch.runPermissionsAudit("context0");

                assertEquals(completed, rtn);
                audit.verify(() -> PermissionsAudit.run(anyString(), any()));
                assertEquals(1, shepherds.constructed().size(), "one short transaction for the stamp");
                verify(shepherds.constructed().get(0).getPM()).makePersistent(any(SystemValue.class));
            }
        }
    }

    @Test void configComesFromTheBackgroundProperties() {
        PermissionsAudit.Config c = OpenSearch.permissionsAuditConfig();
        assertEquals(OpenSearch.BACKGROUND_PERMISSIONS_PAGE_SIZE, c.pageSize);
        assertEquals(OpenSearch.BACKGROUND_PERMISSIONS_MAX_REPAIRS, c.repairCap);
        assertEquals(OpenSearch.BACKGROUND_PERMISSIONS_MAX_REBUILDS, c.rebuildCap);
        assertEquals(OpenSearch.BACKGROUND_PERMISSIONS_SCROLL_KEEP_ALIVE, c.keepAlive);
    }

    @Test void schedulerStartsOnlyInTheDesignatedRunner() {
        OpenSearch.BACKGROUND_PERMISSIONS_RUNNER = false;
        assertFalse(OpenSearch.startPermissionsAuditScheduler("context0"),
            "a JVM that is not the runner never audits");
        OpenSearch.BACKGROUND_PERMISSIONS_RUNNER = true;
        assertTrue(OpenSearch.startPermissionsAuditScheduler("context0"));
        ScheduledExecutorService exec = OpenSearch.backgroundExecutor();
        assertNotNull(exec);
        assertFalse(exec.isShutdown());
    }

    @Test void shutdownStopsTheExecutorAndAFreshStartGetsANewOne() {
        assertTrue(OpenSearch.startPermissionsAuditScheduler("context0"));
        ScheduledExecutorService first = OpenSearch.backgroundExecutor();
        OpenSearch.shutdownBackground();
        assertTrue(first.isShutdown(), "undeploy stops the scheduler");
        assertTrue(OpenSearch.startPermissionsAuditScheduler("context0"), "a redeploy starts a fresh one");
        assertFalse(OpenSearch.backgroundExecutor().isShutdown());
        assertTrue(first != OpenSearch.backgroundExecutor());
    }
}
