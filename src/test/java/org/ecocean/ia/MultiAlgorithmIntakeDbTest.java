package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.IAJsonProperties;
import org.ecocean.MultiPipelineIAJson;
import org.ecocean.RestClient;
import org.ecocean.TestIAJsonPropertiesUtil;
import org.ecocean.identity.IBEISIA;
import org.ecocean.identity.IdentityServiceLog;
import org.ecocean.ia.plugin.WildbookIAM;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Spec §5.2: the intake core, the ml-service match step, the identify handler's retry
 * contract, the selector and the import status against real DataNucleus on real Postgres.
 * The dispatcher seam builds vector leaves the way Embedding.findMatchProspects does and
 * records HotSpotter jobs; WBIA is answered by partial static mocks. No OpenSearch,
 * ml-service, WBIA or file queue is touched.
 */
@Testcontainers
class MultiAlgorithmIntakeDbTest extends IdentificationDbTestSupport {
    @BeforeAll static void configureIdentification() {
        IAJsonProperties config = IAJsonProperties.iaConfig();
        config.setJson(iaJson());
        TestIAJsonPropertiesUtil.override(config);
    }
    @AfterAll static void resetDispatcher() {
        IA.setDispatcherForTests(null);
        TestIAJsonPropertiesUtil.override(null);
    }
    /** Production-shaped stand-in for the two dispatch targets. */
    static final class LeafBuildingDispatcher implements IA.Dispatcher {
        volatile Runnable onFirstVector = null;      // persist-before-dispatch probe
        String throwForIaClass = null;
        boolean killTransactionOnVector = false;          // a datastore failure mid-dispatch: the transaction dies
        boolean closePersistenceManagerOnVector = false;  // worse: the manager itself is gone
        final List<JSONObject> published = new ArrayList<JSONObject>();
        int vectorCalls = 0;                              // counted at entry, before anything else

        @Override public void runVector(JSONObject opt, Task optionTask, Shepherd shep) {
            vectorCalls++;
            Runnable probe = onFirstVector;
            onFirstVector = null;
            if (probe != null) probe.run();
            if (killTransactionOnVector) shep.rollbackDBTransaction();   // on a strict Shepherd the writes below then throw
            if (closePersistenceManagerOnVector) shep.getPM().close();   // every later datastore touch throws
            String iaClass = optionTask.getObjectAnnotations().get(0).getIAClass();
            if (iaClass.equals(throwForIaClass)) {
                Task leaf = leaf(optionTask, optionTask.getObjectAnnotations().get(0));
                shep.getPM().makePersistent(leaf);           // attached, unfinished
                throw new IllegalStateException("vector dispatch failed for " + iaClass);
            }
            for (Annotation a : optionTask.getObjectAnnotations()) {
                Task leaf = leaf(optionTask, a);
                leaf.setStatus("completed");
                leaf.setCompletionDateInMilliseconds();
                shep.getPM().makePersistent(leaf);
            }
            optionTask.setStatus("completed");
            optionTask.setCompletionDateInMilliseconds();
        }

        @Override public boolean publish(String context, String job, boolean fastlane) {
            published.add(new JSONObject(job));
            return true;
        }

        static Task leaf(Task option, Annotation a) {
            Task leaf = new Task(option);                   // inherits the option's parameters
            JSONObject p = leaf.getParameters();
            p.remove(Task.PARAM_OPTION);
            leaf.setParameters(p);
            leaf.addObject(a);
            return leaf;
        }
    }

    static LeafBuildingDispatcher dispatcher;

    @BeforeEach
    void freshDispatcher() {
        dispatcher = new LeafBuildingDispatcher();
        IA.setDispatcherForTests(dispatcher);
    }

    // JsonProperties.get walks dotted keys through NESTED objects, so the fixture nests
    // genus -> species -> iaClass exactly like a real IA.json.
    // Pseudobatos lentiginosus: MiewID + HotSpotter default for both classes.
    // Rhina ancylostoma: HotSpotter only (single-option runs).


    // ---- fixtures ----

    // An encounter with one annotation per iaClass, each on its own persisted MediaAsset with a
    // bbox feature wired on both owning sides (Feature.asset via addFeature, Feature.annotation
    // via setAnnotation — the mapped-by side of Annotation.features). Its scalars are snapshotted
    // into the registry before the caller commits.

    // root (stamped with the import) -> D (detection task, completed)


    // the ImportTask's own id is what statsAnnotations scopes by, so it MUST equal the stamp


    // registry-backed: safe before and after the fixture's transaction

    static JSONObject matchConfig() {
        return new JSONObject("{\"method\":\"miewid\",\"version\":\"msv4_v3\"}");
    }

    static MlServiceProcessor processor() {
        return processorFailingCommit(-1);
    }

    // Production write defaults everywhere else; the two dead-transaction cases make only the
    // processor's own managers strict (non-transactional writes refused), which is per-manager.
    static MlServiceProcessor strictProcessor() {
        MatchVisibilityGate gate = (ids, task, cfg, attempt, first) ->
            MatchVisibilityGate.GateOutcome.ready(attempt, System.currentTimeMillis());
        DeferredMatchPublisher publisher = payload -> true;
        return new MlServiceProcessor(CTX, new MlServiceClient(), gate, publisher) {
            @Override Shepherd openShepherd(String action) {
                Shepherd s = new Shepherd(CTX) {
                    @Override public void beginDBTransaction() {
                        super.beginDBTransaction();
                        try {
                            getPM().currentTransaction().setNontransactionalWrite(false);
                        } catch (RuntimeException ex) {   // a closed manager: nothing to make strict
                        }
                    }
                };
                s.setAction("MultiAlgorithmIntakeDbTest.strictProcessor." + action);
                return s;
            }
        };
    }

    // The processor's own Shepherds come from openShepherd; the Nth checked commit (counting
    // across the main and the recovery Shepherd) returns false, as a lost connection would.
    static MlServiceProcessor processorFailingCommit(final int failOnCall) {
        final int[] calls = { 0 };
        MatchVisibilityGate gate = (ids, task, cfg, attempt, first) ->
            MatchVisibilityGate.GateOutcome.ready(attempt, System.currentTimeMillis());
        DeferredMatchPublisher publisher = payload -> true;
        return new MlServiceProcessor(CTX, new MlServiceClient(), gate, publisher) {
            @Override Shepherd openShepherd(String action) {
                Shepherd s = new Shepherd(CTX) {
                    @Override public boolean commitDBTransactionWithStatus() {
                        if (++calls[0] == failOnCall) return false;
                        return super.commitDBTransactionWithStatus();
                    }
                };
                s.setAction("MultiAlgorithmIntakeDbTest.processor." + action);
                return s;
            }
        };
    }

    // Like processorFailingCommit, but the transaction RESTART right after the Nth checked commit
    // throws once (the run is durable, the Shepherd cannot continue).
    static MlServiceProcessor processorFailingRestart(final int afterCommitCall) {
        final int[] calls = { 0 };
        final boolean[] thrown = { false };
        MatchVisibilityGate gate = (ids, task, cfg, attempt, first) ->
            MatchVisibilityGate.GateOutcome.ready(attempt, System.currentTimeMillis());
        DeferredMatchPublisher publisher = payload -> true;
        return new MlServiceProcessor(CTX, new MlServiceClient(), gate, publisher) {
            @Override Shepherd openShepherd(String action) {
                Shepherd s = new Shepherd(CTX) {
                    @Override public boolean commitDBTransactionWithStatus() {
                        calls[0]++;
                        return super.commitDBTransactionWithStatus();
                    }

                    @Override public void beginDBTransaction() {
                        if ((calls[0] == afterCommitCall) && !thrown[0]) {
                            thrown[0] = true;
                            throw new IllegalStateException("datastore gone after commit " + afterCommitCall);
                        }
                        super.beginDBTransaction();
                    }
                };
                s.setAction("MultiAlgorithmIntakeDbTest.processor." + action);
                return s;
            }
        };
    }

