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

export default function AuthenticatedAppHeader() {
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

  return <div className="container" style={{ height: "60px" }}>
    {
      visible ? (
        <Navbar
          variant="dark"
          expand="lg"
          style={{
            backgroundColor: "#303336",
            height: "50px",
            padding: 0,
            fontSize: "1rem",
            // position: "fixed",
            zIndex: "200",
            // width: "100%",
    
          }}
        >
          <Navbar.Brand
            className="d-flex flex-row align-items-center"
            href="/"
            style={{
              // marginLeft: "10%",
              // marginRight: 0,
            }}
          >
            <Logo />
            {process.env.SITE_NAME}
          </Navbar.Brand>
          <Navbar.Toggle aria-controls="basic-navbar-nav"
            style={{
              // marginRight: "15%"
            }}
          />
          <Navbar.Collapse
            id="basic-navbar-nav"
          // style={{ marginRight: "40%" }}
          >
            <Nav
              className="mr-auto"
              style={{
                display: "flex",
                // justifyContent: "flex-end",
                marginLeft: "auto",
                // width: "100%",
                // marginRight: "10%"
              }}
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
                      paddingLeft: 5,
                      paddingRight: 5,
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
              // marginRight: "10%",
            }}
            href={"/react/login"}
          >
            {<FormattedMessage id="LOGIN_LOGIN" />}
          </Button>
        </Navbar>
      ) : null
    }
  </div> 
}
