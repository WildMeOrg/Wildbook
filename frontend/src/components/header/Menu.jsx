import React, { useState } from "react";
import { Nav, NavDropdown } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import DownIcon from "../svg/DownIcon";
import { authenticatedMenu } from "../../constants/navMenu";

import HeaderDropdownItems from "./HeaderDropdownItems";

export default function Menu({
  username,
  showclassicsubmit,
  showClassicEncounterSearch,
  showHowToPhotograph,
}) {
  const [dropdownShows, setDropdownShows] = useState({});
  const [dropdownBorder, setDropdownBorder] = useState("2px solid transparent");

  const handleMouseEnterLeave = (id, isEnter) => {
    setDropdownShows((prev) => ({ ...prev, [id]: isEnter ? true : false }));
    setDropdownBorder((prev) => ({
      ...prev,
      [id]: isEnter ? "2px solid white" : "2px solid transparent",
    }));
  };

  return (
    <>
      {authenticatedMenu(
        username,
        showclassicsubmit,
        showClassicEncounterSearch,
        showHowToPhotograph,
      ).map((item, idx) => (
        <Nav className="me-auto nav" key={idx}>
          <NavDropdown
            className="header-dropdown"
            title={
              <span style={{ color: "white" }}>
                <FormattedMessage id={Object.keys(item)[0].toUpperCase()} />{" "}
                <DownIcon color={"white"} />
              </span>
            }
            id={`basic-nav-dropdown${idx}`}
            style={{
              color: "white",
              boxSizing: "border-box",
              paddingLeft: 2,
              paddingRight: 2,
              borderBottom:
                dropdownBorder[`dropdown${idx + 1}`] || "2px solid transparent",
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
    </>
  );
}
