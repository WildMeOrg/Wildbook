package org.ecocean.api.submission;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionJsonTest {
    @Test void defaultAndExplicitProcessingHaveSameCanonicalHash() {
        JSONObject request = new JSONObject("{\"contractVersion\":\"1\",\"source\":{\"name\":\"test\"}}");
        String defaulted = SubmissionJson.canonical(SubmissionJson.create(request));
        request.put("processing", new JSONObject().put("mode", "detect-and-identify"));
        assertEquals(defaulted, SubmissionJson.canonical(SubmissionJson.create(request)));
        request.put("ownerId", "arbitrary");
        assertThrows(SubmissionException.class, () -> SubmissionJson.create(request));
    }
    @Test void explicitImportOnlyIsAnOptOutAndUnknownModeIsRejected() {
        JSONObject input = new JSONObject("{\"contractVersion\":\"1\",\"source\":{\"name\":\"test\"}}");
        input.put("processing", new JSONObject().put("mode", "import-only"));
        assertEquals("import-only", SubmissionJson.create(input).getJSONObject("processing").getString("mode"));
        input.getJSONObject("processing").put("mode", "detect");
        assertEquals(422, assertThrows(SubmissionException.class, () -> SubmissionJson.create(input)).status);
    }
    @Test void duplicateRowIdsAndNullValuesAreRejected() {
        JSONObject row = new JSONObject().put("clientRowId", "one").put("fields", new JSONObject().put("Encounter.year", 2026));
        JSONObject input = new JSONObject().put("rows", new JSONArray().put(row).put(row));
        assertEquals("DUPLICATE_CLIENT_ROW_ID", assertThrows(SubmissionException.class, () -> SubmissionJson.rows(input)).code);
        row.getJSONObject("fields").put("Encounter.year", JSONObject.NULL);
        assertEquals(400, assertThrows(SubmissionException.class, () -> SubmissionJson.rows(input)).status);
    }
    @Test void canonicalizationSortsObjectsButPreservesRowOrder() {
        assertEquals(SubmissionJson.canonical(new JSONObject("{\"a\":1,\"b\":2}")), SubmissionJson.canonical(new JSONObject("{\"b\":2,\"a\":1}")));
        assertNotEquals(SubmissionJson.hash("[1,2]"), SubmissionJson.hash("[2,1]"));
    }
    @Test void parserRejectsLenientJsonTrailingValuesDuplicateKeysAndInvalidUtf8() {
        for (String json : new String[]{"{a:1}", "{'a':1}", "{} {}", "{\"a\":1,\"a\":2}", "[1]", "{\"a\":\"\\u0000\"}", "{\"a\":\"\\ud800\"}", "{\"\\udc00\":1}",
            "{\"a\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}"})
            assertEquals(400, assertThrows(SubmissionException.class,
                () -> SubmissionJson.parse(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))).status);
        assertThrows(SubmissionException.class, () -> SubmissionJson.parse(new byte[]{'{', '"', (byte)0xC3, '"', ':', '1', '}'}));
        assertEquals(1, SubmissionJson.parse("{\"a\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8)).getInt("a"));
    }
}
