package org.ecocean.api;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.api.BaseObject;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
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

class ApiPatchTest {
    PersistenceManagerFactory mockPMF;
    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    BaseObject apiServlet;
    StringWriter responseOut;
    List<File> emptyFiles = new ArrayList<File>();

    @BeforeEach void setUp()
    throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockPMF = mock(PersistenceManagerFactory.class);
        apiServlet = new BaseObject();

        responseOut = new StringWriter();
        PrintWriter writer = new PrintWriter(responseOut);
        when(mockResponse.getWriter()).thenReturn(writer);
    }

    private JSONArray patchPayload(String op, String path, Object value) {
        return patchPayload(op, path, value, null);
    }

    private JSONArray patchPayload(String op, String path, Object value, JSONArray prev) {
        if (prev == null) prev = new JSONArray();
        JSONObject opObj = new JSONObject();
        opObj.put("op", op);
        opObj.put("path", path);
        opObj.put("value", value);
        prev.put(opObj);
        return prev;
    }

    @Test void api401()
    throws ServletException, IOException {
        Encounter enc = mock(Encounter.class);
        // to test this we need a valid payload *and* a (faked) valid encounter... sigh
        String payload = patchPayload("fake", null, null).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    apiServlet.doPatch(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(401);
                    assertFalse(jout.getBoolean("success"));
                }
            }
        }
    }

    @Test void apiInvalidPayload()
    throws ServletException, IOException {
        String payload = "{}";

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    apiServlet.doPatch(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(400);
                    assertFalse(jout.getBoolean("success"));
                    assertEquals(jout.getString("error"), "empty payload array");
                }
            }
        }
    }

    @Test void apiInvalidOp()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        // apparently admin is not good enough to edit encounters!!
        // when(user.isAdmin(any(Shepherd.class))).thenReturn(false);
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        String payload = patchPayload("failOp", "fakePath", "someValue").toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    apiServlet.doPatch(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(400);
                    assertFalse(jout.getBoolean("success"));
                    assertTrue(gotErrorsValue(jout, "code", "INVALID_OP"));
                }
            }
        }
    }

    @Test void apiInvalidPath()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        String payload = patchPayload("add", "fakePath", "someValue").toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    apiServlet.doPatch(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(400);
                    assertFalse(jout.getBoolean("success"));
                    assertTrue(gotErrorsValue(jout, "code", "INVALID"));
                    assertTrue(gotErrorsValue(jout, "details", "unknown fieldName: fakePath"));
                }
            }
        }
    }

    // this test a path (dateTime) which should trigger our test via Encounter.validateFieldValue()
    @Test void apiInvalidValue1()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        String payload = patchPayload("add", "dateTime", "not-a-valid-value").toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    apiServlet.doPatch(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(400);
                    assertFalse(jout.getBoolean("success"));
                    assertTrue(gotErrorsValue(jout, "fieldName", "dateTime"));
                    assertTrue(gotErrorsValue(jout, "code", "INVALID"));
                }
            }
        }
    }

    // this test a path (elevation) which should trigger our test via BulkValidator
    @Test void apiInvalidValue2()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        String payload = patchPayload("add", "elevation", "not-a-valid-value").toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    apiServlet.doPatch(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(400);
                    assertFalse(jout.getBoolean("success"));
                    // assertTrue(gotErrorsValue(jout, "fieldName", "dateTime"));
                    assertTrue(gotErrorsValue(jout, "code", "INVALID"));
                }
            }
        }
    }

    // just some successful ones (single and multi-patch)
    @Test void apiValidPatches()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        String payload = patchPayload("add", "elevation", 10.0).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    apiServlet.doPatch(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(200);
                    assertTrue(jout.getBoolean("success"));
                    assertTrue(enc.getMaximumElevationInMeters() == 10.0D);
                }
            }
        }
    }

