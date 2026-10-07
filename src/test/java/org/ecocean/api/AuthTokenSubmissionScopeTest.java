package org.ecocean.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Base64;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.CommonConfiguration;
import org.ecocean.User;
import org.ecocean.api.auth.JwtService;
import org.ecocean.api.submission.SubmissionPolicy;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthTokenSubmissionScopeTest {
    private void request(String scope, boolean enabled, boolean enrolled, int expected) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Basic " + Base64.getEncoder().encodeToString("pilot:password".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        when(request.getParameter("scope")).thenReturn(scope);
        HttpServletResponse response = mock(HttpServletResponse.class);
        StringWriter output = new StringWriter(); when(response.getWriter()).thenReturn(new PrintWriter(output));
        User user = mock(User.class); when(user.checkPassword("password")).thenReturn(true); when(user.getId()).thenReturn("pilot-id"); when(user.getUsername()).thenReturn("pilot");
        javax.jdo.Query query = mock(javax.jdo.Query.class);
        when(query.execute("pilot", org.ecocean.Role.API_SUBMISSION, "context0")).thenReturn(enrolled ? 1L : 0L);
        javax.jdo.PersistenceManager pm = mock(javax.jdo.PersistenceManager.class);
        when(pm.newQuery(eq(org.ecocean.Role.class), anyString())).thenReturn(query);
        JwtService jwt = mock(JwtService.class); when(jwt.isEnabled()).thenReturn(true);
        when(jwt.signSubmission(anyString(), anyString(), anyLong(), anyString())).thenReturn("submission-token");
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class, (m,c) -> { when(m.getUser("pilot")).thenReturn(user); when(m.getUserByUUID("pilot-id")).thenReturn(user); when(m.getPM()).thenReturn(pm); when(m.getContext()).thenReturn("context0"); });
             MockedStatic<CommonConfiguration> config = mockStatic(CommonConfiguration.class);
             MockedStatic<JwtService> js = mockStatic(JwtService.class)) {
            config.when(() -> CommonConfiguration.getApiAccessProperty("submissions.enabled", "context0")).thenReturn(Boolean.toString(enabled));

            js.when(() -> JwtService.fromConfig("context0")).thenReturn(jwt);
            new AuthToken().doPost(request, response);
            verify(response).setStatus(expected);
            if (expected == 200) {
                verify(jwt).signSubmission(eq("pilot-id"), eq("context0"), anyLong(), eq(scope));
                assertTrue(output.toString().contains("submission-token"));
            } else verify(jwt, never()).signSubmission(anyString(), anyString(), anyLong(), anyString());
        }
    }
    @Test void disabledAndUnenrolledCannotMintWriteScope() throws Exception {
        request(SubmissionPolicy.WRITE, false, true, 503);
        request(SubmissionPolicy.WRITE, true, false, 403);
    }
    @Test void enrolledUserExplicitlyOptsIntoWriteScope() throws Exception { request(SubmissionPolicy.WRITE, true, true, 200); }
    @Test void readScopeCanBeRenewedAfterAdmissionShutdown() throws Exception { request(SubmissionPolicy.READ, false, false, 200); }
    @Test void unsupportedScopeIsRejected() throws Exception { request("admin", true, true, 400); }
}
