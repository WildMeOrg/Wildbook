import {
  allRequiredColumns,
  removedColumns,
  tableHeaderMapping,
  stringRule,
  intRule,
  doubleRule,
  latlongRule,
  parseEncounterDateString,
} from "../../../pages/BulkImport/BulkImportConstants";

describe("BulkImportConstants", () => {
  describe("allRequiredColumns", () => {
    it("includes required fields like Encounter.year and Encounter.genus", () => {
      expect(allRequiredColumns).toContain("Encounter.year");
      expect(allRequiredColumns).toContain("Encounter.genus");
    });

    it("does not include Encounter.specificEpithet", () => {
      expect(allRequiredColumns).not.toContain("Encounter.specificEpithet");
    });
  });

  describe("removedColumns", () => {
    it("includes time-related fields", () => {
      expect(removedColumns).toContain("Encounter.month");
      expect(removedColumns).toContain("Sighting.hour");
    });
  });

  describe("tableHeaderMapping", () => {
    it("maps Encounter.genus to Species*", () => {
      expect(tableHeaderMapping["Encounter.genus"]).toBe("Species*");
    });

    it("does not include commented-out columns like Encounter.sightingID", () => {
      expect(tableHeaderMapping["Encounter.sightingID"]).toBeUndefined();
    });
  });

  describe("stringRule", () => {
    const { validate } = stringRule;

    it("returns true for valid strings", () => {
      expect(validate("hello")).toBe(true);
      expect(validate("")).toBe(true);
    });

    it("returns false for non-strings", () => {
      expect(validate(123)).toBe(false);
      expect(validate(null)).toBe(true);
    });
  });

  describe("intRule", () => {
    const { validate } = intRule;

    it("validates integers", () => {
      expect(validate("123")).toBe(true);
      expect(validate("-99")).toBe(true);
    });

    it("invalidates non-integers", () => {
      expect(validate("3.14")).toBe(false);
      expect(validate("abc")).toBe(false);
    });
  });

  describe("doubleRule", () => {
    const { validate } = doubleRule;

    it("validates doubles", () => {
      expect(validate("3.14")).toBe(true);
      expect(validate("-123")).toBe(true);
    });

    it("invalidates bad doubles", () => {
      expect(validate("3.14.15")).toBe(false);
      expect(validate("abc")).toBe(false);
    });
  });

  describe("latlongRule", () => {
    const { validate } = latlongRule;

    it("validates correct lat/long formats", () => {
      expect(validate("34.05, -118.25")).toBe(true);
      expect(validate("0,0")).toBe(true);
    });

    it("invalidates out-of-bound or wrong formats", () => {
      expect(validate("91, 0")).toBe(false);
      expect(validate("34.05 -118.25")).toBe(false);
      expect(validate("")).toBe(true);
    });
  });

  describe("parseEncounterDateString", () => {
    it("parses YYYY correctly", () => {
      const raw = {};
      parseEncounterDateString("Encounter.date", "2023", raw);
      expect(raw).toEqual({
        "Encounter.date.year": 2023,
        "Encounter.date.month": "",
        "Encounter.date.day": "",
        "Encounter.date.hour": "",
        "Encounter.date.minutes": "",
      });
    });

    it("parses YYYY-MM-DD correctly", () => {
      const raw = {};
      parseEncounterDateString("Encounter.date", "2023-07-16", raw);
      expect(raw["Encounter.date.year"]).toBe(2023);
      expect(raw["Encounter.date.month"]).toBe(7);
      expect(raw["Encounter.date.day"]).toBe(16);
    });

    it("parses ISO string like YYYY-MM-DDTHH", () => {
      const raw = {};
      parseEncounterDateString("Encounter.date", "2023-07-16T14", raw);
      expect(raw["Encounter.date.hour"]).toBe(14);
    });

    it("parses full date string like from Date()", () => {
      const raw = {};
      parseEncounterDateString("Encounter.date", "July 16, 2023 08:30", raw);
      expect(raw["Encounter.date.year"]).toBe(2023);
      expect(raw["Encounter.date.month"]).toBe(7);
      expect(raw["Encounter.date.day"]).toBe(16);
    });
  });
});
