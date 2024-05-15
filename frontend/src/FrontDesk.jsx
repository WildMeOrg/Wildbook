import React, { useEffect, useState, useContext } from "react";
import UnauthenticatedSwitch from "./UnAuthenticatedSwitch";
import AuthenticatedSwitch from "./AuthenticatedSwitch";
import axios from "axios";
import AuthContext from "./AuthProvider";
import getMergeNotifications from "./models/notifications/getMergeNotifications";
import getCollaborationNotifications from "./models/notifications/getCollaborationNotifications";
import NotFound from "./pages/errorPages/NotFound";
import ServerError from "./pages/errorPages/ServerError";

export default function FrontDesk() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [error, setError] = useState();
  const [collaborationTitle, setCollaborationTitle] = useState();
  const [collaborationData, setCollaborationData] = useState([]);
  const [mergeData, setMergeData] = useState([]);
  const [count, setCount] = useState(0);
  const [showAlert, setShowAlert] = useState(true);

  const checkLoginStatus = () => {
    console.log("Polling API...");
    axios
      .head("/api/v3/user")
      .then((response) => {
        setIsLoggedIn(response.status === 200);
      })
      .catch((error) => {
        console.log("Error", error);
        setIsLoggedIn(false);
        setError(error.response.status);
      });
  };

  const getAllNotifications = async () => {
    const { collaborationTitle, collaborationData } =
      await getCollaborationNotifications();
    const mergeData = await getMergeNotifications();
    const count = collaborationData.length + mergeData.length;
    setCollaborationTitle(collaborationTitle);
    setCollaborationData(collaborationData);
    setMergeData(mergeData);
    setCount(count);
  };

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
      <AuthContext.Provider
        value={{
          isLoggedIn,
          setIsLoggedIn,
          count,
          collaborationTitle,
          collaborationData,
          mergeData,
          getAllNotifications,
        }}
      >
        <AuthenticatedSwitch
          showAlert={showAlert}
          setShowAlert={setShowAlert}
        />
      </AuthContext.Provider>
    );
  }

  if (!isLoggedIn) {
    return (
      <UnauthenticatedSwitch
        showAlert={showAlert}
        setShowAlert={setShowAlert}
      />
    );
  }

  return <h1>Loading</h1>;
}
