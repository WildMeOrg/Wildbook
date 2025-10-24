import React from "react";
import HorizontalBarChart from "../../../components/HorizontalBarChart";
import VerticalBarChart from "../../../components/VerticalBarChart";
import Piechart from "../../../components/Piechart";
import { observer } from "mobx-react-lite";
import { FormattedMessage } from "react-intl";
import { Row, Col } from "react-bootstrap";
import FullScreenLoader from "../../../components/FullScreenLoader";
import {
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
} from "../buildChartsFunctions";

const Wrapper = ({ children }) => {
  return (
    <div
      className="my-3"
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
      {children}
    </div>
  );
};

const ChartView = observer(({ store }) => {
  const sexDistributionData = processData(
    store.searchResultsAll.map((item) => item.sex).filter((sex) => sex),
  );
  const speciesDistributionData = processData(
    store.searchResultsAll
      .map((item) => item.taxonomy)
      .filter((species) => species),
  );
  const countryDistributionData = processData(
    store.searchResultsAll
      .map((item) => item.country)
      .filter((country) => country),
  );

  const weeklyEncounterDates = store
    .calculateWeeklyDates(store.searchResultsAll.map((item) => item.date))
    .map(({ week, count }) => ({ name: week, value: count }));

  const userTypeDistributionData = processData(
    store.searchResultsAll.map((item) =>
      item.assignedUsername ? "Researcher" : "Public User",
    ),
  );

  const assignedUsers = store.searchResultsAll
    .map((item) => item.assignedUsername)
    .filter((user) => user && user.trim() !== "");
  const assignedUserDistributionData = processData(assignedUsers);

  const stateDistributionData = processData(
    store.searchResultsAll.map((item) => item.state).filter((state) => state),
  );

  const yearSubmissionData = processData(
    store.searchResultsAll
      .map((item) => (item.date ? new Date(item.date).getFullYear() : null))
      .filter((year) => year),
  ).sort((a, b) => a.name - b.name);

  const discoveryBars = buildDiscoveryBars(store.searchResultsAll);
  const yearlyCumulativeHumanTotals = buildYearlyCumulativeHumanTotals(
    store.searchResultsAll,
  );
  const topTaggers = buildTopTaggers(store.searchResultsAll);

  const textStats = React.useMemo(() => {
    const rows = store.searchResultsAll || [];

    const numberMatching = rows.length;
    const numberIdentified = rows.reduce(
      (acc, it) => acc + (isIdentified(it) ? 1 : 0),
      0,
    );
    const seenIndividuals = new Set();
    for (const it of rows) {
      const indiv = it?.individualId || it?.individualDisplayName;
      if (indiv && String(indiv).toLowerCase() !== "unassigned") {
        seenIndividuals.add(String(indiv).trim());
      }
    }
    const numberMarkedIndividuals = seenIndividuals.size;
    const numberMediaAssets = rows.reduce(
      (acc, it) => acc + countMediaAssets(it),
      0,
    );
    const numberAnnotations = rows.reduce(
      (acc, it) => acc + countAnnotations(it),
      0,
    );
    const {
      total: numberContributors,
      researchCount,
      publicCount,
    } = accumulateContributors(rows);

    return {
      numberMatching,
      numberIdentified,
      numberMarkedIndividuals,
      numberMediaAssets,
      numberAnnotations,
      numberContributors,
      researchContributors: researchCount,
      publicContributors: publicCount,
    };
  }, [store.searchResultsAll]);

  const measurementStats = React.useMemo(
    () => buildMeasurementStats(store.searchResultsAll),
    [store.searchResultsAll],
  );

  const bioMeasurementStats = React.useMemo(
    () => buildBioMeasurementStats(store.searchResultsAll),
    [store.searchResultsAll],
  );

  return (
    <div
      className="container mt-1"
      style={{
        backgroundColor: "transparent",
      }}
    >
      {store.loadingAll && <FullScreenLoader />}

      <div className="d-flex flex-row justify-content-between gap-3">
        <Wrapper>
          <>
            <h3 className="mb-2">
              <FormattedMessage id="SUMMARY" />
            </h3>
            <div className="my-3">
              <p>
                <FormattedMessage id="NUMBER_MATCHING_ENCOUNTERS" />:{" "}
                {textStats.numberMatching}
              </p>
              <p>
                <FormattedMessage id="NUMBER_IDENTIFIED" />:{" "}
                {textStats.numberIdentified}
              </p>
              <p>
                <FormattedMessage id="NUMBER_MARKED_INDIVIDUALS" />:{" "}
                {textStats.numberMarkedIndividuals}
              </p>
              <p>
                <FormattedMessage id="NUMBER_MEDIAASSETS" />:{" "}
                {textStats.numberMediaAssets}
              </p>
              <p>
                <FormattedMessage id="NUMBER_ANNOTATION_FROM_MACHINE_LEARNING" />
                : {textStats.numberAnnotations}
              </p>
              <p>
                <FormattedMessage id="NUMBER_DATE_CONTRIBUTORS" />:{" "}
                {textStats.numberContributors}
              </p>
              {bioMeasurementStats.length > 0 ? (
                <div className="mt-4">
                  <h4 className="mb-2">Biochemical Measurements</h4>
                  {bioMeasurementStats.map((row) => (
                    <div key={row.type} className="mb-4">
                      <p>{`Mean ${row.type}  ${fmt(row.overall, row.units)}`}</p>
                      <ul style={{ marginLeft: "1.2rem" }}>
                        <li>{`Mean for males: ${fmt(row.males, row.units)}`}</li>
                        <li>{`Mean for females: ${fmt(row.females, row.units)}`}</li>
                      </ul>
                    </div>
                  ))}
                </div>
              ) : (
                "no biochemical measurements data found"
              )}
            </div>
          </>
        </Wrapper>
        <Wrapper>
          <div>
            {measurementStats.length > 0 && (
              <div className="mt-4">
                <h4 className="mb-2">
                  <FormattedMessage id="MEASUREMENTS" />
                </h4>
                {measurementStats.map((row) => (
                  <div key={row.type} className="mb-4">
                    <p>{`Mean ${row.type}  ${fmt(row.overall, row.units)}`}</p>
                    <ul style={{ marginLeft: "1.2rem" }}>
                      <li>{`Mean for males: ${fmt(row.males, row.units)}`}</li>
                      <li>{`Mean for females: ${fmt(row.females, row.units)}`}</li>
                    </ul>
                  </div>
                ))}
              </div>
            )}
          </div>
        </Wrapper>
      </div>
      <Wrapper>
        <Row className="g-4">
          <Col xs={12} md={6}>
            <Piechart title="STATE_DISTRIBUTION" data={stateDistributionData} />
          </Col>
          <Col xs={12} md={6}>
            <Piechart
              title="USER_TYPE_DISTRIBUTION"
              data={userTypeDistributionData}
            />
          </Col>
        </Row>
      </Wrapper>
      <Wrapper>
        <Row className="g-4 my-3">
          <Col xs={12}>
            <VerticalBarChart
              title="SEARCH_RESULTS_COUNTRY_DISTRIBUTION"
              data={countryDistributionData}
            />
          </Col>
        </Row>
      </Wrapper>
      <Wrapper>
        <Row className="g-4 my-3">
          <Col xs={12}>
            <Piechart
              title="SEARCH_RESULTS_SEX_DISTRIBUTION"
              data={sexDistributionData}
            />
          </Col>
        </Row>
      </Wrapper>
      <Wrapper>
        <Row className="g-4 my-3">
          <Col xs={12}>
            <VerticalBarChart
              title="SEARCH_RESULTS_ASSIGNED_USER_DISTRIBUTION"
              data={speciesDistributionData}
            />
          </Col>
        </Row>
      </Wrapper>
      <Wrapper>
        <Row className="g-4 my-3">
          <Col xs={12}>
            <VerticalBarChart
              title="SEARCH_RESULTS_SPECIES_DISTRIBUTION"
              data={assignedUserDistributionData}
            />
          </Col>
        </Row>
      </Wrapper>
      <Wrapper>
        <Row className="g-4 my-3">
          <Col xs={12}>
            <HorizontalBarChart
              title="WEEKELY_ENCOUNTER_DATES"
              data={weeklyEncounterDates}
            />
          </Col>
        </Row>
      </Wrapper>
      <Wrapper>
        <Row className="g-4 my-3">
          <Col xs={12}>
            <HorizontalBarChart
              title="ENCOUNTER_BY_YEAR_SUBMITTED"
              data={yearSubmissionData}
            />
          </Col>
        </Row>
      </Wrapper>
      <Wrapper>
        <Row className="g-4 my-3">
          <Col xs={12}>
            <HorizontalBarChart
              title="CURVE_MARKED_INDIVIDUALS_DISCOVERED"
              data={discoveryBars}
            />
          </Col>
        </Row>
      </Wrapper>
      <Wrapper>
        <Row className="g-4 my-3">
          <Col xs={12}>
            <HorizontalBarChart
              title="OVERALL_TOTALS_BY_YEAR"
              data={yearlyCumulativeHumanTotals}
            />
          </Col>
        </Row>
      </Wrapper>
      <Wrapper>
        <Row className="g-4 my-3">
          <Col xs={12}>
            <HorizontalBarChart title="TOP_TEN_TAGGERS" data={topTaggers} />
          </Col>
        </Row>
      </Wrapper>
    </div>
  );
});
export default ChartView;
