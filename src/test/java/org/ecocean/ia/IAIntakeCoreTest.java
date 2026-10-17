package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jdo.PersistenceManager;

import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.IAJsonProperties;
import org.ecocean.servlet.IAGateway;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

/**
 * Spec §4.2: the intake core builds, persists (checked commit), then dispatches through the
 * Dispatcher seam. No OpenSearch, ml-service or file queue is touched.
 */
class IAIntakeCoreTest {

    /** Records dispatches; builds vector leaves the way Embedding.findMatchProspects does. */
    static class RecordingDispatcher implements IA.Dispatcher {   // not final: one test subclasses it
        final List<Task> vectorRuns = new ArrayList<Task>();
        final List<JSONObject> vectorOpts = new ArrayList<JSONObject>();
        final List<JSONObject> published = new ArrayList<JSONObject>();
        final List<Boolean> publishedFastlane = new ArrayList<Boolean>();
        List<String> events = null;
        boolean publishResult = true;
        boolean publishThrows = false;
        String vectorThrowsForIaClass = null;
        boolean attachLeafBeforeThrow = false;

        @Override public void runVector(JSONObject opt, Task optionTask, Shepherd shep) {
            vectorRuns.add(optionTask);
            vectorOpts.add(opt);
            if (events != null) events.add("runVector");
            String iaClass = optionTask.getObjectAnnotations().get(0).getIAClass();
            if (iaClass.equals(vectorThrowsForIaClass)) {
                if (attachLeafBeforeThrow) {
                    Task leaf = new Task(optionTask);
                    leaf.addObject(optionTask.getObjectAnnotations().get(0));
                }
                throw new IllegalStateException("vector dispatch failed for " + iaClass);
            }
            for (Annotation a : optionTask.getObjectAnnotations()) {
                Task leaf = new Task(optionTask);
                leaf.addObject(a);
                leaf.setStatus("completed");
            }
            optionTask.setStatus("completed");
        }

        @Override public boolean publish(String context, String job, boolean fastlane)
        throws java.io.IOException {
            if (publishThrows) throw new java.io.IOException("queue unwritable");
            published.add(new JSONObject(job));
            publishedFastlane.add(fastlane);
            if (events != null) events.add("publish");
            return publishResult;
        }
    }

    @AfterEach void resetDispatcher() {
        IA.setDispatcherForTests(null);
    }

    static Shepherd shepherd(boolean commitOk) {
        PersistenceManager pm = mock(PersistenceManager.class);
        Shepherd sh = mock(Shepherd.class);
        when(sh.getPM()).thenReturn(pm);
        when(sh.getContext()).thenReturn("context0");
        when(sh.commitDBTransactionWithStatus()).thenReturn(commitOk);
        when(sh.isDBTransactionActive()).thenReturn(true);
        return sh;
    }

    static Annotation ann(String id, String iaClass) {
        Annotation a = new Annotation();
        a.setId(id);
        a.setIAClass(iaClass);
        return a;
    }

    static JSONObject hotspotter() {
        return new JSONObject(
            "{\"query_config_dict\":{\"sv_on\":true},\"default\":true,\"description\":\"HotSpotter\"}");
    }

    static JSONObject miewid() {
        return new JSONObject(
            "{\"pipeline_root\":\"vector\",\"method\":\"miewid\",\"version\":\"msv4_v3\",\"default\":true,\"description\":\"MiewID\"}");
    }

    /** identOpts answered per annotation iaClass (fresh copies each call, as the real one does). */
    static IAJsonProperties config(final Map<String, List<JSONObject>> optsByClass) {
        IAJsonProperties cfg = mock(IAJsonProperties.class);
        when(cfg.identOpts(any(Shepherd.class), any(Annotation.class))).thenAnswer(inv -> {
            Annotation a = inv.getArgument(1);
            List<JSONObject> src = optsByClass.get(a.getIAClass());
            if (src == null) return null;
            List<JSONObject> copy = new ArrayList<JSONObject>();
            for (JSONObject o : src) copy.add(new JSONObject(o.toString()));
            return copy;
        });
        return cfg;
    }

