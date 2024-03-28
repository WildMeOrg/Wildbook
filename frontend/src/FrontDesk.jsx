import React, { useEffect, useState, useContext } from 'react';
import UnauthenticatedSwitch from './UnAuthenticatedSwitch';
import AuthenticatedSwitch from './AuthenticatedSwitch';
import axios from 'axios';
import AuthContext from './AuthProvider';
// import ThemeProvider from './ThemeProvider';

export default function FrontDesk() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [notificationTitle, setNotificationTitle] = useState();
  const [notificationData, setNotificationData] = useState([]);
  const [error, setError] = useState();
  
  const getNotifications = async () => {
    try {
      const response = await fetch(`/Collaborate?json=1&getNotifications=1`);   
      const data = await response.json();   
      const parser = new DOMParser();
      const doc = parser.parseFromString(data?.content, 'text/html');
      const title = doc.querySelector('h2')?.innerText;
      if(title) {
        setNotificationTitle(title);
        const invites = [...doc.querySelectorAll('.collaboration-invite-notification')];     
        setNotificationData(invites);        
      }else {        
        setNotificationTitle('');
        setNotificationData([]);
        console.log('No title found');
      };
      console.log(data);
    } catch (error) {
      console.error('Error:', error); 
    } finally {
    }
  };

  useEffect(() => {
    getNotifications();
  }, []);

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
        notificationTitle,       
        notificationData,
        getNotifications,
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
