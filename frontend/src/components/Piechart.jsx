import React from "react";
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Tooltip,
  Legend,
} from "recharts";

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

  return (
    <div
      style={{
        width: "100%",
        height: 300,
      }}
    >
      <p style={{ textAlign: "center", color: "white" }}>{title}</p>
      <ResponsiveContainer>
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            cx="50%"
            cy="50%"
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
            layout="vertical"
            verticalAlign="middle"
            align="right"
            iconType="circle"
            wrapperStyle={{ color: "#fff" }}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
