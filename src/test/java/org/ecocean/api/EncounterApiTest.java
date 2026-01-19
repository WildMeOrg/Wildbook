package org.ecocean.api;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import org.ecocean.api.SiteSettings;
import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.security.Collaboration;
import org.ecocean.servlet.ReCAPTCHA;
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
import java.util.HashMap;
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
import static org.mockito.Mockito.*;

class EncounterApiTest {
    PersistenceManagerFactory mockPMF;
    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    SiteSettings apiServlet;
    StringWriter responseOut;
    List<String> fakeConfValues = new ArrayList<String>();

    @BeforeEach void setUp()
    throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockPMF = mock(PersistenceManagerFactory.class);
        apiServlet = new SiteSettings();

        responseOut = new StringWriter();
        PrintWriter writer = new PrintWriter(responseOut);
        when(mockResponse.getWriter()).thenReturn(writer);
        fakeConfValues.add("test");

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

    @Test void encounterUtilityTests()
    throws ServletException, IOException {
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            doNothing().when(mock).beginDBTransaction();
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                Encounter enc = new Encounter();
                String encId = "7f20cf47-a7b9-44a2-b4a3-8637dcdf603b";
                enc.setId(encId);
                // spy to fake some calls on enc so it doesnt blow up
                Encounter encSpy = spy(enc);
                doReturn(true).when(encSpy).isPubliclyReadable();
                Map emptyMap = new HashMap();
                doReturn(emptyMap).when(encSpy).getBiologicalMeasurementsByType();
                Shepherd myShepherd = new Shepherd("context0");
                JSONObject json = encSpy.jsonForApiGet(myShepherd, null);
                assertEquals(json.length(), 34);
                assertEquals(json.getString("id"), encId);
            }
        }
    }

    @Test void encounterDateValuesTest() {
        Encounter enc = new Encounter();

        // hour and minutes are set by default, sigh
        enc.setHour(-1);
        enc.setMinutes("0");
        JSONObject dv = enc.getDateValuesJson();
        assertEquals(dv.length(), 0); // empty
        enc.setYear(1901);
        dv = enc.getDateValuesJson();
        assertEquals(dv.length(), 1);
        // set day but we have no month
        enc.setDay(11);
        dv = enc.getDateValuesJson();
        assertEquals(dv.length(), 1);
        // set a month, but invalid
        enc.setMonth(22);
        dv = enc.getDateValuesJson();
        assertEquals(dv.length(), 1);
        // now set proper month, so we should get y/m/d values
        enc.setMonth(2);
        dv = enc.getDateValuesJson();
        assertEquals(dv.length(), 3);
        // set invalid hour
        enc.setHour(99);
        dv = enc.getDateValuesJson();
        assertEquals(dv.length(), 3);
        // set valid hour
        enc.setHour(23);
        dv = enc.getDateValuesJson();
        assertEquals(dv.length(), 5);
        // set invalid minutes
        enc.setMinutes("99");
        dv = enc.getDateValuesJson();
        assertEquals(dv.length(), 5);
        assertEquals(dv.getInt("minutes"), 0);
        // set a valid minutes
        enc.setMinutes("15");
        dv = enc.getDateValuesJson();
        assertEquals(dv.length(), 5);
        assertEquals(dv.getInt("minutes"), 15);
    }

    @Test void encounterApiGetTest()
    throws ServletException, IOException {
        Encounter enc = new Encounter();
        User user = new User();
        Shepherd myShepherd = mock(Shepherd.class);
        // MediaAsset ma = mock(MediaAsset.class);
        MediaAsset ma = new MediaAsset();

        enc.addMediaAsset(ma);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenThrow(new RuntimeException(
                "ohmgee"));
        })) {
            try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                    org.mockito.Answers.CALLS_REAL_METHODS)) {
                mockCollab.when(() -> Collaboration.securityEnabled(any(String.class))).thenReturn(
                    true);
                try (MockedStatic<CommonConfiguration> mockConfig = mockStatic(
                    CommonConfiguration.class)) {
                    // this returns list of fakeConfValues for *anything* calling getIndexedPropertyValues()
                    mockConfig.when(() -> CommonConfiguration.getIndexedPropertyValues(any(
                        String.class), any(String.class))).thenReturn(fakeConfValues);
                    // first test anon user
                    JSONObject res = enc.jsonForApiGet(myShepherd, null);
                    assertEquals(res.getJSONArray("mediaAssets").length(), 1);
                    // now test with user
                    MediaAsset mockMA = mock(MediaAsset.class);
                    when(mockMA.getDetectionStatus()).thenReturn("complete");
                    when(mockMA.getUserFilename()).thenReturn("test-file-name");
                    when(mockMA.getKeywordsJSONArray()).thenReturn(new JSONArray());
                    try (MockedStatic<MediaAssetFactory> mockMAF = mockStatic(
                        MediaAssetFactory.class)) {
                        mockMAF.when(() -> MediaAssetFactory.loadByUuid(any(String.class),
                            any(Shepherd.class))).thenReturn(mockMA);
                        Annotation mockAnnot = mock(Annotation.class);
                        when(mockAnnot.getMediaAsset()).thenReturn(mockMA);
                        when(mockAnnot.getIdentificationStatus()).thenReturn("test");
                        when(mockAnnot.getId()).thenReturn("test-annot-id");
                        when(myShepherd.getAnnotation(any(String.class))).thenReturn(mockAnnot);
                        when(mockMA.hasAnnotations()).thenReturn(true);
                        ArrayList<Annotation> anns = new ArrayList<Annotation>();
                        anns.add(mockAnnot);
                        when(mockMA.getAnnotations()).thenReturn(anns);
                        enc.addAnnotation(mockAnnot);
                        user.setUsername("test");
                        enc.setSubmitterID("test");
                        res = enc.jsonForApiGet(myShepherd, user);
                        assertEquals(res.getJSONArray("mediaAssets").length(), 2);
                        assertEquals(res.getJSONArray("mediaAssets").getJSONObject(0).getString(
                            "detectionStatus"), "complete");
                        assertEquals(res.getJSONArray("mediaAssets").getJSONObject(0).getString(
                            "userFilename"), "test-file-name");
                        assertNotNull(res.getJSONArray("mediaAssets").getJSONObject(0).optString(
                            "uuid", null));
                        assertEquals(res.getJSONArray("mediaAssets").getJSONObject(1).getJSONArray(
                            "annotations").length(), 1);
                        assertEquals(res.getJSONArray("mediaAssets").getJSONObject(1).getJSONArray(
                            "annotations").getJSONObject(0).getString("identificationStatus"),
                            "test");
                        assertEquals(res.getJSONArray("mediaAssets").getJSONObject(1).getJSONArray(
                            "annotations").getJSONObject(0).getString("id"), "test-annot-id");
                    }
                }
            }
        }
    }
}
