package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class AuthTokenTest {

    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    StringWriter responseOut;
    PrintWriter writer;

    @BeforeEach
    void setUp() throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        responseOut = new StringWriter();
        writer = new PrintWriter(responseOut);
        when(mockResponse.getWriter()).thenReturn(writer);
        // getContext(request) reads servletContext; return "context0" for tests
        when(mockRequest.getServletContext()).thenReturn(null);
        when(mockRequest.getContextPath()).thenReturn("");
    }

    /**
     * Primary protection gate: unauthenticated caller (getUser returns null) → 401.
     * This is the most important unit-testable gate: the endpoint must reject requests
     * where Shiro passed but no User principal was resolved.
     */
    @Test
    void unauthenticated_returns401() throws Exception {
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    doNothing().when(mock).beginDBTransaction();
                    doNothing().when(mock).setAction(anyString());
                    doNothing().when(mock).rollbackAndClose();
                    when(mock.getUser(any(HttpServletRequest.class))).thenReturn(null);
                })) {
            AuthToken servlet = new AuthToken();
            servlet.doPost(mockRequest, mockResponse);

            writer.flush();
            verify(mockResponse).setStatus(401);
            String body = responseOut.toString();
            assertFalse(body.isEmpty(), "response body must not be empty");
            JSONObject json = new JSONObject(body);
            assertFalse(json.optBoolean("success", true), "success must be false");
            assertEquals("unauthenticated", json.optString("error"), "error message");
        }
    }

    /**
     * JwtService disabled (no private key configured) → 503.
     * Exercises the second gate after successful auth.
     */
    @Test
    void jwtDisabled_returns503() throws Exception {
        User fakeUser = new User();
        fakeUser.setUsername("test-user");

        try (MockedStatic<org.ecocean.CommonConfiguration> mockConfig =
                mockStatic(org.ecocean.CommonConfiguration.class);
             MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                 (mock, ctx) -> {
                     doNothing().when(mock).beginDBTransaction();
                     doNothing().when(mock).setAction(anyString());
                     doNothing().when(mock).rollbackAndClose();
                     when(mock.getUser(any(HttpServletRequest.class))).thenReturn(fakeUser);
                 })) {
            // All CommonConfiguration.getProperty calls return null → JwtService disabled
            mockConfig.when(() -> org.ecocean.CommonConfiguration.getProperty(
                anyString(), anyString())).thenReturn(null);

            AuthToken servlet = new AuthToken();
            servlet.doPost(mockRequest, mockResponse);

            writer.flush();
            verify(mockResponse).setStatus(503);
            String body = responseOut.toString();
            JSONObject json = new JSONObject(body);
            assertFalse(json.optBoolean("success", true), "success must be false");
            assertEquals("token issuance not configured", json.optString("error"));
        }
    }
}
