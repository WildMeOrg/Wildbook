import React, { useContext, useState } from "react";
import { Navbar, Nav, NavDropdown } from "react-bootstrap";
import "../css/dropdown.css";
import { unAuthenticatedMenu } from "../constants/navMenu";
import DownIcon from "./svg/DownIcon";
import Button from "react-bootstrap/Button";
import MultiLanguageDropdown from "./navBar/MultiLanguageDropdown";
import { FormattedMessage } from "react-intl";
import FooterVisibilityContext from "../FooterVisibilityContext";
import Logo from "./svg/Logo";

import HeaderDropdownItems from "./header/HeaderDropdownItems";

export default function UnAuthenticatedAppHeader({
  showclassicsubmit,
  showHowToPhotograph,
}) {
  const { visible } = useContext(FooterVisibilityContext);
  const [dropdownShows, setDropdownShows] = useState({});
  const [dropdownBorder, setDropdownBorder] = useState({});

  const handleMouseEnterLeave = (id, isEnter) => {
    setDropdownShows((prev) => ({ ...prev, [id]: isEnter }));
    setDropdownBorder((prev) => ({
      ...prev,
      [id]: isEnter ? "2px solid white" : "2px solid transparent",
    }));
  };

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
          paddingLeft: "5%",
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
              zIndex: "200",
            }}
          >
            <Navbar.Brand
              className="d-flex flex-row align-items-center"
              href="/"
            >
              <Logo />
              {process.env.SITE_NAME}
            </Navbar.Brand>
            <Navbar.Toggle aria-controls="basic-navbar-nav" />
            <Navbar.Collapse id="basic-navbar-nav">
              <Nav
                className="mr-auto"
                style={{
                  display: "flex",
                  marginLeft: "auto",
                }}
              >
                {unAuthenticatedMenu(
                  showclassicsubmit,
                  showHowToPhotograph,
                ).map((item, idx) => (
                  <Nav key={idx} className="me-auto">
                    <NavDropdown
                      title={
                        <span style={{ color: "white" }}>
                          <FormattedMessage
                            id={Object.keys(item)[0].toUpperCase()}
                          />{" "}
                          <DownIcon />
                        </span>
                      }
                      id={`basic-nav-dropdown${idx}`}
                      style={{
                        color: "white",
                        boxSizing: "border-box",
                        borderBottom:
                          dropdownBorder[`dropdown${idx + 1}`] ||
                          "2px solid transparent",
                        paddingLeft: 5,
                        paddingRight: 5,
                      }}
                      onMouseEnter={() =>
                        handleMouseEnterLeave(`dropdown${idx + 1}`, true)
                      }
                      onMouseLeave={() =>
                        handleMouseEnterLeave(`dropdown${idx + 1}`, false)
                      }
                      show={dropdownShows[`dropdown${idx + 1}`]}
                    >
                      <HeaderDropdownItems items={Object.values(item)[0]} />
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
              href={`${process.env.PUBLIC_URL}/login`}
            >
              {<FormattedMessage id="LOGIN_LOGIN" />}
            </Button>
          </Navbar>
        ) : null}
      </div>
    </div>
  );
}
