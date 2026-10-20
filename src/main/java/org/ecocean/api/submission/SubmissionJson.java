package org.ecocean.api.submission;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONObject;

public class SubmissionJson {
    private static final com.fasterxml.jackson.core.JsonFactory JSON = com.fasterxml.jackson.core.JsonFactory.builder()
        .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder()
            .maxNestingDepth(32).maxStringLength(SubmissionPolicy.MAX_BODY_BYTES).maxNumberLength(128).build())
        .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    public static JSONObject parse(byte[] bytes) {
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            try (com.fasterxml.jackson.core.JsonParser parser = JSON.createParser(value)) {
                if (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.START_OBJECT)
                    throw new SubmissionException(400, "BAD_REQUEST", "JSON object required");
                int depth = 1;
                while (depth > 0) {
                    com.fasterxml.jackson.core.JsonToken token = parser.nextToken();
                    if (token == null) throw new SubmissionException(400, "BAD_REQUEST", "Incomplete JSON");
                    if (token == com.fasterxml.jackson.core.JsonToken.FIELD_NAME || token == com.fasterxml.jackson.core.JsonToken.VALUE_STRING)
                        checkText(parser.getText());
                    if (token.isStructStart()) depth++;
                    if (token.isStructEnd()) depth--;
                }
                if (parser.nextToken() != null) throw new SubmissionException(400, "BAD_REQUEST", "Trailing JSON content");
            }
            return new JSONObject(value);
        } catch (java.io.IOException | org.json.JSONException ex) {
            throw new SubmissionException(400, "BAD_REQUEST", "Invalid UTF-8 JSON object or nesting limit exceeded");
        }
    }
    private static void checkText(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == 0 || Character.isLowSurrogate(c))
                throw new SubmissionException(400, "BAD_REQUEST", "Invalid Unicode text");
            if (Character.isHighSurrogate(c) && (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))))
                throw new SubmissionException(400, "BAD_REQUEST", "Invalid Unicode text");
        }
    }
    public static String canonical(Object value) {
        if (value instanceof JSONObject) {
            JSONObject obj = (JSONObject)value;
            java.util.List<String> entries = new java.util.ArrayList<>();
            for (String key : new TreeSet<>(obj.keySet())) entries.add(JSONObject.quote(key) + ":" + canonical(obj.get(key)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray)value;
            java.util.List<String> values = new java.util.ArrayList<>();
            for (int i = 0; i < arr.length(); i++) values.add(canonical(arr.get(i)));
            return "[" + String.join(",", values) + "]";
        }
        return JSONObject.valueToString(value);
    }
    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : digest) result.append(String.format("%02x", b & 255));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public static void keys(JSONObject value, String... names) {
        Set<String> allowed = Set.of(names);
        for (String key : value.keySet()) if (!allowed.contains(key))
            throw new SubmissionException(400, "BAD_REQUEST", "Unknown property: " + key);
    }
    public static String requiredString(JSONObject value, String key, int max) {
        Object raw = value.opt(key);
        if (!(raw instanceof String) || ((String)raw).isEmpty() || ((String)raw).length() > max)
            throw new SubmissionException(400, "BAD_REQUEST", "Invalid " + key);
        return (String)raw;
    }
    public static JSONObject create(JSONObject value) { return create(value, "detect-and-identify"); }
    public static JSONObject create(JSONObject value, String defaultMode) {
        keys(value, "contractVersion", "source", "processing");
        if (!"1".equals(value.opt("contractVersion"))) throw new SubmissionException(400, "BAD_REQUEST", "Unsupported contract version");
        JSONObject source = value.optJSONObject("source");
        if (source == null) throw new SubmissionException(400, "BAD_REQUEST", "source is required");
        keys(source, "name", "batchId");
        requiredString(source, "name", 128);
        if (source.has("batchId")) requiredString(source, "batchId", 256);
        JSONObject processing = value.has("processing") ? value.optJSONObject("processing") : new JSONObject().put("mode", defaultMode);
        if (processing == null) throw new SubmissionException(400, "BAD_REQUEST", "Invalid processing");
        keys(processing, "mode");
        if (!"import-only".equals(processing.opt("mode")) && !"detect-and-identify".equals(processing.opt("mode")))
            throw new SubmissionException(422, "CAPABILITY_UNAVAILABLE", "Use detect-and-identify or import-only");
        return new JSONObject().put("contractVersion", "1").put("source", new JSONObject(source.toString()))
            .put("processing", processing);
    }
    public static JSONArray rows(JSONObject value) {
        keys(value, "rows");
        JSONArray rows = value.optJSONArray("rows");
        if (rows == null || rows.length() == 0) throw new SubmissionException(400, "BAD_REQUEST", "Nonempty rows required");
        if (rows.length() > SubmissionPolicy.MAX_ROWS) throw new SubmissionException(413, "LIMIT_EXCEEDED", "Maximum 200 rows");
        Set<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) throw new SubmissionException(400, "BAD_REQUEST", "Rows must be objects");
            keys(row, "clientRowId", "fields");
            if (!ids.add(requiredString(row, "clientRowId", 128))) throw new SubmissionException(422, "DUPLICATE_CLIENT_ROW_ID", "clientRowId must be unique");
            JSONObject fields = row.optJSONObject("fields");
            if (fields == null || fields.length() == 0 || fields.length() > 256) throw new SubmissionException(400, "BAD_REQUEST", "Invalid fields object");
            for (String key : fields.keySet()) {
                Object field = fields.get(key);
                if (!(field instanceof String) && !(field instanceof Number) && !(field instanceof Boolean))
                    throw new SubmissionException(400, "BAD_REQUEST", "Fields must contain non-null scalar values");
            }
        }
        return rows;
    }
}
