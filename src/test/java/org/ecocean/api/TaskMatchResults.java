package org.ecocean.api;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.ia.Task;

import org.ecocean.api.GenericObject;
// import org.ecocean.CommonConfiguration;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.ecocean.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaskMatchResults {
    PersistenceManagerFactory mockPMF;
    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    GenericObject apiServlet;
    StringWriter responseOut;

    @BeforeEach void setUp()
    throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockPMF = mock(PersistenceManagerFactory.class);
        apiServlet = new GenericObject();

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

    @Test void apiGet401()
    throws ServletException, IOException {
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(401);
                assertFalse(jout.getBoolean("success"));
            }
        }
    }

    // basically tests api path without /match-results
    @Test void apiGetInvalidOperation()
    throws ServletException, IOException {
        User user = mock(User.class);
        String id = "some-id";

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id);
        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getTask(any(String.class))).thenReturn(null);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(400);
                assertFalse(jout.getBoolean("success"));
                assertTrue(jout.getString("debug").contains("invalid tasks operation"));
            }
        }
    }

    @Test void apiGet404()
    throws ServletException, IOException {
        User user = mock(User.class);
        String id = "404-id";

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getTask(any(String.class))).thenReturn(null);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(404);
                assertFalse(jout.getBoolean("success"));
            }
        }
    }

    @Test void apiGetSuccess()
    throws ServletException, IOException {
        User user = mock(User.class);

        // this doesnt really test the output value of "matchResultsRoot", since we
        // test task.matchResultsJson() seperately so dont care about the null value here
        Task task = mock(Task.class);
        String id = "ok-id";

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/tasks/" + id + "/match-results");
        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getTask(any(String.class))).thenReturn(task);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(200);
                assertTrue(jout.getBoolean("success"));
            }
        }
    }
}
