import React, { useEffect, useState, useContext } from 'react';
import UnauthenticatedSwitch from './UnAuthenticatedSwitch';
import AuthenticatedSwitch from './AuthenticatedSwitch';
import axios from 'axios';
import AuthContext from './AuthProvider';
// import ThemeProvider from './ThemeProvider';
import getMergeNotifications from './models/notifications/getMergeNotifications';
import getCollaborationNotifications from './models/notifications/getCollaborationNotifications';
import { merge } from 'lodash-es';


export default function FrontDesk() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [error, setError] = useState();  
  const [collaborationTitle, setCollaborationTitle] = useState();
  const [collaborationData, setCollaborationData] = useState([]);
  const [mergeData, setMergeData] = useState([]);

  // const { collaborationTitle, collaborationData } = getCollaborationNotifications();
  // const { notifications: mergeData, error: mergeError, loading: mergeLoading } = getMergeNotifications();

  console.log('FrontDesk collaborationData:', collaborationData, mergeData);
  useEffect(() => {
    const fetchData = async () => {
      const { collaborationTitle, collaborationData } = await getCollaborationNotifications();
      const mergeData = await getMergeNotifications();
      setCollaborationTitle(collaborationTitle);
      setCollaborationData(collaborationData);
      setMergeData(mergeData);
    };
  
    fetchData();
  }, []); // 

  const checkLoginStatus = () => {
    console.log("Polling API...");
    axios.head('/api/v3/user')
      .then(response => {
        setIsLoggedIn(response.status === 200);
      })
      .catch(error => {
        console.log('Error', error);
        setIsLoggedIn(false);
        setError(error);
      });
  };
  
  useEffect(() => {
    checkLoginStatus();
    const intervalId = setInterval(() => {
      checkLoginStatus();
    }, 60000);

    return () => clearInterval(intervalId);
  }, []);

  if (isLoggedIn) {
    return (
      <AuthContext.Provider value={{ 
        isLoggedIn, 
        setIsLoggedIn, 
        collaborationTitle,       
        collaborationData,
        mergeData
         }}>
         {/* <ThemeProvider>           */}
          <AuthenticatedSwitch isLoggedIn={isLoggedIn}/>
         {/* </ThemeProvider> */}
      </AuthContext.Provider>
    );
  }
  if (error || !isLoggedIn) {
    console.log('Error', error);
    return <UnauthenticatedSwitch />
  };

  return (    
    <h1>Loading</h1>
  );
}
