import React from 'react';
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Label,
} from 'recharts';

export default function DiscoveryLineChart({
  data = [],
  label = "Discovery Line Chart",
}) {
  return (
    <div style={{
      width: '100%',
      height: 300,
      padding: 16,
    }}>
      <ResponsiveContainer>
        <LineChart
          data={data}
          margin={{ top: 20, right: 30, left: 40, bottom: 40 }}
        >
          <CartesianGrid stroke="rgba(255,255,255,0.1)" strokeWidth={1} />

          <XAxis
            dataKey="encounters"
            type="number"
            stroke="#ccc"
            tickLine={false}
            axisLine={false}
          >
            <Label
              value={label}
              position="insideBottom"
              offset={-10}
              style={{ fill: '#ccc', fontSize: 12 }}
            />
          </XAxis>

          <YAxis
            dataKey="count"
            type="number"
            stroke="#ccc"
            tickLine={false}
            axisLine={false}
          >
            <Label
              value="Number or Individual"
              angle={-90}
              position="insideLeft"
              offset={0}
              style={{ textAnchor: 'middle', fill: '#ccc', fontSize: 12 }}
            />
          </YAxis>

          <Tooltip
            cursor={{ stroke: 'rgba(255,255,255,0.2)', strokeWidth: 1 }}
            contentStyle={{
              backgroundColor: '#424242',
              border: 'none',
              borderRadius: 4,
              color: '#fff',
            }}
            itemStyle={{ color: '#fff' }}
            formatter={v => v}
          />

          <Line
            type="monotone"
            dataKey="count"
            stroke="#fff"
            strokeWidth={2}
            dot={{ r: 5, fill: '#fff', strokeWidth: 0 }}
            activeDot={{ r: 7 }}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
