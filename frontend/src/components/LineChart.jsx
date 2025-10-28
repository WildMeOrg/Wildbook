import React from "react";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Label,
} from "recharts";
import { FormattedMessage } from "react-intl";

export default function DiscoveryLineChart({
  title = "Line Chart",
  data = [],
}) {
  if (!data || data.length === 0) {
    return (
      <div style={{ width: "100%", height: 100 }}>
        <p>
          <FormattedMessage id={title} />
        </p>
        <p>No data available</p>
      </div>
    );
  }
  return (
    <div style={{ width: "100%", height: 300 }}>
      <ResponsiveContainer>
        <LineChart
          data={data}
          margin={{ top: 20, right: 30, left: 40, bottom: 40 }}
        >
          <CartesianGrid stroke="rgba(255,255,255,0.1)" strokeWidth={1} />

          <XAxis dataKey="name" stroke="#ccc" tickLine={false} axisLine={false}>
            {/* <Label
              value={title}
              position="insideBottom"
              offset={-10}
              style={{ fill: '#ccc', fontSize: 12 }}
            /> */}
          </XAxis>

          <YAxis stroke="#ccc" tickLine={false} axisLine={false}>
            <Label
              value="Value"
              angle={-90}
              position="insideLeft"
              offset={0}
              style={{ textAnchor: "middle", fill: "#ccc", fontSize: 12 }}
            />
          </YAxis>

          <Tooltip
            cursor={{ stroke: "rgba(255,255,255,0.2)", strokeWidth: 1 }}
            contentStyle={{
              backgroundColor: "#424242",
              border: "none",
              borderRadius: 4,
              color: "#fff",
            }}
            itemStyle={{ color: "#fff" }}
          />

          <Line
            type="monotone"
            dataKey="value"
            stroke="#fff"
            strokeWidth={2}
            dot={{ r: 5, fill: "#fff", strokeWidth: 0 }}
            activeDot={{ r: 7 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
