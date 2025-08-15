import React from "react";
import HorizontalBarChart from "../../../components/HorizontalBarChart";
import VerticalBarChart from "../../../components/VerticalBarChart";
import Piechart from "../../../components/Piechart";
import Linechart from "../../../components/Linechart";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Row, Col } from "react-bootstrap";
import FullScreenLoader from "../../../components/FullScreenLoader";

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

  const haploTypesDistributionData = processData(
    store.searchResultsAll.map((item) => item.haplotype).filter(haplo => haplo),
  );

  const assignedUserDistributionData = processData(
    store.searchResultsAll.map((item) => item.assignedUsername).filter(user => user),
  );

  const stateDistributionData = processData(
    store.searchResultsAll.map((item) => item.state).filter(state => state),
  );

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
      { store.loadingAll && <FullScreenLoader/>}
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
            title="SEARCH_RESULTS_HAPLO_TYPES_DISTRIBUTION"
            data={haploTypesDistributionData}
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
        <Col xs={12} md={6}>
          <Piechart
            title="SEARCH_RESULTS_SPECIES_DISTRIBUTION"
            data={speciesDistributionData}
          />
        </Col>
      </Row>
      <VerticalBarChart data={assignedUserDistributionData} />
      <HorizontalBarChart
        title="SEARCH_RESULTS_MEASUREMENTS"
        data={weeklyEncounterDates}
      />
      <Linechart />
    </div>
  );
});

export default ChartView;
