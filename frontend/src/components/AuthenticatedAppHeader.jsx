import React, { useContext, useEffect, useState } from "react";
import { Navbar, Nav } from "react-bootstrap";
import "../css/dropdown.css";
import NotificationButton from "./navBar/NotificationButton";
import MultiLanguageDropdown from "./navBar/MultiLanguageDropdown";
import AuthContext from "../AuthProvider";
import AvatarAndUserProfile from "./header/AvatarAndUserProfile";
import { debounce } from "lodash";
import Menu from "./header/Menu";
import FooterVisibilityContext from "../FooterVisibilityContext";
import Logo from "./svg/Logo";

export default function AuthenticatedAppHeader({
  username,
  avatar,
  showAlert,
}) {
  const { visible } = useContext(FooterVisibilityContext);

  const {
    count,
    collaborationTitle,
    collaborationData,
    mergeData,
    getAllNotifications,
  } = useContext(AuthContext);

  return visible ? (
    <Navbar
      variant="dark"
      expand="lg"
      style={{
        backgroundColor: "#303336",
        height: "60px",
        padding: 0,
        fontSize: "1rem",
        position: "fixed",
        // top: showAlert ? 60 : 0,
        // maxWidth: "1440px",
        marginLeft: "auto",
        marginRight: "auto",
        zIndex: "200",
        width: "100%",
      }}
    >
      <Navbar.Brand
        className="d-flex flex-row align-items-center"
        href="/"
        style={{ marginLeft: "10%", padding: 0 }}
      >
        <Logo />
        {process.env.SITE_NAME}
      </Navbar.Brand>
      <Navbar.Toggle aria-controls="basic-navbar-nav"
        style={{
          marginRight: "10%"
        }}
      />
      <Navbar.Collapse id="basic-navbar-nav">
        <Nav className="mr-auto w-100 d-flex justify-content-end" id="nav">
          <Menu username={username} />
        </Nav>
        <NotificationButton
          collaborationTitle={collaborationTitle}
          collaborationData={collaborationData}
          count={count}
          mergeData={mergeData}
          getAllNotifications={getAllNotifications}
        />
        <MultiLanguageDropdown />
        {/* <AvatarAndUserProfile username={username} avatar={avatar} /> */}
      </Navbar.Collapse>
      <div
        className="avatar-container d-flex align-items-center"
        style={{ marginRight: "10%" }}
      >
        <AvatarAndUserProfile username={username} avatar={avatar} />
      </div>
    </Navbar>
  ) : null;
}
