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
        maxHeight: "60px",
        padding: 0,
        fontSize: "1rem",
        position: "fixed",
        top: showAlert ? 60 : 0,
        maxWidth: "1440px",
        marginLeft: "auto",
        marginRight: "auto",
        zIndex: "100",
        width: "100%",
        paddingRight: "20px",
      }}
    >
      <Navbar.Brand
        className="d-flex flex-row align-items-center"
        href="/"
        style={{ marginLeft: "1rem", padding: 0 }}
      >
        <Logo />
        {process.env.SITE_NAME}
      </Navbar.Brand>
      <Navbar.Toggle aria-controls="basic-navbar-nav" />
      <Navbar.Collapse id="basic-navbar-nav" style={{ marginLeft: "20%" }}>
        <Nav
          className="mr-auto"
          id="nav"
          style={{
            display: "flex",
            justifyContent: "flex-end",
            width: "100%",
          }}
        >
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
        <AvatarAndUserProfile username={username} avatar={avatar} />
      </Navbar.Collapse>
    </Navbar>
  ) : null;
}
