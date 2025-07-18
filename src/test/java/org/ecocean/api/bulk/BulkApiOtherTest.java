package org.ecocean.api;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.api.bulk.BulkImportUtil;
import org.ecocean.api.BulkImport;
import org.ecocean.api.UploadedFiles;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.servlet.ReCAPTCHA;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.ecocean.User;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
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

class BulkApiOtherTest {
    PersistenceManagerFactory mockPMF;
    PersistenceManager mockPM = mock(PersistenceManager.class);
    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    BulkImport apiServlet;
    StringWriter responseOut;
    List<File> emptyFiles = new ArrayList<File>();

    @BeforeEach void setUp()
    throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockPMF = mock(PersistenceManagerFactory.class);
        apiServlet = new BulkImport();

        responseOut = new StringWriter();
        PrintWriter writer = new PrintWriter(responseOut);
        when(mockResponse.getWriter()).thenReturn(writer);
    }

    @Test void apiGetNonSuccess()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);

        // this is no user
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

        // non-admin, but some mocked ImportTask which does not exist (404)
        setUp(); // reset
        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/bulk-import/00000000-0000-0000-0000-000000000000");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            doNothing().when(mock).beginDBTransaction();
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

        // fake the ImportTask existing, but not owned by user (403)
        setUp(); // reset
        ImportTask itask = mock(ImportTask.class);
        when(itask.getCreator()).thenReturn(null);
        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/bulk-import/00000000-0000-0000-0000-000000000000");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getImportTask(any(String.class))).thenReturn(itask);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(403);
                assertFalse(jout.getBoolean("success"));
            }
        }

        // fail at /files flavor due to no such id (404)
        setUp(); // reset
        File dir = new File("/tmp/does-not-exist");
        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/bulk-import/00000000-0000-0000-0000-000000000000/files");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getImportTask(any(String.class))).thenReturn(itask);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<UploadedFiles> mockUF = mockStatic(UploadedFiles.class,
                        org.mockito.Answers.CALLS_REAL_METHODS)) {
                    mockUF.when(() -> UploadedFiles.getUploadDir(any(HttpServletRequest.class),
                        any(String.class), any(Boolean.class))).thenReturn(dir);
                    apiServlet.doGet(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(404);
                    assertFalse(jout.getBoolean("success"));
                }
            }
        }
    }

    @Test void apiGetSuccess()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
        List<ImportTask> fakeTaskList = new ArrayList<ImportTask>();
        ImportTask fakeTask = mock(ImportTask.class);
        fakeTaskList.add(fakeTask);
        when(fakeTask.iaSummaryJson(any(Shepherd.class))).thenReturn(new JSONObject());

        // non-admin get-list
        setUp(); // reset
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import/");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getImportTasksForUser(any(User.class))).thenReturn(fakeTaskList);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(200);
                assertTrue(jout.getBoolean("success"));
                assertEquals(jout.getJSONArray("tasks").length(), 1);
            }
        }

        // admin get-list
        setUp(); // reset
        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import/");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getImportTasks()).thenReturn(fakeTaskList);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(200);
                assertTrue(jout.getBoolean("success"));
                assertEquals(jout.getJSONArray("tasks").length(), 1);
            }
        }

        // get single (admin, but meh very close to owner)
        setUp(); // reset
        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/bulk-import/00000000-0000-0000-0000-000000000000");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getImportTask(any(String.class))).thenReturn(fakeTask);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doGet(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(200);
                assertTrue(jout.getBoolean("success"));
                jout.getJSONObject("task");
            }
        }

        // list some files as if they were there!
        setUp(); // reset
        File dir = mock(File.class);
        when(dir.exists()).thenReturn(true);
        File[] fakeListing = new File[] { new File("/fake/filename") };
        when(dir.listFiles()).thenReturn(fakeListing);

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/bulk-import/00000000-0000-0000-0000-000000000000/files");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<UploadedFiles> mockUF = mockStatic(UploadedFiles.class,
                        org.mockito.Answers.CALLS_REAL_METHODS)) {
                    mockUF.when(() -> UploadedFiles.getUploadDir(any(HttpServletRequest.class),
                        any(String.class), any(Boolean.class))).thenReturn(dir);
                    apiServlet.doGet(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(200);
                    assertTrue(jout.getBoolean("success"));
                    assertEquals(jout.getJSONArray("files").length(), 1);
                }
            }
        }
    }

    @Test void apiDeleteSuccess()
    throws ServletException, IOException {
        User user = mock(User.class);
        Encounter testEnc = mock(Encounter.class);
        List<Encounter> testEncounters = new ArrayList<Encounter>();

        testEncounters.add(testEnc);

        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
        ImportTask fakeTask = mock(ImportTask.class);
        when(fakeTask.getEncounters()).thenReturn(testEncounters);

        setUp(); // reset
        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/bulk-import/00000000-0000-0000-0000-000000000000");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getImportTask(any(String.class))).thenReturn(fakeTask);
            when(mock.getPM()).thenReturn(mockPM);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doDelete(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(204);
                assertTrue(jout.getBoolean("success"));
                verify(mockPM).deletePersistent(any(ImportTask.class));
                verify(fakeTask).removeEncounter(testEnc);
            }
        }
    }

    @Test void apiDeleteFailNoUser()
    throws ServletException, IOException {
        setUp(); // reset
        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/bulk-import/00000000-0000-0000-0000-000000000000");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(null);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doDelete(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(400);
                assertFalse(jout.getBoolean("success"));
                assertEquals(jout.getString("error"),
                    "java.io.IOException: must provide id and user");
            }
        }
    }

    @Test void apiDeleteFailNoAccess()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("it-is-me");
        Encounter testEnc = mock(Encounter.class);
        when(testEnc.getAssignedUsername()).thenReturn("not-me");
        List<Encounter> testEncounters = new ArrayList<Encounter>();
        testEncounters.add(testEnc);

        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
        ImportTask fakeTask = mock(ImportTask.class);
        when(fakeTask.getEncounters()).thenReturn(testEncounters);

        setUp(); // reset
        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/bulk-import/00000000-0000-0000-0000-000000000000");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getImportTask(any(String.class))).thenReturn(fakeTask);
            when(mock.getPM()).thenReturn(mockPM);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doDelete(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(403);
                assertFalse(jout.getBoolean("success"));
            }
        }
    }
}
