import moment from "moment";

export const REPORT_DATE_TIME_FORMAT = "YYYY-MM-DDTHH:mm";

// Reporters may know only the year or the month of an encounter.  The API
// stores these at their own precision (Encounter.setDateFromISO8601String), so
// they are sent as typed rather than padded to a day and time.
const PARTIAL_DATE_FORMATS = ["YYYY", "YYYY-MM"];

const CIVIL_DATE_TIME_FORMATS = [
  REPORT_DATE_TIME_FORMAT,
  "YYYY-MM-DDTHH:mm:ss",
  "YYYY-MM-DDTHH:mm:ss.SSS",
  "YYYY-MM-DD HH:mm",
  "YYYY-MM-DD HH:mm:ss",
  "YYYY-MM-DD",
  ...PARTIAL_DATE_FORMATS,
];

export function isPartialReportDate(value) {
  return (
    typeof value === "string" &&
    moment(value.trim(), PARTIAL_DATE_FORMATS, true).isValid()
  );
}

// Encounter dates are civil values.  They must not acquire a timezone merely
// because they pass through a browser Date or JSON serialization.
export function parseReportDateTime(value) {
  if (!value) return moment("");

  if (typeof value === "string") {
    const trimmed = value.trim();
    // Older login continuations stored an ISO instant.  Parsing that as an
    // instant restores the local value the reporter had selected; all newly
    // saved values use one of the civil formats above.
    if (/(Z|[+-]\d\d:\d\d)$/.test(trimmed)) return moment(trimmed);
    // A partial date parses to the start of its year or month, so the
    // future-date check rejects only a period that has not yet begun.
    return moment(trimmed, CIVIL_DATE_TIME_FORMATS, true);
  }

  return moment(value);
}

// Value to hold in the report store for a saved or selected string: a partial
// date stays a string so it keeps its precision; anything else becomes a
// moment; an empty selection clears the field.
export function toReportDateTimeValue(value) {
  if (!value) return "";
  if (isPartialReportDate(value)) return value.trim();
  return parseReportDateTime(value);
}

export function formatReportDateTime(value) {
  const dateTime = toReportDateTimeValue(value);
  if (typeof dateTime === "string") return dateTime || null;
  if (!dateTime.isValid()) return null;
  return dateTime.format(REPORT_DATE_TIME_FORMAT);
}
