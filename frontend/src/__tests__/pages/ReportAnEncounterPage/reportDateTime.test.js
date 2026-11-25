import moment from "moment";
import {
  formatReportDateTime,
  isPartialReportDate,
  parseReportDateTime,
} from "../../../pages/ReportsAndManagamentPages/reportDateTime";

describe("reportDateTime partial dates", () => {
  test.each(["2024", "2024-03", " 2024-03 ", "2024-12", "0000-03"])(
    "%p is a valid partial date",
    (value) => {
      expect(isPartialReportDate(value)).toBe(true);
      expect(parseReportDateTime(value).isValid()).toBe(true);
    },
  );

  test.each(["2024-3", "2024-13", "2024-00", "24", "2024-03-", "abcd"])(
    "%p is rejected",
    (value) => {
      expect(isPartialReportDate(value)).toBe(false);
      expect(parseReportDateTime(value).isValid()).toBe(false);
      expect(formatReportDateTime(value)).toBe(null);
    },
  );

  test("partial dates are sent at their own precision, not padded", () => {
    expect(formatReportDateTime("2024")).toBe("2024");
    expect(formatReportDateTime("2024-03")).toBe("2024-03");
    expect(formatReportDateTime(" 2024-03 ")).toBe("2024-03");
  });

  test("partial dates parse to the start of their period", () => {
    expect(parseReportDateTime("2024-03").format("YYYY-MM-DDTHH:mm")).toBe(
      "2024-03-01T00:00",
    );
    expect(parseReportDateTime("2024").format("YYYY-MM-DDTHH:mm")).toBe(
      "2024-01-01T00:00",
    );
  });

  test("non-strings and full dates keep the full date-time format", () => {
    expect(isPartialReportDate(moment("2024-03-12T15:30"))).toBe(false);
    expect(isPartialReportDate(null)).toBe(false);
    expect(formatReportDateTime("2024-03-12")).toBe("2024-03-12T00:00");
    expect(formatReportDateTime("2024-03-12 15:30")).toBe("2024-03-12T15:30");
    expect(formatReportDateTime(moment("2024-03-12T15:30"))).toBe(
      "2024-03-12T15:30",
    );
  });
});
