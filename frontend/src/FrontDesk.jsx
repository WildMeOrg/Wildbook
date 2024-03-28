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

  console.log('isLoggedIn', isLoggedIn);

  if (isLoggedIn) {
    console.log('isLoggedIn', isLoggedIn);
    return (
      <AuthContext.Provider value={{ isLoggedIn, setIsLoggedIn }}>
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
