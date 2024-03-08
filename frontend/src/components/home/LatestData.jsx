import React from 'react';
import DiamondCard from '../DiamondCard';
import More from '../CircledMoreButton';

export default function LatestData() {

    const href = '/latest-data';
    
    return (
        <div className="content col-12"
            style = {{
                display: 'flex',
                flexDirection: 'column',
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
                <DiamondCard 
                    date="Aug 05 2021"
                    title="Lorem ipsum"
                    annotations={5}
                    animals={2}
                />
                <DiamondCard 
                    date="Aug 05 2022"
                    title="Lorem ipsum"
                    annotations={5}
                    animals={2}
                />
                <DiamondCard 
                    date="Aug 05 2023"
                    title="Lorem ipsum"
                    annotations={5}
                    animals={2}
                />
                <More href={"/encounters/searchResults.jsp?null"}/>
            </div>
        </div>
    );
}