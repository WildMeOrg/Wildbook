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

export default function UnAuthenticatedSwitch({ showAlert, setShowAlert }) {
  console.log("UnAuthenticatedSwitch", showAlert);

  return (
    <main className="d-flex flex-column">
      <div
        className="position-fixed top-0 mx-auto"
        style={{ maxWidth: "1440px", zIndex: 100, width: "100%" }}
      >
        {showAlert && <AlertBanner setShowAlert={setShowAlert} />}
        <UnAuthenticatedAppHeader
          showAlert={showAlert}
          setShowAlert={setShowAlert}
        />
      </div>
      <div
        className="position-absolute top-0 start-0 justify-content-center"
        style={{
          overflow: "hidden",
          boxSizing: "border-box",
          width: "100%",
          minHeight: "calc(100vh - 64px)", // Assuming the header height is 64px
        }}
      >
        <Routes>
          {/* <Route path="/about" element={<Login />} /> */}
          <Route path="/home" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Login />} />
          <Route path="*" element={<NotFound />} />
        </Routes>
        <Footer />
      </div>
    </main>
  );
}
