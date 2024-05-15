import React from "react";
import LandingImage from '../components/home/LandingImage';
import LatestData from '../components/home/LatestData';
import PickUp from '../components/home/PickUpWhereYouLeft';
import Report from '../components/home/Report';
import Projects from '../components/home/Projects';
import { useState, useEffect } from 'react';
import useDocumentTitle from "../hooks/useDocumentTitle";
import useGetHomePageInfo from '../models/useGetHomePageInfo';

export default function Home() {

    useDocumentTitle('HOME');
    const { data, loading } = useGetHomePageInfo();
    
    return (
        <>
            <div className="col-12">
                <LandingImage />
                <LatestData data={data?.latestEncounters || []} username={data?.user?.username} loading={loading}/>
                <PickUp data={data} />
                <Report />
                <Projects data={data?.projects} />
            </div>
        </>
    );
}