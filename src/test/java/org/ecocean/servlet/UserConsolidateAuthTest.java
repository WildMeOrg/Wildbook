package org.ecocean.servlet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class UserConsolidateAuthTest {
    @Test void nonAdminGets403() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.isUserInRole("admin")).thenReturn(false);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));

        try (MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class)) {
            new UserConsolidate().doPost(request, response);
            assertEquals(0, shepherds.constructed().size(), "no Shepherd for a non-admin");
        }
        verify(response).setStatus(403);
        assertTrue(body.toString().contains("\"success\":false"), "JSON error body");
    }
}
