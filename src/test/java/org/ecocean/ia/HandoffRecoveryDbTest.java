package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.ecocean.Encounter;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Bulk-import boxes PR A, task 4 (spec 4.4), on a REAL Shepherd: when the hand-off's commit is
 * rolled back, or its publish fails, the recovery reloads the ImportTask and the root after the
 * rollback, restores the link between them, and leaves one error child holding every box, all
 * visible from a fresh persistence manager.
 */
@Testcontainers
class HandoffRecoveryDbTest extends IdentificationDbTestSupport {
    private static final String BASE = "https://base.test";

    static final class Setup {
        String importId;
        List<String> ids;
    }

    static Setup boxedImport() throws Exception {
        Shepherd fx = open("fixture");
        Setup s = new Setup();
        s.importId = nextImportId();
        Encounter enc = boxedEncounter(fx, "Pseudobatos", "lentiginosus", s.importId, 2);
        s.ids = annotationIds(enc);
        ImportTask itask = new ImportTask(null, s.importId);
        itask.addEncounter(enc);
        itask.setPassedParameters(new JSONObject().put(ImportTask.PARAM_ANNOTATIONS_IMPORTED, true));
        fx.storeNewImportTask(itask);
        fx.commitDBTransaction();
        fx.closeDBTransaction();
        return s;
    }

    static JSONObject data(String importId) {
        return new JSONObject().put("taskParameters",
            new JSONObject().put("importTaskId", importId).put("skipIdent", false))
            .put("bulkImport", new JSONObject());
    }

    static void assertErrorTree(String importId, String code, List<String> ids, int expectedChildren) {
        Shepherd check = open("check");
        try {
            ImportTask reloaded = check.getImportTask(importId);
            Task root = reloaded.getIATask();
            assertNotNull(root, "the ImportTask still references its IA root after the rollback");
            assertEquals("error", root.getStoredStatus());
            assertEquals(code, errorCode(root));
            assertEquals(expectedChildren, root.getChildren().size());
            Task errorChild = null;
            for (Task t : root.getChildren()) if ("error".equals(t.getStoredStatus())) errorChild = t;
            assertNotNull(errorChild, "an error child exists");
            assertEquals(code, errorCode(errorChild));
            for (String id : ids) assertTrue(errorChild.holdsAnnotationId(id), "the error child holds box " + id);
            assertTrue(reloaded.getLog() != null && reloaded.getLog().toString().contains(code), "the ImportTask logged it");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void rolledBackCommitLeavesTheImportLinkedToAnErrorTreeHoldingEveryBox() throws Exception {
        Setup s = boxedImport();
        Shepherd real = open("handoff");
        Shepherd sh = spy(real);
        AtomicInteger commits = new AtomicInteger();
        doAnswer(inv -> {
            if (commits.incrementAndGet() == 1) {   // the first commit fails and, as a real failure does, rolls back
                real.rollbackDBTransaction();
                return false;
            }
            return inv.callRealMethod();
        }).when(sh).commitDBTransactionWithStatus();
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            JSONObject res = IAGateway.handleBulkImport(data(s.importId), new JSONObject(), sh, CTX, BASE);
            assertFalse(res.getBoolean("success"));
            assertEquals("HANDOFF_COMMIT", res.getString("error"));
            gateway.verify(() -> IAGateway.addToDetectionQueue(anyString(), anyString()), never());
        } finally {
            real.rollbackAndClose();
        }
        assertErrorTree(s.importId, "HANDOFF_COMMIT", s.ids, 1);   // the root was re-created: its never-persisted request child is gone, the error child is there
    }

    @Test void failedPublishLeavesTheImportLinkedToAnErrorTreeHoldingEveryBox() throws Exception {
        Setup s = boxedImport();
        Shepherd real = open("handoff");
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenReturn(false);
            JSONObject res = IAGateway.handleBulkImport(data(s.importId), new JSONObject(), real, CTX, BASE);
            assertFalse(res.getBoolean("success"));
            assertEquals("HANDOFF_PUBLISH", res.getString("error"));
        } finally {
            real.rollbackAndClose();
        }
        assertErrorTree(s.importId, "HANDOFF_PUBLISH", s.ids, 2);   // the committed request child plus the error child
    }
}
