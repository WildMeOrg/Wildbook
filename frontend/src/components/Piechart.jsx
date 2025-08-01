import React from 'react';
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  Tooltip,
  Legend,
} from 'recharts';

const data = [
  { name: 'Female',  value: 140 },
  { name: 'Male',    value: 35 },
  { name: 'Unknown', value: 25 },
];

const COLORS = ['#165dbe', '#74c0fc', '#37b24d'];

export default function Piechart() {
  return (
    <div style={{
      width: '100%',
      height: 300,
      padding: 16,
    }}>
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
            label={({ percent }) =>
              `${(percent * 100).toFixed(0)}%`
            }
            
          >
            {data.map((entry, idx) => (
              <Cell key={entry.name} fill={COLORS[idx % COLORS.length]} />
            ))}

          </Pie>

          <Tooltip
            formatter={v => v + '%'}
            contentStyle={{
              backgroundColor: '#424242',
              border: 'none',
              borderRadius: 4,
              color: '#fff',
            }}
            itemStyle={{ color: '#fff' }}
          />

          <Legend
            layout="vertical"
            verticalAlign="middle"
            align="right"
            iconType="circle"
            wrapperStyle={{ color: '#fff', paddingLeft: 20 }}
          />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
