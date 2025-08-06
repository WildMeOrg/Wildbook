import React from "react";
import HorizontalBarChart from "../../../components/HorizontalBarChart";
import VerticalBarChart from "../../../components/VerticalBarChart";
import Piechart from "../../../components/Piechart";
import Linechart from "../../../components/Linechart";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Row, Col } from "react-bootstrap";

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

const ChartView = observer(({ store }) => {
  const measurementsData = store.searchResultsAll.map(
    (item) => item.measurements || [],
  );
  const sexDistributionData = processData(
    store.searchResultsAll.map((item) => item.sex || "unknown"),
  );
  const speciesDistributionData = processData(
    store.searchResultsAll.map((item) => item.taxonomy || "unknown"),
  );
  const countryDistributionData = processData(
    store.searchResultsAll.map((item) => item.country || "unknown"),
  );

  const weeklyEncounterDates = store.calculateWeeklyDates(
    store.searchResultsAll.map((item) => item.date),
  ).map(({ week, count }) => ({
    name: week,
    value: count,
  }));

  if (store.loadingAll) {
    return (
      <h3 style={{ color: "white" }}>
        <FormattedMessage id="LOADING" />
      </h3>
    );
  }

  return (
    <div
      className="container"
      style={{
        padding: "1rem",
        background: "rgba(255, 255, 255, 0.1)",
        backdropFilter: "blur(2px)",
        WebkitBackdropFilter: "blur(2px)",
        color: "white",
      }}
    >
      <h2>
        <FormattedMessage id="CHART_VIEW" />
      </h2>

      <Row className="g-4">
        <Col xs={12} md={6}>
          <Piechart
            title="SEARCH_RESULTS_SEX_DISTRIBUTION"
            data={sexDistributionData}
          />
        </Col>
        <Col xs={12} md={6}>
          <Piechart
            title="SEARCH_RESULTS_SPECIES_DISTRIBUTION"
            data={speciesDistributionData}
          />
        </Col>
      </Row>
      <VerticalBarChart data={countryDistributionData} />
      <HorizontalBarChart
        title="SEARCH_RESULTS_MEASUREMENTS"
        data={weeklyEncounterDates}
      />
      <Linechart />
    </div>
  );
});

export default ChartView;
