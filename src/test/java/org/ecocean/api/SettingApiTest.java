package org.ecocean.api;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;

import org.ecocean.Shepherd;
import org.ecocean.ShepherdPMF;
import org.ecocean.api.SiteSettings;
import org.ecocean.Setting;
import org.ecocean.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockConstruction;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

class SettingApiTest {

    PersistenceManagerFactory mockPMF;
    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    SiteSettings apiServlet;
    StringWriter responseOut;

    @BeforeEach
    void setUp() throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockPMF = mock(PersistenceManagerFactory.class);
        apiServlet = new SiteSettings();

        responseOut = new StringWriter();
        PrintWriter writer = new PrintWriter(responseOut);
        when(mockResponse.getWriter()).thenReturn(writer);
/*
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenThrow(new RuntimeException("ohmgee"));
            })) {
            mockRequest = mock(HttpServletRequest.class);
            mockResponse = mock(HttpServletResponse.class);
            apiServlet = new SiteSettings();
        }
*/
    }


    @Test void apiPost401() throws ServletException, IOException {
        try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
            mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
            apiServlet.doPost(mockRequest, mockResponse);
            responseOut.flush();
            JSONObject jout = new JSONObject(responseOut.toString());
            verify(mockResponse).setStatus(401);
            assertFalse(jout.getBoolean("success"));
        }
    }

    @Test void apiPostNonAdmin401() throws ServletException, IOException {
        User user = mock(User.class);
        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doPost(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(401);
                assertFalse(jout.getBoolean("success"));
            }
        }
    }

    @Test void apiPostBadPath() throws ServletException, IOException {
        User user = mock(User.class);
        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);

        // this prefix is pretty much guaranteed from web.xml, so we need it here before /bad-uri
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/site-settings/bad-uri");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                Exception ex = assertThrows(ServletException.class, () -> {
                    apiServlet.doPost(mockRequest, mockResponse);
                });
                assertTrue(ex.getMessage().contains("Bad path"));
            }
        }
    }
}
