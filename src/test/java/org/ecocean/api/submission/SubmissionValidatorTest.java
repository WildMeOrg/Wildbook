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
}
