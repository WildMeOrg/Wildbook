import React from 'react';
import DiamondCard from '../DiamondCard';
import More from '../CircledMoreButton';
import { formatDate } from '../../utils/formatters';

export default function LatestData({data}) {

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
                {(data?.latestSightings || []).map(sighting => 
                {
                    const formattedDate = formatDate(sighting.dateTime, true);
                    return <DiamondCard 
                        date={formattedDate}
                        title={sighting.taxonomies[0]}
                        annotations={sighting.numberAnnotations}
                        animals={sighting.numberEncounters}
                    />
                }
                    )
                }
                {!data?.latestSightings && <>
                    <DiamondCard 
                    date="Aug 05 2021"
                    title="Fake Species"
                    annotations={5}
                    />
                    <DiamondCard 
                        date="Aug 05 2022"
                        title="Fake Species"
                        annotations={5}
                    />
                    <DiamondCard 
                        date="Aug 05 2023"
                        title="Fake Species"
                        annotations={5}
                    /></>
                }
                
                <More href={"/encounters/searchResults.jsp?null"}/>
            </div>
        </div>
    );
}