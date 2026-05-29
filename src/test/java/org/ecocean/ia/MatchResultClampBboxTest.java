package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.json.JSONArray;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the bbox-clamp + JSON-conversion helpers added to
 * {@link MatchResult} to fix the PairX {@code /explain/} 400
 * rejections and the shared-tmpArr bug. (Empty-match-prospects
 * design Track 2 C12.)
 */
class MatchResultClampBboxTest {

    // --- clampBbox -------------------------------------------------------

    @Test void clampBbox_passesThroughPositiveValues() {
        int[] in = { 10, 20, 100, 200 };
        assertArrayEquals(new int[] { 10, 20, 100, 200 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_clampsNegativeX_shrinksWidth() {
        // x=-80 means box starts 80px to the left of the image; the
        // in-image portion is x=0, w=1786-80=1706.
        int[] in = { -80, 42, 1786, 2228 };
        assertArrayEquals(new int[] { 0, 42, 1706, 2228 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_clampsNegativeY_shrinksHeight() {
        int[] in = { 10, -50, 100, 300 };
        assertArrayEquals(new int[] { 10, 0, 100, 250 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_clampsBothXAndY_independently() {
        int[] in = { -44, -27, 2072, 2406 };
        assertArrayEquals(new int[] { 0, 0, 2028, 2379 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_clampsWidthFloorToZero_whenAbsXExceedsW() {
        // Pathological: x=-200, w=100 means the box is entirely off-image.
        // Clamp to a zero-width box at the origin rather than a negative
        // width that would also be rejected.
        int[] in = { -200, 50, 100, 80 };
        assertArrayEquals(new int[] { 0, 50, 0, 80 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_clampsHeightFloorToZero_whenAbsYExceedsH() {
        int[] in = { 10, -300, 100, 100 };
        assertArrayEquals(new int[] { 10, 0, 100, 0 },
            MatchResult.clampBbox(in));
    }

    @Test void clampBbox_returnsInput_whenNull() {
        assertNull(MatchResult.clampBbox(null));
    }

    @Test void clampBbox_returnsInput_whenShorterThanFour() {
        int[] in = { 1, 2, 3 };
        assertArrayEquals(in, MatchResult.clampBbox(in));
    }

    @Test void clampBbox_doesNotMutateInput() {
        int[] in = { -80, 42, 1786, 2228 };
        MatchResult.clampBbox(in);
        // Caller's array must be untouched; clamp returns a fresh copy.
        assertArrayEquals(new int[] { -80, 42, 1786, 2228 }, in);
    }

    // --- bboxToJsonArray -------------------------------------------------

    @Test void bboxToJsonArray_buildsArrayFromInts() {
        JSONArray out = MatchResult.bboxToJsonArray(new int[] { 1, 2, 3, 4 });
        assertEquals(4, out.length());
        assertEquals(1, out.getInt(0));
        assertEquals(2, out.getInt(1));
        assertEquals(3, out.getInt(2));
        assertEquals(4, out.getInt(3));
    }

    @Test void bboxToJsonArray_emptyForNullInput() {
        JSONArray out = MatchResult.bboxToJsonArray(null);
        assertEquals(0, out.length());
    }

    @Test void bboxToJsonArray_handlesEmptyArray() {
        JSONArray out = MatchResult.bboxToJsonArray(new int[0]);
        assertEquals(0, out.length());
    }

    // --- isDegenerateBbox ------------------------------------------------

    @Test void isDegenerateBbox_falseForPositiveDims() {
        org.junit.jupiter.api.Assertions.assertFalse(
            MatchResult.isDegenerateBbox(new int[] { 0, 0, 100, 200 }));
    }

    @Test void isDegenerateBbox_trueForZeroWidth() {
        org.junit.jupiter.api.Assertions.assertTrue(
            MatchResult.isDegenerateBbox(new int[] { 0, 0, 0, 200 }));
    }

    @Test void isDegenerateBbox_trueForZeroHeight() {
        org.junit.jupiter.api.Assertions.assertTrue(
            MatchResult.isDegenerateBbox(new int[] { 0, 0, 100, 0 }));
    }

    @Test void isDegenerateBbox_trueForNull() {
        org.junit.jupiter.api.Assertions.assertTrue(MatchResult.isDegenerateBbox(null));
    }

    @Test void isDegenerateBbox_trueForShortArray() {
        org.junit.jupiter.api.Assertions.assertTrue(
            MatchResult.isDegenerateBbox(new int[] { 1, 2, 3 }));
    }

    // --- addBboxPayload (bug-1 regression guard) -------------------------

    @Test void addBboxPayload_bb1AndBb2AreDistinctReferences() {
        // Bug-1 (the shared-tmpArr bug): the previous implementation
        // reused one JSONArray for both keys and bb2's contents stomped
        // bb1. This test fails if a regression reintroduces that.
        org.json.JSONObject payload = new org.json.JSONObject();
        int[] bbox1 = { 10, 20, 100, 200 };
        int[] bbox2 = { 50, 60, 300, 400 };
        MatchResult.addBboxPayload(payload, bbox1, bbox2);
        JSONArray bb1 = payload.getJSONArray("bb1");
        JSONArray bb2 = payload.getJSONArray("bb2");
        org.junit.jupiter.api.Assertions.assertNotSame(bb1, bb2,
            "bb1 and bb2 must be distinct JSONArray references");
        // And actually different contents.
        assertEquals(10, bb1.getJSONArray(0).getInt(0));
        assertEquals(50, bb2.getJSONArray(0).getInt(0));
    }

    @Test void addBboxPayload_buildsDoubleArrayShape() {
        // PairX expects [[x, y, w, h]] for each of bb1 / bb2.
        org.json.JSONObject payload = new org.json.JSONObject();
        MatchResult.addBboxPayload(payload,
            new int[] { 1, 2, 3, 4 }, new int[] { 5, 6, 7, 8 });
        JSONArray bb1 = payload.getJSONArray("bb1");
        assertEquals(1, bb1.length());
        JSONArray inner = bb1.getJSONArray(0);
        assertEquals(4, inner.length());
        assertEquals(1, inner.getInt(0));
        assertEquals(2, inner.getInt(1));
        assertEquals(3, inner.getInt(2));
        assertEquals(4, inner.getInt(3));
    }
}
