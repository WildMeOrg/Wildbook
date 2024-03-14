import React, { useEffect, useState, useContext} from 'react';
import UnauthenticatedSwitch from './UnAuthenticatedSwitch';
import AuthenticatedSwitch from './AuthenticatedSwitch';
import axios from 'axios';
import AuthContext from './AuthProvider';


// import CreateAdminUser from './pages/setup/CreateAdminUser';

export default function FrontDesk({ adminUserInitialized=true }) {
  // Display a loading spinner while waiting for authentication status from the server.
    const [ isLoggedIn, setIsLoggedIn ] = useState(false);
    const [ error, setError ] = useState();

    const checkLoginStatus = () => {
      // console.log("Polling API...");
      axios.head('/api/v3/user')
        .then(response => {
          // console.log('Success', response);
          setIsLoggedIn(response.status === 200); 
        })
        .catch(error => {
          // console.log('Error', error);
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

//   if (isFetched && !adminUserInitialized) return <CreateAdminUser />;
  // if (isLoggedIn) {
    /* Disable email verification site in development mode.
    Also explicitly checking is_email_confirmed for "false"
    to be safer about locking users out of their accounts
    in case the property isn't present for some reason. */

    return (
      <AuthContext.Provider value={isLoggedIn}>
        <AuthenticatedSwitch   adminUserInitialized/>
      </AuthContext.Provider>
    );
  // }
  // if (error) {
  //   console.log('Error', error);
  //   return <UnauthenticatedSwitch />};

  // return (
  //   <h1>Loading</h1>
  // );
}