/*
    @Test void apiEmptyPayload()
    throws ServletException, IOException {
        User user = null;
        // to test this we need a valid payload *and* a (faked) valid encounter... sigh
        String payload = patchPayload("fake", null, null).toString();
        when(mockRequest.getRequestURI()).thenReturn("/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            //when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    apiServlet.doPatch(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(401);
                    assertFalse(jout.getBoolean("success"));
                }
            }
        }
    }
 */

/*
          "errors": [{
                    "code": "INVALID",
                    "details": "invalid op: failOp",
                    "type": "INVALID_OP"
          }],
 */
    private static boolean gotErrorsValue(JSONObject rtn, String key, Object value) {
        if (rtn == null) return false;
        JSONArray errArr = rtn.optJSONArray("errors");
        if (errArr == null) return false;
        for (int i = 0; i < errArr.length(); i++) {
            JSONObject err = errArr.optJSONObject(i);
            if (err == null) continue;
            Object errVal = err.opt(key);
            if (errVal == null) continue;
            if (errVal.equals(value)) return true;
        }
        return false;
    }

/*
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
            row.put("Encounter.submitterID", "non-array-fake-username");
            rows.put(row);
        }
        rtn.put("rows", rows);
        rtn.put("verbose", true);
        return rtn.toString();
    }

    private String getValidPayloadArrays() {
        JSONObject rtn = basePayload();
        JSONArray fieldNames = new JSONArray();

        fieldNames.put("Encounter.year");
        fieldNames.put("Encounter.genus");
        fieldNames.put("Encounter.specificEpithet");
        fieldNames.put("Encounter.submitterID");
        rtn.put("fieldNames", fieldNames);

        JSONArray rows = new JSONArray();
        for (int i = 0; i < 20; i++) {
            JSONArray row = new JSONArray();
            row.put(2000 + i);
            row.put("Genus" + i);
            row.put("specificEpithet" + i);
            row.put("fake-username");
            rows.put(row);
        }
        rtn.put("rows", rows);
        rtn.put("verbose", true);
        return rtn.toString();
    }

    private String getInvalidPayloadArraysSynonyms() {
        JSONObject rtn = basePayload();
        JSONArray fieldNames = new JSONArray();

        fieldNames.put("Encounter.year");
        fieldNames.put("Sighting.year");
        fieldNames.put("Encounter.genus");
        fieldNames.put("Encounter.specificEpithet");
        fieldNames.put("Encounter.submitterID");
        rtn.put("fieldNames", fieldNames);

        JSONArray rows = new JSONArray();
        for (int i = 0; i < 3; i++) {
            JSONArray row = new JSONArray();
            row.put(2000 + i);
            row.put(2000 + i);
            row.put("Genus" + i);
            row.put("specificEpithet" + i);
            row.put("fake-username");
            rows.put(row);
        }
        rtn.put("rows", rows);
        return rtn.toString();
    }

    private String getInvalidPayloadNonArraysSynonyms() {
        JSONObject rtn = basePayload();
        JSONArray rows = new JSONArray();

        for (int i = 0; i < 3; i++) {
            JSONObject row = new JSONObject();
            row.put("Encounter.year", 2000 + i);
            row.put("Sighting.year", 2000 + i);
            row.put("Encounter.genus", "Genus" + i);
            row.put("Encounter.specificEpithet", "specificEpithet" + i);
            row.put("Encounter.submitterID", "non-array-fake-username");
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

    @Test void apiPostDuplicateSynonyms()
    throws ServletException, IOException {
        User user = mock(User.class);
        String requestBody = getInvalidPayloadArraysSynonyms();

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
                    assertEquals(jout.getInt("statusCode"), 400);
                    assertEquals(jout.getJSONArray("errors").length(), 2);
                    assertTrue(jout.getJSONArray("errors").getJSONObject(0).getString(
                        "details").startsWith("synonym columns: "));
                }
            }
        }
    }
 */
}
