package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class AuthTokenStepUpTest {

    private HttpServletResponse resp(StringWriter out) throws Exception {
        HttpServletResponse r = mock(HttpServletResponse.class);
        when(r.getWriter()).thenReturn(new PrintWriter(out));
        return r;
    }
    private String basic(String u, String p) {
        return "Basic " + Base64.getEncoder().encodeToString((u + ":" + p).getBytes());
    }

    @Test void noBasicHeader_sessionOnly_is401() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(null); // session-only
        StringWriter out = new StringWriter();
        HttpServletResponse r = resp(out);
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            doNothing().when(m).rollbackAndClose();
        })) {
            new AuthToken().doPostForTest(req, r);
        }
        verify(r).setStatus(401);
    }

    @Test void wrongPassword_servletRejects_401() throws Exception {
        // Servlet-level check: a present-but-wrong Basic credential is rejected regardless of session.
        // (The full filter+session end-to-end is covered by the live smoke in the spec.)
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(basic("alice", "WRONG"));
        StringWriter out = new StringWriter();
        HttpServletResponse r = resp(out);
        User alice = mock(User.class);
        when(alice.checkPassword("WRONG")).thenReturn(false);
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
            doNothing().when(m).beginDBTransaction();
            doNothing().when(m).setAction(anyString());
            doNothing().when(m).rollbackAndClose();
            when(m.getUser("alice")).thenReturn(alice);
        })) {
            new AuthToken().doPostForTest(req, r);
        }
        verify(r).setStatus(401);
    }

    @Test void correctPassword_mints200WithToken() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(basic("alice", "right"));
        StringWriter out = new StringWriter();
        HttpServletResponse r = resp(out);
        User alice = mock(User.class);
        when(alice.checkPassword("right")).thenReturn(true);
        when(alice.getId()).thenReturn("uuid-alice");
        org.ecocean.api.auth.JwtService jwt = mock(org.ecocean.api.auth.JwtService.class);
        when(jwt.isEnabled()).thenReturn(true);
        when(jwt.sign(anyString(), anyString(), anyLong())).thenReturn("signed-jwt");
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m, c) -> {
                 doNothing().when(m).beginDBTransaction();
                 doNothing().when(m).setAction(anyString());
                 doNothing().when(m).rollbackAndClose();
                 when(m.getUser("alice")).thenReturn(alice);
             });
             org.mockito.MockedStatic<org.ecocean.api.auth.JwtService> js =
                 mockStatic(org.ecocean.api.auth.JwtService.class)) {
            js.when(() -> org.ecocean.api.auth.JwtService.fromConfig(anyString())).thenReturn(jwt);
            new AuthToken().doPostForTest(req, r);
        }
        verify(r).setStatus(200);
        verify(r).setHeader("Cache-Control", "no-store");
        String body = out.toString();
        assertTrue(body.contains("signed-jwt"), "response carries the minted token");
        assertTrue(body.contains("expiresInSeconds"), "response carries expiry");
    }
}
