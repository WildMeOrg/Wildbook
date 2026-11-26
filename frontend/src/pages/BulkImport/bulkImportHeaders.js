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
