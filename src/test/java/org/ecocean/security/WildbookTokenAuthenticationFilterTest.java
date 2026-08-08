package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.util.Base64;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.api.auth.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WildbookTokenAuthenticationFilterTest {

    private JwtService realService; // genuine RS256 keypair; sign + verify both work
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private StringWriter out;

    /** Filter with seams overridden: fixed context, real JwtService, canned username. */
    private WildbookTokenAuthenticationFilter filterFor(String requestContext,
        String resolvedUsername) {
        return new WildbookTokenAuthenticationFilter() {
            @Override protected String expectedContext() {
                return "context0";
            }
            @Override protected String requestContext(HttpServletRequest r) {
                return requestContext;
            }
            @Override protected JwtService jwtService(String context) {
                return realService;
            }
            @Override protected String lookupUsername(String context, String uuid) {
                return resolvedUsername;
            }
        };
    }

    @BeforeEach void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        String priv = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        String pub = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        realService = JwtService.fromBase64Keys(priv, pub, "wildbook", "wildbook-scoped-api");

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        out = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(out));
    }

    @Test void noBearer_passesThroughUnchanged() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response); // original request, untouched
        verify(response, never()).setStatus(anyInt());
    }

    @Test void validBearer_wrapsRequestWithUsernamePrincipal() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");

        filterFor("context0", "alice").doFilterInternal(request, response, chain);

        ArgumentCaptor<javax.servlet.ServletRequest> cap =
            ArgumentCaptor.forClass(javax.servlet.ServletRequest.class);
        verify(chain).doFilter(cap.capture(), eq(response));
        HttpServletRequest wrapped = (HttpServletRequest) cap.getValue();
        Principal p = wrapped.getUserPrincipal();
        assertEquals("alice", p.toString(), "principal.toString() must yield the username");
        assertEquals(Boolean.TRUE, wrapped.getAttribute(
            WildbookTokenAuthenticationFilter.TOKEN_AUTH_ATTR), "token-auth marker set");
    }

    @Test void validBearer_setsVerifiedContextAttribute() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        org.mockito.ArgumentCaptor<javax.servlet.ServletRequest> cap =
            org.mockito.ArgumentCaptor.forClass(javax.servlet.ServletRequest.class);
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(chain).doFilter(cap.capture(), eq(response));
        javax.servlet.http.HttpServletRequest wrapped =
            (javax.servlet.http.HttpServletRequest) cap.getValue();
        assertEquals("context0",
            wrapped.getAttribute(WildbookTokenAuthenticationFilter.TOKEN_CONTEXT_ATTR),
            "filter sets the verified context attribute for SearchApi");
    }

    @Test void expiredToken_returns401() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", -60_000L); // expired 60s ago (avoids sub-second boundary flake)
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void wrongContextClaim_returns401() throws Exception {
        String token = realService.sign("user-uuid-1", "contextX", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void requestContextDrift_returns401() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        // token says context0, but the resolved request context is context1 (e.g. ?context=, cookie, host)
        filterFor("context1", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void unknownUser_returns401() throws Exception {
        String token = realService.sign("ghost-uuid", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", null).doFilterInternal(request, response, chain); // lookup -> null
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void disallowedMethod_returns405() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("DELETE");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(405);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void tamperedToken_returns401() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L) + "tamper";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void lowercaseBearerScheme_stillAuthenticates() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("bearer " + token); // lowercase scheme
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(chain).doFilter(any(), eq(response)); // wrapped + forwarded, not treated as no-token
        verify(response, never()).setStatus(anyInt());
    }

    @Test void blankBearerToken_returns401() throws Exception {
        // "Bearer " with no token -> token becomes "" -> jjwt throws IllegalArgumentException
        when(request.getHeader("Authorization")).thenReturn("Bearer ");
        when(request.getMethod()).thenReturn("POST");
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test void validBearerGet_wrapsRequestWithUsernamePrincipal() throws Exception {
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("GET");

        org.mockito.ArgumentCaptor<javax.servlet.ServletRequest> cap =
            org.mockito.ArgumentCaptor.forClass(javax.servlet.ServletRequest.class);
        filterFor("context0", "alice").doFilterInternal(request, response, chain);
        verify(chain).doFilter(cap.capture(), eq(response));
        javax.servlet.http.HttpServletRequest wrapped =
            (javax.servlet.http.HttpServletRequest) cap.getValue();
        assertEquals("alice", wrapped.getUserPrincipal().getName(), "getName() == username");
        assertEquals("alice", wrapped.getUserPrincipal().toString(), "toString() == username");
        verify(response, never()).setStatus(anyInt());
    }

    @Test void notConfigured_returns503() throws Exception {
        JwtService noKeys = JwtService.fromBase64Keys(null, null, "wildbook", "wildbook-scoped-api");
        String token = realService.sign("user-uuid-1", "context0", 60_000L);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(request.getMethod()).thenReturn("POST");
        WildbookTokenAuthenticationFilter f = new WildbookTokenAuthenticationFilter() {
            @Override protected String expectedContext() {
                return "context0";
            }
            @Override protected String requestContext(HttpServletRequest r) {
                return "context0";
            }
            @Override protected JwtService jwtService(String context) {
                return noKeys; // canVerify() == false
            }
            @Override protected String lookupUsername(String context, String uuid) {
                return "alice";
            }
        };
        f.doFilterInternal(request, response, chain);
        verify(response).setStatus(503);
        verify(chain, never()).doFilter(any(), any());
    }
}
