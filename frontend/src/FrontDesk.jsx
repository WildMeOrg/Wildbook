import React, { useEffect, useState, useContext } from 'react';
import UnauthenticatedSwitch from './UnAuthenticatedSwitch';
import AuthenticatedSwitch from './AuthenticatedSwitch';
import axios from 'axios';
import AuthContext from './AuthProvider';
import ThemeProvider from './ThemeProvider';

export default function FrontDesk() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [error, setError] = useState();

  const checkLoginStatus = () => {
    console.log("Polling API...");
    axios.head('/api/v3/user')
      .then(response => {
        setIsLoggedIn(response.status === 200);
      })
      .catch(error => {
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
    console.log(111111111111111);
    return (
      <AuthContext.Provider value={isLoggedIn}>
         {/* <ThemeProvider>           */}
          <AuthenticatedSwitch />
         {/* </ThemeProvider> */}
      </AuthContext.Provider>
    );
  }
  if (error) {
    console.log('Error', error);
    return <UnauthenticatedSwitch />
  };
  console.log(3333333333333);
  return (    
    <h1>Loading</h1>
  );
}
