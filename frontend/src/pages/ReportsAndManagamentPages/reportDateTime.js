import moment from "moment";

export const REPORT_DATE_TIME_FORMAT = "YYYY-MM-DDTHH:mm";

const CIVIL_DATE_TIME_FORMATS = [
  REPORT_DATE_TIME_FORMAT,
  "YYYY-MM-DDTHH:mm:ss",
  "YYYY-MM-DDTHH:mm:ss.SSS",
  "YYYY-MM-DD HH:mm",
  "YYYY-MM-DD HH:mm:ss",
  "YYYY-MM-DD",
];

// Encounter dates are civil values.  They must not acquire a timezone merely
// because they pass through a browser Date or JSON serialization.
export function parseReportDateTime(value) {
  if (!value) return moment("");

  if (typeof value === "string") {
    // Older login continuations stored an ISO instant.  Parsing that as an
    // instant restores the local value the reporter had selected; all newly
    // saved values use one of the civil formats above.
    if (/(Z|[+-]\d\d:\d\d)$/.test(value)) return moment(value);
    return moment(value, CIVIL_DATE_TIME_FORMATS, true);
  }

  return moment(value);
}

export function formatReportDateTime(value) {
  const dateTime = parseReportDateTime(value);
  if (!dateTime.isValid()) return null;
  return dateTime.format(REPORT_DATE_TIME_FORMAT);
}
