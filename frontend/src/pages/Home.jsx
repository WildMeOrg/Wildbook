import React from "react";
import Header from '../components/home/Header';
import LatestData from '../components/home/LatestData';
import PickUp from '../components/home/PickUp';
import Report from '../components/home/Report';
import Projects from '../components/home/Projects';
import Footer from '../components/Footer';
import useFetch from '../hooks/useFetch';
import { useState, useEffect } from 'react';

export default function Home( ) {

    // const result = useFetch({
    //     queryKey: 'home',
    //     url: `/api/v3/home`,
    //     queryOptions: {
    //       retry: 1,
    //       refetchInterval: 5000,
    //       enabled: true,
    //     },
    //   });
    
    //   console.log(result);

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
            console.log(result);
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