import React from 'react';
import DimondCard from './DimondCard';
import Tail from './Tail';
import More from './More';

export default function LatestData() {
    return (
        <div className="content col-12"
            style = {{
                display: 'flex',
                flexDirection: 'column',
                justifyContent: 'space-around',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '20px',
                marginTop: '40px',
            
            }}
        >
            <h1 style={{fontSize: 48}}>Latest Data</h1>
            <div style={{
                display: 'flex',
                flexDirection: 'row',
                justifyContent: 'space-around',
                alignItems: 'center',
                justifyContent: 'center',
            }}>
                <DimondCard 
                    date="Aug 05 2023"
                    title="Lorem ipsum"
                    annotations={5}
                    animals={2}
                />
                <DimondCard 
                    date="Aug 05 2023"
                    title="Lorem ipsum"
                    annotations={5}
                    animals={2}
                />
                <DimondCard 
                    date="Aug 05 2023"
                    title="Lorem ipsum"
                    annotations={5}
                    animals={2}
                />
                <More />
            </div>
        </div>
    );
}