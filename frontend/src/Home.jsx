import React from "react";
import Header from './components/Header';
import LatestData from './components/LatestData';
import PickUp from './components/PickUp';
import Report from './components/Report';

export default function Home() {

    return (
        <>
           <div className="col-12">
        
            <Header />
            <LatestData />
            <PickUp />
            <Report />            
        
            </div>
        
        </>
        
    );
}