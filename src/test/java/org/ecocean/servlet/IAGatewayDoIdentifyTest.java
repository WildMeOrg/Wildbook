package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jdo.PersistenceManager;

import org.ecocean.Annotation;
import org.ecocean.ia.Task;
import org.ecocean.identity.IAQueryCache;
import org.ecocean.identity.IBEISIA;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Spec §4.0 (fan-out identity) and §4.4 (queued retries are pending). */
class IAGatewayDoIdentifyTest {

    // The Shepherd's PM answers getObjectById from a map, so Task.load and the annotation
    // lookups in _doIdentify resolve to our fixtures. Ids must be distinct across types.
    private static Shepherd shepherdWith(Map<String, Object> byId) {
        PersistenceManager pm = mock(PersistenceManager.class);
        when(pm.newObjectIdInstance(any(Class.class), any())).thenAnswer(inv -> inv.getArgument(1));
        when(pm.getObjectById(any(), anyBoolean())).thenAnswer(inv -> byId.get(inv.getArgument(0)));
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(pm);
        when(sh.getContext()).thenReturn("context0");
        return sh;
    }

    // Records the task's stored status at every commit boundary, so the tests can see what a
    // concurrent poller could have read (spec §4.4: no terminal error before the requeue decision).
    private static void recordCommits(Shepherd sh, Task t, List<String> events) {
        doAnswer(inv -> {
            events.add("commit:" + t.getStoredStatus());
            return null;
        }).when(sh).updateDBTransaction();
        doAnswer(inv -> {
            events.add("commit:" + t.getStoredStatus());
            return null;
        }).when(sh).commitDBTransaction();
    }

    private static Annotation ann(String id) {
        Annotation a = new Annotation();
        a.setId(id);
        a.setIAClass("guitarfish");
        return a;
    }

    private static JSONObject jin(String... annIds) {
        JSONArray ids = new JSONArray();
        for (String id : annIds) ids.put(id);
        return new JSONObject().put("identify", new JSONObject().put("annotationIds", ids))
            .put("opt", new JSONObject("{\"query_config_dict\":{\"sv_on\":true}}"));
    }

    private static JSONObject sendResult(boolean ok) {
        JSONObject r = new JSONObject().put("success", ok);
        if (ok) {
            r.put("status", new JSONObject().put("success", true)).put("response", "job-1");
        } else {
            r.put("error", "WBIA refused");
        }
        return r;
    }

    interface Body { void run() throws Exception; }

