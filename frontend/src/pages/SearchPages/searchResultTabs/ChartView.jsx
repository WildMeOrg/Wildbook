import React from "react";
import HorizontalBarChart from "../../../components/HorizontalBarChart";
import VerticalBarChart from "../../../components/VerticalBarChart";
import Piechart from "../../../components/Piechart";
import Linechart from "../../../components/Linechart";
import { observer } from "mobx-react-lite";

const ChartView = observer(({ store }) => {

  const measurementsData = store.searchResultsAll.map((item) => item.measurements || []);

  if (store.loadingAll) {
    return (
      <h3 style={{ color: "white" }} >
        Loading...
      </h3>
    );
  }

  return (
    <div className="container" style={{
      padding: "1rem",
      background: "rgba(255, 255, 255, 0.1)",
      backdropFilter: "blur(2px)",
      WebkitBackdropFilter: "blur(2px)",
      color: "white",
    }}>
      <h2>Chart View</h2>
      <HorizontalBarChart />
      <div className="d-flex flex-row justify-content-between">
        <Piechart />
        <Piechart />
      </div>
      <VerticalBarChart
        data={measurementsData}
        vertical={true}
      />
      <Linechart />
    </div>
  );
})

export default ChartView;