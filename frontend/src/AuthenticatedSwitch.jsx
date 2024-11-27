import React from "react";
import { Routes, Route } from "react-router-dom";
import NotFound from "./pages/errorPages/NotFound";
import Login from "./pages/Login";
import Profile from "./pages/Profile";
import Home from "./pages/Home";
import Footer from "./components/Footer";
import AuthenticatedAppHeader from "./components/AuthenticatedAppHeader";
import useGetMe from "./models/auth/users/useGetMe";
import AlertBanner from "./components/AlertBanner";
import EncounterSearch from "./pages/EncounterSearch";
import Citation from "./pages/Citation";
import AdminLogs from "./pages/AdminLogs";
import ReportEncounter from "./pages/ReportsAndManagamentPages/ReportEncounter";
import ReportConfirm from "./pages/ReportsAndManagamentPages/ReportConfirm";

export default function AuthenticatedSwitch({ showAlert, setShowAlert, showclassicsubmit }) {
  const { data } = useGetMe();
  const username = data?.username;
  // eslint-disable-next-line no-undef
  const avatar =
    data?.imageURL || `${process.env.PUBLIC_URL}/images/Avatar.png`;
  const [header, setHeader] = React.useState(true);

  return (
    <div className="d-flex flex-column min-vh-100">
      <div
        id="header"
        className="position-fixed top-0 mx-auto w-100"
        style={{
          zIndex: "100",
          height: "50px",
          backgroundColor: "#303336",
        }}
      >
        {showAlert && <AlertBanner setShowAlert={setShowAlert} />}
        <AuthenticatedAppHeader
          username={username}
          avatar={avatar}
          showAlert={showAlert}
          setShowAlert={setShowAlert}
          showclassicsubmit={showclassicsubmit}
        />
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
          <Route path="/profile" element={<Profile />} />
          <Route path="/citation" element={<Citation />} />
          <Route path="/home" element={<Home />} />
          <Route path="/report" element={<ReportEncounter />} />
          <Route path="/reportConfirm" element={<ReportConfirm />} />
          <Route path="/encounter-search" element={<EncounterSearch />} />
          <Route path="/admin/logs" element={<AdminLogs />} />
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Home />} />
          <Route path="*" element={<NotFound setHeader={setHeader} />} />
        </Routes>
      </div>

      <Footer />
    </div>
  );
}
