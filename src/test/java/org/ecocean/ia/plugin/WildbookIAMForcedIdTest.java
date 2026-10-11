package org.ecocean.ia.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.RestClient;
import org.ecocean.cache.QueryCacheFactory;
import org.ecocean.ia.IA;
import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterOutcome;
import org.ecocean.ia.plugin.WildbookIAM.WbiaRegisterRequest;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.MediaAsset;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;

/** Spec §4.2.5: the poller and the identify-time registration force the same identifier end to end. */
class WildbookIAMForcedIdTest {

    static final String ANN_ID = "11111111-1111-1111-1111-111111111111";
    static final String ACM_ID = "22222222-2222-2222-2222-222222222222";
    static final String MA_ACM = "33333333-3333-3333-3333-333333333333";

    static WbiaRegisterRequest dto(String annotationId, String acmId) {
        return new WbiaRegisterRequest(annotationId, acmId, MA_ACM, new int[] { 1, 2, 3, 4 },
            0.0d, "guitarfish", "____", "https://x/img.jpg", null, null, null);
    }

    static JSONObject echo(String uuid) {
        return new JSONObject().put("status", new JSONObject().put("success", true))
            .put("response", new JSONArray().put(WildbookIAM.toFancyUUID(uuid)));
    }

    interface Body { void run() throws Exception; }

    static Answer<JSONObject> reply(final JSONObject r) {
        return inv -> r;
    }

    // IA.getProperty answers the two WBIA endpoints (plain mock); WildbookIAM is a PARTIAL static
    // mock through a custom Answer (the two id-list fetches are answered, everything else —
    // toFancyUUID, buildForcedRequestMap, validateForcedResponse, validMediaAsset — runs real);
    // RestClient.post is answered by `post`; the query cache is a plain mock.
    static void withWbia(List<String> knownAnnots, List<String> knownImages, Answer<JSONObject> post, Body body)
    throws Exception {
        try (MockedStatic<IA> ia = mockStatic(IA.class);
             MockedStatic<WildbookIAM> iam = mockStatic(WildbookIAM.class, inv -> {
                 String m = inv.getMethod().getName();
                 if (m.equals("iaAnnotationIdsStrict")) return knownAnnots;
                 if (m.equals("iaImageIdsStrict")) return knownImages;
                 return inv.callRealMethod();
             });
             MockedStatic<RestClient> rest = mockStatic(RestClient.class);
             MockedStatic<QueryCacheFactory> qc = mockStatic(QueryCacheFactory.class)) {
            ia.when(() -> IA.getProperty(anyString(), eq("IBEISIARestUrlAddAnnotations"))).thenReturn("http://wbia/api/annot/json/");
            ia.when(() -> IA.getProperty(anyString(), eq("IBEISIARestUrlAddImages"))).thenReturn("http://wbia/api/image/json/");
            rest.when(() -> RestClient.post(any(URL.class), any(JSONObject.class))).thenAnswer(post);
            body.run();
        }
    }

    @Test void forcedIdIsTheAcmIdWhenPresentElseTheAnnotationId() {
        assertEquals(ACM_ID, dto(ANN_ID, ACM_ID).forcedId);
        assertEquals(ANN_ID, dto(ANN_ID, null).forcedId);
        assertEquals(ANN_ID, dto(ANN_ID, "  ").forcedId);
    }

    @Test void presenceIsCheckedOnTheForcedIdOnly() throws Exception {
        WildbookIAM plugin = new WildbookIAM("context0");
        // present under the acmId: already registered, no POST
        withWbia(Arrays.asList(ACM_ID), Arrays.asList(MA_ACM), reply(echo(ACM_ID)), () -> {
            assertEquals(WbiaRegisterOutcome.REGISTERED_ALREADY_PRESENT, plugin.registerOneByDto(dto(ANN_ID, ACM_ID)));
        });
        // present only under the annotation id: NOT evidence the acmId is registered -> POST the acmId
        withWbia(Arrays.asList(ANN_ID), Arrays.asList(MA_ACM), reply(echo(ACM_ID)), () -> {
            assertEquals(WbiaRegisterOutcome.REGISTERED_OK, plugin.registerOneByDto(dto(ANN_ID, ACM_ID)));
        });
    }

    @Test void postBodyCarriesTheAcmIdAndAMismatchedEchoIsRejected() throws Exception {
        WildbookIAM plugin = new WildbookIAM("context0");
        final JSONObject[] captured = new JSONObject[1];
        withWbia(new ArrayList<String>(), Arrays.asList(MA_ACM), inv -> {
            captured[0] = inv.getArgument(1);
            return echo(ANN_ID);   // WBIA echoes the wrong id
        }, () -> {
            assertEquals(WbiaRegisterOutcome.RESPONSE_BAD, plugin.registerOneByDto(dto(ANN_ID, ACM_ID)));
            JSONObject sent = captured[0].getJSONArray("annot_uuid_list").getJSONObject(0);
            assertEquals(ACM_ID, WildbookIAM.fromFancyUUID(sent));
        });
        withWbia(new ArrayList<String>(), Arrays.asList(MA_ACM), reply(echo(ACM_ID)), () -> {
            assertEquals(WbiaRegisterOutcome.REGISTERED_OK, plugin.registerOneByDto(dto(ANN_ID, ACM_ID)));
        });
    }

