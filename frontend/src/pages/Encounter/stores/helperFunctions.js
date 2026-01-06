import dayjs from "dayjs";
import { LOCAL_FIELD_ERRORS } from "../constants";
import axios from "axios";

function validateFieldValue(sectionName, fieldPath, value, ctx = {}) {
  const errors = LOCAL_FIELD_ERRORS[sectionName] || {};
  const errorMessage = errors[fieldPath];
  if (!errorMessage) return null;

  if (sectionName === "date") {
    if (value == null || value === "") return errorMessage;
    const FORMATS = [
      "YYYY",
      "YYYY-MM",
      "YYYY-MM-DD",
      "YYYY-MM-DDTHH",
      "YYYY-MM-DDTHH:mm",
      "YYYY-MM-DDTHH:mm[Z]",
    ];
    let parsed;
    if (value instanceof Date) {
      parsed = dayjs(value);
    } else {
      parsed = dayjs(String(value).trim(), FORMATS, true);
    }

    if (!parsed.isValid() || parsed.isAfter(dayjs())) {
      return errorMessage;
    }
    return null;
  }

  if (sectionName === "location") {
    const isEmpty = (v) => v === "" || v == null;

    const lat = fieldPath === "latitude" ? value : ctx.lat;
    const lon = fieldPath === "longitude" ? value : ctx.lon;

    if (isEmpty(lat) && isEmpty(lon)) return null;

    if (fieldPath === "latitude") {
      if (isEmpty(value)) return errorMessage;
      const n = Number(value);
      if (!Number.isFinite(n) || n < -90 || n > 90) return errorMessage;
      return null;
    }

    if (fieldPath === "longitude") {
      if (isEmpty(value)) return errorMessage;
      const n = Number(value);
      if (!Number.isFinite(n) || n < -180 || n > 180) return errorMessage;
      return null;
    }
  }

  return null;
}

function splitPathIntoSegments(fieldPath) {
  return fieldPath.replace(/\[(\d+)\]/g, ".$1").split(".");
}

function getValueAtPath(targetObject, fieldPath) {
  const pathSegments = splitPathIntoSegments(fieldPath);
  return pathSegments.reduce((currentValue, pathSegment) => {
    if (currentValue == null) return undefined;
    return currentValue[pathSegment];
  }, targetObject);
}

function setValueAtPath(targetObject, fieldPath, newValue) {
  const pathSegments = splitPathIntoSegments(fieldPath);
  let cursor = targetObject;
  for (let i = 0; i < pathSegments.length - 1; i++) {
    const segment = pathSegments[i];
    const nextSegment = pathSegments[i + 1];
    const nextIsArrayIndex = /^\d+$/.test(nextSegment);
    if (cursor[segment] == null || typeof cursor[segment] !== "object") {
      cursor[segment] = nextIsArrayIndex ? [] : {};
    }
    cursor = cursor[segment];
  }
  const lastSegment = pathSegments[pathSegments.length - 1];
  cursor[lastSegment] = newValue;
}

function deleteValueAtPath(targetObject, fieldPath) {
  const pathSegments = splitPathIntoSegments(fieldPath);
  let cursor = targetObject;
  for (let i = 0; i < pathSegments.length - 1; i++) {
    const segment = pathSegments[i];
    if (cursor[segment] == null) return;
    cursor = cursor[segment];
  }
  const lastSegment = pathSegments[pathSegments.length - 1];
  const isArrayIndex = /^\d+$/.test(lastSegment);
  if (Array.isArray(cursor) && isArrayIndex) {
    cursor.splice(Number(lastSegment), 1);
  } else {
    delete cursor[lastSegment];
  }
}

function parseYMDHM(val) {
  if (val == null) return null;

  if (val instanceof Date && !isNaN(val)) {
    return {
      year: String(val.getFullYear()).padStart(4, "0"),
      month: String(val.getMonth() + 1).padStart(2, "0"),
      day: String(val.getDate()).padStart(2, "0"),
      hour: String(val.getHours()).padStart(2, "0"),
      minutes: String(val.getMinutes()).padStart(2, "0"),
    };
  }

  const s = String(val).trim();

  const re =
    /^(\d{4})(?:-(\d{2}))?(?:-(\d{2}))?(?:[T\s](\d{2}):(\d{2}))?(?:Z|[+-]\d{2}:\d{2})?$/;
  const m = re.exec(s);
  if (!m) return null;

  const [, Y, M, D, H, Min] = m;
  return {
    year: Y,
    month: M ?? "",
    day: D ?? "",
    hour: H ?? "",
    minutes: Min ?? "",
  };
}

const pad2 = (n) => String(n).padStart(2, "0");

function formatDateValues({ year, month, day, hour, minutes } = {}) {
  if (
    year == null &&
    month == null &&
    day == null &&
    hour == null &&
    minutes == null
  ) {
    return "";
  }

  let dateStr = "";
  if (year != null) {
    const parts = [String(year)];
    if (month != null) parts.push(pad2(month));
    if (day != null) parts.push(pad2(day));
    dateStr = parts.join("-");
  }

  let timeStr = "";
  if (hour != null) {
    timeStr = pad2(hour);
    if (minutes != null) timeStr += `:${pad2(minutes)}`;
  }

  return [dateStr, timeStr].filter(Boolean).join(" ").trim();
}

function expandOperations(operations) {
  const base = operations.slice();
  const out = [];

  for (const op of base) {
    if (op.path === "dateValues") {
      const p = parseYMDHM(op.value);
      if (!p) continue;
      out.push({ op: "replace", path: "year", value: String(p.year) });
      out.push({
        op: "replace",
        path: "month",
        value: p.month ? String(p.month) : null,
      });
      out.push({
        op: "replace",
        path: "day",
        value: p.day ? String(p.day) : null,
      });
      out.push({
        op: "replace",
        path: "hour",
        value: p.hour ? String(p.hour) : null,
      });
      out.push({
        op: "replace",
        path: "minutes",
        value: p.minutes ? String(p.minutes) : null,
      });
      continue;
    }

    if (op.path === "locationGeoPoint" && op.value) {
      out.push({ op: "replace", path: "decimalLatitude", value: op.value.lat });
      out.push({
        op: "replace",
        path: "decimalLongitude",
        value: op.value.lon,
      });
      continue;
    }

    if (op.path === "taxonomy" && op.value) {
      const s = String(op.value).trim();
      const [genus = "", specificEpithet = ""] = s.split(/\s+/, 2);
      out.push({ op: "replace", path: "genus", value: genus });
      out.push({
        op: "replace",
        path: "specificEpithet",
        value: specificEpithet,
      });
      continue;
    }
    out.push(op);
  }

  return out;
}

async function setEncounterState(newState, encounterId) {
  const operations = [{ op: "replace", path: "state", value: newState }];
  try {
    await axios.patch(`/api/v3/encounters/${encounterId}`, operations);
  } catch (error) {
    console.error("Error setting encounter state:", error);
    throw error;
  }
}

export {
  validateFieldValue,
  splitPathIntoSegments,
  getValueAtPath,
  setValueAtPath,
  deleteValueAtPath,
  parseYMDHM,
  formatDateValues,
  expandOperations,
  setEncounterState,
};
