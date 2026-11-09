package org.ecocean.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.api.submission.SubmissionStore;
import org.ecocean.api.submission.SubmissionPolicy;
import org.ecocean.security.SubmissionAuthenticationFilter;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubmissionsTest {
    private HttpServletResponse response(StringWriter out) throws Exception {
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(out)); return response;
    }
    @Test void directUnauthenticatedAccessIsJson401() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = response(new StringWriter());
        new Submissions().service(request, response);
        verify(response).setStatus(401); verify(response, atLeastOnce()).setHeader("Cache-Control", "no-store");
    }
    @Test void ownedStatusReturnsEtagWithoutAdmissionCheck() throws Exception {
        SubmissionStore store = mock(SubmissionStore.class);
        String id = "00000000-0000-4000-8000-000000000001";
        when(store.get("context0", "owner", id, false, false)).thenReturn(new JSONObject().put("id", id).put("revision", 4));
        Submissions servlet = new Submissions() { @Override protected SubmissionStore store() { return store; } };
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET"); when(request.getPathInfo()).thenReturn("/" + id);
        when(request.getAttribute(SubmissionAuthenticationFilter.ACTOR)).thenReturn(new SubmissionAuthenticationFilter.Actor("owner", false));
        StringWriter out = new StringWriter(); HttpServletResponse response = response(out);
        servlet.service(request, response);
        verify(response).setHeader("ETag", "\"4\""); assertEquals(id, new JSONObject(out.toString()).getString("id"));
    }
    @Test void missingPreconditionDoesNotTouchDraft() throws Exception {
        SubmissionStore store = mock(SubmissionStore.class);
        Submissions servlet = new Submissions() { @Override protected SubmissionStore store() { return store; } };
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("DELETE"); when(request.getPathInfo()).thenReturn("/00000000-0000-4000-8000-000000000001");
        when(request.getAttribute(SubmissionAuthenticationFilter.ACTOR)).thenReturn(new SubmissionAuthenticationFilter.Actor("owner", false));
        HttpServletResponse response = response(new StringWriter());
        try (org.mockito.MockedStatic<SubmissionPolicy> policy = mockStatic(SubmissionPolicy.class)) {
            servlet.service(request, response);
            verify(response).setStatus(428); verifyNoInteractions(store);
        }
    }
    @Test void capabilitiesFixtureComesFromTheRuntimeServlet() throws Exception {
        try (org.mockito.MockedStatic<org.ecocean.CommonConfiguration> config = mockStatic(org.ecocean.CommonConfiguration.class)) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET"); when(request.getPathInfo()).thenReturn("/capabilities");
        when(request.getAttribute(SubmissionAuthenticationFilter.ACTOR)).thenReturn(new SubmissionAuthenticationFilter.Actor("owner", false));
        StringWriter out = new StringWriter();
        new Submissions().service(request, response(out));
        JSONObject capabilities = new JSONObject(out.toString());
        assertEquals(200, capabilities.getJSONObject("limits").getInt("maxRows"));
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/submissions-capabilities.json"), out.toString());
        }
    }

}