    static Map<String, List<JSONObject>> twoClassesTwoOptions() {
        Map<String, List<JSONObject>> m = new HashMap<String, List<JSONObject>>();
        m.put("guitarfish", Arrays.asList(miewid(), hotspotter()));
        m.put("guitarfish+rostrum", Arrays.asList(miewid(), hotspotter()));
        return m;
    }

    interface Body { void run() throws Exception; }

    static void withStatics(IAJsonProperties cfg, Body body) throws Exception {
        try (MockedStatic<CommonConfiguration> cc = mockStatic(CommonConfiguration.class);
             MockedStatic<IAJsonProperties> jp = mockStatic(IAJsonProperties.class)) {
            cc.when(() -> CommonConfiguration.getServerURL(anyString())).thenReturn("http://wb");
            jp.when(IAJsonProperties::iaConfig).thenReturn(cfg);
            body.run();
        }
    }

    static String errorCode(Task t) {
        JSONObject sd = t.getStatusDetails();
        if ((sd == null) || (sd.optJSONArray("errors") == null)) return null;
        JSONArray errs = sd.getJSONArray("errors");
        return errs.getJSONObject(errs.length() - 1).optString("code", null);
    }

    @Test void twoClassesTwoOptionsBuildOneOptionTaskPerClassAndOption() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Annotation body = ann("body", "guitarfish");
        Annotation rostrum = ann("rostrum", "guitarfish+rostrum");
        withStatics(config(twoClassesTwoOptions()), () -> {
            JSONObject inherited = new JSONObject().put("importTaskId", "IMP1")
                .put("matchingSetFilter", new JSONObject().put("locationIds", new JSONArray().put("X")));
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(body, rostrum), null, inherited, false);

            Task u = o.umbrella;
            assertNotNull(u);
            assertTrue(Task.isUmbrella(u));
            assertFalse(Task.isOptionTask(u));
            assertFalse(u.getParameters().has(Task.PARAM_V2_MATCH));
            assertEquals("IMP1", u.getParameters().getString("importTaskId"));
            assertEquals(2, u.numberAnnotations());
            assertFalse(o.noWork);
            assertFalse(o.persistFailed);
            assertEquals(4, o.options.size());
            assertEquals(4, u.getChildren().size());
            int vectors = 0;
            for (IA.OptionResult r : o.options) {
                Task t = r.task;
                assertEquals(u.getId(), Task.runIdOf(t));
                assertTrue(Task.isOptionTask(t));
                assertFalse(Task.isUmbrella(t));
                assertEquals("IMP1", t.getParameters().getString("importTaskId"));
                assertTrue(t.getParameters().has("matchingSetFilter"));
                assertEquals(1, t.numberAnnotations(), "one class per option task");
                JSONObject ident = t.getParameters().getJSONObject("ibeis.identification");
                if (r.vector) {
                    vectors++;
                    assertEquals("miewid", ident.getString("method"));
                    assertTrue(t.getParameters().getBoolean(Task.PARAM_V2_MATCH));
                } else {
                    assertTrue(ident.has("query_config_dict"));
                    assertFalse(t.getParameters().has(Task.PARAM_V2_MATCH), "HotSpotter never carries the v2 flag");
                }
                assertTrue(r.published);
                assertNull(r.failureCode);
            }
            assertEquals(2, vectors);
            assertEquals(2, d.vectorRuns.size());
            assertEquals(2, d.published.size());
            for (JSONObject job : d.published) {
                String tid = job.getString("taskId");
                Task opt = null;
                for (IA.OptionResult r : o.options) if (r.task.getId().equals(tid)) opt = r.task;
                assertNotNull(opt, "published job names an option task");
                assertEquals(opt.getObjectAnnotations().get(0).getId(),
                    job.getJSONObject("identify").getJSONArray("annotationIds").getString(0));
                assertEquals("context0", job.getString("__context"));
                assertEquals(job.toString(), opt.getQueueResumeMessage());
            }
        });
    }

    @Test void inheritedRunIdAndMarkersAreReplaced() throws Exception {
        IA.setDispatcherForTests(new RecordingDispatcher());
        Shepherd sh = shepherd(true);
        Annotation body = ann("body", "guitarfish");
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(miewid(), hotspotter()));
        withStatics(config(one), () -> {
            JSONObject inherited = new JSONObject()
                .put(Task.PARAM_RUN_ID, "OLD-RUN").put(Task.PARAM_V2_MATCH, true)
                .put(Task.PARAM_OPTION, true).put("ibeis.identification", new JSONObject().put("x", 1))
                .put("importTaskId", "IMP1").put("skipIdent", false);
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(body), null, inherited, false);
            Task u = o.umbrella;
            assertEquals(u.getId(), Task.runIdOf(u));
            assertFalse(u.getParameters().has(Task.PARAM_OPTION));
            assertFalse(u.getParameters().has(Task.PARAM_V2_MATCH));
            assertFalse(u.getParameters().has("ibeis.identification"));
            assertEquals("IMP1", u.getParameters().getString("importTaskId"));
            assertFalse(u.getParameters().getBoolean("skipIdent"));
            for (IA.OptionResult r : o.options) {
                assertEquals(u.getId(), Task.runIdOf(r.task));
                assertFalse(r.task.getParameters().toString().contains("OLD-RUN"));
            }
            assertEquals("OLD-RUN", inherited.getString(Task.PARAM_RUN_ID), "the caller's object is not mutated");
        });
    }

    @Test void singleOptionSingleClassUsesTheUmbrellaAsTheOption() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(hotspotter()));
        withStatics(config(one), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertEquals(1, o.options.size());
            assertSame(o.umbrella, o.options.get(0).task);
            assertTrue(Task.isUmbrella(o.umbrella));
            assertTrue(Task.isOptionTask(o.umbrella));
            assertFalse(o.umbrella.hasChildren());
            assertTrue(o.umbrella.getParameters().has("ibeis.identification"));
            assertEquals(1, d.published.size());
        });
    }

    @Test void persistFailureDispatchesNothingAndOldOverloadReturnsNull() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(false);
        withStatics(config(twoClassesTwoOptions()), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("body", "guitarfish")), null, null, false);
            assertTrue(o.persistFailed);
            assertNotNull(o.umbrella);
            assertEquals(2, o.options.size(), "every option is listed even when nothing is durable");
            for (IA.OptionResult r : o.options) assertFalse(r.published);
            assertTrue(d.vectorRuns.isEmpty());
            assertTrue(d.published.isEmpty());
            assertNull(IA.intakeAnnotations(sh, Arrays.asList(ann("body", "guitarfish")), null, false));
        });
    }

    @Test void parentIsLinkedBeforeThePersistCommit() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        final List<String> events = new ArrayList<String>();
        d.events = events;
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        final Task parent = new Task();
        final Task[] umbrella = new Task[1];
        when(sh.getPM().makePersistent(any())).thenAnswer(inv -> {
            umbrella[0] = inv.getArgument(0);
            events.add("persist");
            return inv.getArgument(0);
        });
        when(sh.commitDBTransactionWithStatus()).thenAnswer(inv -> {
            assertSame(parent, umbrella[0].getParent(), "linked before the commit");
            assertTrue(parent.getChildren().contains(umbrella[0]));
            events.add("commit");
            return true;
        });
        withStatics(config(twoClassesTwoOptions()), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("body", "guitarfish")), parent, null, false);
            assertSame(parent, o.umbrella.getParent());
            assertEquals("persist", events.get(0));
            assertEquals("commit", events.get(1));
            assertEquals(4, events.size());
            for (int k = 2; k < events.size(); k++) {
                assertTrue(events.get(k).equals("runVector") || events.get(k).equals("publish"));
            }
            InOrder order = inOrder(sh);
            order.verify(sh).commitDBTransactionWithStatus();
            order.verify(sh).beginDBTransaction();
        });
    }

    @Test void publishFailureMarksQueueFailedAndKeepsTheVectorOption() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        d.publishResult = false;
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(miewid(), hotspotter()));
        withStatics(config(one), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            IA.OptionResult hs = o.options.get(1);
            assertFalse(hs.vector);
            assertFalse(hs.published);
            assertEquals("QUEUE_FAILED", hs.failureCode);
            assertEquals("error", hs.task.getStoredStatus());
            assertEquals("QUEUE_FAILED", errorCode(hs.task));
            assertNotNull(hs.task.getCompletionDateInMilliseconds());
            assertTrue(o.options.get(0).published);
        });
    }

    @Test void publishRuntimeExceptionMarksQueueFailedAndTheOutcomeStillComesBack() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher() {
            @Override public boolean publish(String context, String job, boolean fastlane) {
                throw new IllegalStateException("PersistenceManager is closed");   // a broken datastore, not an IOException
            }
        };
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(miewid(), hotspotter()));
        withStatics(config(one), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertEquals(2, o.options.size());
            assertTrue(o.options.get(0).published);
            assertEquals("QUEUE_FAILED", o.options.get(1).failureCode);
            assertEquals("QUEUE_FAILED", errorCode(o.options.get(1).task));
        });
    }

    @Test void publishExceptionMarksQueueFailed() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        d.publishThrows = true;
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(hotspotter()));
        withStatics(config(one), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertEquals("QUEUE_FAILED", o.options.get(0).failureCode);
            assertEquals("error", o.umbrella.getStoredStatus(), "single option: the umbrella is the option");
        });
    }

    @Test void vectorDispatchThrowTerminalizesAttachedLeavesAndContinues() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        d.vectorThrowsForIaClass = "guitarfish";
        d.attachLeafBeforeThrow = true;
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        withStatics(config(twoClassesTwoOptions()), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("body", "guitarfish"), ann("rostrum", "guitarfish+rostrum")), null, null, false);
            assertEquals(4, o.options.size(), "the outcome lists every option even after a throw");
            IA.OptionResult failed = null;
            int published = 0;
            for (IA.OptionResult r : o.options) {
                if ("DISPATCH_FAILED".equals(r.failureCode)) failed = r;
                if (r.published) published++;
            }
            assertNotNull(failed);
            assertTrue(failed.vector);
            assertEquals("error", failed.task.getStoredStatus());
            assertEquals("DISPATCH_FAILED", errorCode(failed.task));
            assertEquals(1, failed.task.getChildren().size());
            Task leaf = failed.task.getChildren().get(0);
            assertEquals("error", leaf.getStoredStatus(), "an attached, unfinished leaf is terminalized");
            assertEquals("DISPATCH_FAILED", errorCode(leaf));
            assertEquals(3, published, "the other three options were still dispatched");
            assertEquals(2, d.vectorRuns.size());
            assertEquals(2, d.published.size());
        });
    }

    /** Under non-transactional writes a transaction that dies inside runVector is noticed: no later option is dispatched. */
    @Test void transactionThatDiesDuringAVectorDispatchStopsEveryLaterDispatch() throws Exception {
        final boolean[] active = { true };
        RecordingDispatcher d = new RecordingDispatcher() {
            @Override public void runVector(JSONObject opt, Task optionTask, Shepherd shep) {
                super.runVector(opt, optionTask, shep);
                active[0] = false;   // the transaction died during the dispatch
            }
        };
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        when(sh.isDBTransactionActive()).thenAnswer(inv -> active[0]);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(miewid(), hotspotter()));
        withStatics(config(one), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertEquals(2, o.options.size());
            IA.OptionResult vec = o.options.get(0);
            assertTrue(vec.vector);
            assertFalse(vec.published, "a dispatch whose transaction died is not published");
            assertEquals("DISPATCH_FAILED", vec.failureCode);
            IA.OptionResult hs = o.options.get(1);
            assertFalse(hs.vector);
            assertFalse(hs.published);
            assertEquals("QUEUE_FAILED", hs.failureCode);
            assertEquals(1, d.vectorRuns.size());
            assertTrue(d.published.isEmpty(), "the HotSpotter job after the dead transaction is not published");
        });
    }

    @Test void noDefaultOptionsMarksTheUmbrellaInvalidAndDispatchesNothing() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> none = new HashMap<String, List<JSONObject>>();
        JSONObject off = hotspotter().put("default", false);
        none.put("guitarfish", Arrays.asList(off));
        withStatics(config(none), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertTrue(o.noWork);
            assertTrue(IA.intakeProducedNoWork(o.umbrella));
            assertEquals("error", o.umbrella.getStoredStatus());
            assertEquals("INVALID_MATCH_CONFIG", errorCode(o.umbrella));
            assertNotNull(o.umbrella.getCompletionDateInMilliseconds());
            assertEquals(Arrays.asList("guitarfish"), o.skippedIaClasses);
            assertTrue(o.options.isEmpty());
            assertTrue(d.published.isEmpty() && d.vectorRuns.isEmpty());
            verify(sh.getPM()).makePersistent(o.umbrella);
            verify(sh).commitDBTransactionWithStatus();
        });
    }

    @Test void classWithoutOptionsIsSkippedWhileOthersRun() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> m = new HashMap<String, List<JSONObject>>();
        m.put("guitarfish", Arrays.asList(miewid()));
        withStatics(config(m), () -> {   // "mystery" has no entry -> identOpts returns null
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("a", "guitarfish"), ann("b", "mystery")), null, null, false);
            assertFalse(o.noWork);
            assertEquals(Arrays.asList("mystery"), o.skippedIaClasses);
            assertEquals(1, o.options.size());
            assertEquals(2, o.umbrella.numberAnnotations(), "the umbrella still holds every annotation");
            assertNull(o.umbrella.getStoredStatus());
        });
    }

    /** A matchingAlgorithms override replaces the default list exactly as today. */
    @Test void matchingAlgorithmsOverrideReplacesDefaults() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        withStatics(config(twoClassesTwoOptions()), () -> {
            JSONObject inherited = new JSONObject().put("matchingAlgorithms", new JSONArray().put(hotspotter()));
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, inherited, false);
            assertEquals(1, o.options.size());
            assertFalse(o.options.get(0).vector);
            assertTrue(Task.isOptionTask(o.options.get(0).task));
            assertEquals(o.umbrella.getId(), Task.runIdOf(o.options.get(0).task));
            assertTrue(d.vectorRuns.isEmpty());
        });
    }

    @Test void defaultFalseOptionsAreDroppedAndFastlanePublishesToTheFastQueue() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> m = new HashMap<String, List<JSONObject>>();
        m.put("guitarfish", Arrays.asList(miewid().put("default", false), hotspotter()));
        withStatics(config(m), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, true);
            assertEquals(1, o.options.size());
            assertFalse(o.options.get(0).vector);
            assertTrue(d.published.get(0).getBoolean("fastlane"));
            assertEquals("fast", d.published.get(0).getString("lane"));
            assertEquals(Arrays.asList(true), d.publishedFastlane);
            assertTrue(o.options.get(0).task.getParameters().getBoolean("fastlane"));
            assertEquals("fast", o.options.get(0).task.getParameters().getString("lane"));

            RecordingDispatcher d2 = new RecordingDispatcher();
            IA.setDispatcherForTests(d2);
            IA.IntakeOutcome o2 = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("b", "guitarfish")), null, null, false);
            assertEquals(Arrays.asList(false), d2.publishedFastlane);
            assertFalse(o2.options.get(0).task.getParameters().has("fastlane"));
            assertFalse(o2.options.get(0).task.getParameters().has("lane"));
        });
    }

    @Test void realDispatcherPublishesThroughTheLaneClassifier() throws Exception {
        IA.setDispatcherForTests(null);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> m = new HashMap<String, List<JSONObject>>();
        m.put("guitarfish", Arrays.asList(hotspotter()));
        List<String> jobs = new ArrayList<String>();
        try (MockedStatic<IAGateway> gw = mockStatic(IAGateway.class)) {
            gw.when(() -> IAGateway.publishToLane(anyString(), anyString())).thenAnswer(inv -> {
                jobs.add(inv.getArgument(1));
                return true;
            });
            withStatics(config(m), () -> {
                assertTrue(IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, true).options.get(0).published);
                assertTrue(IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false).options.get(0).published);
            });
            gw.verify(() -> IAGateway.addToDetectionQueue(anyString(), anyString()), never());
            gw.verify(() -> IAGateway.addToQueue(anyString(), anyString()), never());
        }
        // classified outside the mock: the real IAGateway.laneFor decides where each job goes
        assertEquals(2, jobs.size());
        assertEquals(IAGateway.LANE_FASTLANE, IAGateway.laneFor(new JSONObject(jobs.get(0))));
        assertEquals(IAGateway.LANE_IA, IAGateway.laneFor(new JSONObject(jobs.get(1))));
    }

    @Test void singleVectorOptionPutsTheV2FlagOnTheUmbrella() throws Exception {
        IA.setDispatcherForTests(new RecordingDispatcher());
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> m = new HashMap<String, List<JSONObject>>();
        m.put("guitarfish", Arrays.asList(miewid()));
        withStatics(config(m), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertSame(o.umbrella, o.options.get(0).task);
            assertTrue(o.options.get(0).vector);
            assertTrue(o.umbrella.getParameters().getBoolean(Task.PARAM_V2_MATCH));
            assertTrue(o.umbrella.getParameters().getBoolean(Task.PARAM_OPTION));
        });
    }

    @Test void transactionRestartFailureAfterThePersistCommitReportsEveryOptionUndispatched() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        // the first beginDBTransaction (before makePersistent) works; the restart after the commit fails
        doNothing().doThrow(new IllegalStateException("datastore gone")).when(sh).beginDBTransaction();
        withStatics(config(twoClassesTwoOptions()), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("body", "guitarfish")), null, null, false);
            assertFalse(o.persistFailed, "the run IS durable");
            assertEquals(2, o.options.size());
            for (IA.OptionResult r : o.options) {
                assertFalse(r.published);
                assertEquals(r.vector ? "DISPATCH_FAILED" : "QUEUE_FAILED", r.failureCode);
            }
            assertTrue(d.vectorRuns.isEmpty() && d.published.isEmpty(), "nothing was dispatched");
        });
    }

    @Test void transactionRestartThatSwallowsItsFailureIsDetected() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        when(sh.isDBTransactionActive()).thenReturn(false);   // beginDBTransaction() returns normally, no transaction
        withStatics(config(twoClassesTwoOptions()), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("body", "guitarfish")), null, null, false);
            assertFalse(o.persistFailed);
            assertEquals(2, o.options.size());
            for (IA.OptionResult r : o.options) {
                assertFalse(r.published);
                assertEquals(r.vector ? "DISPATCH_FAILED" : "QUEUE_FAILED", r.failureCode);
            }
            assertTrue(d.vectorRuns.isEmpty() && d.published.isEmpty(), "nothing was dispatched");
        });
    }

    @Test void baseUrlFailureMarksHotspotterOptionsQueueFailedAndStillRunsVector() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(miewid(), hotspotter()));
        IAJsonProperties cfg = config(one);
        try (MockedStatic<CommonConfiguration> cc = mockStatic(CommonConfiguration.class);
             MockedStatic<IAJsonProperties> jp = mockStatic(IAJsonProperties.class)) {
            cc.when(() -> CommonConfiguration.getServerURL(anyString())).thenThrow(new IllegalStateException("no server URL"));
            jp.when(IAJsonProperties::iaConfig).thenReturn(cfg);
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false);
            assertTrue(o.options.get(0).published, "the vector option does not need a base URL");
            assertEquals("QUEUE_FAILED", o.options.get(1).failureCode);
            assertEquals("error", o.options.get(1).task.getStoredStatus());
            assertTrue(d.published.isEmpty());
        }
    }

    @Test void emptyInputReturnsNull() throws Exception {
        assertNull(IA.intakeAnnotationsUnder(shepherd(true), new ArrayList<Annotation>(), null, null, false));
        assertNull(IA.intakeAnnotationsUnder(shepherd(true), null, null, null, false));
    }

    @Test void scrubRunParamsRemovesExactlyTheRunKeys() {
        JSONObject in = new JSONObject().put(Task.PARAM_RUN_ID, "R").put(Task.PARAM_V2_MATCH, true)
            .put(Task.PARAM_OPTION, true).put("ibeis.identification", new JSONObject())
            .put("importTaskId", "IMP1").put("matchingSetFilter", new JSONObject().put("k", 1)).put("ibeis.detection", true);
        JSONObject out = IA.scrubRunParams(in);
        assertEquals(3, out.length());
        assertEquals("IMP1", out.getString("importTaskId"));
        assertTrue(out.has("matchingSetFilter"));
        assertTrue(out.getBoolean("ibeis.detection"), "the media boundary keeps its own keys; only run keys go");
        assertEquals(7, in.length(), "input not mutated");
        assertEquals(0, IA.scrubRunParams(null).length());
    }

    @Test void intakeProducedNoWorkIsPure() {
        assertFalse(IA.intakeProducedNoWork(null));
        Task bare = new Task();
        assertTrue(IA.intakeProducedNoWork(bare));
        bare.addParameter("ibeis.identification", new JSONObject());
        assertFalse(IA.intakeProducedNoWork(bare), "a single-option umbrella is work");
        Task withKid = new Task();
        new Task(withKid);
        assertFalse(IA.intakeProducedNoWork(withKid));
    }
    // ---- final fix 1: vector options are completed from the ml-service match config ----

    static JSONObject vectorDefaults() {
        return new JSONObject().put("method", "miewid-default").put("version", "msv9")
            .put("api_endpoint", "http://ml/match").put("pipeline_root", "ignored");
    }

    @Test void vectorOptionWithoutVersionTakesTheVersionFromTheDefaults() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        JSONObject noVersion = new JSONObject(
            "{\"pipeline_root\":\"vector\",\"method\":\"miewid\",\"default\":true}");
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(noVersion, hotspotter()));
        withStatics(config(one), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")),
                null, null, false, vectorDefaults());
            assertEquals(1, d.vectorOpts.size());
            JSONObject used = d.vectorOpts.get(0);
            assertEquals("msv9", used.getString("version"));
            assertEquals("miewid", used.getString("method"), "the option's own method is kept");
            assertEquals("http://ml/match", used.getString("api_endpoint"));
            assertEquals("vector", used.getString("pipeline_root"), "only method/version/api_endpoint are filled");
            JSONObject ident = o.options.get(0).task.getParameters().getJSONObject("ibeis.identification");
            assertEquals("msv9", ident.getString("version"), "the marker records the completed option");
            assertFalse(noVersion.has("version"), "the IA.json option object is never mutated");
        });
    }

    @Test void pipelineRootVectorWithoutMethodBecomesAVectorOptionOnceDefaultsSupplyIt() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(new JSONObject(
            "{\"pipeline_root\":\"vector\",\"version\":\"own\",\"default\":true}")));
        withStatics(config(one), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")),
                null, null, false, vectorDefaults());
            assertEquals(1, d.vectorRuns.size(), "ran as a vector option");
            assertTrue(d.published.isEmpty(), "never published to the HotSpotter queue");
            assertTrue(o.options.get(0).vector);
            assertTrue(o.umbrella.getParameters().getBoolean(Task.PARAM_V2_MATCH));
            assertEquals("miewid-default", d.vectorOpts.get(0).getString("method"));
            assertEquals("own", d.vectorOpts.get(0).getString("version"), "existing keys are never overwritten");
        });
    }

    @Test void hotspotterOptionIsUntouchedByVectorDefaults() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(hotspotter()));
        withStatics(config(one), () -> {
            IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false,
                vectorDefaults());
            assertTrue(d.vectorRuns.isEmpty());
            assertEquals(1, d.published.size());
            assertEquals(hotspotter().toString(), d.published.get(0).getJSONObject("opt").toString());
        });
    }

    @Test void nullVectorDefaultsKeepTodaysBehaviour() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> one = new HashMap<String, List<JSONObject>>();
        one.put("guitarfish", Arrays.asList(new JSONObject(
            "{\"pipeline_root\":\"vector\",\"method\":\"miewid\",\"default\":true}"),
            new JSONObject("{\"pipeline_root\":\"vector\",\"default\":true}")));
        withStatics(config(one), () -> {
            IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")), null, null, false, null);
            assertEquals(1, d.vectorRuns.size());
            assertFalse(d.vectorOpts.get(0).has("version"));
            assertEquals(1, d.published.size(), "without defaults a method-less option is not vector, as today");
        });
    }

    // ---- final fix 2: an identOpts failure skips that class instead of aborting the run ----

    static IAJsonProperties configThrowingFor(final Map<String, List<JSONObject>> optsByClass,
        final String throwingClass) {
        IAJsonProperties cfg = config(optsByClass);
        IAJsonProperties wrapper = mock(IAJsonProperties.class);
        when(wrapper.identOpts(any(Shepherd.class), any(Annotation.class))).thenAnswer(inv -> {
            Annotation a = inv.getArgument(1);
            if ((throwingClass == null) || throwingClass.equals(a.getIAClass()))
                throw new NullPointerException("taxonomyKey(null)");
            return cfg.identOpts(inv.getArgument(0), a);
        });
        return wrapper;
    }

    @Test void identOptsThrowingForOneClassSkipsOnlyThatClass() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        withStatics(configThrowingFor(twoClassesTwoOptions(), "guitarfish+rostrum"), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("body", "guitarfish"), ann("rostrum", "guitarfish+rostrum")), null, null, false);
            assertFalse(o.noWork);
            assertFalse(o.persistFailed);
            assertEquals(2, o.options.size());
            assertEquals(Arrays.asList("guitarfish+rostrum"), o.skippedIaClasses);
            assertEquals(1, d.vectorRuns.size());
            assertEquals(1, d.published.size());
        });
    }

    @Test void identOptsThrowingForEveryClassIsNoWork() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        withStatics(configThrowingFor(twoClassesTwoOptions(), null), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh,
                Arrays.asList(ann("body", "guitarfish"), ann("rostrum", "guitarfish+rostrum")), null, null, false);
            assertTrue(o.noWork);
            assertFalse(o.persistFailed);
            assertTrue(o.options.isEmpty());
            assertEquals(2, o.skippedIaClasses.size());
            assertEquals("error", o.umbrella.getStoredStatus());
            assertEquals("INVALID_MATCH_CONFIG", errorCode(o.umbrella));
            assertTrue(d.vectorRuns.isEmpty());
            assertTrue(d.published.isEmpty());
        });
    }
    @Test void unusableVectorDefaultsNeverPublishTheVectorOptionToWbia() throws Exception {
        RecordingDispatcher d = new RecordingDispatcher();
        IA.setDispatcherForTests(d);
        Shepherd sh = shepherd(true);
        Map<String, List<JSONObject>> opts = new HashMap<String, List<JSONObject>>();
        opts.put("guitarfish", Arrays.asList(new JSONObject().put("pipeline_root", "vector").put("default", true)));
        withStatics(config(opts), () -> {
            IA.IntakeOutcome o = IA.intakeAnnotationsUnder(sh, Arrays.asList(ann("a", "guitarfish")),
                null, null, false, new JSONObject());
            assertEquals("INVALID_MATCH_CONFIG", o.options.get(0).failureCode);
            assertEquals("error", o.options.get(0).task.getStoredStatus());
            assertFalse(o.options.get(0).published);
            assertTrue(d.published.isEmpty(), "a malformed vector option must not become a WBIA job");
            assertTrue(d.vectorRuns.isEmpty());
        });
    }

}
