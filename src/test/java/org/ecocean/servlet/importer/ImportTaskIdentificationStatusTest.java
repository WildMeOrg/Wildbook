package org.ecocean.servlet.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import org.ecocean.Annotation;
import org.ecocean.ia.Task;
import org.ecocean.identity.IdentityServiceLog;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Spec §4.4: one representative per option; pending > error > completed; seven-day maximum age. */
class ImportTaskIdentificationStatusTest {

    private static Annotation annot(String id) {
        Annotation a = new Annotation();
        a.setId(id);
        a.setIAClass("guitarfish");
        return a;
    }

    private static Set<String> ids(String... s) {
        return new HashSet<String>(Arrays.asList(s));
    }

    private static Task umbrella(Annotation... anns) {
        Task u = new Task();
        u.setParameters(new JSONObject().put(Task.PARAM_RUN_ID, u.getId()));
        for (Annotation a : anns) u.addObject(a);
        return u;
    }

    private static Task umbrellaAsOption(Annotation... anns) {
        Task u = umbrella(anns);
        u.addParameter(Task.PARAM_OPTION, true);
        u.addParameter("ibeis.identification", new JSONObject());
        return u;
    }

    private static Task option(Task parent, boolean vector, Annotation... anns) {
        Task t = new Task(parent);
        t.addParameter(Task.PARAM_OPTION, true);
        t.addParameter("ibeis.identification", new JSONObject());
        if (vector) t.addParameter(Task.PARAM_V2_MATCH, true);
        for (Annotation a : anns) t.addObject(a);
        return t;
    }

    private static Task leaf(Task option, String status, Annotation a) {
        Task l = new Task(option);
        JSONObject p = l.getParameters();
        p.remove(Task.PARAM_OPTION);
        l.setParameters(p);
        l.addObject(a);
        if (status != null) l.setStatus(status);
        return l;
    }

    private static Shepherd shepherd() {
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(mock(PersistenceManager.class));
        return sh;
    }

    interface Body { void run() throws Exception; }

    // null stored statuses make Task.getStatus read WBIA logs; none exist -> "waiting to queue"
    private static void withNoLogs(Body body) throws Exception {
        try (MockedStatic<IdentityServiceLog> logs = mockStatic(IdentityServiceLog.class)) {
            logs.when(() -> IdentityServiceLog.loadByTaskID(anyString(), anyString(), any(Shepherd.class)))
                .thenReturn(new ArrayList<IdentityServiceLog>());
            body.run();
        }
    }

    private static ImportTask.AnnotationIdStatus status(Annotation a, List<Task> direct, Set<String> enc, Shepherd sh) {
        return ImportTask.identificationStatusForAnnotation(a, direct, "IMP1", enc, true, sh);
    }