    /**
     * A fake WBIA behind the REAL WildbookIAM registration methods: RestClient is mocked, the
     * two id-list fetches answer from the lists here, every POST is recorded, and annotation
     * POSTs echo the uuids they were sent (forced path) or mint fresh ones (legacy path, which
     * sends no annot_uuid_list — exactly what real WBIA does). The eligibility chain runs real
     * unless an override names the annotation id.
     */

    // an identification task that holds the query annotation (the HotSpotter option of a run)

    // Partial static mocks for a real _doIdentify run (same shape as IAGatewayDoIdentifyTest):
    // IBEISIA.beginIdentifyAnnotations answers sendResult, requeueJob answers through `requeue`.

    static Task optionOf(Task umbrella, boolean vector, String iaClass) {
        for (Task t : childrenOf(umbrella)) {
            boolean v = t.getParameters().optBoolean(Task.PARAM_V2_MATCH, false);
            if ((v == vector) && iaClass.equals(t.getObjectAnnotations().get(0).getIAClass())) return t;
        }
        return null;
    }

    // ---- cases ----

    @Test void processorBuildsOneUmbrellaPerEncounterAndPersistsBeforeDispatch() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Encounter e2 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        final String dId = d.getId();
        dispatcher.onFirstVector = () -> {
            Shepherd probe = open("probe");   // a second PersistenceManager
            try {
                Task dd = Task.load(dId, probe);
                assertEquals(1, childrenOf(dd).size(), "the first group's umbrella is committed before its dispatch");
                Task u = childrenOf(dd).get(0);
                assertTrue(Task.isUmbrella(u));
                assertEquals(4, childrenOf(u).size(), "its option tasks are committed too");
            } finally {
                probe.rollbackAndClose();
            }
        };
        MlServiceJobOutcome out = processor().runMatchProspects(ids, dId, matchConfig());
        assertEquals(MlServiceJobOutcome.Kind.OK, out.getKind());
        assertNull(dispatcher.onFirstVector, "the probe ran");

