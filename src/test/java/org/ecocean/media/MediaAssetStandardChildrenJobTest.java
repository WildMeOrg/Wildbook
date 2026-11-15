package org.ecocean.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Orchestration of the background standard-children job: one Shepherd (transaction) per asset,
 * progress committed asset by asset, the callback once at the end. Shepherd and the asset loader
 * are mocked, so this proves the order of operations, not DataNucleus behaviour.
 */
class MediaAssetStandardChildrenJobTest {
    private static final List<Integer> IDS = Arrays.asList(11, 12, 13);
    private static final List<Integer> NONE = Collections.<Integer>emptyList();

    // what happened, in order: "begin:1", "update:11", "commit:1", "close:1", ..., "callback"
    // (begin/commit/close carry the Shepherd number in construction order, update the asset id)
    private List<String> events;
    // per Shepherd number
    private Set<Integer> beginFails;
    private Set<Integer> commitFails;
    private Map<Integer, Throwable> closeThrows;
    // per asset id
    private Map<Integer, Throwable> updateThrows;
    private Set<Integer> missing;
    private RuntimeException callbackThrows;

    @BeforeEach
    void setUp() {
        events = new ArrayList<String>();
        beginFails = new HashSet<Integer>();
        commitFails = new HashSet<Integer>();
        closeThrows = new HashMap<Integer, Throwable>();
        updateThrows = new HashMap<Integer, Throwable>();
        missing = new HashSet<Integer>();
        callbackThrows = null;
    }

    // runs the job over IDS and returns the ids it reports as failed
    private List<Integer> run() {
        try (MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class,
                (shep, ctx) -> {
            final int n = ctx.getCount();
            final boolean[] active = { false };
            doAnswer(inv -> {
                events.add("begin:" + n);
                active[0] = !beginFails.contains(n);
                return null;
            }).when(shep).beginDBTransaction();
            when(shep.isDBTransactionActive()).thenAnswer(inv -> active[0]);
            when(shep.commitDBTransactionWithStatus()).thenAnswer(inv -> {
                events.add("commit:" + n);
                return !commitFails.contains(n);
            });
            doAnswer(inv -> {
                events.add("close:" + n);
                Throwable t = closeThrows.get(n);
                if (t != null) throw t;
                return null;
            }).when(shep).rollbackAndClose();
        });
            MockedStatic<MediaAssetFactory> factory = mockStatic(MediaAssetFactory.class)) {
            factory.when(() -> MediaAssetFactory.load(anyInt(), any(Shepherd.class))).thenAnswer(
                inv -> {
                final int id = inv.getArgument(0);
                final Shepherd loadedWith = inv.getArgument(1);
                if (missing.contains(id)) return null;
                MediaAsset ma = mock(MediaAsset.class);
                // only the Shepherd the asset was loaded with may save its children
                when(ma.updateStandardChildren(loadedWith)).thenAnswer(inv2 -> {
                    events.add("update:" + id);
                    Throwable t = updateThrows.get(id);
                    if (t != null) throw t;
                    return new ArrayList<MediaAsset>();
                });
                return ma;
            });
            return MediaAsset.runStandardChildrenJob("context0", IDS, () -> {
                events.add("callback");
                if (callbackThrows != null) throw callbackThrows;
            }, "test");
        }
    }

    private static final List<String> ALL_OK = Arrays.asList(
        "begin:1", "update:11", "commit:1", "close:1",
        "begin:2", "update:12", "commit:2", "close:2",
        "begin:3", "update:13", "commit:3", "close:3",
        "callback");

    @Test
    void eachAssetIsCommittedAndClosedBeforeTheNextBegins() {
        assertEquals(NONE, run());
        assertEquals(ALL_OK, events);
    }

    @Test
    void anExceptionOnOneAssetDoesNotLoseTheOthers() {
        updateThrows.put(12, new RuntimeException("resize failed"));
        assertEquals(Arrays.asList(12), run());
        assertEquals(Arrays.asList(
            "begin:1", "update:11", "commit:1", "close:1",
            "begin:2", "update:12", "close:2",
            "begin:3", "update:13", "commit:3", "close:3",
            "callback"), events);
    }

    @Test
    void aNonFatalErrorOnOneAssetDoesNotLoseTheOthers() {
        updateThrows.put(12, new AssertionError("not fatal"));
        assertEquals(Arrays.asList(12), run());
        assertEquals(Arrays.asList(
            "begin:1", "update:11", "commit:1", "close:1",
            "begin:2", "update:12", "close:2",
            "begin:3", "update:13", "commit:3", "close:3",
            "callback"), events);
    }

    @Test
    void aFailedCommitIsReportedCleanedUpAndTheJobContinues() {
        commitFails.add(2);
        assertEquals(Arrays.asList(12), run());
        assertEquals(ALL_OK, events);
    }

    @Test
    void whenEveryAssetFailsTheCallbackStillRunsOnceAfterCleanup() {
        commitFails.addAll(Arrays.asList(1, 2, 3));
        assertEquals(IDS, run());
        assertEquals(ALL_OK, events);
    }

    @Test
    void aFatalErrorStopsTheJobKeepsEarlierCommitsAndSkipsTheCallback() {
        for (Error fatal : new Error[] {
            new OutOfMemoryError("Java heap space"), new ThreadDeath(),
            new NoClassDefFoundError("gone")
        }) {
            setUp();
            updateThrows.put(12, fatal);
            assertThrows(fatal.getClass(), () -> run());
            assertEquals(Arrays.asList(
                "begin:1", "update:11", "commit:1", "close:1",
                "begin:2", "update:12", "close:2"), events);
        }
    }

    @Test
    void aFatalErrorDuringCleanupStopsTheJobAndSkipsTheCallback() {
        closeThrows.put(1, new OutOfMemoryError("Java heap space"));
        assertThrows(OutOfMemoryError.class, () -> run());
        assertEquals(Arrays.asList("begin:1", "update:11", "commit:1", "close:1"), events);
    }

    @Test
    void aCleanupFailureDoesNotStopTheJobOrUndoTheCommit() {
        closeThrows.put(1, new RuntimeException("close failed"));
        assertEquals(NONE, run());
        assertEquals(ALL_OK, events);
    }

    @Test
    void anAssetWhoseTransactionDidNotBeginIsSkipped() {
        beginFails.add(2);
        assertEquals(Arrays.asList(12), run());
        assertEquals(Arrays.asList(
            "begin:1", "update:11", "commit:1", "close:1",
            "begin:2", "close:2",
            "begin:3", "update:13", "commit:3", "close:3",
            "callback"), events);
    }

    @Test
    void aMissingAssetIsSkipped() {
        missing.add(12);
        assertEquals(Arrays.asList(12), run());
        assertEquals(Arrays.asList(
            "begin:1", "update:11", "commit:1", "close:1",
            "begin:2", "close:2",
            "begin:3", "update:13", "commit:3", "close:3",
            "callback"), events);
    }

    @Test
    void anExceptionFromTheCallbackDoesNotEscape() {
        callbackThrows = new RuntimeException("indexing failed");
        assertEquals(NONE, run());
        assertEquals(ALL_OK, events);
    }

    @Test
    void emptyOrNullIdsRunTheCallbackAtOnceWithoutAThread() {
        final List<String> ran = new ArrayList<String>();

        MediaAsset.updateStandardChildrenBackground("context0", NONE, () -> ran.add("empty"));
        MediaAsset.updateStandardChildrenBackground("context0", null, () -> ran.add("null"));
        assertEquals(Arrays.asList("empty", "null"), ran);
    }
}
