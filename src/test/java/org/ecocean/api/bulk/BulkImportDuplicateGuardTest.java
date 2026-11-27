package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    // Shepherd actions, as BulkImport sets them
    static final String REQUEST = "api.Bulk.doPost";
    static final String RESERVE = "BulkImport.reserveImportTask";
    static final String WORKER = "api.Bulk.processBackground";
    static final String UNDO = "BulkImport.undoReservation";
    static final String WRITER = "BulkImport.markReservedFailed";
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
        Set<String> inactive = new HashSet<String>();   // Shepherd actions whose transaction is not active
        Set<String> beginFails = new HashSet<String>(); // Shepherd actions whose beginDBTransaction throws
        Map<String, ImportTask> table = null;          // when set: lookups read it, persist adds, delete removes
        Set<Integer> constructFails = new HashSet<Integer>(); // Shepherd numbers whose construction throws
        Runnable afterDoPost = null;                   // runs after doPost, still inside this call's mocks
        Throwable mediaFailure = null;                 // UploadedFiles.makeMediaAsset throws this
        Throwable importFailure = null;                // BulkImporter.createImport throws this
        Set<String> commitFails = new HashSet<String>(); // Shepherd actions whose commitDBTransactionWithStatus is false
        boolean iaEligible = false;                    // the importer returns one encounter with a MediaAsset
        Answer<ImportTask> finalLookup = inv -> this.held;  // getImportTask (the import's final task update)
        Answer<ImportTask> failLookup = inv -> (this.table != null) ? this.table.get((String)inv.getArgument(0)) : this.held; // the failure writer's lookup
        final Map<Shepherd, String> actions = new java.util.concurrent.ConcurrentHashMap<Shepherd, String>(); // setAction per Shepherd
        HttpServletResponse httpResponse;

        final List<String> events = Collections.synchronizedList(new ArrayList<String>());
        final List<Runnable> launched = new ArrayList<Runnable>();
        final List<ImportTask> persisted = new ArrayList<ImportTask>();
        final List<String> archived = new ArrayList<String>();   // archive file paths written
        final List<Object> shepherdContexts = new ArrayList<Object>();
        final List<Long> lockKeys = new ArrayList<Long>();
        final List<Object> lockShepherds = new ArrayList<Object>();
        final List<ImportTask> deleted = new ArrayList<ImportTask>();
        int lockCalls, lookups, fileListings, mediaCreated, importers, deepReindexes, iaHandoffs;
        ImportTask held;
        JSONObject response;

        Call(JSONObject payload) { this.payload = payload; }

        /** The one Shepherd that set this action. */
        Shepherd byAction(String action) {
            Shepherd found = null;
            for (Map.Entry<Shepherd, String> e : actions.entrySet()) {
                if (!action.equals(e.getValue())) continue;
                assertTrue(found == null, "one Shepherd for " + action + ": " + actions.values());
                found = e.getKey();
            }
            assertNotNull(found, "a Shepherd for " + action + ": " + actions.values());
            return found;
        }

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
        c.httpResponse = response;
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
                 when(mock.createImport()).thenAnswer(inv -> {
                     if (c.importFailure != null) throw c.importFailure;
                     return new JSONObject();
                 });
                 List<org.ecocean.Encounter> encs = new ArrayList<org.ecocean.Encounter>();
                 if (c.iaEligible) {
                     MediaAsset ma = mock(MediaAsset.class);
                     when(ma.getIdInt()).thenReturn(7);
                     org.ecocean.Encounter enc = mock(org.ecocean.Encounter.class);
                     when(enc.getId()).thenReturn("enc-1");
                     when(enc.getMedia()).thenReturn(new ArrayList<MediaAsset>(Arrays.asList(ma)));
                     encs.add(enc);
                 }
                 when(mock.getEncounters()).thenReturn(encs);
                 when(mock.getTouchedIndividualIds()).thenReturn(new HashSet<String>());
             });
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (mock, ctx) -> {
                 int n = ctx.getCount();
                 if (c.constructFails.contains(n)) throw new IllegalStateException("cannot build Shepherd " + n);
                 Object context = ctx.arguments().isEmpty() ? null : ctx.arguments().get(0);
                 c.shepherdContexts.add(context);
                 doAnswer(inv -> {
                     c.actions.put(mock, inv.getArgument(0));
                     return null;
                 }).when(mock).setAction(any());
                 when(mock.isDBTransactionActive()).thenAnswer(inv -> !c.inactive.contains(c.actions.get(mock)));
                 doAnswer(inv -> {
                     if (c.beginFails.contains(c.actions.get(mock))) throw new RuntimeException("cannot begin");
                     return null;
                 }).when(mock).beginDBTransaction();
                 when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
                 when(mock.getUser(any(String.class))).thenReturn(user);
                 when(mock.getUserByUUID(any(String.class))).thenAnswer(inv -> c.creatorMissing ? null : user);
                 when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
                 when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
                 when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
                 when(mock.findImportTask(any())).thenAnswer(inv -> {
                     if (WRITER.equals(c.actions.get(mock))) return c.failLookup.answer(inv);
                     c.lookups++;
                     c.events.add("find:" + n);
                     if (c.table != null) return c.table.get((String)inv.getArgument(0));
                     return (lookupNumber.incrementAndGet() == 1 ? c.guardLookup : c.reserveLookup).answer(inv);
                 });
                 when(mock.getImportTask(any())).thenAnswer(inv -> c.finalLookup.answer(inv));
                 when(mock.getPM()).thenReturn(pm);
                 when(mock.commitDBTransactionWithStatus()).thenAnswer(inv -> {
                     c.events.add("commit:" + n);
                     return c.reserveCommits && !c.commitFails.contains(c.actions.get(mock));
                 });
                 when(mock.getContext()).thenReturn(context instanceof String ? (String)context : "context0");
                 // the reservation's rollback (other Shepherds close as usual)
                 doAnswer(inv -> {
                     if (c.rollbackFails && RESERVE.equals(c.actions.get(mock)))
                         throw new RuntimeException("rollback failed");
                     return null;
                 }).when(mock).rollbackAndClose();
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
             MockedStatic<org.ecocean.Util> util = mockStatic(org.ecocean.Util.class, CALLS_REAL_METHODS);
             MockedStatic<org.ecocean.IndexingManager> indexing = mockStatic(org.ecocean.IndexingManager.class, CALLS_REAL_METHODS)) {
            indexing.when(() -> org.ecocean.IndexingManager.queueIndividualsByIdForDeepReindex(any(), any())).thenAnswer(inv -> {
                c.deepReindexes++;
                c.events.add("deepReindex");
                return null;
            });
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
                .thenAnswer(inv -> {
                    c.iaHandoffs++;
                    return new JSONObject().put("success", true);
                });
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
        Shepherd request = first.byAction(REQUEST);
        Shepherd undo = first.byAction(UNDO);
        InOrder order = inOrder(undo, request);
        order.verify(undo).commitDBTransactionWithStatus();
        order.verify(request).rollbackAndClose();
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
        assertFalse(c.actions.containsValue(WORKER), "the worker built no Shepherd: " + c.actions.values());
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
        c.inactive.add(REQUEST);
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
        c.inactive.add(RESERVE);
        post(c);
        assertEquals(500, c.status());
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());
        verify(c.byAction(RESERVE)).rollbackAndClose();
    }

    @Test void reservationBeginFailureClosesItsShepherd() throws Exception {
        Call c = new Call(background());
        c.beginFails.add(RESERVE);
        post(c);
        assertEquals(500, c.status());
        assertTrue(c.persisted.isEmpty());
        assertTrue(c.launched.isEmpty());
        verify(c.byAction(RESERVE)).rollbackAndClose();
    }

    @Test void workerBeginFailureClosesItsShepherd() throws Exception {
        Call c = new Call(background());
        c.runWorker = true;
        c.beginFails.add(WORKER);
        post(c);
        assertEquals(200, c.status(), "the request was accepted before the worker ran");
        verify(c.byAction(WORKER)).rollbackAndClose();
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
        verify(c.byAction(RESERVE)).rollbackAndClose();
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
        assertTrue(a.lockShepherds.get(0) == a.byAction(REQUEST), "the lock is taken on the request's transaction");
        assertTrue(b.lockShepherds.get(0) == b.byAction(REQUEST));
    }

    void assertReservedBeforeRequestTransactionEnds(Call c) {
        Shepherd request = c.byAction(REQUEST);
        Shepherd reservation = c.byAction(RESERVE);
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
        c.requestContext = "context1";
        c.runWorker = true;
        c.mediaFailure = new IllegalStateException("media store unavailable");
        post(c);
        assertEquals(200, c.status(), "the request was accepted before the worker ran");
        assertEquals(1, c.mediaCreated);
        assertEquals(0, c.importers);
        assertEquals("failed", c.held.getStatus());
        c.byAction(WRITER);
        for (Object context : c.shepherdContexts) assertEquals("context1", context, "the failure is written on the request's context");
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
        assertNotNull(c.held.getErrors(), "the media errors are on the task, with the failure");
        assertEquals(1, countLog(c.held, "errors: "), c.held.getLog().toString());
    }

    @Test void workerToleratedMediaErrorsWrittenOnRequestContext() throws Exception {
        Call c = new Call(withFile().put("tolerance", new JSONObject().put("failImportOnError", false)));
        c.requestContext = "context1";
        c.runWorker = true;
        c.mediaFailure = new ApiException("bad image", new JSONObject().put("code", "INVALID"));
        post(c);
        assertEquals(200, c.status());
        assertEquals(1, c.importers, "a tolerated media error does not block the import");
        assertEquals("complete", c.held.getStatus());
        assertNotNull(c.held.getErrors(), "the media errors are on the task");
        for (Object context : c.shepherdContexts) assertEquals("context1", context, c.shepherdContexts.toString());
    }

    @Test void foregroundRuntimeFailureIs500AndTaskFailed() throws Exception {
        Call c = new Call(foregroundWithFile());
        c.mediaFailure = new IllegalStateException("media store unavailable");
        post(c);
        assertEquals(500, c.status());
        assertFalse(c.response.getBoolean("success"));
        assertEquals(1, c.persisted.size(), "the task was reserved");
        assertEquals("failed", c.held.getStatus());
        verify(c.byAction(REQUEST)).rollbackAndClose();
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

    @Test void foregroundIaEligibleSuccessHandsOffOnce() throws Exception {
        Call c = new Call(valid().put("skipDetection", false));
        c.iaEligible = true;
        post(c);
        assertEquals(200, c.status(), c.response.toString());
        assertEquals(1, c.iaHandoffs, "the fixture reaches IA when the import is saved");
    }

    @Test void foregroundCommitFailureIs500AndTaskFailed() throws Exception {
        Call c = new Call(valid().put("skipDetection", false));
        c.iaEligible = true;
        c.commitFails.add(REQUEST);   // the request's own transaction
        ImportTask row = existing("processing-foreground");   // the row as the rollback left it
        c.failLookup = inv -> row;
        post(c);
        assertEquals(500, c.status());
        verify(c.httpResponse).setStatus(500);
        assertFalse(c.response.getBoolean("success"));
        assertEquals("import could not be saved", c.response.getString("error"));
        assertEquals(1, c.importers);
        assertEquals(0, c.deepReindexes);
        assertEquals(0, c.iaHandoffs);
        assertEquals("failed", row.getStatus());
        Shepherd request = c.byAction(REQUEST);
        InOrder order = inOrder(request);
        order.verify(request).commitDBTransactionWithStatus();
        order.verify(request).rollbackAndClose();
        verify(request, never()).closeDBTransaction();
    }

    @Test void failureWriterLeavesOtherStatusesAlone() throws Exception {
        // finished, handed on to detection, or a background status on a foreground reservation
        for (String status : new String[] { "complete", "processing-detection", "processing-background" }) {
            Call c = new Call(valid());
            c.commitFails.add(REQUEST);
            ImportTask other = existing(status);
            c.failLookup = inv -> other;
            post(c);
            assertEquals(500, c.status(), status);
            assertEquals(status, other.getStatus());
            assertEquals(0, countLog(other, "status: failed"), status);
            Shepherd writer = c.byAction(WRITER);
            verify(writer, never()).commitDBTransactionWithStatus();
            verify(writer).rollbackAndClose();
        }
    }

    @Test void failureWriterCommitFailureIsLoggedOnly() throws Exception {
        Call baseline = new Call(valid());
        baseline.commitFails.add(REQUEST);
        baseline.failLookup = inv -> existing("processing-foreground");
        post(baseline);

        Call c = new Call(valid());
        c.commitFails.add(REQUEST);
        c.commitFails.add(WRITER);   // the failure write itself cannot be saved
        c.failLookup = inv -> existing("processing-foreground");
        post(c);   // nothing thrown
        Shepherd writer = c.byAction(WRITER);
        verify(writer).commitDBTransactionWithStatus();
        verify(writer).rollbackAndClose();
        verify(writer, never()).closeDBTransaction();
        verify(c.httpResponse).setStatus(500);
        JSONObject expected = new JSONObject(baseline.response.toString());
        JSONObject actual = new JSONObject(c.response.toString());
        expected.remove("processingTime");
        actual.remove("processingTime");
        assertTrue(expected.similar(actual), "the response does not change: " + expected + " vs " + actual);
    }

    @Test void workerMissingTaskAfterImportIsFailed() throws Exception {
        Call c = new Call(background());
        c.runWorker = true;
        c.finalLookup = inv -> null;   // the import ran, the task's final update cannot load it
        post(c);
        assertEquals(200, c.status());
        assertEquals(1, c.importers);
        Shepherd worker = c.byAction(WORKER);
        verify(worker, never()).commitDBTransactionWithStatus();
        verify(worker).rollbackAndClose();
        assertEquals("failed", c.held.getStatus());
        assertEquals(0, c.deepReindexes);
    }

    @Test void foregroundMissingTaskAfterImportIs500AndFailed() throws Exception {
        Call c = new Call(valid());
        c.finalLookup = inv -> null;
        post(c);
        assertEquals(500, c.status());
        verify(c.httpResponse).setStatus(500);
        assertFalse(c.response.getBoolean("success"));
        assertEquals(1, c.importers);
        Shepherd request = c.byAction(REQUEST);
        verify(request, never()).commitDBTransactionWithStatus();
        verify(request).rollbackAndClose();
        assertEquals("failed", c.held.getStatus());
        assertEquals(0, c.deepReindexes);
    }

    @Test void failedUndoAfterLaunchFailureMarksTheTaskFailed() throws Exception {
        Call c = new Call(background());
        c.launchFails = true;
        c.commitFails.add(UNDO);   // the undo cannot commit its delete
        post(c);
        assertEquals(500, c.status());
        String error = c.response.getString("error");
        assertTrue(error.contains("cannot start a thread"), "the launch failure is reported: " + error);
        assertEquals(0, c.importers);
        assertEquals("failed", c.held.getStatus(), "the task that could not be removed is not left processing");
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

    @Test void workerCommitFailureMarksTheTaskFailed() throws Exception {
        Call c = new Call(background().put("skipDetection", false));
        c.iaEligible = true;
        c.runWorker = true;
        c.commitFails.add(WORKER);   // the worker's own transaction; the request and reservation commit
        ImportTask row = existing("processing-background");   // the row as the rollback left it
        c.failLookup = inv -> row;
        post(c);
        assertEquals(200, c.status(), "the request was accepted before the worker ran");
        assertEquals(1, c.importers, "the worker imported");
        verify(c.byAction(WORKER)).rollbackAndClose();
        assertEquals("failed", row.getStatus(), "the unsaved import is not left processing");
        assertEquals(0, c.deepReindexes, "nothing saved, nothing reindexed");
        assertEquals(0, c.iaHandoffs, "nothing saved, nothing sent to IA");
        assertTrue(c.archived.stream().anyMatch(a -> a.contains("backgroundComplete_failed")), c.archived.toString());
        assertFalse(c.archived.stream().anyMatch(a -> a.contains("backgroundComplete_success")), c.archived.toString());
    }

    @Test void workerSuccessCommitsAndReindexes() throws Exception {
        Call c = new Call(background());
        c.runWorker = true;
        post(c);
        assertEquals(200, c.status());
        Shepherd worker = c.byAction(WORKER);
        verify(worker).commitDBTransactionWithStatus();
        verify(worker, never()).rollbackAndClose();
        verify(worker).closeDBTransaction();
        assertEquals("complete", c.held.getStatus());
        assertEquals(1, c.deepReindexes);
        assertTrue(c.archived.stream().anyMatch(a -> a.contains("backgroundComplete_success")), c.archived.toString());
    }

    @Test void backgroundRequestCommitFailureKeeps200() throws Exception {
        Call c = new Call(background());
        c.commitFails.add(REQUEST);   // the request's own transaction (it holds only the lock)
        post(c);
        assertEquals(200, c.status(), "the worker owns the task: " + c.response);
        assertTrue(c.response.getBoolean("success"));
        assertEquals(1, c.launched.size());
        assertEquals("processing-background", c.held.getStatus(), "the request does not touch the worker's task");
        Shepherd request = c.byAction(REQUEST);
        InOrder order = inOrder(request);
        order.verify(request).commitDBTransactionWithStatus();
        order.verify(request).rollbackAndClose();
        verify(c.httpResponse).setStatus(200);
    }

    @Test void workerShepherdConstructionFailureMarksTheTaskFailed() throws Exception {
        Call c = new Call(background());
        c.runWorker = true;
        c.constructFails.add(3);   // request, reservation, then the worker's (built before it has an action)
        post(c);
        assertEquals(200, c.status(), "the request was accepted before the worker ran");
        assertEquals(0, c.importers);
        assertFalse(c.actions.containsValue(WORKER), "the worker's Shepherd was never built: " + c.actions.values());
        assertEquals("failed", c.held.getStatus());
        c.byAction(WRITER);
    }

    @Test void foregroundImportErrorPropagatesAndTaskFailed() throws Exception {
        Call c = new Call(valid());
        c.importFailure = new OutOfMemoryError("import ran out of memory");
        assertThrows(OutOfMemoryError.class, () -> post(c));
        assertEquals(1, c.importers);
        assertEquals("failed", c.held.getStatus());
        verify(c.byAction(REQUEST)).rollbackAndClose();
        verify(c.byAction(REQUEST), never()).commitDBTransactionWithStatus();
    }

    @Test void foregroundImportServletExceptionPropagatesAndTaskFailed() throws Exception {
        Call c = new Call(valid());
        c.importFailure = new javax.servlet.ServletException("import refused");
        javax.servlet.ServletException thrown = assertThrows(javax.servlet.ServletException.class, () -> post(c));
        assertEquals("import refused", thrown.getMessage());
        assertEquals(1, c.importers);
        assertEquals("failed", c.held.getStatus());
        verify(c.byAction(REQUEST)).rollbackAndClose();
        verify(c.byAction(REQUEST), never()).commitDBTransactionWithStatus();
    }

    static int countLog(ImportTask t, String text) {
        int n = 0;
        if (t.getLog() == null) return 0;
        for (Object entry : t.getLog()) if (String.valueOf(entry).contains(text)) n++;
        return n;
    }
}
