const buildBioMeasurementStats = (results = []) => {
  const map = new Map();

  for (const it of results) {
    const sx = sexOf(it);
    const ms = Array.isArray(it?.biologicalMeasurements)
      ? it.biologicalMeasurements
      : [];
    for (const m of ms) {
      const type = (m?.type || "").trim();
      if (!type) continue;

      const v = Number(m?.value);
      if (!Number.isFinite(v)) continue;

      if (!map.has(type))
        map.set(type, {
          units: (m?.units || "nounits").trim(),
          all: [],
          male: [],
          female: [],
        });
      const bucket = map.get(type);
      if ((!bucket.units || bucket.units === "nounits") && m?.units)
        bucket.units = String(m.units).trim();

      bucket.all.push(v);
      if (sx === "male") bucket.male.push(v);
      if (sx === "female") bucket.female.push(v);
    }
  }

  return [...map.entries()]
    .map(([type, b]) => ({
      type,
      units: b.units || "nounits",
      overall: { n: b.all.length, mean: mean(b.all), sd: sampleStd(b.all) },
      males: { n: b.male.length, mean: mean(b.male), sd: sampleStd(b.male) },
      females: {
        n: b.female.length,
        mean: mean(b.female),
        sd: sampleStd(b.female),
      },
    }))
    .sort((a, b) => a.type.localeCompare(b.type));
};

const processData = (data) => {
  const counts = data.reduce((acc, curr) => {
    acc[curr] = (acc[curr] || 0) + 1;
    return acc;
  }, {});
  return Object.entries(counts).map(([key, value]) => ({ name: key, value }));
};

const parseDate = (item) => {
  if (item?.dateSubmitted) return new Date(item.dateSubmitted);
  if (item?.indexTimestamp) return new Date(Number(item.indexTimestamp));
  if (item?.dateMillis) return new Date(Number(item.dateMillis));
  if (item?.date) return new Date(item.date);
  return null;
};

const getYear = (item) => {
  const d = parseDate(item);
  return d && !isNaN(d.getTime()) ? d.getFullYear() : null;
};

const isAI = (item) => {
  const needle = "wildbookai";
  if (
    item?.assignedUsername &&
    String(item.assignedUsername).toLowerCase() === needle
  )
    return true;
  if (
    item?.submitterUserId &&
    String(item.submitterUserId).toLowerCase() === needle
  )
    return true;
  if (
    Array.isArray(item?.submitters) &&
    item.submitters.some((s) => String(s).toLowerCase() === needle)
  )
    return true;
  return false;
};

const getSubmitterDisplay = (item) => {
  if (item?.assignedUsername) return String(item.assignedUsername);
  if (item?.submitterUserId) return String(item.submitterUserId);
  if (Array.isArray(item?.submitters) && item.submitters.length > 0)
    return String(item.submitters[0]);
  return "unknown";
};

const sortByAddedTimeAsc = (arr) => {
  return [...arr].sort((a, b) => {
    const da = parseDate(a)?.getTime() ?? 0;
    const db = parseDate(b)?.getTime() ?? 0;
    return da - db;
  });
};

const sampleMax = (rows, max = 50) => {
  if (rows.length <= max) return rows;
  const result = [];
  const step = (rows.length - 1) / (max - 1);
  for (let i = 0; i < max; i++) {
    const idx = Math.round(i * step);
    result.push(rows[idx]);
  }
  return result;
};

const buildDiscoveryBars = (results) => {
  const sorted = sortByAddedTimeAsc(results);
  const seen = new Set();
  const bars = [];
  for (let i = 0; i < sorted.length; i++) {
    const enc = sorted[i];
    const indiv = enc?.individualId || enc?.individualDisplayName;
    if (indiv && String(indiv).toLowerCase() !== "unassigned") {
      if (!seen.has(indiv)) seen.add(indiv);
    }
    bars.push({ name: String(i + 1), value: seen.size });
  }
  return sampleMax(bars, 50);
};

const buildYearlyCumulativeHumanTotals = (results) => {
  const countsByYear = new Map();
  let minYear = Infinity;
  let maxYear = -Infinity;

  for (const item of results) {
    const y = getYear(item);
    if (!y) continue;
    if (isAI(item)) continue;
    countsByYear.set(y, (countsByYear.get(y) || 0) + 1);
    if (y < minYear) minYear = y;
    if (y > maxYear) maxYear = y;
  }
  if (!isFinite(minYear) || !isFinite(maxYear)) return [];

  for (let y = minYear; y <= maxYear; y++) {
    if (!countsByYear.has(y)) countsByYear.set(y, 0);
  }

  const rows = [];
  let running = 0;
  for (let y = minYear; y <= maxYear; y++) {
    running += countsByYear.get(y) || 0;
    rows.push({ name: String(y), value: running });
  }
  return rows;
};

