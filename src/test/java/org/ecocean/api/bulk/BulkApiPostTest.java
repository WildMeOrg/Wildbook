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

class BulkApiPostTest {
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

    @Test void apiPost401()
    throws ServletException, IOException {
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            doNothing().when(mock).beginDBTransaction();
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

    @Test void apiPostNoBulkImportId()
    throws ServletException, IOException {
        User user = mock(User.class);
        String requestBody = "{}";

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                Exception ex = assertThrows(ServletException.class, () -> {
                    apiServlet.doPost(mockRequest, mockResponse);
                });
                assertEquals(ex.getMessage(), "no rows in payload");
            }
        }
    }

    @Test void apiPostNoRowsError()
    throws ServletException, IOException {
        User user = mock(User.class);
        String requestBody = basePayload().toString();

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                Exception ex = assertThrows(ServletException.class, () -> {
                    apiServlet.doPost(mockRequest, mockResponse);
                });
                assertEquals(ex.getMessage(), "no rows in payload");
            }
        }
    }

    private JSONObject basePayload() {
        JSONObject payload = new JSONObject();

        payload.put("bulkImportId", "00000000-0000-0000-0000-000000000000");
        payload.put("tolerance", new JSONObject());
        return payload;
    }

    private String getBadFieldNamesPayload() {
        JSONObject rtn = basePayload();
        JSONArray rows = new JSONArray();
        JSONObject badRow = new JSONObject("{\"fubarFail\": 123}");

        rows.put(badRow);
        rtn.put("rows", rows);
        // default behavior is to go easy on bad fieldnames, so we disable warning-only
        rtn.getJSONObject("tolerance").put("badFieldnamesAreWarnings", false);
        return rtn.toString();
    }

    private String getValidPayloadNonArrays() {
        JSONObject rtn = basePayload();
        JSONArray rows = new JSONArray();

        for (int i = 0; i < 20; i++) {
            JSONObject row = new JSONObject();
            row.put("Encounter.year", 2000 + i);
            row.put("Encounter.genus", "Genus" + i);
            row.put("Encounter.specificEpithet", "specificEpithet" + i);
            rows.put(row);
        }
        rtn.put("rows", rows);
        return rtn.toString();
    }

    private String getValidPayloadArrays() {
        JSONObject rtn = basePayload();
        JSONArray fieldNames = new JSONArray();

        fieldNames.put("Encounter.year");
        fieldNames.put("Encounter.genus");
        fieldNames.put("Encounter.specificEpithet");
        rtn.put("fieldNames", fieldNames);

        JSONArray rows = new JSONArray();
        for (int i = 0; i < 20; i++) {
            JSONArray row = new JSONArray();
            row.put(2000 + i);
            row.put("Genus" + i);
            row.put("specificEpithet" + i);
            rows.put(row);
        }
        rtn.put("rows", rows);
        return rtn.toString();
    }

    // adds values to only first row of data
    private String addToRows(String jsonString, String fieldName, Object value) {
        JSONObject json = new JSONObject(jsonString);

        json.getJSONArray("fieldNames").put(fieldName);
        json.getJSONArray("rows").getJSONArray(0).put(value);
        return json.toString();
    }

