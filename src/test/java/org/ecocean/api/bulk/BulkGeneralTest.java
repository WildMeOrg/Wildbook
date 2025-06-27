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
            row.put(fn, "value-" + fn);
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
                // this should not throw any exception
                Map<String, Object> res = testOneRow(row);
                // res will have hacky extra key "_BulkImporter", so:
                assertEquals(res.size(), row.size() + 1);
            }
        }
    }

    private Map<String, Object> baseRow() {
        Map<String, Object> row = new HashMap<String, Object>();

        row.put("Encounter.submitterID", "fakeSubmitterId");
        return row;
    }

    @Test void geneticSamples()
    throws ServletException {
        Occurrence occ = mock(Occurrence.class);
        User user = mock(User.class);
        Map<String, Object> row = baseRow();
        String tsType = "test-type";

        row.put("TissueSample.sampleID", "tissue-sample-id");
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
                List<TissueSample> ts = enc.getTissueSamples();
                assertEquals(Util.collectionSize(ts), 1);
                assertEquals(ts.get(0).getTissueType(), tsType);
                assertEquals(Util.collectionSize(ts.get(0).getGeneticAnalyses()), 0);
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

    Map<String, Object> testOneRow(Map<String, Object> singleRowData)
    throws ServletException {
        // Shepherd should be handled by caller via MockConstruction etc
        // see fieldNameCoverage() for example
        Shepherd myShepherd = new Shepherd("context0");
        String impId = "test-one-row";
        List<Map<String, Object> > allRows = new ArrayList<Map<String, Object> >();
        Map<String, Object> row = new HashMap<String, Object>();

        for (String fieldName : singleRowData.keySet()) {
            BulkValidator bv = null;
            try {
                bv = new BulkValidator(fieldName, singleRowData.get(fieldName), myShepherd);
                // System.out.println("A A A A A A A A A A A A A A A " + fieldName + ": " + bv);
                row.put(fieldName, bv);
            } catch (Exception ex) {
                row.put(fieldName, ex);
                // System.out.println("B B B B B B B B B B B B B B B " + fieldName + ": " + ex);
            }
        }
        allRows.add(row);
        BulkImporter imp = new BulkImporter(impId, allRows, null, null, myShepherd);
        imp.createImport();
        row.put("_BulkImporter", imp); // hacky but i am going to allow it for now
        return row;
    }
}
