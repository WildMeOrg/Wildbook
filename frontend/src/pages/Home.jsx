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
        try {
            const response = await fetch('/api/v3/home', {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json',
                },
            });

            if (!response.ok) {
                throw new Error('Network response was not ok');
            }
            const result = await response.json();
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
                <LatestData data={data.latestEncounters || []} username={data?.user?.username} />
                <PickUp data={data} />
                <Report />
                <Projects data={data.projects} />
            </div>
        </>
    );
}