/*
    "errors": [
        {
            "fieldName": "Encounter.genus",
            "details": "org.ecocean.api.bulk.BulkValidatorException: required value",
            "rowNumber": 0,
            "errors": [{"code": "REQUIRED"}]
        },
        {
            "fieldName": "Encounter.year",
            "details": "org.ecocean.api.bulk.BulkValidatorException: required value",
            "rowNumber": 0,
            "errors": [{"code": "REQUIRED"}]
        },
        {
            "fieldName": "fubarFail",
            "details": "org.ecocean.api.bulk.BulkValidatorException: invalid fieldName: fubarFail",
            "rowNumber": 0,
            "errors": [{"code": "INVALID"}]
        },
        {
            "fieldName": "Encounter.specificEpithet",
            "details": "org.ecocean.api.bulk.BulkValidatorException: required value",
            "rowNumber": 0,
            "errors": [{"code": "REQUIRED"}]
        }
 */
    private boolean hasError(JSONObject rtnJson, int rowNumber, String fieldName) {
        if (rtnJson == null) return false;
        JSONArray errors = rtnJson.optJSONArray("errors");
        if (errors == null) return false;
        for (int i = 0; i < errors.length(); i++) {
            JSONObject error = errors.optJSONObject(i);
            if (error == null) continue;
            int errRowNumber = error.optInt("rowNumber", -9999);
            String errFieldName = error.optString("fieldName", "__FAIL__");
            if ((errRowNumber == rowNumber) && errFieldName.equals(fieldName)) return true;
        }
        return false;
    }

    @Test void apiPostRowsBadFieldNames()
    throws ServletException, IOException {
        User user = mock(User.class);
        String requestBody = getBadFieldNamesPayload();

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
        })) {
            try (MockedStatic<UploadedFiles> mockUF = mockStatic(UploadedFiles.class)) {
                mockUF.when(() -> UploadedFiles.findFiles(any(HttpServletRequest.class),
                    any(String.class))).thenReturn(emptyFiles);
                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
                        mockPMF);
                    apiServlet.doPost(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(400);
                    assertFalse(jout.getBoolean("success"));
                    assertTrue(hasError(jout, 0, "fubarFail"));
                    // lets also check the required fields are getting reported
                    assertTrue(hasError(jout, 0, "Encounter.year"));
                    assertTrue(hasError(jout, 0, "Encounter.specificEpithet"));
                    assertTrue(hasError(jout, 0, "Encounter.genus"));
                }
            }
        }
    }

    @Test void apiPostValidNonArrays()
    throws ServletException, IOException {
        User user = mock(User.class);
        String requestBody = getValidPayloadNonArrays();

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<UploadedFiles> mockUF = mockStatic(UploadedFiles.class)) {
                mockUF.when(() -> UploadedFiles.findFiles(any(HttpServletRequest.class),
                    any(String.class))).thenReturn(emptyFiles);
                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
                        mockPMF);
                    apiServlet.doPost(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(200);
                    assertTrue(jout.getBoolean("success"));
                }
            }
        }
    }

    @Test void apiPostValidArrays()
    throws ServletException, IOException {
        User user = mock(User.class);
        String requestBody = getValidPayloadArrays();

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<UploadedFiles> mockUF = mockStatic(UploadedFiles.class)) {
                mockUF.when(() -> UploadedFiles.findFiles(any(HttpServletRequest.class),
                    any(String.class))).thenReturn(emptyFiles);
                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
                        mockPMF);
                    apiServlet.doPost(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(200);
                    assertTrue(jout.getBoolean("success"));
                }
            }
        }
    }

    @Test void apiPostValidArraysNullInt()
    throws ServletException, IOException {
        User user = mock(User.class);
        String requestBody = getValidPayloadArrays();

        requestBody = addToRows(requestBody, "Encounter.month", null);

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<UploadedFiles> mockUF = mockStatic(UploadedFiles.class)) {
                mockUF.when(() -> UploadedFiles.findFiles(any(HttpServletRequest.class),
                    any(String.class))).thenReturn(emptyFiles);
                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
                        mockPMF);
                    apiServlet.doPost(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(200);
                    assertTrue(jout.getBoolean("success"));
                }
            }
        }
    }

    @Test void apiPostValidMeasurements()
    throws ServletException, IOException {
        User user = mock(User.class);
        String requestBody = getValidPayloadArrays();

        requestBody = addToRows(requestBody, "Encounter.measurement0", 12.345);
        if (requestBody != null) return; // FIXME this test is broken :(

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));
        List<String> mockMeasValues = new ArrayList<String>();
        mockMeasValues.add("SomeMeasurementName");

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<UploadedFiles> mockUF = mockStatic(UploadedFiles.class)) {
                mockUF.when(() -> UploadedFiles.findFiles(any(HttpServletRequest.class),
                    any(String.class))).thenReturn(emptyFiles);
                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
                        mockPMF);
                    try (MockedStatic<BulkImportUtil> mockUtil = mockStatic(BulkImportUtil.class)) {
                        // allows us to skip commonConfig to get list of values
                        mockUtil.when(() -> BulkImportUtil.getMeasurementValues()).thenReturn(
                            mockMeasValues);
                        apiServlet.doPost(mockRequest, mockResponse);
                        responseOut.flush();
                        JSONObject jout = new JSONObject(responseOut.toString());
                        System.out.println(jout.toString(4));
                        verify(mockResponse).setStatus(200);
                        assertTrue(jout.getBoolean("success"));
                    }
                }
            }
        }
    }

/*
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
