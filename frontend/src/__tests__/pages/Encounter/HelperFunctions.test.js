import dayjs from "dayjs";
import axios from "axios";

jest.mock("axios");

import {
  validateFieldValue,
  splitPathIntoSegments,
  getValueAtPath,
  setValueAtPath,
  deleteValueAtPath,
  parseYMDHM,
  expandOperations,
  setEncounterState,
} from "../../../pages/Encounter/stores/helperFunctions";

jest.mock("../../../pages/Encounter/constants", () => ({
  LOCAL_FIELD_ERRORS: {
    date: {
      eventDate: "Invalid date",
    },
    location: {
      latitude: "Invalid latitude",
      longitude: "Invalid longitude",
    },
  },
}));

describe("validateFieldValue", () => {
  test("returns null when field has no error rule", () => {
    const res = validateFieldValue("metadata", "submitterID", "someone");
    expect(res).toBeNull();
  });

  test("date: empty -> returns error", () => {
    const res = validateFieldValue("date", "eventDate", "");
    expect(res).toBe("Invalid date");
  });

  test("date: valid YYYY-MM-DD -> null", () => {
    const res = validateFieldValue("date", "eventDate", "2025-10-31");
    expect(res).toBeNull();
  });

  test("date: future date -> error", () => {
    const tomorrow = dayjs().add(1, "day").format("YYYY-MM-DD");
    const res = validateFieldValue("date", "eventDate", tomorrow);
    expect(res).toBe("Invalid date");
  });

  test("location: both empty -> null", () => {
    const res = validateFieldValue("location", "latitude", "", {
      lat: "",
      lon: "",
    });
    expect(res).toBeNull();
  });

  test("location: invalid latitude -> error", () => {
    const res = validateFieldValue("location", "latitude", "999", {
      lat: "999",
      lon: "10",
    });
    expect(res).toBe("Invalid latitude");
  });

  test("location: valid latitude -> null", () => {
    const res = validateFieldValue("location", "latitude", "45", {
      lat: "45",
      lon: "10",
    });
    expect(res).toBeNull();
  });

  test("location: invalid longitude -> error", () => {
    const res = validateFieldValue("location", "longitude", "-190", {
      lat: "10",
      lon: "-190",
    });
    expect(res).toBe("Invalid longitude");
  });

  test("location: valid longitude -> null", () => {
    const res = validateFieldValue("location", "longitude", "120", {
      lat: "10",
      lon: "120",
    });
    expect(res).toBeNull();
  });
});

describe("splitPathIntoSegments", () => {
  test("splits simple path", () => {
    expect(splitPathIntoSegments("a.b.c")).toEqual(["a", "b", "c"]);
  });

  test("splits with array index", () => {
    expect(splitPathIntoSegments("a[0].b[2].c")).toEqual([
      "a",
      "0",
      "b",
      "2",
      "c",
    ]);
  });
});

describe("getValueAtPath", () => {
  const obj = {
    a: {
      b: [{ name: "x" }, { name: "y" }],
    },
  };

  test("gets deep value", () => {
    expect(getValueAtPath(obj, "a.b[1].name")).toBe("y");
  });

  test("returns undefined when path not exist", () => {
    expect(getValueAtPath(obj, "a.b[3].name")).toBeUndefined();
  });
});

describe("setValueAtPath", () => {
  test("sets value on object path", () => {
    const obj = {};
    setValueAtPath(obj, "a.b.c", 123);
    expect(obj).toEqual({ a: { b: { c: 123 } } });
  });

  test("sets value on array index", () => {
    const obj = {};
    setValueAtPath(obj, "a.list[0].name", "tom");
    expect(obj).toEqual({
      a: {
        list: [{ name: "tom" }],
      },
    });
  });
});

describe("deleteValueAtPath", () => {
  test("deletes object key", () => {
    const obj = { a: { b: 1, c: 2 } };
    deleteValueAtPath(obj, "a.b");
    expect(obj).toEqual({ a: { c: 2 } });
  });

  test("deletes array index", () => {
    const obj = { a: { list: ["x", "y", "z"] } };
    deleteValueAtPath(obj, "a.list[1]");
    expect(obj).toEqual({ a: { list: ["x", "z"] } });
  });

  test("does nothing when path missing", () => {
    const obj = { a: 1 };
    deleteValueAtPath(obj, "a.b.c");
    expect(obj).toEqual({ a: 1 });
  });
});

