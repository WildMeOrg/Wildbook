package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.ecocean.api.auth.JwtService;
import org.ecocean.api.submission.SubmissionException;
import org.ecocean.api.submission.SubmissionPolicy;
import org.ecocean.auth.ServiceKeyAuth;
import org.ecocean.auth.ServiceKeyException;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class AuthTokenServiceKeyTest {
    private static final String CREDENTIAL = "wbsvc_00000000-0000-4000-8000-000000000000.secret";

    private static final class Result {
        int status; String body; JwtService jwt; boolean committed; HttpServletResponse resp;
    }

    private static ServiceKeyAuth.Grant grant(long keyExpiresAt, String... scopes) {
        return new ServiceKeyAuth.Grant("svc-user-id", "key-id", "org-id", Set.of(scopes), keyExpiresAt);
    }

    /** Runs the servlet with ServiceKeyAuth.authenticate answering {@code grant} (or throwing {@code failure}). */
    private Result post(String authorization, String scope, ServiceKeyAuth.Grant grant,
        RuntimeException failure, RuntimeException admission) throws Exception {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader("Authorization")).thenReturn(authorization);
        when(req.getParameter("scope")).thenReturn(scope);
        HttpServletResponse resp = mock(HttpServletResponse.class);
        StringWriter out = new StringWriter();
        when(resp.getWriter()).thenReturn(new PrintWriter(out));
        JwtService jwt = mock(JwtService.class);
        when(jwt.isEnabled()).thenReturn(true);
        when(jwt.signUntil(anyString(), anyString(), anyLong(), any(), anyLong())).thenReturn("key-jwt");
        Result r = new Result();
        r.jwt = jwt;
        r.resp = resp;
        try (MockedConstruction<Shepherd> sh = mockConstruction(Shepherd.class,
                 (m, c) -> when(m.commitDBTransactionWithStatus()).thenReturn(true));
             MockedStatic<JwtService> js = mockStatic(JwtService.class);
             MockedStatic<ServiceKeyAuth> ska = mockStatic(ServiceKeyAuth.class, CALLS_REAL_METHODS);
             MockedStatic<SubmissionPolicy> sp = mockStatic(SubmissionPolicy.class, CALLS_REAL_METHODS)) {
            js.when(() -> JwtService.fromConfig(anyString())).thenReturn(jwt);
            if (failure != null) {
                ska.when(() -> ServiceKeyAuth.authenticate(any(), anyString(), anyLong())).thenThrow(failure);
            } else {
                ska.when(() -> ServiceKeyAuth.authenticate(any(), anyString(), anyLong())).thenReturn(grant);
            }
            if (admission != null) {
                sp.when(() -> SubmissionPolicy.requireAdmission(anyString(), anyString())).thenThrow(admission);
            } else {
                sp.when(() -> SubmissionPolicy.requireAdmission(anyString(), anyString())).then(i -> null);
            }
            new AuthToken().doPostForTest(req, resp);
            org.mockito.ArgumentCaptor<Integer> status = org.mockito.ArgumentCaptor.forClass(Integer.class);
            verify(resp).setStatus(status.capture());
            verify(resp).setHeader("Cache-Control", "no-store");
            r.status = status.getValue();
            r.body = out.toString();
            if (!sh.constructed().isEmpty()) {
                Shepherd m = sh.constructed().get(0);
                verify(m).rollbackAndClose();
                r.committed = mockingDetails(m).getInvocations().stream()
                    .anyMatch(i -> i.getMethod().getName().equals("commitDBTransactionWithStatus"));
            }
            if (r.status != 200) assertFalse(r.committed, "a refused exchange never commits");
        }
        return r;
    }

    private Result post(String scope, ServiceKeyAuth.Grant grant) throws Exception {
        return post("Bearer " + CREDENTIAL, scope, grant, null, null);
    }

    @Test void bearerServiceKeyMintsDataJwt() throws Exception {
        long keyExpiresAt = System.currentTimeMillis() + 30L * 24 * 3600 * 1000;
        Result r = post(null, grant(keyExpiresAt, "data:read"));
        assertEquals(200, r.status, r.body);
        JSONObject body = new JSONObject(r.body);
        assertEquals("key-jwt", body.getString("token"), "token");
        assertEquals("Bearer", body.getString("tokenType"), "token type");
        assertTrue(body.getLong("expiresInSeconds") <= 900, "at most 15 minutes: " + r.body);
        assertTrue(body.getLong("expiresInSeconds") > 0, "positive lifetime");
        assertEquals(keyExpiresAt, body.getLong("keyExpiresAt"), "key expiry exposed");
        assertFalse(body.has("scope"), "data token has no scope");
        assertFalse(r.body.contains("key-id") || r.body.contains("org-id") || r.body.contains("svc-user-id"),
            "no ids in the response");
        verify(r.jwt).signUntil(eq("svc-user-id"), eq("context0"), longThat(t -> t > 0 && t <= 900_000L),
            isNull(), eq(keyExpiresAt));
        assertTrue(r.committed, "lastUsedAt is committed on success");
    }

    @Test void scopeCeilingEnforced() throws Exception {
        Result r = post(null, grant(System.currentTimeMillis() + 3_600_000L, SubmissionPolicy.WRITE));
        assertEquals(403, r.status, r.body);
        assertEquals("scope not allowed for this key", new JSONObject(r.body).getString("error"), "error");
        verify(r.jwt, never()).signUntil(anyString(), anyString(), anyLong(), any(), anyLong());

        Result read = post(SubmissionPolicy.READ, grant(System.currentTimeMillis() + 3_600_000L, "data:read"));
        assertEquals(403, read.status, read.body);
        Result write = post(SubmissionPolicy.WRITE,
            grant(System.currentTimeMillis() + 3_600_000L, "data:read", SubmissionPolicy.READ));
        assertEquals(403, write.status, write.body);
        Result bogus = post("admin", grant(System.currentTimeMillis() + 3_600_000L, "data:read"));
        assertEquals(400, bogus.status, bogus.body);
    }

    @Test void writeImpliesSubmissionRead() throws Exception {
        long keyExpiresAt = System.currentTimeMillis() + 3_600_000L;
        Result r = post(SubmissionPolicy.READ, grant(keyExpiresAt, SubmissionPolicy.WRITE));
        assertEquals(200, r.status, r.body);
        assertEquals(SubmissionPolicy.READ, new JSONObject(r.body).getString("scope"), "scope echoed");
        verify(r.jwt).signUntil(eq("svc-user-id"), eq("context0"), anyLong(), eq(SubmissionPolicy.READ),
            eq(keyExpiresAt));
    }

    @Test void submissionWriteChecksAdmission() throws Exception {
        long keyExpiresAt = System.currentTimeMillis() + 3_600_000L;
        Result denied = post("Bearer " + CREDENTIAL, SubmissionPolicy.WRITE, grant(keyExpiresAt, SubmissionPolicy.WRITE),
            null, new SubmissionException(403, "ACCESS_DENIED", "Account requires the api-submission role"));
        assertEquals(403, denied.status, denied.body);
        verify(denied.jwt, never()).signUntil(anyString(), anyString(), anyLong(), any(), anyLong());

        Result ok = post(SubmissionPolicy.WRITE, grant(keyExpiresAt, SubmissionPolicy.WRITE));
        assertEquals(200, ok.status, ok.body);
        verify(ok.jwt).signUntil(eq("svc-user-id"), eq("context0"), anyLong(), eq(SubmissionPolicy.WRITE),
            eq(keyExpiresAt));
    }

    @Test void jwtNeverOutlivesKey() {
        long now = System.currentTimeMillis();
        assertEquals(30_000L, ServiceKeyAuth.exchangeTtlMillis(1_800_000L, now + 30_000L, now), "key expiry wins");
        assertEquals(900_000L, ServiceKeyAuth.exchangeTtlMillis(1_800_000L, now + 3_600_000L, now), "900 s cap");
        assertEquals(60_000L, ServiceKeyAuth.exchangeTtlMillis(60_000L, now + 3_600_000L, now), "configured ttl");
    }

    @Test void keyFailuresPassThroughUnchanged() throws Exception {
        for (String message : new String[] { "invalid service key", "service key expired", "service key revoked",
            "service key unavailable; contact your Wildbook administrator" }) {
            Result r = post("Bearer " + CREDENTIAL, null, null, new ServiceKeyException(401, message), null);
            assertEquals(401, r.status, r.body);
            JSONObject body = new JSONObject(r.body);
            assertFalse(body.getBoolean("success"), "error shape");
            assertEquals(message, body.getString("error"), "message passes through");
            verify(r.resp, never()).setHeader(eq("WWW-Authenticate"), anyString());
        }
    }

    @Test void nonServiceBearerIs401() throws Exception {
        Result r = post("Bearer eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1In0.c2ln", null, null,
            new IllegalStateException("must not be called"), null);
        assertEquals(401, r.status, r.body);
        assertEquals("invalid service key", new JSONObject(r.body).getString("error"), "generic message");
        verify(r.jwt, never()).signUntil(anyString(), anyString(), anyLong(), any(), anyLong());
        verify(r.jwt, never()).sign(anyString(), anyString(), anyLong());
    }
}
