package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ecocean.media.MediaAsset;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic coverage of the AcmIdBot reconciliation sweep helpers
 * (spec docs/superpowers/specs/2026-07-01-acmidbot-reconciliation-sweep-design.md):
 * page collection (dedup, bucketing, exhaustion), cursor advancement
 * (wrap-around, maxFixes clamp), and the poisoned-page skip threshold.
 */
class AcmIdBotSweepTest {

    private static MediaAsset asset(int id) {
        // constructor defaults acmId to the asset's own UUID
        return new MediaAsset(id, null, null);
    }

    private static MediaAsset nullAcmAsset(int id) {
        MediaAsset ma = asset(id);

        ma.setAcmId(null); // legacy row: never assigned
        return ma;
    }

    private static MediaAsset invalidAsset(int id) {
        MediaAsset ma = asset(id);

        ma.setIsValidImageForIA(false);
        return ma;
    }

    // ---------- collectSweepPage ----------

    @Test void bucketsNullAcmSeparatelyFromProbeable() {
        List<MediaAsset> in = Arrays.asList(asset(1), nullAcmAsset(2), asset(3));
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 10);

        assertEquals(2, page.acmIdToAssetId.size());
        assertEquals(1, page.nullAcmAssetIds.size());
        assertEquals(Integer.valueOf(2), page.nullAcmAssetIds.get(0));
        assertTrue(page.rawExhausted, "short input should exhaust");
        assertEquals(3, page.lastAssetId);
    }

    @Test void dedupesRepeatedAssets() {
        MediaAsset one = asset(1);
        List<MediaAsset> in = Arrays.asList(one, one, asset(2), one);
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 10);

        assertEquals(2, page.acmIdToAssetId.size());
        assertTrue(page.rawExhausted);
    }

    @Test void skipsKnownInvalidButStillCountsThemTowardPage() {
        List<MediaAsset> in = Arrays.asList(invalidAsset(1), asset(2));
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 10);

        assertEquals(1, page.acmIdToAssetId.size());
        assertEquals(0, page.nullAcmAssetIds.size());
        assertEquals(2, page.lastAssetId);
    }

    @Test void skipsNullAssetsFromDanglingFeatures() {
        List<MediaAsset> in = Arrays.asList(null, asset(2));
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 10);

        assertEquals(1, page.acmIdToAssetId.size());
        assertTrue(page.rawExhausted);
    }

    @Test void pageLimitStopsCollectionAndMarksNotExhausted() {
        List<MediaAsset> in = new ArrayList<MediaAsset>();
        for (int i = 1; i <= 5; i++) in.add(asset(i));
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 3);

        assertEquals(3, page.acmIdToAssetId.size());
        assertFalse(page.rawExhausted, "limit hit before input end: not exhausted");
        assertEquals(3, page.lastAssetId);
    }

    @Test void exactlyFullPageWithNoMoreInputIsExhausted() {
        List<MediaAsset> in = Arrays.asList(asset(1), asset(2), asset(3));
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(in.iterator(), 3);

        assertEquals(3, page.acmIdToAssetId.size());
        assertTrue(page.rawExhausted, "input ended exactly at limit: exhausted");
    }

    @Test void emptyInputIsExhaustedWithSentinelLastId() {
        AcmIdBot.SweepPage page =
            AcmIdBot.collectSweepPage(new ArrayList<MediaAsset>().iterator(), 10);

        assertTrue(page.rawExhausted);
        assertEquals(-1, page.lastAssetId);
        assertEquals(0, page.acmIdToAssetId.size());
        assertEquals(0, page.nullAcmAssetIds.size());
    }

    // ---------- nextCursorAfterSuccess ----------

    @Test void normalPageAdvancesToLastAssetId() {
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(
            Arrays.asList(asset(7), asset(9)).iterator(), 1); // limit -> not exhausted
        assertFalse(page.rawExhausted);
        assertEquals(7, AcmIdBot.nextCursorAfterSuccess(page, false, page.lastAssetId));
    }

    @Test void exhaustionWrapsCursorToZero() {
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(
            Arrays.asList(asset(7)).iterator(), 10);
        assertTrue(page.rawExhausted);
        assertEquals(0, AcmIdBot.nextCursorAfterSuccess(page, false, page.lastAssetId));
    }

    @Test void maxFixesClampBeatsExhaustionWrap() {
        AcmIdBot.SweepPage page = AcmIdBot.collectSweepPage(
            Arrays.asList(asset(7), asset(9)).iterator(), 10);
        assertTrue(page.rawExhausted);
        // cap hit while healing asset 7: resume from 7, do NOT wrap
        assertEquals(7, AcmIdBot.nextCursorAfterSuccess(page, true, 7));
    }

    // ---------- shouldSkipPoisonedPage ----------

    @Test void skipsOnlyAtFailLimit() {
        assertFalse(AcmIdBot.shouldSkipPoisonedPage(1));
        assertFalse(AcmIdBot.shouldSkipPoisonedPage(2));
        assertTrue(AcmIdBot.shouldSkipPoisonedPage(3));
        assertTrue(AcmIdBot.shouldSkipPoisonedPage(4));
    }

    // ---------- sendConfirmedAcmId ----------

    private static org.json.JSONObject sendRtnWithUuids(String... uuids) {
        org.json.JSONArray resp = new org.json.JSONArray();

        for (String u : uuids) {
            org.json.JSONObject fancy = new org.json.JSONObject();
            fancy.put("__UUID__", u);
            resp.put(fancy);
        }
        org.json.JSONObject rtn = new org.json.JSONObject();
        rtn.put("response", resp);
        org.json.JSONArray batches = new org.json.JSONArray();
        batches.put(rtn);
        org.json.JSONObject all = new org.json.JSONObject();
        all.put("batchResults", batches);
        return all;
    }

    @Test void confirmsWhenResponseContainsExpectedAcmId() {
        assertTrue(AcmIdBot.sendConfirmedAcmId(sendRtnWithUuids("abc", "def"), "def"));
    }

    @Test void doesNotConfirmWhenUuidAbsentOrBatchEmpty() {
        assertFalse(AcmIdBot.sendConfirmedAcmId(sendRtnWithUuids("abc"), "def"));
        org.json.JSONObject all = new org.json.JSONObject();
        org.json.JSONArray batches = new org.json.JSONArray();
        batches.put("EMPTY BATCH");
        all.put("batchResults", batches);
        assertFalse(AcmIdBot.sendConfirmedAcmId(all, "def"));
    }

    @Test void doesNotConfirmOnNulls() {
        assertFalse(AcmIdBot.sendConfirmedAcmId(null, "abc"));
        assertFalse(AcmIdBot.sendConfirmedAcmId(sendRtnWithUuids("abc"), null));
    }
}
