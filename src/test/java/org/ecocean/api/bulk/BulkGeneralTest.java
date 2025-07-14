package org.ecocean;

import org.ecocean.api.bulk.*;
import org.ecocean.genetics.*;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.ShepherdPMF;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletException;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BulkGeneralTest {
    static String fieldNameValidEncounterYear = "Encounter.year";
    static String fieldNameInvalid = "Fail.fubar";
    PersistenceManagerFactory mockPMF;
    PersistenceManager mockPM = mock(PersistenceManager.class);

    @Test void basicValidation()
    throws BulkValidatorException {
        BulkValidator bv = new BulkValidator(fieldNameValidEncounterYear, 2000, null);

        assertNotNull(bv);
        assertFalse(bv.isIndexed());
        assertTrue(BulkValidator.isValidFieldName(fieldNameValidEncounterYear));
        assertFalse(BulkValidator.isValidFieldName(fieldNameInvalid));

        BulkValidatorException ex = assertThrows(BulkValidatorException.class, () -> {
            BulkValidator bvFail = new BulkValidator(fieldNameInvalid, 0, null);
        });
        assertTrue(ex.getMessage().contains("invalid fieldName"));
        assertEquals(ex.getType(), "UNKNOWN_FIELDNAME");

        bv = new BulkValidator("Encounter.mediaAsset123", "fake", null);
        assertTrue(bv.isIndexed());
        assertEquals(bv.getIndexInt(), 123);
        assertEquals(bv.getIndexPrefix(), "Encounter.mediaAsset");
        assertEquals(bv.getValue(), "fake");
        assertEquals(bv.getFieldName(), "Encounter.mediaAsset123");

        // tests the embedded 3-part flavor
        bv = new BulkValidator("Encounter.informOther99.emailAddress", "fake@example.com", null);
        assertTrue(bv.isIndexed());
        assertEquals(bv.getIndexInt(), 99);
        assertEquals(bv.getIndexPrefix(), "Encounter.informOther.emailAddress");
        assertEquals(bv.getValue(), "fake@example.com");
        assertEquals(bv.getFieldName(), "Encounter.informOther99.emailAddress");

        // invalid email address on indexy thing
        ex = assertThrows(BulkValidatorException.class, () -> {
            BulkValidator bvFail = new BulkValidator("Encounter.photographer11.emailAddress",
                "not_email_valid_example.com", null);
        });
        assertTrue(ex.getMessage().equals("invalid email address"));

        // some indexable fieldName tests
        assertTrue(BulkValidator.isValidFieldName("Encounter.mediaAsset123"));
        assertEquals(BulkValidator.indexPrefixValue("Encounter.mediaAsset123"),
            "Encounter.mediaAsset");
        assertEquals(BulkValidator.indexIntValue("Encounter.mediaAsset123"), 123);
        assertNull(BulkValidator.indexPrefixValue(fieldNameValidEncounterYear));
        assertEquals(BulkValidator.indexIntValue(fieldNameValidEncounterYear), -1);
        ex = assertThrows(BulkValidatorException.class, () -> {
            BulkValidator.indexIntValue(fieldNameInvalid);
        });
        assertTrue(ex.getMessage().contains("invalid fieldName"));
        ex = assertThrows(BulkValidatorException.class, () -> {
            BulkValidator.indexPrefixValue(fieldNameInvalid);
        });
        assertTrue(ex.getMessage().contains("invalid fieldName"));
    }

    @Test void basicUtilTests() {
        Set<String> fields = new HashSet<String>();

        fields.add("Encounter.mediaAsset0");
        fields.add("Encounter.mediaAsset1");
        fields.add("Encounter.mediaAsset4");
        fields.add("Encounter.mediaAsset0.keywords");
        fields.add("Encounter.keyword0");
        fields.add("Encounter.keyword3");
        fields.add("Encounter.year");
        List<String> found = BulkImportUtil.findIndexedFieldNames(fields, "Encounter.mediaAsset");
        assertEquals(found.size(), 5);
        assertEquals(found.get(4), "Encounter.mediaAsset4");
        assertNull(found.get(3));
    }

    @Test void basicUtilTestValidateRow() {
        Map<String, Object> rowResult = BulkImportUtil.validateRow(null, null);

        assertNotNull(rowResult);
        assertEquals(rowResult.size(), 0);

        JSONObject rowData = new JSONObject();
        rowData.put(fieldNameValidEncounterYear, 2000);
        rowData.put(fieldNameInvalid, "fail");
        rowResult = BulkImportUtil.validateRow(rowData, null);
        assertNotNull(rowResult);
        assertEquals(rowResult.size(), 4);
        assertTrue(rowResult.get(fieldNameValidEncounterYear) instanceof BulkValidator);
        assertTrue(rowResult.get(fieldNameInvalid) instanceof BulkValidatorException);
        BulkValidator bv = (BulkValidator)rowResult.get(fieldNameValidEncounterYear);
        assertEquals(bv.getFieldName(), fieldNameValidEncounterYear);
    }

    @Test void fieldIntegrity() {
        for (String f : BulkValidator.MINIMAL_FIELD_NAMES_STRING) {
            if (!BulkValidator.FIELD_NAMES.contains(f))
                throw new RuntimeException("BulkValidator.FIELD_NAMES is missing " + f);
        }
        for (String f : BulkValidator.MINIMAL_FIELD_NAMES_INT) {
            if (!BulkValidator.FIELD_NAMES.contains(f))
                throw new RuntimeException("BulkValidator.FIELD_NAMES is missing " + f);
        }
        for (String f : BulkValidator.MINIMAL_FIELD_NAMES_DOUBLE) {
            if (!BulkValidator.FIELD_NAMES.contains(f))
                throw new RuntimeException("BulkValidator.FIELD_NAMES is missing " + f);
        }
    }

    @Test void fieldNameCoverage()
    throws ServletException {
        Map<String, Object> row = new HashMap<String, Object>();
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);

        for (String fn : BulkValidator.FIELD_NAMES) {
            row.put(fn, "123");
        }
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getContext()).thenReturn("context0");
            when(mock.getPM()).thenReturn(mockPM);
            when(mock.getUser(any(String.class))).thenReturn(user);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                Map<String, Object> res = testOneRow(row);
                // res will have hacky extra meta values, we need to add:
                assertEquals(res.size(), row.size() + 3);
            }
        }
    }

    private Map<String, Object> baseRow() {
        Map<String, Object> row = new HashMap<String, Object>();

        row.put("Encounter.submitterID", "fakeSubmitterId");
        row.put("Encounter.year", 2000);
        // note, you should mock Shepherd.isValidTaxonomyName() to allow these
        row.put("Encounter.genus", "genus");
        row.put("Encounter.specificEpithet", "specificEpithet");
        return row;
    }

    @Test void locationIdTest()
    throws ServletException {
        Map<String, Object> row = baseRow();
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);
        JSONObject locJson = new JSONObject();

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getContext()).thenReturn("context0");
            when(mock.getPM()).thenReturn(mockPM);
            when(mock.getUser(any(String.class))).thenReturn(user);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<LocationID> mockLocClass = mockStatic(LocationID.class)) {
                    // here we mock the locationID validator mechanism
                    mockLocClass.when(() -> LocationID.isValidLocationID("fail")).thenReturn(false);
                    mockLocClass.when(() -> LocationID.isValidLocationID("pass")).thenReturn(true);
                    row.put("Encounter.locationID", "fail");
                    Map<String, Object> res = testOneRow(row);
                    assertTrue(res.get("Encounter.locationID") instanceof BulkValidatorException);
                    row.put("Encounter.locationID", "pass");
                    res = testOneRow(row);
                    assertTrue(res.get("Encounter.locationID") instanceof BulkValidator);
                }
            }
        }
    }

    @Test void dateTimeTest()
    throws ServletException {
        Map<String, Object> row = baseRow();
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getContext()).thenReturn("context0");
            when(mock.getPM()).thenReturn(mockPM);
            when(mock.getUser(any(String.class))).thenReturn(user);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                row.put("Encounter.year", 1);
                Map<String, Object> res = testOneRow(row);
                // year
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.year") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.year").toString().contains("required value"));
                row.put("Encounter.year", 999999); // TODO adjust higher when we hit the year 999999
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.year") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.year").toString().contains("future"));
                // month
                row.put("Encounter.year", 2000);
                row.put("Encounter.month", 0);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.month") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.month").toString().contains("too small"));
                row.put("Encounter.month", 99);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.month") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.month").toString().contains("too large"));
                // day
                row.put("Encounter.month", 2);
                row.put("Encounter.day", 0);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.day") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.day").toString().contains("too small"));
                row.put("Encounter.day", 99);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.day") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.day").toString().contains("too large"));
                // humans and their calendars, amirite?
                row.put("Encounter.day", 30);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.day") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.day").toString().contains("out of range for month"));
                // hour
                row.put("Encounter.day", 1);
                row.put("Encounter.hour", -3);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.hour") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.hour").toString().contains("too small"));
                row.put("Encounter.hour", 99);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.hour") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.hour").toString().contains("too large"));
                // minutes
                row.put("Encounter.hour", 13);
                row.put("Encounter.minutes", -3);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.minutes") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.minutes").toString().contains("too small"));
                row.put("Encounter.minutes", 99);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.minutes") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.minutes").toString().contains("too large"));
            }
        }
    }

    @Test void miscFieldValidationTest()
    throws ServletException {
        Map<String, Object> row = baseRow();
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getContext()).thenReturn("context0");
            when(mock.getPM()).thenReturn(mockPM);
            // the default (from baseRow) submitter id will work, username 'fail' will not
            when(mock.getUser("fakeSubmitterId")).thenReturn(user);
            when(mock.getUser("fail")).thenReturn(null);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                row.put("Encounter.id", "not-a-uuid");
                Map<String, Object> res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.id") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.id").toString().contains("proper UUID"));
                row.remove("Encounter.id");
                // cannot have just one (lat)
                row.put("Encounter.decimalLatitude", 50.0);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.decimalLongitude") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.decimalLongitude").toString().contains(
                    "must supply both"));
                // cannot have just one (lon)
                row.remove("Encounter.decimalLatitude");
                row.put("Encounter.decimalLongitude", 50.0);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.decimalLatitude") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.decimalLatitude").toString().contains(
                    "must supply both"));
                // invalid latitude
                row.put("Encounter.decimalLatitude", 666.0);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.decimalLatitude") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.decimalLatitude").toString().contains("invalid"));
                // invalid longitude
                row.put("Encounter.decimalLatitude", -10.0);
                row.put("Encounter.decimalLongitude", 666.0);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.decimalLongitude") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.decimalLongitude").toString().contains("invalid"));
                // valid lat/lon!
                row.put("Encounter.decimalLongitude", -6.66);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 0);
                // sex
                row.put("Encounter.sex", "fail");
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.sex") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.sex").toString().contains("invalid sex value"));
                row.remove("Encounter.sex");
                // state
                row.put("Encounter.state", "fail");
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.state") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.state").toString().contains("invalid state value"));
                row.remove("Encounter.state");
                // user
                row.put("Encounter.submitterID", "fail");
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Encounter.submitterID") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.submitterID").toString().contains(
                    "invalid username"));
                // we have effectively been testing fakeSubmitterID successfully, so lets toggle to public
                row.put("Encounter.submitterID", "public");
                // positive int check
                row.put("Sighting.individualCount", -3);
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get("Sighting.individualCount") instanceof BulkValidatorException);
                assertTrue(res.get("Sighting.individualCount").toString().contains("0 or larger"));
                row.remove("Sighting.individualCount");
                // email flavors
                row.put("Encounter.informOther0.emailAddress", "fubar");
                res = testOneRow(row);
                assertEquals(res.get("_numInvalid"), 1);
                assertTrue(res.get(
                    "Encounter.informOther0.emailAddress") instanceof BulkValidatorException);
                assertTrue(res.get("Encounter.informOther0.emailAddress").toString().contains(
                    "invalid email address"));
                row.put("Encounter.informOther0.emailAddress", "pass@example.com");
                // setup some fake CommonConfiguration returns
                List<String> fakeList = new ArrayList<String>();
                fakeList.add("pass"); // a value of 'pass' will be valid, otherwise not
                try (MockedStatic<CommonConfiguration> mockCCClass = mockStatic(
                    CommonConfiguration.class)) {
                    mockCCClass.when(() -> CommonConfiguration.getIndexedPropertyValues(any(
                        String.class), any(String.class))).thenReturn(fakeList);
                    // lifeStage
                    row.put("Encounter.lifeStage", "fail");
                    res = testOneRow(row);
                    assertEquals(res.get("_numInvalid"), 1);
                    assertTrue(res.get("Encounter.lifeStage") instanceof BulkValidatorException);
                    assertTrue(res.get("Encounter.lifeStage").toString().contains(
                        "invalid lifeStage value"));
                    row.put("Encounter.lifeStage", "pass");
                    res = testOneRow(row);
                    assertEquals(res.get("_numInvalid"), 0);
                    // livingStatus
                    row.put("Encounter.livingStatus", "fail");
                    res = testOneRow(row);
                    assertEquals(res.get("_numInvalid"), 1);
                    assertTrue(res.get("Encounter.livingStatus") instanceof BulkValidatorException);
                    assertTrue(res.get("Encounter.livingStatus").toString().contains(
                        "invalid livingStatus value"));
                    row.put("Encounter.livingStatus", "pass");
                    res = testOneRow(row);
                    assertEquals(res.get("_numInvalid"), 0);
                    // country
                    row.put("Encounter.country", "fail");
                    res = testOneRow(row);
                    assertEquals(res.get("_numInvalid"), 1);
                    assertTrue(res.get("Encounter.country") instanceof BulkValidatorException);
                    assertTrue(res.get("Encounter.country").toString().contains(
                        "invalid country value"));
                    row.put("Encounter.country", "pass");
                    res = testOneRow(row);
                }
            }
        }
    }

    @Test void projectsTest()
    throws ServletException {
        Map<String, Object> row = baseRow();
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getContext()).thenReturn("context0");
            when(mock.getPM()).thenReturn(mockPM);
            // the default (from baseRow) submitter id will work, username 'fail' will not
            when(mock.getUser("fakeSubmitterId")).thenReturn(user);
            when(mock.getUser("fail")).thenReturn(null);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                row.put("Encounter.project0.projectIdPrefix", "proj0");
                // this project will not exist, but since we have no name/email, it will not get created
                Shepherd myShepherd = new Shepherd("context0");
                Map<String, Object> res = testOneRow(row);
                BulkImporter bimp = (BulkImporter)res.get("_BulkImporter");
                Encounter enc = bimp.getEncounters().get(0);
                assertEquals(Util.collectionSize(enc.getProjects(myShepherd)), 0);
                // now we give a name and email, which should create
                row.put("Encounter.project0.researchProjectName", "Proj0 Name");
                row.put("Encounter.project0.ownerUsername", "fakeSubmitterId");
                res = testOneRow(row);
                bimp = (BulkImporter)res.get("_BulkImporter");
                enc = bimp.getEncounters().get(0);
                // TODO actually verify project
            }
        }
    }

    @Test void geneticSamples()
    throws ServletException {
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);
        Map<String, Object> row = baseRow();
        String tsType = "test-type";
        String tsId = "tissue-sample-id";

        row.put("TissueSample.sampleID", tsId);
        row.put("TissueSample.tissueType", tsType);
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getContext()).thenReturn("context0");
            when(mock.getPM()).thenReturn(mockPM);
            when(mock.getUser(any(String.class))).thenReturn(user);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                // simplest TissueSample
                Map<String, Object> res = testOneRow(row);
                assertNotNull(res);
                assertTrue(res.containsKey("_BulkImporter"));
                BulkImporter bimp = (BulkImporter)res.get("_BulkImporter");
                assertEquals(Util.collectionSize(bimp.getEncounters()), 1);
                Encounter enc = bimp.getEncounters().get(0);
                List<TissueSample> tsamps = enc.getTissueSamples();
                assertEquals(Util.collectionSize(tsamps), 1);
                assertEquals(tsamps.get(0).getTissueType(), tsType);
                assertEquals(tsamps.get(0).getSampleID(), tsId);
                assertEquals(Util.collectionSize(tsamps.get(0).getGeneticAnalyses()), 0);

                // test with only 1 of 3 fields needed (no analyses made)
                String tsId2 = "microsatellite-id";
                row.remove("TissueSample.sampleID");
                row.put("MicrosatelliteMarkersAnalysis.analysisID", tsId2);
                row.put("MicrosatelliteMarkersAnalysis.alleleNames", "foo,bar");
                res = testOneRow(row);
                bimp = (BulkImporter)res.get("_BulkImporter");
                enc = bimp.getEncounters().get(0);
                TissueSample ts = enc.getTissueSamples().get(0);
                assertEquals(ts.getSampleID(), tsId2);
                assertEquals(Util.collectionSize(ts.getGeneticAnalyses()), 0);

                // test with only 3 of 3 fields needed; but wrong count (no analyses made)
                row.put("MicrosatelliteMarkersAnalysis.alleleNames", "foo,bar");
                row.put("MicrosatelliteMarkersAnalysis.alleles0", "a,b");
                row.put("MicrosatelliteMarkersAnalysis.alleles1", "c,d,e");
                res = testOneRow(row);
                bimp = (BulkImporter)res.get("_BulkImporter");
                enc = bimp.getEncounters().get(0);
                ts = enc.getTissueSamples().get(0);
                assertEquals(Util.collectionSize(ts.getGeneticAnalyses()), 0);

                // should add markers for real
                row.put("MicrosatelliteMarkersAnalysis.alleleNames", "foo,bar");
                row.put("MicrosatelliteMarkersAnalysis.alleles0", "0,1");
                row.put("MicrosatelliteMarkersAnalysis.alleles1", "2,3");
                res = testOneRow(row);
                bimp = (BulkImporter)res.get("_BulkImporter");
                enc = bimp.getEncounters().get(0);
                ts = enc.getTissueSamples().get(0);
                assertEquals(Util.collectionSize(ts.getGeneticAnalyses()), 1);
                assertEquals(ts.getGeneticAnalyses().get(0).getAnalysisType(),
                    "MicrosatelliteMarkers");
                assertEquals(ts.getSampleID(), tsId2);

                // SexAnalysis
                row.remove("MicrosatelliteMarkersAnalysis.alleleNames");
                row.remove("MicrosatelliteMarkersAnalysis.alleles0");
                row.remove("MicrosatelliteMarkersAnalysis.alleles1");
                row.remove("MicrosatelliteMarkersAnalysis.analysisID");
                String tsId3 = "sexanalysis-id";
                row.put("SexAnalysis.processingLabTaskID", tsId3);
                row.put("SexAnalysis.sex", "sex-value");
                res = testOneRow(row);
                bimp = (BulkImporter)res.get("_BulkImporter");
                enc = bimp.getEncounters().get(0);
                ts = enc.getTissueSamples().get(0);
                assertEquals(Util.collectionSize(ts.getGeneticAnalyses()), 1);
                assertEquals(ts.getGeneticAnalyses().get(0).getAnalysisType(), "SexAnalysis");
                assertEquals(ts.getSampleID(), tsId3);

                // haplotype
                row.remove("SexAnalysis.processingLabTaskID");
                row.remove("SexAnalysis.sex");
                String tsId4 = "haplotype-id";
                row.put("TissueSample.sampleID", tsId4);
                row.put("MitochondrialDNAAnalysis.haplotype", "hap-type");
                res = testOneRow(row);
                bimp = (BulkImporter)res.get("_BulkImporter");
                enc = bimp.getEncounters().get(0);
                ts = enc.getTissueSamples().get(0);
                assertEquals(Util.collectionSize(ts.getGeneticAnalyses()), 1);
                assertEquals(ts.getGeneticAnalyses().get(0).getAnalysisType(), "MitochondrialDNA");
                assertEquals(ts.getSampleID(), tsId4);
            }
        }
    }

    @Test void indexedFields()
    throws ServletException {
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);
        User user0 = mock(User.class);
        User user1 = mock(User.class);
        Map<String, Object> row = baseRow();

        row.put("MarkedIndividual.individualID", "test-indiv-id");
        row.put("MarkedIndividual.name0.label", "test-name-label-0");
        row.put("MarkedIndividual.name0.value", "test-name-value-0");
        row.put("MarkedIndividual.name1.label", "test-name-label-1");
        row.put("MarkedIndividual.name1.value", "test-name-value-1");
        row.put("Encounter.informOther0.emailAddress", "inform0@example.com");
        row.put("Encounter.informOther1.emailAddress", "inform1@example.com");
        row.put("Encounter.photographer0.emailAddress", "inform0@example.com");
        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getContext()).thenReturn("context0");
            when(mock.getPM()).thenReturn(mockPM);
            when(mock.getUser(any(String.class))).thenReturn(user);
            when(mock.getUserByEmailAddress("inform0@example.com")).thenReturn(user0);
            when(mock.getUserByEmailAddress("inform1@example.com")).thenReturn(user1);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                // also tests individual
                Map<String, Object> res = testOneRow(row);
                assertNotNull(res);
                assertTrue(res.containsKey("_BulkImporter"));
                BulkImporter bimp = (BulkImporter)res.get("_BulkImporter");
                assertEquals(Util.collectionSize(bimp.getEncounters()), 1);
                Encounter enc = bimp.getEncounters().get(0);
                assertEquals(enc.getInformOthers().size(), 2);
                assertEquals(enc.getInformOthers().get(0), user0);
                assertEquals(enc.getInformOthers().get(1), user1);
                assertEquals(enc.getPhotographers().size(), 1);
                assertEquals(enc.getInformOthers().get(0), user0);
