import React from "react";
import HorizontalBarChart from "../../../components/HorizontalBarChart";
import VerticalBarChart from "../../../components/VerticalBarChart";
import Piechart from "../../../components/Piechart";
import Linechart from "../../../components/Linechart";

export default function ChartView({ loadingAll }) {

  const data = [];

  if (loadingAll) {
    return (
      <div className="spinner-border spinner-border-sm ms-1" role="status">
        <span className="visually-hidden">Loading...</span>
      </div>
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
        data={data}
        vertical={true}
      />
      <Linechart />
    </div>
  );
}