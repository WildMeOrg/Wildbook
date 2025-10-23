import React from "react";
import HorizontalBarChart from "../../../components/HorizontalBarChart";
import VerticalBarChart from "../../../components/VerticalBarChart";
import Piechart from "../../../components/Piechart";
import Linechart from "../../../components/Linechart";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Row, Col } from "react-bootstrap";
import FullScreenLoader from "../../../components/FullScreenLoader";
import { PieChart } from "recharts";

const processData = (data) => {
  const counts = data.reduce((acc, curr) => {
    acc[curr] = (acc[curr] || 0) + 1;
    return acc;
  }, {});

  return Object.entries(counts).map(([key, value]) => ({
    name: key,
    value: value,
  }));
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
  if (Array.isArray(item?.submitters) && item.submitters.some(s => String(s).toLowerCase() === needle)) return true;
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

const ChartView = observer(({ store }) => {
  const sexDistributionData = processData(
    store.searchResultsAll.map((item) => item.sex).filter(sex => sex),
  );
  const speciesDistributionData = processData(
    store.searchResultsAll.map((item) => item.taxonomy).filter(species => species),
  );
  const countryDistributionData = processData(
    store.searchResultsAll.map((item) => item.country).filter(country => country),
  );

  const weeklyEncounterDates = store.calculateWeeklyDates(
    store.searchResultsAll.map((item) => item.date),
  ).map(({ week, count }) => ({
    name: week,
    value: count,
  }));

  const userTypeDistributionData = processData(
    store.searchResultsAll.map((item) =>
      item.assignedUsername ? 'Researcher' : 'Public User'
    )
  );

  const assignedUsers = store.searchResultsAll
    .map((item) => item.assignedUsername)
    .filter((user) => user && user.trim() !== "");
  const label = "Number of Encounters at New Individual Discoveries";

  const assignedUserDistributionData = processData(assignedUsers);

  const stateDistributionData = processData(
    store.searchResultsAll.map((item) => item.state).filter(state => state),
  );

  const yearSubmissionData = processData(store.searchResultsAll.map(
    (item) => item.date ? new Date(item.date).getFullYear() : null,
  ).filter(year => year)
  ).sort((a, b) => a.name - b.name);

  const discoveryBars = buildDiscoveryBars(store.searchResultsAll);
  const yearlyCumulativeHumanTotals = buildYearlyCumulativeHumanTotals(
    store.searchResultsAll
  );
  const topTaggers = buildTopTaggers(store.searchResultsAll);

  return (
    <div
      className="container mt-1"
      style={{
        padding: "1rem",
        background: "rgba(255, 255, 255, 0.1)",
        backdropFilter: "blur(2px)",
        WebkitBackdropFilter: "blur(2px)",
        color: "white",
        position: "relative",
      }}
    >
      {store.loadingAll && <FullScreenLoader />}
      <h2>
        <FormattedMessage id="CHART_VIEW" />
      </h2>

      <Row className="g-4">
        <Col xs={12} md={6}>
          <Piechart
            title="SEARCH_RESULTS_STATE_DISTRIBUTION"
            data={stateDistributionData}
          />
        </Col>
        <Col xs={12} md={6}>
          <Piechart
            title="Assigned User Distribution"
            data={assignedUserDistributionData}
          />
        </Col>
      </Row>
      <VerticalBarChart data={countryDistributionData} />
      <Row className="g-4">
        <Col xs={12} md={6}>
          <Piechart
            title="SEARCH_RESULTS_SEX_DISTRIBUTION"
            data={sexDistributionData}
          />
        </Col>
        <VerticalBarChart data={speciesDistributionData} />
      </Row>
      <VerticalBarChart data={userTypeDistributionData} />
      <HorizontalBarChart
        title="weekly_encounter_dates"
        data={weeklyEncounterDates}
      />
      <HorizontalBarChart
        title="Encounters by year submitted"
        data={yearSubmissionData}
      />
      <HorizontalBarChart
        title="Curve of marked individual (cumulative unique individuals by encounter order)"
        data={discoveryBars}
      />

      <HorizontalBarChart
        title="Overall totals by year (human-only cumulative)"
        data={yearlyCumulativeHumanTotals}
      />

      <HorizontalBarChart
        title="Top 10 taggers (unique individuals)"
        data={topTaggers}
      />
    </div>

  );
});

export default ChartView;
