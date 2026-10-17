package org.ecocean.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ecocean.Annotation;
import org.ecocean.cache.QueryCacheFactory;
import org.ecocean.ia.IA;
import org.ecocean.ia.plugin.WildbookIAM;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import javax.jdo.PersistenceManager;
import org.mockito.Mockito;
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
        final List<String> sentAnns = new ArrayList<String>();   // non-forced sendAnnotations
        final List<String> sentAssets = new ArrayList<String>(); // non-forced sendMediaAssets
        final Set<String> failAcmIds = new HashSet<String>();    // forced annotation POSTs that fail

        FakePlugin() { super("context0"); }

        @Override public boolean registerImageForced(MediaAsset ma) {
            imageCalls.add(ma.getAcmId());
            return imageOk;
        }

        @Override public boolean sendAnnotationForcedByAcmId(Annotation ann, Shepherd sh) {
            annCalls.add(ann.getAcmId());
            return annOk && !failAcmIds.contains(ann.getAcmId());
        }

        @Override public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst, Shepherd sh) {
            for (Annotation a : anns) sentAnns.add(a.getId());
            return new JSONObject();
        }

        @Override public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst) {
            for (MediaAsset m : mas) sentAssets.add(m.getAcmId());
            return new JSONObject();
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

    /** Flag-stateful: setWbiaRegistered is visible to getWbiaRegistered. */
    static Annotation statefulAnn(String id, String acmId, Boolean flag, MediaAsset ma) {
        Annotation a = mock(Annotation.class);
        final Boolean[] f = { flag };
        when(a.getId()).thenReturn(id);
        when(a.getAcmId()).thenReturn(acmId);
        when(a.getWbiaRegistered()).thenAnswer(inv -> f[0]);
        doAnswer(inv -> {
            f[0] = inv.getArgument(0);
            return null;
        }).when(a).setWbiaRegistered(any());
        when(a.getMediaAsset()).thenReturn(ma);
        return a;
    }

    static final Set<String> INVALID = new HashSet<String>();      // ids validForIdentification rejects
    static final List<String> IDENTIFY_CALLS = new ArrayList<String>();

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
        INVALID.clear();
        IDENTIFY_CALLS.clear();
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("getPluginInstance")) return plugin;
                 if (m.equals("validForIdentification"))
                     return !INVALID.contains(((Annotation)inv.getArgument(0)).getId());
                 if (m.equals("log")) return null;
                 if (m.equals("isIAPrimed")) return true;
                 if (m.equals("sendIdentify")) {
                     IDENTIFY_CALLS.add("called");
                     return new JSONObject().put("status", new JSONObject().put("success", false).put("message", "stub"));
                 }
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
             MockedStatic<IA> ia = mockStatic(IA.class);
             // the forced repair invalidates the iaAnnotationIds cache: never the real cache/PMF
             MockedStatic<QueryCacheFactory> cache = mockStatic(QueryCacheFactory.class)) {
            CACHE = cache;
            try {
                body.run();
            } finally {
                CACHE = null;
            }
        }
    }

    /** The QueryCacheFactory static mock of the running withPlugin body (for verification). */
    static MockedStatic<QueryCacheFactory> CACHE = null;

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

    /** A blank pending acmId takes the annotation id (the ml-service persistence convention). */
    @Test void pendingAnnotationWithABlankAcmIdIsRegisteredUnderItsId() throws Exception {
        FakePlugin plugin = new FakePlugin();
        Annotation q = statefulAnn(ACM_Q, null, Boolean.FALSE, asset(MA_UUID));
        final String[] acm = { "" };
        when(q.getAcmId()).thenAnswer(inv -> acm[0]);
        doAnswer(inv -> {
            acm[0] = inv.getArgument(0);
            return null;
        }).when(q).setAcmId(anyString());
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            JSONObject report = new JSONObject();
            IBEISIA.sendAnnotationsAsNeeded(new ArrayList<Annotation>(Arrays.asList(q)),
                new ArrayList<Annotation>(), sh, report);
            assertEquals(ACM_Q, acm[0], "the acmId is the annotation id");
            assertEquals(Arrays.asList(ACM_Q), plugin.annCalls, "registered under its id");
            assertEquals(Boolean.TRUE, q.getWbiaRegistered());
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
            CACHE.verify(() -> QueryCacheFactory.safeInvalidate(anyString(), eq("iaAnnotationIds")));
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

    // ---- fix round 1 ----

    @Test void legacyFilterAndNonForcedSendAreExercised() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.legacyKnown = Arrays.asList(ACM_N);
        Annotation bad = ann("bad", "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee", null, asset(MA_UUID));
        Annotation nullQ = ann("nullQ", "ffffffff-ffff-ffff-ffff-ffffffffffff", null, asset(MA_UUID));
        Annotation trueT = ann("trueT", "99999999-9999-9999-9999-999999999999", Boolean.TRUE, asset(MA_UUID));
        Shepherd sh = shepherd();
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            INVALID.add("bad");
            ArrayList<Annotation> targets = IBEISIA.sendAnnotationsAsNeeded(
                new ArrayList<Annotation>(Arrays.asList(bad, nullQ)),
                new ArrayList<Annotation>(Arrays.asList(trueT)), sh, null);
            assertEquals(Arrays.asList(trueT), targets);
            assertTrue(plugin.sentAnns.contains("nullQ"), "null-flag query reaches the non-forced send");
            assertTrue(plugin.sentAnns.contains("trueT"), "TRUE-flag target reaches the non-forced send");
            assertFalse(plugin.sentAnns.contains("bad"), "invalid legacy query is filtered out");
            assertEquals(Arrays.asList(MA_UUID, MA_UUID), plugin.sentAssets);
            assertTrue(plugin.annCalls.isEmpty(), "never the forced path");
            assertTrue(plugin.imageCalls.isEmpty(), "never the forced path");
        });
    }

    @Test void allPendingTargetsDroppedMakesAnEmptyListAndFailsBeginIdentify() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.legacyKnown = Arrays.asList(ACM_N);
        Annotation q = ann("q", ACM_N, Boolean.TRUE, asset(MA_UUID));
        Annotation t = ann("t", ACM_T, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = mock(Shepherd.class);
        when(sh.getContext()).thenReturn("context0");
        when(sh.getPM()).thenReturn(mock(PersistenceManager.class, Mockito.RETURNS_DEEP_STUBS));
        withPlugin(plugin, new ArrayList<String>(), "missing media asset", () -> {
            assertTrue(IBEISIA.sendAnnotationsAsNeeded(new ArrayList<Annotation>(Arrays.asList(q)),
                new ArrayList<Annotation>(Arrays.asList(t)), sh, null).isEmpty());
            JSONObject res = IBEISIA.beginIdentifyAnnotations(new ArrayList<Annotation>(Arrays.asList(q)),
                new ArrayList<Annotation>(Arrays.asList(t)), null, null, sh, null, "http://x", false);
            assertFalse(res.getBoolean("success"));
            assertTrue(res.getString("error").contains("all target annotations were dropped"));
            assertEquals("t", res.getJSONObject("sendAnnotationsAsNeeded").getJSONArray("droppedTargets").getString(0));
            assertTrue(IDENTIFY_CALLS.isEmpty(), "sendIdentify must not run with no targets");
        });
    }

    @Test void iaCheckMissingMixedOutcomesNeverRetryInEitherOrder() throws Exception {
        for (boolean failFirst : new boolean[] { true, false }) {
            FakePlugin plugin = new FakePlugin();
            Annotation a = statefulAnn("a", ACM_Q, Boolean.FALSE, asset(MA_UUID));
            Annotation b = statefulAnn("b", ACM_T, Boolean.FALSE, asset(MA_UUID));
            plugin.failAcmIds.add(failFirst ? ACM_Q : ACM_T);
            Shepherd sh = shepherd();
            when(sh.getAnnotationsWithACMId(ACM_Q)).thenReturn(new ArrayList<Annotation>(Arrays.asList(a)));
            when(sh.getAnnotationsWithACMId(ACM_T)).thenReturn(new ArrayList<Annotation>(Arrays.asList(b)));
            JSONObject rep = new JSONObject()
                .put("status", new JSONObject().put("code", 600).put("success", false).put("message", "missing"))
                .put("response", new JSONObject().put("missing_annot_uuid_list", new JSONArray()
                    .put(WildbookIAM.toFancyUUID(ACM_Q)).put(WildbookIAM.toFancyUUID(ACM_T))));
            withPlugin(plugin, new ArrayList<String>(), null, () -> {
                assertFalse(IBEISIA.iaCheckMissing(rep, "context0", sh), "one failed repair means no retry");
                assertEquals(2, plugin.annCalls.size(), "both attempted");
            });
        }
    }

    @Test void iaCheckMissingSecondReportOfARepairedAnnotationDoesNotRetry() throws Exception {
        FakePlugin plugin = new FakePlugin();
        plugin.legacyKnown = Arrays.asList(ACM_N);
        Annotation a = statefulAnn("a", ACM_Q, Boolean.FALSE, asset(MA_UUID));
        Shepherd sh = shepherd();
        when(sh.getAnnotationsWithACMId(ACM_Q)).thenReturn(new ArrayList<Annotation>(Arrays.asList(a)));
        withPlugin(plugin, new ArrayList<String>(), null, () -> {
            assertTrue(IBEISIA.iaCheckMissing(missing(ACM_Q), "context0", sh));
            assertEquals(Boolean.TRUE, a.getWbiaRegistered());
            assertEquals(1, plugin.annCalls.size());
            assertFalse(IBEISIA.iaCheckMissing(missing(ACM_Q), "context0", sh), "now-TRUE annotation takes the legacy branch");
            assertEquals(1, plugin.annCalls.size(), "no new forced call");
        });
    }
}
