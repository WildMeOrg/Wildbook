import React from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import Login from "./pages/Login";
import Footer from "./components/Footer";
import AlertBanner from "./components/AlertBanner";
import UnAuthenticatedAppHeader from "./components/UnAuthenticatedAppHeader";
import Unauthorized from "./pages/errorPages/Unauthorized";
import Citation from "./pages/Citation";
import ReportEncounter from "./pages/ReportsAndManagamentPages/ReportEncounter";
import ReportConfirm from "./pages/ReportsAndManagamentPages/ReportConfirm";

export default function UnAuthenticatedSwitch({ showAlert, setShowAlert, showclassicsubmit }) {
  const [header, setHeader] = React.useState(true);
  const location = useLocation();
  console.log("showclassicsubmit", showclassicsubmit);

  const redirParam = encodeURIComponent(
    `${location.pathname}${location.search}${location.hash}`,
  );

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
        <UnAuthenticatedAppHeader showclassicsubmit={showclassicsubmit}/>
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
          <Route path="/report" element={<ReportEncounter />} />
          <Route path="/reportConfirm" element={<ReportConfirm />} />
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Login />} />
          <Route
            path="*"
            element={<Navigate to={`/login?redirect=${redirParam}`} />}
          />
        </Routes>
      </div>
      <Footer />
    </div>
  );
}
