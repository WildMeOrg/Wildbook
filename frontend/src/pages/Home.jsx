import React from "react";
import Header from '../components/home/LandingImage';
import LatestData from '../components/home/LatestData';
import PickUp from '../components/home/PickUp';
import Report from '../components/home/Report';
import Projects from '../components/home/Projects';
import Footer from '../components/Footer';
import useFetch from '../hooks/useFetch';
import { useState, useEffect } from 'react';

export default function Home( ) {

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
    
            // const result = await response.json();
            setData(response); 
            console.log(response);
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
                <Header />
                <LatestData />
                <PickUp />
                <Report />  
                <Projects data={data}/>
                {/* <Footer />                       */}
            </div>        
        </>        
    );
}