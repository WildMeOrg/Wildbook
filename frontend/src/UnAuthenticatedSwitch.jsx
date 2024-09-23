import React from "react";
import { Routes, Route } from "react-router-dom";
import Login from "./pages/Login";
import Footer from "./components/Footer";
import AlertBanner from "./components/AlertBanner";
import UnAuthenticatedAppHeader from "./components/UnAuthenticatedAppHeader";
import NotFound from "./pages/errorPages/NotFound";
import Unauthorized from "./pages/errorPages/Unauthorized";
import Citation from "./pages/Citation";
import UserAccessLog from "./pages/UserAccessLog";

export default function UnAuthenticatedSwitch({ showAlert, setShowAlert }) {
  const [header, setHeader] = React.useState(true);

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
          <Route path="/user-access-log" element={<UserAccessLog />} />
          <Route path="/citation" element={<Citation />} />
          <Route path="/encounter-search" element={<Login />} />
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Login />} />
          <Route path="*" element={<NotFound setHeader={setHeader} />} />
        </Routes>
      </div>
      <Footer />
    </div>
  );
}
