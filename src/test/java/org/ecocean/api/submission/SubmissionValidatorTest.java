package org.ecocean.api.submission;

import java.nio.file.Path;
import java.util.Properties;
import org.ecocean.*;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.submission.Submission;
import org.json.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionValidatorTest {
    @TempDir Path root;
    @Test void configuredMembershipStrictFieldsMediaAndPartialDateUseSharedValidation() throws Exception {
        try (MockedStatic<CommonConfiguration> config = mockStatic(CommonConfiguration.class)) {
        JSONObject locations = new JSONObject("{\"locationID\":[{\"id\":\"reef\"}]}");
        Shepherd sh = mock(Shepherd.class); when(sh.isValidTaxonomyName(anyString())).thenReturn(true);
        SubmissionFiles storage = new SubmissionFiles(root);
        JSONObject image = storage.write("one.png", new java.io.ByteArrayInputStream(SubmissionFilesTest.png()), 10000);
        Submission draft = new Submission("id", "context0", "owner", "hash", "hash", "{}", 0, Long.MAX_VALUE);
        JSONObject fields = new JSONObject().put("Encounter.genus", "Manta").put("Encounter.specificEpithet", "birostris")
            .put("Encounter.year", 2025).put("Encounter.locationID", "reef").put("Encounter.mediaAsset0", "one.png");
        draft.setFiles(new JSONArray().put(image).toString());
        draft.replaceRows(new JSONArray().put(new JSONObject().put("clientRowId", "source-row").put("fields", fields)).toString());
        try (MockedStatic<LocationID> location = mockStatic(LocationID.class)) {
            location.when(LocationID::getLocationIDStructure).thenReturn(locations);
            location.when(() -> LocationID.isValidLocationID("reef")).thenReturn(true);
            JSONObject report = new SubmissionValidator().validate(draft, sh, storage);
            assertTrue(report.getBoolean("valid"), report.toString());
            assertFalse(report.getJSONArray("normalizedRows").getJSONObject(0).getJSONObject("fields").has("Encounter.month"));
            fields.put("Encounter.id", "arbitrary").put("Encounter.locationID", "unknown").put("Encounter.mediaAsset0", "missing.png");
            draft.replaceRows(new JSONArray().put(new JSONObject().put("clientRowId", "source-row").put("fields", fields)).toString());
            report = new SubmissionValidator().validate(draft, sh, storage);
            assertFalse(report.getBoolean("valid"));
            assertTrue(report.getJSONArray("errors").toString().contains("UNSUPPORTED_FIELD"));
            assertTrue(report.getJSONArray("errors").toString().contains("INVALID_LOCATION"));
            assertTrue(report.getJSONArray("errors").toString().contains("MISSING_MEDIA"));
            verify(sh, never()).getPM(); // validation never persists domain objects
        }
        }
    }

    @Test void sharedSightingIdIsAcceptedOnlyWhenWellFormedAndNew() throws Exception {
        try (MockedStatic<CommonConfiguration> config = mockStatic(CommonConfiguration.class);
             MockedStatic<LocationID> location = mockStatic(LocationID.class)) {
            location.when(LocationID::getLocationIDStructure).thenReturn(new JSONObject("{\"locationID\":[{\"id\":\"reef\"}]}"));
            location.when(() -> LocationID.isValidLocationID("reef")).thenReturn(true);
            Shepherd sh = mock(Shepherd.class); when(sh.isValidTaxonomyName(anyString())).thenReturn(true);
            when(sh.getOccurrence("taken")).thenReturn(new Occurrence("taken"));
            SubmissionFiles storage = new SubmissionFiles(root); JSONArray files = new JSONArray();
            for (int n = 0; n < 9; n++) files.put(storage.write(n + ".png", new java.io.ByteArrayInputStream(SubmissionFilesTest.png()), 10000));
            Object[] values = { "UGA_PUWR_20250822080851", "UGA_PUWR_20250822080851", "UGA_PUWR_20250822080851",
                "taken", "taken", "<img src=x onerror=alert(1)>", "-leading", 42, JSONObject.NULL };
            JSONArray rows = new JSONArray();
            for (int n = 0; n < values.length; n++)
                rows.put(new JSONObject().put("clientRowId", "row-" + n).put("fields", new JSONObject().put("Encounter.genus", "Manta")
                    .put("Encounter.specificEpithet", "birostris").put("Encounter.year", 2025).put("Encounter.locationID", "reef")
                    .put("Encounter.mediaAsset0", n + ".png").put("Encounter.sightingID", values[n])));
            Submission draft = new Submission("id", "context0", "owner", "hash", "hash", "{}", 0, Long.MAX_VALUE);
            draft.setFiles(files.toString()); draft.replaceRows(rows.toString());
            JSONObject report = new SubmissionValidator().validate(draft, sh, storage);
            java.util.Map<String, String> codes = new java.util.HashMap<>();
            for (Object issue : report.getJSONArray("errors")) {
                JSONObject error = (JSONObject)issue;
                assertEquals("Encounter.sightingID", error.getString("field"), report.toString());
                assertNull(codes.put(error.getString("clientRowId"), error.getString("code")), "one issue per row");
            }
            assertEquals(java.util.Map.of("row-3", "SIGHTING_EXISTS", "row-4", "SIGHTING_EXISTS", "row-5", "INVALID_VALUE",
                "row-6", "INVALID_VALUE", "row-7", "INVALID_VALUE", "row-8", "INVALID_VALUE"), codes);
            for (int n = 0; n < 3; n++)
                assertTrue(report.getJSONArray("normalizedRows").getJSONObject(n).getJSONObject("fields").toString().contains("UGA_PUWR_20250822080851"));
            verify(sh, times(1)).getOccurrence("UGA_PUWR_20250822080851"); // one lookup per ID
            verify(sh, times(1)).getOccurrence("taken");
            verify(sh, never()).getOccurrence("<img src=x onerror=alert(1)>");
            assertTrue(SubmissionValidator.SIGHTING_ID_FORMAT.matcher("a".repeat(100)).matches());
            assertFalse(SubmissionValidator.SIGHTING_ID_FORMAT.matcher("a".repeat(101)).matches());
            assertFalse(SubmissionValidator.SIGHTING_ID_FORMAT.matcher("").matches());
            assertFalse(SubmissionValidator.SIGHTING_ID_FORMAT.matcher("a b").matches());
            assertFalse(SubmissionValidator.SIGHTING_ID_FORMAT.matcher("a&b").matches());
            assertFalse(SubmissionValidator.supported("Sighting.sightingID"));
        }
    }
}
