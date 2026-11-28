package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import javax.jdo.PersistenceManager;

import org.ecocean.Taxonomy;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.IAGateway;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/**
 * Bulk-import boxes PR A, task 5 (spec 4.4): an ml-service job is persisted with a checked
 * commit before it is published; a child whose persist or commit fails is never published.
 * The per-asset detection enqueue keeps its payload and now stores the job as its resume message.
 */
class IAEnqueueMlServiceJobTest {
    private static final String CTX = "context0";

    private static JSONObject job(String taskId) {
        return new JSONObject().put("mlServiceV2", true).put("annotationId", "ann-1")
            .put("taxonomyString", "Genus species").put("taskId", taskId).put("__context", CTX);
    }

    private static final class Harness {
        final Shepherd sh = mock(Shepherd.class);
        final PersistenceManager pm = mock(PersistenceManager.class);
        Harness(boolean commitResult) {
            when(sh.getPM()).thenReturn(pm);
            when(pm.makePersistent(any())).thenAnswer(inv -> inv.getArgument(0));
            when(sh.commitDBTransactionWithStatus()).thenReturn(commitResult);
        }
    }

    @Test void failedCommitDoesNotPublish() {
        Harness h = new Harness(false);
        Task child = new Task();
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class)) {
            assertFalse(IA.enqueueMlServiceJob(h.sh, child, job(child.getId()), CTX));
            gateway.verify(() -> IAGateway.addToDetectionQueue(anyString(), anyString()), never());
        }
        verify(h.sh).beginDBTransaction();
    }

    @Test void successfulCommitPublishesTheJobAndStoresTheResumeMessage() {
        Harness h = new Harness(true);
        Task child = new Task();
        JSONObject job = job(child.getId());
        AtomicReference<String> published = new AtomicReference<String>();
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenAnswer(call -> {
                published.set(call.getArgument(1, String.class)); return true; });
            assertTrue(IA.enqueueMlServiceJob(h.sh, child, job, CTX));
        }
        assertEquals(job.toString(), child.getQueueResumeMessage());
        assertEquals(job.toString(), published.get());
        verify(h.pm).makePersistent(child);
        verify(h.sh).commitDBTransactionWithStatus();
        verify(h.sh).beginDBTransaction();
    }

    @Test void persistExceptionReturnsFalseWithoutPublishing() {
        Harness h = new Harness(true);
        doThrow(new RuntimeException("dn")).when(h.pm).makePersistent(any());
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class)) {
            assertFalse(IA.enqueueMlServiceJob(h.sh, new Task(), job("t"), CTX));
            gateway.verify(() -> IAGateway.addToDetectionQueue(anyString(), anyString()), never());
        }
        verify(h.sh).rollbackDBTransaction();
        verify(h.sh, never()).commitDBTransactionWithStatus();
    }

    @Test void queueIoExceptionReturnsFalse() {
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenThrow(new IOException("disk"));
            assertFalse(IA.publishMlServiceJob(CTX, job("t")));
        }
    }

    @Test void enqueueOneAssetForMlServiceStillEmitsTheSamePayload() { // (regression)
        Harness h = new Harness(true);
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.getId()).thenReturn("42");
        when(ma.getAnnotations()).thenReturn(null);
        Taxonomy taxy = new Taxonomy("Genus species");
        AtomicReference<String> published = new AtomicReference<String>();
        try (MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class)) {
            gateway.when(() -> IAGateway.addToDetectionQueue(eq(CTX), anyString())).thenAnswer(call -> {
                published.set(call.getArgument(1, String.class)); return true; });
            assertTrue(IA.enqueueOneAssetForMlService(h.sh, ma, taxy, null, CTX, "https://x"));
        }
        JSONObject msg = new JSONObject(published.get());
        assertTrue(msg.getBoolean("mlServiceV2"));
        assertEquals("42", msg.getString("mediaAssetId"));
        assertEquals("Genus species", msg.getString("taxonomyString"));
        assertEquals(CTX, msg.getString("__context"));
        assertEquals("https://x", msg.getString("__baseUrl"));
        ArgumentCaptor<Object> persisted = ArgumentCaptor.forClass(Object.class);
        verify(h.pm).makePersistent(persisted.capture());
        Task child = (Task)persisted.getValue();
        assertEquals(child.getId(), msg.getString("taskId"));
        assertTrue(child.getParameters().getBoolean("ibeis.detection"));
        assertEquals(published.get(), child.getQueueResumeMessage());
    }
}
