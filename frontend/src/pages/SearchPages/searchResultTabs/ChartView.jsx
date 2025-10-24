import React from "react";
import HorizontalBarChart from "../../../components/HorizontalBarChart";
import VerticalBarChart from "../../../components/VerticalBarChart";
import Piechart from "../../../components/Piechart";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Row, Col } from "react-bootstrap";
import FullScreenLoader from "../../../components/FullScreenLoader";

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
  if (item?.assignedUsername && String(item.assignedUsername).toLowerCase() === needle) return true;
  if (item?.submitterUserId && String(item.submitterUserId).toLowerCase() === needle) return true;
  if (Array.isArray(item?.submitters) && item.submitters.some((s) => String(s).toLowerCase() === needle)) return true;
  return false;
};

const getSubmitterDisplay = (item) => {
  if (item?.assignedUsername) return String(item.assignedUsername);
  if (item?.submitterUserId) return String(item.submitterUserId);
  if (Array.isArray(item?.submitters) && item.submitters.length > 0) return String(item.submitters[0]);
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

const mean = (arr) => (arr.length ? arr.reduce((a, b) => a + b, 0) / arr.length : NaN);
const sampleStd = (arr) => {
  const n = arr.length;
  if (n <= 1) return NaN;
  const m = mean(arr);
  const variance = arr.reduce((s, x) => s + (x - m) ** 2, 0) / (n - 1);
  return Math.sqrt(variance);
};

const pickNumericByKeyCI = (obj, key) => {
  if (!obj || typeof obj !== "object") return undefined;
  const wanted = key.toLowerCase();
  for (const k of Object.keys(obj)) {
    if (k.toLowerCase() === wanted) {
      const v = obj[k];
      const num =
        typeof v === "number"
          ? v
          : typeof v === "string"
            ? Number(v)
            : typeof v?.value === "number"
              ? v.value
              : typeof v?.value === "string"
                ? Number(v.value)
                : undefined;
      return Number.isFinite(num) ? num : undefined;
    }
  }
  return undefined;
};

const collectMeasurement = (results, key, kind) => {
  const out = [];
  for (const item of results) {
    const buckets = [];
    if (kind === "measurements") {
      if (item?.measurements) buckets.push(item.measurements);
      if (item?.biologicalMeasurements) {
        buckets.push(item.biologicalMeasurements);
      }
    } else {
      if (item?.biologicalMeasurements) buckets.push(item.biologicalMeasurements);
    }
    for (const bucket of buckets) {
      const val = pickNumericByKeyCI(bucket, key);
      if (Number.isFinite(val)) out.push(val);
    }
  }
  return out;
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

  return { researchCount: research.size, publicCount: pub.size, total: research.size + pub.size };
};

const ChartView = observer(({ store }) => {
  const sexDistributionData = processData(store.searchResultsAll.map((item) => item.sex).filter((sex) => sex));
  const speciesDistributionData = processData(
    store.searchResultsAll.map((item) => item.taxonomy).filter((species) => species),
  );
  const countryDistributionData = processData(
    store.searchResultsAll.map((item) => item.country).filter((country) => country),
  );

  const weeklyEncounterDates = store
    .calculateWeeklyDates(store.searchResultsAll.map((item) => item.date))
    .map(({ week, count }) => ({ name: week, value: count }));

  const userTypeDistributionData = processData(
    store.searchResultsAll.map((item) => (item.assignedUsername ? "Researcher" : "Public User")),
  );

  const assignedUsers = store.searchResultsAll
    .map((item) => item.assignedUsername)
    .filter((user) => user && user.trim() !== "");
  const assignedUserDistributionData = processData(assignedUsers);

  const stateDistributionData = processData(store.searchResultsAll.map((item) => item.state).filter((state) => state));

  const yearSubmissionData = processData(
    store.searchResultsAll
      .map((item) => (item.date ? new Date(item.date).getFullYear() : null))
      .filter((year) => year),
  ).sort((a, b) => a.name - b.name);

  const discoveryBars = buildDiscoveryBars(store.searchResultsAll);
  const yearlyCumulativeHumanTotals = buildYearlyCumulativeHumanTotals(store.searchResultsAll);
  const topTaggers = buildTopTaggers(store.searchResultsAll);

  const textStats = React.useMemo(() => {
    const rows = store.searchResultsAll || [];

    const numberMatching = rows.length;
    const numberIdentified = rows.reduce((acc, it) => acc + (isIdentified(it) ? 1 : 0), 0);
    const seenIndividuals = new Set();
    for (const it of rows) {
      const indiv = it?.individualId || it?.individualDisplayName;
      if (indiv && String(indiv).toLowerCase() !== "unassigned") {
        seenIndividuals.add(String(indiv).trim());
      }
    }
    const numberMarkedIndividuals = seenIndividuals.size;

    const numberMediaAssets = rows.reduce((acc, it) => acc + countMediaAssets(it), 0);

    const numberAnnotations = rows.reduce((acc, it) => acc + countAnnotations(it), 0);

    const { total: numberContributors, researchCount, publicCount } = accumulateContributors(rows);

    const wtVals = collectMeasurement(rows, "WaterTemperature", "measurements");
    const salVals = collectMeasurement(rows, "Salinity", "measurements");

    const bio13C = collectMeasurement(rows, "13C", "biological");
    const bio15N = collectMeasurement(rows, "15N", "biological");
    const bio34S = collectMeasurement(rows, "34S", "biological");

    const fmt = (arr) => {
      const n = arr.length;
      if (n === 0) return null;
      const m = mean(arr);
      const sd = sampleStd(arr);
      return { n, mean: m, sd };
    };

    return {
      numberMatching,
      numberIdentified,
      numberMarkedIndividuals,
      numberMediaAssets,
      numberAnnotations,
      numberContributors,
      researchContributors: researchCount,
      publicContributors: publicCount,
      wt: fmt(wtVals),
      sal: fmt(salVals),
      c13: fmt(bio13C),
      n15: fmt(bio15N),
      s34: fmt(bio34S),
    };
  }, [store.searchResultsAll]);

  const fmtLine = (label, stat) =>
    stat
      ? `${label}  Mean ${stat.mean.toFixed(2)} (SD ${Number.isFinite(stat.sd) ? stat.sd.toFixed(2) : "—"})  N=${stat.n}`
      : `${label}  No measurement values available.`;

  return (
    <div
      className="container mt-1"
      style={{
        padding: "30px",
        background: "rgba(255, 255, 255, 0.1)",
        backdropFilter: "blur(2px)",
        WebkitBackdropFilter: "blur(2px)",
        color: "white",
        position: "relative",
        borderRadius: "8px",
      }}
    >
      {store.loadingAll && <FullScreenLoader />}
      <h2>
        <FormattedMessage id="CHART_VIEW" />
      </h2>
      <div className="mb-4" style={{ lineHeight: 1.6 }}>
        <h3 className="mb-2"><FormattedMessage id="SUMMARY"/></h3>
        <p><FormattedMessage id="NUMBER_MATCHING_ENCOUNTERS"/>: {textStats.numberMatching}</p>
        <p><FormattedMessage id="NUMBER_IDENTIFIED"/>: {textStats.numberIdentified}</p>
        <p><FormattedMessage id="NUMBER_MARKED_INDIVIDUALS"/>: {textStats.numberMarkedIndividuals}</p>        
        <p><FormattedMessage id="NUMBER_MEDIAASSETS"/>: {textStats.numberMediaAssets}</p>
        <p><FormattedMessage id="NUMBER_ANNOTATION_FROM_MACHINE_LEARNING"/>: {textStats.numberAnnotations}</p>
        <p><FormattedMessage id="NUMBER_DATE_CONTRIBUTORS"/>: {textStats.numberContributors}</p>
        {/* 
        <h4 className="mt-3">Measurements</h4>
        <p>{fmtLine("Mean WaterTemperature", textStats.wt)}</p>
        <p>{fmtLine("Mean Salinity", textStats.sal)}</p>

        <h4 className="mt-3">Biochemical Measurements</h4>
        <p>{fmtLine("Mean 13C", textStats.c13)}</p>
        <p>{fmtLine("Mean 15N", textStats.n15)}</p>
        <p>{fmtLine("Mean 34S", textStats.s34)}</p> */}
      </div>

      <Row className="g-4">
        <Col xs={12} md={6}>
          <Piechart title="STATE_DISTRIBUTION" data={stateDistributionData} />
        </Col>
        <Col xs={12} md={6}>
          <Piechart title="USER_TYPE_DISTRIBUTION" data={userTypeDistributionData} />
        </Col>
      </Row>

      <Row className="g-4 my-2">
        <Col xs={12}>
          <VerticalBarChart title="SEARCH_RESULTS_COUNTRY_DISTRIBUTION" data={countryDistributionData} />
        </Col>
      </Row>

      <Row className="g-4 my-2">
        <Col xs={12}>
          <Piechart title="SEARCH_RESULTS_SEX_DISTRIBUTION" data={sexDistributionData} />
        </Col>
      </Row>

      <Row className="g-4 my-2">
        <Col xs={12}>
          <VerticalBarChart title="SEARCH_RESULTS_ASSIGNED_USER_DISTRIBUTION" data={speciesDistributionData} />
        </Col>
      </Row>

      <Row className="g-4 my-2">
        <Col xs={12}>
          <VerticalBarChart title="SEARCH_RESULTS_SPECIES_DISTRIBUTION" data={assignedUserDistributionData} />
        </Col>
      </Row>

      <Row className="g-4 my-2">
        <Col xs={12}>
          <HorizontalBarChart title="WEEKELY_ENCOUNTER_DATES" data={weeklyEncounterDates} />
        </Col>
      </Row>

      <Row className="g-4 my-2">
        <Col xs={12}>
          <HorizontalBarChart title="ENCOUNTER_BY_YEAR_SUBMITTED" data={yearSubmissionData} />
        </Col>
      </Row>

      <Row className="g-4 my-2">
        <Col xs={12}>
          <HorizontalBarChart title="CURVE_MARKED_INDIVIDUALS_DISCOVERED" data={discoveryBars} />
        </Col>
      </Row>

      <Row className="g-4 my-2">
        <Col xs={12}>
          <HorizontalBarChart title="OVERALL_TOTALS_BY_YEAR" data={yearlyCumulativeHumanTotals} />
        </Col>
      </Row>

      <Row className="g-4 my-2">
        <Col xs={12}>
          <HorizontalBarChart title="TOP_TEN_TAGGERS" data={topTaggers} />
        </Col>
      </Row>
    </div>
  );
});
export default ChartView;
