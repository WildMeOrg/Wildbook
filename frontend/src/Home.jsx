import React from "react";
import Header from './components/home/Header';
import LatestData from './components/home/LatestData';
import PickUp from './components/home/PickUp';
import Report from './components/home/Report';

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