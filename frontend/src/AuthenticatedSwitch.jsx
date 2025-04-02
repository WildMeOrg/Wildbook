import React from "react";
import { Routes, Route } from "react-router-dom";
import NotFound from "./pages/errorPages/NotFound";
import Login from "./pages/Login";
import Profile from "./pages/Profile";
import Home from "./pages/Home";
import Footer from "./components/Footer";
import AuthenticatedAppHeader from "./components/AuthenticatedAppHeader";
import useGetMe from "./models/auth/users/useGetMe";
import EncounterSearch from "./pages/SearchPages/EncounterSearch";
import Citation from "./pages/Citation";
import AdminLogs from "./pages/AdminLogs";
import ReportEncounter from "./pages/ReportsAndManagamentPages/ReportEncounter";
import ReportConfirm from "./pages/ReportsAndManagamentPages/ReportConfirm";
import ProjectList from "./pages/ProjectList";
import ManualAnnotation from "./pages/ManualAnnotation";

export default function AuthenticatedSwitch({
  showclassicsubmit,
  showClassicEncounterSearch,
}) {
  const { data } = useGetMe();
  const username = data?.username;
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
        <AuthenticatedAppHeader
          username={username}
          avatar={avatar}
          showclassicsubmit={showclassicsubmit}
          showClassicEncounterSearch={showClassicEncounterSearch}
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
          <Route path="/projects/overview" element={<ProjectList />} />
          <Route path="/home" element={<Home />} />
          <Route path="/report" element={<ReportEncounter />} />
          <Route path="/reportConfirm" element={<ReportConfirm />} />
          <Route path="/encounter-search" element={<EncounterSearch />} />
          <Route path="/admin/logs" element={<AdminLogs />} />
          <Route path="/manual-annotation" element={<ManualAnnotation />} />

          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Home />} />
          <Route path="*" element={<NotFound setHeader={setHeader} />} />
        </Routes>
      </div>

      <Footer />
    </div>
  );
}
