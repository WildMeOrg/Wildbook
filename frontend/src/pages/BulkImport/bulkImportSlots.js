// Bulk-import boxes (spec 4.1 / 4.6): which columns are photo slots and box columns, and the
// client-side checks for box values. Client checks never reject a value the server accepts.

export const BOX_SUFFIXES = [
  "bbox",
  "iaClass",
  "viewpoint",
  "theta",
  "bboxSource",
];
export const THETA_LIMIT = 6.2832;
const INT_MIN = -2147483648;
const INT_MAX = 2147483647;

const PHOTO_SLOT_RE = /^Encounter\.mediaAsset\d+$/;
const SLOT_RE = /^Encounter\.mediaAsset\d+(\..+)?$/;
const BOX_RE = new RegExp(
  `^Encounter\\.mediaAsset\\d+\\.(${BOX_SUFFIXES.join("|")})$`,
);

export const isPhotoSlotColumn = (col) => PHOTO_SLOT_RE.test(col);

// a bare photo slot or any per-photo column (box columns, keywords, labeled keywords)
export const isSlotColumn = (col) => SLOT_RE.test(col);

export const boxSuffixOf = (col) => {
  const m = BOX_RE.exec(col);
  return m ? m[1] : null;
};

export const siteAcceptsBoxes = (indexableFields) =>
  Array.isArray(indexableFields) &&
  indexableFields.includes("Encounter.mediaAsset#.bbox");

export const hasBoxColumns = (cols, indexableFields) =>
  siteAcceptsBoxes(indexableFields) &&
  (cols || []).some((col) => boxSuffixOf(col) === "bbox");

// Java String.trim(): strips every leading/trailing character <= U+0020
export const javaTrim = (val) => {
  const text = String(val ?? "");
  let start = 0;
  let end = text.length;
  while (start < end && text.charCodeAt(start) <= 0x20) start++;
  while (end > start && text.charCodeAt(end - 1) <= 0x20) end--;
  return text.slice(start, end);
};

const isBlank = (val) => javaTrim(val) === "";

// mirrors BulkValidator.parseBbox: four Java ints x,y,width,height with x,y >= 0, width,height >= 1
const hasNonAscii = (text) =>
  Array.from(String(text)).some((ch) => ch.charCodeAt(0) > 0x7f);

export const isValidBbox = (val) => {
  if (isBlank(val)) return true;
  if (hasNonAscii(val)) return true; // e.g. full-width digits: the server decides
  const parts = String(val).split(",");
  if (parts.length !== 4) return false;
  const nums = [];
  for (const part of parts) {
    const text = javaTrim(part);
    if (!/^[+-]?\d+$/.test(text)) return false;
    const n = Number(text);
    if (n < INT_MIN || n > INT_MAX) return false;
    nums.push(n);
  }
  return nums[0] >= 0 && nums[1] >= 0 && nums[2] >= 1 && nums[3] >= 1;
};

// rejects only what is certainly invalid; Java-only number forms are left to the server
export const isValidTheta = (val) => {
  if (isBlank(val)) return true;
  const text = javaTrim(val);
  if (hasNonAscii(text)) return true;
  if (!/\d/.test(text)) return false;
  const n = Number(text);
  if (Number.isNaN(n)) return true;
  return Number.isFinite(n) && Math.abs(n) <= THETA_LIMIT;
};

// a draft saved before the page kept photo slots: its sheet had box columns, and other photo
// slots were merged into slot 0, so their identity cannot be recovered
export const isLegacyBoxedDraft = (saved) => {
  const columnsDef = saved?.columnsDef || [];
  const rawColumns = saved?.rawColumns || [];
  if (!columnsDef.some((col) => boxSuffixOf(col) === "bbox")) return false;
  return rawColumns.some(
    (col) => isPhotoSlotColumn(col) && !columnsDef.includes(col),
  );
};
