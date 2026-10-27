package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import javax.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

class SameOriginCheckTest {
    private static boolean allows(String origin, String host) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Origin")).thenReturn(origin);
        when(req.getHeader("Host")).thenReturn(host);
        return SameOriginCheck.allows(req);
    }

    @Test void absentOriginIsAllowed() {
        assertTrue(allows(null, "wb.example"), "no Origin (curl, server-to-server)");
        assertTrue(allows(null, null), "no Origin and no Host");
    }

    @Test void matchingHostIsAllowed() {
        assertTrue(allows("https://wb.example", "wb.example"));
        assertTrue(allows("http://wb.example", "wb.example"), "scheme ignored");
        assertTrue(allows("https://WB.Example", "wb.EXAMPLE"), "case-insensitive");
        assertTrue(allows("https://wb.example:443", "wb.example"), "default port in Origin");
        assertTrue(allows("https://wb.example", "wb.example:443"), "default port in Host");
        assertTrue(allows("http://wb.example:80", "wb.example"), "port 80 dropped");
        assertTrue(allows("https://wb.example:8443", "wb.example:8443"), "same non-default port");
    }

    @Test void foreignOrMalformedOriginIsRefused() {
        assertFalse(allows("https://evil.example", "wb.example"), "foreign host");
        assertFalse(allows("https://wb.example.evil.example", "wb.example"), "suffix trick");
        assertFalse(allows("https://wb.example:8443", "wb.example"), "other port");
        assertFalse(allows("https://wb.example:8443", "wb.example:9443"), "different port");
        assertFalse(allows("https://wb.example", "wb.example:8443"), "port only on Host");
        assertFalse(allows("null", "wb.example"), "opaque origin");
        assertFalse(allows("not a uri at all", "wb.example"), "unparseable");
        assertFalse(allows("https://wb.example/path", "wb.example"), "path");
        assertFalse(allows("https://user@wb.example", "wb.example"), "userinfo");
        assertFalse(allows("https://wb.example", null), "Origin without Host");
        assertFalse(allows("https://wb.example", " "), "Origin with blank Host");
    }
}
