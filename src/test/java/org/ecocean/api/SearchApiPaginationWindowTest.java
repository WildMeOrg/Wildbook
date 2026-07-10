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

class SearchApiPaginationWindowTest {
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
            .thenReturn(Boolean.TRUE);
        when(request.getMethod()).thenReturn("POST");
        when(request.getPathInfo()).thenReturn("/encounter");
        when(request.getHeader("Authorization")).thenReturn("Bearer x");
        su = mockStatic(ServletUtilities.class);
        su.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
        su.when(() -> ServletUtilities.jsonFromHttpServletRequest(any()))
            .thenReturn(new JSONObject("{\"query\":{\"match_all\":{}}}"));
    }

    @AfterEach void tearDown() {
        su.close();
    }

    private User mockUser(String id) {
        User u = mock(User.class);

        when(u.getId()).thenReturn(id);
        when(u.getUUID()).thenReturn(id);
        return u;
    }

    private MockedConstruction<Shepherd> shepherdReturning(User user) {
        return mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            doNothing().when(m).rollbackAndClose();
            when(m.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(user.isAdmin(m)).thenReturn(false);
        });
    }

    private static final JSONObject EMPTY_HITS =
        new JSONObject("{\"hits\":{\"total\":{\"value\":0},\"hits\":[]}}");

    private MockedConstruction<OpenSearch> openSearchWithWindow(int window) {
        return mockConstruction(OpenSearch.class, (m, c) -> {
            when(m.getMaxResultWindow(anyString())).thenReturn(window);
            doNothing().when(m).deletePit(anyString());
            when(m.queryPit(anyString(), any(JSONObject.class), anyInt(), anyInt(), any(), any()))
                .thenReturn(EMPTY_HITS);
        });
    }

    // from+size beyond the window -> 400 with the limit in the payload; OpenSearch never queried
    @Test void beyondWindow_returns400_withoutQuerying() throws Exception {
        when(request.getParameter("from")).thenReturn("147060");
        when(request.getParameter("size")).thenReturn("20");
        User user = mockUser("u1");
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user);
            MockedConstruction<OpenSearch> os = openSearchWithWindow(10000)) {
            new SearchApi().doPost(request, response);
            verify(os.constructed().get(0), never()).queryPit(anyString(), any(JSONObject.class),
                anyInt(), anyInt(), any(), any());
        }
        verify(response).setStatus(400);
        JSONObject body = new JSONObject(out.toString());
        assertEquals(10000, body.getInt("maxResultWindow"));
        assertTrue(body.getString("error").contains("from + size"));
    }

    // integer-overflow pagination values must not slip past the window check
    @Test void overflowingFromPlusSize_returns400() throws Exception {
        when(request.getParameter("from")).thenReturn(Integer.toString(Integer.MAX_VALUE - 5));
        when(request.getParameter("size")).thenReturn("1000");
        User user = mockUser("u1");
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user);
            MockedConstruction<OpenSearch> os = openSearchWithWindow(10000)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(400);
    }

    @Test void negativeFrom_returns400() throws Exception {
        when(request.getParameter("from")).thenReturn("-5");
        when(request.getParameter("size")).thenReturn("10");
        User user = mockUser("u1");
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user);
            MockedConstruction<OpenSearch> os = openSearchWithWindow(10000)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(400);
    }

    // valid pagination -> 200, and the live window limit is exposed as a response header
    @Test void withinWindow_succeeds_andExposesWindowHeader() throws Exception {
        when(request.getParameter("from")).thenReturn("9990");
        when(request.getParameter("size")).thenReturn("10");
        User user = mockUser("u1");
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user);
            MockedConstruction<OpenSearch> os = openSearchWithWindow(10000)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
        verify(response).setHeader("X-Wildbook-Max-Result-Window", "10000");
    }

    // a broken window value from the backend (mock default 0, misconfigured index)
    // falls back to the default ceiling instead of rejecting every request
    @Test void nonPositiveWindow_fallsBackToDefault() throws Exception {
        when(request.getParameter("from")).thenReturn("9990");
        when(request.getParameter("size")).thenReturn("10");
        User user = mockUser("u1");
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user);
            MockedConstruction<OpenSearch> os = openSearchWithWindow(0)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
        verify(response).setHeader("X-Wildbook-Max-Result-Window", "10000");
    }

    // size=0 (aggregation-style) stays valid, including exactly at the window boundary
    @Test void sizeZeroAtWindowBoundary_isAllowed() throws Exception {
        when(request.getParameter("from")).thenReturn("10000");
        when(request.getParameter("size")).thenReturn("0");
        User user = mockUser("u1");
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user);
            MockedConstruction<OpenSearch> os = openSearchWithWindow(10000)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
    }

    // the check uses the PER-INDEX window (which Wildbook raises above 10k on big indexes)
    @Test void perIndexWindowIsRespected() throws Exception {
        when(request.getParameter("from")).thenReturn("12000");
        when(request.getParameter("size")).thenReturn("4");
        User user = mockUser("u1");
        try (MockedConstruction<Shepherd> sh = shepherdReturning(user);
            MockedConstruction<OpenSearch> os = openSearchWithWindow(12004)) {
            new SearchApi().doPost(request, response);
        }
        verify(response).setStatus(200);
    }
}