describe("parseYMDHM", () => {
  test("parses YYYY", () => {
    expect(parseYMDHM("2025")).toEqual({
      year: "2025",
      month: "",
      day: "",
      hour: "",
      minutes: "",
    });
  });

  test("parses YYYY-MM-DD", () => {
    expect(parseYMDHM("2025-10-31")).toEqual({
      year: "2025",
      month: "10",
      day: "31",
      hour: "",
      minutes: "",
    });
  });

  test("parses with time", () => {
    expect(parseYMDHM("2025-10-31T14:30")).toEqual({
      year: "2025",
      month: "10",
      day: "31",
      hour: "14",
      minutes: "30",
    });
  });

  test("returns null for invalid", () => {
    expect(parseYMDHM("abc")).toBeNull();
  });

  test("parses Date instance", () => {
    const d = new Date("2025-10-31T14:30:00Z");
    const res = parseYMDHM(d);
    expect(res.year).toBe("2025");
    expect(res.month).toBe("10");
    expect(res.day).toBe("31");
  });
});

describe("expandOperations", () => {
  test("expands date operation to year/month/day/hour/minutes", () => {
    const ops = [{ op: "replace", path: "dateValues", value: "2025-10-31T14:20" }];

    const out = expandOperations(ops);
    expect(out).toEqual([
      { op: "replace", path: "year", value: "2025" },
      { op: "replace", path: "month", value: "10" },
      { op: "replace", path: "day", value: "31" },
      { op: "replace", path: "hour", value: "14" },
      { op: "replace", path: "minutes", value: "20" },
    ]);
  });

  test("expands locationGeoPoint", () => {
    const ops = [
      {
        op: "replace",
        path: "locationGeoPoint",
        value: { lat: 1.23, lon: 4.56 },
      },
    ];
    const out = expandOperations(ops);
    expect(out).toEqual([
      { op: "replace", path: "decimalLatitude", value: 1.23 },
      { op: "replace", path: "decimalLongitude", value: 4.56 },
    ]);
  });

  test("locationGeoPoint with missing coords still pushes both ops", () => {
    const ops = [
      { op: "replace", path: "locationGeoPoint", value: { lat: 1.23 } },
    ];
    const out = expandOperations(ops);
    expect(out).toEqual([
      { op: "replace", path: "decimalLatitude", value: 1.23 },
      { op: "replace", path: "decimalLongitude", value: undefined },
    ]);
  });

  test("expands taxonomy", () => {
    const ops = [{ op: "replace", path: "taxonomy", value: "Panthera leo" }];
    const out = expandOperations(ops);
    expect(out).toEqual([
      { op: "replace", path: "genus", value: "Panthera" },
      { op: "replace", path: "specificEpithet", value: "leo" },
    ]);
  });

  test("keeps other ops unchanged", () => {
    const ops = [{ op: "replace", path: "sex", value: "male" }];
    const out = expandOperations(ops);
    expect(out).toEqual([{ op: "replace", path: "sex", value: "male" }]);
  });

  test("mixed ops", () => {
    const ops = [
      { op: "replace", path: "dateValues", value: "2025-01-02" },
      { op: "replace", path: "taxonomy", value: "Homo sapiens" },
      { op: "replace", path: "sex", value: "female" },
    ];

    const out = expandOperations(ops);
    expect(out).toEqual([
      { op: "replace", path: "year", value: "2025" },
      { op: "replace", path: "month", value: "01" },
      { op: "replace", path: "day", value: "02" },
      { op: "replace", path: "hour", value: null },
      { op: "replace", path: "minutes", value: null },
      { op: "replace", path: "genus", value: "Homo" },
      { op: "replace", path: "specificEpithet", value: "sapiens" },
      { op: "replace", path: "sex", value: "female" },
    ]);
  });
});

describe("setEncounterState", () => {
  test("calls axios.patch with correct payload", async () => {
    axios.patch.mockResolvedValue({ status: 200 });

    await setEncounterState("approved", "E-123");

    expect(axios.patch).toHaveBeenCalledWith("/api/v3/encounters/E-123", [
      { op: "replace", path: "state", value: "approved" },
    ]);
  });

  test("throws when axios fails", async () => {
    axios.patch.mockRejectedValue(new Error("network"));

    await expect(setEncounterState("review", "E-999")).rejects.toThrow(
      "network",
    );
  });
});
