import React, { useContext, useState, useEffect } from "react";
import { Navbar, Nav, NavDropdown } from "react-bootstrap";
import "../css/dropdown.css";
import { unAuthenticatedMenu } from "../constants/navMenu";
import DownIcon from "./svg/DownIcon";
import Button from "react-bootstrap/Button";
import MultiLanguageDropdown from "./navBar/MultiLanguageDropdown";
import { FormattedMessage } from "react-intl";
import NotificationButton from "./navBar/NotificationButton";
import FooterVisibilityContext from "../FooterVisibilityContext";
import Logo from "./svg/Logo";

export default function AuthenticatedAppHeader({ headerTop }) {
  console.log("headerTop", headerTop);
  const { visible } = useContext(FooterVisibilityContext);

  const [dropdownShows, setDropdownShows] = useState({
    dropdown1: false,
    dropdown2: false,
    dropdown3: false,
  });
  const [dropdownBorder, setDropdownBorder] = useState("2px solid transparent");

  const handleMouseEnter = (id) => {
    setDropdownShows((prev) => ({ ...prev, [id]: true }));
    setDropdownBorder((prev) => ({ ...prev, [id]: "2px solid white" }));
  };

  const handleMouseLeave = (id) => {
    setDropdownShows((prev) => ({ ...prev, [id]: false }));
    setDropdownBorder((prev) => ({ ...prev, [id]: "2px solid transparent" }));
  };

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
        // top: headerTop,
        maxWidth: "1440px",
        marginLeft: "auto",
        marginRight: "auto",
        zIndex: "200",
        width: "100%",
        // '@media (max-width: 600px)': {
        //   top: "80px" 
        // },
        // '@media (min-width: 601px) and (max-width: 1024px)': {
        //   top: "70px" 
        // },
        // '@media (min-width: 1025px)': {
        //   top: "60px" 
        // }
      }}
    >
      <Navbar.Brand
        className="d-flex flex-row align-items-center"
        href="/"
        style={{ marginLeft: "1rem" }}
      >
        <Logo />
        {process.env.SITE_NAME}
      </Navbar.Brand>
      <Navbar.Toggle aria-controls="basic-navbar-nav" />
      <Navbar.Collapse
        id="basic-navbar-nav"
      // style={{ marginLeft: "40%" }}
      >
        <Nav
          className="mr-auto"
          style={{ display: "flex", justifyContent: "flex-end", width: "100%" }}
        >
          {unAuthenticatedMenu.map((item, idx) => (
            <Nav className="me-auto">
              <NavDropdown
                title={
                  <span style={{ color: "white" }}>
                    <FormattedMessage id={Object.keys(item)[0].toUpperCase()} />{" "}
                    <DownIcon />
                  </span>
                }
                id={`basic-nav-dropdown${item}`}
                style={{
                  color: "white",
                  boxSizing: "border-box",
                  borderBottom:
                    dropdownBorder[`dropdown${idx + 1}`] ||
                    "2px solid transparent",
                }}
                onMouseEnter={() => handleMouseEnter(`dropdown${idx + 1}`)}
                onMouseLeave={() => handleMouseLeave(`dropdown${idx + 1}`)}
                show={dropdownShows[`dropdown${idx + 1}`]}
              >
                {Object.values(item)[0].map((subItem, idx) => {
                  return (
                    <NavDropdown.Item
                      href={subItem.href}
                      style={{ color: "black", fontSize: "0.9rem" }}
                    >
                      {subItem.name}
                    </NavDropdown.Item>
                  );
                })}
              </NavDropdown>
            </Nav>
          ))}
        </Nav>
        <MultiLanguageDropdown />
      </Navbar.Collapse>
      <Button
        variant="basic"
        style={{
          backgroundColor: "transparent",
          color: "white",
          border: "none",
          width: "100px",
          whiteSpace: "nowrap",
          padding: 5,
        }}
        href={"/react/login"}
      >
        {<FormattedMessage id="LOGIN_LOGIN" />}
      </Button>
    </Navbar>
  ) : null;
}
