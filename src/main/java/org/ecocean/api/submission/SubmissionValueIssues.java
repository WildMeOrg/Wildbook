package org.ecocean.api.submission;

import java.time.LocalDate;
import java.util.*;
import org.ecocean.CommonConfiguration;
import org.ecocean.Util;
import org.ecocean.api.SiteSettings;
import org.ecocean.api.bulk.BulkValidator;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * Explains a legacy bulk-import field rejection as a stable submissions reason, a specific message and the field
 * the submitter should look at. Explanation never changes whether a row is accepted: callers report exactly one
 * issue per legacy rejection. Mapping keys on legacy message text, pinned by SubmissionValueIssuesTest, with an
 * INVALID fallback that never exposes Java exception text.
 */
public class SubmissionValueIssues {
    public static final List<String> REASONS = List.of("REQUIRED", "REQUIRES_FIELD", "UNPARSEABLE", "OUT_OF_RANGE",
        "FUTURE_DATE", "NOT_CONFIGURED", "INVALID", "INVALID_BOX", "MISSING_CLASS", "UNKNOWN_CLASS", "INVALID_VIEWPOINT",
        "INVALID_THETA", "BOX_OUT_OF_BOUNDS");
    private static final int MAX_ECHO = 64, MAX_ALLOWED_LISTED = 20, MAX_ALLOWED_TEXT = 300;

    public static class Issue {
        public final String field, reason, message;
        Issue(String field, String reason, String message) { this.field = field; this.reason = reason; this.message = message; }
    }

    /**
     * @param today the date at UTC+14 captured before legacy validation ran; legacy "future" was judged at the
     *              same or a later instant, so every date it rejected is also after this date
     */
    public static Issue explain(String field, Exception ex, JSONObject fields, Map<String, Object> checked,
        Shepherd sh, LocalDate today) {
        String legacy = ex.getMessage() == null ? "" : ex.getMessage();
        Object raw = fields.opt(field);
        if (legacy.startsWith("required value") || legacy.equals("must supply a valid month along with day")) {
            // legacy replaces a supplied-but-invalid value's own error with a generic "required"; recover it
            if (raw != null) {
                Issue cause = recover(field, raw, sh);
                if (cause != null) return cause;
            }
            if (legacy.startsWith("must supply")) return new Issue(field, "REQUIRES_FIELD", "Encounter.month is required when Encounter.day is supplied");
            return new Issue(field, "REQUIRED", field + " is required");
        }
        if (legacy.equals("date is in the future")) return futureDate(field, fields, checked, today);
        return translate(field, legacy, fields, checked, sh);
    }

    private static Issue recover(String field, Object raw, Shepherd sh) {
        try {
            BulkValidator.validateValue(field, raw, sh);
            return null;
        } catch (Exception ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (message.startsWith("required value")) return null;
            return translate(field, message, new JSONObject().put(field, raw), Collections.emptyMap(), sh);
        }
    }

