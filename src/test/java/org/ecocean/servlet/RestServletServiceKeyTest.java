package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.datanucleus.exceptions.NucleusUserException;
import org.ecocean.Encounter;
import org.ecocean.auth.ServiceKey;
import org.junit.jupiter.api.Test;

/** Service key records are not served by RestServlet. */
class RestServletServiceKeyTest {
    static final class Call {
        HttpServletResponse resp; StringWriter body = new StringWriter(); boolean reachedGet;
    }

    /** Request for {@code /wildbook/rest/<path>} with the given raw query string and decoded parameters. */
    static HttpServletRequest request(String servletPath, String path, String query, Map<String, String> params) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getUserPrincipal()).thenReturn(() -> "researcher");
        when(req.getRemoteUser()).thenReturn("researcher");
        when(req.getContextPath()).thenReturn("/wildbook");
        when(req.getServletPath()).thenReturn(servletPath);
        when(req.getRequestURI()).thenReturn("/wildbook" + servletPath + path);
        when(req.getPathInfo()).thenReturn(path);
        when(req.getQueryString()).thenReturn(query);
        when(req.getParameterNames()).thenReturn(Collections.enumeration(params.keySet()));
        for (Map.Entry<String, String> e : params.entrySet()) {
            when(req.getParameter(e.getKey())).thenReturn(e.getValue());
            when(req.getParameterValues(e.getKey())).thenReturn(new String[] { e.getValue() });
        }
        return req;
    }

    private static Call run(HttpServletRequest req) throws Exception {
        Call call = new Call();
        call.resp = mock(HttpServletResponse.class);
        when(call.resp.getWriter()).thenReturn(new PrintWriter(call.body, true));
        RestServlet servlet = spy(new RestServlet());
        doNothing().when(servlet).doGet(any(), any());
        servlet.service(req, call.resp);
        call.reachedGet = mockingDetails(servlet).getInvocations().stream()
            .anyMatch(i -> i.getMethod().getName().equals("doGet"));
        return call;
    }

    private static void assertRefused(Call call) {
        assertFalse(call.reachedGet, "refused before any query runs");
        verify(call.resp).setStatus(403);
        assertTrue(call.body.toString().contains(RestClassGuard.MESSAGE), call.body.toString());
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    @Test void classGetIsRefused() throws Exception {
        assertRefused(run(request("/rest", "/org.ecocean.auth.ServiceKey", null, Map.of())));
        assertRefused(run(request("/rest", "/org.ecocean.auth.ServiceKey/some-key-id", null, Map.of())));
        // entity-name form and a filter on the class path
        assertRefused(run(request("/rest", "/ServiceKey", "revokedAt == null", Map.of())));
        assertRefused(run(request("/rest", "/ORG.ECOCEAN.AUTH.SERVICEKEY", null, Map.of())));
    }

    @Test void jdoqlQueryNamingServiceKeyIsRefused() throws Exception {
        String projection = "SELECT secretHash FROM org.ecocean.auth.ServiceKey";
        // GET /rest/jdoql?<query> (raw query string, decoded once by the servlet)
        assertRefused(run(request("/rest", "/jdoql", enc(projection), Map.of())));
        // GET /rest/query?<query>, double-encoded
        assertRefused(run(request("/rest", "/query", enc(enc(projection)), Map.of())));
        // GET /rest/anything?query=<query> (parameter, decoded again by the servlet)
        assertRefused(run(request("/rest", "/x", "query=" + enc(enc(projection)),
            Map.of("query", enc(projection)))));
        assertRefused(run(request("/rest", "/jdoql", enc("select FROM org.ecocean.Encounter WHERE "
            + "catalogNumber == 'x' && (SELECT count(this) FROM servicekey) > 0"), Map.of())));
    }

    @Test void normalClassGetIsUnaffected() throws Exception {
        Call byClass = run(request("/rest", "/org.ecocean.Encounter", null, Map.of()));
        assertTrue(byClass.reachedGet, "a normal class GET reaches doGet");
        verify(byClass.resp, never()).setStatus(403);
        Call query = run(request("/rest", "/jdoql", enc("SELECT FROM org.ecocean.Encounter WHERE sex == 'male'"),
            Map.of()));
        assertTrue(query.reachedGet, "a normal query reaches doGet");
        verify(query.resp, never()).setStatus(403);
    }

    @Test void percentLiteralQueryIsAllowed() throws Exception {
        // raw "50%25" decodes to "50%"; the literal percent is not a decode failure
        Call literal = run(request("/rest", "/jdoql", "SELECT FROM org.ecocean.Encounter WHERE comments == '50%25'",
            Map.of()));
        assertTrue(literal.reachedGet, "an encoded percent literal reaches doGet");
        verify(literal.resp, never()).setStatus(403);
        Call twice = run(request("/rest", "/jdoql", enc(enc("comments == '50%'")), Map.of()));
        assertTrue(twice.reachedGet, "a double-encoded percent literal reaches doGet");
        verify(twice.resp, never()).setStatus(403);
        // a forbidden name is still refused after the percent literal is decoded
        assertRefused(run(request("/rest", "/jdoql", "50%25 " + enc(enc("FROM ServiceKey")), Map.of())));
    }

    @Test void nonAsciiHexEscapesAreDecodedLikeTheServlet() throws Exception {
        // URLDecoder accepts any digit Character.digit(c, 16) accepts, e.g. fullwidth digits
        StringBuilder escaped = new StringBuilder();
        for (char c : "ServiceKey".toCharArray()) {
            escaped.append('%');
            // fullwidth forms of the two hex digits
            String hex = String.format("%02X", (int)c);
            for (char h : hex.toCharArray())
                escaped.append((char)(h >= 'A' ? 0xFF21 + (h - 'A') : 0xFF10 + (h - '0')));
        }
        String query = escaped.toString();
        assertEquals("ServiceKey", java.net.URLDecoder.decode(query, "UTF-8"), "the servlet decodes this form");
        assertRefused(run(request("/rest", "/jdoql", "SELECT FROM org.ecocean.auth." + query, Map.of())));
        assertRefused(run(request("/rest", "/jdoql", query, Map.of())));
    }

    @Test void filterResultDropsServiceKeys() {
        RestServlet servlet = new RestServlet();
        Encounter enc = new Encounter();
        ServiceKey key = new ServiceKey();
        List<?> rows = (List<?>)servlet.filterResult(Arrays.asList(enc, key));
        assertEquals(Collections.singletonList(enc), rows, "service keys dropped, others kept");
        assertThrows(NucleusUserException.class, () -> servlet.filterResult(key), "single key refused");
        List<String> plain = Arrays.asList("a", "b");
        assertEquals(plain, servlet.filterResult(plain), "unrelated results unchanged");
    }
}
