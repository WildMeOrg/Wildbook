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
import org.ecocean.Keyword;
import org.ecocean.media.MediaAsset;
import org.ecocean.Occurrence;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulkImagesTest {
    PersistenceManagerFactory mockPMF;
    PersistenceManager mockPM = mock(PersistenceManager.class);
    HttpServletRequest mockRequest;
    HttpServletResponse mockResponse;
    BulkImport apiServlet;
    StringWriter responseOut;
    List<File> emptyFiles = new ArrayList<File>();
    // this seems to be based on repo root, but TODO is to verify in all situations
    File mockUploadDir = new File("src/test/bulk-images");
    MediaAsset mockMediaAsset;

    @BeforeEach void setUp()
    throws IOException {
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        mockPMF = mock(PersistenceManagerFactory.class);
        apiServlet = new BulkImport();
        mockMediaAsset = mock(MediaAsset.class);

        responseOut = new StringWriter();
        PrintWriter writer = new PrintWriter(responseOut);
        when(mockResponse.getWriter()).thenReturn(writer);
    }

    private JSONObject basePayload() {
        JSONObject payload = new JSONObject();

        payload.put("bulkImportId", "00000000-0000-0000-0000-000000000000");
        payload.put("tolerance", new JSONObject());
        return payload;
    }

    private String getValidPayloadArrays() {
        JSONObject rtn = basePayload();
        JSONArray fieldNames = new JSONArray();

        fieldNames.put("Encounter.mediaAsset0");
        fieldNames.put("Encounter.year");
        fieldNames.put("Encounter.genus");
        fieldNames.put("Encounter.specificEpithet");
        fieldNames.put("Encounter.submitterID");
        // may as well test these too since we have some MediaAssets
        fieldNames.put("Encounter.keyword0");
        fieldNames.put("Encounter.mediaAsset0.keywords");
        rtn.put("fieldNames", fieldNames);

        JSONArray rows = new JSONArray();
        for (int i = 0; i < 5; i++) {
            JSONArray row = new JSONArray();
            // we only have image-ok-0.jpg to test on, so recycle here
            // row.put("image-ok-" + i + ".jpg");
            row.put("image-ok-0.jpg");
            row.put(2000 + i);
            row.put("Genus" + i);
            row.put("specificEpithet" + i);
            row.put("fake-username");
            row.put("keywordA");
            row.put("keywordB_keywordC");
            rows.put(row);
        }
        rtn.put("rows", rows);
        rtn.put("verbose", true);
        return rtn.toString();
    }

    private String getPayloadBadImageFilenames() {
        JSONObject rtn = new JSONObject(getValidPayloadArrays());

        for (int i = 0; i < 5; i++) {
            rtn.getJSONArray("rows").getJSONArray(i).put(0, "image-missing-" + i + ".jpg");
        }
        return rtn.toString();
    }

    // adds values to only first row of data
    private String addToRows(String jsonString, String fieldName, Object value) {
        JSONObject json = new JSONObject(jsonString);

        json.getJSONArray("fieldNames").put(fieldName);
        json.getJSONArray("rows").getJSONArray(0).put(value);
        return json.toString();
    }

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

    @Test void apiPostMissingFiles()
    throws ServletException, IOException {
        User user = mock(User.class);
        Occurrence occ = mock(Occurrence.class);
        String requestBody = getValidPayloadArrays();

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getUser(any(String.class))).thenReturn(user);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
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
                    assertEquals(jout.getJSONArray("errors").length(), 5);
                    assertEquals(jout.getJSONArray("errors").getJSONObject(0).getString(
                        "fieldName"), "Encounter.mediaAsset0");
                    assertTrue(jout.getJSONArray("errors").getJSONObject(0).getString(
                        "details").contains("not found in uploaded files"));
                }
            }
        }
    }

    @Test void apiFilesFoundButInvalid()
    throws ServletException, IOException {
        User user = mock(User.class);
        Occurrence occ = mock(Occurrence.class);
        String requestBody = getPayloadBadImageFilenames();

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getUser(any(String.class))).thenReturn(user);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
        })) {
            // re: CALLS_REAL_METHODS, h/t to https://stackoverflow.com/a/63841319
            try (MockedStatic<UploadedFiles> mockUF = mockStatic(UploadedFiles.class,
                    org.mockito.Answers.CALLS_REAL_METHODS)) {
                mockUF.when(() -> UploadedFiles.getUploadDir(any(HttpServletRequest.class),
                    any(String.class))).thenReturn(mockUploadDir);
                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
                        mockPMF);
                    apiServlet.doPost(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(400);
                    assertFalse(jout.getBoolean("success"));
                    assertEquals(jout.getJSONArray("errors").length(), 5);
                    assertTrue(jout.getJSONArray("errors").getJSONObject(0).getString(
                        "details").contains("not found in uploaded files"));
                }
            }
        }
    }

    @Test void apiPostValid()
    throws ServletException, IOException {
        User user = mock(User.class);
        Occurrence occ = mock(Occurrence.class);
        String requestBody = getValidPayloadArrays();

        when(mockRequest.getRequestURI()).thenReturn("/api/v3/bulk-import");
        when(mockRequest.getReader()).thenReturn(new BufferedReader(new StringReader(requestBody)));

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getUser(any(HttpServletRequest.class))).thenReturn(user);
            when(mock.getUser(any(String.class))).thenReturn(user);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
            when(mock.getPM()).thenReturn(mockPM);
        })) {
            try (MockedStatic<UploadedFiles> mockUF = mockStatic(UploadedFiles.class,
                    org.mockito.Answers.CALLS_REAL_METHODS)) {
                mockUF.when(() -> UploadedFiles.getUploadDir(any(HttpServletRequest.class),
                    any(String.class))).thenReturn(mockUploadDir);
                mockUF.when(() -> UploadedFiles.makeMediaAsset(any(String.class), any(File.class),
                    any(Shepherd.class))).thenReturn(mockMediaAsset);
                try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                    mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(
                        mockPMF);
                    apiServlet.doPost(mockRequest, mockResponse);
                    responseOut.flush();
                    JSONObject jout = new JSONObject(responseOut.toString());
                    verify(mockResponse).setStatus(200);
                    assertTrue(jout.getBoolean("success"));
                    assertEquals(jout.getJSONArray("encounters").length(), 5);
                    // our mocked MediaAsset is the only one created, so
                    // we only get 1 id here (which is 0)
                    assertEquals(jout.getJSONArray("mediaAssets").length(), 1);
                    // this is called 3 keywords on 5 assets = 15 times
                    verify(mockMediaAsset, times(15)).addKeyword(any(Keyword.class));
                    // TODO kinda wish i could figure out that the keywords are correct
                }
            }
        }
    }
}
