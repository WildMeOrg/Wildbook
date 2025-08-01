import React from "react";
import HorizontalBarChart from "../../../components/HorizontalBarChart";
import VerticalBarChart from "../../../components/VerticalBarChart";
import Piechart from "../../../components/Piechart";
import Linechart from "../../../components/Linechart";

export default function ChartView({ filteredData = [] }) {
  return (
    <div className="container" style={{
      padding: "1rem",
      background: "rgba(255, 255, 255, 0.1)",
      backdropFilter: "blur(2px)",
      WebkitBackdropFilter: "blur(2px)",
      color: "white",
    }}>
      <h2>Chart View</h2>
      <HorizontalBarChart data={filteredData} />
      <div className="d-flex flex-row justify-content-between">
        <Piechart />
        <Piechart />
      </div>
      <VerticalBarChart
        data={filteredData}
        vertical={true}
      />
      <Linechart />
    </div>
  );
}