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

    // ---- bulk-import boxes PR E (spec 4.9) ----
    static final String DETECT = "{\"processing\":{\"mode\":\"detect-and-identify\"}}";
    static final String IMPORT_ONLY = "{\"processing\":{\"mode\":\"import-only\"}}";

    /** Manta birostris with a matchable class (body) and a non-matchable one (fin, default false). */
    static IAJsonProperties boxFixture() {
        JSONObject json = new JSONObject().put("Manta", new JSONObject().put("birostris", new JSONObject()
            .put("body", new JSONObject().put("_id_conf", new JSONArray().put(new JSONObject().put("default", true))))
            .put("fin", new JSONObject().put("_id_conf", new JSONArray().put(new JSONObject().put("default", false))))));
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac);
        iac.setJson(json);
        return iac;
    }

    interface Body { void run(Shepherd sh, SubmissionFiles storage, JSONObject image) throws Exception; }

    /** A mocked Shepherd, the reef location, the box fixture installed, one staged 2x2 "one.png". */
    void withBoxHarness(Body body) throws Exception {
        try (MockedStatic<CommonConfiguration> config = mockStatic(CommonConfiguration.class);
             MockedStatic<LocationID> location = mockStatic(LocationID.class)) {
            location.when(LocationID::getLocationIDStructure).thenReturn(new JSONObject("{\"locationID\":[{\"id\":\"reef\"}]}"));
            location.when(() -> LocationID.isValidLocationID("reef")).thenReturn(true);
            TestIAJsonPropertiesUtil.override(boxFixture());
            try {
                Shepherd sh = mock(Shepherd.class); when(sh.isValidTaxonomyName(anyString())).thenReturn(true);
                SubmissionFiles storage = new SubmissionFiles(root);
                JSONObject image = storage.write("one.png", new java.io.ByteArrayInputStream(SubmissionFilesTest.png()), 10000);
                body.run(sh, storage, image);
            } finally {
                TestIAJsonPropertiesUtil.override(null);
            }
        }
    }

    static JSONObject boxRow(String id, Object... kv) {
        JSONObject fields = new JSONObject().put("Encounter.genus", "Manta").put("Encounter.specificEpithet", "birostris")
            .put("Encounter.year", 2025).put("Encounter.locationID", "reef");
        for (int i = 0; i < kv.length; i += 2) fields.put((String)kv[i], kv[i + 1]);
        return new JSONObject().put("clientRowId", id).put("fields", fields);
    }

    static Submission boxDraft(String createJson, JSONArray files, JSONObject... rows) {
        Submission draft = new Submission("id", "context0", "owner", "hash", "hash", createJson, 0, Long.MAX_VALUE);
        draft.setFiles(files.toString());
        draft.replaceRows(new JSONArray(java.util.Arrays.asList(rows)).toString());
        return draft;
    }

    static java.util.List<JSONObject> issues(JSONObject report) {
        java.util.List<JSONObject> out = new java.util.ArrayList<>();
        for (Object o : report.getJSONArray("errors")) out.add((JSONObject)o);
        return out;
    }

    static JSONObject issueOn(JSONObject report, String field) {
        for (JSONObject i : issues(report)) if (field.equals(i.optString("field", null))) return i;
        return null;
    }

    @Test void boxFieldsAndQualityAreSupportedAndSuffixKeysAreNotPhotos() throws Exception {
        for (String suffix : org.ecocean.api.bulk.BulkValidator.BOX_SUFFIXES) assertTrue(SubmissionValidator.supported("Encounter.mediaAsset0." + suffix), suffix);
        assertTrue(SubmissionValidator.supported("Encounter.mediaAsset199.theta"));
        assertTrue(SubmissionValidator.supported("Encounter.quality7"));
        assertFalse(SubmissionValidator.supported("Encounter.mediaAsset200.bbox"));
        assertFalse(SubmissionValidator.supported("Encounter.mediaAsset0.keywords"));
        assertFalse(SubmissionValidator.supported("Encounter.quality200"));
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = new SubmissionValidator().validate(boxDraft(DETECT, new JSONArray().put(image),
                boxRow("r", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.bbox", "0,0,1,1", "Encounter.mediaAsset0.iaClass", "body", "Encounter.quality0", "0.5")), sh, storage);
            assertTrue(report.getBoolean("valid"), report.toString());
            assertEquals("0,0,1,1", report.getJSONArray("normalizedRows").getJSONObject(0).getJSONObject("fields").getString("Encounter.mediaAsset0.bbox"));
        });
    }

    @Test void unsupportedHugeSlotIndexIsRejectedWithoutTouchingTheSlotHelper() throws Exception {   // (regression)
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = new SubmissionValidator().validate(boxDraft(DETECT, new JSONArray().put(image),
                boxRow("r", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset1000000000", "x")), sh, storage);
            assertEquals(1, issues(report).size(), report.toString());
            assertEquals("UNSUPPORTED_FIELD", issues(report).get(0).getString("code"));
            assertEquals("Encounter.mediaAsset1000000000", issues(report).get(0).getString("field"));
        });
    }

    @Test void skipFlagFollowsTheProcessingMode() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject rejected = new SubmissionValidator().validate(boxDraft(DETECT, new JSONArray().put(image),
                boxRow("r", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.bbox", "0,0,1,1", "Encounter.mediaAsset0.iaClass", "fin")), sh, storage);
            JSONObject issue = issueOn(rejected, "Encounter.mediaAsset0.iaClass");
            assertNotNull(issue, "fin has no default option: rejected when identification is requested: " + rejected);
            assertEquals("INVALID_VALUE", issue.getString("code"));
            JSONObject accepted = new SubmissionValidator().validate(boxDraft(IMPORT_ONLY, new JSONArray().put(image),
                boxRow("r", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.bbox", "0,0,1,1", "Encounter.mediaAsset0.iaClass", "fin")), sh, storage);
            assertTrue(accepted.getBoolean("valid"), "import-only accepts any stored class: " + accepted);
        });
    }

    // ---- Task 2: empty values, image reuse across rows, bounds, reasons ----
    static JSONObject boxed(String id, String photo, String bbox, Object... more) {
        Object[] kv = new Object[6 + more.length];
        kv[0] = "Encounter.mediaAsset0"; kv[1] = photo; kv[2] = "Encounter.mediaAsset0.bbox"; kv[3] = bbox; kv[4] = "Encounter.mediaAsset0.iaClass"; kv[5] = "body";
        System.arraycopy(more, 0, kv, 6, more.length);
        return boxRow(id, kv);
    }

    static JSONObject plain(String id, String photo) {
        return boxRow(id, "Encounter.mediaAsset0", photo);
    }

    static JSONObject validateRows(Shepherd sh, SubmissionFiles storage, JSONArray files, String mode, JSONObject... rows) {
        return new SubmissionValidator().validate(boxDraft(mode, files, rows), sh, storage);
    }

    static void assertOnlyIssue(JSONObject report, String clientRowId, String field, String code, String messagePart) {
        assertEquals(1, issues(report).size(), report.toString());
        JSONObject issue = issues(report).get(0);
        assertEquals(clientRowId, issue.getString("clientRowId"), report.toString());
        assertEquals(field, issue.getString("field"), report.toString());
        assertEquals(code, issue.getString("code"), report.toString());
        assertTrue(issue.getString("message").contains(messagePart), issue.toString());
    }

    @Test void samePhotoWithDifferentBoxesOnTwoRowsIsAccepted() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxed("A", "one.png", "0,0,1,1"), boxed("B", "one.png", "1,1,1,1"));
            assertTrue(report.getBoolean("valid"), report.toString());
        });
    }

    @Test void identicalBoxTwiceIsDuplicateBox() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxed("A", "one.png", "0,0,1,1"), boxed("B", "one.png", "0,0,1,1"));
            assertOnlyIssue(report, "B", "Encounter.mediaAsset0.bbox", "DUPLICATE_BOX", "row 0");
        });
    }

    @Test void identicalBoxesAndAnUnboxedUseAreBothReported() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxed("A", "one.png", "0,0,1,1"), boxed("B", "one.png", "0,0,1,1"), plain("C", "one.png"));
            assertEquals(2, issues(report).size(), report.toString());
            JSONObject onB = null, onC = null;
            for (JSONObject i : issues(report)) { if ("B".equals(i.getString("clientRowId"))) onB = i; if ("C".equals(i.getString("clientRowId"))) onC = i; }
            assertNotNull(onB); assertEquals("DUPLICATE_BOX", onB.getString("code")); assertEquals("Encounter.mediaAsset0.bbox", onB.getString("field"));
            assertNotNull(onC); assertEquals("DUPLICATE_MEDIA", onC.getString("code")); assertEquals("Encounter.mediaAsset0", onC.getString("field"));
            assertTrue(onC.getString("message").contains("has a box on row 0 but none here"), onC.toString());
        });
    }

    @Test void mixedBoxedAndUnboxedUsesFlagTheUnboxedRows() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxed("A", "one.png", "0,0,1,1"), boxed("B", "one.png", "1,1,1,1"), plain("C", "one.png"));
            assertOnlyIssue(report, "C", "Encounter.mediaAsset0", "DUPLICATE_MEDIA", "has a box on row 0 but none here");
            report = validateRows(sh, storage, new JSONArray().put(image), DETECT, plain("C", "one.png"), boxed("A", "one.png", "0,0,1,1"), boxed("B", "one.png", "1,1,1,1"));
            assertOnlyIssue(report, "C", "Encounter.mediaAsset0", "DUPLICATE_MEDIA", "has a box on row 1 but none here");
        });
    }

    @Test void rejectedBoxStillCountsAsABoxedUse() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxed("A", "one.png", "bad"), plain("B", "one.png"));
            assertEquals(2, issues(report).size(), report.toString());
            JSONObject onA = issueOn(report, "Encounter.mediaAsset0.bbox"), onB = issueOn(report, "Encounter.mediaAsset0");
            assertNotNull(onA); assertEquals("INVALID_BOX", onA.getString("reason"));
            assertNotNull(onB); assertEquals("B", onB.getString("clientRowId")); assertEquals("DUPLICATE_MEDIA", onB.getString("code"));
            assertTrue(onB.getString("message").contains("has a box on row 0 but none here"));
            for (JSONObject i : issues(report)) assertNotEquals("DUPLICATE_BOX", i.getString("code"));
        });
    }

    @Test void unboxedRepeatsStayDuplicateMedia() throws Exception {   // (regression)
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, plain("A", "one.png"), plain("B", "one.png"));
            assertOnlyIssue(report, "B", "Encounter.mediaAsset0", "DUPLICATE_MEDIA", "Use each image in one row only");
        });
    }

    @Test void repeatedPhotoInOneRowGetsOneIssue() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxed("A", "one.png", "0,0,1,1", "Encounter.mediaAsset1", "one.png"));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset1", "DUPLICATE_MEDIA", "Image is repeated in this row");
            report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxRow("A", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset1", "one.png", "Encounter.mediaAsset1.bbox", "0,0,1,1", "Encounter.mediaAsset1.iaClass", "body"));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset1", "DUPLICATE_MEDIA", "Image is repeated in this row");
        });
    }

    @Test void unboxedInRowRepeatGetsOneIssueOnTheLaterSlot() throws Exception {   // the recorded no-box change: one issue, not two
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxRow("A", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset1", "one.png"));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset1", "DUPLICATE_MEDIA", "Image is repeated in this row");
        });
    }

    @Test void boundsComeFromTheStagedFileHeader() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            java.io.File fourByThree = root.resolve("four-by-three.png").toFile();
            ImageFixtures.write(ImageFixtures.noise(4, 3, 1), "png", fourByThree, false);
            JSONObject wide = storage.write("four.png", new java.io.FileInputStream(fourByThree), 100000);
            JSONArray files = new JSONArray().put(image).put(wide);
            assertTrue(validateRows(sh, storage, files, DETECT, boxed("A", "one.png", "0,0,2,2")).getBoolean("valid"));
            JSONObject report = validateRows(sh, storage, files, DETECT, boxed("A", "one.png", "0,0,3,2"));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset0.bbox", "INVALID_VALUE", "exceeds image 2x2");
            assertEquals("BOX_OUT_OF_BOUNDS", issues(report).get(0).getString("reason"));
            assertTrue(validateRows(sh, storage, files, DETECT, boxed("A", "four.png", "1,1,3,2")).getBoolean("valid"));
            report = validateRows(sh, storage, files, DETECT, boxed("A", "four.png", "1,1,4,2"));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset0.bbox", "INVALID_VALUE", "exceeds image 4x3");
        });
    }

    @Test void boxIdentityIgnoresWhitespaceAndThetaSpelling() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONArray files = new JSONArray().put(image);
            JSONObject report = validateRows(sh, storage, files, DETECT, boxed("A", "one.png", " 0, 0 ,1,1 ", "Encounter.mediaAsset0.theta", "0"), boxed("B", "one.png", "0,0,1,1", "Encounter.mediaAsset0.theta", "-0.0"));
            assertOnlyIssue(report, "B", "Encounter.mediaAsset0.bbox", "DUPLICATE_BOX", "row 0");
            assertTrue(validateRows(sh, storage, files, DETECT, boxed("A", "one.png", "0,0,1,1", "Encounter.mediaAsset0.theta", "0"), boxed("B", "one.png", "0,0,1,1", "Encounter.mediaAsset0.theta", "0.5")).getBoolean("valid"));
            report = validateRows(sh, storage, files, DETECT, boxed("A", "one.png", "0,0,1,1"), boxed("B", "one.png", "0,0,1,1", "Encounter.mediaAsset0.theta", "0"));
            assertOnlyIssue(report, "B", "Encounter.mediaAsset0.bbox", "DUPLICATE_BOX", "row 0");
        });
    }

    @Test void emptyBoxColumnsSurviveNormalizationWithoutBoxingTheDraft() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONArray files = new JSONArray().put(image);
            JSONObject report = validateRows(sh, storage, files, DETECT, boxRow("A", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.bbox", "", "Encounter.mediaAsset0.iaClass", "", "Encounter.mediaAsset0.theta", ""));
            assertTrue(report.getBoolean("valid"), report.toString());
            JSONObject normalized = report.getJSONArray("normalizedRows").getJSONObject(0).getJSONObject("fields");
            for (String key : new String[] { "Encounter.mediaAsset0.bbox", "Encounter.mediaAsset0.iaClass", "Encounter.mediaAsset0.theta" }) assertEquals("", normalized.getString(key), key);
            JSONObject again = validateRows(sh, storage, files, DETECT, new JSONObject().put("clientRowId", "A").put("fields", normalized));
            assertTrue(again.getBoolean("valid"), again.toString());
            assertEquals(SubmissionJson.canonical(normalized), SubmissionJson.canonical(again.getJSONArray("normalizedRows").getJSONObject(0).getJSONObject("fields")));
            report = validateRows(sh, storage, files, DETECT, boxRow("A", "Encounter.mediaAsset0", "one.png", "Encounter.quality0", ""));
            assertOnlyIssue(report, "A", "Encounter.quality0", "INVALID_VALUE", "decimal number");
            assertEquals("UNPARSEABLE", issues(report).get(0).getString("reason"));
        });
    }

    @Test void classWithoutBoxIsReportedOnTheClassField() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxRow("A", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.iaClass", "body"));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset0.iaClass", "INVALID_VALUE", "requires Encounter.mediaAsset0.bbox");
            assertEquals("REQUIRES_FIELD", issues(report).get(0).getString("reason"));
        });
    }

    @Test void thetaWithoutBoxIsRequiresField() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxRow("A", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.theta", "0.5"));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset0.theta", "INVALID_VALUE", "requires Encounter.mediaAsset0.bbox");
            assertEquals("REQUIRES_FIELD", issues(report).get(0).getString("reason"));
        });
    }

    @Test void missingAndUnmatchableClassesReportPlainReasons() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONArray files = new JSONArray().put(image);
            JSONObject report = validateRows(sh, storage, files, DETECT, boxRow("A", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.bbox", "0,0,1,1"));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset0.bbox", "INVALID_VALUE", "required when a box is supplied");
            assertEquals("MISSING_CLASS", issues(report).get(0).getString("reason"));
            report = validateRows(sh, storage, files, DETECT, boxRow("A", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.bbox", "0,0,1,1", "Encounter.mediaAsset0.iaClass", ""));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset0.iaClass", "INVALID_VALUE", "required when a box is supplied");
            assertEquals("MISSING_CLASS", issues(report).get(0).getString("reason"));
            report = validateRows(sh, storage, files, DETECT, boxed("A", "one.png", "0,0,1,1", "Encounter.mediaAsset0.iaClass", "fin"));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset0.iaClass", "INVALID_VALUE", "use one of: body");
            assertEquals("UNKNOWN_CLASS", issues(report).get(0).getString("reason"));
            assertTrue(issues(report).get(0).getString("message").startsWith("'fin' is not a matchable class for Manta birostris"), issues(report).get(0).toString());
        });
    }

    @Test void malformedValuesReportTheirReasons() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONArray files = new JSONArray().put(image);
            Object[][] cases = {
                { "Encounter.mediaAsset0.bbox", "1,2,3", "INVALID_BOX" }, { "Encounter.mediaAsset0.bbox", "a,b,c,d", "INVALID_BOX" },
                { "Encounter.mediaAsset0.bbox", "-1,0,5,5", "INVALID_BOX" }, { "Encounter.mediaAsset0.bbox", "0,0,0,5", "INVALID_BOX" },
                { "Encounter.mediaAsset0.viewpoint", "sideways", "INVALID_VIEWPOINT" },
                { "Encounter.mediaAsset0.theta", "9", "INVALID_THETA" }, { "Encounter.mediaAsset0.theta", "abc", "INVALID_THETA" },
                { "Encounter.mediaAsset0.iaClass", "x".repeat(65), "OUT_OF_RANGE" }, { "Encounter.mediaAsset0.bboxSource", "x".repeat(129), "OUT_OF_RANGE" },
                { "Encounter.quality0", "NaN", "OUT_OF_RANGE" }, { "Encounter.quality0", "abc", "UNPARSEABLE" },
            };
            for (Object[] c : cases) {
                JSONObject row = boxed("A", "one.png", "0,0,1,1", (String)c[0], c[1]);
                JSONObject report = validateRows(sh, storage, files, DETECT, row);
                JSONObject issue = issueOn(report, (String)c[0]);
                assertNotNull(issue, c[0] + "=" + c[1] + ": " + report);
                assertEquals("INVALID_VALUE", issue.getString("code"), issue.toString());
                assertEquals(c[2], issue.getString("reason"), issue.toString());
                assertFalse(issue.getString("message").contains("Exception") || issue.getString("message").contains("java."), issue.toString());
            }
        });
    }

    @Test void canonicalBboxSurvivesNormalizedRows() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONArray files = new JSONArray().put(image);
            JSONObject report = validateRows(sh, storage, files, DETECT, boxed("A", "one.png", " 0, 0 ,1,1 "));
            JSONObject normalized = report.getJSONArray("normalizedRows").getJSONObject(0).getJSONObject("fields");
            assertEquals("0,0,1,1", normalized.getString("Encounter.mediaAsset0.bbox"));
            JSONObject again = validateRows(sh, storage, files, DETECT, new JSONObject().put("clientRowId", "A").put("fields", normalized));
            assertEquals("0,0,1,1", again.getJSONArray("normalizedRows").getJSONObject(0).getJSONObject("fields").getString("Encounter.mediaAsset0.bbox"));
        });
    }

    // ---- Codex branch round 1 regressions ----
    @Test void missingClassMessageNamesTheClassField() throws Exception {   // accepted deviation: names .iaClass even on the .bbox issue
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxRow("A", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.bbox", "0,0,1,1"));
            assertEquals("Encounter.mediaAsset0.iaClass is required when a box is supplied", issues(report).get(0).getString("message"));
        });
    }

    @Test void classTextContainingTheDelimiterIsEchoedWhole() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONArray files = new JSONArray().put(image);
            for (String cls : new String[] { "a'; b", "'; valid classes for Evil species: x" }) {
                JSONObject issue = issueOn(validateRows(sh, storage, files, DETECT, boxed("A", "one.png", "0,0,1,1", "Encounter.mediaAsset0.iaClass", cls)), "Encounter.mediaAsset0.iaClass");
                assertNotNull(issue, cls);
                assertEquals("UNKNOWN_CLASS", issue.getString("reason"));
                assertTrue(issue.getString("message").endsWith("is not a matchable class for Manta birostris; use one of: body"), issue.toString());
                assertFalse(issue.getString("message").contains("Evil species: x;"), issue.toString());
            }
        });
    }

    @Test void speciesWithNoMatchableClassSaysSo() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject row = boxed("A", "one.png", "0,0,1,1"); row.getJSONObject("fields").put("Encounter.specificEpithet", "alfredi");
            JSONObject issue = issueOn(validateRows(sh, storage, new JSONArray().put(image), DETECT, row), "Encounter.mediaAsset0.iaClass");
            assertNotNull(issue);
            assertEquals("UNKNOWN_CLASS", issue.getString("reason"));
            assertEquals("'body' cannot be matched: Manta alfredi has no matchable classes", issue.getString("message"));
        });
    }

    @Test void severalBoxedSlotsOfOnePhotoPlusAnotherRow() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject a = boxRow("A", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.bbox", "0,0,1,1", "Encounter.mediaAsset0.iaClass", "body",
                "Encounter.mediaAsset1", "one.png", "Encounter.mediaAsset1.bbox", "1,1,1,1", "Encounter.mediaAsset1.iaClass", "body");
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, a, boxed("B", "one.png", "1,0,1,1"));
            assertOnlyIssue(report, "A", "Encounter.mediaAsset1", "DUPLICATE_MEDIA", "Image is repeated in this row");
        });
    }

    @Test void boxedMissingOrUnverifiedMediaGetsNoBoxIssue() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            // the row's only photo is missing, so today's "at least one image required" also applies; no box issue is added
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxed("A", "nope.png", "0,0,9,9"));
            java.util.List<String> missing = new java.util.ArrayList<>();
            for (JSONObject i : issues(report)) missing.add(i.getString("code"));
            assertEquals(java.util.List.of("MISSING_MEDIA", "REQUIRED_VALUE"), missing, report.toString());
            JSONObject bad = storage.write("bad.png", new java.io.ByteArrayInputStream(SubmissionFilesTest.png()), 10000);
            java.nio.file.Files.write(storage.path(bad), new byte[] { 1, 2, 3 }); // fails verification
            report = validateRows(sh, storage, new JSONArray().put(image).put(bad), DETECT, boxed("A", "bad.png", "0,0,9,9"));
            java.util.List<String> codes = new java.util.ArrayList<>();
            for (JSONObject i : issues(report)) codes.add(i.getString("code"));
            assertEquals(java.util.List.of("INVALID_MEDIA", "MISSING_MEDIA", "REQUIRED_VALUE"), codes, report.toString());
        });
    }

    @Test void boxWithoutAPhotoInItsSlotRequiresThePhoto() throws Exception {
        withBoxHarness((sh, storage, image) -> {
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, boxRow("A", "Encounter.mediaAsset0", "one.png",
                "Encounter.mediaAsset1.bbox", "0,0,1,1", "Encounter.mediaAsset1.iaClass", "body"));
            JSONObject issue = issueOn(report, "Encounter.mediaAsset1.bbox");
            assertNotNull(issue, report.toString());
            assertEquals("REQUIRES_FIELD", issue.getString("reason"));
            assertEquals("Encounter.mediaAsset1.bbox requires Encounter.mediaAsset1", issue.getString("message"));
        });
    }
}
