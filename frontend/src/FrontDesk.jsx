import React, { useEffect, useState, useContext } from 'react';
import UnauthenticatedSwitch from './UnAuthenticatedSwitch';
import AuthenticatedSwitch from './AuthenticatedSwitch';
import axios from 'axios';
import AuthContext from './AuthProvider';
// import ThemeProvider from './ThemeProvider';
import getMergeNotifications from './models/notifications/getMergeNotifications';
import getCollaborationNotifications from './models/notifications/getCollaborationNotifications';

export default function FrontDesk() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [error, setError] = useState();
  const [collaborationTitle, setCollaborationTitle] = useState();
  const [collaborationData, setCollaborationData] = useState([]);
  const [mergeData, setMergeData] = useState([]);
  const [count, setCount] = useState(0);


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

  const getAllNotifications = async () => {
    const { collaborationTitle, collaborationData } = await getCollaborationNotifications();
    const mergeData = await getMergeNotifications();
    const count = collaborationData.length + mergeData.length;
    setCollaborationTitle(collaborationTitle);
    setCollaborationData(collaborationData);
    setMergeData(mergeData);
    setCount(count);
  }

  useEffect(() => {      
    getAllNotifications();  
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
        count,
        collaborationTitle,
        collaborationData,
        mergeData,
        getAllNotifications,
      }}>
        {/* <ThemeProvider>           */}
        <AuthenticatedSwitch isLoggedIn={isLoggedIn} />
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