// System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> " + enc);
                MarkedIndividual indiv = enc.getIndividual();
                assertNotNull(indiv);
                Set<String> nameTest = indiv.getNames().getKeys();
                assertEquals(nameTest.size(), 3);
                assertTrue(nameTest.contains("*"));
                assertTrue(nameTest.contains("test-name-label-0"));
                assertTrue(nameTest.contains("test-name-label-1"));
                nameTest = indiv.getNames().getAllValues();
                assertEquals(nameTest.size(), 3);
                assertTrue(nameTest.contains("test-indiv-id"));
                assertTrue(nameTest.contains("test-name-value-0"));
                assertTrue(nameTest.contains("test-name-value-1"));
            }
        }
    }

    @Test void measurement() {
        List<String> mockMeasValues = new ArrayList<String>();

        mockMeasValues.add("Measurement0");
        mockMeasValues.add("Measurement1");
        try (MockedStatic<BulkImportUtil> mockUtil = mockStatic(BulkImportUtil.class)) {
            // allows us to skip commonConfig to get list of values
            mockUtil.when(() -> BulkImportUtil.getMeasurementValues()).thenReturn(mockMeasValues);
            JSONObject mf = BulkValidator.minimalFieldsJson();
            int ct = 0;
            for (String key : mockMeasValues) {
                assertEquals(mf.getString("Encounter.measurement" + ct), "double");
                assertEquals(mf.getString("Encounter.measurement" + ct + ".samplingProtocol"),
                    "string");
                assertEquals(mf.getString("Encounter." + key), "double");
                assertEquals(mf.getString("Encounter." + key + ".samplingProtocol"), "string");
                assertEquals(mf.getString("Encounter.measurement." + key), "double");
                assertEquals(mf.getString("Encounter.measurement." + key + ".samplingProtocol"),
                    "string");
                ct++;
            }
        }
    }

    @Test void measurementRow()
    throws ServletException {
        Map<String, Object> row = baseRow();
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);
        List<String> mockMeasValues = new ArrayList<String>();

        mockMeasValues.add("Measurement0");

        try (MockedConstruction<Shepherd> mockShepherd = mockConstruction(Shepherd.class,
                (mock, context) -> {
            when(mock.getContext()).thenReturn("context0");
            when(mock.getPM()).thenReturn(mockPM);
            when(mock.getUser(any(String.class))).thenReturn(user);
            when(mock.getOrCreateOccurrence(any(String.class))).thenReturn(occ);
            when(mock.getOrCreateOccurrence(null)).thenReturn(occ);
            when(mock.isValidTaxonomyName(any(String.class))).thenReturn(true);
        })) {
            try (MockedStatic<ShepherdPMF> mockService = mockStatic(ShepherdPMF.class)) {
                mockService.when(() -> ShepherdPMF.getPMF(any(String.class))).thenReturn(mockPMF);
                try (MockedStatic<BulkImportUtil> mockUtil = mockStatic(BulkImportUtil.class,
                        org.mockito.Answers.CALLS_REAL_METHODS)) {
                    // allows us to skip commonConfig to get list of values
                    mockUtil.when(() -> BulkImportUtil.getMeasurementValues()).thenReturn(
                        mockMeasValues);
                    row.put("Encounter.measurement0", 99.9);
                    row.put("Encounter.measurement0.samplingProtocol", "sampProt");
                    Map<String, Object> res = testOneRow(row);
                    BulkImporter bimp = (BulkImporter)res.get("_BulkImporter");
                    Encounter enc = bimp.getEncounters().get(0);
                    assertEquals(Util.collectionSize(enc.getMeasurements()), 1);
                    Measurement meas = enc.getMeasurements().get(0);
                    assertEquals(meas.getValue(), (Double)99.9);
                }
            }
        }
    }

    @Test void synonymFieldNames() {
        List<List<String> > res = BulkValidator.findSynonyms(null);

        assertNull(res);
        Set<String> fieldNames = new HashSet<String>();
        fieldNames.add("Fake.field");
        // these are in syn list, but no doubles of anything = null
        fieldNames.add("Encounter.id");
        fieldNames.add("Encounter.individualID");
        fieldNames.add("Encounter.year");
        res = BulkValidator.findSynonyms(fieldNames);
        assertNull(res);
        // now we add names to create 2 out of 3 of these have duplicates
        fieldNames.add("Encounter.catalogNumber");
        fieldNames.add("Sighting.year");
        res = BulkValidator.findSynonyms(fieldNames);
        assertEquals(res.size(), 3);
        assertEquals(res.get(0).size(), 2);
        assertEquals(res.get(1).size(), 2);
    }

    Map<String, Object> testOneRow(Map<String, Object> singleRowData)
    throws ServletException {
        // Shepherd should be handled by caller via MockConstruction etc
        // see fieldNameCoverage() for example
        Shepherd myShepherd = new Shepherd("context0");
        String impId = "test-one-row";
        List<Map<String, Object> > allRows = new ArrayList<Map<String, Object> >();
        Map<String, Object> row = new HashMap<String, Object>();

        row = BulkImportUtil.validateRow(new JSONObject(singleRowData), myShepherd);
        int numInvalid = 0;
        for (Object r : row.values()) {
            if (r instanceof Exception) numInvalid++;
        }
        // some hacky meta-stuff
        row.put("_numValid", singleRowData.size() - numInvalid);
        row.put("_numInvalid", numInvalid);
        // normally BulkImporter() would not be called if we had invalid rows, but we let it process
        // here, except in the case of an invalid users (as that blows up BulkImporter() in a bad way)
        if (row.containsKey("Encounter.submitterID") &&
            (row.get("Encounter.submitterID") instanceof Exception))
            return row;
        allRows.add(row);
        BulkImporter imp = new BulkImporter(impId, allRows, null, null, myShepherd);
        imp.createImport();
        row.put("_BulkImporter", imp);
        return row;
    }
}
