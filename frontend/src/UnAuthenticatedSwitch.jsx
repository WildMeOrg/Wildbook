import React from "react";
import { Routes, Route } from "react-router-dom";
import ErrorPage from "./pages/errorPages/ErrorPage";
import Login from "./pages/Login";
import Footer from "./components/Footer";
import Home from "./pages/Home";
import AlertBanner from "./components/AlertBanner";
import UnAuthenticatedAppHeader from "./components/UnAuthenticatedAppHeader";
import NotFound from "./pages/errorPages/NotFound";
import Forbidden from "./pages/errorPages/Forbidden";
import Unauthorized from "./pages/errorPages/Unauthorized";
import ServerError from "./pages/errorPages/ServerError";
import BadRequest from "./pages/errorPages/BadRequest";
import About from "./About";
import EncounterSearch from "./pages/EncounterSearch";
import { set } from "date-fns";

export default function UnAuthenticatedSwitch({ showAlert, setShowAlert }) {
  console.log("UnAuthenticatedSwitch", showAlert);
  const [header, setHeader] = React.useState(true);

  return (
    <div className="d-flex flex-column min-vh-100">
      <div
        id="header"
        className="position-fixed top-0 mx-auto w-100"
        style={{
          zIndex: "100",
          height: "60px",
          maxWidth: "1440px",
        }}
      >
        {showAlert && <AlertBanner setShowAlert={setShowAlert} />}
        <UnAuthenticatedAppHeader
          showAlert={showAlert}
          setShowAlert={setShowAlert}
        />
      </div>

      <div
        id="main-content"
        className="flex-grow-1 d-flex justify-content-center"
        style={{
          boxSizing: "border-box",
          maxWidth: "1440px",
          overflow: "hidden",
          paddingTop: header? "48px" : "0",
        }}
      >
        <Routes>
          <Route path="/about" element={<About />} />
          <Route path="/home" element={<Unauthorized setHeader={setHeader}/>} />
          <Route path="/encounter-search" element={<EncounterSearch />} />
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Login />} />
          <Route path="*" element={<NotFound setHeader={setHeader}/>} />
        </Routes>
      </div>
      <Footer />

    </div>);
}
