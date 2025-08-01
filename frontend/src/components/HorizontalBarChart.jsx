import React from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

const data = [
    { name: 'Kiribati', value: 32 },
    { name: 'Montserrat', value: 27 },
];

export default function HorizontalBarChart({
    // data = [],
}) {

    return (<ResponsiveContainer width="100%" height={300} >
        <BarChart
            data={data}
            margin={{
                top: 5,
                right: 30,
                left: 20,
                bottom: 5,
            }}>
            <XAxis dataKey="name" stroke="#aaa" tickLine={false} 
          axisLine={false}  />
            <YAxis stroke="#ccc" unit="" tickMargin={52} tickLine={false} 
          axisLine={false} />
            <Tooltip formatter={v => v} cursor={false} />
            <CartesianGrid stroke="rgba(255,255,255,0.2)" strokeWidth={1} strokeDasharray="" />
            <Bar dataKey="value" fill="#74c0fc" barSize={20} radius={[6, 6, 0, 0]} />
        </BarChart>
    </ResponsiveContainer>)
}