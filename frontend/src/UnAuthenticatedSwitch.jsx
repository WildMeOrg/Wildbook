import React from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import Login from "./pages/Login";
import Footer from "./components/Footer";
import AlertBanner from "./components/AlertBanner";
import UnAuthenticatedAppHeader from "./components/UnAuthenticatedAppHeader";
import Unauthorized from "./pages/errorPages/Unauthorized";
import Citation from "./pages/Citation";

export default function UnAuthenticatedSwitch({ showAlert, setShowAlert }) {
  const [header, setHeader] = React.useState(true);
  const location = useLocation();

  return (
    <div className="d-flex flex-column min-vh-100">
      <div
        id="header"
        className="position-fixed top-0 w-100"
        style={{
          zIndex: "100",
          height: "50px",
          backgroundColor: "#303336",
        }}
      >
        {showAlert && <AlertBanner setShowAlert={setShowAlert} />}
        <UnAuthenticatedAppHeader />
      </div>

      <div
        id="main-content"
        className="flex-grow-1 d-flex justify-content-center"
        style={{
          boxSizing: "border-box",
          overflow: "hidden",
          paddingTop: header ? "48px" : "0",
        }}
      >
        <Routes>
          <Route
            path="/home"
            element={<Unauthorized setHeader={setHeader} />}
          />
          <Route path="/citation" element={<Citation />} />
          
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Login />} />
          <Route
            path = "*"
            element={<Navigate to={`/login?redirect=${location.pathname}`}/>}
          />
        </Routes>
      </div>
      <Footer />
    </div>
  );
}
