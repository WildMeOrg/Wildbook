package org.ecocean.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.ia.IA;
import org.ecocean.ia.plugin.WildbookIAM;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Spec §4.2.5: pending annotations are registered under their acmId at identify time. */
class IBEISIASendAnnotationsAsNeededTest {

    static final String ACM_Q = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    static final String ACM_T = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    static final String ACM_N = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    static final String MA_UUID = "dddddddd-dddd-dddd-dddd-dddddddddddd";

    /** Records forced calls; answers the legacy id lists. */
    static final class FakePlugin extends WildbookIAM {
        final List<String> imageCalls = new ArrayList<String>();
        final List<String> annCalls = new ArrayList<String>();
        boolean imageOk = true;
        boolean annOk = true;
        List<String> legacyKnown = new ArrayList<String>();

        FakePlugin() { super("context0"); }

        @Override public boolean registerImageForced(MediaAsset ma) {
            imageCalls.add(ma.getAcmId());
            return imageOk;
        }

        @Override public boolean sendAnnotationForcedByAcmId(Annotation ann, Shepherd sh) {
            annCalls.add(ann.getAcmId());
            return annOk;
        }

        @Override public List<String> iaAnnotationIds() { return legacyKnown; }
        @Override public List<String> iaImageIds() { return legacyKnown; }
    }

    static Annotation ann(String id, String acmId, Boolean flag, MediaAsset ma) {
        Annotation a = mock(Annotation.class);
        when(a.getId()).thenReturn(id);
        when(a.getAcmId()).thenReturn(acmId);
        when(a.getWbiaRegistered()).thenReturn(flag);
        when(a.getMediaAsset()).thenReturn(ma);
        return a;
    }

