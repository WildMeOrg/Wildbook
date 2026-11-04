package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
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

/** Service key records are not served by LightRestServlet. */
class LightRestServletServiceKeyTest {
    private static final class Call {
        HttpServletResponse resp; StringWriter body = new StringWriter(); LightRestServlet servlet;
    }

    private static Call run(HttpServletRequest req) throws Exception {
        Call call = new Call();
        call.resp = mock(HttpServletResponse.class);
        when(call.resp.getWriter()).thenReturn(new PrintWriter(call.body, true));
        call.servlet = spy(new LightRestServlet());
        doNothing().when(call.servlet).doGet(any(), any());
        call.servlet.service(req, call.resp);
        return call;
    }

    private static void assertRefused(Call call) throws Exception {
        verify(call.servlet, never()).doGet(any(), any());
        verify(call.resp).setStatus(403);
        assertTrue(call.body.toString().contains(RestClassGuard.MESSAGE), call.body.toString());
    }

    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }

    private static HttpServletRequest request(String path, String query) {
        return RestServletServiceKeyTest.request("/LightRest", path, query, Map.of());
    }

    @Test void classGetIsRefused() throws Exception {
        assertRefused(run(request("/org.ecocean.auth.ServiceKey", null)));
        assertRefused(run(request("/org.ecocean.auth.ServiceKey/some-key-id", null)));
    }

    @Test void jdoqlQueryNamingServiceKeyIsRefused() throws Exception {
        assertRefused(run(request("/jdoql", enc("SELECT secretHash FROM org.ecocean.auth.ServiceKey"))));
        assertRefused(run(request("/query", enc("SELECT FROM ServiceKey"))));
    }

    @Test void normalClassGetIsUnaffected() throws Exception {
        HttpServletRequest req = request("/jdoql", enc("SELECT FROM org.ecocean.Encounter WHERE sex == 'male'"));
        Call call = run(req);
        verify(call.servlet, times(1)).doGet(req, call.resp);
        verify(call.resp, never()).setStatus(403);
    }

    @Test void filterResultDropsServiceKeys() {
        LightRestServlet servlet = new LightRestServlet();
        Encounter enc = new Encounter();
        ServiceKey key = new ServiceKey();
        List<?> rows = (List<?>)servlet.filterResult(Arrays.asList(enc, key));
        assertEquals(Collections.singletonList(enc), rows, "service keys dropped, others kept");
        assertThrows(NucleusUserException.class, () -> servlet.filterResult(key), "single key refused");
    }
}
