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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.IAJsonProperties;
import org.ecocean.ImageFixtures;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
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

    @BeforeAll static void fixtures() throws Exception {
        ImageFixtures.write(ImageFixtures.noise(200, 150, 42), "jpeg", uploadDir.resolve("a.jpg").toFile(), false);
    }

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

    /** One row referencing the uploaded a.jpg, so a media-creation count means something. */
    static JSONObject withFile() {
        return BulkBoxImportApiTest.payload(new String[] { "Encounter.mediaAsset0" }, new Object[] { "a.jpg" })
            .put("skipDetection", true).put("processInBackground", true);
    }

    /** One row with a box on a.jpg: validation sets annotationsImported true. */
    static JSONObject boxed() {
        return BulkBoxImportApiTest.payload(BulkBoxImportApiTest.BOXED, new Object[] { "a.jpg", "10,10,50,50", "body" })
            .put("skipDetection", true).put("processInBackground", true);
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
        String userId = "00000000-0000-4000-8000-0000000000aa"; // the request user's id
        boolean rollbackFails = false;                 // Shepherd.rollbackAndClose throws
        boolean launchFails = false;                   // the launcher throws before the worker starts
        boolean creatorMissing = false;                // the reservation cannot load the request user
        Set<Integer> inactive = new HashSet<Integer>();   // Shepherd numbers whose transaction is not active
        Set<Integer> beginFails = new HashSet<Integer>(); // Shepherd numbers whose beginDBTransaction throws
        Map<String, ImportTask> table = null;          // when set: lookups read it, persist adds, delete removes
        Set<Integer> constructFails = new HashSet<Integer>(); // Shepherd numbers whose construction throws
        Runnable afterDoPost = null;                   // runs after doPost, still inside this call's mocks
        Throwable mediaFailure = null;                 // UploadedFiles.makeMediaAsset throws this
        Set<Integer> commitFails = new HashSet<Integer>(); // Shepherd numbers whose commitDBTransactionWithStatus is false

        final List<String> events = Collections.synchronizedList(new ArrayList<String>());
        final List<Runnable> launched = new ArrayList<Runnable>();
        final List<ImportTask> persisted = new ArrayList<ImportTask>();
        final List<String> archived = new ArrayList<String>();   // archive file paths written
        final List<Object> shepherdContexts = new ArrayList<Object>();
        final List<Shepherd> shepherds = Collections.synchronizedList(new ArrayList<Shepherd>());   // in construction order
        final List<Long> lockKeys = new ArrayList<Long>();
        final List<Object> lockShepherds = new ArrayList<Object>();
        final List<ImportTask> deleted = new ArrayList<ImportTask>();
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
        return post(c, null);
    }

    /** {@code launcher}, when given, replaces the recording launcher. */
    Call post(Call c, Consumer<Runnable> launcher) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        when(request.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(request.getReader()).thenReturn(new BufferedReader(new StringReader(c.payload.toString())));
        BulkImport servlet = new BulkImport();
        servlet.launcher = r -> {
            if (c.launchFails) throw new IllegalStateException("cannot start a thread");
            c.events.add("launch");
            c.launched.add(r);
            if (c.runWorker) r.run();
        };
        if (launcher != null) servlet.launcher = launcher;
        User user = mock(User.class);
        when(user.getId()).thenReturn(c.userId);
        when(user.getUsername()).thenReturn("fake-username");
        Occurrence occ = mock(Occurrence.class);
        PersistenceManager pm = mock(PersistenceManager.class);
        PersistenceManagerFactory pmf = mock(PersistenceManagerFactory.class);
        doAnswer(inv -> {
            ImportTask t = inv.getArgument(0);
            c.events.add("persist:" + t.getStatus());
            c.persisted.add(t);
            c.held = t;
            if (c.table != null) c.table.put(t.getId(), t);
            return t;
        }).when(pm).makePersistent(any(ImportTask.class));
        doAnswer(inv -> {
            ImportTask t = inv.getArgument(0);
            c.events.add("delete");
            c.deleted.add(t);
            if (c.table != null) c.table.remove(t.getId());
            return null;
        }).when(pm).deletePersistent(any(ImportTask.class));
        doAnswer(inv -> {
            if (c.creatorMissing) throw new JDOObjectNotFoundException("no such user");
            return user;
        }).when(pm).getObjectById(eq(User.class), any());
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
                 if (c.constructFails.contains(n)) throw new IllegalStateException("cannot build Shepherd " + n);
                 Object context = ctx.arguments().isEmpty() ? null : ctx.arguments().get(0);
                 c.shepherdContexts.add(context);
                 c.shepherds.add(mock);
                 when(mock.isDBTransactionActive()).thenAnswer(inv -> !c.inactive.contains(n));
                 if (c.beginFails.contains(n)) doThrow(new RuntimeException("cannot begin")).when(mock).beginDBTransaction();
                 when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
                 when(mock.getUser(any(String.class))).thenReturn(user);
                 when(mock.getUserByUUID(any(String.class))).thenAnswer(inv -> c.creatorMissing ? null : user);
                 when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
                 when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
                 when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
                 when(mock.findImportTask(any())).thenAnswer(inv -> {
                     c.lookups++;
                     c.events.add("find:" + n);
                     if (c.table != null) return c.table.get((String)inv.getArgument(0));
                     return (lookupNumber.incrementAndGet() == 1 ? c.guardLookup : c.reserveLookup).answer(inv);
                 });
                 when(mock.getImportTask(any())).thenAnswer(inv -> c.held);
                 when(mock.getPM()).thenReturn(pm);
                 when(mock.commitDBTransactionWithStatus()).thenAnswer(inv -> {
                     c.events.add("commit:" + n);
                     return c.reserveCommits && !c.commitFails.contains(n);
                 });
                 when(mock.getContext()).thenReturn(context instanceof String ? (String)context : "context0");
                 if (c.rollbackFails) doThrow(new RuntimeException("rollback failed")).when(mock).rollbackAndClose();
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
                c.lockShepherds.add(inv.getArgument(0));
                c.lockKeys.add(inv.getArgument(1));
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
                if (c.mediaFailure != null) throw c.mediaFailure;
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
            if (c.afterDoPost != null) c.afterDoPost.run();
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
        Call c = new Call(withFile());   // a.jpg would become a MediaAsset if the worker ran
        c.reserveCommits = false;
        post(c);
        assertEquals(500, c.status());
        assertEquals(1, c.response.getInt("numberFilesValid"), "the payload's file passed validation");
        assertTrue(c.launched.isEmpty());
        assertEquals(0, c.mediaCreated);
        assertEquals(0, c.importers);
    }

    @Test void launchFailureFreesTheId() throws Exception {
        Map<String, ImportTask> table = new HashMap<String, ImportTask>();
        Call first = new Call(background());
        first.table = table;
        first.launchFails = true;
        post(first);
        assertEquals(500, first.status());
        assertEquals(1, first.persisted.size(), "the task was reserved");
        assertEquals(first.persisted, first.deleted, "and removed again when the worker could not start");
        assertTrue(table.isEmpty());
        // removed on its own Shepherd while the request transaction (and its lock) was still open
        Shepherd request = first.shepherds.get(0);
        Shepherd undo = first.shepherds.get(2);
        InOrder order = inOrder(undo, request);
        order.verify(undo).commitDBTransactionWithStatus();
        order.verify(request).rollbackDBTransaction();
        verify(undo).closeDBTransaction();

        Call retry = new Call(background());
        retry.table = table;
        post(retry);
        assertEquals(200, retry.status(), retry.response.toString());
        assertEquals(1, retry.launched.size());
        assertEquals(1, table.size());
    }

    @Test void launchFailureAfterTheWorkerStartedKeepsTheTask() throws Exception {
        Map<String, ImportTask> table = new HashMap<String, ImportTask>();
        Call c = new Call(background());
        c.table = table;
        // a launcher that runs the worker, then fails: the task is the worker's now
        post(c, r -> { r.run(); throw new IllegalStateException("after the worker ran"); });
        assertEquals(500, c.status());
        assertEquals(1, c.importers, "the worker claimed the task first and imported");
        assertTrue(c.deleted.isEmpty(), "a started worker's task is not removed");
        assertEquals(1, table.size());
    }

    @Test void queuedWorkerAfterUndoDoesNothing() throws Exception {
        Map<String, ImportTask> table = new HashMap<String, ImportTask>();
        Call c = new Call(withFile());
        c.table = table;
        List<Runnable> queue = new ArrayList<Runnable>();
        // the launcher queues the worker, then fails; the queued worker runs after the undo
        c.afterDoPost = () -> queue.get(0).run();
        post(c, r -> { queue.add(r); throw new IllegalStateException("queue rejected the start"); });
        assertEquals(500, c.status());
        assertEquals(1, queue.size());
        assertEquals(1, c.deleted.size(), "the undo claimed the task and removed it");
        assertTrue(table.isEmpty());
        assertEquals(0, c.importers, "the worker lost the claim and imported nothing");
        assertEquals(0, c.mediaCreated);
        assertEquals(3, c.shepherds.size(), "request, reservation and undo; the worker built none: " + c.shepherdContexts);
    }

    @Test void undoSetupFailureKeepsTheLaunchFailure() throws Exception {
        Call c = new Call(background());
        c.launchFails = true;
        c.constructFails.add(3);   // the undo's Shepherd cannot be built
        post(c);
        assertEquals(500, c.status());
        String error = c.response.getString("error");
        assertTrue(error.contains("cannot start a thread"), "the launch failure is reported: " + error);
        assertTrue(c.launched.isEmpty());
        assertEquals(0, c.importers);
    }

    @Test void inactiveRequestTransactionIs500() throws Exception {
        Call c = new Call(background());
        c.inactive.add(1);
        post(c);
        assertEquals(500, c.status());
        assertEquals(0, c.lockCalls, "no lock without a transaction to hold it");
        assertEquals(0, c.lookups);
        assertEquals(0, c.fileListings);
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());
        assertEquals(0, c.importers);
    }

    @Test void inactiveReservationTransactionIs500() throws Exception {
        Call c = new Call(background());
        c.inactive.add(2);
        post(c);
        assertEquals(500, c.status());
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());
        verify(c.shepherds.get(1)).rollbackAndClose();
    }

    @Test void reservationBeginFailureClosesItsShepherd() throws Exception {
        Call c = new Call(background());
        c.beginFails.add(2);
        post(c);
        assertEquals(500, c.status());
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());
        verify(c.shepherds.get(1)).rollbackAndClose();
    }

    @Test void workerBeginFailureClosesItsShepherd() throws Exception {
        Call c = new Call(background());
        c.runWorker = true;
        c.beginFails.add(3);
        post(c);
        assertEquals(200, c.status(), "the request was accepted before the worker ran");
        // request, reservation, worker, and the one that marks the task failed
        assertEquals(4, c.shepherds.size(), c.shepherdContexts.toString());
        verify(c.shepherds.get(2)).closeDBTransaction();
        assertEquals(0, c.importers);
        assertEquals("failed", c.held.getStatus(), "a worker that could not begin leaves no processing task");
    }

    @Test void creatorNotLoadableIs500() throws Exception {
        Call c = new Call(background());
        c.creatorMissing = true;   // the user has an id, but the reservation cannot load it
        post(c);
        assertEquals(500, c.status());
        assertTrue(c.persisted.isEmpty(), "no creatorless task");
        assertTrue(c.launched.isEmpty());
        assertEquals(0, c.importers);
        verify(c.shepherds.get(1)).rollbackAndClose();
    }

    @Test void sameIdSameLockKeyOnTheRequestTransaction() throws Exception {
        Call a = new Call(background());
        post(a);
        Call b = new Call(background());
        b.guardLookup = inv -> existing("processing-background");
        post(b);
        String other = "00000000-0000-0000-0000-00000000c0c0";
        Call c = new Call(background().put("bulkImportId", other));
        post(c);
        long expected = AdvisoryLock.key("bulk-import:" + BULK_ID);
        assertEquals(Arrays.asList(expected), a.lockKeys);
        assertEquals(a.lockKeys, b.lockKeys, "two requests for one upload contend for one lock");
        assertEquals(Arrays.asList(AdvisoryLock.key("bulk-import:" + other)), c.lockKeys);
        assertFalse(c.lockKeys.equals(a.lockKeys));
        assertEquals(1, a.lockShepherds.size());
        assertTrue(a.lockShepherds.get(0) == a.shepherds.get(0), "the lock is taken on the request's transaction");
        assertTrue(b.lockShepherds.get(0) == b.shepherds.get(0));
    }

    void assertReservedBeforeRequestTransactionEnds(Call c) {
        Shepherd request = c.shepherds.get(0);
        Shepherd reservation = c.shepherds.get(1);
        InOrder order = inOrder(reservation, request);
        order.verify(reservation).commitDBTransactionWithStatus();
        order.verify(request).commitDBTransactionWithStatus();
        order.verify(request).closeDBTransaction();
        // that commit and close are the request transaction's only end, so none came before the reservation's
        verify(request, times(1)).commitDBTransactionWithStatus();
        verify(request, times(1)).closeDBTransaction();
        verify(request, never()).updateDBTransaction();
        verify(request, never()).commitDBTransaction();
        verify(request, never()).rollbackDBTransaction();
        verify(request, never()).rollbackAndClose();
    }

    @Test void reservationCommitsBeforeTheRequestTransactionEnds() throws Exception {
        Call fg = new Call(valid());
        post(fg);
        assertEquals(200, fg.status());
        assertReservedBeforeRequestTransactionEnds(fg);
        Call bg = new Call(background());
        post(bg);
        assertEquals(200, bg.status());
        assertReservedBeforeRequestTransactionEnds(bg);
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

    @Test void boxedReservationRaceReportsNoAnnotations() throws Exception {
        Call c = new Call(boxed());
        c.reserveLookup = inv -> existing("processing-background");
        post(c);
        assertEquals(409, c.status(), c.response.toString());
        assertEquals("DUPLICATE_IMPORT", c.firstError().getString("type"));
        assertFalse(c.response.getBoolean("annotationsImported"), "this request imported nothing");
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());

        Call ok = new Call(boxed());   // the same payload, not refused: validation does set it
        post(ok);
        assertEquals(200, ok.status(), ok.response.toString());
        assertTrue(ok.response.getBoolean("annotationsImported"));
    }

    @Test void reservationWithoutUserIdIs500() throws Exception {
        Call c = new Call(background());
        c.userId = null;
        post(c);
        assertEquals(500, c.status());
        assertTrue(c.persisted.isEmpty(), "no creatorless task");
        assertEquals(1, c.lookups, "refused before the reservation Shepherd opens");
        assertTrue(c.launched.isEmpty());
        assertEquals(0, c.importers);
    }

    @Test void rollbackFailureKeepsDuplicateRefusal() throws Exception {
        Call c = new Call(background());
        c.reserveLookup = inv -> existing("processing-background");
        c.rollbackFails = true;
        post(c);
        assertEquals(409, c.status());
        assertEquals("DUPLICATE_IMPORT", c.firstError().getString("type"));
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());
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
        assertEquals(Arrays.asList("lock", "find:1", "find:2", "persist:processing-background", "commit:2", "launch", "commit:1"), order);
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
        Call c = new Call(withFile());
        c.requestContext = "context1";
        c.runWorker = true;
        post(c);
        assertEquals(200, c.status());
        assertEquals(1, c.importers, "the worker ran");
        assertEquals(1, c.mediaCreated, "and created the file's MediaAsset");
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

    /** withFile(), processed in the request (foreground). */
    static JSONObject foregroundWithFile() {
        return withFile().put("processInBackground", false);
    }

    @Test void workerRuntimeFailureMarksTheTaskFailed() throws Exception {
        Call c = new Call(withFile());
        c.runWorker = true;
        c.mediaFailure = new IllegalStateException("media store unavailable");
        post(c);
        assertEquals(200, c.status(), "the request was accepted before the worker ran");
        assertEquals(1, c.mediaCreated);
        assertEquals(0, c.importers);
        assertEquals("failed", c.held.getStatus());
    }

    @Test void workerErrorMarksTheTaskFailed() throws Exception {
        Call c = new Call(withFile());
        c.mediaFailure = new OutOfMemoryError("worker ran out of memory");
        AtomicReference<Throwable> thrown = new AtomicReference<Throwable>();
        post(c, r -> {
            try { r.run(); } catch (Error e) { thrown.set(e); }
        });
        assertEquals(200, c.status());
        assertTrue(thrown.get() instanceof OutOfMemoryError, "the Error left the worker: " + thrown.get());
        assertEquals("failed", c.held.getStatus());
    }

    @Test void workerBlockedByMediaErrorsMarksTheTaskFailedOnce() throws Exception {
        Call c = new Call(withFile());
        c.runWorker = true;
        c.mediaFailure = new ApiException("bad image", new JSONObject().put("code", "INVALID"));
        post(c);
        assertEquals(200, c.status());
        assertEquals("failed", c.held.getStatus());
        assertEquals(1, countLog(c.held, "status: failed"), c.held.getLog().toString());
    }

    @Test void foregroundRuntimeFailureIs500AndTaskFailed() throws Exception {
        Call c = new Call(foregroundWithFile());
        c.mediaFailure = new IllegalStateException("media store unavailable");
        post(c);
        assertEquals(500, c.status());
        assertFalse(c.response.getBoolean("success"));
        assertEquals(1, c.persisted.size(), "the task was reserved");
        assertEquals("failed", c.held.getStatus());
        verify(c.shepherds.get(0)).rollbackDBTransaction();
    }

    @Test void foregroundBlockedByMediaErrorsIs400AndTaskFailedOnce() throws Exception {
        Call c = new Call(foregroundWithFile());
        c.mediaFailure = new ApiException("bad image", new JSONObject().put("code", "INVALID"));
        post(c);
        assertEquals(400, c.status());
        assertEquals("failed", c.held.getStatus());
        assertEquals(1, countLog(c.held, "status: failed"), c.held.getLog().toString());
        assertNotNull(c.held.getErrors(), "the media errors are on the task");
    }

    @Test void foregroundCommitFailureIs500AndTaskFailed() throws Exception {
        Call c = new Call(valid());
        c.commitFails.add(1);   // the request's own transaction
        post(c);
        assertEquals(500, c.status());
        assertFalse(c.response.getBoolean("success"));
        assertEquals("import could not be saved", c.response.getString("error"));
        assertEquals(1, c.importers);
        assertEquals("failed", c.held.getStatus());
    }

    @Test void foregroundDuplicateLeavesTheExistingTaskAlone() throws Exception {
        Call c = new Call(valid());
        ImportTask other = existing("processing-foreground");
        c.reserveLookup = inv -> other;
        c.held = other;   // what any later getImportTask would load
        post(c);
        assertEquals(409, c.status());
        assertEquals("processing-foreground", other.getStatus());
        assertEquals(0, countLog(other, "status: failed"));
        assertEquals(0, c.importers);
    }

    static int countLog(ImportTask t, String text) {
        int n = 0;
        if (t.getLog() == null) return 0;
        for (Object entry : t.getLog()) if (String.valueOf(entry).contains(text)) n++;
        return n;
    }
}
