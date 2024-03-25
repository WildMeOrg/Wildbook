import React from "react";
import LandingImage from '../components/home/LandingImage';
import LatestData from '../components/home/LatestData';
import PickUp from '../components/home/PickUpWhereYouLeft';
import Report from '../components/home/Report';
import Projects from '../components/home/Projects';
import { useState, useEffect } from 'react';
import useDocumentTitle from "../hooks/useDocumentTitle";

export default function Home() {

    useDocumentTitle('HOME');
    const [data, setData] = useState([]);

    async function fetchData() {
        console.log('fetching data');
        try {
            const response = await fetch('/api/v3/home', {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });
            console.log(response);

            if (!response.ok) {
                throw new Error('Network response was not ok');
            }

            const result = await response.json();
            // result.latestBulkImportTask = {
            //     "id": "task_12345",
            //             "dateTimeCreated": "2023-03-20T10:00:00Z",
            //             "numberEncounters": 15,
            //             "numberMediaAssets": 10
            //                     };
            // result.latestBulkImportIndividual = {
            //     "id": "indiv_67890",
            //     "dateTimeCreated": "2023-03-18T08:30:00Z"
            // }
            // result.latestMatchTask = {
            //     "id": "match_54321",
            //     "dateTimeCreated": "2023-03-19T09:45:00Z",
            //     "encounterId": "enc_98765",
            // }
            console.log(result);
            setData(result);
        } catch (error) {
            console.error('There has been a problem with your fetch operation:', error);
        }
    }

    useEffect(() => {
        fetchData();
    }, []);

    return (
        <>
            <div className="col-12">
                <LandingImage />
                <LatestData data={data.latestEncounters || []} username={data?.user?.displayName}/>
                <PickUp data={data}/>
                <Report />
                <Projects data={data.projects} />
            </div>
        </>
    );
}