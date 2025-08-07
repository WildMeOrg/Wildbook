import React from "react";
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Tooltip,
  Legend,
} from "recharts";
import { FormattedMessage } from "react-intl";

function generateHslColors(count) {
  return Array.from({ length: count }, (_, i) => {
    const hue = (215 + (i * 360) / count) % 360; // Start from blue color
    return `hsl(${hue}, 70%, 50%)`;
  });
}

export default function Piechart({ title = "Sample Pie Chart", data = [] }) {
  const colors = React.useMemo(
    () => generateHslColors(data.length),
    [data.length],
  );


  if (!data || data.length === 0) {
    return (
      <div style={{ width: '100%', height: 300 }}>
        <p><FormattedMessage id={title} /></p>
        <p>No data available</p>
      </div>
    );
  }  

  return (
    <div
      style={{
        width: "100%",
        height: "450px",
      }}
    >
      <p><FormattedMessage id={title} /></p>
      <ResponsiveContainer width="100%" height="100%">

        <PieChart margin={{ top: 0, right: 0, bottom: 80, left: 0 }}>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            cx="50%"
            cy="40%"
            outerRadius={100}
            innerRadius={0}
            labelLine={false}
            label={({ percent }) => `${(percent * 100).toFixed(2)}%`}
          >
            {data.map((entry, idx) => (
              <Cell key={entry.name} fill={colors[idx]} />
            ))}
          </Pie>

          <Tooltip
            formatter={(v) => v}
            contentStyle={{
              backgroundColor: "#424242",
              border: "none",
              borderRadius: 4,
              color: "#fff",
            }}
            itemStyle={{ color: "#fff" }}
          />

          <Legend
            layout="horizontal"
            verticalAlign="bottom"
            align="center"
            iconType="circle"
            wrapperStyle={{ color: "#fff" }}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
