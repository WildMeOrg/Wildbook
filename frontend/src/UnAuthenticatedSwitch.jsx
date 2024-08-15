import React, { useEffect } from "react";
import { Routes, Route } from "react-router-dom";
import Login from "./pages/Login";
import Footer from "./components/Footer";
import AlertBanner from "./components/AlertBanner";
import UnAuthenticatedAppHeader from "./components/UnAuthenticatedAppHeader";
import NotFound from "./pages/errorPages/NotFound";
import Unauthorized from "./pages/errorPages/Unauthorized";
import About from "./About";
import EncounterSearch from "./pages/EncounterSearch";
import Home from "./pages/Home";

export default function UnAuthenticatedSwitch({ showAlert, setShowAlert }) {
  const [header, setHeader] = React.useState(true);
  const [headerTop, setHeaderTop] = React.useState("60px");
  const alertBannerRef = React.useRef(null);

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
        {showAlert && <AlertBanner
          setShowAlert={setShowAlert} />}
        <UnAuthenticatedAppHeader/>
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
          {/* <Route path="/about" element={<About />} /> */}
          <Route path="/home" element={<Unauthorized setHeader={setHeader} />} />
          {/* <Route path="/encounter-search" element={<EncounterSearch />} /> */}
          <Route path="/encounter-search" element={<Login />} />
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Login />} />
          <Route path="*" element={<NotFound setHeader={setHeader} />} />
        </Routes>
      </div>
      <Footer />

    </div>);
}
