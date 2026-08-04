package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pure-logic coverage of the WBIA annotation reconciliation sweep helpers
 * (spec docs/superpowers/specs/2026-08-03-wbia-annotation-reconciliation-sweep-design.md):
 * page collection (dedup, acmId bucketing, exhaustion) and cursor advancement
 * (wrap-around, maxFixes clamp) over a String/UUID cursor.
 *
 * <p>Sibling of {@link AcmIdBotSweepTest}, which covers the MediaAsset sweep. Two
 * structural differences worth keeping in mind while reading:</p>
 * <ul>
 *   <li>{@code Annotation.id} is a String UUID primary key, so the cursor is
 *       lexicographic and its "start from the beginning" sentinel is the empty string
 *       rather than 0.</li>
 *   <li>The read query projects {@code (id, acmId)} rather than materializing
 *       Annotation entities, so the collector consumes {@code String[]} rows.</li>
 * </ul>
 */
class AcmIdBotAnnotSweepTest {
    // a projection row as the read phase yields it: [0] = id, [1] = acmId
    private static String[] row(String id, String acmId) {
        return new String[] { id, acmId };
    }

    // Arrays.asList() with a single String[] would bind as varargs of String and yield a
    // List<String>, so single-row inputs go through singletonList instead.
    private static java.util.Iterator<String[]> oneRow(String id, String acmId) {
        return java.util.Collections.singletonList(row(id, acmId)).iterator();
    }

    private static final String UUID_A = "aaaaaaaa-0000-4000-8000-000000000001";
    private static final String UUID_B = "bbbbbbbb-0000-4000-8000-000000000002";
    private static final String UUID_C = "cccccccc-0000-4000-8000-000000000003";

    // ---------- collectAnnotSweepPage ----------

    @Test void bucketsNullAcmSeparatelyFromProbeable() {
        List<String[]> in = Arrays.asList(
            row("id-1", UUID_A), row("id-2", null), row("id-3", UUID_B));
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(in.iterator(), 10);

        assertEquals(2, page.acmIdToAnnotId.size());
        assertEquals(1, page.nullAcmAnnotIds.size());
        assertEquals("id-2", page.nullAcmAnnotIds.get(0));
        assertTrue(page.rawExhausted, "short input should exhaust");
        assertEquals("id-3", page.lastAnnotId);
    }

    @Test void mapsAcmIdBackToItsAnnotationId() {
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(
            oneRow("id-1", UUID_A), 10);

        assertEquals("id-1", page.acmIdToAnnotId.get(UUID_A));
    }

    @Test void dedupesRepeatedAnnotations() {
        // the scope query joins through Encounter, so an annotation attached to more
        // than one encounter can appear twice even with SQL distinct as a backstop
        List<String[]> in = Arrays.asList(
            row("id-1", UUID_A), row("id-1", UUID_A), row("id-2", UUID_B), row("id-1", UUID_A));
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(in.iterator(), 10);

        assertEquals(2, page.acmIdToAnnotId.size());
        assertTrue(page.rawExhausted);
        assertEquals("id-2", page.lastAnnotId);
    }

    @Test void skipsNullRows() {
        List<String[]> in = Arrays.asList(null, row("id-2", UUID_A));
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(in.iterator(), 10);

        assertEquals(1, page.acmIdToAnnotId.size());
        assertTrue(page.rawExhausted);
    }

    @Test void skipsRowsWithNoIdAtAll() {
        // an id-less row could not be re-loaded in the heal phase, and must not
        // become the cursor either
        List<String[]> in = Arrays.asList(row(null, UUID_A), row("id-2", UUID_B));
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(in.iterator(), 10);

        assertEquals(1, page.acmIdToAnnotId.size());
        assertEquals("id-2", page.lastAnnotId);
    }

    @Test void pageLimitStopsCollectionAndMarksNotExhausted() {
        List<String[]> in = new ArrayList<String[]>();

        for (int i = 1; i <= 5; i++) in.add(row("id-" + i, UUID_A.replace("001", "00" + i)));
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(in.iterator(), 3);

        assertEquals(3, page.acmIdToAnnotId.size());
        assertFalse(page.rawExhausted, "limit hit before input end: not exhausted");
        assertEquals("id-3", page.lastAnnotId);
    }

    @Test void exactlyFullPageWithNoMoreInputIsExhausted() {
        List<String[]> in = Arrays.asList(
            row("id-1", UUID_A), row("id-2", UUID_B), row("id-3", UUID_C));
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(in.iterator(), 3);

        assertEquals(3, page.acmIdToAnnotId.size());
        assertTrue(page.rawExhausted, "input ended exactly at limit: exhausted");
    }