        Shepherd check = open("check");
        try {
            Task dd = Task.load(dId, check);
            assertEquals("completed", dd.getStoredStatus(), "D is untouched");
            assertEquals(2, childrenOf(dd).size(), "one umbrella per encounter");
            int options = 0, leaves = 0;
            for (Task u : childrenOf(dd)) {
                assertTrue(Task.isUmbrella(u));
                assertEquals(dId, u.getParent().getId());
                assertEquals(dd.getParameters().getString("importTaskId"), u.getParameters().getString("importTaskId"));
                assertFalse(u.getParameters().has("ibeis.detection"));
                for (Task opt : childrenOf(u)) {
                    options++;
                    assertEquals(u.getId(), Task.runIdOf(opt));
                    assertTrue(Task.isOptionTask(opt));
                    if (opt.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) {
                        assertEquals("completed", opt.getStoredStatus());
                        for (Task leaf : childrenOf(opt)) {
                            leaves++;
                            assertEquals(u.getId(), Task.runIdOf(leaf));
                            assertFalse(Task.isOptionTask(leaf));
                            assertEquals("completed", leaf.getStoredStatus());
                        }
                    } else {
                        assertNull(opt.getStoredStatus(), "HotSpotter waits for WBIA");
                        assertNotNull(opt.getQueueResumeMessage());
                    }
                }
            }
            assertEquals(6, options);
            assertEquals(3, leaves);
            assertEquals(3, dispatcher.published.size());
        } finally {
            check.rollbackAndClose();
        }
    }

    static JSONObject petSpace() {
        return new JSONObject().put("pipeline_root", "vector").put("method", "miewid")
            .put("version", "pet_v1.1.0").put("api_endpoint", MultiPipelineIAJson.FACE_ENDPOINT)
            .put("description", "MiewID pet (vector)");
    }

    /**
     * A multi-pipeline species (docs/plans/2026-10-16-mlservice-multi-config.md) matches in the
     * embedding space MlServiceProcessor resolved for the annotation, which can differ from the
     * class's configured vector option. Extraction pins without being a match group.
     */
    @Test void multiPipelineSpeciesMatchesInItsResolvedEmbeddingSpace() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = encounter(sh, "Canis", "familiaris", "dog");
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        MlServiceJobOutcome out = processor().runMatchProspects(ids, d.getId(), petSpace(), false, true);
        assertEquals(MlServiceJobOutcome.Kind.OK, out.getKind());

        Shepherd check = open("check");
        try {
            Task u = childrenOf(Task.load(d.getId(), check)).get(0);
            Task vopt = optionOf(u, true, "dog");
            JSONObject ident = vopt.getParameters().getJSONObject("ibeis.identification");
            assertEquals("pet_v1.1.0", ident.getString("version"), "not the class's msv4_v3");
            assertEquals(MultiPipelineIAJson.FACE_ENDPOINT, ident.getString("api_endpoint"));
            assertEquals(1, dispatcher.published.size(), "HotSpotter still runs");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void unpinnedMatchKeepsTheConfiguredVectorOption() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK,
            processor().runMatchProspects(ids, d.getId(), petSpace(), false, false).getKind());

        Shepherd check = open("check");
        try {
            Task u = childrenOf(Task.load(d.getId(), check)).get(0);
            JSONObject ident = optionOf(u, true, BODY).getParameters().getJSONObject("ibeis.identification");
            assertEquals("msv4_v3", ident.getString("version"), "unpinned: only filled");
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void initialPersistFailureDispatchesNothingAndMarksEveryAnnotation() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        String importId = nextImportId();
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1);
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        // checked commit 1 = the intake core's persist commit
        MlServiceJobOutcome out = processorFailingCommit(1).runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode());
        assertEquals(0, dispatcher.vectorCalls, "no vector dispatch was entered");
        assertTrue(dispatcher.published.isEmpty(), "nothing was dispatched");
        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("MATCH_PERSIST", errorCode(dd));
            assertEquals(1, childrenOf(dd).size(), "recovery creates an error run for the undispatched group");
            Task failed = childrenOf(dd).get(0);
            assertEquals("MATCH_PERSIST", errorCode(failed));
            assertEquals(new HashSet<String>(ids), new HashSet<String>(idsOf(failed.getObjectAnnotations())));
        } finally {
            check.rollbackAndClose();
        }
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(ids.size(), sum.getInt("identificationNumberError"));
    }

    @Test void selectorLandsOnTheUmbrellaAfterReloadAndRendersExactlyItsRun() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Task r = new Task();
        r.setParameters(new JSONObject().put("importTaskId", "IMP-2"));
        Task subParent = new Task(r);
        subParent.addParameter("matchingSetFilter", new JSONObject());
        sh.getPM().makePersistent(r);
        sh.getPM().makePersistent(subParent);
        IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, new ArrayList<Annotation>(e1.getAnnotations()),
            subParent, subParent.getParameters(), false);
        assertFalse(o.persistFailed);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        String uId = o.umbrella.getId();
        Set<String> encSet = idSet(e1);

        Shepherd sh2 = open("reload");   // five levels: r -> subParent -> U -> T_vec -> SA
        try {
            for (String annId : annotationIds(e1)) {
                Annotation ann = sh2.getAnnotation(annId);
                Task picked = Task.getPreferredMatchResultsTaskForAnnotation(ann, "IMP-2", encSet, sh2);
                assertEquals(uId, picked.getId(), "both annotations link to the run's umbrella");
                assertEquals(uId, Task.getPreferredMatchResultsTaskForAnnotation(ann, null, encSet, sh2).getId(),
                    "the encounter page (unscoped) agrees");
            }
            Task u = Task.load(uId, sh2);
            JSONObject json = u.matchResultsJson(10, null, sh2);
            JSONArray options = json.getJSONArray("children");
            assertEquals(4, options.length());
            Set<String> leafIds = new HashSet<String>();
            for (int i = 0; i < options.length(); i++) {
                JSONArray kids = options.getJSONObject(i).optJSONArray("children");
                if (kids == null) continue;
                for (int j = 0; j < kids.length(); j++) leafIds.add(kids.getJSONObject(j).getString("id"));
            }
            Set<String> expected = new HashSet<String>();
            for (Task opt : childrenOf(u)) for (Task leaf : childrenOf(opt)) expected.add(leaf.getId());
            assertEquals(2, expected.size());
            assertEquals(expected, leafIds, "the page renders exactly the run's leaves");
        } finally {
            sh2.rollbackAndClose();
        }
    }

    @Test void importStatusCountsEveryOptionOfTheRun() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Encounter e2 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1, e2);
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), matchConfig()).getKind());

        // vector leaves completed, HotSpotter options still queued -> sent
        JSONObject sum = summary(itask.getId());
        assertEquals("sent", sum.getString("identificationStatus"));
        assertEquals(3, sum.getInt("identificationNumTotal"), "exactly one latest task per non-trivial annotation");
        assertEquals(0, sum.getInt("identificationNumberComplete"));

        // every HotSpotter option completes (the WBIA callback) -> complete
        for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
        sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(3, sum.getInt("identificationNumberComplete"));
        assertEquals(0, sum.getInt("identificationNumberError"));

        JSONArray e1Before = sum.getJSONObject("statsAnnotations").getJSONObject("encounterTaskInfo").getJSONArray(e1.getCatalogNumber());
        // e2's vector LEAF (the option's representative) fails -> still complete, one error
        setStatus(vectorLeafId(d.getId(), e2), "error");
        sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberError"));

        // the Class label of a shared task merges the classes
        JSONObject info = sum.getJSONObject("statsAnnotations").getJSONObject("encounterTaskInfo");
        JSONArray e1Tasks = info.getJSONArray(e1.getCatalogNumber());
        assertEquals(1, e1Tasks.length(), "both annotations of e1 link to one task");
        assertEquals(BODY + ", " + ROSTRUM, e1Tasks.getJSONArray(0).getString(2));
        assertEquals(1, e1Before.length());
        assertEquals(e1Before.getJSONArray(0).getString(0), e1Tasks.getJSONArray(0).getString(0), "e2's failure leaves e1's task unchanged");
        assertEquals(e1Before.getJSONArray(0).getString(2), e1Tasks.getJSONArray(0).getString(2), "and its label");
        assertEquals(e1Before.getJSONArray(0).get(1).toString(), e1Tasks.getJSONArray(0).get(1).toString(),
            "and its overall status");
        JSONArray e2Tasks = info.getJSONArray(e2.getCatalogNumber());
        assertEquals(1, e2Tasks.length());
        assertTrue(e2Tasks.getJSONArray(0).get(1).toString().contains("error"),
            "e2's overall status shows its failed leaf: " + e2Tasks.getJSONArray(0).get(1));
    }

    /** §5.2 status matrix: error + pending, single-HotSpotter pending, skipped class, late fan-out next to a newer run. */
    @Test void importStatusMatrixVariants() throws Exception {
        // (a) error vector leaf + pending HotSpotter -> sent
        Shepherd sh = open("fixture-a");
        String importA = nextImportId();
        Encounter ea = guitarfish(sh, BODY);
        Task da = detectionTask(sh, importA);
        ImportTask ia = importWith(sh, importA, da.getParent(), ea);
        List<String> idsA = annotationIds(ea);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        processor().runMatchProspects(idsA, da.getId(), matchConfig());
        setStatus(vectorLeafId(da.getId(), ea), "error");
        assertEquals("sent", summary(ia.getId()).getString("identificationStatus"), "pending beats error");

        // (b) single-HotSpotter umbrella pending -> sent; completed -> complete
        sh = open("fixture-b");
        String importB = nextImportId();
        Encounter eb = encounter(sh, "Rhina", "ancylostoma", BODY);
        Task db = detectionTask(sh, importB);
        ImportTask ib = importWith(sh, importB, db.getParent(), eb);
        List<String> idsB = annotationIds(eb);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        processor().runMatchProspects(idsB, db.getId(), matchConfig());
        assertEquals("sent", summary(ib.getId()).getString("identificationStatus"));
        for (String id : hotspotterOptionIds(db.getId())) setStatus(id, "completed");
        assertEquals("complete", summary(ib.getId()).getString("identificationStatus"));

        // (c) a class without options next to one with -> completed + skipped for that annotation
        sh = open("fixture-c");
        String importC = nextImportId();
        Encounter ec = guitarfish(sh, BODY, "mystery");
        Task dc = detectionTask(sh, importC);
        ImportTask ic = importWith(sh, importC, dc.getParent(), ec);
        List<String> idsC = annotationIds(ec);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        processor().runMatchProspects(idsC, dc.getId(), matchConfig());
        for (String id : hotspotterOptionIds(dc.getId())) setStatus(id, "completed");
        JSONObject sumC = summary(ic.getId());
        assertEquals("complete", sumC.getString("identificationStatus"));
        assertEquals(1, sumC.getJSONObject("statsAnnotations").getInt("numLatestTask_skipped"));
        assertEquals(2, sumC.getInt("identificationNumberComplete"));

        // (d) a late fan-out child under an OLDER run next to a NEWER pending run -> sent
        sh = open("fixture-d");
        String importD = nextImportId();
        Encounter ed = guitarfish(sh, BODY);
        Task dd = detectionTask(sh, importD);
        ImportTask idTask = importWith(sh, importD, dd.getParent(), ed);
        List<Annotation> annsD = new ArrayList<Annotation>(ed.getAnnotations());
        JSONObject inherited = MlServiceProcessor.matchIntakeParams(dd.getParameters());
        IA.IntakeOutcome run1 = IA.intakeAnnotationsUnder(sh, annsD, dd, inherited, false);
        sh.commitDBTransaction();
        sh.beginDBTransaction();
        IA.IntakeOutcome run2 = IA.intakeAnnotationsUnder(sh, annsD, dd, inherited, false);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        String run1Hs = null;
        for (IA.OptionResult r : run1.options) if (!r.vector) run1Hs = r.task.getId();
        setStatus(run1Hs, "completed");                    // run 1 fully complete
        Shepherd late = open("late");                      // the late fan-out child, created after run 2
        try {
            Task hs1 = Task.load(run1Hs, late);
            Task child = new Task(hs1);
            child.setParameters(Task.withoutOptionMarker(hs1.getParameters()));   // a fan-out child is a leaf, not an option (§4.0)
            child.addObject(late.getAnnotation(annsD.get(0).getId()));
            child.setStatus("completed");
            late.getPM().makePersistent(child);
            late.commitDBTransaction();
        } finally {
            late.closeDBTransaction();
        }
        assertEquals("sent", summary(idTask.getId()).getString("identificationStatus"),
            "run 2's pending HotSpotter wins over run 1's late completed child");
        assertNotNull(run2.umbrella);
    }

    /** _doIdentify mid fan-out: the option itself represents the annotation without a child. */
    @Test void midFanOutReadsPendingForTheUnfannedAnnotation() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY, BODY);        // two body annotations share one option
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1);
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        processor().runMatchProspects(ids, d.getId(), matchConfig());
        String hsId = hotspotterOptionIds(d.getId()).get(0);
        Shepherd fan = open("fanout");                      // H_A exists, H_B not yet
        try {
            Task hs = Task.load(hsId, fan);
            JSONObject params = hs.getParameters();             // as _doIdentify: snapshot, then strip the option
            hs.setParameters(Task.identityParams(params));      // drops ibeis.identification; keeps run id + option marker
            Task hA = new Task(hs);
            hA.setParameters(Task.withoutOptionMarker(params));
            hA.addObject(fan.getAnnotation(ids.get(0)));
            hA.setStatus("completed");
            fan.getPM().makePersistent(hA);
            fan.commitDBTransaction();
        } finally {
            fan.closeDBTransaction();
        }
        JSONObject sum = summary(itask.getId());
        assertEquals("sent", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberComplete"), "A is complete, B still waits on its fan-out");
    }

    @Test void secondGroupCommitFailureKeepsTheFirstGroupAndMarksTheSecond() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Encounter e2 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1, e2);
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        // checked commits: 1 = group-1 intake persist, 2 = group-1 commit, 3 = group-2 intake persist, 4 = group-2 commit
        MlServiceJobOutcome out = processorFailingCommit(4).runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode());

        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("error", dd.getStoredStatus());
            assertEquals("MATCH_PERSIST", errorCode(dd));
            assertEquals(2, childrenOf(dd).size(), "both umbrellas are durable (the intake persist commit)");
            Task u1 = umbrellaFor(dd, e1);
            Task u2 = umbrellaFor(dd, e2);
            assertNull(u1.getStoredStatus(), "group 1 untouched");
            for (Task opt : childrenOf(u1)) {
                if (opt.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) {
                    assertEquals("completed", opt.getStoredStatus());
                    assertEquals(1, childrenOf(opt).size(), "group 1's leaves are durable");
                }
            }
            assertEquals("error", u2.getStoredStatus());
            assertEquals("MATCH_PERSIST", errorCode(u2));
            Task v2 = optionOf(u2, true, BODY);
            assertEquals("error", v2.getStoredStatus());
            assertEquals("MATCH_PERSIST", errorCode(v2));
            assertEquals(0, childrenOf(v2).size(), "its leaves were rolled back");
            Task h2 = optionOf(u2, false, BODY);
            assertNull(h2.getStoredStatus(), "a published HotSpotter option is left to its job");
        } finally {
            check.rollbackAndClose();
        }
        // the HotSpotter jobs complete later -> the import ends complete-with-errors
        for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberError"));
    }

    @Test void vectorDispatchExceptionMarksTheOptionAndItsAttachedLeaf() throws Exception {
        dispatcher.throwForIaClass = ROSTRUM;
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1);
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), matchConfig()).getKind(),
            "a dispatch failure of one option is not a job failure");

        Shepherd check = open("check");
        try {
            Task u = umbrellaFor(Task.load(d.getId(), check), e1);
            Task vr = optionOf(u, true, ROSTRUM);
            assertEquals("error", vr.getStoredStatus());
            assertEquals("DISPATCH_FAILED", errorCode(vr));
            assertEquals(1, childrenOf(vr).size());
            assertEquals("error", childrenOf(vr).get(0).getStoredStatus(), "the attached leaf is terminalized");
            Task vb = optionOf(u, true, BODY);
            assertEquals("completed", vb.getStoredStatus(), "the other option still ran");
            assertEquals(2, dispatcher.published.size());
        } finally {
            check.rollbackAndClose();
        }
        for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberError"), "rostrum reads error through its option");
    }

    @Test void dispatchExceptionThenGroupCommitFailureMarksEveryUnpublishedOption() throws Exception {
        dispatcher.throwForIaClass = ROSTRUM;
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        // 1 = intake persist, 2 = the group commit
        MlServiceJobOutcome out = processorFailingCommit(2).runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode());

        Shepherd check = open("check");
        try {
            Task u = umbrellaFor(Task.load(d.getId(), check), e1);
            assertEquals("MATCH_PERSIST", errorCode(u));
            assertEquals("MATCH_PERSIST", errorCode(optionOf(u, true, BODY)), "published vector option: its leaves were rolled back");
            assertEquals("MATCH_PERSIST", errorCode(optionOf(u, true, ROSTRUM)), "its DISPATCH_FAILED mark was rolled back too");
            assertNull(optionOf(u, false, BODY).getStoredStatus(), "published HotSpotter options are untouched");
            assertNull(optionOf(u, false, ROSTRUM).getStoredStatus());
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void singleHotspotterRunWhosePublishedJobSurvivesACommitFailureStaysPending() throws Exception {
        Shepherd sh = open("fixture");
        Encounter rhina = encounter(sh, "Rhina", "ancylostoma", BODY);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(rhina);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        MlServiceJobOutcome out = processorFailingCommit(2).runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode());

        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("MATCH_PERSIST", errorCode(dd));
            Task u = childrenOf(dd).get(0);
            assertTrue(Task.isUmbrella(u));
            assertTrue(Task.isOptionTask(u), "the umbrella is the single option");
            assertNull(u.getStoredStatus(), "its job was published and will complete it");
            assertEquals(1, dispatcher.published.size());
        } finally {
            check.rollbackAndClose();
        }
    }

    /** §4.4: a poll between the inner commit and the requeue decision sees a pending task, not a terminal error. */
    @Test void failedSendIsPendingWhilePollingBetweenTheInnerCommitAndTheRequeueDecision() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1);
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), matchConfig()).getKind());
        String hsId = hotspotterOptionIds(d.getId()).get(0);
        // the log line a failed send leaves behind: with a NULL stored status getStatus reads it as "error"
        Shepherd logSh = open("log");
        try {
            logSh.getPM().makePersistent(new IdentityServiceLog(hsId, "IBEISIA", "-1",   // the 4-arg ctor: objectIDs null
                new JSONObject().put("_action", "sendIdentify")
                    .put("_response", new JSONObject().put("error", "HTTP error code : 500"))));
            logSh.commitDBTransaction();
        } finally {
            logSh.closeDBTransaction();
        }
        final String[] polled = new String[1];
        JSONObject failed = new JSONObject().put("success", false).put("error", "WBIA refused");
        JSONObject jin = new JSONObject()
            .put("identify", new JSONObject().put("annotationIds", new JSONArray().put(ids.get(0))))
            .put("opt", new JSONObject());
        Shepherd consumer = open("consumer");   // the queue consumer's Shepherd
        try {
            withIdentifyStubs(failed, inv -> {
                polled[0] = summary(itask.getId()).getString("identificationStatus");   // a second PM
                return true;                                                              // retry scheduled
            }, () -> {
                IAGateway._doIdentify(jin, new JSONObject().put("taskId", hsId), consumer, CTX, "http://wb", false);
            });
            consumer.commitDBTransaction();
        } finally {
            consumer.closeDBTransaction();
        }
        assertEquals("sent", polled[0], "pending between the inner commit and the requeue decision");
        Shepherd check = open("check");
        try {
            Task h = Task.load(hsId, check);
            assertEquals(Task.STATUS_REQUEUED, h.getStoredStatus());
            assertEquals(Task.STATUS_REQUEUED, h.getStatus(check), "the stored status wins over the HTTP-error log");
            assertNull(h.getCompletionDateInMilliseconds());
        } finally {
            check.rollbackAndClose();
        }
        assertEquals("sent", summary(itask.getId()).getString("identificationStatus"));

        // the retry budget is exhausted on the next attempt -> terminal SEND_FAILED
        Shepherd consumer2 = open("consumer2");
        try {
            withIdentifyStubs(failed, inv -> false, () -> {
                IAGateway._doIdentify(jin, new JSONObject().put("taskId", hsId), consumer2, CTX, "http://wb", false);
            });
            consumer2.commitDBTransaction();
        } finally {
            consumer2.closeDBTransaction();
        }
        Shepherd check2 = open("check2");
        try {
            Task h = Task.load(hsId, check2);
            assertEquals("error", h.getStoredStatus());
            assertEquals("SEND_FAILED", errorCode(h));
            assertNotNull(h.getCompletionDateInMilliseconds());
        } finally {
            check2.rollbackAndClose();
        }
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberError"));
    }

    /** One image, two encounters, one of which has no default option. */
    @Test void mixedNoWorkGroupsDoNotFailTheDetectionTask() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY);
        Encounter e2 = guitarfish(sh, "mystery");   // no _id_conf for this class
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1, e2);
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), matchConfig()).getKind());

        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("completed", dd.getStoredStatus(), "D is not INVALID_MATCH_CONFIG");
            Task u2 = umbrellaFor(dd, e2);
            assertEquals("error", u2.getStoredStatus());
            assertEquals("INVALID_MATCH_CONFIG", errorCode(u2));
            assertTrue(IA.intakeProducedNoWork(u2));
            assertEquals(2, childrenOf(umbrellaFor(dd, e1)).size());
        } finally {
            check.rollbackAndClose();
        }
        for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberError"), "the no-option encounter's annotation reads error");
    }

    /** An encounter with no genus/species makes identOpts throw; only its group is skipped. */
    @Test void encounterWithoutTaxonomyIsANoWorkGroupAndDoesNotAbortTheOthers() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter bad = encounter(sh, null, null, BODY);   // first group: identOpts -> taxonomyKey(null)
        Encounter good = guitarfish(sh, BODY);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), bad, good);
        List<String> ids = annotationIds(bad, good);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        // the processor's match config completes the vector option
        JSONObject mc = matchConfig().put("api_endpoint", "http://ml/match");
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), mc).getKind());

        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("completed", dd.getStoredStatus(), "D is not INVALID_MATCH_CONFIG: not every group is noWork");
            Task ub = umbrellaFor(dd, bad);
            assertEquals("error", ub.getStoredStatus());
            assertEquals("INVALID_MATCH_CONFIG", errorCode(ub));
            assertTrue(IA.intakeProducedNoWork(ub));
            Task ug = umbrellaFor(dd, good);
            assertEquals(2, childrenOf(ug).size(), "the other encounter's run is built normally");
            Task vopt = optionOf(ug, true, BODY);
            assertEquals("completed", vopt.getStoredStatus());
            assertEquals("http://ml/match", vopt.getParameters().getJSONObject("ibeis.identification")
                .getString("api_endpoint"), "vector defaults come from runMatchProspects' matchConfig");
        } finally {
            check.rollbackAndClose();
        }
        for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
    }

    @Test void allGroupsWithoutOptionsMarkTheDetectionTaskInvalid() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, "mystery");
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        MlServiceJobOutcome out = processor().runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("INVALID_MATCH_CONFIG", out.getCode());
        Shepherd check = open("check");
        try {
            assertEquals("INVALID_MATCH_CONFIG", errorCode(Task.load(d.getId(), check)));
        } finally {
            check.rollbackAndClose();
        }
    }

    @Test void handleRestWithAnUmbrellaAsRootCreatesAFreshRootThatKeepsTheRequestParameters() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY);
        JSONObject requestParams = new JSONObject().put("matchingSetFilter", new JSONObject().put("locationIds", new JSONArray().put("X")));
        IA.IntakeOutcome first = IA.intakeAnnotationsUnder(sh, new ArrayList<Annotation>(e1.getAnnotations()), null, requestParams, false);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        String oldUmbrella = first.umbrella.getId();
        String annId = annotationIds(e1).get(0);

        JSONObject jin = new JSONObject().put("__context", CTX).put("taskId", oldUmbrella)
            .put("annotationIds", new JSONArray().put(annId));
        IA.handleRest(jin);

        Shepherd check = open("check");
        try {
            Task old = Task.load(oldUmbrella, check);
            assertEquals(2, childrenOf(old).size(), "the old run gained nothing");
            Annotation ann = check.getAnnotation(annId);
            Task newUmbrella = null;
            for (Task t : Task.getTasksFor(ann, check)) {
                if (Task.isUmbrella(t) && !t.getId().equals(oldUmbrella)) newUmbrella = t;
            }
            assertNotNull(newUmbrella, "a second run exists");
            Task freshRoot = newUmbrella.getParent();
            assertNotNull(freshRoot);
            assertFalse(freshRoot.getId().equals(oldUmbrella));
            assertNull(Task.runIdOf(freshRoot));
            assertEquals(0, freshRoot.numberAnnotations());
            assertTrue(freshRoot.getParameters().has("matchingSetFilter"), "the request parameters moved to the fresh root");
            assertTrue(newUmbrella.getParameters().has("matchingSetFilter"), "and the new run inherited them");
            // the old umbrella's page renders only the old run
            Set<String> newRunIds = new HashSet<String>();
            newRunIds.add(newUmbrella.getId());
            for (Task opt : childrenOf(newUmbrella)) {
                newRunIds.add(opt.getId());
                for (Task leaf : childrenOf(opt)) newRunIds.add(leaf.getId());
            }
            Set<String> oldLeafIds = new HashSet<String>();
            for (Task opt : childrenOf(old)) for (Task leaf : childrenOf(opt)) oldLeafIds.add(leaf.getId());
            assertEquals(1, oldLeafIds.size(), "the old run has its one vector leaf");
            JSONArray oldOptions = Task.load(oldUmbrella, check).matchResultsJson(10, null, check).getJSONArray("children");
            Set<String> rendered = new HashSet<String>();
            for (int i = 0; i < oldOptions.length(); i++) {
                rendered.add(oldOptions.getJSONObject(i).getString("id"));
                JSONArray kids = oldOptions.getJSONObject(i).optJSONArray("children");
                if (kids == null) continue;
                for (int j = 0; j < kids.length(); j++) rendered.add(kids.getJSONObject(j).getString("id"));
            }
            assertTrue(rendered.containsAll(oldLeafIds), "the old run's leaves are rendered");
            for (String id : newRunIds) assertFalse(rendered.contains(id), "no task of the new run on the old page: " + id);
        } finally {
            check.rollbackAndClose();
        }
    }

    /** Partial vector dispatch within one option: V_A attached, V_B never created, H_B completed -> B reads error through the option. */
    @Test void partialVectorDispatchLeavesTheUnleafedAnnotationOnTheFailedOption() throws Exception {
        dispatcher.throwForIaClass = BODY;                 // attaches A's leaf, then throws
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY, BODY);         // one vector option holds A and B
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1);
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), matchConfig()).getKind());
        // the HotSpotter option fanned out and completed for B (H_B, no option marker), so B's only
        // error can come from the vector option that never created V_B
        String hsId = hotspotterOptionIds(d.getId()).get(0);
        Shepherd fan = open("fanout");
        try {
            Task hs = Task.load(hsId, fan);
            Task hB = new Task(hs);
            hB.setParameters(Task.withoutOptionMarker(hs.getParameters()));
            hB.addObject(fan.getAnnotation(ids.get(1)));
            hB.setStatus("completed");
            hB.setCompletionDateInMilliseconds();
            fan.getPM().makePersistent(hB);
            Task hA = new Task(hs);
            hA.setParameters(Task.withoutOptionMarker(hs.getParameters()));
            hA.addObject(fan.getAnnotation(ids.get(0)));
            hA.setStatus("completed");
            hA.setCompletionDateInMilliseconds();
            fan.getPM().makePersistent(hA);
            fan.commitDBTransaction();
        } finally {
            fan.closeDBTransaction();
        }

        Shepherd check = open("check");
        try {
            Task u = umbrellaFor(Task.load(d.getId(), check), e1);
            Task v = optionOf(u, true, BODY);
            assertEquals("DISPATCH_FAILED", errorCode(v));
            assertEquals(1, childrenOf(v).size(), "only A's leaf exists");
            assertTrue(childrenOf(v).get(0).holdsAnnotationId(ids.get(0)));
            assertEquals("error", childrenOf(v).get(0).getStoredStatus());
        } finally {
            check.rollbackAndClose();
        }
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(2, sum.getInt("identificationNumberError"), "A through its leaf, B through the failed option (H_B is complete)");
    }

    /** Single-HotSpotter U(A,B) mid fan-out (H_A exists) beside a newer completed run for A -> the import stays sent (B's representative is U). */
    @Test void singleHotspotterUmbrellaMidFanOutNextToANewerCompletedRunStaysSent() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = encounter(sh, "Rhina", "ancylostoma", BODY, BODY);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1);
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        assertEquals(MlServiceJobOutcome.Kind.OK, processor().runMatchProspects(ids, d.getId(), matchConfig()).getKind());
        String uId = hotspotterOptionIds(d.getId()).get(0);   // the umbrella IS the single option

        Shepherd more = open("more");
        try {
            Task u = Task.load(uId, more);
            JSONObject params = u.getParameters();           // as _doIdentify: snapshot, then strip the option
            u.setParameters(Task.identityParams(params));
            Task hA = new Task(u);                           // _doIdentify fanned A out, not B yet
            hA.setParameters(Task.withoutOptionMarker(params));
            hA.addObject(more.getAnnotation(ids.get(0)));
            hA.setStatus("completed");
            more.getPM().makePersistent(hA);
            // a newer run for A only, already complete
            Task dd = Task.load(d.getId(), more);
            IA.IntakeOutcome newer = IA.intakeAnnotationsUnder(more,
                new ArrayList<Annotation>(Arrays.asList(more.getAnnotation(ids.get(0)))), dd,
                MlServiceProcessor.matchIntakeParams(dd.getParameters()), false);
            newer.umbrella.setStatus("completed");
            more.commitDBTransaction();
        } finally {
            more.closeDBTransaction();
        }
        JSONObject sum = summary(itask.getId());
        assertEquals("sent", sum.getString("identificationStatus"));
        assertEquals(1, sum.getInt("identificationNumberComplete"), "A is complete via the newer run; B waits on U");
    }

    /** Old trees (no run ids), reloaded: the five-level climb, the pre-fix reused root, and the scoped foreign-only root fallback. */
    @Test void oldTreeShapesSelectThroughAttachedObjectsAfterReload() throws Exception {
        Shepherd sh = open("fixture");
        Encounter enc = guitarfish(sh, BODY, ROSTRUM);
        List<String> ids = annotationIds(enc);
        Annotation body = sh.getAnnotation(ids.get(0));
        Annotation rostrum = sh.getAnnotation(ids.get(1));
        Set<String> encSet = idSet(enc);
        long t = 1000L;

        // (i) five-level Mantamatcher resend shape: R -> subParent(IMP-old) -> U -> {T_vec -> SA, T_hs} x 2
        Task r = new Task();
        r.setCreated(t++);
        Task subParent = new Task(r);
        subParent.setParameters(new JSONObject().put("importTaskId", "IMP-old").put("matchingSetFilter", new JSONObject()));
        subParent.setCreated(t++);
        Task u = new Task(subParent);
        u.addObject(body);
        u.addObject(rostrum);
        u.setCreated(t++);
        Task tVecBody = new Task(u);
        tVecBody.addParameter("ibeis.identification", new JSONObject("{\"method\":\"miewid\"}"));
        tVecBody.addObject(body);
        tVecBody.setCreated(t++);
        Task tHsBody = new Task(u);
        tHsBody.addParameter("ibeis.identification", new JSONObject("{\"query_config_dict\":{\"sv_on\":true}}"));
        tHsBody.addObject(body);
        tHsBody.setCreated(t++);
        Task tVecRostrum = new Task(u);
        tVecRostrum.addParameter("ibeis.identification", new JSONObject("{\"method\":\"miewid\"}"));
        tVecRostrum.addObject(rostrum);
        tVecRostrum.setCreated(t++);
        Task tHsRostrum = new Task(u);
        tHsRostrum.addParameter("ibeis.identification", new JSONObject("{\"query_config_dict\":{\"sv_on\":true}}"));
        tHsRostrum.addObject(rostrum);
        tHsRostrum.setCreated(t++);
        Task saBody = new Task(tVecBody);
        saBody.addObject(body);
        saBody.setCreated(t++);
        Task saRostrum = new Task(tVecRostrum);
        saRostrum.addObject(rostrum);
        saRostrum.setCreated(t++);
        sh.getPM().makePersistent(r);

        // (ii) pre-fix reused root: R2(media) -> U1(A, media) -> H1, H2, U2(A) -> H3, H4
        Encounter enc2 = guitarfish(sh, BODY);
        Annotation a = sh.getAnnotation(annotationIds(enc2).get(0));
        MediaAsset ma = a.getMediaAsset();
        Task r2 = new Task();
        r2.addObject(ma);
        r2.setCreated(t++);
        Task u1 = new Task(r2);
        u1.addObject(a);
        u1.addObject(ma);
        u1.setCreated(t++);
        Task h1 = new Task(u1);
        h1.addParameter("ibeis.identification", new JSONObject());
        h1.addObject(a);
        h1.setCreated(t++);
        Task h2 = new Task(u1);
        h2.addParameter("ibeis.identification", new JSONObject());
        h2.addObject(a);
        h2.setCreated(t++);
        Task u2 = new Task(u1);
        u2.addObject(a);
        u2.setCreated(t++);
        Task h3 = new Task(u2);
        h3.addParameter("ibeis.identification", new JSONObject());
        h3.addObject(a);
        h3.setCreated(t++);
        Task h4 = new Task(u2);
        h4.addParameter("ibeis.identification", new JSONObject());
        h4.addObject(a);
        h4.setCreated(t++);
        sh.getPM().makePersistent(r2);

        // (iii) scoped foreign-only: R3(B, no params) -> F(B, IMP-foreign, ident)
        Encounter enc3 = guitarfish(sh, BODY);
        Annotation b = sh.getAnnotation(annotationIds(enc3).get(0));
        Task r3 = new Task();
        r3.addObject(b);
        r3.setCreated(t++);
        Task f = new Task(r3);
        f.addParameter("importTaskId", "IMP-foreign");
        f.addParameter("ibeis.identification", new JSONObject());
        f.addObject(b);
        f.setCreated(t++);
        sh.getPM().makePersistent(r3);
        // each shape belongs to an import, so the bulk page's encounterTaskInfo can be asserted
        importWith(sh, "IMP-old", r, enc);
        importWith(sh, "IMP-reused", r2, enc2);
        importWith(sh, "IMP-mine", r3, enc3);
        sh.commitDBTransaction();
        sh.closeDBTransaction();

        Shepherd check = open("check");
        try {
            Annotation bodyR = check.getAnnotation(ids.get(0));
            Annotation rostrumR = check.getAnnotation(ids.get(1));
            assertEquals(u.getId(), Task.getPreferredMatchResultsTaskForAnnotation(bodyR, "IMP-old", encSet, check).getId(),
                "the climb reaches U through attached parents");
            assertEquals(u.getId(), Task.getPreferredMatchResultsTaskForAnnotation(rostrumR, "IMP-old", encSet, check).getId());
            assertEquals(saBody.getId(), Task.getPreferredMatchResultsTaskForAnnotation(bodyR, "IMP-old", null, check).getId(),
                "today's behaviour without the encounter set");
            Annotation aR = check.getAnnotation(a.getId());
            assertEquals(u2.getId(), Task.getPreferredMatchResultsTaskForAnnotation(aR, null, idSet(enc2), check).getId(),
                "the inner umbrella; U1's media assets stop the climb");
            Annotation bR = check.getAnnotation(b.getId());
            assertEquals(r3.getId(), Task.getPreferredMatchResultsTaskForAnnotation(bR, "IMP-mine", idSet(enc3), check).getId(),
                "foreign identification tasks fall through to the root fallback");
        } finally {
            check.rollbackAndClose();
        }
        // the same landings through statsAnnotations -> iaSummaryJson (what the bulk-import page renders)
        JSONArray oldInfo = summary("IMP-old").getJSONObject("statsAnnotations").getJSONObject("encounterTaskInfo").getJSONArray(enc.getCatalogNumber());
        assertEquals(1, oldInfo.length(), "both annotations share the umbrella");
        assertEquals(u.getId(), oldInfo.getJSONArray(0).getString(0));
        assertEquals(BODY + ", " + ROSTRUM, oldInfo.getJSONArray(0).getString(2));
        JSONArray reusedInfo = summary("IMP-reused").getJSONObject("statsAnnotations").getJSONObject("encounterTaskInfo").getJSONArray(enc2.getCatalogNumber());
        assertEquals(u2.getId(), reusedInfo.getJSONArray(0).getString(0));
        JSONArray mineInfo = summary("IMP-mine").getJSONObject("statsAnnotations").getJSONObject("encounterTaskInfo").getJSONArray(enc3.getCatalogNumber());
        assertEquals(r3.getId(), mineInfo.getJSONArray(0).getString(0));
    }

    /** The run is durable but the Shepherd cannot restart its transaction: the caller's recovery marks every option and the umbrella. */
    @Test void transactionRestartFailureAfterThePersistCommitIsRecovered() throws Exception {
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        MlServiceJobOutcome out = processorFailingRestart(1).runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode());
        assertEquals(0, dispatcher.vectorCalls, "no vector dispatch was entered");
        assertTrue(dispatcher.published.isEmpty(), "nothing was dispatched");
        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("MATCH_PERSIST", errorCode(dd));
            assertEquals(1, childrenOf(dd).size(), "the umbrella is durable");
            Task u = childrenOf(dd).get(0);
            assertEquals("MATCH_PERSIST", errorCode(u));
            assertEquals(4, childrenOf(u).size());
            for (Task opt : childrenOf(u)) {
                assertEquals("error", opt.getStoredStatus());
                assertEquals("MATCH_PERSIST", errorCode(opt), "no option's job was published, so all are marked");
                assertEquals(0, childrenOf(opt).size());
            }
        } finally {
            check.rollbackAndClose();
        }
    }

    /** §5.2 registration through a REAL identify and the REAL forced path: pending query + pending target registered under their acmIds (request uuids asserted), flags persisted, asset acmId minted, pruned targets sent. */


    /** A pending query WBIA does not confirm makes the real identify return success=false (flag stays FALSE); an ineligible pending target is dropped. */


    /** Registration, persisted: a pending annotation WBIA already holds under its acmId is flagged TRUE without any POST. */


    /** Registration, persisted: a null-flag (legacy) annotation takes today's non-forced path — WBIA mints its uuid and acmId is rewritten — and its flag stays null. */


    /** Registration, persisted: the missing-annotation repair during identify forces a pending annotation under its acmId and asks for a retry; a second report never loops. */


    /** The transaction is rolled back mid-dispatch (the processor's managers refuse nontransactional writes): the intake core stops dispatching, nothing is published, and the caller's recovery marks every option and the umbrella. */
    @Test void dispatchThatRollsBackTheTransactionIsRecovered() throws Exception {
        dispatcher.killTransactionOnVector = true;
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        MlServiceJobOutcome out = strictProcessor().runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode(), "the group commit finds no active transaction");
        assertEquals(1, dispatcher.vectorCalls, "only the dispatch that killed the transaction ran");
        assertTrue(dispatcher.published.isEmpty(), "no publication followed the dead transaction");
        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("MATCH_PERSIST", errorCode(dd));
            Task u = childrenOf(dd).get(0);
            assertEquals("MATCH_PERSIST", errorCode(u));
            assertEquals(4, childrenOf(u).size());
            for (Task opt : childrenOf(u)) {
                assertEquals("MATCH_PERSIST", errorCode(opt), "nothing was published, so every option is marked");
                // new Task(option) links the leaf into option.children before the field write
                // throws; DataNucleus performs that collection add on the (now nontransactional)
                // option atomically, so ONE empty child row can survive on the option whose
                // dispatch killed the transaction. No usable leaf can: none holds an annotation
                // and none completed.
                assertTrue(childrenOf(opt).size() <= 1, "at most one orphan child per option");
                for (Task leaf : childrenOf(opt)) {
                    assertEquals(0, leaf.numberAnnotations(), "no leaf for an annotation is durable");
                    assertFalse("completed".equals(leaf.getStoredStatus()));
                }
            }
        } finally {
            check.rollbackAndClose();
        }
    }

    /** The PersistenceManager is closed mid-dispatch (lazy reads and writes fail): the populated outcome still comes back and recovery marks what was not published. */
    @Test void dispatchThatClosesThePersistenceManagerIsRecovered() throws Exception {
        dispatcher.closePersistenceManagerOnVector = true;
        Shepherd sh = open("fixture");
        Encounter e1 = guitarfish(sh, BODY, ROSTRUM);
        Task d = detectionTask(sh, nextImportId());
        List<String> ids = annotationIds(e1);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        MlServiceJobOutcome out = strictProcessor().runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", out.getCode(), "the checked commit cannot succeed on a closed manager");
        assertEquals(1, dispatcher.vectorCalls, "the intake core stops after the dispatch that closed the manager");
        assertTrue(dispatcher.published.isEmpty(), "nothing is published on a closed manager");
        Set<String> publishedOptionIds = new HashSet<String>();
        for (JSONObject job : dispatcher.published) publishedOptionIds.add(job.getString("taskId"));
        Shepherd check = open("check");
        try {
            Task dd = Task.load(d.getId(), check);
            assertEquals("MATCH_PERSIST", errorCode(dd));
            Task u = childrenOf(dd).get(0);
            assertEquals("MATCH_PERSIST", errorCode(u));
            assertEquals(4, childrenOf(u).size());
            for (Task opt : childrenOf(u)) {
                if (opt.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) {
                    assertEquals("MATCH_PERSIST", errorCode(opt), "a vector dispatch on a closed manager cannot have produced durable leaves");
                    assertEquals(0, childrenOf(opt).size());
                } else if (publishedOptionIds.contains(opt.getId())) {
                    assertNull(opt.getStoredStatus(), "a published HotSpotter option is left to its job");
                } else {
                    assertEquals("MATCH_PERSIST", errorCode(opt), "an unpublished HotSpotter option is marked");
                }
            }
        } finally {
            check.rollbackAndClose();
        }
    }

    // ---- lookups ----

    static Task umbrellaFor(Task d, Encounter enc) {
        String firstAnn = annotationIds(enc).get(0);
        for (Task u : childrenOf(d)) {
            if (u.holdsAnnotationId(firstAnn)) return u;
        }
        return null;
    }

    static List<String> hotspotterOptionIds(String dId) {
        List<String> ids = new ArrayList<String>();
        Shepherd sh = open("hsOptions");
        try {
            for (Task u : childrenOf(Task.load(dId, sh))) {
                if (Task.isOptionTask(u) && !u.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) ids.add(u.getId());
                for (Task opt : childrenOf(u)) {
                    if (Task.isOptionTask(opt) && !opt.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) ids.add(opt.getId());
                }
            }
        } finally {
            sh.rollbackAndClose();
        }
        return ids;
    }

    // the vector leaf (the option's representative) for the encounter's first annotation
    static String vectorLeafId(String dId, Encounter enc) {
        String firstAnn = annotationIds(enc).get(0);
        Shepherd sh = open("vectorLeaf");
        try {
            Task u = umbrellaFor(Task.load(dId, sh), enc);
            for (Task opt : childrenOf(u)) {
                if (!opt.getParameters().optBoolean(Task.PARAM_V2_MATCH, false)) continue;
                for (Task leaf : childrenOf(opt)) {
                    if (leaf.holdsAnnotationId(firstAnn)) return leaf.getId();
                }
            }
            throw new IllegalStateException("no vector leaf for " + firstAnn);
        } finally {
            sh.rollbackAndClose();
        }
    }

    static JSONObject summary(String importId) {
        Shepherd sh = open("summary");
        try {
            ImportTask itask = sh.getImportTask(importId);
            return itask.iaSummaryJson(sh);
        } finally {
            sh.rollbackAndClose();
        }
    }
    static JSONObject iaJson() {
        JSONArray both = new JSONArray()
            .put(new JSONObject("{\"pipeline_root\":\"vector\",\"method\":\"miewid\",\"version\":\"msv4_v3\",\"default\":true,\"description\":\"MiewID\"}"))
            .put(new JSONObject("{\"query_config_dict\":{\"sv_on\":true},\"default\":true,\"description\":\"HotSpotter\"}"));
        JSONArray hsOnly = new JSONArray()
            .put(new JSONObject("{\"query_config_dict\":{\"sv_on\":true},\"default\":true,\"description\":\"HotSpotter\"}"));
        JSONObject lentiginosus = new JSONObject()
            .put(BODY, new JSONObject().put("_id_conf", both))
            .put(ROSTRUM, new JSONObject().put("_id_conf", new JSONArray(both.toString())));
        JSONObject ancylostoma = new JSONObject().put(BODY, new JSONObject().put("_id_conf", hsOnly));
        // Canis familiaris: multi-pipeline (body + face ml-services); dog has MiewID + HotSpotter
        JSONObject canis = MultiPipelineIAJson.dogBodyAndFace().getJSONObject("Canis");
        canis.getJSONObject("familiaris").getJSONObject("dog").put("_id_conf", new JSONArray(both.toString()));
        return new JSONObject()
            .put("Pseudobatos", new JSONObject().put("lentiginosus", lentiginosus))
            .put("Rhina", new JSONObject().put("ancylostoma", ancylostoma))
            .put("Canis", canis);
    }
    @Test void laterGroupsAfterAGroupCommitFailureGetTerminalTasks() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY);
        Encounter e2 = guitarfish(sh, BODY);
        Encounter e3 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1, e2, e3);
        List<String> ids = annotationIds(e1, e2, e3);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        MlServiceJobOutcome outcome = processorFailingCommit(2).runMatchProspects(ids, d.getId(), matchConfig());
        assertEquals("MATCH_PERSIST", outcome.getCode());
        Shepherd check = open("remainingGroups");
        try {
            Task parent = Task.load(d.getId(), check);
            assertEquals(3, childrenOf(parent).size());
            for (Encounter e : Arrays.asList(e2, e3)) {
                Task failed = umbrellaFor(parent, e);
                assertNotNull(failed);
                assertEquals("MATCH_PERSIST", errorCode(failed));
                assertTrue(Task.isUmbrella(failed));
                assertEquals(parent.getId(), failed.getParent().getId());
            }
        } finally {
            check.rollbackAndClose();
        }
        assertEquals(1, dispatcher.published.size(), "only the first group reached dispatch");
        for (String id : hotspotterOptionIds(d.getId())) setStatus(id, "completed");
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(3, sum.getInt("identificationNumberError"));
    }

    @Test void failureBeforeTheFirstGroupMarksEveryAnnotation() throws Exception {
        Shepherd sh = open("fixture");
        String importId = nextImportId();
        Encounter e1 = guitarfish(sh, BODY);
        Encounter e2 = guitarfish(sh, BODY);
        Task d = detectionTask(sh, importId);
        ImportTask itask = importWith(sh, importId, d.getParent(), e1, e2);
        List<String> ids = annotationIds(e1, e2);
        sh.commitDBTransaction();
        sh.closeDBTransaction();
        MatchVisibilityGate gate = (anns, task, cfg, attempt, first) ->
            MatchVisibilityGate.GateOutcome.ready(attempt, System.currentTimeMillis());
        MlServiceProcessor broken = new MlServiceProcessor(CTX, new MlServiceClient(), gate, payload -> true) {
            @Override Shepherd openShepherd(String action) {
                if (!"runMatchProspects".equals(action)) return super.openShepherd(action);
                return new Shepherd(CTX) {
                    @Override public Annotation getAnnotation(String id) {
                        throw new IllegalStateException("cannot load input annotations");
                    }
                };
            }
        };
        assertEquals("MATCH", broken.runMatchProspects(ids, d.getId(), matchConfig()).getCode());
        assertTrue(dispatcher.published.isEmpty());
        JSONObject sum = summary(itask.getId());
        assertEquals("complete", sum.getString("identificationStatus"));
        assertEquals(2, sum.getInt("identificationNumberError"));
    }

    @Test void nullMatchConfigDoesNotPublishAnIncompleteVectorOptionToWbia() throws Exception {
        JSONObject defaults = iaJson();
        JSONObject vector = defaults.getJSONObject("Pseudobatos").getJSONObject("lentiginosus")
            .getJSONObject(BODY).getJSONArray("_id_conf").getJSONObject(0);
        vector.remove("method");
        vector.remove("version");
        IAJsonProperties config = IAJsonProperties.iaConfig();
        config.setJson(defaults);
        try {
            Shepherd sh = open("nullConfigFixture");
            String importId = nextImportId();
            Encounter encounter = guitarfish(sh, BODY);
            Task detection = detectionTask(sh, importId);
            ImportTask imported = importWith(sh, importId, detection.getParent(), encounter);
            List<String> ids = annotationIds(encounter);
            sh.commitDBTransaction();
            sh.closeDBTransaction();
            assertEquals(MlServiceJobOutcome.Kind.OK,
                processor().runMatchProspects(ids, detection.getId(), null).getKind());
            assertEquals(1, dispatcher.published.size(), "only the real HotSpotter option is queued");
            assertEquals(0, dispatcher.vectorCalls);
            Shepherd reader = open("nullConfigCheck");
            try {
                Task run = umbrellaFor(Task.load(detection.getId(), reader), encounter);
                Task invalid = null;
                for (Task option : childrenOf(run)) {
                    JSONObject algorithm = option.getParameters().optJSONObject("ibeis.identification");
                    if ((algorithm != null) && "vector".equals(algorithm.optString("pipeline_root")))
                        invalid = option;
                }
                assertNotNull(invalid, "the explicitly vector option is persisted even though unusable");
                assertEquals("INVALID_MATCH_CONFIG", errorCode(invalid));
                assertEquals("error", invalid.getStoredStatus());
            } finally {
                reader.rollbackAndClose();
            }
            for (JSONObject job : dispatcher.published) setStatus(job.getString("taskId"), "completed");
            JSONObject sum = summary(imported.getId());
            assertEquals("complete", sum.getString("identificationStatus"));
            assertEquals(1, sum.getInt("identificationNumberError"));
        } finally {
            config.setJson(iaJson());
        }
    }

}
