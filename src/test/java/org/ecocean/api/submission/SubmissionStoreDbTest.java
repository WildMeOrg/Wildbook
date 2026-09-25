package org.ecocean.api.submission;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.*;
import org.ecocean.CommonConfiguration;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SubmissionStoreDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    private static Properties properties;
    private static SubmissionStore store;
    private static java.util.Map<String, Properties> configCache;
    private static Properties priorConfig;
    @BeforeAll static void setup() throws Exception {
        TestPMFUtil.closePMF("context0");
        java.lang.reflect.Field field = CommonConfiguration.class.getDeclaredField("contextToPropsCache"); field.setAccessible(true);
        configCache = (java.util.Map<String, Properties>)field.get(null); priorConfig = configCache.get("context0");
        CommonConfiguration.initialize("context0", new Properties());
        properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateAll", "true");
        store = new SubmissionStore(() -> new Shepherd("context0", properties));
        // Pre-create metadata/tables before testing concurrent requests.
        store.create("context0", UUID.randomUUID().toString(), "bootstrap", create());
    }
    @AfterAll static void cleanup() {
        TestPMFUtil.closePMF("context0");
        if (configCache != null) { if (priorConfig == null) configCache.remove("context0"); else configCache.put("context0", priorConfig); }
    }
    private static JSONObject create() { return new JSONObject().put("contractVersion", "1").put("source", new JSONObject().put("name", "test")); }
    private JSONObject rows(int year) { return new JSONObject().put("rows", new JSONArray().put(new JSONObject()
        .put("clientRowId", "row-1").put("fields", new JSONObject().put("Encounter.year", year)))); }

    private boolean persistedEnrollment(String id) {
        Shepherd sh = new Shepherd("context0", properties);
        try {
            sh.beginDBTransaction();
            return SubmissionPolicy.enrolled(sh, id);
        } finally { sh.rollbackAndClose(); }
    }

    @Test void enrollmentTracksPersistedRoleGrantAndRevocation() {
        String id = UUID.randomUUID().toString();
        String username = "pilot'" + id;
        Shepherd sh = new Shepherd("context0", properties);
        try {
            sh.beginDBTransaction();
            org.ecocean.User user = new org.ecocean.User(username, id);
            user.setUsername(username);
            sh.getPM().makePersistent(user);
            org.ecocean.Role role = new org.ecocean.Role(username, org.ecocean.Role.API_SUBMISSION);
            role.setContext("context1");
            sh.getPM().makePersistent(role);
            assertTrue(sh.commitDBTransactionWithStatus());
            assertFalse(persistedEnrollment(id));
            sh.beginDBTransaction();
            role.setContext("context0");
            assertTrue(sh.commitDBTransactionWithStatus());
            assertTrue(persistedEnrollment(id));
            sh.beginDBTransaction();
            sh.getPM().deletePersistent(role);
            assertTrue(sh.commitDBTransactionWithStatus());
            assertFalse(persistedEnrollment(id));
        } finally { sh.rollbackAndClose(); }
    }

    @Test void durableRowsOwnershipAndOriginalReplay() {
        String owner = UUID.randomUUID().toString();
        JSONObject first = store.create("context0", owner, "key", create()); String id = first.getString("id");
        store.replaceRows("context0", owner, id, false, 0, rows(2026));
        TestPMFUtil.closePMF("context0"); // restart persistence context, not just a same-PM read
        assertEquals(1, store.get("context0", owner, id, false, false).getLong("revision"));
        assertEquals(2026, store.get("context0", owner, id, false, true).getJSONArray("rows")
            .getJSONObject(0).getJSONObject("fields").getInt("Encounter.year"));
        JSONObject replay = store.create("context0", owner, "key", create());
        assertEquals(first.toString(), replay.toString());
        assertEquals(404, assertThrows(SubmissionException.class, () -> store.get("context0", "another", id, false, false)).status);
        assertEquals(404, assertThrows(SubmissionException.class, () -> store.get("other-context", owner, id, false, false)).status);
        assertEquals(412, assertThrows(SubmissionException.class, () -> store.replaceRows("context0", owner, id, false, 0, rows(2025))).status);
        assertEquals(409, assertThrows(SubmissionException.class, () -> store.create("context0", owner, "key", create().put("source", new JSONObject().put("name", "different")))).status);
        store.cancel("context0", owner, id, false, 1);
        store.cancel("context0", owner, id, false, 1);
        assertEquals("cancelled", store.get("context0", owner, id, false, false).getString("state"));
    }

    @Test void concurrentCreatesDeduplicateAndCompetingRevisionsHaveOneWinner() throws Exception {
        String owner = UUID.randomUUID().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<JSONObject> task = () -> { start.await(); return store.create("context0", owner, "same-key", create()); };
            Future<JSONObject> a = pool.submit(task), b = pool.submit(task); start.countDown();
            String id = a.get(30, TimeUnit.SECONDS).getString("id");
            assertEquals(id, b.get(30, TimeUnit.SECONDS).getString("id"));
            Callable<Integer> edit = () -> {
                try { store.replaceRows("context0", owner, id, false, 0, rows(2026)); return 200; }
                catch (SubmissionException ex) { return ex.status; }
            };
            Future<Integer> e1 = pool.submit(edit), e2 = pool.submit(edit);
            java.util.List<Integer> codes = new java.util.ArrayList<>();
            codes.add(e1.get(30, TimeUnit.SECONDS)); codes.add(e2.get(30, TimeUnit.SECONDS));
            java.util.Collections.sort(codes);
            assertEquals(java.util.Arrays.asList(200, 412), codes);
            assertEquals(1, store.get("context0", owner, id, false, false).getLong("revision"));
        } finally { pool.shutdownNow(); }
    }

    @Test void unconfirmedCommitIsNotReportedAsSavedAndRollsBackRows() {
        String owner = UUID.randomUUID().toString();
        String id = store.create("context0", owner, "failure-case", create()).getString("id");
        SubmissionStore failing = new SubmissionStore(() -> new Shepherd("context0", properties) {
            @Override public boolean commitDBTransactionWithStatus() { return false; }
        });
        assertEquals(503, assertThrows(SubmissionException.class,
            () -> failing.replaceRows("context0", owner, id, false, 0, rows(2026))).status);
        assertEquals(0, store.get("context0", owner, id, false, false).getLong("revision"));
        assertEquals(0, store.get("context0", owner, id, false, true).getJSONArray("rows").length());
    }

    @Test void expiryAdminAccessAndConcurrentQuotaAdmission() throws Exception {
        String owner = UUID.randomUUID().toString();
        String expiredId = UUID.randomUUID().toString();
        Shepherd sh = new Shepherd("context0", properties);
        try {
            sh.beginDBTransaction();
            long past = System.currentTimeMillis() - SubmissionPolicy.DRAFT_TTL_MILLIS - 1000;
            sh.getPM().makePersistent(new org.ecocean.submission.Submission(expiredId, "context0", owner,
                SubmissionJson.hash(expiredId), SubmissionJson.hash("expired"),
                SubmissionJson.canonical(SubmissionJson.create(create())), past, past + SubmissionPolicy.DRAFT_TTL_MILLIS));
            assertTrue(sh.commitDBTransactionWithStatus());
        } finally { sh.rollbackAndClose(); }
        assertEquals("expired", store.get("context0", owner, expiredId, false, false).getString("state"));
        assertEquals(409, assertThrows(SubmissionException.class,
            () -> store.replaceRows("context0", owner, expiredId, false, 0, rows(2026))).status);
        assertEquals(expiredId, store.get("context0", "admin-user", expiredId, true, false).getString("id"));
        for (int i = 0; i < 19; i++) store.create("context0", owner, "quota-" + i, create());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Integer> task = () -> {
                start.await();
                try { store.create("context0", owner, UUID.randomUUID().toString(), create()); return 201; }
                catch (SubmissionException ex) { return ex.status; }
            };
            Future<Integer> a = pool.submit(task), b = pool.submit(task); start.countDown();
            java.util.List<Integer> codes = new java.util.ArrayList<>();
            codes.add(a.get(30, TimeUnit.SECONDS)); codes.add(b.get(30, TimeUnit.SECONDS));
            java.util.Collections.sort(codes);
            assertEquals(java.util.Arrays.asList(201, 429), codes);
        } finally { pool.shutdownNow(); }
    }
    @Test void uploadIsDurableIdempotentAndRejectsCompetingContent(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        SubmissionFiles files = new SubmissionFiles(root);
        String owner = UUID.randomUUID().toString(), id = store.create("context0", owner, "upload", create()).getString("id");
        byte[] image = SubmissionFilesTest.png();
        SubmissionStore.Receiver receive = limit -> files.write("a.png", new java.io.ByteArrayInputStream(image), limit);
        JSONObject first = store.upload("context0", owner, id, false, 0, files, receive);
        assertEquals(1, first.getLong("revision"));
        TestPMFUtil.closePMF("context0");
        assertEquals(first.toString(), store.manifest("context0", owner, id, false).toString());
        assertFalse(first.toString().contains("blob"));
        assertEquals(first.toString(), store.upload("context0", owner, id, false, 1, files, receive).toString());
        assertEquals(1, root.toFile().list().length);
        assertEquals(412, assertThrows(SubmissionException.class, () -> store.upload("context0", owner, id, false, 0, files, receive)).status);
        assertEquals(409, assertThrows(SubmissionException.class, () -> store.upload("context0", owner, id, false, 1, files,
            limit -> files.write("A.png", new java.io.ByteArrayInputStream(image), limit))).status);
        assertEquals(1, root.toFile().list().length);
        assertEquals(404, assertThrows(SubmissionException.class, () -> store.manifest("context0", "other", id, false)).status);
    }

    private JSONObject readyJob(String owner) {
        String id = store.create("context0", owner, UUID.randomUUID().toString(), create()).getString("id");
        String validation = UUID.randomUUID().toString();
        Shepherd sh = new Shepherd("context0", properties);
        try {
            sh.beginDBTransaction();
            org.ecocean.User user = new org.ecocean.User("pilot-" + owner, owner);
            user.setUsername("pilot-" + owner);
            sh.getPM().makePersistent(user);
            javax.jdo.Query<?> query = sh.getPM().newQuery(org.ecocean.submission.Submission.class, "id == :id");
            try {
                org.ecocean.submission.Submission draft = (org.ecocean.submission.Submission)((java.util.List<?>)query.execute(id)).get(0);
                draft.setValidation(new JSONObject().put("id", validation).put("valid", true).put("revision", 0).put("configDigest", SubmissionJson.hash(SubmissionJson.canonical(SubmissionValidator.configuration("context0")))).toString(), true);
                assertTrue(sh.commitDBTransactionWithStatus());
            } finally { query.closeAll(); }
        } finally { sh.rollbackAndClose(); }
        return new JSONObject().put("id", id).put("validationId", validation);
    }
    private JSONObject enqueue(SubmissionJobs jobs, String owner, JSONObject ready, String key) {
        try (org.mockito.MockedStatic<SubmissionPolicy> policy = org.mockito.Mockito.mockStatic(SubmissionPolicy.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            policy.when(() -> SubmissionPolicy.commitEnabled("context0")).thenReturn(true);
            policy.when(() -> SubmissionPolicy.enrolled("context0", owner)).thenReturn(true);
            return jobs.enqueue("context0", owner, ready.getString("id"), false, 0, key,
                new JSONObject().put("validationId", ready.getString("validationId")));
        }
    }
    @Test void commitRaceHasOneDurableExecutionAndLostResponseReplays() throws Exception {
        String owner = UUID.randomUUID().toString(); JSONObject ready = readyJob(owner);
        SubmissionJobs jobs = new SubmissionJobs(() -> new Shepherd("context0", properties));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<JSONObject> call = () -> enqueue(jobs, owner, ready, "same-commit-key");
            Future<JSONObject> a = pool.submit(call), b = pool.submit(call);
            JSONObject accepted = a.get(30, TimeUnit.SECONDS);
            java.nio.file.Files.writeString(java.nio.file.Path.of("target/submissions-accepted.json"), accepted.toString());
            assertEquals(accepted.toString(), b.get(30, TimeUnit.SECONDS).toString());
            assertEquals(409, assertThrows(SubmissionException.class, () -> enqueue(jobs, owner, ready, "different-key")).status);
            TestPMFUtil.closePMF("context0");
            assertEquals(accepted.toString(), enqueue(jobs, owner, ready, "same-commit-key").toString());
            Future<String> claimA = pool.submit(() -> jobs.claimNext("context0"));
            Future<String> claimB = pool.submit(() -> jobs.claimNext("context0"));
            java.util.List<String> claims = java.util.Arrays.asList(claimA.get(30, TimeUnit.SECONDS), claimB.get(30, TimeUnit.SECONDS));
            assertEquals(1, claims.stream().filter(java.util.Objects::nonNull).count());
            assertTrue(claims.contains(ready.getString("id")));
            jobs.execute("context0", ready.getString("id"), (draft, sh) -> new JSONObject().put("rows", new JSONArray()));
            assertEquals("imported", store.get("context0", owner, ready.getString("id"), false, false).getString("state"));
            assertNull(jobs.claimNext("context0"));
        } finally { pool.shutdownNow(); }
    }
    @Test void importFailureRollsBackDomainWriteAndIsNeverAutomaticallyReplayed() {
        String owner = UUID.randomUUID().toString(); JSONObject ready = readyJob(owner);
        SubmissionJobs jobs = new SubmissionJobs(() -> new Shepherd("context0", properties));
        enqueue(jobs, owner, ready, "commit"); assertEquals(ready.getString("id"), jobs.claimNext("context0"));
        String encounterId = UUID.randomUUID().toString();
        jobs.execute("context0", ready.getString("id"), (draft, sh) -> {
            org.ecocean.Encounter encounter = new org.ecocean.Encounter(); encounter.setId(encounterId); encounter.setSkipAutoIndexing(true);
            sh.getPM().makePersistent(encounter); sh.getPM().flush();
            throw new IllegalStateException("injected crash before commit");
        });
        assertEquals("needs_reconciliation", store.get("context0", owner, ready.getString("id"), false, false).getString("state"));
        assertNull(jobs.claimNext("context0"));
        Shepherd sh = new Shepherd("context0", properties);
        try {
            sh.beginDBTransaction();
            javax.jdo.Query<?> query = sh.getPM().newQuery(org.ecocean.Encounter.class, "catalogNumber == :id");
            try { assertTrue(((java.util.List<?>)query.execute(encounterId)).isEmpty()); }
            finally { query.closeAll(); }
        } finally { sh.rollbackAndClose(); }
    }

    @Test void realImporterDeferredPersistenceRemainsInsideCallerRollback() throws Exception {
        String ownerId = UUID.randomUUID().toString(), ownerName = "rollback-" + ownerId;
        Shepherd sh = new Shepherd("context0", properties); java.util.List<String> ids = new java.util.ArrayList<>();
        try {
            sh.beginDBTransaction();
            org.ecocean.User user = new org.ecocean.User(ownerName, ownerId); user.setUsername(ownerName); sh.getPM().makePersistent(user);
            java.util.Map<String, Object> row = new java.util.HashMap<>();
            row.put("Encounter.year", new org.ecocean.api.bulk.BulkValidator("Encounter.year", 2026, sh));
            row.put("Encounter.submitterID", new org.ecocean.api.bulk.BulkValidator("Encounter.submitterID", ownerName, sh));
            org.ecocean.api.bulk.BulkImporter importer = new org.ecocean.api.bulk.BulkImporter("reserved", java.util.Arrays.asList(row, row), null, user, sh)
                .deferSideEffects((index, encounter) -> ids.add(encounter.getId()));
            importer.createImport(); sh.getPM().flush();
            assertTrue(sh.isDBTransactionActive()); assertEquals(2, ids.size());
        } finally { sh.rollbackAndClose(); }
        sh = new Shepherd("context0", properties);
        try {
            sh.beginDBTransaction();
            javax.jdo.Query<?> query = sh.getPM().newQuery(org.ecocean.Encounter.class, "catalogNumber == :id");
            try { for (String id : ids) assertTrue(((java.util.List<?>)query.execute(id)).isEmpty()); }
            finally { query.closeAll(); }
        } finally { sh.rollbackAndClose(); }
    }
    @Test void lostCommitAcknowledgmentPreservesDurablyImportedResult() {
        String owner = UUID.randomUUID().toString(); JSONObject ready = readyJob(owner);
        SubmissionJobs jobs = new SubmissionJobs(() -> new Shepherd("context0", properties));
        enqueue(jobs, owner, ready, "commit"); assertEquals(ready.getString("id"), jobs.claimNext("context0"));
        SubmissionJobs uncertain = new SubmissionJobs(() -> new Shepherd("context0", properties) {
            @Override public boolean commitDBTransactionWithStatus() { super.commitDBTransactionWithStatus(); return false; }
        });
        uncertain.execute("context0", ready.getString("id"), (draft, sh) -> new JSONObject().put("rows", new JSONArray()));
        assertEquals("imported", store.get("context0", owner, ready.getString("id"), false, false).getString("state"));
        assertNull(jobs.claimNext("context0"));
    }

    private void mutate(String id, java.util.function.Consumer<org.ecocean.submission.Submission> change) {
        Shepherd sh = new Shepherd("context0", properties);
        try {
            sh.beginDBTransaction(); javax.jdo.Query<?> query = sh.getPM().newQuery(org.ecocean.submission.Submission.class, "id == :id");
            try { change.accept((org.ecocean.submission.Submission)((java.util.List<?>)query.execute(id)).get(0)); assertTrue(sh.commitDBTransactionWithStatus()); }
            finally { query.closeAll(); }
        } finally { sh.rollbackAndClose(); }
    }
    @Test void staleClaimsAreHeldButFreshDerivativeClaimsUseTheirOwnTimestamp() {
        String owner = UUID.randomUUID().toString(); JSONObject ready = readyJob(owner); String id = ready.getString("id");
        SubmissionJobs jobs = new SubmissionJobs(() -> new Shepherd("context0", properties));
        enqueue(jobs, owner, ready, "commit");
        mutate(id, draft -> draft.claim(System.currentTimeMillis() - 2 * 60 * 60 * 1000));
        jobs.reconcileStaleClaims("context0");
        assertEquals("needs_reconciliation", store.get("context0", owner, id, false, false).getString("state"));
        assertNull(jobs.claimNext("context0"));
        mutate(id, draft -> { draft.imported(new JSONObject().put("rows", new JSONArray()).toString()); draft.derivatives("running"); });
        jobs.reconcileStaleClaims("context0");
        assertEquals("running", store.get("context0", owner, id, false, false).getJSONObject("derivatives").getString("state"));
    }
    @Test void pendingWorkDoesNotCompeteWithCompletedHistoryAndReplayIsBounded() {
        SubmissionJobs jobs = new SubmissionJobs(() -> new Shepherd("context0", properties));
        java.util.Set<String> history = new java.util.HashSet<>();
        for (int i = 0; i < 7; i++) {
            String owner = UUID.randomUUID().toString(); String id = store.create("context0", owner, "history", create()).getString("id"); history.add(id);
            mutate(id, draft -> { draft.imported(new JSONObject().put("rows", new JSONArray()).toString()); draft.derivatives("complete"); draft.phase("unknown"); });
        }
        String pending = store.create("context0", UUID.randomUUID().toString(), "pending", create()).getString("id");
        mutate(pending, draft -> draft.imported(new JSONObject().put("rows", new JSONArray()).toString()));
        java.util.List<String> work = jobs.pendingPostprocessing("context0");
        assertTrue(work.contains(pending)); for (String id : history) assertFalse(work.contains(id));
        java.util.List<String> replay = jobs.replayBatch("context0", System.currentTimeMillis(), "");
        assertEquals(5, replay.size());
        java.util.List<String> next = jobs.replayBatch("context0", System.currentTimeMillis(), replay.get(4));
        java.util.Set<String> all = new java.util.HashSet<>(replay); all.addAll(next);
        assertTrue(java.util.Collections.disjoint(replay, next));
        assertTrue(all.containsAll(history));
    }

    @Test void oldImplicitCreateReplayKeepsSavedImportOnlyMode() {
        String owner = UUID.randomUUID().toString(); JSONObject explicit = create().put("processing", new JSONObject().put("mode", "import-only"));
        JSONObject old = store.create("context0", owner, "old-default", explicit);
        JSONObject replay = store.create("context0", owner, "old-default", create());
        assertEquals(old.getString("id"), replay.getString("id"));
        assertEquals("import-only", replay.getJSONObject("processing").getString("mode"));
        assertEquals("detect-and-identify", store.create("context0", owner, "new-default", create()).getJSONObject("processing").getString("mode"));
        assertEquals(409, assertThrows(SubmissionException.class, () -> store.create("context0", owner, "old-default",
            create().put("processing", new JSONObject().put("mode", "detect-and-identify")))).status);
    }
    private String importedForAi() {
        String owner = UUID.randomUUID().toString(); JSONObject ready = readyJob(owner);
        enqueue(new SubmissionJobs(() -> new Shepherd("context0", properties)), owner, ready, "ai-commit");
        String id = ready.getString("id");
        mutate(id, draft -> { draft.imported(new JSONObject().put("rows", new JSONArray())
            .put("records", new JSONObject().put("mediaAssets", new JSONArray().put(42))).toString()); draft.derivatives("complete"); });
        return id;
    }
    private String aiState(String id) {
        return store.get("context0", "admin", id, true, false).getJSONObject("detection").getString("state");
    }
    @Test void concurrentAiDispatchPublishesOnceAndNeverReplaysAfterRestart() throws Exception {
        String id = importedForAi(); java.util.concurrent.atomic.AtomicInteger publishes = new java.util.concurrent.atomic.AtomicInteger();
        SubmissionProcessing processing = new SubmissionProcessing(() -> new Shepherd("context0", properties));
        SubmissionProcessing.Preparation prepare = (draft, sh) -> new JSONObject().put("taskId", "durable-test-task");
        SubmissionProcessing.Publisher publish = (ctx, msg) -> { assertEquals("dispatching", aiState(id)); publishes.incrementAndGet(); };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = pool.submit(() -> { try { processing.dispatch("context0", id, prepare, publish); } catch (Exception ex) { throw new RuntimeException(ex); } });
            Future<?> second = pool.submit(() -> { try { processing.dispatch("context0", id, prepare, publish); } catch (Exception ex) { throw new RuntimeException(ex); } });
            first.get(20, TimeUnit.SECONDS); second.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(1, publishes.get()); assertEquals("dispatched", aiState(id));
        TestPMFUtil.closePMF("context0");
        processing.dispatch("context0", id, prepare, publish);
        assertEquals(1, publishes.get()); assertFalse(processing.pending("context0").contains(id));
    }
    @Test void realAiTasksAndResumeMessageAreCommittedBeforePublishing() throws Exception {
        String id = importedForAi(); SubmissionProcessing processing = new SubmissionProcessing(() -> new Shepherd("context0", properties));
        try (org.mockito.MockedStatic<org.ecocean.ia.IA> ia = org.mockito.Mockito.mockStatic(org.ecocean.ia.IA.class)) {
            ia.when(() -> org.ecocean.ia.IA.getBaseURL("context0")).thenReturn("https://example.test");
            processing.dispatch("context0", id, processing::prepare, (ctx, msg) -> {
                Shepherd sh = new Shepherd("context0", properties);
                try {
                    sh.beginDBTransaction(); JSONObject message = new JSONObject(msg);
                    org.ecocean.ia.Task task = org.ecocean.ia.Task.load(message.getString("taskId"), sh);
                    assertNotNull(task); assertEquals(msg, task.getQueueResumeMessage());
                    assertFalse(task.getParameters().getBoolean("skipIdent"));
                    org.ecocean.servlet.importer.ImportTask imported = sh.getImportTask(message.getJSONObject("taskParameters").getString("importTaskId"));
                    assertNotNull(imported.getIATask());
                    assertFalse(imported.getPassedParameters().getBoolean("skipDetection"));
                    assertFalse(imported.getPassedParameters().getBoolean("skipIdentification"));
                    assertEquals("detect-and-identify", imported.getPassedParameters().getString("processing"));
                } finally { sh.rollbackAndClose(); }
            });
        }
        assertEquals("dispatched", aiState(id));
    }
    @Test void aiFailureAndStaleDispatchAreHeldWithoutRepublishing() throws Exception {
        SubmissionProcessing processing = new SubmissionProcessing(() -> new Shepherd("context0", properties));
        String uncertain = importedForAi(); java.util.concurrent.atomic.AtomicInteger publishes = new java.util.concurrent.atomic.AtomicInteger();
        SubmissionProcessing.Preparation prepare = (draft, sh) -> new JSONObject();
        assertThrows(java.io.IOException.class, () -> processing.dispatch("context0", uncertain, prepare, (ctx, msg) -> { publishes.incrementAndGet(); throw new java.io.IOException("uncertain publish"); }));
        assertEquals("unknown", aiState(uncertain));
        processing.dispatch("context0", uncertain, prepare, (ctx, msg) -> publishes.incrementAndGet()); assertEquals(1, publishes.get());
        String failed = importedForAi();
        assertThrows(IllegalStateException.class, () -> processing.dispatch("context0", failed, (draft, sh) -> { throw new IllegalStateException("missing callback configuration"); }, (ctx, msg) -> fail("must not publish")));
        assertEquals("failed", aiState(failed));
        String stale = importedForAi(); mutate(stale, d -> d.claimAi(System.currentTimeMillis() - 2 * 60 * 60 * 1000));
        String derivatives = importedForAi(); mutate(derivatives, d -> d.derivatives("unknown"));
        processing.reconcile("context0"); assertEquals("unknown", aiState(stale)); assertEquals("failed", aiState(derivatives));
        processing.dispatch("context0", stale, prepare, (ctx, msg) -> fail("must not replay stale claim"));
    }
    @Test void oldImportOnlyAndFreshAiClaimsAreNotDispatchedOrReconciled() throws Exception {
        SubmissionProcessing processing = new SubmissionProcessing(() -> new Shepherd("context0", properties));
        String owner = UUID.randomUUID().toString();
        String old = store.create("context0", owner, "old-import", create().put("processing", new JSONObject().put("mode", "import-only"))).getString("id");
        mutate(old, d -> { d.imported(new JSONObject().put("rows", new JSONArray()).toString()); d.derivatives("complete"); d.aiState(null); });
        assertEquals("import-only", store.get("context0", owner, old, false, false).getJSONObject("processing").getString("mode"));
        processing.dispatch("context0", old, (draft, sh) -> { fail("must not prepare import-only"); return null; }, (ctx, msg) -> fail("must not publish import-only"));
        assertFalse(processing.pending("context0").contains(old));
        String fresh = importedForAi(); mutate(fresh, d -> d.claimAi(System.currentTimeMillis()));
        processing.reconcile("context0"); assertEquals("dispatching", aiState(fresh));
        mutate(fresh, d -> d.aiState("unknown"));
    }
    @Test void aiPreparationCommitFailureNeverPublishes() {
        String id = importedForAi();
        SubmissionProcessing processing = new SubmissionProcessing(() -> new Shepherd("context0", properties) {
            @Override public boolean commitDBTransactionWithStatus() { return false; }
        });
        assertThrows(SubmissionException.class, () -> processing.dispatch("context0", id, (draft, sh) -> new JSONObject(), (ctx, msg) -> fail("must not publish before commit")));
        assertEquals("pending", aiState(id));
    }

    @Test void invalidValidationCannotCommitAndStaleReportStillConflicts() {
        String owner = UUID.randomUUID().toString(); JSONObject ready = readyJob(owner);
        mutate(ready.getString("id"), draft -> draft.setValidation(new JSONObject().put("id", ready.getString("validationId"))
            .put("valid", false).put("revision", 0).toString(), false));
        SubmissionJobs jobs = new SubmissionJobs(() -> new Shepherd("context0", properties));
        SubmissionException invalid = assertThrows(SubmissionException.class, () -> enqueue(jobs, owner, ready, "invalid"));
        assertEquals(422, invalid.status); assertEquals("VALIDATION_INVALID", invalid.code);
        ready.put("validationId", UUID.randomUUID().toString());
        assertEquals(409, assertThrows(SubmissionException.class, () -> enqueue(jobs, owner, ready, "stale")).status);
    }

    @Test void realAdapterImportsTwoImagesWithDurableSourceRowMapping(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        String owner = UUID.randomUUID().toString(); JSONObject ready = readyJob(owner); String id = ready.getString("id");
        java.util.function.Supplier<Shepherd> supplier = () -> new Shepherd("context0", properties) {
            @Override public boolean isValidTaxonomyName(String value) { return true; }
        };
        SubmissionStore intake = new SubmissionStore(supplier); SubmissionJobs jobs = new SubmissionJobs(supplier);
        java.nio.file.Path staged = java.nio.file.Files.createDirectory(root.resolve("staged"));
        java.nio.file.Path assets = java.nio.file.Files.createDirectory(root.resolve("assets"));
        SubmissionFiles storage = new SubmissionFiles(staged);
        Shepherd sh = supplier.get();
        try {
            sh.beginDBTransaction();
            sh.getPM().makePersistent(new org.ecocean.media.LocalAssetStore("pilot", assets, "http://localhost/assets", true));
            assertTrue(sh.commitDBTransactionWithStatus());
        } finally { sh.rollbackAndClose(); }
        try (org.mockito.MockedStatic<org.ecocean.LocationID> locations = org.mockito.Mockito.mockStatic(org.ecocean.LocationID.class);
             org.mockito.MockedStatic<SubmissionPolicy> policy = org.mockito.Mockito.mockStatic(SubmissionPolicy.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            locations.when(org.ecocean.LocationID::getLocationIDStructure).thenReturn(new JSONObject("{\"locationID\":[{\"id\":\"reef\"}]}"));
            locations.when(() -> org.ecocean.LocationID.isValidLocationID("reef")).thenReturn(true);
            policy.when(() -> SubmissionPolicy.commitEnabled("context0")).thenReturn(true);
            policy.when(() -> SubmissionPolicy.enrolled("context0", owner)).thenReturn(true);
            JSONArray rows = new JSONArray(); long revision = 0;
            for (int n = 0; n < 2; n++) {
                String name = "photo-" + n + ".png";
                intake.upload("context0", owner, id, false, revision++, storage,
                    limit -> storage.write(name, new java.io.ByteArrayInputStream(SubmissionFilesTest.png()), limit));
                rows.put(new JSONObject().put("clientRowId", "source-" + n).put("fields", new JSONObject()
                    .put("Encounter.genus", "Manta").put("Encounter.specificEpithet", "birostris")
                    .put("Encounter.year", 2024 + n).put("Encounter.locationID", "reef").put("Encounter.mediaAsset0", name)));
            }
            intake.replaceRows("context0", owner, id, false, revision++, new JSONObject().put("rows", rows));
            JSONObject report = intake.validate("context0", owner, id, false, revision, storage);
            assertTrue(report.getBoolean("valid"), report.toString());
            jobs.enqueue("context0", owner, id, false, revision, "commit", new JSONObject().put("validationId", report.getString("id")));
            assertEquals(id, jobs.claimNext("context0"));
            jobs.execute("context0", id, (draft, transaction) -> {
                try { return new SubmissionImporter().execute(draft, draft.getJobId(), transaction, storage); }
                catch (Exception ex) { ex.printStackTrace(); throw ex; }
            });
            assertEquals("imported", intake.get("context0", owner, id, false, false).getString("state"));
            TestPMFUtil.closePMF("context0");
            JSONObject result = jobs.results("context0", owner, id, false, 0, 100);
            assertEquals(2, result.getJSONArray("rows").length());
            sh = supplier.get();
            try {
                sh.beginDBTransaction();
                for (int n = 0; n < 2; n++) {
                    JSONObject row = result.getJSONArray("rows").getJSONObject(n);
                    assertEquals("source-" + n, row.getString("clientRowId"));
                    org.ecocean.Encounter encounter = sh.getEncounter(row.getJSONArray("encounterIds").getString(0));
                    assertEquals(2024 + n, encounter.getYear());
                    assertEquals(1, row.getJSONArray("mediaAssetIds").length());
                    assertEquals(encounter.getOccurrenceID(), row.getJSONArray("occurrenceIds").getString(0));
                }
            } finally { sh.rollbackAndClose(); }
        } finally { org.ecocean.media.AssetStore.init(java.util.Collections.emptyList()); }
    }

    @Test void cleanupKeepsProtectedAndBusyFilesAndRemovesOnlyOldUnreferencedOrTerminalFiles(@org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
        SubmissionFiles files = new SubmissionFiles(root); SubmissionJobs jobs = new SubmissionJobs(() -> new Shepherd("context0", properties));
        java.util.List<JSONObject> retained = new java.util.ArrayList<>(); JSONObject cancelled = null, completed = null; String cancelledId = null;
        long old = System.currentTimeMillis() - SubmissionPolicy.DRAFT_TTL_MILLIS - 1000;
        for (String state : new String[]{"draft", "imported", "old-imported", "needs_reconciliation", "cancelled"}) {
            String id = store.create("context0", UUID.randomUUID().toString(), "cleanup", create()).getString("id");
            JSONObject entry = files.write("a.png", new java.io.ByteArrayInputStream(SubmissionFilesTest.png()), 10000);
            java.nio.file.Files.setLastModifiedTime(files.path(entry), java.nio.file.attribute.FileTime.fromMillis(old));
            java.nio.file.Files.setLastModifiedTime(files.path(entry).getParent(), java.nio.file.attribute.FileTime.fromMillis(old));
            mutate(id, draft -> {
                draft.setFiles(new JSONArray().put(entry).toString());
                if ("imported".equals(state)) draft.imported(new JSONObject().put("rows", new JSONArray()).toString());
                if ("old-imported".equals(state)) draft.imported(new JSONObject().put("rows", new JSONArray()).toString(), old);
                if ("needs_reconciliation".equals(state)) draft.fail("TEST_INTERRUPTION", true);
                if ("cancelled".equals(state)) draft.cancel();
            });
            if ("cancelled".equals(state)) { cancelled = entry; cancelledId = id; }
            else if ("old-imported".equals(state)) completed = entry;
            else retained.add(entry);
        }
        JSONObject orphan = files.write("orphan.png", new java.io.ByteArrayInputStream(SubmissionFilesTest.png()), 10000);
        java.nio.file.Files.setLastModifiedTime(files.path(orphan), java.nio.file.attribute.FileTime.fromMillis(old));
        java.nio.file.Files.setLastModifiedTime(files.path(orphan).getParent(), java.nio.file.attribute.FileTime.fromMillis(old));
        Shepherd locked = jobs.open();
        try {
            jobs.lock(locked, "submission:" + cancelledId);
            jobs.cleanup("context0", files);
            assertTrue(java.nio.file.Files.exists(files.path(cancelled)));
        } finally { locked.rollbackAndClose(); }
        jobs.cleanup("context0", files);
        assertFalse(java.nio.file.Files.exists(files.path(cancelled)));
        assertFalse(java.nio.file.Files.exists(files.path(orphan)));
        assertFalse(java.nio.file.Files.exists(files.path(completed)));
        for (JSONObject entry : retained) assertTrue(java.nio.file.Files.exists(files.path(entry)));
    }

    @Test void createCancelChurnCannotBypassDailyAdmissionBudget() {
        String owner = UUID.randomUUID().toString(); JSONObject first = null;
        for (int n = 0; n < 20; n++) {
            JSONObject draft = store.create("context0", owner, "daily-" + n, create());
            if (n == 0) first = draft;
            store.cancel("context0", owner, draft.getString("id"), false, 0);
        }
        assertEquals(429, assertThrows(SubmissionException.class, () -> store.create("context0", owner, "over-budget", create())).status);
        assertEquals(first.toString(), store.create("context0", owner, "daily-0", create()).toString());
    }

}
