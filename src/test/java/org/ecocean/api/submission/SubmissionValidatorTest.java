package org.ecocean.api.submission;

import java.nio.file.Path;
import java.util.Properties;
import org.ecocean.*;
import org.ecocean.api.SiteSettings;
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

    /** A key never creates a User: every bulk column the shared importer can create an account from stays unsupported. */
    @Test void columnsThatCouldCreateAnAccountAreNotSupported() {
        for (String field : new String[] { "Encounter.submitterID",
                "Encounter.submitter0.emailAddress", "Encounter.submitter0.fullName", "Encounter.submitter0.affiliation",
                "Encounter.photographer0.emailAddress", "Encounter.photographer0.fullName", "Encounter.photographer0.affiliation",
                "Encounter.informOther0.emailAddress", "Encounter.informOther0.fullName", "Encounter.informOther0.affiliation",
                "Encounter.project0.ownerUsername", "Encounter.project0.projectIdPrefix", "Encounter.project0.researchProjectName" })
            assertFalse(SubmissionValidator.supported(field), field);
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
            // B repeats the box of A's excluded second slot: that slot must not take part in duplicate-box comparison
            JSONObject report = validateRows(sh, storage, new JSONArray().put(image), DETECT, a, boxed("B", "one.png", "1,1,1,1"));
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

    // ---- #1789: rows sharing Encounter.groupKey validate as one encounter ----
    static final String[] PHOTOS = { "one.png", "two.png", "three.png", "four.png", "five.png", "six.png", "seven.png", "eight.png",
        "Encounter.mediaAsset0.png" };

    interface GroupBody { void run(Shepherd sh, SubmissionFiles storage, JSONArray files, MockedStatic<CommonConfiguration> config) throws Exception; }

    /** As withBoxHarness (plus a second configured location, lagoon), with up to 10 photos per encounter and every PHOTOS name staged as a 20x20 image. */
    void withGroups(GroupBody body) throws Exception {
        try (MockedStatic<CommonConfiguration> config = mockStatic(CommonConfiguration.class);
             MockedStatic<LocationID> location = mockStatic(LocationID.class)) {
            config.when(() -> CommonConfiguration.getMaxMediaCountEncounter(any())).thenReturn(10);
            location.when(LocationID::getLocationIDStructure).thenReturn(new JSONObject("{\"locationID\":[{\"id\":\"reef\"},{\"id\":\"lagoon\"}]}"));
            location.when(() -> LocationID.isValidLocationID("reef")).thenReturn(true);
            location.when(() -> LocationID.isValidLocationID("lagoon")).thenReturn(true);
            TestIAJsonPropertiesUtil.override(boxFixture());
            try {
                Shepherd sh = mock(Shepherd.class); when(sh.isValidTaxonomyName(anyString())).thenReturn(true);
                SubmissionFiles storage = new SubmissionFiles(root);
                JSONArray files = new JSONArray();
                for (String name : PHOTOS) {
                    java.io.File source = root.resolve("source-" + name).toFile();
                    ImageFixtures.write(ImageFixtures.noise(20, 20, name.hashCode()), "png", source, false);
                    try (java.io.InputStream in = new java.io.FileInputStream(source)) { files.put(storage.write(name, in, 100000)); }
                }
                body.run(sh, storage, files, config);
            } finally {
                TestIAJsonPropertiesUtil.override(null);
            }
        }
    }

    /** A complete row (taxonomy, year, reef) in group key (null: no key). */
    static JSONObject full(String id, Object key, Object... kv) {
        JSONObject row = boxRow(id, kv);
        if (key != null) row.getJSONObject("fields").put("Encounter.groupKey", key);
        return row;
    }

    /** A continuation row: only the key and kv. */
    static JSONObject part(String id, Object key, Object... kv) {
        JSONObject fields = new JSONObject().put("Encounter.groupKey", key);
        for (int i = 0; i < kv.length; i += 2) fields.put((String)kv[i], kv[i + 1]);
        return new JSONObject().put("clientRowId", id).put("fields", fields);
    }

    /** An issue on ungrouped row r0 exactly as the validator emits it. */
    static JSONObject expectedIssue(String code, String reason, String message, String field) {
        JSONObject issue = new JSONObject().put("code", code).put("message", message).put("clientRowId", "r0").put("rowIndex", 0).put("field", field);
        if (reason != null) issue.put("reason", reason);
        return issue;
    }

    static JSONObject only(JSONObject report) {
        assertEquals(1, issues(report).size(), report.toString());
        return issues(report).get(0);
    }

    static void assertIssue(JSONObject issue, String clientRowId, String field, String code, String groupKey) {
        assertEquals(clientRowId, issue.getString("clientRowId"), issue.toString());
        assertEquals(field, issue.getString("field"), issue.toString());
        assertEquals(code, issue.getString("code"), issue.toString());
        assertEquals(groupKey, issue.optString("groupKey", null), issue.toString());
    }

    static JSONObject normalizedFields(JSONObject report, int row) {
        return report.getJSONArray("normalizedRows").getJSONObject(row).getJSONObject("fields");
    }

    @Test void groupKeyIsSupported() {
        assertTrue(SubmissionValidator.supported("Encounter.groupKey"));
        assertTrue(SubmissionValidator.FIELDS.contains("Encounter.groupKey"));
    }

    @Test void groupedRowsValidateOnceForTheGroup() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G1", "Encounter.mediaAsset0", "one.png"),
                part("r1", "g1", "Encounter.mediaAsset0", "two.png"));
            assertTrue(report.getBoolean("valid"), report.toString());
        });
    }

    @Test void normalizedRowsKeepSourceSpellings() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G1", "Encounter.mediaAsset0", "one.png"),
                part("r1", " g1 ", "Encounter.mediaAsset0", "two.png"));
            assertTrue(report.getBoolean("valid"), report.toString());
            assertEquals(2, report.getJSONArray("normalizedRows").length());
            assertEquals("r1", report.getJSONArray("normalizedRows").getJSONObject(1).getString("clientRowId"));
            assertEquals(java.util.Map.of("Encounter.mediaAsset0", "two.png", "Encounter.groupKey", "g1"), normalizedFields(report, 1).toMap());
            assertEquals("G1", normalizedFields(report, 0).getString("Encounter.groupKey"));
            assertEquals("reef", normalizedFields(report, 0).getString("Encounter.locationID"));
        });
    }

    @Test void locationOnOneGroupedRowSatisfiesGroup() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G1", "Encounter.mediaAsset0", "one.png"),
                part("r1", "g1", "Encounter.mediaAsset0", "two.png", "Encounter.locationID", "", "Encounter.sex", ""));
            assertTrue(report.getBoolean("valid"), report.toString());
            assertFalse(report.toString().contains("cannot be empty"), report.toString());
        });
    }

    @Test void missingLocationFansOutToEveryGroupedRow() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject r0 = full("r0", "G1", "Encounter.mediaAsset0", "one.png");
            r0.getJSONObject("fields").remove("Encounter.locationID");
            JSONObject report = validateRows(sh, storage, files, DETECT, r0, part("r1", "g1", "Encounter.mediaAsset0", "two.png"));
            assertEquals(2, issues(report).size(), report.toString());
            assertIssue(issues(report).get(0), "r0", "Encounter.locationID", "INVALID_LOCATION", "G1");
            assertIssue(issues(report).get(1), "r1", "Encounter.locationID", "INVALID_LOCATION", "g1");
            assertEquals(1, issues(report).get(1).getInt("rowIndex"));
        });
    }

    @Test void conflictReportedPerClientRow() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G1", "Encounter.mediaAsset0", "one.png", "Encounter.sex", "male"),
                part("r1", "g1", "Encounter.mediaAsset0", "two.png", "Encounter.sex", "female"));
            assertEquals(2, issues(report).size(), report.toString());
            assertIssue(issues(report).get(0), "r0", "Encounter.sex", "GROUP_CONFLICT", "G1");
            assertIssue(issues(report).get(1), "r1", "Encounter.sex", "GROUP_CONFLICT", "g1");
        });
    }

    @Test void sightingIdCheckedOnMergedRow() throws Exception {
        withGroups((sh, storage, files, config) -> {
            when(sh.getOccurrence("taken")).thenReturn(new Occurrence("taken"));
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G1", "Encounter.mediaAsset0", "one.png"),
                part("r1", "g1", "Encounter.mediaAsset0", "two.png", "Encounter.sightingID", "taken"));
            assertIssue(only(report), "r1", "Encounter.sightingID", "SIGHTING_EXISTS", "g1");
            verify(sh, times(1)).getOccurrence("taken");
        });
    }

    @Test void numericLocationBlamedInEitherOrder() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                part("r1", "G", "Encounter.mediaAsset0", "two.png", "Encounter.locationID", 123));
            assertIssue(only(report), "r1", "Encounter.locationID", "INVALID_LOCATION", "G");
            JSONObject first = full("r0", "G", "Encounter.mediaAsset0", "one.png", "Encounter.locationID", 123);
            report = validateRows(sh, storage, files, DETECT, first, part("r1", "G", "Encounter.mediaAsset0", "two.png", "Encounter.locationID", "reef"));
            assertIssue(only(report), "r0", "Encounter.locationID", "INVALID_LOCATION", "G");
        });
    }

    @Test void onlyLocationNumericIsReportedOncePerRow() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject first = full("r0", "G", "Encounter.mediaAsset0", "one.png", "Encounter.locationID", 7);
            JSONObject report = validateRows(sh, storage, files, DETECT, first, part("r1", "G", "Encounter.mediaAsset0", "two.png"));
            // r1 supplied no location: the group's only location failed on r0, so r1 is not blamed
            assertIssue(only(report), "r0", "Encounter.locationID", "INVALID_LOCATION", "G");
            report = validateRows(sh, storage, files, DETECT, part("r1", "G", "Encounter.mediaAsset0", "two.png"), first);
            assertIssue(only(report), "r0", "Encounter.locationID", "INVALID_LOCATION", "G");
        });
    }

    @Test void rejectedGroupedThetaBlamesOnlyItsOwnRow() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject bad = full("bad", "G", "Encounter.mediaAsset0", "two.png", "Encounter.mediaAsset0.bbox", "1,1,5,5",
                "Encounter.mediaAsset0.iaClass", "body", "Encounter.mediaAsset0.theta", "9");
            JSONObject good = full("good", "H", "Encounter.mediaAsset0", "two.png", "Encounter.mediaAsset0.bbox", "1,1,5,5",
                "Encounter.mediaAsset0.iaClass", "body");
            for (JSONObject[] order : new JSONObject[][] { { bad, good }, { good, bad } }) {
                JSONObject issue = only(validateRows(sh, storage, files, DETECT, order[0], order[1]));
                assertIssue(issue, "bad", "Encounter.mediaAsset0.theta", "INVALID_VALUE", "G");
                assertEquals("INVALID_THETA", issue.getString("reason"), issue.toString());
            }
        });
    }

    @Test void groupedUnconfiguredLocationIsInvalidLocation() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject ungrouped = only(validateRows(sh, storage, files, DETECT, full("u", null, "Encounter.mediaAsset0", "one.png", "Encounter.locationID", "nowhere")));
            assertIssue(ungrouped, "u", "Encounter.locationID", "INVALID_LOCATION", null);
            JSONObject grouped = only(validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png", "Encounter.locationID", "nowhere")));
            assertIssue(grouped, "r0", "Encounter.locationID", "INVALID_LOCATION", "G");
            assertEquals(ungrouped.getString("message"), grouped.getString("message"));
            assertEquals(ungrouped.has("reason"), grouped.has("reason"), grouped.toString());
        });
    }

    @Test void failedGroupedLocationBlamesOnlyItsOwnRow() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject first = full("r0", "G", "Encounter.mediaAsset0", "one.png", "Encounter.locationID", "nowhere");
            JSONObject report = validateRows(sh, storage, files, DETECT, first,
                part("r1", "G", "Encounter.mediaAsset0", "two.png", "Encounter.locationID", "reef"));
            assertIssue(only(report), "r0", "Encounter.locationID", "INVALID_LOCATION", "G");
        });
    }

    @Test void groupedLocationConflictIsGroupConflictOnly() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                part("r1", "G", "Encounter.mediaAsset0", "two.png", "Encounter.locationID", "lagoon"));
            assertEquals(2, issues(report).size(), report.toString());
            assertIssue(issues(report).get(0), "r0", "Encounter.locationID", "GROUP_CONFLICT", "G");
            assertIssue(issues(report).get(1), "r1", "Encounter.locationID", "GROUP_CONFLICT", "G");
        });
    }

    @Test void explicitEmptyUngroupedSightingIdStillRejected() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", null, "Encounter.mediaAsset0", "one.png", "Encounter.sightingID", ""),
                full("r1", "G", "Encounter.mediaAsset0", "two.png", "Encounter.sightingID", ""));
            JSONObject issue = only(report);
            assertIssue(issue, "r0", "Encounter.sightingID", "INVALID_VALUE", null);
            assertEquals("Use 1 to 100 letters, digits, '.', '_' or '-', starting with a letter or digit", issue.getString("message"));
        });
    }

    @Test void packedSlotDiagnosticsNameSourceColumns() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject issue = only(validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset7", "Encounter.mediaAsset0.png",
                "Encounter.mediaAsset7.iaClass", "body")));
            assertIssue(issue, "r0", "Encounter.mediaAsset7.iaClass", "INVALID_VALUE", "G");
            assertEquals("REQUIRES_FIELD", issue.getString("reason"), issue.toString());
            assertEquals("Encounter.mediaAsset7.iaClass requires Encounter.mediaAsset7.bbox", issue.getString("message"));
            issue = only(validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset7", "Encounter.mediaAsset0.png",
                "Encounter.mediaAsset7.bbox", "1,1,5,5")));
            assertIssue(issue, "r0", "Encounter.mediaAsset7.bbox", "INVALID_VALUE", "G");
            assertEquals("MISSING_CLASS", issue.getString("reason"), issue.toString());
            assertEquals("Encounter.mediaAsset7.iaClass is required when a box is supplied", issue.getString("message"));
        });
    }

    @Test void packedSlotsTranslatedOnce() throws Exception {
        withGroups((sh, storage, files, config) -> {
            // packed: r0 slots 0,7 -> 0,1; r1 slots 0,1,2 -> 2,3,4; r2 slots 0,1,3 -> 5,6,7
            JSONObject report = validateRows(sh, storage, files, DETECT,
                full("r0", "G", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset7", "two.png", "Encounter.mediaAsset7.bbox", "1,1,5,5"),
                part("r1", "G", "Encounter.mediaAsset0", "three.png", "Encounter.mediaAsset1", "four.png", "Encounter.mediaAsset2", "five.png"),
                part("r2", "G", "Encounter.mediaAsset0", "six.png", "Encounter.mediaAsset1", "seven.png", "Encounter.mediaAsset3", "eight.png"));
            JSONObject issue = only(report);
            assertIssue(issue, "r0", "Encounter.mediaAsset7.bbox", "INVALID_VALUE", "G");
            assertEquals("MISSING_CLASS", issue.getString("reason"));
            assertEquals("Encounter.mediaAsset7.iaClass is required when a box is supplied", issue.getString("message"));
        });
    }

    @Test void rejectedUngroupedSightingIdAbsentFromNormalized() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", null, "Encounter.mediaAsset0", "one.png", "Encounter.sightingID", 12));
            assertIssue(only(report), "r0", "Encounter.sightingID", "INVALID_VALUE", null);
            assertFalse(normalizedFields(report, 0).has("Encounter.sightingID"), report.toString());
        });
    }

    @Test void sparseContinuationMissingClassNamesSourceSlot() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject issue = only(validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                part("r1", "G", "Encounter.mediaAsset7", "two.png", "Encounter.mediaAsset7.bbox", "1,1,5,5")));
            assertIssue(issue, "r1", "Encounter.mediaAsset7.bbox", "INVALID_VALUE", "G");
            assertEquals("MISSING_CLASS", issue.getString("reason"));
            assertTrue(issue.getString("message").contains("Encounter.mediaAsset7.iaClass"), issue.toString());
            assertFalse(issue.getString("message").contains("mediaAsset1"), issue.toString());
        });
    }

    @Test void numericSightingIdBlamedInEitherOrder() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png", "Encounter.sightingID", "123"),
                part("r1", "G", "Encounter.mediaAsset0", "two.png", "Encounter.sightingID", 123));
            assertIssue(only(report), "r1", "Encounter.sightingID", "INVALID_VALUE", "G");
            report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png", "Encounter.sightingID", 123),
                part("r1", "G", "Encounter.mediaAsset0", "two.png", "Encounter.sightingID", "123"));
            assertIssue(only(report), "r0", "Encounter.sightingID", "INVALID_VALUE", "G");
        });
    }

    @Test void interleavedCrossGroupRepeatBlamesLaterSource() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                full("r1", "H", "Encounter.mediaAsset0", "two.png"), part("r2", "g", "Encounter.mediaAsset0", "two.png"));
            assertIssue(only(report), "r2", "Encounter.mediaAsset0", "DUPLICATE_MEDIA", "g");
        });
    }

    @Test void distinctBoxesSameFileAcrossGroupsAllowed() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                full("r1", "H", "Encounter.mediaAsset0", "two.png", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", "body"),
                part("r2", "g", "Encounter.mediaAsset0", "two.png", "Encounter.mediaAsset0.bbox", "10,10,5,5", "Encounter.mediaAsset0.iaClass", "body"));
            assertTrue(report.getBoolean("valid"), report.toString());
            report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                full("r1", "H", "Encounter.mediaAsset0", "two.png", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", "body"),
                part("r2", "g", "Encounter.mediaAsset0", "two.png", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", "body"));
            JSONObject issue = only(report);
            assertIssue(issue, "r2", "Encounter.mediaAsset0.bbox", "DUPLICATE_BOX", "g");
            assertEquals("Identical box already on row 1", issue.getString("message"));
        });
    }

    @Test void groupedBoxOutOfBoundsNamesSourceColumn() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject issue = only(validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                part("r1", "G", "Encounter.mediaAsset3", "two.png", "Encounter.mediaAsset3.bbox", "10,10,15,5", "Encounter.mediaAsset3.iaClass", "body")));
            assertIssue(issue, "r1", "Encounter.mediaAsset3.bbox", "INVALID_VALUE", "G");
            assertEquals("BOX_OUT_OF_BOUNDS", issue.getString("reason"));
            assertEquals("box 10,10,15,5 exceeds image 20x20", issue.getString("message"));
        });
    }

    @Test void explicitEncounterIdStillUnsupported() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject issue = only(validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png",
                "Encounter.id", "0b9e3a4e-7f1c-4d2a-9c55-1f0e6c9a2b10")));
            assertIssue(issue, "r0", "Encounter.id", "UNSUPPORTED_FIELD", "G");
        });
    }

    @Test void currentYearLaterMonthBlamesMonthRow() throws Exception {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 3, 10);
        java.time.Clock clock = java.time.Clock.fixed(java.time.Instant.parse("2026-03-10T00:00:00Z"), java.time.ZoneOffset.UTC);
        assertEquals(today, clock.instant().atOffset(Util.LATEST_CIVIL_OFFSET).toLocalDate());
        withGroups((sh, storage, files, config) -> {
            try (MockedStatic<Util> util = mockStatic(Util.class, CALLS_REAL_METHODS)) {
                util.when(() -> Util.dateIsInFuture(any(), any(), any())).thenAnswer(a -> Util.dateIsInFuture(
                    a.getArgument(0), a.getArgument(1), a.getArgument(2), today));
                JSONObject r0 = full("r0", "G", "Encounter.mediaAsset0", "one.png", "Encounter.month", "");
                r0.getJSONObject("fields").put("Encounter.year", 2026);
                JSONObject report = new SubmissionValidator(clock).validate(boxDraft(DETECT, files, r0,
                    part("r1", "G", "Encounter.mediaAsset0", "two.png", "Encounter.month", 11)), sh, storage);
                JSONObject issue = only(report);
                assertIssue(issue, "r1", "Encounter.month", "INVALID_VALUE", "G");
                assertEquals("FUTURE_DATE", issue.getString("reason"));
                assertEquals("2026-11 is later than today's date in every time zone", issue.getString("message"));
            }
        });
    }

    @Test void blankGroupKeyOnUngroupedRowIsSilent() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "", "Encounter.mediaAsset0", "one.png"),
                full("r1", "  ", "Encounter.mediaAsset0", "two.png"));
            assertTrue(report.getBoolean("valid"), report.toString());
            assertFalse(normalizedFields(report, 0).has("Encounter.groupKey"), report.toString());
            assertFalse(normalizedFields(report, 1).has("Encounter.groupKey"), report.toString());
        });
    }

    @Test void blankExtraSlotIsSilent() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                part("r1", "G", "Encounter.mediaAsset0", "two.png", "Encounter.mediaAsset1", "", "Encounter.mediaAsset2", "  "));
            assertTrue(report.getBoolean("valid"), report.toString());
        });
    }

    @Test void repeatedPhotoInGroupNamesLaterRowOnce() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                part("r1", "G", "Encounter.mediaAsset0", "two.png"), part("r2", "G", "Encounter.mediaAsset0", "two.png"));
            assertIssue(only(report), "r2", "Encounter.mediaAsset0", "DUPLICATE_MEDIA", "G");
        });
    }

    @Test void repeatedPhotoSameGroupedRowOnce() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset1", "one.png"));
            assertIssue(only(report), "r0", "Encounter.mediaAsset1", "DUPLICATE_MEDIA", "G");
        });
    }

    @Test void repeatedBoxedPhotoInGroupOnce() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT,
                full("r0", "G", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.bbox", "1,1,5,5", "Encounter.mediaAsset0.iaClass", "body"),
                part("r1", "G", "Encounter.mediaAsset0", "one.png", "Encounter.mediaAsset0.bbox", "2,2,5,5", "Encounter.mediaAsset0.iaClass", "body"));
            assertIssue(only(report), "r1", "Encounter.mediaAsset0", "DUPLICATE_MEDIA", "G");
        });
    }

    @Test void repeatedPhotoAcrossGroupsOnce() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G1", "Encounter.mediaAsset0", "one.png"),
                full("r1", "G2", "Encounter.mediaAsset0", "one.png"));
            JSONObject issue = only(report);
            assertIssue(issue, "r1", "Encounter.mediaAsset0", "DUPLICATE_MEDIA", "G2");
            assertEquals("Use each image in one row only", issue.getString("message"));
        });
    }

    @Test void rowWithoutPhotoInGroupIsRequired() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject issue = only(validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                part("r1", "G", "Encounter.sex", "male")));
            assertIssue(issue, "r1", "Encounter.mediaAsset0", "REQUIRED_VALUE", "G");
            assertEquals("At least one image required", issue.getString("message"));
        });
    }

    @Test void groupPhotoCapIsMaxMediaPerEncounter() throws Exception {
        withGroups((sh, storage, files, config) -> {
            config.when(() -> CommonConfiguration.getMaxMediaCountEncounter(any())).thenReturn(2);
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", "G", "Encounter.mediaAsset0", "one.png"),
                part("r1", "G", "Encounter.mediaAsset0", "two.png"), part("r2", "G", "Encounter.mediaAsset0", "three.png"));
            assertIssue(only(report), "r2", "Encounter.mediaAsset0", "GROUP_CONFLICT", "G");
        });
    }

    @Test void nonStringGroupKeyRejected() throws Exception {
        withGroups((sh, storage, files, config) -> {
            JSONObject issue = only(validateRows(sh, storage, files, DETECT, full("r0", 7, "Encounter.mediaAsset0", "one.png")));
            assertIssue(issue, "r0", "Encounter.groupKey", "INVALID_VALUE", "7");
        });
    }

    @Test void checkReturnsGroupedRowsOnlyWhenValid() throws Exception {
        withGroups((sh, storage, files, config) -> {
            SubmissionValidator.Checked ok = new SubmissionValidator().check(boxDraft(DETECT, files,
                full("r0", "G1", "Encounter.mediaAsset0", "one.png"), part("r1", "g1", "Encounter.mediaAsset0", "two.png")), sh, storage);
            assertTrue(ok.report.getBoolean("valid"), ok.report.toString());
            assertNotNull(ok.rows);
            assertEquals(2, ok.rows.sourceRowCount());
            assertTrue(ok.rows.isGroupedRow(0) && ok.rows.isGroupedRow(1));
            assertEquals(ok.rows.mergedIndexOf(0), ok.rows.mergedIndexOf(1));
            SubmissionValidator.Checked bad = new SubmissionValidator().check(boxDraft(DETECT, files,
                full("r0", "G1", "Encounter.mediaAsset0", "one.png", "Encounter.sex", "male"),
                part("r1", "g1", "Encounter.mediaAsset0", "two.png", "Encounter.sex", "female")), sh, storage);
            assertFalse(bad.report.getBoolean("valid"));
            assertNull(bad.rows);
        });
    }

    @Test void ungroupedIssuesUnchanged() throws Exception {   // (regression) order, codes, messages, no groupKey
        withGroups((sh, storage, files, config) -> {
            JSONObject report = validateRows(sh, storage, files, DETECT, full("r0", null, "Encounter.mediaAsset0", "missing.png",
                "Encounter.id", "x", "Encounter.sightingID", "bad id", "Encounter.locationID", "nowhere", "Encounter.sex", "x"));
            // the complete issue objects, in order: code, reason, message, field, row and no groupKey
            JSONArray expected = new JSONArray()
                .put(expectedIssue("UNSUPPORTED_FIELD", null, "Field is not supported by this pilot", "Encounter.id"))
                .put(expectedIssue("MISSING_MEDIA", null, "Reference must exactly match a completed image filename", "Encounter.mediaAsset0"))
                .put(expectedIssue("INVALID_VALUE", "INVALID", "Use 1 to 100 letters, digits, '.', '_' or '-', starting with a letter or digit", "Encounter.sightingID"))
                .put(expectedIssue("REQUIRED_VALUE", null, "At least one image required", "Encounter.mediaAsset0"))
                .put(expectedIssue("INVALID_LOCATION", null, "A configured location ID is required", "Encounter.locationID"))
                .put(expectedIssue("INVALID_VALUE", "NOT_CONFIGURED", "'x' is not a configured sex value; use one of: " + String.join(", ", SiteSettings.VALUES_SEX), "Encounter.sex"));
            JSONArray actual = report.getJSONArray("errors");
            assertTrue(expected.similar(actual), "expected " + expected + " but was " + actual);
        });
    }
}
