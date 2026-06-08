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
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class SearchApiChildIndexTest {

    HttpServletRequest request;
    HttpServletResponse response;
    StringWriter out;
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
        });
    }

    private static final JSONObject EMPTY_HITS =
        new JSONObject("{\"hits\":{\"total\":{\"value\":0},\"hits\":[]}}");

    /** True if the query has a bool.filter whose acl should-clause carries a term on the given field. */
    private static boolean filterShouldHasTerm(JSONObject q, String field) {
        JSONArray filter = q.getJSONObject("query").getJSONObject("bool").getJSONArray("filter");
        for (int i = 0; i < filter.length(); i++) {
            JSONArray should = filter.getJSONObject(i).getJSONObject("bool").optJSONArray("should");
            if (should == null) continue;
            for (int j = 0; j < should.length(); j++) {
                JSONObject term = should.getJSONObject(j).optJSONObject("term");
                if ((term != null) && term.has(field)) return true;
            }
        }
        return false;
    }

    // token POST /individual (non-admin) -> allowed; ACL filter uses submitterUserIds; status 200
    @Test void tokenIndividualSearch_injectsSubmitterUserIdsFilter() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/individual");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenAnswer(inv -> {
                        JSONObject q = inv.getArgument(1);
                        assertTrue(q.getJSONObject("query").getJSONObject("bool").has("filter"),
                            "ACL filter injected before queryPit");
                        assertTrue(filterShouldHasTerm(q, "submitterUserIds"),
                            "individual ACL should-clause uses submitterUserIds");
                        return EMPTY_HITS;
                    });
            })) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
    }

    // token POST /annotation (non-admin) -> allowed, filter applied -> 200
    @Test void tokenAnnotationSearch_allowedAndFiltered() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/annotation");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedConstruction<OpenSearch> os = mockConstruction(OpenSearch.class, (m, c) -> {
                doNothing().when(m).deletePit(anyString());
                when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(),
                    any(), any())).thenAnswer(inv -> {
                        JSONObject q = inv.getArgument(1);
                        assertTrue(q.getJSONObject("query").getJSONObject("bool").has("filter"),
                            "annotation ACL filter injected before queryPit");
                        return EMPTY_HITS;
                    });
            })) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
    }

    // token POST /occurrence -> still 403
    @Test void tokenOccurrenceSearch_returns403() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/occurrence");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(403);
    }

    // session (non-token) POST /individual, non-admin -> NOT blocked by token gate (proceeds to 200)
    @Test void sessionIndividualSearch_notBlocked() throws Exception {
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
        verify(response).setStatus(200);
    }

    // session annotation, non-admin -> still 403 (admin-only on session path)
    @Test void sessionAnnotationSearch_nonAdmin_403() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/annotation");
        when(request.getHeader("Authorization")).thenReturn(null); // no bearer
        when(request.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR))
            .thenReturn(null); // not a token request
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(403);
    }

    // a stored query that fails to load -> 404 "invalid searchQueryId" (NOT "unknown index")
    @Test void storedQueryMissing_returns404InvalidId() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class)) {
            osStatic.when(() -> OpenSearch.queryLoad(anyString())).thenReturn(null);
            osStatic.when(() -> OpenSearch.isValidIndexName(anyString())).thenReturn(true);
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(404);
        assertTrue(out.toString().contains("invalid searchQueryId"),
            "missing stored query returns invalid searchQueryId, not unknown index");
    }

    // token stored query owned by someone else whose indexName is invalid -> 403 owner, NOT 404
    @Test void tokenStoredQuery_otherOwnerInvalidIndex_returns403() throws Exception {
        when(request.getMethod()).thenReturn("GET");
        when(request.getPathInfo()).thenReturn("/11111111-1111-1111-1111-111111111111");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        User user = mockUser("u1", false);
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user, false);
            MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class)) {
            osStatic.when(() -> OpenSearch.queryLoad(anyString())).thenReturn(
                new JSONObject().put("creator", "someoneElse").put("indexName", "bogus")
                    .put("query", new JSONObject().put("match_all", new JSONObject())));
            osStatic.when(() -> OpenSearch.isValidIndexName(anyString())).thenReturn(false);
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(403);
        verify(response, never()).setStatus(404);
    }
}