    // Partial static mocks through a custom default Answer: the few IBEISIA / IAGateway statics
    // _doIdentify needs are answered here, everything else runs real. (MockedStatic.when(...)
    // on a CALLS_REAL_METHODS mock would execute the real method while defining the stub —
    // waitForIAPriming can block for minutes and requeueJob(null, …) would throw.)
    private static void withIdentifyStubs(JSONObject sendResult, boolean requeueResult, Task task,
        List<String> events, Body body)
    throws Exception {
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("waitForIAPriming")) return null;
                 if (m.equals("queryConfigDict")) return new JSONObject();
                 if (m.equals("beginIdentifyAnnotations")) return sendResult;
                 if (m.equals("log")) return null;
                 return inv.callRealMethod();
             });
             MockedStatic<IAQueryCache> cache = mockStatic(IAQueryCache.class);
             MockedStatic<IAGateway> gw = mockStatic(IAGateway.class, inv -> {
                 if (inv.getMethod().getName().equals("requeueJob")) {
                     events.add("requeue:" + task.getStoredStatus());
                     return requeueResult;
                 }
                 return inv.callRealMethod();
             })) {
            cache.when(() -> IAQueryCache.tryTargetAnnotationsCache(anyString(), any(Annotation.class),
                any(JSONObject.class), any(Shepherd.class))).thenReturn(null);
            body.run();
        }
    }

    private static int firstIndexStartingWith(List<String> events, String prefix) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).startsWith(prefix)) return i;
        }
        return -1;
    }

    private static String lastErrorCode(Task t) {
        JSONObject sd = t.getStatusDetails();
        JSONArray errs = (sd == null) ? null : sd.optJSONArray("errors");
        if ((errs == null) || (errs.length() == 0)) return null;
        return errs.getJSONObject(errs.length() - 1).optString("code", null);
    }

    @Test void failedSendStoresRequeuedWhileARetryIsScheduled() throws Exception {
        Task t = new Task("T1");
        Annotation a = ann("A1");
        Map<String, Object> byId = new HashMap<String, Object>();
        byId.put("T1", t);
        byId.put("A1", a);
        Shepherd sh = shepherdWith(byId);
        List<String> events = new ArrayList<String>();
        recordCommits(sh, t, events);
        JSONObject res = new JSONObject().put("taskId", "T1");
        withIdentifyStubs(sendResult(false), true, t, events, () -> {
            JSONObject out = IAGateway._doIdentify(jin("A1"), res, sh, "context0", "http://wb", false);
            assertTrue(out.getBoolean("success"));
            assertEquals(Task.STATUS_REQUEUED, t.getStoredStatus());
            assertFalse(t.statusInEndState());
            assertEquals("SEND_FAILED", lastErrorCode(t));
            assertNull(t.getCompletionDateInMilliseconds(), "not terminal: no completion date");
            int firstCommit = firstIndexStartingWith(events, "commit:");
            int requeueAt = firstIndexStartingWith(events, "requeue:");
            assertTrue(firstCommit >= 0 && requeueAt >= 0, "events: " + events);
            assertEquals("commit:requeued", events.get(firstCommit), "first commit: " + events);
            assertTrue(events.indexOf("commit:requeued") < requeueAt, "requeued committed before the requeue decision: " + events);
            assertEquals("requeue:requeued", events.get(requeueAt), "events: " + events);
            assertEquals(-1, firstIndexStartingWith(events, "commit:error"), "no terminal error while a retry is queued: " + events);
        });
    }

    @Test void failedSendStoresErrorWhenTheRetryBudgetIsExhausted() throws Exception {
        Task t = new Task("T2");
        Annotation a = ann("A2");
        Map<String, Object> byId = new HashMap<String, Object>();
        byId.put("T2", t);
        byId.put("A2", a);
        Shepherd sh = shepherdWith(byId);
        List<String> events = new ArrayList<String>();
        recordCommits(sh, t, events);
        JSONObject res = new JSONObject().put("taskId", "T2");
        withIdentifyStubs(sendResult(false), false, t, events, () -> {
            IAGateway._doIdentify(jin("A2"), res, sh, "context0", "http://wb", false);
            assertEquals("error", t.getStoredStatus());
            assertTrue(t.statusInEndState());
            assertEquals("SEND_FAILED", lastErrorCode(t));
            assertNotNull(t.getCompletionDateInMilliseconds());
            int pending = events.indexOf("commit:requeued");
            int requeueAt = events.indexOf("requeue:requeued");
            int terminal = events.indexOf("commit:error");
            assertTrue(pending >= 0 && pending < requeueAt && requeueAt < terminal,
                "order commit:requeued, requeue:requeued, commit:error: " + events);
            assertTrue(firstIndexStartingWith(events, "commit:error") > requeueAt,
                "no commit:error before the requeue decision: " + events);
        });
    }





    /** The requeued job re-enters with one annotation: no second fan-out; the stored status is left for the WBIA callback. */
    @Test void retryReentryWithOneAnnotationDoesNotFanOut() throws Exception {
        Task t = new Task("T5");
        t.setStatus(Task.STATUS_REQUEUED);
        Annotation a = ann("A5");
        t.addObject(a);
        Map<String, Object> byId = new HashMap<String, Object>();
        byId.put("T5", t);
        byId.put("A5", a);
        Shepherd sh = shepherdWith(byId);
        withIdentifyStubs(sendResult(true), true, t, new ArrayList<String>(), () -> {
            JSONObject out = IAGateway._doIdentify(jin("A5"), new JSONObject().put("taskId", "T5"), sh, "context0", "http://wb", false);
            assertTrue(out.getBoolean("success"));
            assertFalse(t.hasChildren());
            assertEquals(Task.STATUS_REQUEUED, t.getStoredStatus(), "the WBIA callback overwrites it with completed");
            assertEquals(IBEISIA.STATUS_PROCESSING, a.getIdentificationStatus());
            assertEquals("job-1", out.getJSONArray("tasks").getJSONObject(0).getString("jobId"));
        });
    }








}