    @Test void allOptionsCompletedReadsCompleted() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task v = option(u, true, a);
        Task vl = leaf(v, "completed", a);
        v.setStatus("completed");
        Task h = option(u, false, a);
        h.setStatus("completed");
        withNoLogs(() -> {
            ImportTask.AnnotationIdStatus s = status(a, Arrays.asList(vl, h, v, u), ids("A"), shepherd());
            assertEquals("completed", s.status);
            assertFalse(s.skipped);
        });
    }

    @Test void pendingHotspotterBeatsCompletedVector() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task v = option(u, true, a);
        Task vl = leaf(v, "completed", a);
        v.setStatus("completed");
        Task h = option(u, false, a);   // stored status null: queued at WBIA
        withNoLogs(() -> {
            assertEquals("waiting to queue", status(a, Arrays.asList(vl, h, v, u), ids("A"), shepherd()).status);
        });
    }

    @Test void errorVectorWithPendingHotspotterReadsPending() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task v = option(u, true, a);
        Task vl = leaf(v, "error", a);
        v.setStatus("error");
        Task h = option(u, false, a);   // queued at WBIA: pending beats the error
        withNoLogs(() -> {
            assertEquals("waiting to queue", status(a, Arrays.asList(vl, h, v, u), ids("A"), shepherd()).status);
        });
    }

    @Test void requeuedHotspotterIsPending() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task v = option(u, true, a);
        leaf(v, "completed", a);
        v.setStatus("completed");
        Task h = option(u, false, a);
        h.setStatus(Task.STATUS_REQUEUED);
        withNoLogs(() -> {
            assertEquals(Task.STATUS_REQUEUED, status(a, Arrays.asList(h, v, u), ids("A"), shepherd()).status);
        });
    }

    @Test void errorVectorWithCompletedHotspotterReadsError() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task v = option(u, true, a);
        Task vl = leaf(v, "error", a);
        v.setStatus("error");
        Task h = option(u, false, a);
        h.setStatus("completed");
        withNoLogs(() -> {
            assertEquals("error", status(a, Arrays.asList(vl, h, v, u), ids("A"), shepherd()).status);
        });
    }

    /** Partial vector dispatch: V_A exists, V_B was never created; the option itself represents B. */
    @Test void failedVectorOptionWithoutALeafForThisAnnotationReadsErrorThroughTheOption() throws Exception {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = umbrella(a, b);
        Task v = option(u, true, a, b);
        leaf(v, "error", a);
        v.setStatus("error");
        Task h = option(u, false, a, b);
        Task hb = leaf(h, "completed", b);
        withNoLogs(() -> {
            assertEquals("error", status(b, Arrays.asList(hb, h, v, u), ids("A", "B"), shepherd()).status);
        });
    }

    /** _doIdentify mid fan-out: H_A exists, H_B not yet; the option represents B and reads pending. */
    @Test void hotspotterMidFanOutReadsPendingForTheAnnotationWithoutAChild() throws Exception {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = umbrella(a, b);
        Task v = option(u, true, a, b);
        Task vb = leaf(v, "completed", b);
        v.setStatus("completed");
        Task h = option(u, false, a, b);
        leaf(h, null, a);
        withNoLogs(() -> {
            assertEquals("waiting to queue", status(b, Arrays.asList(vb, h, v, u), ids("A", "B"), shepherd()).status);
        });
    }

    /** Single-HotSpotter umbrella U(A,B) mid fan-out next to a newer completed run for A: B's representative is U. */
    @Test void singleHotspotterUmbrellaMidFanOutStaysPendingForTheUnfannedAnnotation() throws Exception {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = umbrellaAsOption(a, b);
        leaf(u, "completed", a);
        Task newer = umbrellaAsOption(a);
        newer.setStatus("completed");
        withNoLogs(() -> {
            assertEquals("waiting to queue", status(b, Arrays.asList(u), ids("A", "B"), shepherd()).status);
            assertEquals("completed", status(a, Arrays.asList(newer, u), ids("A", "B"), shepherd()).status);
        });
    }

    @Test void noWorkUmbrellaReportsItsOwnError() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        u.setStatus("error");
        withNoLogs(() -> {
            ImportTask.AnnotationIdStatus s = status(a, Arrays.asList(u), ids("A"), shepherd());
            assertEquals("error", s.status);
            assertFalse(s.skipped);
        });
    }

    @Test void classWithoutOptionReadsCompletedAndSkipped() throws Exception {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = umbrella(a, b);
        Task h = option(u, false, a);   // B's class had no option
        h.setStatus("completed");
        withNoLogs(() -> {
            ImportTask.AnnotationIdStatus s = status(b, Arrays.asList(u), ids("A", "B"), shepherd());
            assertEquals("completed", s.status);
            assertTrue(s.skipped);
        });
    }

    @Test void agedPendingLeafReportsError() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task h = option(u, false, a);   // null status, never completed
        long saved = Task.TIMEOUT_INACTIVE_MILLIS;
        Task.TIMEOUT_INACTIVE_MILLIS = -1L;   // everything is older than the maximum age
        try {
            withNoLogs(() -> {
                assertEquals("error", status(a, Arrays.asList(h, u), ids("A"), shepherd()).status);
                assertNull(h.getStoredStatus(), "reported only; the stored status is untouched");
            });
        } finally {
            Task.TIMEOUT_INACTIVE_MILLIS = saved;
        }
    }

    @Test void agedRequeuedRepresentativeIsReportedAsErrorWithoutBeingChanged() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task h = option(u, false, a);
        h.setStatus(Task.STATUS_REQUEUED);
        h.setStatusDetailsAddError("SEND_FAILED", "could not send");
        String detailsBefore = h.getStatusDetails().toString();
        long saved = Task.TIMEOUT_INACTIVE_MILLIS;
        Task.TIMEOUT_INACTIVE_MILLIS = -1L;
        try {
            withNoLogs(() -> {
                assertEquals("error", status(a, Arrays.asList(h, u), ids("A"), shepherd()).status);
                assertEquals(Task.STATUS_REQUEUED, h.getStoredStatus(), "stored status untouched");
                assertEquals(detailsBefore, h.getStatusDetails().toString(), "details untouched");
            });
        } finally {
            Task.TIMEOUT_INACTIVE_MILLIS = saved;
        }
    }

    @Test void oldTreeUsesTodaysRule() throws Exception {
        Annotation a = annot("A");
        Task root = new Task();
        root.addObject(a);
        Task newest = new Task(root);
        newest.addObject(a);
        newest.setStatus("completed");
        root.setStatus("error");
        withNoLogs(() -> {
            assertEquals("completed", status(a, Arrays.asList(newest, root), ids("A"), shepherd()).status);
            assertEquals("completed", ImportTask.identificationStatusForAnnotation(a, Arrays.asList(newest, root),
                "IMP1", null, false, shepherd()).status);
        });
    }

    @Test void notRunAwareUsesTodaysRuleEvenWithRunIds() throws Exception {
        Annotation a = annot("A");
        Task u = umbrella(a);
        Task h = option(u, false, a);
        Task v = option(u, true, a);
        Task vl = leaf(v, "completed", a);
        withNoLogs(() -> {
            assertEquals("completed", ImportTask.identificationStatusForAnnotation(a, Arrays.asList(vl, h, v, u),
                "IMP1", null, false, shepherd()).status);
        });
    }

    @Test void fallsBackToTheNewestUmbrellaWhenNoneIsEligibleForTheEncounterSet() throws Exception {
        Annotation a = annot("A");
        Annotation b = annot("B");
        Task u = umbrella(a, b);          // spans encounters: ineligible for {A}
        Task h = option(u, false, a, b);
        h.setStatus("completed");
        Task h2 = option(u, false, a, b);   // pending sibling: only the run-aware fallback sees it
        withNoLogs(() -> {
            assertEquals("waiting to queue", status(a, Arrays.asList(h, h2, u), ids("A"), shepherd()).status);
        });
    }

    @Test void mergeClassLabelSortsDistinctNonBlankClasses() {
        assertEquals("guitarfish", ImportTask.mergeClassLabel(null, "guitarfish"));
        assertEquals("guitarfish, guitarfish+rostrum", ImportTask.mergeClassLabel("guitarfish", "guitarfish+rostrum"));
        assertEquals("guitarfish, guitarfish+rostrum", ImportTask.mergeClassLabel("guitarfish+rostrum", "guitarfish"));
        assertEquals("guitarfish", ImportTask.mergeClassLabel("guitarfish", "guitarfish"));
        assertEquals("guitarfish", ImportTask.mergeClassLabel("guitarfish", " "));
        assertNull(ImportTask.mergeClassLabel(null, null));
    }

    @Test void encounterIdsToAnnotationIdsGroupsRowsAndReturnsNullOnFailure() {
        PersistenceManager pm = mock(PersistenceManager.class);
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(pm);
        Query q = mock(Query.class);
        when(pm.newQuery(eq("javax.jdo.query.SQL"), anyString())).thenReturn(q);
        List<Object> rows = new ArrayList<Object>();
        rows.add(new Object[] { "enc-1", "A" });
        rows.add(new Object[] { "enc-1", "B" });
        rows.add(new Object[] { "enc-2", "C" });
        when(q.execute()).thenReturn(rows);
        Map<String, Set<String>> m = ImportTask.encounterIdsToAnnotationIds(Arrays.asList("enc-1", "enc-2"), sh);
        assertEquals(ids("A", "B"), m.get("enc-1"));
        assertEquals(ids("C"), m.get("enc-2"));
        verify(q).closeAll();

        when(q.execute()).thenThrow(new RuntimeException("boom"));
        assertNull(ImportTask.encounterIdsToAnnotationIds(Arrays.asList("enc-1"), sh));
        assertTrue(ImportTask.encounterIdsToAnnotationIds(new ArrayList<String>(), sh).isEmpty());
    }
}
