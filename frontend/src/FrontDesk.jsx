import React from 'react';

import UnauthenticatedSwitch from './UnauthenticatedSwitch';
import AuthenticatedSwitch from './AuthenticatedSwitch';

// import useGetMe from './models/users/useGetMe';
// import CreateAdminUser from './pages/setup/CreateAdminUser';

export default function FrontDesk({ adminUserInitialized=true }) {
  // Display a loading spinner while waiting for authentication status from the server.
//   const { isFetched, data, error } = useGetMe();
    const data = {};
    const error = false;

//   if (isFetched && !adminUserInitialized) return <CreateAdminUser />;
  if (data) {
    /* Disable email verification site in development mode.
    Also explicitly checking is_email_confirmed for "false"
    to be safer about locking users out of their accounts
    in case the property isn't present for some reason. */

    return (
      <AuthenticatedSwitch   adminUserInitialized/>
    );
  }
  if (error) return <UnauthenticatedSwitch />;

  return (
    <h1>Error</h1>
  );
}
