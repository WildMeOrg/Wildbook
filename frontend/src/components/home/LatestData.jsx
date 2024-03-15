import React from 'react';
import DiamondCard from '../DiamondCard';
import More from '../CircledMoreButton';
import useGetLatestSightings from '../../models/sightings/useGetLatestSightings';

export default function LatestData({data}) {

    const href = '/latest-data';
    // const latestData = useGetLatestSightings();
    // console.log('latestData', latestData);

    // const dateTimeStr = data?.dateTime;
    // console.log('dateTimeStr', dateTimeStr);
    // const date = new Date(dateTimeStr);

    // const options = { year: 'numeric', month: 'long', day: 'numeric' };
    // const formattedDate = new Intl.DateTimeFormat('en-US', options).format(date);

    // console.log(formattedDate); 

    console.log('data', data);
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
                    <DiamondCard 
                        date={sighting.dateTime}
                        title={sighting.taxonomies[0]}
                        annotations={sighting.numberAnnotations}
                        animals={sighting.numberEncounters}
                    />)
                }
                {/* <DiamondCard 
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
                /> */}
                <More href={"/encounters/searchResults.jsp?null"}/>
            </div>
        </div>
    );
}