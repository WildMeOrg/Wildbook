import React from 'react';
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  LabelList,
} from 'recharts';
import { FormattedMessage } from 'react-intl';

export default function HorizontalBarChart({
  data = [],
  title = "VERTICAL_BAR_CHART",
}) {
  const total = React.useMemo(
    () => data.length ? data.reduce((sum, item) => sum + item.value, 0) : 0,
    [data]
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
    <div style={{ width: '100%', height: 400 }}>
      <p><FormattedMessage id={title} /></p>
      <ResponsiveContainer width="100%" height={400}>
        <BarChart
          data={data}
          layout="vertical"
          margin={{ top: 20, right: 40, left: 60, bottom: 20 }}
        >
          <CartesianGrid stroke="rgba(255,255,255,0.1)" />
          <XAxis
            type="number"
            stroke="#ccc"
            unit=""
            tickLine={false}
            axisLine={false}
          />
          <YAxis
            dataKey="name"
            type="category"
            stroke="#ccc"
            width={120}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            formatter={v => v}
            cursor={{ fill: 'rgba(255,255,255,0.1)' }}
          />
          <Bar dataKey="value" fill="#74c0fc" barSize={20} radius={[0, 10, 10, 0]}>
            <LabelList
              dataKey="value"
              position="right"
              formatter={v => `${(v / total * 100).toFixed(2)}%`}
              fill="#fff"
              fontSize={12}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