    @Test void emptyInputIsExhaustedWithNullSentinelLastId() {
        AcmIdBot.AnnotSweepPage page =
            AcmIdBot.collectAnnotSweepPage(new ArrayList<String[]>().iterator(), 10);

        assertTrue(page.rawExhausted);
        assertNull(page.lastAnnotId, "empty page has no last id");
        assertEquals(0, page.acmIdToAnnotId.size());
        assertEquals(0, page.nullAcmAnnotIds.size());
    }

    @Test void routesMalformedAcmIdToNullBucket() {
        // a non-UUID value would make WBIA reject the whole probe chunk and could
        // strand the rest of the page, so it goes straight to the heal path
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(
            oneRow("id-7", "not-a-valid-uuid"), 10);

        assertEquals(1, page.nullAcmAnnotIds.size());
        assertEquals("id-7", page.nullAcmAnnotIds.get(0));
        assertEquals(0, page.acmIdToAnnotId.size());
    }

    @Test void duplicateAcmIdKeepsLatterAnnotation() {
        // two annotations sharing one acmId is corrupt data; the sweep must not lose
        // the page over it, and only one of them can be probed/healed this pass
        List<String[]> in = Arrays.asList(row("id-1", UUID_A), row("id-2", UUID_A));
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(in.iterator(), 10);

        assertEquals(1, page.acmIdToAnnotId.size());
        assertEquals("id-2", page.acmIdToAnnotId.get(UUID_A));
        assertEquals("id-2", page.lastAnnotId);
    }

    // ---------- partial-read progress (Codex finding #4) ----------

    @Test void partialProgressSurvivesAnIterationFailure() {
        // The page is populated in place, so a throw partway through iteration still
        // leaves the caller a last-safely-read id to make a TARGETED skip to. Without
        // this the sweep would retry the same poison page forever: a String cursor has
        // no "cursor += PAGE_SIZE" blind advance available to it.
        AcmIdBot.AnnotSweepPage page = new AcmIdBot.AnnotSweepPage();
        java.util.Iterator<String[]> exploding = new java.util.Iterator<String[]>() {
            private int n = 0;
            public boolean hasNext() { return true; }
            public String[] next() {
                if (++n > 2) throw new IllegalStateException("simulated bad row");
                return row("id-" + n, UUID_A.replace("001", "00" + n));
            }
        };

        try {
            AcmIdBot.collectAnnotSweepPageInto(page, exploding, 10);
        } catch (RuntimeException expected) { /* the read phase catches this */ }
        assertEquals("id-2", page.lastAnnotId, "partial progress must be visible to the caller");
        assertFalse(page.rawExhausted, "a failed read never counts as exhaustion");
    }

    // ---------- nextAnnotCursorAfterSuccess ----------

    @Test void normalPageAdvancesToLastAnnotId() {
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(
            Arrays.asList(row("id-7", UUID_A), row("id-9", UUID_B)).iterator(), 1);

        assertFalse(page.rawExhausted);
        assertEquals("id-7",
            AcmIdBot.nextAnnotCursorAfterSuccess(page, false, page.lastAnnotId));
    }

    @Test void exhaustionWrapsCursorToEmptyString() {
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(
            oneRow("id-7", UUID_A), 10);

        assertTrue(page.rawExhausted);
        assertEquals("", AcmIdBot.nextAnnotCursorAfterSuccess(page, false, page.lastAnnotId),
            "wrap-around restarts the sweep from the beginning");
    }

    @Test void maxFixesClampBeatsExhaustionWrap() {
        AcmIdBot.AnnotSweepPage page = AcmIdBot.collectAnnotSweepPage(
            Arrays.asList(row("id-7", UUID_A), row("id-9", UUID_B)).iterator(), 10);

        assertTrue(page.rawExhausted);
        // cap hit while healing id-7: resume from id-7, do NOT wrap
        assertEquals("id-7", AcmIdBot.nextAnnotCursorAfterSuccess(page, true, "id-7"));
    }

    @Test void emptyPageHoldsCursorRatherThanNullingIt() {
        // an empty page is exhaustion: wrap, never write a null cursor
        AcmIdBot.AnnotSweepPage page =
            AcmIdBot.collectAnnotSweepPage(new ArrayList<String[]>().iterator(), 10);

        assertEquals("", AcmIdBot.nextAnnotCursorAfterSuccess(page, false, page.lastAnnotId));
    }
}
