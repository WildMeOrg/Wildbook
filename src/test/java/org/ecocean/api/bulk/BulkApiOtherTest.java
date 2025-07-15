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
        when(fakeTask.iaSummaryJson()).thenReturn(new JSONObject());

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

/*
   System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> " + jout.toString(10));
    @Test void apiPostSuccess() throws ServletException, IOException {
        User user = mock(User.class);
        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);

        String requestBody = "{\"value\": [\"xx\"]}";
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/site-settings/language/site");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

        List langList = new ArrayList<String>();
        langList.add("ok");
        langList.add("yes");
        List testLangs = new ArrayList<String>();
        testLangs.add("xy");
        // FIXME test is broken because i think update to setting tries to write to db
        if (requestBody != null) return;

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
                when(mock.getSettingValue("language", "available")).thenReturn("xxxxxxx");
                //when(mock.storeSetting(any(Setting.class))).doNothing();
                doNothing().when(mock).storeSetting(any(Setting.class));
                doNothing().when(mock).beginDBTransaction();
                Setting st = new Setting("language", "site", testLangs);
                when(mock.getOrCreateSetting(any(String.class), any(String.class))).thenReturn(st);
            })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doPost(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
   System.out.println(">>> " + jout.toString(4));
                verify(mockResponse).setStatus(400);
                assertEquals(jout.getString("debug"), "invalid group [bad-group] or id [bad-id]");
            }
        }
    }

    // this will dump an exception about IA.json missing, but still pass; yeah, messy.
    @Test void apiGetUser() throws ServletException, IOException {
        User user = mock(User.class);
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            })) {
            try (MockedStatic<CommonConfiguration> mockService = mockStatic(CommonConfiguration.class)) {
                mockService.when(() -> CommonConfiguration.getProperty(any(String.class), any(String.class))).thenReturn("test-value");
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(HttpServletRequest.class))).thenReturn(true);
                    apiServlet.doGet(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    // kinda meek test of results, but a decent start?
                    assertTrue(jout.has("users"));  // only shown to logged in user
                    assertEquals(jout.keySet().size(), 39);
                }
            }
        }
    }

    @Test void apiGetAnon() throws ServletException, IOException {
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenReturn(null);
            })) {
            try (MockedStatic<CommonConfiguration> mockService = mockStatic(CommonConfiguration.class)) {
                mockService.when(() -> CommonConfiguration.getProperty(any(String.class), any(String.class))).thenReturn("test-value");
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(HttpServletRequest.class))).thenReturn(false);
                    apiServlet.doGet(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    // kinda meek test of results, but a decent start?
                    assertFalse(jout.has("users"));  // only shown to logged in user
                    assertEquals(jout.keySet().size(), 37);
                }
            }
        }
    }

    @Test void apiDelete401() throws ServletException, IOException {
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                doNothing().when(mock).beginDBTransaction();
            })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doDelete(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(401);
                assertFalse(jout.getBoolean("success"));
            }
        }
    }

    @Test void apiDeleteNonAdmin401() throws ServletException, IOException {
        User user = mock(User.class);
        when(user.isAdmin(any(Shepherd.class))).thenReturn(false);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doDelete(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(401);
                assertFalse(jout.getBoolean("success"));
            }
        }
    }

    @Test void apiDeleteBadPath() throws ServletException, IOException {
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
                    apiServlet.doDelete(mockRequest, mockResponse);
                });
                assertTrue(ex.getMessage().contains("Bad path"));
            }
        }
    }

    @Test void apiDeleteInvalidIdGroup() throws ServletException, IOException {
        User user = mock(User.class);
        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);

        // this prefix is pretty much guaranteed from web.xml, so we need it here before /bad-uri
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/site-settings/bad-group/bad-id");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doDelete(mockRequest, mockResponse);
                responseOut.flush();
                JSONObject jout = new JSONObject(responseOut.toString());
                verify(mockResponse).setStatus(400);
                assertEquals(jout.getString("debug"), "invalid group [bad-group] or id [bad-id]");
            }
        }
    }

    @Test void apiDelete404() throws ServletException, IOException {
        User user = mock(User.class);
        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/site-settings/language/site");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
                when(mock.getSetting(any(String.class), any(String.class))).thenReturn(null);
            })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doDelete(mockRequest, mockResponse);
                verify(mockResponse).setStatus(404);
            }
        }
    }

    @Test void apiDeleteSuccess() throws ServletException, IOException {
        User user = mock(User.class);
        when(user.isAdmin(any(Shepherd.class))).thenReturn(true);
        Setting fakeSetting = new Setting("language", "available");
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/site-settings/language/available");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
            (mock, context) -> {
                when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
                when(mock.getSetting(any(String.class), any(String.class))).thenReturn(fakeSetting);
                doNothing().when(mock).beginDBTransaction();
                doNothing().when(mock).deleteSetting(any(Setting.class));
            })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                apiServlet.doDelete(mockRequest, mockResponse);
                responseOut.flush();
                verify(mockResponse).setStatus(204);
            }
        }
    }

 */
}
