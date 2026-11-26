// The page renames Occurrence -> Sighting in spreadsheet headers. No trimming.
export const normalizeHeader = (key) =>
  key.replace(/Occurrence/g, "Sighting").replace(/occurrence/g, "sighting");

// First pair (first-seen, later) of non-blank header cells that name the same
// field once renamed, or null.
export const findHeaderCollision = (headers) => {
  const seen = new Map();
  for (const h of headers || []) {
    if (h == null || String(h) === "") continue;
    const original = String(h);
    const name = normalizeHeader(original);
    if (seen.has(name)) return [seen.get(name), original];
    seen.set(name, original);
  }
  return null;
};

// A row with at least one non-blank cell (blank = empty or whitespace only).
export const hasAnyValue = (row) =>
  Object.values(row).some((v) => String(v ?? "").trim() !== "");

export const DATE_PARTS = ["year", "month", "day", "hour", "minutes"];
const DATE_PART_RE = new RegExp(
  `^(Encounter|Sighting)\\.(${DATE_PARTS.join("|")})$`,
);

// The prefix of an Encounter.* / Sighting.* calendar date part (seconds excluded), else null.
export const datePrefixOf = (field) => {
  const m = DATE_PART_RE.exec(String(field ?? ""));
  return m ? m[1] : null;
};

// Which table column shows each prefix's date, derived from the source headers only:
// one prefix -> shown in Encounter.year; both -> Encounter.year and Sighting.year.
export const dateDisplayMapFor = (rawColumns) => {
  const prefixes = new Set((rawColumns || []).map(datePrefixOf));
  const hasEnc = prefixes.has("Encounter");
  const hasSig = prefixes.has("Sighting");
  if (hasEnc && hasSig) {
    return { "Encounter.year": "Encounter", "Sighting.year": "Sighting" };
  }
  if (hasEnc) return { "Encounter.year": "Encounter" };
  if (hasSig) return { "Encounter.year": "Sighting" };
  return {};
};
