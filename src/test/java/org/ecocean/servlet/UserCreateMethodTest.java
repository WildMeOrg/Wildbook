package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/** Account edits are accepted only by POST, and only from the same origin when an Origin is sent. */
class UserCreateMethodTest {

    @Test void getIs405() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getParameter(anyString())).thenReturn("x");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            new UserCreate().doGet(req, resp);
            assertTrue(sh.constructed().isEmpty(), "no Shepherd for GET");
        }
        verify(resp).setHeader("Allow", "POST");
        verify(resp).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        verify(resp, never()).getWriter();
    }

    @Test void foreignOriginPostIs403BeforeAnyDbWork() throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Origin")).thenReturn("https://evil.example");
        when(req.getHeader("Host")).thenReturn("wildbook.example");
        when(req.getParameter(anyString())).thenReturn("x");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class)) {
            new UserCreate().doPost(req, resp);
            assertTrue(sh.constructed().isEmpty(), "no Shepherd before the origin check");
        }
        verify(resp).sendError(HttpServletResponse.SC_FORBIDDEN);
        verify(resp, never()).getWriter();
    }
}