const buildTopTaggers = (results) => {
  const map = new Map();

  for (const item of results) {
    const user = getSubmitterDisplay(item);
    const indiv = item?.individualId || item?.individualDisplayName;
    if (!indiv) continue;
    if (!map.has(user)) map.set(user, new Set());
    map.get(user).add(indiv);
  }

  const rows = Array.from(map.entries()).map(([user, set]) => ({
    name: user.replace(/['"]/g, ""),
    value: set.size,
  }));

  rows.sort((a, b) => b.value - a.value);
  return rows.slice(0, 10);
};

const isIdentified = (item) => {
  const id = item?.individualId || item?.individualDisplayName;
  return id && String(id).toLowerCase() !== "unassigned";
};

const countMediaAssets = (item) => {
  const a = Array.isArray(item?.mediaAssets) ? item.mediaAssets.length : 0;
  const b = Array.isArray(item?.media) ? item.media.length : 0;
  return a || b;
};

const countAnnotations = (item) => {
  let total = 0;
  if (Array.isArray(item?.mediaAssets)) {
    for (const ma of item.mediaAssets) {
      if (Array.isArray(ma?.annotations)) total += ma.annotations.length;
    }
  }
  if (!total && Array.isArray(item?.annotations)) {
    total += item.annotations.length;
  }
  return total;
};

const mean = (arr) =>
  arr.length ? arr.reduce((a, b) => a + b, 0) / arr.length : NaN;
const sampleStd = (arr) => {
  const n = arr.length;
  if (n <= 1) return NaN;
  const m = mean(arr);
  const variance = arr.reduce((s, x) => s + (x - m) ** 2, 0) / (n - 1);
  return Math.sqrt(variance);
};

const sexOf = (item) => {
  const s = String(item?.sex ?? item?.individualSex ?? "").toLowerCase();
  if (s.startsWith("m")) return "male";
  if (s.startsWith("f")) return "female";
  return "unknown";
};

const buildMeasurementStats = (results = []) => {
  const map = new Map();

  for (const it of results) {
    const sx = sexOf(it);
    const ms = Array.isArray(it?.measurements) ? it.measurements : [];
    for (const m of ms) {
      const type = (m?.type || "").trim();
      if (!type) continue;

      const v = Number(m?.value);
      if (!Number.isFinite(v)) continue;

      if (!map.has(type))
        map.set(type, {
          units: (m?.units || "nounits").trim(),
          all: [],
          male: [],
          female: [],
        });
      const bucket = map.get(type);
      if ((!bucket.units || bucket.units === "nounits") && m?.units)
        bucket.units = String(m.units).trim();

      bucket.all.push(v);
      if (sx === "male") bucket.male.push(v);
      if (sx === "female") bucket.female.push(v);
    }
  }

  return [...map.entries()]
    .map(([type, b]) => ({
      type,
      units: b.units || "nounits",
      overall: { n: b.all.length, mean: mean(b.all), sd: sampleStd(b.all) },
      males: { n: b.male.length, mean: mean(b.male), sd: sampleStd(b.male) },
      females: {
        n: b.female.length,
        mean: mean(b.female),
        sd: sampleStd(b.female),
      },
    }))
    .sort((a, b) => a.type.localeCompare(b.type));
};

const fmt = (stat, units) => {
  if (!stat?.n) return `— ${units} (Std. Dev. —) N=0`;
  const m = Number.isFinite(stat.mean) ? stat.mean.toFixed(2) : "—";
  const sd = Number.isFinite(stat.sd) ? stat.sd.toFixed(2) : "—";
  return `${m} ${units} (Std. Dev. ${sd}) N=${stat.n}`;
};

const toArray = (v) => (Array.isArray(v) ? v : v ? [v] : []);

const lower = (s) => (typeof s === "string" ? s.trim().toLowerCase() : "");

const userKeyResearch = (u) => {
  if (u && typeof u === "object" && u.username) return lower(u.username);
  if (typeof u === "string") return "";
  return "";
};

const userKeyPublic = (u) => {
  if (u && typeof u === "object") {
    if (u.username) return "";
    if (u.email) return lower(u.email);
    if (u.id) return lower(u.id);
    if (u.uuid) return lower(u.uuid);
    if (u.fullName) return lower(u.fullName);
    try {
      return lower(JSON.stringify(u));
    } catch {
      return "";
    }
  }
  if (typeof u === "string") return lower(u);
  return "";
};

const accumulateContributors = (rows) => {
  const research = new Set();
  const pub = new Set();

  for (const it of rows) {
    const submitters = toArray(it?.submitters);
    const photographers = toArray(it?.photographers);

    for (const u of [...submitters, ...photographers]) {
      const keyR = userKeyResearch(u);
      if (keyR) {
        research.add(keyR);
      } else {
        const keyP = userKeyPublic(u);
        if (keyP) pub.add(keyP);
      }
    }
  }

  return {
    researchCount: research.size,
    publicCount: pub.size,
    total: research.size + pub.size,
  };
};

export {
  buildMeasurementStats,
  buildBioMeasurementStats,
  fmt,
  buildDiscoveryBars,
  buildYearlyCumulativeHumanTotals,
  buildTopTaggers,
  processData,
  isIdentified,
  countMediaAssets,
  countAnnotations,
  accumulateContributors,
};
