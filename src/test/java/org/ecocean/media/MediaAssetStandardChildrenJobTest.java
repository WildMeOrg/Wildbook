package org.ecocean.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

    // what happened, in order: "begin:1", "update:12", "commit:1", "close:1", ..., "callback"
    private List<String> events;
    // per Shepherd number (1-based, in construction order)
    private Set<Integer> beginFails;
    private Set<Integer> commitFails;
    private Set<Integer> closeThrows;
    // per asset id
    private Map<Integer, Throwable> updateThrows;
    private Set<Integer> missing;

    @BeforeEach
    void setUp() {
        events = new ArrayList<String>();
        beginFails = new HashSet<Integer>();
        commitFails = new HashSet<Integer>();
        closeThrows = new HashSet<Integer>();
        updateThrows = new HashMap<Integer, Throwable>();
        missing = new HashSet<Integer>();
    }

    private void run(List<Integer> ids) {
        final int[] shepherdCount = { 0 };
        final Map<Shepherd, Integer> numbers = new HashMap<Shepherd, Integer>();

        try (MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class,
                (shep, ctx) -> {
            final int n = ++shepherdCount[0];
            numbers.put(shep, n);
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
                if (closeThrows.contains(n)) throw new RuntimeException("close failed");
                return null;
            }).when(shep).rollbackAndClose();
        });
            MockedStatic<MediaAssetFactory> factory = mockStatic(MediaAssetFactory.class)) {
            factory.when(() -> MediaAssetFactory.load(anyInt(), any(Shepherd.class))).thenAnswer(
                inv -> {
                final int id = inv.getArgument(0);
                if (missing.contains(id)) return null;
                MediaAsset ma = mock(MediaAsset.class);
                when(ma.updateStandardChildren(any(Shepherd.class))).thenAnswer(inv2 -> {
                    events.add("update:" + id);
                    Throwable t = updateThrows.get(id);
                    if (t != null) throw t;
                    return new ArrayList<MediaAsset>();
                });
                return ma;
            });
            MediaAsset.runStandardChildrenJob("context0", ids, () -> events.add("callback"),
                "test");
        }
    }

    @Test
    void eachAssetIsCommittedAndClosedBeforeTheNextBegins() {
        run(IDS);
        assertEquals(Arrays.asList(
            "begin:1", "update:11", "commit:1", "close:1",
            "begin:2", "update:12", "commit:2", "close:2",
            "begin:3", "update:13", "commit:3", "close:3",
            "callback"), events);
    }

    @Test
    void anExceptionOnOneAssetDoesNotLoseTheOthers() {
        updateThrows.put(12, new RuntimeException("resize failed"));
        run(IDS);
        assertEquals(Arrays.asList(
            "begin:1", "update:11", "commit:1", "close:1",
            "begin:2", "update:12", "close:2",
            "begin:3", "update:13", "commit:3", "close:3",
            "callback"), events);
    }

    @Test
    void aNonFatalErrorOnOneAssetDoesNotLoseTheOthers() {
        updateThrows.put(12, new AssertionError("not fatal"));
        run(IDS);
        assertFalse(events.contains("commit:2"));
        assertTrue(events.contains("close:2"));
        assertTrue(events.contains("commit:3"));
        assertEquals("callback", events.get(events.size() - 1));
    }

    @Test
    void aFailedCommitIsCleanedUpAndTheJobContinues() {
        commitFails.add(2);
        run(IDS);
        assertEquals(Arrays.asList(
            "begin:1", "update:11", "commit:1", "close:1",
            "begin:2", "update:12", "commit:2", "close:2",
            "begin:3", "update:13", "commit:3", "close:3",
            "callback"), events);
    }

    @Test
    void aFatalErrorStopsTheJobKeepsEarlierCommitsAndSkipsTheCallback() {
        updateThrows.put(12, new OutOfMemoryError("Java heap space"));
        assertThrows(OutOfMemoryError.class, () -> run(IDS));
        assertEquals(Arrays.asList(
            "begin:1", "update:11", "commit:1", "close:1",
            "begin:2", "update:12", "close:2"), events);
    }

    @Test
    void aCleanupFailureDoesNotStopTheJob() {
        closeThrows.add(1);
        run(IDS);
        assertTrue(events.contains("commit:1"));
        assertTrue(events.contains("commit:2"));
        assertTrue(events.contains("commit:3"));
        assertEquals("callback", events.get(events.size() - 1));
    }

    @Test
    void anAssetWhoseTransactionDidNotBeginIsSkipped() {
        beginFails.add(2);
        run(IDS);
        assertFalse(events.contains("update:12"));
        assertFalse(events.contains("commit:2"));
        assertTrue(events.contains("close:2"));
        assertTrue(events.contains("commit:3"));
        assertEquals("callback", events.get(events.size() - 1));
    }

    @Test
    void aMissingAssetIsSkipped() {
        missing.add(12);
        run(IDS);
        assertFalse(events.contains("update:12"));
        assertFalse(events.contains("commit:2"));
        assertTrue(events.contains("close:2"));
        assertTrue(events.contains("commit:3"));
        assertEquals("callback", events.get(events.size() - 1));
    }

    @Test
    void emptyOrNullIdsRunTheCallbackAtOnceWithoutAThread() {
        final List<String> ran = new ArrayList<String>();

        MediaAsset.updateStandardChildrenBackground("context0", Collections.<Integer>emptyList(),
            () -> ran.add("empty"));
        MediaAsset.updateStandardChildrenBackground("context0", null, () -> ran.add("null"));
        assertEquals(Arrays.asList("empty", "null"), ran);
    }
}
