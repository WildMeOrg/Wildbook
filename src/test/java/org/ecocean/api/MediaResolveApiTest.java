package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Annotation;
import org.ecocean.User;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.URLAssetStore;
import org.ecocean.media.YouTubeAssetStore;
import org.ecocean.media.LocalAssetStore;
import org.ecocean.security.WildbookTokenAuthenticationFilter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class MediaResolveApiTest {

    @Test void scaleBbox_identityWhenSameDims() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {10, 20, 30, 40}, 100, 200, 100, 200);
        assertArrayEquals(new int[] {10, 20, 30, 40}, out, "same src/dst dims must be identity");
    }

    @Test void scaleBbox_halfScaleDownscales() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {100, 200, 300, 400}, 1000, 1000, 500, 500);
        assertArrayEquals(new int[] {50, 100, 150, 200}, out, "0.5x scale halves every component");
    }

    @Test void scaleBbox_clampsOverflowToDerivative() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {900, 0, 400, 100}, 1000, 1000, 500, 500);
        assertArrayEquals(new int[] {450, 0, 50, 50}, out, "overflow width/height clamped to derivative bounds");
    }

    @Test void scaleBbox_nullOnBadInput() {
        assertNull(MediaResolveApi.scaleBbox(null, 100, 100, 50, 50), "null src -> null");
        assertNull(MediaResolveApi.scaleBbox(new int[] {1, 2, 3}, 100, 100, 50, 50), "short src -> null");
        assertNull(MediaResolveApi.scaleBbox(new int[] {0, 0, 10, 10}, 0, 100, 50, 50), "zero src dim -> null");
        assertNull(MediaResolveApi.scaleBbox(new int[] {0, 0, 10, 10}, 100, 100, 0, 50), "zero dst dim -> null");
    }

    @Test void scaleBbox_negativeOriginClampsCornerAndShrinksWidth() {
        // src bbox starts at x=-10; clamping the LEFT corner to 0 must shrink width to 10, not keep 20.
        int[] out = MediaResolveApi.scaleBbox(new int[] {-10, 0, 20, 20}, 100, 100, 100, 100);
        assertArrayEquals(new int[] {0, 0, 10, 20}, out, "negative origin clamps corner and shrinks width");
    }

    @Test void scaleBbox_fullyOutsideReturnsNull() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {-50, -50, 20, 20}, 100, 100, 100, 100);
        assertNull(out, "a box entirely outside the image collapses to <1px -> null (omit)");
    }

    @Test void scaleBbox_asymmetricScaleAppliedPerAxis() {
        // sx=2 on X, sy=0.5 on Y — a single-factor bug would produce wrong Y values
        int[] out = MediaResolveApi.scaleBbox(new int[] {10, 100, 50, 200}, 100, 1000, 200, 500);
        assertArrayEquals(new int[] {20, 50, 100, 100}, out, "x and y must use their own scale factor");
    }

    @Test void scaleBbox_nullWhenScaledRegionVanishes() {
        int[] out = MediaResolveApi.scaleBbox(new int[] {0, 0, 1, 1}, 10000, 10000, 5, 5);
        assertNull(out, "a region that rounds to <1px in the derivative -> null (omit)");
    }

    private MediaAsset child(String label, boolean urlStore) {
        MediaAsset ma = mock(MediaAsset.class);
        ArrayList<String> labels = new ArrayList<>();
        labels.add(label);
        when(ma.getLabels()).thenReturn(labels);
        when(ma.hasLabel(label)).thenReturn(true);
        when(ma.getStore()).thenReturn(urlStore ? mock(URLAssetStore.class) : mock(LocalAssetStore.class));
        return ma;
    }

    private Annotation annWithSource(MediaAsset src) {
        Annotation ann = mock(Annotation.class);
        when(ann.getMediaAsset()).thenReturn(src);
        return ann;
    }

    @Test void selectSafeDerivative_prefersMaster() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset master = child("_master", false);
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(master);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        MediaAsset out = MediaResolveApi.selectSafeDerivative(annWithSource(src), sh);
        assertSame(master, out, "a _master child must be selected first");
    }

    @Test void selectSafeDerivative_fallsBackToMidWhenNoMaster() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(null);
        MediaAsset mid = child("_mid", false);
        ArrayList<MediaAsset> mids = new ArrayList<>(); mids.add(mid);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(mids);
        MediaAsset out = MediaResolveApi.selectSafeDerivative(annWithSource(src), sh);
        assertSame(mid, out, "must fall back to _mid when _master is absent (bestSafeAsset bug bypassed)");
    }

    @Test void selectSafeDerivative_nullWhenNoSafeDerivative() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(null);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(null);
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "no _master/_mid -> null (omit)");
    }

    @Test void selectSafeDerivative_rejectsUrlAssetStoreSource() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(URLAssetStore.class));
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "URLAssetStore source (external original) must be rejected");
    }

    @Test void selectSafeDerivative_skipsUrlStoreChildren() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset urlChild = child("_master", true); // _master label but URLAssetStore
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(urlChild);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(null);
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "a non-local (URLAssetStore) child must be skipped");
    }

    @Test void selectSafeDerivative_rejectsYouTubeStoreSource() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(YouTubeAssetStore.class));
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "YouTubeAssetStore source (webURL is a watch page, not image bytes) must be rejected");
    }

    @Test void selectSafeDerivative_skipsMasterAlsoLabeledOriginal_fallsBackToMid() {
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset masterButOriginal = child("_master", false);
        when(masterButOriginal.hasLabel("_original")).thenReturn(true); // also carries _original
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(masterButOriginal);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        MediaAsset mid = child("_mid", false);
        ArrayList<MediaAsset> mids = new ArrayList<>(); mids.add(mid);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(mids);
        assertSame(mid, MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "a _master child also labeled _original is skipped; falls back to _mid");
    }

    @Test void selectSafeDerivative_nullWhenChildrenExistButNoneMatchLabel() {
        // findChildrenByLabel returns an empty list (not null) when children exist but labels don't match
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(new ArrayList<>());
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(new ArrayList<>());
        assertNull(MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "empty child list (no matching labels) -> null");
    }

    @Test void selectSafeDerivative_skipsUrlMasterThenFallsBackToLocalMid() {
        // URL-backed _master is skipped, then a local _mid is returned
        Shepherd sh = mock(Shepherd.class);
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(LocalAssetStore.class));
        MediaAsset urlMaster = child("_master", true);
        ArrayList<MediaAsset> masters = new ArrayList<>(); masters.add(urlMaster);
        when(src.findChildrenByLabel(sh, "_master")).thenReturn(masters);
        MediaAsset mid = child("_mid", false);
        ArrayList<MediaAsset> mids = new ArrayList<>(); mids.add(mid);
        when(src.findChildrenByLabel(sh, "_mid")).thenReturn(mids);
        assertSame(mid, MediaResolveApi.selectSafeDerivative(annWithSource(src), sh),
            "a non-local _master is skipped; falls back to the local _mid");
    }

    /** Build a token-auth request mock; body comes from the ServletUtilities static mock. */
    private HttpServletRequest tokenRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR)).thenReturn(Boolean.TRUE);
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_CONTEXT_ATTR)).thenReturn("context0");
        return req;
    }

    private User mockUser(String id, boolean admin) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        return u;
    }

    @Test void doPost_non_token_request_401() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR)).thenReturn(Boolean.FALSE);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        new MediaResolveApi().doPostForTest(req, resp);
        verify(resp).setStatus(401);
    }

    @Test void doPost_token_but_missing_context_401_noShepherd() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR)).thenReturn(Boolean.TRUE);
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_CONTEXT_ATTR)).thenReturn(null);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            new MediaResolveApi().doPostForTest(req, resp);
            assertTrue(sh.constructed().isEmpty(), "no Shepherd may be constructed without a verified context");
        }
        verify(resp).setStatus(401);
    }

    @Test void doPost_empty_ids_400_noShepherd() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject("{\"annotationIds\":[]}"));
            new MediaResolveApi().doPostForTest(req, resp);
            assertTrue(sh.constructed().isEmpty(), "body validation must happen before any Shepherd/DB work");
        }
        verify(resp).setStatus(400);
    }

    @Test void doPost_over_max_ids_400() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        JSONArray big = new JSONArray();
        for (int i = 0; i < MediaResolveApi.MAX_IDS + 1; i++) big.put("id-" + i);
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", big));
            new MediaResolveApi().doPostForTest(req, resp);
            assertTrue(sh.constructed().isEmpty(), "over-max body must be rejected before Shepherd construction");
        }
        verify(resp).setStatus(400);
    }

    @Test void doPost_malformed_body_400() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenThrow(new org.json.JSONException("bad json"));
            new MediaResolveApi().doPostForTest(req, resp);
            assertTrue(sh.constructed().isEmpty(), "malformed body must be rejected before Shepherd construction");
        }
        verify(resp).setStatus(400);
    }

    private JSONObject hitsFor(String... idsThatPass) {
        JSONArray hits = new JSONArray();
        for (String id : idsThatPass) hits.put(new JSONObject().put("_id", id));
        return new JSONObject().put("hits",
            new JSONObject().put("total", new JSONObject().put("value", idsThatPass.length))
                            .put("hits", hits));
    }

    @Test void gate_returns_only_acl_passing_ids_and_sizes_to_id_count() throws Exception {
        // 12 requested ids; OpenSearch (mocked) returns all 12 as visible -> proves size>=12, not 10.
        JSONArray req = new JSONArray();
        String[] all = new String[12];
        for (int i = 0; i < 12; i++) { all[i] = "ann-" + i; req.put(all[i]); }

        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));

        final int[] capturedSize = {-1};
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("viewer", false);
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(false);
                 when(m.getAnnotation(anyString())).thenReturn(null); // resolveOne no-op here
             });
             MockedConstruction<org.ecocean.OpenSearch> os = mockConstruction(org.ecocean.OpenSearch.class,
                 (m, c) -> {
                     doNothing().when(m).deletePit(anyString());
                     when(m.queryPit(eq("annotation"), any(), eq(0), anyInt(), any(), any()))
                         .thenAnswer(inv -> { capturedSize[0] = inv.getArgument(3); return hitsFor(all); });
                 })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", req));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        assertEquals(12, capturedSize[0], "gate query size must equal the de-duplicated id count, not the default 10");
    }
}