    private static Issue translate(String field, String legacy, JSONObject fields, Map<String, Object> checked, Shepherd sh) {
        Issue box = translateBox(field, legacy);
        if (box != null) return box;
        if (legacy.startsWith("error parsing integer")) return new Issue(field, "UNPARSEABLE", field + " must be a whole number");
        if (legacy.startsWith("error parsing double")) return new Issue(field, "UNPARSEABLE", field + " must be a decimal number");
        if (legacy.equals("year value too small")) return new Issue(field, "OUT_OF_RANGE", "Encounter.year must be 1000 or later");
        if (legacy.startsWith("month value too")) return new Issue(field, "OUT_OF_RANGE", "Encounter.month must be 1 through 12");
        if (legacy.startsWith("day value too")) return new Issue(field, "OUT_OF_RANGE", "Encounter.day must be 1 through 31");
        if (legacy.startsWith("hour value too")) return new Issue(field, "OUT_OF_RANGE", "Encounter.hour must be 0 through 23");
        if (legacy.startsWith("minutes value too")) return new Issue(field, "OUT_OF_RANGE", "Encounter.minutes must be 0 through 59");
        if (legacy.startsWith("invalid Encounter.decimalLatitude value")) return new Issue(field, "OUT_OF_RANGE", "Encounter.decimalLatitude must be between -90 and 90");
        if (legacy.startsWith("invalid Encounter.decimalLongitude value")) return new Issue(field, "OUT_OF_RANGE", "Encounter.decimalLongitude must be between -180 and 180");
        if (legacy.equals("day is out of range for month")) {
            Integer y = legacyComponent(fields, checked, "Encounter.year"), m = legacyComponent(fields, checked, "Encounter.month");
            String day = echo(fields.opt("Encounter.day"));
            if (y != null && m != null) return new Issue(field, "OUT_OF_RANGE", String.format("%04d-%02d has no day %s", y, m, day));
            return new Issue(field, "OUT_OF_RANGE", "Encounter.day does not exist in the supplied month");
        }
        if (legacy.equals("must supply both latitude and longitude"))
            return new Issue(field, "REQUIRES_FIELD", "Encounter.decimalLatitude and Encounter.decimalLongitude must be supplied together");
        if (legacy.equals("invalid taxonomy value")) {
            String name = Util.taxonomyString(String.valueOf(fields.opt("Encounter.genus")), String.valueOf(fields.opt("Encounter.specificEpithet")));
            return new Issue(field, "NOT_CONFIGURED", "'" + echo(name) + "' is not a configured taxonomy; see siteTaxonomies in /api/v3/site-settings");
        }
        if (legacy.startsWith("invalid location value"))
            return new Issue(field, "NOT_CONFIGURED", "'" + echo(fields.opt(field)) + "' is not a configured location ID");
        if (legacy.startsWith("invalid sex value")) return notConfigured(field, fields, Arrays.asList(SiteSettings.VALUES_SEX));
        if (legacy.startsWith("invalid lifeStage value"))
            return notConfigured(field, fields, CommonConfiguration.getIndexedPropertyValues("lifeStage", sh.getContext()));
        if (legacy.startsWith("invalid livingStatus value"))
            return notConfigured(field, fields, CommonConfiguration.getIndexedPropertyValues("livingStatus", sh.getContext()));
        return new Issue(field, "INVALID", sanitize(legacy));
    }

    // bulk-import boxes (spec 4.9): the per-photo box columns and the detector score, from the shared rules' messages
    private static Issue translateBox(String field, String legacy) {
        String slot = field.replaceAll("^(Encounter\\.mediaAsset\\d+).*$", "$1");
        if (legacy.startsWith("bbox must be") || legacy.startsWith("bbox x and y") || legacy.startsWith("bbox width and height"))
            return new Issue(field, "INVALID_BOX", field + " must be \"x,y,width,height\": four whole numbers on the image's pixel grid, x and y at least 0, width and height at least 1");
        if (legacy.startsWith("a box needs an iaClass")) {
            String classField = legacy.startsWith("a box needs an iaClass in ") ? legacy.substring("a box needs an iaClass in ".length()) : field;
            return new Issue(field, "MISSING_CLASS", classField + " is required when a box is supplied");
        }
        if (legacy.startsWith("invalid iaClass '")) {
            int end = legacy.indexOf("'; ", "invalid iaClass '".length());
            String cls = end > 0 ? legacy.substring("invalid iaClass '".length(), end) : "";
            String rest = end > 0 ? legacy.substring(end + 3) : "";
            if (rest.startsWith("valid classes for ")) {
                int colon = rest.indexOf(": ");
                String species = colon > 0 ? rest.substring("valid classes for ".length(), colon) : "";
                String list = colon > 0 ? rest.substring(colon + 2) : "";
                return new Issue(field, "UNKNOWN_CLASS", "'" + echo(cls) + "' is not a matchable class for " + species + "; use one of: " + list);
            }
            if (rest.startsWith("no matchable classes for "))
                return new Issue(field, "UNKNOWN_CLASS", "'" + echo(cls) + "' cannot be matched: " + rest.substring("no matchable classes for ".length()) + " has no matchable classes");
            return new Issue(field, "UNKNOWN_CLASS", "'" + echo(cls) + "' is not a matchable class");
        }
        if (legacy.startsWith("invalid viewpoint value")) return new Issue(field, "INVALID_VIEWPOINT", field + " is not a known viewpoint");
        if (legacy.startsWith("invalid theta value") || legacy.startsWith("theta must be"))
            return new Issue(field, "INVALID_THETA", field + " must be a finite number of radians between -6.2832 and 6.2832");
        if (legacy.startsWith(field + " needs a box in "))
            return new Issue(field, "REQUIRES_FIELD", field + " requires " + legacy.substring((field + " needs a box in ").length()));
        if (legacy.equals("box without a photo in this slot")) return new Issue(field, "REQUIRES_FIELD", field + " requires " + slot);
        if (legacy.startsWith("iaClass longer than")) return new Issue(field, "OUT_OF_RANGE", field + " must be at most 64 characters");
        if (legacy.startsWith("bboxSource longer than")) return new Issue(field, "OUT_OF_RANGE", field + " must be at most 128 characters");
        if (legacy.startsWith("invalid quality value")) return new Issue(field, "UNPARSEABLE", field + " must be a decimal number");
        if (legacy.startsWith("quality must be a finite number")) return new Issue(field, "OUT_OF_RANGE", field + " must be a finite decimal number");
        return null;
    }

