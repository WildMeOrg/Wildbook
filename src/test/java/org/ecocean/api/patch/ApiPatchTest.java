package org.ecocean.api;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.api.BaseObject;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.servlet.ReCAPTCHA;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;
import org.ecocean.tag.MetalTag;
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
    // lets "test" work for any validation against common config
    List<String> fakeConfValues = new ArrayList<String>();

    @BeforeEach void setUp()
    throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockPMF = mock(PersistenceManagerFactory.class);
        apiServlet = new BaseObject();

        responseOut = new StringWriter();
        PrintWriter writer = new PrintWriter(responseOut);
        when(mockResponse.getWriter()).thenReturn(writer);
        fakeConfValues.add("test");
    }

    private JSONArray patchPayload(String op, String path, Object value) {
        return patchPayload(op, path, value, (JSONArray)null);
    }

    private JSONArray patchPayload(String op, String path, Object value, String prev) {
        return patchPayload(op, path, value, new JSONArray(prev));
    }

    private JSONArray patchPayload(String op, String path, Object value, JSONArray prev) {
        if (prev == null) prev = new JSONArray();
        JSONObject opObj = new JSONObject();
        opObj.put("op", op);
        opObj.put("path", path);
        if (value != null) opObj.put("value", value);
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

    @Test void apiRequiredValue1()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        String payload = patchPayload("remove", "genus", null).toString();

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
                    assertTrue(gotErrorsValue(jout, "code", "REQUIRED"));
                    assertTrue(gotErrorsValue(jout, "details",
                        "genus is a required value, cannot remove"));
                }
            }
        }
    }

    @Test void apiRequiredValue2()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        enc.setGenus("Genus");
        enc.setSpecificEpithet("specificEpithet");
        String payload = patchPayload("add", "genus", null).toString();

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
                    assertTrue(gotErrorsValue(jout, "details",
                        "genus and specificEpithet are an invalid taxonomy: specificEpithet"));
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
        payload = patchPayload("add", "behavior", "test", payload).toString();
        payload = patchPayload("add", "alternateId", "test", payload).toString();
        payload = patchPayload("add", "country", "Cuba", payload).toString();
        payload = patchPayload("add", "sex", "male", payload).toString();
        payload = patchPayload("add", "state", "approved", payload).toString();
        payload = patchPayload("add", "lifeStage", "test", payload).toString();
        payload = patchPayload("add", "livingStatus", "test", payload).toString();
        payload = patchPayload("add", "submitterName", "test", payload).toString();
        payload = patchPayload("add", "submitterOrganization", "test", payload).toString();
        payload = patchPayload("add", "decimalLatitude", 1.23, payload).toString();
        payload = patchPayload("add", "decimalLongitude", 1.23, payload).toString();
        // from here on these are not yet tested on the enc after
        payload = patchPayload("add", "groupRole", "test", payload).toString();
        payload = patchPayload("add", "distinguishingScar", "test", payload).toString();
        payload = patchPayload("add", "occurrenceRemarks", "test", payload).toString();
        payload = patchPayload("add", "researcherComments", "test", payload).toString();
        payload = patchPayload("add", "verbatimLocality", "test", payload).toString();
        payload = patchPayload("add", "verbatimEventDate", "test", payload).toString();
        payload = patchPayload("add", "sightingRemarks", "test", payload).toString();
        payload = patchPayload("add", "otherCatalogNumbers", "test", payload).toString();
        payload = patchPayload("add", "patterningCode", "test", payload).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getContext()).thenReturn("context0");
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    try (MockedStatic<CommonConfiguration> mockConfig = mockStatic(
                        CommonConfiguration.class)) {
                        mockConfig.when(() -> CommonConfiguration.getIndexedPropertyValues(any(
                            String.class), any(String.class))).thenReturn(fakeConfValues);
                        apiServlet.doPatch(mockRequest, mockResponse);
                        responseOut.flush();
                        JSONObject jout = new JSONObject(responseOut.toString());
                        verify(mockResponse).setStatus(200);
                        assertTrue(jout.getBoolean("success"));
                        assertTrue(enc.getMaximumElevationInMeters() == 10.0D);
                        assertEquals(enc.getBehavior(), "test");
                        assertEquals(enc.getAlternateID(), "test");
                        assertEquals(enc.getSex(), "male");
                        assertEquals(enc.getState(), "approved");
                        assertEquals(enc.getLifeStage(), "test");
                        assertEquals(enc.getLivingStatus(), "test");
                        assertEquals(enc.getSubmitterName(), "test");
                        assertEquals(enc.getCountry(), "Cuba");
                        assertTrue(enc.getDecimalLatitudeAsDouble() == 1.23);
                        assertTrue(enc.getDecimalLongitudeAsDouble() == 1.23);
                    }
                }
            }
        }
    }

    @Test void apiDateValid()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        String payload = patchPayload("replace", "year", "2000").toString();
        payload = patchPayload("replace", "month", "01", payload).toString();
        payload = patchPayload("replace", "day", "02", payload).toString();
        payload = patchPayload("replace", "hour", "11", payload).toString();
        payload = patchPayload("replace", "minutes", "00", payload).toString();

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
                    assertEquals("2000-01-02 11:00", enc.getDate());
                }
            }
        }
    }

    // goes from full date set to only year
    @Test void apiDateTruncateValid()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        enc.setYear(2025);
        enc.setMonth(11);
        enc.setDay(1);
        enc.setHour(13);
        String payload = patchPayload("replace", "year", "2000").toString();
        payload = patchPayload("remove", "month", null, payload).toString();
        payload = patchPayload("replace", "day", null, payload).toString();
        payload = patchPayload("remove", "hour", null, payload).toString();
        payload = patchPayload("remove", "minutes", null, payload).toString();

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
                    // getDate() returns stupid string for year only but what can you do?
                    assertEquals("2000", enc.getDate());
                }
            }
        }
    }

    @Test void apiListyUserAddFailNoUser()
    throws ServletException, IOException {
        User owner = mock(User.class);
        String idA = "00000000-0000-0000-0000-00000000000A";

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("add", "photographers", idA).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
            when(mock.getUserByUUID(idA)).thenReturn(null);
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
                    assertTrue(gotErrorsValue(jout, "details", "photographers no user id=" + idA));
                }
            }
        }
    }

    @Test void apiListyUserAddEmailFail()
    throws ServletException, IOException {
        User owner = mock(User.class);
        String badEmail = "bademail";

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("add", "photographers", badEmail).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
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
                    assertTrue(gotErrorsValue(jout, "details",
                        "photographers has unusable value=" + badEmail));
                }
            }
        }
    }

    @Test void apiListyUserAddByIdSuccess()
    throws ServletException, IOException {
        User owner = mock(User.class);
        User userA = mock(User.class);
        String idA = "00000000-0000-0000-0000-00000000000A";

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("add", "photographers", idA).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
            when(mock.getUserByUUID(idA)).thenReturn(userA);
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
                    assertEquals(enc.getPhotographers().get(0), userA);
                }
            }
        }
    }

    @Test void apiListyUserAddByEmailSuccess()
    throws ServletException, IOException {
        User owner = mock(User.class);
        User userA = mock(User.class);
        String emailA = "userA@example.com";

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("add", "photographers", emailA).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
            when(mock.getOrCreateUserByEmailAddress(emailA, null)).thenReturn(userA);
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
                    assertEquals(enc.getPhotographers().get(0), userA);
                }
            }
        }
    }

    @Test void apiListyUserRemoveFail()
    throws ServletException, IOException {
        User owner = mock(User.class);
        User userA = mock(User.class);
        String idA = "00000000-0000-0000-0000-00000000000A";

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        enc.addInformOther(userA);
        String payload = patchPayload("remove", "informOthers", idA).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
            when(mock.getUserByUUID(idA)).thenReturn(null);
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
                    assertTrue(gotErrorsValue(jout, "details",
                        "informOthers value is invalid user id"));
                    assertEquals(enc.getInformOthers().get(0), userA);
                }
            }
        }
    }

    @Test void apiListyUserRemoveSuccess()
    throws ServletException, IOException {
        User owner = mock(User.class);
        User userA = mock(User.class);
        String idA = "00000000-0000-0000-0000-00000000000A";

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        enc.addInformOther(userA);
        String payload = patchPayload("remove", "informOthers", idA).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
            when(mock.getUserByUUID(idA)).thenReturn(userA);
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
                    assertEquals(enc.getInformOthers().size(), 0);
                }
            }
        }
    }

    // this is basically impossible to fail as it only finds existing indiv
    // or will just create a new one
    @Test void apiIndividualAddExistingSuccess()
    throws ServletException, IOException {
        User owner = mock(User.class);
        String indivId = "existing-indiv-id";
        MarkedIndividual indiv = mock(MarkedIndividual.class);

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("add", "individualId", indivId).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getMarkedIndividual(indivId)).thenReturn(indiv);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
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
                    assertEquals(indiv, enc.getIndividual());
                }
            }
        }
    }

    // no real failure case here either, as it just silently ignores no indiv
    @Test void apiIndividualRemoveExistingSuccess()
    throws ServletException, IOException {
        User owner = mock(User.class);
        MarkedIndividual indiv = mock(MarkedIndividual.class);

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        enc.setIndividual(indiv);
        assertTrue(enc.hasMarkedIndividual());
        String payload = patchPayload("remove", "individualId", null).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
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
                    assertFalse(enc.hasMarkedIndividual());
                }
            }
        }
    }

    // an explicit occurrence id passed but user cannot add to it
    @Test void apiOccurrenceAddFailure()
    throws ServletException, IOException {
        User owner = mock(User.class);
        String occId = "existing-occur-id";
        Occurrence occ = mock(Occurrence.class);

        when(occ.canUserAccess(any(User.class), any(Shepherd.class))).thenReturn(false);
        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("add", "occurrenceId", occId).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getOccurrence(occId)).thenReturn(occ);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
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
                    assertTrue(gotErrorsValue(jout, "code", "FORBIDDEN"));
                }
            }
        }
    }

    @Test void apiOccurrenceAddExistingSuccess()
    throws ServletException, IOException {
        User owner = mock(User.class);
        String occId = "existing-occur-id";
        Occurrence occ = mock(Occurrence.class);

        when(occ.canUserAccess(any(User.class), any(Shepherd.class))).thenReturn(true);
        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        // when(enc.getOccurrence(any(Shepherd.class))).thenReturn(occ);
        String payload = patchPayload("add", "occurrenceId", occId).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getOccurrence(occId)).thenReturn(occ);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
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
                }
            }
        }
    }

    // explicit occId returns null
    @Test void apiOccurrenceRemoveExplicitFailure1()
    throws ServletException, IOException {
        User owner = mock(User.class);
        String occId = "existing-occur-id";

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("remove", "occurrenceId", occId).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getOccurrence(occId)).thenReturn(null);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
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
                    assertTrue(gotErrorsValue(jout, "code", "REQUIRED"));
                    assertTrue(gotErrorsValue(jout, "details", "id " + occId + " does not exist"));
                }
            }
        }
    }

    // explicit occId returns forbidden
    @Test void apiOccurrenceRemoveExplicitFailure2()
    throws ServletException, IOException {
        User owner = mock(User.class);
        String occId = "existing-occur-id";
        Occurrence occ = mock(Occurrence.class);

        when(occ.canUserAccess(any(User.class), any(Shepherd.class))).thenReturn(false);
        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("remove", "occurrenceId", occId).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getOccurrence(occId)).thenReturn(occ);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
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
                    assertTrue(gotErrorsValue(jout, "code", "FORBIDDEN"));
                }
            }
        }
    }

    // explicit occId but enc not in that occ
    @Test void apiOccurrenceRemoveExplicitFailure3()
    throws ServletException, IOException {
        User owner = mock(User.class);
        String occId = "existing-occur-id";
        Occurrence occ = mock(Occurrence.class);

        when(occ.canUserAccess(any(User.class), any(Shepherd.class))).thenReturn(true);
        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("remove", "occurrenceId", occId).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getOccurrence(occId)).thenReturn(occ);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
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
                    assertTrue(gotErrorsValue(jout, "code", "REQUIRED"));
                    assertTrue(gotErrorsValue(jout, "details",
                        "encounter is not in occurrence " + occId));
                }
            }
        }
    }

    // remove (no explicit id) but enc not in occurrence
    @Test void apiOccurrenceRemoveFailure()
    throws ServletException, IOException {
        User owner = mock(User.class);

        // Occurrence occ = mock(Occurrence.class);

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("remove", "occurrenceId", null).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getOccurrence(any(Encounter.class))).thenReturn(null);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
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
                    assertTrue(gotErrorsValue(jout, "code", "REQUIRED"));
                    assertTrue(gotErrorsValue(jout, "details",
                        "encounter has no occurrence to remove from"));
                }
            }
        }
    }

    @Test void apiOccurrenceRemoveSuccess()
    throws ServletException, IOException {
        User owner = mock(User.class);
        Occurrence occ = mock(Occurrence.class);

        when(owner.getUsername()).thenReturn("ownerUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("ownerUser");
        String payload = patchPayload("remove", "occurrenceId", null).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getOccurrence(any(Encounter.class))).thenReturn(occ);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(owner);
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
                }
            }
        }
    }

    @Test void apiMetalTagsFailLocation()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        JSONObject tagData = new JSONObject();
        tagData.put("location", "test_fail");
        tagData.put("number", "aaa");
        String payload = patchPayload("add", "metalTags", tagData).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getContext()).thenReturn("context0");
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    try (MockedStatic<CommonConfiguration> mockConfig = mockStatic(
                        CommonConfiguration.class)) {
                        // common config enable showMetalTags
                        mockConfig.when(() -> CommonConfiguration.showMetalTags(any(
                            String.class))).thenReturn(true);
                        // valid locations mocked:
                        mockConfig.when(() -> CommonConfiguration.getIndexedPropertyValues(eq(
                            "metalTagLocation"), any(String.class))).thenReturn(fakeConfValues);
                        apiServlet.doPatch(mockRequest, mockResponse);
                        responseOut.flush();
                        JSONObject jout = new JSONObject(responseOut.toString());
                        verify(mockResponse).setStatus(400);
                        assertFalse(jout.getBoolean("success"));
                        assertTrue(gotErrorsValue(jout, "code", "INVALID"));
                        assertTrue(gotErrorsValue(jout, "details",
                            "metalTags has invalid location=test_fail"));
                    }
                }
            }
        }
    }

    @Test void apiMetalTagsSuccess()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        JSONObject tagData = new JSONObject();
        tagData.put("location", "test");
        tagData.put("number", "aaa");
        String payload = patchPayload("add", "metalTags", tagData).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getContext()).thenReturn("context0");
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    try (MockedStatic<CommonConfiguration> mockConfig = mockStatic(
                        CommonConfiguration.class)) {
                        // common config enable showMetalTags
                        mockConfig.when(() -> CommonConfiguration.showMetalTags(any(
                            String.class))).thenReturn(true);
                        // valid locations mocked:
                        mockConfig.when(() -> CommonConfiguration.getIndexedPropertyValues(eq(
                            "metalTagLocation"), any(String.class))).thenReturn(fakeConfValues);
                        apiServlet.doPatch(mockRequest, mockResponse);
                        responseOut.flush();
                        JSONObject jout = new JSONObject(responseOut.toString());
                        verify(mockResponse).setStatus(200);
                        assertTrue(jout.getBoolean("success"));
                        assertEquals(enc.getMetalTags().size(), 1);
                    }
                }
            }
        }
    }

    @Test void apiMetalTagsModifySuccess()
    throws ServletException, IOException {
        User user = mock(User.class);

        when(user.getUsername()).thenReturn("someUser");
        Encounter enc = new Encounter();
        enc.setSubmitterID("someUser");
        MetalTag mtag = new MetalTag("bbb", "test");
        enc.addMetalTag(mtag);
        assertEquals(enc.getMetalTags().size(), 1);
        assertEquals(enc.getMetalTags().get(0).getTagNumber(), "bbb");
        JSONObject tagData = new JSONObject();
        tagData.put("location", "test");
        tagData.put("number", "aaa");
        String payload = patchPayload("add", "metalTags", tagData).toString();

        when(mockRequest.getRequestURI()).thenReturn(
            "/api/v3/encounters/00000000-0000-0000-0000-000000000000");
        when(mockRequest.getMethod()).thenReturn("PATCH");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(payload)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getEncounter(any(String.class))).thenReturn(enc);
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getContext()).thenReturn("context0");
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<ReCAPTCHA> mockCaptcha = mockStatic(ReCAPTCHA.class)) {
                    mockCaptcha.when(() -> ReCAPTCHA.sessionIsHuman(any(
                        HttpServletRequest.class))).thenReturn(true);
                    try (MockedStatic<CommonConfiguration> mockConfig = mockStatic(
                        CommonConfiguration.class)) {
                        // common config enable showMetalTags
                        mockConfig.when(() -> CommonConfiguration.showMetalTags(any(
                            String.class))).thenReturn(true);
                        // valid locations mocked:
                        mockConfig.when(() -> CommonConfiguration.getIndexedPropertyValues(eq(
                            "metalTagLocation"), any(String.class))).thenReturn(fakeConfValues);
                        apiServlet.doPatch(mockRequest, mockResponse);
                        responseOut.flush();
                        JSONObject jout = new JSONObject(responseOut.toString());
                        verify(mockResponse).setStatus(200);
                        assertTrue(jout.getBoolean("success"));
                        // should still have only 1 tag, but number should be "aaa" not "bbb" now
                        assertEquals(enc.getMetalTags().size(), 1);
                        assertEquals(enc.getMetalTags().get(0).getTagNumber(), "aaa");
                    }
                }
            }
        }
    }

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