    static MediaAsset asset(String acmId) {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.getAcmId()).thenReturn(acmId);
        when(ma.getUUID()).thenReturn(MA_UUID);
        return ma;
    }

    static Shepherd shepherd() {
        Shepherd sh = mock(Shepherd.class);
        when(sh.getContext()).thenReturn("context0");
        return sh;
    }

    interface Body { void run() throws Exception; }

    // Partial static mocks through custom Answers: IBEISIA.getPluginInstance -> the fake and
    // validForIdentification -> true (the legacy filter; the real one needs bbox features);
    // WildbookIAM.iaAnnotationIdsStrict -> the test's list (null = the fetch throws IOException)
    // and wbiaEligibilityReason -> the test's reason (null = eligible); everything else runs real.
    // IA is a plain mock: getProperty -> null, so the legacy __sendAnnotations throws and is
    // swallowed exactly as in production without WBIA configured.
    static void withPlugin(FakePlugin plugin, List<String> knownForced, String eligibilityReason, Body body)
    throws Exception {
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("getPluginInstance")) return plugin;
                 if (m.equals("validForIdentification")) return true;
                 return inv.callRealMethod();
             });
             MockedStatic<WildbookIAM> iam = mockStatic(WildbookIAM.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("iaAnnotationIdsStrict")) {
                     if (knownForced == null) throw new IOException("WBIA unreachable");
                     return knownForced;
                 }
                 if (m.equals("wbiaEligibilityReason")) {
                     if ("THROW".equals(eligibilityReason)) throw new IllegalStateException("eligibility blew up");
                     return eligibilityReason;
                 }
                 return inv.callRealMethod();
             });
             MockedStatic<IA> ia = mockStatic(IA.class)) {
            body.run();
        }
    }

    @Test void pendingQueryIsRegisteredUnderItsAcmIdAndFlaggedTrue() throws Exception {
        FakePlugin plugin = new FakePlugin();
        MediaAsset ma = asset(MA_UUID);
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, ma);
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            JSONObject report = new JSONObject();
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(q)), new ArrayList<Annotation>(), sh, report);
            assertTrue(targets.isEmpty());
            assertEquals(Arrays.asList(MA_UUID), plugin.imageCalls);
            assertEquals(Arrays.asList(ACM_Q), plugin.annCalls);
            verify(q).setWbiaRegistered(Boolean.TRUE);
            assertEquals("q", report.getJSONArray("forcedRegistered").getString(0));
            assertEquals(0, report.getJSONArray("droppedTargets").length());
        });
    }

    @Test void pendingQueryThatCannotBeRegisteredFailsTheJob() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.annOk = false;
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            assertThrows(IOException.class, () -> IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(q)), null, sh, null));
            verify(q, never()).setWbiaRegistered(Boolean.TRUE);
        });
        // an ineligible pending query fails the job even when a valid legacy query is in the same call
        FakePlugin eligible = new FakePlugin();
        eligible.legacyKnown = Arrays.asList(ACM_N);
        Annotation legacyQuery = ann("n", ACM_N, null, asset(MA_UUID));
        withPlugin(eligible, new ArrayList<String>(), "missing media asset", () -> {
            assertThrows(IOException.class, () -> IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(legacyQuery, q)), null, sh, null));
            assertTrue(eligible.annCalls.isEmpty());
        });
    }

    @Test void idListFetchFailureFailsAPendingQueryButOnlyDropsAPendingTarget() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.legacyKnown = Arrays.asList(ACM_N);
        Annotation legacyQuery = ann("n", ACM_N, Boolean.TRUE, asset(MA_UUID));
        Annotation t = ann("t", ACM_T, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, null /* the fetch throws */, null, () -> {
            JSONObject report = new JSONObject();
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(legacyQuery)), new ArrayList<Annotation>(Arrays.asList(t)), sh, report);
            assertTrue(targets.isEmpty(), "the pending target is dropped, the job goes on");
            assertEquals("t", report.getJSONArray("droppedTargets").getString(0));
            Annotation pendingQuery = ann("p", ACM_Q, Boolean.FALSE, asset(MA_UUID));
            assertThrows(IOException.class, () -> IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(pendingQuery)), null, sh, null));
        });
    }

    @Test void runtimeFailureWhileRegisteringAPendingQueryBecomesAnIOException() throws Exception {
        FakePlugin plugin = new FakePlugin();
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), "THROW", () -> {
            IOException ex = assertThrows(IOException.class, () -> IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(q)), null, sh, null));
            assertTrue(ex.getMessage().contains("q"), "names the annotation");
            assertTrue(ex.getMessage().contains("eligibility blew up"), "carries the cause");
            assertTrue(plugin.annCalls.isEmpty());
        });
    }

    @Test void threeArgumentOverloadDelegatesWithoutAReport() throws Exception {
        FakePlugin plugin = new FakePlugin();
        Annotation t = ann("t", ACM_T, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(), new ArrayList<Annotation>(Arrays.asList(t)), sh);
            assertEquals(Arrays.asList(t), targets);
            assertEquals(Arrays.asList(ACM_T), plugin.annCalls);
        });
    }

    @Test void ineligiblePendingTargetIsDroppedAndTheJobContinues() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.legacyKnown = Arrays.asList(ACM_N);
        Annotation q = ann("q", ACM_N, null, asset(MA_UUID));          // legacy query, already known
        Annotation t = ann("t", ACM_T, Boolean.FALSE, null);            // pending target without an asset
        Annotation keep = ann("k", ACM_N, Boolean.TRUE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), "missing media asset", () -> {
            JSONObject report = new JSONObject();
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(q)), new ArrayList<Annotation>(Arrays.asList(t, keep)), sh, report);
            assertEquals(Arrays.asList(keep), targets);
            assertEquals("t", report.getJSONArray("droppedTargets").getString(0));
            assertTrue(plugin.annCalls.isEmpty());
        });
    }

    @Test void pendingAnnotationAlreadyPresentUnderItsAcmIdIsFlaggedWithoutAPost() throws Exception {
        FakePlugin plugin = new FakePlugin();
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, Arrays.asList(ACM_Q), null, () -> {
            IBEISIA.sendAnnotationsAsNeeded(new ArrayList<Annotation>(Arrays.asList(q)), null, sh, null);
            assertTrue(plugin.annCalls.isEmpty());
            assertTrue(plugin.imageCalls.isEmpty());
            verify(q).setWbiaRegistered(Boolean.TRUE);
        });
    }

    @Test void assetWithoutAcmIdIsMintedFromItsUuidBeforeTheImageIsForced() throws Exception {
        FakePlugin plugin = new FakePlugin();
        MediaAsset ma = mock(MediaAsset.class);   // stateful: setAcmId is visible to getAcmId
        final String[] acm = { null };
        when(ma.getAcmId()).thenAnswer(inv -> acm[0]);
        when(ma.getUUID()).thenReturn(MA_UUID);
        doAnswer(inv -> {
            acm[0] = inv.getArgument(0);
            return null;
        }).when(ma).setAcmId(anyString());
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, ma);
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            IBEISIA.sendAnnotationsAsNeeded(new ArrayList<Annotation>(Arrays.asList(q)), null, sh, null);
            verify(ma).setAcmId(MA_UUID);
            assertEquals(Arrays.asList(MA_UUID), plugin.imageCalls, "the image is forced under the minted acmId");
            assertEquals(Arrays.asList(ACM_Q), plugin.annCalls);
        });
    }

    @Test void legacyAnnotationsTakeTodaysPathUntouched() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.legacyKnown = Arrays.asList(ACM_N);
        Annotation n = ann("n", ACM_N, null, asset(MA_UUID));
        Annotation t = ann("t", ACM_N, Boolean.TRUE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            JSONObject report = new JSONObject();
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(n)), new ArrayList<Annotation>(Arrays.asList(t)), sh, report);
            assertEquals(Arrays.asList(t), targets);
            assertTrue(plugin.annCalls.isEmpty());
            assertEquals(2, report.getJSONObject("legacy").getInt("numAnnotsTotal"));
            verify(n, never()).setWbiaRegistered(any());
        });
    }

    @Test void pendingOnlyRegistrationWorksAgainstAnEmptyWbia() throws Exception {
        FakePlugin plugin = new FakePlugin();   // legacyKnown empty: the legacy path would throw if reached
        Annotation q = ann("q", ACM_Q, Boolean.FALSE, asset(MA_UUID));
        Annotation t = ann("t", ACM_T, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(q)), new ArrayList<Annotation>(Arrays.asList(t)), sh, null);
            assertEquals(Arrays.asList(t), targets);
            assertEquals(Arrays.asList(ACM_Q, ACM_T), plugin.annCalls);
        });
    }

    @Test void nullTargetsStayNull() throws Exception {
        Shepherd sh = shepherd();
        withPlugin(new FakePlugin(), new ArrayList<String>(), null, () -> {
            assertNull(IBEISIA.sendAnnotationsAsNeeded(new ArrayList<Annotation>(), null, sh, null));
        });
    }

    // ---- iaCheckMissing ----

    static JSONObject missing(String acmId) {
        return new JSONObject()
            .put("status", new JSONObject().put("code", 600).put("success", false).put("message", "missing"))
            .put("response", new JSONObject().put("missing_annot_uuid_list",
                new JSONArray().put(WildbookIAM.toFancyUUID(acmId))));
    }

    @Test void iaCheckMissingForcedRepairRetriesOnlyWhenEveryForcedAttemptSucceeded() throws Exception {
        FakePlugin plugin = new FakePlugin();
        Annotation pending = ann("p", ACM_Q, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        when(sh.getAnnotationsWithACMId(ACM_Q)).thenReturn(new ArrayList<Annotation>(Arrays.asList(pending)));
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            assertTrue(IBEISIA.iaCheckMissing(missing(ACM_Q), "context0", sh));
            assertEquals(Arrays.asList(ACM_Q), plugin.annCalls);
        });
        FakePlugin failing = new FakePlugin();
        failing.annOk = false;
        withPlugin(failing, new ArrayList<String>(), null, () -> {
            assertFalse(IBEISIA.iaCheckMissing(missing(ACM_Q), "context0", sh));
        });
    }

    @Test void iaCheckMissingLegacyBranchIsUnchanged() throws Exception {
        FakePlugin plugin = new FakePlugin();
        Annotation legacy = ann("n", ACM_N, null, asset(MA_UUID));
        Shepherd sh = shepherd();
        when(sh.getAnnotationsWithACMId(ACM_N)).thenReturn(new ArrayList<Annotation>(Arrays.asList(legacy)));
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            // IA.getProperty is a plain static mock here -> null -> __sendAnnotations throws and is
            // swallowed, exactly as before: no retry, no forced call
            assertFalse(IBEISIA.iaCheckMissing(missing(ACM_N), "context0", sh));
            assertTrue(plugin.annCalls.isEmpty());
        });
        assertFalse(IBEISIA.iaCheckMissing(null, "context0", sh));
        assertFalse(IBEISIA.iaCheckMissing(new JSONObject().put("status", new JSONObject().put("code", 200)), "context0", sh));
    }
}
