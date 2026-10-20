package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.json.JSONArray;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Shared intake commits its complete task tree before either dispatch target can observe it. */
class IdentificationIntakeDbTest extends IdentificationDbTestSupport {
    @org.junit.jupiter.api.BeforeAll static void configureIdentification() {
        org.ecocean.IAJsonProperties config = org.ecocean.IAJsonProperties.iaConfig();
        config.setJson(iaJson());
        org.ecocean.TestIAJsonPropertiesUtil.override(config);
    }
    @org.junit.jupiter.api.AfterAll static void clearIdentification() {
        org.ecocean.TestIAJsonPropertiesUtil.override(null);
    }
    @AfterEach void resetDispatcher() { IA.setDispatcherForTests(null); }

    @Test void bothDispatchTargetsObserveTheCommittedTreeThroughAnotherManager() throws Exception {
        Shepherd fixture = open("fixture");
        Encounter enc = guitarfish(fixture, BODY);
        Task parent = detectionTask(fixture, nextImportId());
        List<String> annIds = annotationIds(enc);
        String parentId = parent.getId();
        fixture.commitDBTransaction();
        fixture.closeDBTransaction();
        AtomicInteger dispatches = new AtomicInteger();
        IA.setDispatcherForTests(new IA.Dispatcher() {
            void check(String taskId) {
                Shepherd reader = open("dispatchProbe");
                try {
                    Task option = Task.load(taskId, reader);
                    assertNotNull(option, "the task is visible before publication");
                    Task umbrella = option.getParent();
                    assertEquals(parentId, umbrella.getParent().getId());
                    assertEquals(2, umbrella.getChildren().size(), "both options are already durable");
                    dispatches.incrementAndGet();
                } finally {
                    reader.rollbackAndClose();
                }
            }
            @Override public void runVector(JSONObject opt, Task task, Shepherd sh) { check(task.getId()); }
            @Override public boolean publish(String context, String job, boolean fastlane) {
                check(new JSONObject(job).getString("taskId"));
                return true;
            }
        });
        Shepherd sh = open("intake");
        try {
            List<Annotation> anns = new ArrayList<Annotation>();
            for (String id : annIds) anns.add(sh.getAnnotation(id));
            IA.IntakeOutcome outcome = IA.intakeAnnotationsUnder(sh, anns, Task.load(parentId, sh), null, false);
            assertFalse(outcome.persistFailed);
            assertEquals(2, outcome.options.size());
            assertEquals(2, dispatches.get());
            assertTrue(sh.commitDBTransactionWithStatus());
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void failedCheckedCommitPublishesNothingAndLeavesNoTaskTree() throws Exception {
        Shepherd fixture = open("fixture");
        Encounter enc = guitarfish(fixture, BODY);
        List<String> ids = annotationIds(enc);
        fixture.commitDBTransaction();
        fixture.closeDBTransaction();
        AtomicInteger dispatches = new AtomicInteger();
        IA.setDispatcherForTests(new IA.Dispatcher() {
            @Override public void runVector(JSONObject opt, Task task, Shepherd sh) { dispatches.incrementAndGet(); }
            @Override public boolean publish(String context, String job, boolean fastlane) {
                dispatches.incrementAndGet(); return true;
            }
        });
        Shepherd sh = new Shepherd(CTX) {
            @Override public boolean commitDBTransactionWithStatus() { return false; }
        };
        String runId;
        try {
            sh.beginDBTransaction();
            List<Annotation> anns = new ArrayList<Annotation>();
            for (String id : ids) anns.add(sh.getAnnotation(id));
            IA.IntakeOutcome outcome = IA.intakeAnnotationsUnder(sh, anns, null, null, false);
            runId = outcome.umbrella.getId();
            assertTrue(outcome.persistFailed);
            assertEquals(0, dispatches.get());
        } finally {
            sh.rollbackAndClose();
        }
        Shepherd reader = open("rolledBackRun");
        try {
            assertNull(Task.load(runId, reader));
        } finally {
            reader.rollbackAndClose();
        }
    }
    @Test void failedEncounterIntakeDoesNotStopTheRestOfTheCallback() throws Exception {
        Shepherd fixture = open("callbackFixture");
        Encounter first = guitarfish(fixture, BODY);
        Encounter second = guitarfish(fixture, BODY);
        Task detection = detectionTask(fixture, nextImportId());
        List<String> ids = annotationIds(first, second);
        String taskId = detection.getId();
        fixture.commitDBTransaction();
        fixture.closeDBTransaction();
        AtomicInteger attempts = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> successfulTask = new java.util.concurrent.atomic.AtomicReference<String>();
        try (org.mockito.MockedStatic<IA> ia = org.mockito.Mockito.mockStatic(IA.class, org.mockito.Mockito.CALLS_REAL_METHODS);
             org.mockito.MockedStatic<org.ecocean.identity.IBEISIA> ibeis = org.mockito.Mockito.mockStatic(
                 org.ecocean.identity.IBEISIA.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            ia.when(() -> IA.getProperty(CTX, "IBEISIADisableIdentification")).thenReturn("false");
            ibeis.when(() -> org.ecocean.identity.IBEISIA.validForIdentification(
                org.mockito.ArgumentMatchers.any(Annotation.class), org.mockito.ArgumentMatchers.eq(CTX))).thenReturn(true);
            ia.when(() -> IA.intakeAnnotations(org.mockito.ArgumentMatchers.any(Shepherd.class),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(Task.class),
                org.mockito.ArgumentMatchers.eq(false))).thenAnswer(call -> {
                    Shepherd sh = call.getArgument(0);
                    if (attempts.incrementAndGet() == 1) {
                        sh.rollbackDBTransaction();
                        return null;
                    }
                    Task child = new Task();
                    child.setObjectAnnotations(call.getArgument(1));
                    successfulTask.set(child.getId());
                    return child;
                });
            JSONObject response = new JSONObject().put("embeddingExtraction", true)
                .put("annotationMap", new JSONObject().put("fixtureAsset", new JSONArray(ids)));
            org.ecocean.identity.IBEISIA.processCallback(taskId, response, CTX, "");
        }
        assertEquals(2, attempts.get(), "a failed encounter must not suppress later encounters");
        Shepherd reader = open("callbackCheck");
        try {
            Task succeeded = Task.load(successfulTask.get(), reader);
            assertNotNull(succeeded, "the later encounter's task is durable");
            assertNotNull(succeeded.getParent());
            assertEquals(1, succeeded.numberAnnotations());
        } finally {
            reader.rollbackAndClose();
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
        return new JSONObject()
            .put("Pseudobatos", new JSONObject().put("lentiginosus", lentiginosus))
            .put("Rhina", new JSONObject().put("ancylostoma", ancylostoma));
    }
}
