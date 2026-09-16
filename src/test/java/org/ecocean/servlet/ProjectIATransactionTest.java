package org.ecocean.servlet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.Encounter;
import org.ecocean.Project;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.shepherd.core.Shepherd;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Transaction-ownership tests for {@link ProjectIA#initiateProjectMatch} (issue #1761).
 *
 * <p>{@code IA.intakeAnnotations()} performs the vector (MiewID) match INLINE on the caller's
 * Shepherd -- it creates the per-annotation subtasks, the MatchResult and the terminal task status
 * with {@code makePersistent()} only, and relies on the caller to commit. ProjectIA used to roll
 * back unconditionally, which discarded all of that and left a childless task with a null status;
 * {@code Task.getStatus()} then reports the non-terminal "waiting to queue" forever, so the React
 * match-results page spins indefinitely.</p>
 */
class ProjectIATransactionTest {
    private static final String PREFIX = "MES-#####";
    private static final String ENC_ID = "d316cd66-f0b1-49a4-859e-b1c4cec177ae";
    private static final String PROJECT_ID = "6ea1c0a2-1a7e-4a0e-9b2c-0d3f5a7e9b11";

    // A query encounter carrying exactly one identifiable annotation, in a project of its own.
    private static Shepherd shepherdWithOneMatchableAnnotation() {
        return shepherdWithMatchableAnnotations("ann-1");
    }

    private static Shepherd shepherdWithMatchableAnnotations(String... annIds) {
        ArrayList<Annotation> encAnns = new ArrayList<>();

        for (String annId : annIds) {
            Annotation queryAnn = mock(Annotation.class);
            when(queryAnn.getId()).thenReturn(annId);
            encAnns.add(queryAnn);
        }
        Encounter queryEnc = mock(Encounter.class);
        when(queryEnc.getAnnotations()).thenReturn(encAnns);
        Project project = mock(Project.class);
        when(project.getId()).thenReturn(PROJECT_ID);
        when(project.getEncounters()).thenReturn(new ArrayList<Encounter>());
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getProjectByProjectIdPrefix(PREFIX)).thenReturn(project);
        when(myShepherd.getEncounter(ENC_ID)).thenReturn(queryEnc);
        return myShepherd;
    }

    // Committing before the inline match would persist nothing useful, so order is part of
    // the contract, not an implementation detail. (doPost() still calls rollbackAndClose()
    // afterwards to release the PersistenceManager; that is a no-op on a committed transaction.)
    @Test void helperCommitsAfterTheInlineMatchWorkAndDoesNotRollBackOnSuccess() {
        Shepherd myShepherd = shepherdWithOneMatchableAnnotation();
        List<String> callOrder = new ArrayList<>();

        when(myShepherd.commitDBTransactionWithStatus()).thenAnswer(invocation -> {
            callOrder.add("commit");
            return true;
        });
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class);
            MockedStatic<IA> ia = mockStatic(IA.class)) {
            ibeis.when(() -> IBEISIA.validForIdentification(any(Annotation.class))).thenReturn(
                true);
            ia.when(() -> IA.intakeAnnotations(any(Shepherd.class), any(List.class),
                any(Task.class), anyBoolean())).thenAnswer(invocation -> {
                callOrder.add("intakeAnnotations");
                return new Task();
            });
            ProjectIA.initiateProjectMatch(myShepherd, PREFIX, ENC_ID);
        }
        assertEquals(Arrays.asList("intakeAnnotations", "commit"), callOrder,
            "the inline match work must be committed, and committed after it is done");
        verify(myShepherd, never()).rollbackDBTransaction();
    }

    @Test void reportsSuccessWithInitiatedJobsWhenTheCommitLands() {
        Shepherd myShepherd = shepherdWithOneMatchableAnnotation();

        when(myShepherd.commitDBTransactionWithStatus()).thenReturn(true);
        JSONObject res;
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class);
            MockedStatic<IA> ia = mockStatic(IA.class)) {
            ibeis.when(() -> IBEISIA.validForIdentification(any(Annotation.class))).thenReturn(
                true);
            ia.when(() -> IA.intakeAnnotations(any(Shepherd.class), any(List.class),
                any(Task.class), anyBoolean())).thenReturn(new Task());
            res = ProjectIA.initiateProjectMatch(myShepherd, PREFIX, ENC_ID);
        }
        assertTrue(res.optBoolean("success", false),
            "success must be reported after a good commit");
        assertEquals(1, res.getJSONArray("initiatedJobs").length(),
            "the one identifiable annotation should have initiated one job");
    }

    // commitDBTransaction() swallows commit failures, which is why the production code uses
    // commitDBTransactionWithStatus(): a match whose results were not persisted must not be
    // advertised to the user as started.
    @Test void reportsFailureWhenTheCommitDoesNotLand() {
        Shepherd myShepherd = shepherdWithOneMatchableAnnotation();

        when(myShepherd.commitDBTransactionWithStatus()).thenReturn(false);
        JSONObject res;
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class);
            MockedStatic<IA> ia = mockStatic(IA.class)) {
            ibeis.when(() -> IBEISIA.validForIdentification(any(Annotation.class))).thenReturn(
                true);
            ia.when(() -> IA.intakeAnnotations(any(Shepherd.class), any(List.class),
                any(Task.class), anyBoolean())).thenReturn(new Task());
            res = ProjectIA.initiateProjectMatch(myShepherd, PREFIX, ENC_ID);
        }
        assertFalse(res.optBoolean("success", false),
            "a failed commit must not be reported as a started match");
        assertEquals(0, res.getJSONArray("initiatedJobs").length(),
            "no jobs should be advertised when the work was not persisted");
    }

    // Each annotation's match is committed on its own, so a blow-up part way through the
    // encounter cannot throw away the matches that already ran (issue #1761).
    @Test void aFailingAnnotationDoesNotDiscardTheMatchAlreadyCommittedForAnother() {
        Shepherd myShepherd = shepherdWithMatchableAnnotations("ann-1", "ann-2");
        List<String> callOrder = new ArrayList<>();

        when(myShepherd.commitDBTransactionWithStatus()).thenAnswer(invocation -> {
            callOrder.add("commit");
            return true;
        });
        JSONObject res;
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class);
            MockedStatic<IA> ia = mockStatic(IA.class)) {
            ibeis.when(() -> IBEISIA.validForIdentification(any(Annotation.class))).thenReturn(
                true);
            ia.when(() -> IA.intakeAnnotations(any(Shepherd.class), any(List.class),
                any(Task.class), anyBoolean())).thenAnswer(invocation -> {
                callOrder.add("intakeAnnotations");
                // blow up on the second annotation only
                if (callOrder.contains("commit")) throw new RuntimeException("intake exploded");
                return new Task();
            });
            res = ProjectIA.initiateProjectMatch(myShepherd, PREFIX, ENC_ID);
        }
        assertEquals(Arrays.asList("intakeAnnotations", "commit", "intakeAnnotations"), callOrder,
            "the first annotation must already be committed before the second one runs");
        assertEquals(1, res.getJSONArray("initiatedJobs").length(),
            "the annotation that succeeded keeps its job");
        assertEquals("ann-2", res.getJSONArray("failedAnnotations").getString(0),
            "the annotation that blew up must be reported");
        assertFalse(res.optBoolean("success", false),
            "a partially failed run must not claim overall success");
        // the poisoned transaction has to be unwound so later annotations start clean
        verify(myShepherd).rollbackDBTransaction();
    }

    @Test void reportsFailureAndCommitsNothingWhenTheProjectIsUnknown() {
        Shepherd myShepherd = mock(Shepherd.class);

        when(myShepherd.getProjectByProjectIdPrefix(anyString())).thenReturn(null);
        when(myShepherd.getEncounter(anyString())).thenReturn(null);
        JSONObject res = ProjectIA.initiateProjectMatch(myShepherd, PREFIX, ENC_ID);
        assertFalse(res.optBoolean("success", false), "unknown project must not report success");
        verify(myShepherd, never()).commitDBTransactionWithStatus();
    }
}
