package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ecocean.Encounter;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

class CanUserAccessTest {

    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    StringWriter responseOut;
    PrintWriter writer;

    /** Wraps a byte array as a ServletInputStream for mocking getInputStream(). */
    private static ServletInputStream servletInputStreamOf(byte[] bytes) {
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        return new ServletInputStream() {
            @Override public int read() throws IOException { return bais.read(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener rl) {}
        };
    }

    @BeforeEach
    void setUp() throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        responseOut = new StringWriter();
        writer = new PrintWriter(responseOut);
        when(mockResponse.getWriter()).thenReturn(writer);
        // getContext(request) reads servletContext + contextPath
        when(mockRequest.getServletContext()).thenReturn(null);
        when(mockRequest.getContextPath()).thenReturn("");
        // default: reasonable content-type and size for tests that reach parsing
        when(mockRequest.getContentType()).thenReturn("application/json");
        when(mockRequest.getContentLengthLong()).thenReturn(100L);
    }

    /**
     * (a) Non-admin caller → 403.
     * The PRIMARY admin gate must reject any authenticated user who is not admin.
     */
    @Test
    void nonAdminCaller_returns403() throws Exception {
        User callerUser = mock(User.class);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    doNothing().when(mock).beginDBTransaction();
                    doNothing().when(mock).setAction(anyString());
                    doNothing().when(mock).rollbackAndClose();
                    when(mock.getUser(any(HttpServletRequest.class))).thenReturn(callerUser);
                    when(callerUser.isAdmin(mock)).thenReturn(false);
                })) {
            CanUserAccess servlet = new CanUserAccess();
            servlet.doPost(mockRequest, mockResponse);

            writer.flush();
            verify(mockResponse).setStatus(403);
            String body = responseOut.toString();
            assertFalse(body.isEmpty(), "response body must not be empty");
            JSONObject json = new JSONObject(body);
            assertFalse(json.optBoolean("success", true), "success must be false");
            assertEquals("admin required", json.optString("error"), "error message");
        }
    }

    /**
     * (b) Admin caller, target user, two encounters:
     *   - enc1: canUserView → true  → must appear in accessible
     *   - enc2: canUserView → false → must NOT appear
     */
    @Test
    void adminCaller_filtersEncountersByCanUserView() throws Exception {
        User callerUser = mock(User.class);
        User targetUser = mock(User.class);

        Encounter enc1 = mock(Encounter.class);
        Encounter enc2 = mock(Encounter.class);

        String enc1Id = "enc-visible-001";
        String enc2Id = "enc-hidden-002";
        String targetUuid = "target-uuid-abc";

        JSONObject reqBody = new JSONObject();
        reqBody.put("userUuid", targetUuid);
        reqBody.put("encounterIds", new JSONArray().put(enc1Id).put(enc2Id));

        when(mockRequest.getInputStream()).thenReturn(
            servletInputStreamOf(reqBody.toString().getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    doNothing().when(mock).beginDBTransaction();
                    doNothing().when(mock).setAction(anyString());
                    doNothing().when(mock).rollbackAndClose();
                    when(mock.getUser(any(HttpServletRequest.class))).thenReturn(callerUser);
                    when(callerUser.isAdmin(mock)).thenReturn(true);
                    when(mock.getUserByUUID(targetUuid)).thenReturn(targetUser);
                    when(mock.getEncounter(enc1Id)).thenReturn(enc1);
                    when(mock.getEncounter(enc2Id)).thenReturn(enc2);
                    when(enc1.canUserView(targetUser, mock)).thenReturn(true);
                    when(enc2.canUserView(targetUser, mock)).thenReturn(false);
                })) {
            CanUserAccess servlet = new CanUserAccess();
            servlet.doPost(mockRequest, mockResponse);

            writer.flush();
            verify(mockResponse).setStatus(200);
            String body = responseOut.toString();
            JSONObject json = new JSONObject(body);
            JSONArray accessible = json.getJSONArray("accessible");
            assertEquals(1, accessible.length(), "only one encounter should be accessible");
            assertEquals(enc1Id, accessible.getString(0), "the visible encounter id");
        }
    }

    /**
     * (c) Admin caller, unknown target uuid (getUserByUUID → null) → empty accessible.
     * Fail-closed: unknown users see nothing.
     */
    @Test
    void unknownTargetUuid_returnsEmptyAccessible() throws Exception {
        User callerUser = mock(User.class);

        String unknownUuid = "no-such-user-uuid";
        JSONObject reqBody = new JSONObject();
        reqBody.put("userUuid", unknownUuid);
        reqBody.put("encounterIds", new JSONArray().put("enc-aaa").put("enc-bbb"));

        when(mockRequest.getInputStream()).thenReturn(
            servletInputStreamOf(reqBody.toString().getBytes(StandardCharsets.UTF_8)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    doNothing().when(mock).beginDBTransaction();
                    doNothing().when(mock).setAction(anyString());
                    doNothing().when(mock).rollbackAndClose();
                    when(mock.getUser(any(HttpServletRequest.class))).thenReturn(callerUser);
                    when(callerUser.isAdmin(mock)).thenReturn(true);
                    when(mock.getUserByUUID(unknownUuid)).thenReturn(null);
                })) {
            CanUserAccess servlet = new CanUserAccess();
            servlet.doPost(mockRequest, mockResponse);

            writer.flush();
            verify(mockResponse).setStatus(200);
            String body = responseOut.toString();
            JSONObject json = new JSONObject(body);
            JSONArray accessible = json.getJSONArray("accessible");
            assertEquals(0, accessible.length(), "unknown user sees no encounters");
        }
    }
}
