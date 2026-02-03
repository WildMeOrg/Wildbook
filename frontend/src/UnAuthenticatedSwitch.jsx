import React, { lazy, Suspense } from "react";
import { Routes, Route, Navigate, useLocation } from "react-router-dom";
import Footer from "./components/Footer";
import UnAuthenticatedAppHeader from "./components/UnAuthenticatedAppHeader";
import EncounterPageViewOnly from "./pages/Encounter/EncounterPageViewOnly";
import PoliciesAndData from "./pages/PoliciesAndData/PoliciesAndData";
import HowToPhotograph from "./pages/HowToPhotograph";

// Lazy load pages
const Login = lazy(() => import("./pages/Login"));
const Unauthorized = lazy(() => import("./pages/errorPages/Unauthorized"));
const ReportEncounter = lazy(
  () => import("./pages/ReportsAndManagamentPages/ReportEncounter"),
);
const ReportConfirm = lazy(
  () => import("./pages/ReportsAndManagamentPages/ReportConfirm"),
);

export default function UnAuthenticatedSwitch({
  showclassicsubmit,
  showHowToPhotograph,
}) {
  const [header, setHeader] = React.useState(true);
  const location = useLocation();

  const redirParam = encodeURIComponent(
    `${location.pathname}${location.search}${location.hash}`,
  );

  return (
    <div className="d-flex flex-column min-vh-100">
      {/* Header */}
      <div
        id="header"
        className="position-fixed top-0 w-100"
        style={{
          zIndex: "100",
          height: "50px",
          backgroundColor: "#303336",
        }}
      >
        <UnAuthenticatedAppHeader
          showclassicsubmit={showclassicsubmit}
          showHowToPhotograph={showHowToPhotograph}
        />
      </div>

      {/* Main Content */}
      <div
        id="main-content"
        className="flex-grow-1 d-flex justify-content-center"
        style={{
          boxSizing: "border-box",
          overflow: "hidden",
          paddingTop: header ? "48px" : "0",
        }}
      >
        <Suspense fallback={<div>Loading...</div>}>
          <Routes>
            <Route
              path="/home"
              element={<Unauthorized setHeader={setHeader} />}
            />
            <Route path="/encounter" element={<EncounterPageViewOnly />} />
            <Route path="/policies-and-data" element={<PoliciesAndData />} />
            <Route path="/how-to-photograph" element={<HowToPhotograph />} />
            <Route path="/report" element={<ReportEncounter />} />
            <Route path="/reportConfirm" element={<ReportConfirm />} />
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<Login />} />
            <Route
              path="*"
              element={<Navigate to={`/login?redirect=${redirParam}`} />}
            />
          </Routes>
        </Suspense>
      </div>

      <Footer />
    </div>
  );
}
