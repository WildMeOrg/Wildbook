import React, { lazy, Suspense } from "react";

import { Routes, Route } from "react-router-dom";
import NotFound from "./pages/errorPages/NotFound";
import AuthenticatedAppHeader from "./components/AuthenticatedAppHeader";
import Footer from "./components/Footer";
import useGetMe from "./models/auth/users/useGetMe";
import HowToPhotograph from "./pages/HowToPhotograph";

// Lazy load pages
const Login = lazy(() => import("./pages/Login"));
const Profile = lazy(() => import("./pages/Profile"));
const Home = lazy(() => import("./pages/Home"));
const EncounterSearch = lazy(
  () => import("./pages/SearchPages/EncounterSearch"),
);
const AdminLogs = lazy(() => import("./pages/AdminLogs"));
const ReportEncounter = lazy(
  () => import("./pages/ReportsAndManagamentPages/ReportEncounter"),
);
const ReportConfirm = lazy(
  () => import("./pages/ReportsAndManagamentPages/ReportConfirm"),
);
const ProjectList = lazy(() => import("./pages/ProjectList"));
const ManualAnnotation = lazy(() => import("./pages/ManualAnnotation"));
const EditAnnotation = lazy(() => import("./pages/EditAnnotation"));

const BulkImport = lazy(() => import("./pages/BulkImport/BulkImport"));
const BulkImportTask = lazy(() => import("./pages/BulkImport/BulkImportTask"));

const Encounter = lazy(() => import("./pages/Encounter/Encounter"));
const PoliciesAndData = lazy(
  () => import("./pages/PoliciesAndData/PoliciesAndData"),
);

export default function AuthenticatedSwitch({
  showclassicsubmit,
  showClassicEncounterSearch,
  showHowToPhotograph,
}) {
  const { data } = useGetMe();
  const username = data?.username;
  const avatar =
    data?.imageURL || `${process.env.PUBLIC_URL}/images/Avatar.png`;
  const [header, setHeader] = React.useState(true);

  return (
    <div className="d-flex flex-column min-vh-100">
      {/* Header */}
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
            <Route path="/profile" element={<Profile />} />
            <Route path="/policies-and-data" element={<PoliciesAndData />} />
            <Route path="/how-to-photograph" element={<HowToPhotograph />} />
            <Route path="/projects/overview" element={<ProjectList />} />
            <Route path="/home" element={<Home />} />
            <Route path="/report" element={<ReportEncounter />} />
            <Route path="/reportConfirm" element={<ReportConfirm />} />
            <Route path="/encounter" element={<Encounter />} />

            <Route path="/encounter-search" element={<EncounterSearch />} />
            <Route path="/admin/logs" element={<AdminLogs />} />
            <Route path="/manual-annotation" element={<ManualAnnotation />} />
            <Route path="/edit-annotation" element={<EditAnnotation />} />
            <Route path="/bulk-import" element={<BulkImport />} />
            <Route path="/bulk-import-task" element={<BulkImportTask />} />
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<Home />} />
            <Route path="*" element={<NotFound setHeader={setHeader} />} />
          </Routes>
        </Suspense>
      </div>

      <Footer />
    </div>
  );
}
