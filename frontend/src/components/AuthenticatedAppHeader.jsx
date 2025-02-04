import React, { useContext } from "react";
import { Navbar, Nav } from "react-bootstrap";
import "../css/dropdown.css";
import NotificationButton from "./navBar/NotificationButton";
import MultiLanguageDropdown from "./navBar/MultiLanguageDropdown";
import AuthContext from "../AuthProvider";
import AvatarAndUserProfile from "./header/AvatarAndUserProfile";
import Menu from "./header/Menu";
import FooterVisibilityContext from "../FooterVisibilityContext";
import Logo from "./svg/Logo";
import HeaderQuickSearch from "./header/HeaderQuickSearch";

export default function AuthenticatedAppHeader({
  username,
  avatar,
  showclassicsubmit,
  showClassicEncounterSearch,
}) {
  const { visible } = useContext(FooterVisibilityContext);

  const {
    count,
    collaborationTitle,
    collaborationData,
    mergeData,
    getAllNotifications,
  } = useContext(AuthContext);

  return (
    <div
      className="w-100"
      style={{
        backgroundColor: "#303336",
        height: "50px",
      }}
    >
      <div
        className="container"
        style={{
          height: "50px",
          paddingLeft: 0,
          paddingRight: "5%",
        }}
      >
        {visible ? (
          <Navbar
            variant="dark"
            expand="lg"
            style={{
              backgroundColor: "#303336",
              height: "50px",
              padding: 0,
              fontSize: "1rem",
              marginLeft: "auto",
              marginRight: "auto",
              zIndex: "200",
            }}
          >
            <Navbar.Brand
              className="d-flex flex-row align-items-center"
              href="/"
            >
              <Logo />
              <span className="site-name">{process.env.SITE_NAME}</span>
            </Navbar.Brand>
            <Navbar.Toggle aria-controls="basic-navbar-nav" />
            <Navbar.Collapse id="basic-navbar-nav">
              <Nav
                id="nav"
                className="mr-auto"
                style={{
                  display: "flex",
                  marginLeft: "auto",
                }}
              >
                <Menu
                  username={username}
                  showclassicsubmit={showclassicsubmit}
                  showClassicEncounterSearch={showClassicEncounterSearch}
                />
              </Nav>
              <HeaderQuickSearch />
              <NotificationButton
                collaborationTitle={collaborationTitle}
                collaborationData={collaborationData}
                count={count}
                mergeData={mergeData}
                getAllNotifications={getAllNotifications}
              />
              <MultiLanguageDropdown />
            </Navbar.Collapse>
            <div className="avatar-container d-flex align-items-center">
              <AvatarAndUserProfile username={username} avatar={avatar} />
            </div>
          </Navbar>
        ) : null}
      </div>
    </div>
  );
}
