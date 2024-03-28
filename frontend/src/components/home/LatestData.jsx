import React from 'react';
import DiamondCard from '../DiamondCard';
import More from '../CircledMoreButton';
import { formatDate } from '../../utils/formatters';

export default function LatestData({data, username}) {
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
                {data.map(sighting => 
                {
                    const formattedDate = formatDate(sighting.date, true) || sighting.dateTime;
                    return <DiamondCard 
                        date={formattedDate}
                        title={sighting.taxonomy}
                        annotations={sighting.numberAnnotations}
                    />
                }
                    )
                }
                
                <More href={`/encounters/searchResults.jsp?username=${username}`}/>
            </div>
        </div>
    );
}