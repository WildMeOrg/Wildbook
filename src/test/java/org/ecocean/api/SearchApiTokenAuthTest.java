package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.OpenSearch;
import org.ecocean.User;
import org.ecocean.security.WildbookTokenAuthenticationFilter;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class SearchApiTokenAuthTest {

    HttpServletRequest request;
    HttpServletResponse response;
    StringWriter out;
    // getContext() on a Mockito request would NPE (reads serverName/cookies/context props);
    // stub it for ALL tests. jsonFromHttpServletRequest is stubbed per-test as needed.
    MockedStatic<ServletUtilities> su;

    @BeforeEach void setUp() throws Exception {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
        when(request.getServletContext()).thenReturn(null);
        when(request.getContextPath()).thenReturn("");
        when(request.getParameter(anyString())).thenReturn(null);
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(Boolean.TRUE); // default: token request (override per-test where needed)
        su = mockStatic(ServletUtilities.class);
        su.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
        su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
            .thenReturn(new JSONObject("{\"query\":{\"match_all\":{}}}"));
    }

    @AfterEach void tearDown() {
        su.close();
    }

    private User mockUser(String id, boolean admin) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        when(u.getUUID()).thenReturn(id); // queryStore reads getUUID(); == getId() in source
        return u;
    }

    /** Shepherd mock with the standard tx stubs + a resolved user. */
    private MockedConstruction<Shepherd> shepherdReturning(User user, boolean admin) {
        return mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            doNothing().when(m).rollbackAndClose();
            when(m.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(user.isAdmin(m)).thenReturn(admin);
            // let the REAL OpenSearch.queryStore run against this mock: makePersistent is a
            // no-op on the mock PM, the commit reports success, and the re-begun transaction
            // reads as active (queryPrune swallows the mock PM null-query internally)
            when(m.getPM()).thenReturn(mock(javax.jdo.PersistenceManager.class));
            when(m.commitDBTransactionWithStatus()).thenReturn(true);
            when(m.isDBTransactionActive()).thenReturn(true);
        });
    }

    private static final JSONObject EMPTY_HITS =
        new JSONObject("{\"hits\":{\"total\":{\"value\":0},\"hits\":[]}}");

    @Test void tokenRequest_nonAllowlistedIndex_returns403() throws Exception {
        // token index allowlist is {encounter, annotation, individual}; occurrence is outside it
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/occurrence");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(403);
    }

    @Test void bearerWithoutMarker_returns401() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null); // filter did NOT mark it
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(401);
    }

    @Test void lowercaseBearerWithoutMarker_returns401() throws Exception {
        // case-insensitive bearerPresent: "bearer ..." with no marker must still fail closed
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("bearer x");
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null);
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(401);
    }

    @Test void tokenEncounterSearch_injectsAclFilterBeforeExecution() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenAnswer(inv -> {
                        JSONObject q = inv.getArgument(1);
                        JSONObject bool = q.getJSONObject("query").getJSONObject("bool");
                        assertTrue(bool.has("filter"), "ACL filter injected before queryPit");
                        return EMPTY_HITS;
                    });
            })) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
    }

    @Test void adminTokenEncounterSearch_doesNotInjectAclFilter() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("admin1", true);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, true);
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenAnswer(inv -> {
                        JSONObject q = inv.getArgument(1);
                        JSONObject inner = q.getJSONObject("query");
                        assertFalse(inner.has("bool") && inner.getJSONObject("bool").has("filter"),
                            "admin token must NOT have an injected ACL filter");
                        return EMPTY_HITS;
                    });
            })) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
    }

    // ---- stored-query gating (token path) ----

    /** Stub OpenSearch.queryLoad to return a stored-query doc (with indexName + creator). */
    private MockedStatic<OpenSearch> storedQuery(String indexName, String creator) {
        MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class);
        osStatic.when(() -> OpenSearch.queryLoad(anyString(), any())).thenReturn(
            new JSONObject().put("indexName", indexName).put("creator", creator)
                .put("query", new JSONObject().put("match_all", new JSONObject())));
        osStatic.when(() -> OpenSearch.isValidIndexName(anyString())).thenReturn(true);
        return osStatic;
    }

    @Test void storedQuery_otherOwner_returns403() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = storedQuery("encounter", "someoneElse")) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(403);
    }

    @Test void storedQuery_nonAllowlistedIndex_returns403() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = storedQuery("occurrence", "u1")) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(403); // own query, but index outside the token allowlist
    }

    @Test void storedQuery_postMethod_returns405() throws Exception {
        // token stored-query replay is GET-only
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = storedQuery("encounter", "u1")) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(405);
    }

    @Test void storedQuery_postOtherOwner_is405NotOwnership403() throws Exception {
        // method gate fires FIRST: wrong method must not leak ownership via a 403
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = storedQuery("encounter", "someoneElse")) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(405);
        verify(response, never()).setStatus(403);
    }

    @Test void directIndex_getMethod_returns405() throws Exception {
        // token direct index search is POST-only; a GET to /encounter must be 405
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(405);
    }

    @Test void sessionRequest_nonEncounterIndex_notBlockedByTokenGate() throws Exception {
        // non-token (session) request: no Bearer, no token marker -> all tokenAuth gates skip,
        // and the encounter-only index gate must NOT apply. /individual proceeds to execution.
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/individual");
        when(request.getHeader("Authorization")).thenReturn(null); // no bearer
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null); // not a token request
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenReturn(EMPTY_HITS);
            })) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200); // session path unaffected by token gates
    }

    @Test void sessionPost_storesQuery_andReturnsSearchQueryId() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null); // session path
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class)) {
            osStatic.when(() -> OpenSearch.isValidIndexName(anyString())).thenReturn(true);
            osStatic.when(() -> OpenSearch.querySanitize(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
            osStatic.when(() -> OpenSearch.queryStore(any(), anyString(), any(), any()))
                .thenReturn("11111111-2222-3333-4444-555555555555");
            try (MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenReturn(EMPTY_HITS);
            })) {
                new SearchApi().doPost(request, response);
            }
        }
        verify(response).setStatus(200);
        verify(response).setHeader("X-Wildbook-Search-Query-Id",
            "11111111-2222-3333-4444-555555555555");
        assertTrue(out.toString().contains("11111111-2222-3333-4444-555555555555"),
            "response body carries searchQueryId");
    }

    @Test void post_storeFailure_returns503_andDoesNotExecute() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null);
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class)) {
            osStatic.when(() -> OpenSearch.isValidIndexName(anyString())).thenReturn(true);
            osStatic.when(() -> OpenSearch.querySanitize(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
            osStatic.when(() -> OpenSearch.queryStore(any(), anyString(), any(), any()))
                .thenReturn(null); // store failed: durability not confirmed
            try (MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
            })) {
                new SearchApi().doPost(request, response);
                for (OpenSearch constructed : os.constructed()) {
                    verify(constructed, never()).queryPit(anyString(), any(JSONObject.class),
                        anyInt(), anyInt(), any(), any());
                }
            }
        }
        verify(response).setStatus(503);
        assertTrue(out.toString().contains("failed to store search query"),
            "error surfaced in body");
    }

    @Test void post_storedButSessionRecoveryFailed_returns503_withId() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null);
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            doNothing().when(m).rollbackAndClose();
            when(m.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(user.isAdmin(m)).thenReturn(false);
            when(m.isDBTransactionActive()).thenReturn(false); // re-begin failed post-commit
        });
            MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class)) {
            osStatic.when(() -> OpenSearch.isValidIndexName(anyString())).thenReturn(true);
            osStatic.when(() -> OpenSearch.querySanitize(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
            osStatic.when(() -> OpenSearch.queryStore(any(), anyString(), any(), any()))
                .thenReturn("22222222-3333-4444-5555-666666666666"); // durable
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(503);
        verify(response).setHeader("X-Wildbook-Search-Query-Id",
            "22222222-3333-4444-5555-666666666666");
        assertTrue(out.toString().contains("22222222-3333-4444-5555-666666666666"),
            "durable id still returned when the request session could not continue");
    }

    @Test void storedQuery_ownEncounter_succeeds() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = storedQuery("encounter", "u1");
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenAnswer(inv -> {
                        JSONObject q = inv.getArgument(1);
                        assertTrue(q.getJSONObject("query").getJSONObject("bool").has("filter"),
                            "own stored encounter query is ACL-scoped before execution");
                        return EMPTY_HITS;
                    });
            })) {
            // queryScrubStored is static too; stub it to extract the inner query
            osStatic.when(() -> OpenSearch.queryScrubStored(any())).thenAnswer(inv -> {
                JSONObject stored = inv.getArgument(0);
                return new JSONObject().put("query", stored.optJSONObject("query"));
            });
            osStatic.when(() -> OpenSearch.querySanitize(any(), any(), any()))
                .thenAnswer(inv -> inv.getArgument(0));
            osStatic.when(() -> OpenSearch.applyAclFilter(any(), anyString(), anyString()))
                .thenCallRealMethod();
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
    }

    @Test void tokenAgg_validTerms_surfacesAggregations_aclScoped_aggsTopLevel() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/annotation");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any())).thenReturn(new JSONObject(
            "{\"query\":{\"term\":{\"iaClass\":\"zebra\"}},"
            + "\"aggs\":{\"byLoc\":{\"terms\":{\"field\":\"encounterLocationId\",\"size\":10}}}}"));
        User user = mockUser("u1", false);
        JSONObject withAggs = new JSONObject(
            "{\"hits\":{\"total\":{\"value\":3},\"hits\":[]},"
            + "\"aggregations\":{\"byLoc\":{\"buckets\":[{\"key\":\"SiteA\",\"doc_count\":2}]}}}");
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenAnswer(inv -> {
                        JSONObject q = inv.getArgument(1);
                        assertTrue(q.has("aggs"), "aggs left top-level for OpenSearch to compute");
                        assertTrue(q.getJSONObject("query").getJSONObject("bool").has("filter"),
                            "non-admin aggregation runs over the ACL-filtered query");
                        return withAggs;
                    });
            })) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
        JSONObject res = new JSONObject(out.toString());
        assertTrue(res.has("aggregations"), "valid aggregation is surfaced to the caller");
        assertEquals("SiteA", res.getJSONObject("aggregations").getJSONObject("byLoc")
            .getJSONArray("buckets").getJSONObject(0).getString("key"));
    }

    @Test void tokenAgg_disallowedField_returns400_andNotExecuted() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any())).thenReturn(new JSONObject(
            "{\"aggs\":{\"leak\":{\"terms\":{\"field\":\"viewUsers\"}}}}"));
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) ->
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(), any(), any()))
                    .thenReturn(EMPTY_HITS))) {
            new SearchApi().doPost(request, response);
            assertTrue(os.constructed().isEmpty(),
                "an invalid aggregation is rejected before any OpenSearch execution");
        }
        verify(response).setStatus(400);
        assertFalse(new JSONObject(out.toString()).optString("error", "").isEmpty(),
            "400 carries an explanatory error");
    }
}
