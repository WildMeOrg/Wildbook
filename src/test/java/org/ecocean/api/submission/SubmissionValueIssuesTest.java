package org.ecocean.api.submission;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import org.ecocean.*;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.submission.Submission;
import org.json.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the real legacy field validators, so a legacy wording change fails here instead of degrading reasons. */
class SubmissionValueIssuesTest {
    @TempDir Path root;

    private static final class Case {
        final String id, field, reason, messagePart; final JSONObject changes; final String[] drop;
        Case(String id, JSONObject changes, String field, String reason, String messagePart, String... drop) {
            this.id = id; this.changes = changes; this.field = field; this.reason = reason; this.messagePart = messagePart; this.drop = drop;
        }
    }

    private static JSONObject set(Object... pairs) {
        JSONObject value = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) value.put((String)pairs[i], pairs[i + 1]);
        return value;
    }

    private static String ymd(LocalDate date) {
        return String.format("%04d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
    }

    @Test void legacyRejectionsReportSpecificReasonMessageAndField() throws Exception {
        List<Case> cases = List.of(
            new Case("year-missing", set(), "Encounter.year", "REQUIRED", "Encounter.year is required", "Encounter.year", "Encounter.month", "Encounter.day"),
            new Case("genus-missing", set(), "Encounter.genus", "REQUIRED", "Encounter.genus is required", "Encounter.genus"),
            new Case("year-999", set("Encounter.year", 999), "Encounter.year", "OUT_OF_RANGE", "1000 or later"),
            new Case("year-text", set("Encounter.year", "abc"), "Encounter.year", "UNPARSEABLE", "whole number"),
            new Case("year-fraction", set("Encounter.year", 2017.5), "Encounter.year", "UNPARSEABLE", "whole number"),
            new Case("month-13", set("Encounter.month", 13), "Encounter.month", "OUT_OF_RANGE", "1 through 12"),
            new Case("day-32", set("Encounter.day", 32), "Encounter.day", "OUT_OF_RANGE", "1 through 31"),
            new Case("feb-29-2025", set("Encounter.year", 2025, "Encounter.month", 2, "Encounter.day", 29), "Encounter.day", "OUT_OF_RANGE", "2025-02 has no day 29"),
            new Case("day-without-month", set(), "Encounter.month", "REQUIRES_FIELD", "required when Encounter.day", "Encounter.month"),
            new Case("hour-24", set("Encounter.hour", 24), "Encounter.hour", "OUT_OF_RANGE", "0 through 23"),
            new Case("minutes-60", set("Encounter.hour", 10, "Encounter.minutes", 60), "Encounter.minutes", "OUT_OF_RANGE", "0 through 59"),
            new Case("latitude-91", set("Encounter.decimalLatitude", 91, "Encounter.decimalLongitude", 36.9), "Encounter.decimalLatitude", "OUT_OF_RANGE", "-90 and 90"),
            new Case("latitude-nan", set("Encounter.decimalLatitude", "NaN", "Encounter.decimalLongitude", 36.9), "Encounter.decimalLatitude", "OUT_OF_RANGE", "-90 and 90"),
            new Case("longitude-181", set("Encounter.decimalLatitude", 0.29, "Encounter.decimalLongitude", 181), "Encounter.decimalLongitude", "OUT_OF_RANGE", "-180 and 180"),
            new Case("latitude-text", set("Encounter.decimalLatitude", "abc", "Encounter.decimalLongitude", 36.9), "Encounter.decimalLatitude", "UNPARSEABLE", "decimal number"),
            new Case("latitude-alone", set("Encounter.decimalLatitude", 0.29), "Encounter.decimalLongitude", "REQUIRES_FIELD", "supplied together"),
            new Case("taxonomy", set("Encounter.specificEpithet", "quagga"), "Encounter.genus", "NOT_CONFIGURED", "'Equus quagga' is not a configured taxonomy"),
            new Case("sex", set("Encounter.sex", "F"), "Encounter.sex", "NOT_CONFIGURED", "'F' is not a configured sex value; use one of: unknown, male, female"),
            new Case("life-stage", set("Encounter.lifeStage", "juvenile"), "Encounter.lifeStage", "NOT_CONFIGURED", "'juvenile' is not a configured lifeStage value"),
            new Case("living-status", set("Encounter.livingStatus", "zombie"), "Encounter.livingStatus", "NOT_CONFIGURED", "'zombie' is not a configured livingStatus value"),
            new Case("future-year", set("Encounter.year", 2027, "Encounter.month", 1, "Encounter.day", 1), "Encounter.year", "FUTURE_DATE", "2027 is later than today's date in every time zone"),
            new Case("future-year-string", set("Encounter.year", "2027", "Encounter.month", 1, "Encounter.day", 1), "Encounter.year", "FUTURE_DATE", "2027 is later"),
            new Case("future-year-only", set("Encounter.year", 2027), "Encounter.year", "FUTURE_DATE", "2027 is later", "Encounter.month", "Encounter.day"),
            new Case("future-year-bad-month", set("Encounter.year", 2027, "Encounter.month", 13), "Encounter.year", "FUTURE_DATE", "2027 is later", "Encounter.day"),
            new Case("future-month", set("Encounter.year", 2026, "Encounter.month", 10), "Encounter.month", "FUTURE_DATE", "2026-10 is later", "Encounter.day"),
            new Case("future-day", set("Encounter.year", 2026, "Encounter.month", 9, "Encounter.day", 26), "Encounter.day", "FUTURE_DATE", "2026-09-26 is later"),
            new Case("future-nonexistent-day", set("Encounter.year", 2026, "Encounter.month", 9, "Encounter.day", 31), "Encounter.day", "FUTURE_DATE", "2026-09-31 is later"),
            new Case("past-day-this-month", set("Encounter.year", 2026, "Encounter.month", 9, "Encounter.day", 24), null, null, null),
            new Case("legacy-only-location", set("Encounter.locationID", "retired"), "Encounter.locationID", "NOT_CONFIGURED", "'retired' is not a configured location ID"),
            new Case("today", set("Encounter.year", 2026, "Encounter.month", 9, "Encounter.day", 25), null, null, null));
        JSONArray errors = validate(cases).getJSONArray("errors");
        String all = errors.toString();
        assertFalse(all.contains("java."), all);
        assertFalse(all.contains("Value failed bulk-import validation"), all);
        for (Case c : cases) {
            List<JSONObject> row = new ArrayList<>();
            for (int i = 0; i < errors.length(); i++) if (c.id.equals(errors.getJSONObject(i).optString("clientRowId"))) row.add(errors.getJSONObject(i));
            if (c.field == null) { assertTrue(row.isEmpty(), c.id + " " + row); continue; }
            JSONObject match = null;
            for (JSONObject issue : row) if (c.field.equals(issue.optString("field")) && c.reason.equals(issue.optString("reason"))) match = issue;
            assertNotNull(match, c.id + " expected " + c.field + "/" + c.reason + " in " + row);
            assertEquals("INVALID_VALUE", match.getString("code"), c.id);
            assertTrue(match.getString("message").contains(c.messagePart), c.id + ": " + match);
            if ("FUTURE_DATE".equals(c.reason)) // exactly one future-date issue, on the component that is future
                assertEquals(1, row.stream().filter(e -> "FUTURE_DATE".equals(e.optString("reason"))).count(), c.id + " " + row);
        }
        List<JSONObject> badMonth = new ArrayList<>();
        for (int i = 0; i < errors.length(); i++) if ("future-year-bad-month".equals(errors.getJSONObject(i).optString("clientRowId"))) badMonth.add(errors.getJSONObject(i));
        assertTrue(badMonth.toString().contains("\"reason\":\"OUT_OF_RANGE\""), "invalid month still reported alongside future year: " + badMonth);
        long nonexistentDay = issuesFor(errors, "future-nonexistent-day").stream()
            .filter(e -> "Encounter.day".equals(e.optString("field")) && "OUT_OF_RANGE".equals(e.optString("reason"))
                && e.getString("message").equals("2026-09 has no day 31")).count();
        assertEquals(1, nonexistentDay, "nonexistent day still reported alongside future date");
    }

    @Test void unconfiguredLocationIsReportedOnce() throws Exception {
        JSONArray errors = validate(List.of(new Case("location", set("Encounter.locationID", "Mpala"), null, null, null))).getJSONArray("errors");
        int location = 0;
        for (int i = 0; i < errors.length(); i++) if ("Encounter.locationID".equals(errors.getJSONObject(i).optString("field"))) location++;
        assertEquals(1, location, errors.toString());
        assertEquals("INVALID_LOCATION", errors.getJSONObject(0).getString("code"));
    }

    private static List<JSONObject> issuesFor(JSONArray errors, String id) {
        List<JSONObject> row = new ArrayList<>();
        for (int i = 0; i < errors.length(); i++) if (id.equals(errors.getJSONObject(i).optString("clientRowId"))) row.add(errors.getJSONObject(i));
        return row;
    }

    @Test void longAllowedValueListsAreNotEchoed() throws Exception {
        List<String> longValues = new ArrayList<>();
        for (int i = 0; i < 10; i++) longValues.add("stage-" + i + "-" + "x".repeat(60));
        JSONArray errors = validate(List.of(new Case("life-stage", set("Encounter.lifeStage", "juvenile"), null, null, null)), longValues)
            .getJSONArray("errors");
        String message = errors.getJSONObject(0).getString("message");
        assertTrue(message.endsWith("see /api/v3/site-settings for configured values"), message);
        assertTrue(message.length() < 200, message);
    }

    @Test void unmappedMessagesFallBackWithoutJavaText() {
        assertEquals("error parsing long", SubmissionValueIssues.sanitize("error parsing long: java.lang.NumberFormatException: For input string: \"x\""));
        assertEquals("Value is invalid", SubmissionValueIssues.sanitize("java.lang.IllegalStateException"));
        SubmissionValueIssues.Issue issue = SubmissionValueIssues.explain("Encounter.behavior", new IllegalStateException("something new"),
            new JSONObject(), Collections.emptyMap(), mock(Shepherd.class), LocalDate.now());
        assertEquals("INVALID", issue.reason);
        assertEquals("something new", issue.message);
        assertEquals("Encounter.behavior", issue.field);
    }

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 25);
    private static final java.time.Clock CLOCK = java.time.Clock.fixed(java.time.Instant.parse("2026-09-24T12:00:00Z"), java.time.ZoneOffset.UTC);

    private JSONObject validate(List<Case> cases) throws Exception { return validate(cases, List.of()); }

    /** Validates one draft of cases with "today" fixed for both the report and legacy validation, and checks
     *  the report invariants: exactly one issue per legacy rejection (except the duplicate location one), and
     *  normalized rows holding exactly the values legacy validation accepted. */
    private JSONObject validate(List<Case> cases, List<String> lifeStages) throws Exception {
        assertEquals(TODAY, CLOCK.instant().atOffset(Util.LATEST_CIVIL_OFFSET).toLocalDate());
        try (MockedStatic<CommonConfiguration> config = mockStatic(CommonConfiguration.class);
            MockedStatic<LocationID> location = mockStatic(LocationID.class);
            MockedStatic<Util> util = mockStatic(Util.class, CALLS_REAL_METHODS)) {
            util.when(() -> Util.dateIsInFuture(any(), any(), any())).thenAnswer(a -> Util.dateIsInFuture(
                a.getArgument(0), a.getArgument(1), a.getArgument(2), TODAY));
            config.when(() -> CommonConfiguration.getMaxMediaCountEncounter(any())).thenReturn(10);
            config.when(() -> CommonConfiguration.getIndexedPropertyValues(eq("lifeStage"), nullable(String.class))).thenReturn(lifeStages);
            // "retired" is in the submissions location tree but rejected by the legacy location check
            location.when(LocationID::getLocationIDStructure).thenReturn(new JSONObject("{\"locationID\":[{\"id\":\"reef\"},{\"id\":\"retired\"}]}"));
            location.when(() -> LocationID.isValidLocationID("reef")).thenReturn(true);
            Shepherd sh = mock(Shepherd.class);
            when(sh.isValidTaxonomyName(anyString())).thenAnswer(a -> "Equus grevyi".equals(a.getArgument(0)));
            SubmissionFiles storage = new SubmissionFiles(root);
            JSONArray files = new JSONArray(), rows = new JSONArray();
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i);
                files.put(storage.write("img" + i + ".png", new java.io.ByteArrayInputStream(SubmissionFilesTest.png()), 10000));
                JSONObject fields = set("Encounter.genus", "Equus", "Encounter.specificEpithet", "grevyi", "Encounter.year", 2017,
                    "Encounter.month", 4, "Encounter.day", 25, "Encounter.locationID", "reef", "Encounter.mediaAsset0", "img" + i + ".png");
                for (String field : c.drop) fields.remove(field);
                for (String key : c.changes.keySet()) fields.put(key, c.changes.get(key));
                rows.put(new JSONObject().put("clientRowId", c.id).put("fields", fields));
            }
            Submission draft = new Submission("id", "context0", "owner", "hash", "hash", "{}", 0, Long.MAX_VALUE);
            draft.setFiles(files.toString());
            draft.replaceRows(rows.toString());
            JSONObject report = new SubmissionValidator(CLOCK).validate(draft, sh, storage);
            JSONArray errors = report.getJSONArray("errors");
            assertEquals(errors.length() == 0, report.getBoolean("valid"));
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i), fields = row.getJSONObject("fields");
                Map<String, Object> legacy = org.ecocean.api.bulk.BulkImportUtil.validateRow(new JSONObject(fields.toString()), sh);
                JSONObject accepted = new JSONObject();
                int rejected = 0;
                for (Map.Entry<String, Object> entry : legacy.entrySet()) {
                    if (entry.getValue() instanceof Exception) {
                        if (!("Encounter.locationID".equals(entry.getKey()) && "Mpala".equals(fields.opt("Encounter.locationID")))) rejected++;
                    } else accepted.put(entry.getKey(), ((org.ecocean.api.bulk.BulkValidator)entry.getValue()).getValue());
                }
                long reported = issuesFor(errors, row.getString("clientRowId")).stream().filter(e -> "INVALID_VALUE".equals(e.getString("code"))).count();
                assertEquals(rejected, reported, row.getString("clientRowId") + " " + errors);
                assertEquals(SubmissionJson.canonical(accepted),
                    SubmissionJson.canonical(report.getJSONArray("normalizedRows").getJSONObject(i).getJSONObject("fields")), row.getString("clientRowId"));
            }
            return report;
        }
    }
}
