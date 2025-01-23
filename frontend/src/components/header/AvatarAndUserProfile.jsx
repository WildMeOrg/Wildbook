import React, { useState } from "react";
import { Nav, NavDropdown } from "react-bootstrap";
import { FormattedMessage } from "react-intl";
import Avatar from "../Avatar";
import { useNavigate } from "react-router-dom";
import "../../css/dropdown.css";
import "../../css/avatarDropdown.css";
import AuthContext from "../../AuthProvider";
import { useContext } from "react";

export default function AvatarAndUserProfile({ avatar }) {
  const navigate = useNavigate();
  const [shows, setShows] = useState(false);
  // eslint-disable-next-line no-unused-vars
  const { isLoggedIn, setIsLoggedIn } = useContext(AuthContext);
  const logout = async (event) => {
    event.preventDefault();
    await fetch("/api/v3/logout")
      .then((response) => {
        if (response.status === 200) {
          setIsLoggedIn(false);
        } else if (response.status === 401) {
          console.log("Unauthorized");
        }
        navigate("/login/");
      })
      .catch((error) => {
        console.log(error);
      });
  };

  return (
    <Nav style={{ alignItems: "center", marginLeft: "20px", width: 50 }}>
      <NavDropdown
        title={<Avatar avatar={avatar} />}
        id="basic-nav-dropdown"
        className="custom-nav-dropdown"
        onMouseEnter={() => {
          setShows(true);
        }}
        onMouseLeave={() => {
          setShows(false);
        }}
        show={shows}
      >
        <NavDropdown.Item href={`${process.env.PUBLIC_URL}/home/`} style={{ color: "black" }}>
          <FormattedMessage id="LANDING_PAGE" />
        </NavDropdown.Item>
        <NavDropdown.Item href={"/myAccount.jsp"} style={{ color: "black" }}>
          <FormattedMessage id="USER_PROFILE" />
        </NavDropdown.Item>
        <NavDropdown.Item onClick={logout} style={{ color: "black" }}>
          <FormattedMessage id="LOGIN_LOGOUT" />
        </NavDropdown.Item>
      </NavDropdown>
    </Nav>
  );
}
