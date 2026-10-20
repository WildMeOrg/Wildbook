package org.ecocean.security;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.api.auth.JwtService;
import org.ecocean.api.submission.SubmissionException;
import org.ecocean.api.submission.SubmissionPolicy;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SubmissionAuthenticationFilterTest {
    static JwtService jwt;
    final String user = "00000000-0000-4000-8000-000000000001";
    @BeforeAll static void keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        jwt = JwtService.fromBase64Keys(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
            Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()), "test", "test");
    }
    static class Filter extends SubmissionAuthenticationFilter {
        boolean enrolled = true;
        String context = "context0";
        @Override protected JwtService jwtService() { return jwt; }
        @Override protected String requestContext(HttpServletRequest request) { return context; }
        @Override protected Actor lookup(String id) { return new Actor(id, false); }
        @Override protected void admission(String id) {
            if (!enrolled) throw new SubmissionException(403, "ACCESS_DENIED", "not enrolled");
        }
    }
    private void denied(String token, String method, int status, Filter filter) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(token == null ? null : "Bearer " + token);
        when(request.getMethod()).thenReturn(method);
        when(request.isUserInRole("admin")).thenReturn(true); // unrelated cookie never grants authority
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(request, response, chain);
        verify(response).setStatus(status); verifyNoInteractions(chain);
    }
    @Test void identityOnlyReadTokensDoNotGainSubmissionAccess() throws Exception {
        denied(jwt.sign(user, "context0", 60000), "POST", 401, new Filter());
        denied(null, "POST", 401, new Filter());
        denied("invalid", "POST", 401, new Filter());
    }
    @Test void scopeExpiryContextAndEnrollmentAreEnforced() throws Exception {
        denied(jwt.signSubmission(user, "context0", 60000, SubmissionPolicy.READ), "POST", 403, new Filter());
        denied(jwt.signSubmission(user, "context0", -60000, SubmissionPolicy.WRITE), "POST", 401, new Filter());
        denied(jwt.signSubmission(user, "other", 60000, SubmissionPolicy.WRITE), "POST", 401, new Filter());
        Filter filter = new Filter(); filter.context = "other";
        denied(jwt.signSubmission(user, "context0", 60000, SubmissionPolicy.WRITE), "POST", 401, filter);
        filter.context = "context0"; filter.enrolled = false;
        denied(jwt.signSubmission(user, "context0", 60000, SubmissionPolicy.WRITE), "POST", 403, filter);
    }
    @Test void explicitReadCapabilityRetainsStatusAccessAfterUnenrollment() throws Exception {
        Filter filter = new Filter(); filter.enrolled = false;
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwt.signSubmission(user, "context0", 60000, SubmissionPolicy.READ));
        when(request.isUserInRole("admin")).thenReturn(true);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilterInternal(request, response, chain);
        ArgumentCaptor<ServletRequest> forwarded = ArgumentCaptor.forClass(ServletRequest.class);
        verify(chain).doFilter(forwarded.capture(), eq(response));
        HttpServletRequest wrapped = (HttpServletRequest)forwarded.getValue();
        assertEquals(user, wrapped.getRemoteUser()); assertFalse(wrapped.isUserInRole("admin"));
        assertEquals(user, ((SubmissionAuthenticationFilter.Actor)wrapped.getAttribute(SubmissionAuthenticationFilter.ACTOR)).id);
        verify(request, never()).getSession();
    }

    @Test void submissionTokensAreRejectedByLegacySearchFilter() throws Exception {
        String token = jwt.signSubmission(user, "context0", 60000, SubmissionPolicy.WRITE);
        assertThrows(io.jsonwebtoken.JwtException.class, () -> jwt.verify(token));
        WildbookTokenAuthenticationFilter legacy = new WildbookTokenAuthenticationFilter() {
            @Override protected String expectedContext() { return "context0"; }
            @Override protected String requestContext(HttpServletRequest request) { return "context0"; }
            @Override protected JwtService jwtService(String context) { return jwt; }
        };
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        FilterChain chain = mock(FilterChain.class);
        legacy.doFilterInternal(request, response, chain);
        verify(response).setStatus(401); verifyNoInteractions(chain);
    }
}
