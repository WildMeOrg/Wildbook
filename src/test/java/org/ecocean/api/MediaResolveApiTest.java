package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Annotation;
import org.ecocean.Embedding;
import org.ecocean.Encounter;
import org.ecocean.User;
import org.ecocean.media.MediaAsset;
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

    /** Build a token-auth request mock; body comes from the ServletUtilities static mock. */
    private HttpServletRequest tokenRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR)).thenReturn(Boolean.TRUE);
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_CONTEXT_ATTR)).thenReturn("context0");
        return req;
    }

    private User mockUser(String id) {
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

    @Test void gatedVisibleIds_returnsOnlyAclPassingSubset() throws Exception {
        java.util.Set<String> requested = new java.util.LinkedHashSet<>(
            java.util.Arrays.asList("ann-0", "ann-1", "ann-hidden"));
        try (MockedConstruction<org.ecocean.OpenSearch> os = mockConstruction(org.ecocean.OpenSearch.class,
                (m, c) -> {
                    doNothing().when(m).deletePit(anyString());
                    when(m.queryPit(eq("annotation"), any(), eq(0), anyInt(), any(), any()))
                        .thenReturn(hitsFor("ann-0", "ann-1")); // only these pass the ACL gate
                })) {
            java.util.Set<String> visible = new MediaResolveApi().gatedVisibleIds(requested, "viewer");
            assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("ann-0", "ann-1")),
                new java.util.HashSet<>(visible),
                "gate returns only the ACL-passing subset; the hidden id is excluded");
        }
    }

    /** A resolvable annotation: source asset is a non-_original LocalAssetStore 1000x1000 whose own
        webURL is servable; bbox [100,200,300,400]. */
    private Annotation fullAnnotation(String id, String encId, String indId) throws Exception {
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(org.ecocean.media.LocalAssetStore.class));
        when(src.getWidth()).thenReturn(1000.0);
        when(src.getHeight()).thenReturn(1000.0);
        when(src.hasLabel("_original")).thenReturn(false);
        when(src.webURL()).thenReturn(new java.net.URL("https://h/wildbook_data_dir/x/" + id + "-master.jpg"));
        Annotation ann = mock(Annotation.class);
        when(ann.getId()).thenReturn(id);
        when(ann.getMediaAsset()).thenReturn(src);
        when(ann.getBbox()).thenReturn(new int[] {100, 200, 300, 400});
        when(ann.getTheta()).thenReturn(0.0);
        when(ann.getViewpoint()).thenReturn("up");
        Encounter enc = mock(Encounter.class);
        when(enc.getId()).thenReturn(encId);
        when(enc.hasMarkedIndividual()).thenReturn(true);
        when(enc.getIndividualID()).thenReturn(indId);
        when(ann.findEncounter(any(Shepherd.class))).thenReturn(enc);
        when(ann.getEmbeddings()).thenReturn(null);
        return ann;
    }

    @Test void resolve_payload_sourceFrameBbox_and_derivativeUrl() throws Exception {
        Annotation ann = fullAnnotation("ann-A", "enc-A", "ind-A");
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true); // admin path: no OpenSearch gate
                 when(m.getAnnotation("ann-A")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-A")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        JSONArray arr = new JSONArray(out.toString());
        assertEquals(1, arr.length(), "one entry resolved");
        JSONObject e = arr.getJSONObject(0);
        assertEquals("ann-A", e.getString("id"), "id echoed");
        assertTrue(e.getString("imageUrl").endsWith("-master.jpg"), "serves the source asset's own webURL");
        assertEquals(1000, e.getInt("imageWidth"), "imageWidth is the annotation asset width");
        assertEquals(1000, e.getInt("imageHeight"), "imageHeight is the annotation asset height");
        assertEquals("[100,200,300,400]", e.getJSONArray("bbox").toString(),
            "bbox returned in source coordinate space (no server-side scaling)");
        assertEquals("up", e.getString("viewpoint"), "viewpoint passed through");
        assertEquals("enc-A", e.getString("encounterId"), "first-parent encounter id");
        assertEquals("ind-A", e.getString("individualId"), "individual id");
    }

    @Test void resolve_omits_unresolvable_and_is_not_an_existence_oracle() throws Exception {
        Annotation good = fullAnnotation("ann-good", "enc-1", "ind-1");
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-good")).thenReturn(good);
                 when(m.getAnnotation("ann-garbage")).thenReturn(null); // nonexistent
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds",
                  new JSONArray().put("ann-good").put("ann-garbage")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        JSONArray arr = new JSONArray(out.toString());
        assertEquals(1, arr.length(), "garbage/nonexistent id is silently absent (no existence oracle)");
        assertEquals("ann-good", arr.getJSONObject(0).getString("id"), "only the resolvable id returned");
    }

    @Test void resolve_dedups_repeated_ids() throws Exception {
        Annotation good = fullAnnotation("ann-d", "enc-d", "ind-d");
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-d")).thenReturn(good);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds",
                  new JSONArray().put("ann-d").put("ann-d").put("ann-d")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        JSONArray arr = new JSONArray(out.toString());
        assertEquals(1, arr.length(), "duplicate ids collapse to a single entry");
    }

    @Test void resolve_nonAdmin_onlyGateVisibleResolved_noOracle_andNoLoadForHidden() throws Exception {
        Annotation vis = fullAnnotation("ann-vis", "enc-v", "ind-v");
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("viewer");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(false);
                 when(m.getAnnotation("ann-vis")).thenReturn(vis);
             });
             MockedConstruction<org.ecocean.OpenSearch> os = mockConstruction(org.ecocean.OpenSearch.class,
                 (m, c) -> {
                     doNothing().when(m).deletePit(anyString());
                     when(m.queryPit(eq("annotation"), any(), eq(0), anyInt(), any(), any()))
                         .thenReturn(hitsFor("ann-vis")); // only ann-vis passes the ACL gate
                 })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds",
                  new JSONArray().put("ann-vis").put("ann-hidden").put("ann-garbage")));
            new MediaResolveApi().doPostForTest(request, resp);
            Shepherd constructed = sh.constructed().get(0);
            verify(constructed, never()).getAnnotation("ann-hidden");
            verify(constructed, never()).getAnnotation("ann-garbage");
        }
        verify(resp).setStatus(200);
        JSONArray arr = new JSONArray(out.toString());
        assertEquals(1, arr.length(), "non-admin sees only gate-passed ids; hidden/garbage absent (no oracle)");
        assertEquals("ann-vis", arr.getJSONObject(0).getString("id"), "only the visible id resolved");
    }

    @Test void resolve_nonAdmin_moreThan10VisibleAllResolve() throws Exception {
        JSONArray reqIds = new JSONArray();
        String[] all = new String[12];
        for (int i = 0; i < 12; i++) { all[i] = "ann-" + i; reqIds.put(all[i]); }
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("viewer");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(false);
                 when(m.getAnnotation(anyString())).thenAnswer(inv -> {
                     String id = inv.getArgument(0);
                     return fullAnnotation(id, "enc-" + id, "ind-" + id);
                 });
             });
             MockedConstruction<org.ecocean.OpenSearch> os = mockConstruction(org.ecocean.OpenSearch.class,
                 (m, c) -> {
                     doNothing().when(m).deletePit(anyString());
                     when(m.queryPit(eq("annotation"), any(), eq(0), anyInt(), any(), any()))
                         .thenReturn(hitsFor(all));
                 })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", reqIds));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        assertEquals(12, new JSONArray(out.toString()).length(), "all 12 visible ids resolve (no default-10 truncation)");
    }

    @Test void resolve_thetaDefault_and_nullViewpoint() throws Exception {
        Annotation ann = fullAnnotation("ann-t", "enc-t", "ind-t");
        when(ann.getViewpoint()).thenReturn(null);
        when(ann.getTheta()).thenReturn(0.0);
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-t")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-t")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        JSONObject e = new JSONArray(out.toString()).getJSONObject(0);
        assertTrue(e.isNull("viewpoint"), "null viewpoint serialized as JSON null");
        assertEquals(0.0, e.getDouble("theta"), 0.0001, "theta defaults to 0.0");
    }

    @Test void resolve_methodVersion_dedupAndOrder() throws Exception {
        Annotation ann = fullAnnotation("ann-m", "enc-m", "ind-m");
        Embedding e1 = mock(Embedding.class); when(e1.getMethodVersion()).thenReturn("msv4.1");
        Embedding e2 = mock(Embedding.class); when(e2.getMethodVersion()).thenReturn("msv4.1");
        Embedding e3 = mock(Embedding.class); when(e3.getMethodVersion()).thenReturn("msv3");
        Set<Embedding> embs = new LinkedHashSet<>(); embs.add(e1); embs.add(e2); embs.add(e3);
        when(ann.getEmbeddings()).thenReturn(embs);
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-m")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-m")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        JSONArray mvs = new JSONArray(out.toString()).getJSONObject(0).getJSONArray("methodVersion");
        assertEquals("[\"msv4.1\",\"msv3\"]", mvs.toString(), "method versions de-duplicated, first-seen order");
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
        final org.json.JSONObject[] capturedQuery = {null};
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("viewer");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(false);
                 when(m.getAnnotation(anyString())).thenReturn(null); // resolveOne no-op here
             });
             MockedConstruction<org.ecocean.OpenSearch> os = mockConstruction(org.ecocean.OpenSearch.class,
                 (m, c) -> {
                     doNothing().when(m).deletePit(anyString());
                     when(m.queryPit(eq("annotation"), any(), eq(0), anyInt(), any(), any()))
                         .thenAnswer(inv -> { capturedSize[0] = inv.getArgument(3); capturedQuery[0] = inv.getArgument(1); return hitsFor(all); });
                 })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", req));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        assertEquals(12, capturedSize[0], "gate query size must equal the de-duplicated id count, not the default 10");
        assertNotNull(capturedQuery[0], "queryPit must have been called with a query");
        String q = capturedQuery[0].toString();
        assertTrue(q.contains("ann-0"), "gate query must include the requested ids; was: " + q);
        assertTrue(q.contains("publiclyReadable") && q.contains("submitterUserIds") && q.contains("viewUsers"),
            "gate query must include the Spec A annotation ACL filter; was: " + q);
    }

    @Test void resolve_safeUrlThrows_omitsEntryNot500() throws Exception {
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(org.ecocean.media.LocalAssetStore.class));
        when(src.getWidth()).thenReturn(1000.0);
        when(src.getHeight()).thenReturn(1000.0);
        when(src.hasLabel("_original")).thenReturn(false);
        when(src.webURL()).thenThrow(new IllegalArgumentException("corrupt path"));
        Annotation ann = mock(Annotation.class);
        when(ann.getId()).thenReturn("ann-x");
        when(ann.getMediaAsset()).thenReturn(src);
        when(ann.getBbox()).thenReturn(new int[] {10, 10, 100, 100});
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-x")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-x")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        assertEquals(0, new JSONArray(out.toString()).length(),
            "source webURL throwing -> entry omitted, batch still 200");
    }

    @Test void resolve_emptyEncounterId_serializedAsNull() throws Exception {
        Annotation ann = fullAnnotation("ann-e", "enc-e", "ind-e");
        Encounter blankEnc = mock(Encounter.class);
        when(blankEnc.getId()).thenReturn("");
        when(ann.findEncounter(any(Shepherd.class))).thenReturn(blankEnc);
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-e")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-e")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        JSONObject e = new JSONArray(out.toString()).getJSONObject(0);
        assertTrue(e.isNull("encounterId"), "empty encounter id serialized as JSON null");
    }

    @Test void resolve_nullBbox_omits() throws Exception {
        Annotation ann = fullAnnotation("ann-nb", "enc-nb", "ind-nb");
        when(ann.getBbox()).thenReturn(null);
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-nb")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-nb")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        verify(resp).setStatus(200);
        assertEquals(0, new JSONArray(out.toString()).length(), "null bbox -> omit");
    }

    @Test void resolve_originalSource_servedViaMasterChild() throws Exception {
        MediaAsset src = mock(MediaAsset.class);
        when(src.getStore()).thenReturn(mock(org.ecocean.media.LocalAssetStore.class));
        when(src.getWidth()).thenReturn(1000.0);
        when(src.getHeight()).thenReturn(1000.0);
        when(src.hasLabel("_original")).thenReturn(true); // raw upload -> must NOT serve directly
        MediaAsset master = mock(MediaAsset.class);
        when(master.getStore()).thenReturn(mock(org.ecocean.media.LocalAssetStore.class));
        when(master.hasLabel("_original")).thenReturn(false);
        when(master.webURL()).thenReturn(new java.net.URL("https://h/wildbook_data_dir/x/child-master.jpg"));
        when(src.bestSafeAsset(any(Shepherd.class), isNull(), eq("master"))).thenReturn(master);
        Annotation ann = mock(Annotation.class);
        when(ann.getId()).thenReturn("ann-o");
        when(ann.getMediaAsset()).thenReturn(src);
        when(ann.getBbox()).thenReturn(new int[] {0, 0, 500, 500});
        when(ann.getTheta()).thenReturn(0.0);
        when(ann.getViewpoint()).thenReturn("up");
        Encounter enc = mock(Encounter.class);
        when(enc.getId()).thenReturn("enc-o");
        when(ann.findEncounter(any(Shepherd.class))).thenReturn(enc);
        when(ann.getEmbeddings()).thenReturn(null);
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-o")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-o")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        JSONArray arr = new JSONArray(out.toString());
        assertEquals(1, arr.length(), "_original source resolves via its master child");
        assertTrue(arr.getJSONObject(0).getString("imageUrl").endsWith("child-master.jpg"),
            "_original is served via a _master child, not directly");
        assertEquals(1000, arr.getJSONObject(0).getInt("imageWidth"),
            "reported dims are the source (original) frame; consumer scales to the served master");
    }

    @Test void resolve_negativeOriginBbox_clampsCorner() throws Exception {
        Annotation ann = fullAnnotation("ann-c", "enc-c", "ind-c");
        when(ann.getBbox()).thenReturn(new int[] {-10, 0, 20, 20}); // x=-10 -> clamp corner, width 20->10
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-c")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-c")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        assertEquals("[0,0,10,20]", new JSONArray(out.toString()).getJSONObject(0).getJSONArray("bbox").toString(),
            "negative origin clamps the corner and shrinks width (not kept at 20)");
    }

    @Test void resolve_bboxFullyOutOfBounds_omits() throws Exception {
        Annotation ann = fullAnnotation("ann-oob", "enc-oob", "ind-oob");
        when(ann.getBbox()).thenReturn(new int[] {-50, -50, 20, 20}); // entirely off-image -> omit
        HttpServletRequest request = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 User u = mockUser("admin");
                 when(m.getUser(any(HttpServletRequest.class))).thenReturn(u);
                 when(u.isAdmin(m)).thenReturn(true);
                 when(m.getAnnotation("ann-oob")).thenReturn(ann);
             })) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationIds", new JSONArray().put("ann-oob")));
            new MediaResolveApi().doPostForTest(request, resp);
        }
        assertEquals(0, new JSONArray(out.toString()).length(), "fully out-of-bounds bbox -> omit");
    }
}
