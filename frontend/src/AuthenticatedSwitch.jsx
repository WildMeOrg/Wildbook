import React from "react";
import { Routes, Route } from "react-router-dom";
import NotFound from "./pages/errorPages/NotFound";
import Login from "./pages/Login";
import Profile from "./pages/Profile";
import Home from "./pages/Home";
import Footer from "./components/Footer";
import AuthenticatedAppHeader from "./components/AuthenticatedAppHeader";
import UnAuthenticatedAppHeader from "./components/UnAuthenticatedAppHeader";
import useGetMe from "./models/auth/users/useGetMe";
import AlertBanner from "./components/AlertBanner";

export default function AuthenticatedSwitch({ showAlert, setShowAlert }) {
  const { isFetched, data, error } = useGetMe();
  const username = data?.username;
  const avatar = data?.imageURL || "/react/images/Avatar.png";

  return (
    <main>
      <div
        className="position-fixed top-0 mx-auto w-100"
        style={{
          maxWidth: "1440px",
          zIndex: "100",
        }}
      >
        {showAlert && <AlertBanner setShowAlert={setShowAlert} />}
        <AuthenticatedAppHeader
          username={username}
          avatar={avatar}
          showAlert={showAlert}
          setShowAlert={setShowAlert}
        />
      </div>

      <div
        className="position-absolute top-0 start-0 justify-content-center align-items-center overflow-hidden w-100"
        style={{
          boxSizing: "border-box",
          minHeight: "calc(100vh - 40px)", // Assuming the header height is 40px
        }}
      >
        <Routes>
          <Route path="/profile" element={<Profile />} />
          {/* <Route path="/about" element={<About />} /> */}
          <Route path="/home" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Home />} />
          <Route path="*" element={<NotFound />} />
        </Routes>

        <Footer />
      </div>
    </main>
  );
}
