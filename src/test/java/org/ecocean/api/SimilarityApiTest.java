package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.Annotation;
import org.ecocean.Embedding;
import org.ecocean.Encounter;
import org.ecocean.OpenSearch;
import org.ecocean.User;
import org.ecocean.security.WildbookTokenAuthenticationFilter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class SimilarityApiTest {

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

    private Annotation annWithEmbedding(String id, String viewpoint, String enc) {
        Embedding e = mock(Embedding.class);
        when(e.getMethod()).thenReturn("miewid");
        when(e.getMethodVersion()).thenReturn("msv4.1");
        when(e.vectorToFloatArray()).thenReturn(new float[] {0.1f, 0.2f, 0.3f});
        Set<Embedding> embs = new LinkedHashSet<>();
        embs.add(e);
        Annotation ann = mock(Annotation.class);
        when(ann.getId()).thenReturn(id);
        when(ann.getViewpoint()).thenReturn(viewpoint);
        when(ann.getEmbeddings()).thenReturn(embs);
        Encounter en = mock(Encounter.class);
        when(en.getId()).thenReturn(enc);
        when(ann.findEncounter(any(Shepherd.class))).thenReturn(en);
        return ann;
    }

    private MockedConstruction<Shepherd> shepherd(User user, boolean admin, Annotation ann) {
        return mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            doNothing().when(m).rollbackAndClose();
            when(m.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(user.isAdmin(m)).thenReturn(admin);
            if (ann != null) when(m.getAnnotation(ann.getId())).thenReturn(ann);
        });
    }

    private JSONObject knnResult(double score, String neighborId, String vp) {
        JSONObject src = new JSONObject().put("id", neighborId).put("viewpoint", vp)
            .put("encounterId", "e-" + neighborId).put("individualId", "i-" + neighborId);
        JSONObject hit = new JSONObject().put("_score", score).put("_id", neighborId).put("_source", src);
        return new JSONObject().put("hits", new JSONObject().put("hits", new JSONArray().put(hit)));
    }

    @Test void nonToken_401() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR)).thenReturn(Boolean.FALSE);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        new SimilarityApi().doPostForTest(req, resp);
        verify(resp).setStatus(401);
    }

    @Test void missingAnnotationId_400() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("k", 5));
            new SimilarityApi().doPostForTest(req, resp);
            assertTrue(sh.constructed().isEmpty(), "no Shepherd before body validation");
        }
        verify(resp).setStatus(400);
    }

    @Test void badK_400() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class)) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationId", "a").put("k", 9999));
            new SimilarityApi().doPostForTest(req, resp);
        }
        verify(resp).setStatus(400);
    }

    @Test void admin_happyPath_returnsNeighborsWithScore_noVectors_filteredAnnShape() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        Annotation ann = annWithEmbedding("ann-A", "left", "enc-A");
        User admin = mockUser("admin");
        final JSONObject[] sentBody = {null};
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = shepherd(admin, true, ann);
             MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) ->
                 when(m.queryPitPrivate(eq("annotation"), any(JSONObject.class), anyInt(), anyInt(),
                     any(), any())).thenAnswer(inv -> {
                         sentBody[0] = inv.getArgument(1);
                         return knnResult(0.91, "n1", "left");
                     }))) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationId", "ann-A").put("k", 5));
            new SimilarityApi().doPostForTest(req, resp);
        }
        verify(resp).setStatus(200);
        JSONObject body = new JSONObject(out.toString());
        assertEquals("ann-A", body.getJSONObject("query").getString("annotationId"));
        assertEquals("left", body.getJSONObject("query").getString("viewpoint"));
        assertEquals("msv4.1", body.getJSONObject("query").getString("methodVersion"));
        JSONArray nb = body.getJSONArray("neighbors");
        assertEquals(1, nb.length());
        assertEquals("n1", nb.getJSONObject(0).getString("id"));
        assertEquals(0.91, nb.getJSONObject(0).getDouble("score"), 0.0001);
        assertFalse(nb.getJSONObject(0).has("embeddings"), "neighbors never carry vectors");
        // the kNN body must filter INSIDE knn.filter (filtered ANN), with viewpoint+method+version and self-exclusion
        JSONObject knn = sentBody[0].getJSONObject("query").getJSONObject("nested")
            .getJSONObject("query").getJSONObject("knn").getJSONObject("embeddings.vector");
        JSONObject fbool = knn.getJSONObject("filter").getJSONObject("bool");
        String filt = fbool.getJSONArray("filter").toString();
        assertTrue(filt.contains("viewpoint") && filt.contains("left"), "filters to the query viewpoint");
        assertTrue(filt.contains("embeddings.methodVersion") && filt.contains("msv4.1"),
            "filters to the query method version");
        assertTrue(fbool.getJSONArray("must_not").toString().contains("ann-A"),
            "excludes the query annotation itself");
        assertTrue(sentBody[0].getJSONObject("_source").getJSONArray("excludes").toString()
            .contains("embeddings"), "embeddings excluded from _source");
    }

    @Test void neighborEmbeddingsInSource_areStripped() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        Annotation ann = annWithEmbedding("ann-A", "left", "enc-A");
        User admin = mockUser("admin");
        // a hit whose _source DOES contain an embeddings vector — must be stripped before output
        JSONObject src = new JSONObject().put("id", "n1").put("viewpoint", "left")
            .put("embeddings", new JSONArray().put(new JSONObject()
                .put("methodVersion", "msv4.1").put("vector", new JSONArray().put(0.1).put(0.2))));
        JSONObject hit = new JSONObject().put("_score", 0.8).put("_source", src);
        JSONObject res = new JSONObject().put("hits", new JSONObject().put("hits",
            new JSONArray().put(hit)));
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = shepherd(admin, true, ann);
             MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) ->
                 when(m.queryPitPrivate(eq("annotation"), any(JSONObject.class), anyInt(), anyInt(),
                     any(), any())).thenReturn(res))) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationId", "ann-A"));
            new SimilarityApi().doPostForTest(req, resp);
        }
        JSONObject nb = new JSONObject(out.toString()).getJSONArray("neighbors").getJSONObject(0);
        assertFalse(nb.has("embeddings"), "embeddings in _source must be stripped from the response");
    }

    @Test void ambiguousEmbedding_400() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        // two embeddings both matching method=miewid; no methodVersion given -> ambiguous -> 400
        Embedding e1 = mock(Embedding.class);
        when(e1.getMethod()).thenReturn("miewid"); when(e1.getMethodVersion()).thenReturn("msv4.1");
        Embedding e2 = mock(Embedding.class);
        when(e2.getMethod()).thenReturn("miewid"); when(e2.getMethodVersion()).thenReturn("msv3");
        Set<Embedding> embs = new LinkedHashSet<>(); embs.add(e1); embs.add(e2);
        Annotation ann = mock(Annotation.class);
        when(ann.getId()).thenReturn("ann-amb");
        when(ann.getViewpoint()).thenReturn("left");
        when(ann.getEmbeddings()).thenReturn(embs);
        User admin = mockUser("admin");
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = shepherd(admin, true, ann);
             MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class)) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationId", "ann-amb")); // no methodVersion
            new SimilarityApi().doPostForTest(req, resp);
        }
        verify(resp).setStatus(400);
    }

    @Test void noViewpoint_400() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        Annotation ann = annWithEmbedding("ann-nv", null, "enc"); // null viewpoint
        User admin = mockUser("admin");
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = shepherd(admin, true, ann);
             MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class)) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationId", "ann-nv"));
            new SimilarityApi().doPostForTest(req, resp);
        }
        verify(resp).setStatus(400);
    }

    @Test void nonAdmin_queryAnnotationNotVisible_404_noOracle() throws Exception {
        HttpServletRequest req = tokenRequest();
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        User viewer = mockUser("viewer");
        try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
             MockedConstruction<Shepherd> sh = shepherd(viewer, false, null);
             MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) ->
                 // the visibility eligibility query returns no hits -> not visible
                 when(m.queryPitPrivate(eq("annotation"), any(JSONObject.class), anyInt(), anyInt(),
                     any(), any())).thenReturn(new JSONObject().put("hits",
                         new JSONObject().put("hits", new JSONArray()))))) {
            su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
              .thenReturn(new JSONObject().put("annotationId", "hidden-or-missing"));
            new SimilarityApi().doPostForTest(req, resp);
            verify(sh.constructed().get(0), never()).getAnnotation("hidden-or-missing");
        }
        verify(resp).setStatus(404);
    }

    @Test void poolExhausted_429_withRetryAfter_noShepherd() throws Exception {
        // drain the SIMILARITY pool so the request can't acquire a permit
        List<TokenApiConcurrency.Permit> held = new ArrayList<>();
        TokenApiConcurrency.Permit p;
        while ((p = TokenApiConcurrency.tryAcquire(TokenApiConcurrency.Kind.SIMILARITY)) != null) held.add(p);
        try {
            HttpServletRequest req = tokenRequest();
            HttpServletResponse resp = mock(HttpServletResponse.class);
            when(resp.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
            try (MockedStatic<ServletUtilities> su = mockStatic(ServletUtilities.class);
                 MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
                su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
                  .thenReturn(new JSONObject().put("annotationId", "ann-A").put("k", 5));
                new SimilarityApi().doPostForTest(req, resp);
                assertTrue(sh.constructed().isEmpty(), "no Shepherd/DB work when the pool is at capacity");
            }
            verify(resp).setStatus(429);
            verify(resp).setHeader(eq("Retry-After"), anyString());
        } finally {
            for (TokenApiConcurrency.Permit h : held) h.close();
        }
    }
}