    // ---- wbiaEligibilityReason ----

    static MediaAsset validAsset() throws Exception {
        MediaAsset ma = mock(MediaAsset.class);
        when(ma.getAcmId()).thenReturn(MA_ACM);
        when(ma.isMimeTypeMajor("image")).thenReturn(true);
        when(ma.getWidth()).thenReturn(100.0);
        when(ma.getHeight()).thenReturn(100.0);
        when(ma.webURL()).thenReturn(new URL("http://x/img.jpg"));
        when(ma.isValidImageForIA()).thenReturn(null);
        return ma;
    }

    static Annotation validAnnotation(MediaAsset ma) {
        Annotation ann = mock(Annotation.class);
        when(ann.getId()).thenReturn(ANN_ID);
        when(ann.getAcmId()).thenReturn(ACM_ID);
        when(ann.getMediaAsset()).thenReturn(ma);
        return ann;
    }

    @Test void eligibilityReasonIsNullForAWellFormedPendingAnnotation() throws Exception {
        MediaAsset ma = validAsset();
        Annotation ann = validAnnotation(ma);
        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class)) {
            ibeis.when(() -> IBEISIA.validForIdentification(any(Annotation.class))).thenReturn(true);
            assertNull(WildbookIAM.wbiaEligibilityReason(ann));
        }
    }

    @Test void eligibilityReasonNamesEachFailure() throws Exception {
        assertEquals("null annotation", WildbookIAM.wbiaEligibilityReason(null));
        Annotation noAsset = mock(Annotation.class);
        when(noAsset.getId()).thenReturn(ANN_ID);
        assertEquals("missing media asset", WildbookIAM.wbiaEligibilityReason(noAsset));

        try (MockedStatic<IBEISIA> ibeis = mockStatic(IBEISIA.class)) {
            ibeis.when(() -> IBEISIA.validForIdentification(any(Annotation.class))).thenReturn(false);
            assertTrue(WildbookIAM.wbiaEligibilityReason(validAnnotation(validAsset())).startsWith("validForIdentification"));
            ibeis.when(() -> IBEISIA.validForIdentification(any(Annotation.class))).thenReturn(true);

            MediaAsset corrupt = validAsset();
            when(corrupt.isValidImageForIA()).thenReturn(Boolean.FALSE);
            assertTrue(WildbookIAM.wbiaEligibilityReason(validAnnotation(corrupt)).startsWith("MediaAsset.isValidImageForIA"));

            MediaAsset notImage = validAsset();
            when(notImage.isMimeTypeMajor("image")).thenReturn(false);
            assertTrue(WildbookIAM.wbiaEligibilityReason(validAnnotation(notImage)).startsWith("MediaAsset failed validMediaAsset"));

            Annotation badAcm = validAnnotation(validAsset());
            when(badAcm.getAcmId()).thenReturn("not-a-uuid");
            assertTrue(WildbookIAM.wbiaEligibilityReason(badAcm).startsWith("annotation acmId"));

            MediaAsset noAcm = validAsset();
            when(noAcm.getAcmId()).thenReturn(null);
            assertTrue(WildbookIAM.wbiaEligibilityReason(validAnnotation(noAcm)).startsWith("media asset acmId"));
        }
    }

    // ---- registerImageForced ----

    @Test void registerImageForcedSucceedsWhenPresentOrWhenWbiaEchoesTheAcmId() throws Exception {
        WildbookIAM plugin = new WildbookIAM("context0");
        MediaAsset ma = validAsset();
        withWbia(new ArrayList<String>(), Arrays.asList(MA_ACM), reply(null), () -> {
            assertTrue(plugin.registerImageForced(ma), "already present: no POST needed");
        });
        withWbia(new ArrayList<String>(), new ArrayList<String>(), reply(echo(MA_ACM)), () -> {
            assertTrue(plugin.registerImageForced(ma));
        });
        withWbia(new ArrayList<String>(), new ArrayList<String>(), reply(echo(ACM_ID)), () -> {
            assertFalse(plugin.registerImageForced(ma), "a mismatched echo is a failure");
        });
        MediaAsset noAcm = validAsset();
        when(noAcm.getAcmId()).thenReturn(null);
        assertFalse(plugin.registerImageForced(noAcm));
        assertFalse(plugin.registerImageForced(null));
    }
}