    private static Issue futureDate(String field, JSONObject fields, Map<String, Object> checked, LocalDate today) {
        // compare the components exactly as legacy checkYMD did, even where a later check replaced their entries
        Integer year = legacyComponent(fields, checked, "Encounter.year"), month = legacyComponent(fields, checked, "Encounter.month"),
            day = legacyComponent(fields, checked, "Encounter.day");
        String suffix = " is later than today's date in every time zone";
        if (year == null) return new Issue(field, "FUTURE_DATE", "The date" + suffix);
        if (year > today.getYear()) return new Issue("Encounter.year", "FUTURE_DATE", String.format("%04d", year) + suffix);
        if (month != null && year == today.getYear()) {
            if (month > today.getMonthValue())
                return new Issue("Encounter.month", "FUTURE_DATE", String.format("%04d-%02d", year, month) + suffix);
            if (day != null && month == today.getMonthValue() && day > today.getDayOfMonth())
                return new Issue("Encounter.day", "FUTURE_DATE", String.format("%04d-%02d-%02d", year, month, day) + suffix);
        }
        return new Issue(field, "FUTURE_DATE", "The date" + suffix); // defensive: legacy judged a later instant
    }

    // A component legacy checkYMD compared: its validated value, or, when a later date check replaced the entry
    // (year by "date is in the future", day by "day is out of range for month"), the same raw value re-parsed.
    private static Integer legacyComponent(JSONObject fields, Map<String, Object> checked, String field) {
        Object entry = checked.get(field);
        if (entry instanceof BulkValidator) {
            Object value = ((BulkValidator)entry).getValue();
            return value instanceof Integer ? (Integer)value : null;
        }
        String message = entry instanceof Exception ? ((Exception)entry).getMessage() : null;
        if (!"date is in the future".equals(message) && !"day is out of range for month".equals(message)) return null;
        try { return Integer.valueOf(String.valueOf(fields.opt(field))); }
        catch (NumberFormatException ex) { return null; }
    }

    private static Issue notConfigured(String field, JSONObject fields, List<String> allowed) {
        String message = "'" + echo(fields.opt(field)) + "' is not a configured " + field.substring("Encounter.".length()) + " value";
        String list = allowed == null || allowed.isEmpty() || allowed.size() > MAX_ALLOWED_LISTED ? null : String.join(", ", allowed);
        if (list != null && list.length() <= MAX_ALLOWED_TEXT) message += "; use one of: " + list;
        else message += "; see /api/v3/site-settings for configured values";
        return new Issue(field, "NOT_CONFIGURED", message);
    }

    static String echo(Object value) {
        String text = String.valueOf(value);
        return text.length() <= MAX_ECHO ? text : text.substring(0, MAX_ECHO) + "...";
    }

    static String sanitize(String legacy) {
        int java = legacy.indexOf("java.");
        String text = (java >= 0 ? legacy.substring(0, java) : legacy).replaceAll("[\\s:]+$", "");
        return text.isEmpty() ? "Value is invalid" : echo(text);
    }
}
