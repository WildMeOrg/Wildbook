package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.IAJsonProperties;
import org.ecocean.Occurrence;
import org.ecocean.TestIAJsonPropertiesUtil;
import org.ecocean.User;
import org.ecocean.api.bulk.BulkImporter;
import org.ecocean.ia.IA;
import org.ecocean.media.AssetStore;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.AdvisoryLock;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

/**
 * Duplicate-import guard (#1789 P1): one bulkImportId is imported once. The request takes an
 * advisory lock on the ID and looks for an existing ImportTask before reading the upload, then
 * reserves (persists and commits) the task once, before anything is created or launched.
 * Harness copied from BulkBoxImportApiTest (mocked Shepherds/PMF, real validation); the
 * importer is a construction mock, the background launcher is captured. Each {@link Call}
 * carries one request's mocks and records, so two requests can run on two threads (Mockito
 * static and construction mocks are thread-local).
 */
class BulkImportDuplicateGuardTest {
    static final String BULK_ID = BulkBoxImportApiTest.BULK_ID;
    static final String[] FIELDS = BulkBoxImportApiTest.BASE;
    @TempDir static Path uploadDir;

    @BeforeEach void setUp() {
        JSONObject json = new JSONObject().put("Genus", new JSONObject().put("species", new JSONObject()
            .put("body", BulkBoxImportApiTest.idConf(true))));
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac);
        iac.setJson(json);
        TestIAJsonPropertiesUtil.override(iac);
    }

    @AfterEach void tearDown() {
        TestIAJsonPropertiesUtil.override(null);
    }

    static JSONObject valid() {
        return BulkBoxImportApiTest.payloadRaw(FIELDS, new Object[] { "Genus", "species", 2020 })
            .put("skipDetection", true);
    }

    static JSONObject background() {
        return valid().put("processInBackground", true);
    }

    static ImportTask existing(String status) {
        ImportTask t = new ImportTask(null, BULK_ID);
        t.setStatus(status);
        return t;
    }

    /** One request: what its mocks answer, and what they saw. */
    static class Call {
        final JSONObject payload;
        String requestContext = null;                  // ServletUtilities.getContext(request), when set
        Answer<Boolean> lock = inv -> true;            // AdvisoryLock.tryXact
        Answer<ImportTask> guardLookup = inv -> null;  // first findImportTask (the guard)
        Answer<ImportTask> reserveLookup = inv -> null; // second findImportTask (the reservation's)
        boolean reserveCommits = true;                 // commitDBTransactionWithStatus
        boolean runWorker = false;                     // launcher runs the background Runnable too

        final List<String> events = Collections.synchronizedList(new ArrayList<String>());
        final List<Runnable> launched = new ArrayList<Runnable>();
        final List<ImportTask> persisted = new ArrayList<ImportTask>();
        final List<String> archived = new ArrayList<String>();   // archive file paths written
        final List<Object> shepherdContexts = new ArrayList<Object>();
        int lockCalls, lookups, fileListings, mediaCreated, importers;
        ImportTask held;
        JSONObject response;

        Call(JSONObject payload) { this.payload = payload; }

        int status() { return response.getInt("statusCode"); }
        boolean archivedAny() {
            for (String p : archived) if (p.contains(BULK_ID)) return true;
            return false;
        }
        long archivedPayloads() {
            return archived.stream().filter(p -> p.contains("_payload_")).count();
        }
        JSONObject firstError() { return response.getJSONArray("errors").getJSONObject(0); }
    }

    Call post(Call c) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        when(request.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(c.payload.toString())));
        BulkImport servlet = new BulkImport();
        servlet.launcher = r -> {
            c.events.add("launch");
            c.launched.add(r);
            if (c.runWorker) r.run();
        };
        User user = mock(User.class);
        when(user.getId()).thenReturn("00000000-0000-4000-8000-0000000000aa");
        when(user.getUsername()).thenReturn("fake-username");
        Occurrence occ = mock(Occurrence.class);
        PersistenceManager pm = mock(PersistenceManager.class);
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        doAnswer(inv -> {
            ImportTask t = inv.getArgument(0);
            c.events.add("persist:" + t.getStatus());
            c.persisted.add(t);
            c.held = t;
            return t;
        }).when(pm).makePersistent(any(ImportTask.class));
        AtomicInteger lookupNumber = new AtomicInteger();

        // registered before the static mocks and closed after them (Mockito resolves mocks in LIFO order)
        try (MockedConstruction<BulkImporter> importer = mockConstruction(BulkImporter.class, (mock, ctx) -> {
                 c.importers++;
                 c.events.add("import");
                 when(mock.createImport()).thenReturn(new JSONObject());
                 when(mock.getEncounters()).thenReturn(new ArrayList<org.ecocean.Encounter>());
                 when(mock.getTouchedIndividualIds()).thenReturn(new HashSet<String>());
             });
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (mock, ctx) -> {
                 int n = ctx.getCount();
                 Object context = ctx.arguments().isEmpty() ? null : ctx.arguments().get(0);
                 c.shepherdContexts.add(context);
                 when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
                 when(mock.getUser(any(String.class))).thenReturn(user);
                 when(mock.getUserByUUID(any(String.class))).thenReturn(user);
                 when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
                 when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
                 when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
                 when(mock.findImportTask(any())).thenAnswer(inv -> {
                     c.lookups++;
                     c.events.add("find:" + n);
                     return (lookupNumber.incrementAndGet() == 1 ? c.guardLookup : c.reserveLookup).answer(inv);
                 });
                 when(mock.getImportTask(any())).thenAnswer(inv -> c.held);
                 when(mock.getPM()).thenReturn(pm);
                 when(mock.commitDBTransactionWithStatus()).thenAnswer(inv -> {
                     c.events.add("commit:" + n);
                     return c.reserveCommits;
                 });
                 when(mock.getContext()).thenReturn(context instanceof String ? (String)context : "context0");
             });
             MockedStatic<AdvisoryLock> lock = mockStatic(AdvisoryLock.class, CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class, CALLS_REAL_METHODS);
             MockedStatic<UploadedFiles> files = mockStatic(UploadedFiles.class, CALLS_REAL_METHODS);
             MockedStatic<ShepherdPMF> pmfs = mockStatic(ShepherdPMF.class);
             MockedStatic<FeatureType> ft = mockStatic(FeatureType.class);
             MockedStatic<IAGateway> gateway = mockStatic(IAGateway.class, CALLS_REAL_METHODS);
             MockedStatic<IA> ia = mockStatic(IA.class);
             MockedStatic<MediaAsset> media = mockStatic(MediaAsset.class, CALLS_REAL_METHODS);
             MockedStatic<AssetStore> stores = mockStatic(AssetStore.class, CALLS_REAL_METHODS);
             MockedStatic<org.ecocean.Util> util = mockStatic(org.ecocean.Util.class, CALLS_REAL_METHODS)) {
            lock.when(() -> AdvisoryLock.tryXact(any(), anyLong())).thenAnswer(inv -> {
                c.lockCalls++;
                c.events.add("lock");
                return c.lock.answer(inv);
            });
            if (c.requestContext != null)
                su.when(() -> ServletUtilities.getContext(any(HttpServletRequest.class))).thenReturn(c.requestContext);
            util.when(() -> org.ecocean.Util.writeToFile(anyString(), anyString())).thenAnswer(inv -> {
                c.archived.add(inv.getArgument(1));
                c.events.add("archive:" + inv.getArgument(1));
                return null;
            });
            files.when(() -> UploadedFiles.getUploadDir(any(HttpServletRequest.class), any(String.class))).thenReturn(uploadDir.toFile());
            files.when(() -> UploadedFiles.findFiles(any(HttpServletRequest.class), any(String.class))).thenAnswer(inv -> {
                c.fileListings++;
                return inv.callRealMethod();
            });
            files.when(() -> UploadedFiles.makeMediaAsset(any(String.class), any(File.class), any(Shepherd.class))).thenAnswer(inv -> {
                c.mediaCreated++;
                return null;
            });
            stores.when(() -> AssetStore.getDefault(any(Shepherd.class))).thenReturn(mock(AssetStore.class));
            pmfs.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(pmf);
            ft.when(() -> FeatureType.load(eq("org.ecocean.boundingBox"), any(Shepherd.class))).thenReturn(new FeatureType("org.ecocean.boundingBox"));
            gateway.when(() -> IAGateway.handleBulkImport(any(JSONObject.class), any(JSONObject.class), any(Shepherd.class), anyString(), any()))
                .thenAnswer(inv -> new JSONObject().put("success", true));
            ia.when(() -> IA.getBaseURL(anyString())).thenReturn("https://base.test");
            media.when(() -> MediaAsset.updateStandardChildrenBackground(anyString(), any(List.class), any(Runnable.class))).thenAnswer(inv -> null);
            servlet.doPost(request, response);
            out.flush();
            c.response = new JSONObject(out.toString());
            return c;
        }
    }

    void assertRefusedEarly(Call c) {
        assertEquals(409, c.status());
        assertEquals("DUPLICATE_IMPORT", c.firstError().getString("type"));
        assertEquals("INVALID", c.firstError().getString("code"));
        assertEquals("bulkImportId " + BULK_ID + " has already been submitted", c.firstError().getString("details"));
        assertFalse(c.response.getBoolean("success"));
        assertFalse(c.response.getBoolean("annotationsImported"));
        assertFalse(c.archivedAny(), "a refused request archives nothing: " + c.archived);
        assertEquals(0, c.fileListings, "the upload is not read");
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());
        assertEquals(0, c.importers);
    }

    @Test void existingTaskAnyStatusRefused() throws Exception {
        for (String status : new String[] { "failed", "complete", "processing-background" }) {
            Call c = new Call(valid());
            c.guardLookup = inv -> existing(status);
            post(c);
            assertRefusedEarly(c);
            assertEquals(1, c.lookups, status);
        }
    }

    @Test void lockHeldElsewhereRefused() throws Exception {
        Call c = new Call(valid());
        c.lock = inv -> false;
        post(c);
        assertRefusedEarly(c);
        assertEquals(0, c.lookups, "no task lookup once the lock is refused");
    }

    @Test void paddedIdRejected() throws Exception {
        for (String id : new String[] { " abc", "abc ", "" }) {
            Call c = new Call(valid().put("bulkImportId", id));
            post(c);
            assertEquals(400, c.status(), "[" + id + "]");
            assertEquals("bulkImportId", c.firstError().getString("fieldName"), "[" + id + "]");
            assertEquals("INVALID", c.firstError().getString("code"));
            assertEquals(0, c.lockCalls);
            assertEquals(0, c.lookups);
            assertEquals(0, c.fileListings);
            assertTrue(c.persisted.isEmpty());
        }
        Call v = new Call(valid().put("bulkImportId", "abc ").put("validateOnly", true));
        post(v);
        assertEquals(400, v.status(), "validateOnly gets the same check");
    }

    @Test void lockFailureIs500() throws Exception {
        Call c = new Call(background());
        c.lock = inv -> { throw new SQLException("lock unavailable"); };
        post(c);
        assertEquals(500, c.status());
        assertEquals(0, c.lookups);
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());
        assertEquals(0, c.importers);
    }

    @Test void lookupFailureIs500() throws Exception {
        Call c = new Call(background());
        c.guardLookup = inv -> { throw new RuntimeException("database unavailable"); };
        post(c);
        assertEquals(500, c.status());
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());
        assertEquals(0, c.importers);
    }

    @Test void validateOnlyNeverRefused() throws Exception {
        Call c = new Call(valid().put("validateOnly", true));
        c.lock = inv -> false;
        c.guardLookup = inv -> existing("complete");
        post(c);
        assertEquals(200, c.status());
        assertTrue(c.response.getBoolean("success"));
        assertEquals(0, c.lockCalls);
        assertEquals(0, c.lookups);
        assertTrue(c.persisted.isEmpty());
        assertEquals(0, c.importers);
    }

    @Test void retryAfterValidationFailureAccepted() throws Exception {
        Call bad = new Call(BulkBoxImportApiTest.payloadRaw(FIELDS, new Object[] { null, "species", 2020 })
            .put("skipDetection", true));
        post(bad);
        assertEquals(400, bad.status());
        assertNotNull(BulkBoxImportApiTest.error(bad.response, 0, "Encounter.genus"));
        assertTrue(bad.persisted.isEmpty(), "a refused validation reserves nothing");

        Call fixed = new Call(valid());   // same id; still no task, as the first request reserved none
        post(fixed);
        assertEquals(200, fixed.status());
        assertTrue(fixed.response.getBoolean("success"));
        assertEquals(1, fixed.importers);
        assertEquals(1, fixed.persisted.size());
    }

    @Test void reservationCommitFailureIs500AndNothingLaunched() throws Exception {
        Call c = new Call(background());
        c.reserveCommits = false;
        post(c);
        assertEquals(500, c.status());
        assertTrue(c.launched.isEmpty());
        assertEquals(0, c.mediaCreated);
        assertEquals(0, c.importers);
    }

    @Test void reservationRaceIs409() throws Exception {
        Call c = new Call(background());
        c.reserveLookup = inv -> existing("processing-background");   // another request reserved it first
        post(c);
        assertEquals(409, c.status());
        assertEquals("DUPLICATE_IMPORT", c.firstError().getString("type"));
        assertEquals(2, c.lookups);
        assertEquals(1, c.fileListings, "a late refusal has read the upload");
        assertFalse(c.archivedAny(), "but writes nothing: " + c.archived);
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());
        assertEquals(0, c.importers);
    }

    @Test void backgroundReservationCommittedBeforeLaunch() throws Exception {
        Call c = new Call(background());
        post(c);
        assertEquals(200, c.status());
        assertTrue(c.response.getBoolean("backgrounded"));
        assertEquals(1, c.launched.size());
        List<String> order = new ArrayList<String>();
        for (String e : c.events) if (!e.startsWith("archive:")) order.add(e);
        // shepherd 1 is the request's; shepherd 2 is the reservation's own
        assertEquals(Arrays.asList("lock", "find:1", "find:2", "persist:processing-background", "commit:2", "launch"), order);
        ImportTask t = c.persisted.get(0);
        assertEquals(BULK_ID, t.getId());
        assertEquals("processing-background", t.getStatus());
        assertNotNull(t.getEncounters());
        assertTrue(t.getEncounters().isEmpty());
        assertEquals(Double.valueOf(0.0D), t.getProcessingProgress());
        assertFalse(t.getPassedParameters().has("rows"));
        assertFalse(t.getPassedParameters().has("fieldNames"));
        assertTrue(t.getPassedParameters().getBoolean("processInBackground"));
        assertFalse(t.getPassedParameters().getBoolean(ImportTask.PARAM_ANNOTATIONS_IMPORTED));
    }

    @Test void foregroundReservesOnceThenImports() throws Exception {
        Call c = new Call(valid());
        post(c);
        assertEquals(200, c.status());
        assertTrue(c.response.getBoolean("success"));
        assertEquals(1, c.persisted.size());
        assertTrue(c.events.contains("persist:processing-foreground"), c.events.toString());
        assertEquals("complete", c.held.getStatus(), "finalized by the unchanged foreground path");
        assertEquals(1, c.importers);
        assertTrue(c.launched.isEmpty());
        assertEquals(1, c.archivedPayloads());
        int payloadAt = -1;
        for (int i = 0; i < c.events.size(); i++) if (c.events.get(i).contains("_payload_")) payloadAt = i;
        assertTrue(payloadAt > c.events.indexOf("import"), "payload archived at the tail: " + c.events);
    }

    @Test void reservationUsesRequestContext() throws Exception {
        Call c = new Call(background());
        c.requestContext = "context1";
        c.runWorker = true;
        post(c);
        assertEquals(200, c.status());
        assertEquals(1, c.importers, "the worker ran");
        assertTrue(c.shepherdContexts.size() >= 3, "request, reservation and worker: " + c.shepherdContexts);
        for (Object context : c.shepherdContexts) assertEquals("context1", context, c.shepherdContexts.toString());
    }

    @Test void overlappingRequestsOnlyOneProceeds() throws Exception {
        CountDownLatch inLookup = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Call a = new Call(background());
        a.guardLookup = inv -> {
            inLookup.countDown();
            assertTrue(release.await(30, TimeUnit.SECONDS));
            return null;
        };
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread ta = new Thread(() -> {
            try { post(a); } catch (Throwable t) { failure.set(t); }
        });
        ta.start();
        try {
            assertTrue(inLookup.await(30, TimeUnit.SECONDS), "A reached its guard lookup");
            // A holds the lock in its open transaction, so the real lock refuses B (AdvisoryLockDbTest)
            Call b = new Call(background());
            b.lock = inv -> false;
            post(b);
            assertRefusedEarly(b);
        } finally {
            release.countDown();
            ta.join(60000);
        }
        if (failure.get() != null) throw new AssertionError(failure.get());
        assertEquals(200, a.status());
        assertEquals(1, a.launched.size());
        assertEquals(1, a.persisted.size());
    }
}
