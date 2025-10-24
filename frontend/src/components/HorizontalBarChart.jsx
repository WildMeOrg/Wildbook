import React from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { FormattedMessage } from 'react-intl';

export default function HorizontalBarChart({
    title = "HORIZONTAL_BAR_CHART",
    data = [],
}) {
    if (!data || data.length === 0) {
        return (
            <div className="d-flex align-items-center justify-content-center " style={{ width: '100%', height: 300 }}>
                <p><FormattedMessage id={title} /></p>
                <p>No data available</p>
            </div>
        );
    }
    return (
        <div className="my-3"
            style={{ height: 300, marginTop: 20, width: "100%" }}>
            <p><FormattedMessage id={title} /></p>
            <ResponsiveContainer width="100%" height={300} >
                <BarChart
                    data={data}
                    margin={{
                        top: 5,
                        right: 30,
                        left: 20,
                        bottom: 5,
                    }}>
                    <XAxis dataKey="name" stroke="#aaa" tickLine={false}
                        axisLine={false} />
                    <YAxis stroke="#ccc" unit="" tickMargin={52} tickLine={false}
                        axisLine={false} />
                    <Tooltip formatter={v => v} cursor={false} />
                    <CartesianGrid stroke="rgba(255,255,255,0.2)" strokeWidth={1} strokeDasharray="" />
                    <Bar dataKey="value" fill="#74c0fc" barSize={20} radius={[6, 6, 0, 0]} />
                </BarChart>
            </ResponsiveContainer>
        </div>
    )
}