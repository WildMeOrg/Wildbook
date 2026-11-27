import {
  findHeaderCollision,
  normalizeHeader,
} from "../../../pages/BulkImport/bulkImportHeaders";

test("renamed headers collide", () => {
  expect(
    findHeaderCollision(["Encounter.occurrenceID", "Encounter.sightingID"]),
  ).toEqual(["Encounter.occurrenceID", "Encounter.sightingID"]);
});

test("literal duplicates collide", () => {
  expect(
    findHeaderCollision(["Encounter.sex", "Encounter.genus", "Encounter.sex"]),
  ).toEqual(["Encounter.sex", "Encounter.sex"]);
});

test("blank header cells are ignored", () => {
  expect(findHeaderCollision(["", "Encounter.sex", ""])).toBeNull();
});

test("distinct headers do not collide", () => {
  expect(findHeaderCollision(["Encounter.sex", "Encounter.genus"])).toBeNull();
});

test("normalizeHeader renames occurrence", () => {
  expect(normalizeHeader("Occurrence.comments")).toBe("Sighting.comments");
  expect(normalizeHeader("Encounter.occurrenceID")).toBe(
    "Encounter.sightingID",
  );
});
