import React, { useState } from 'react';
import { Nav, NavDropdown } from 'react-bootstrap';
import { FormattedMessage } from 'react-intl';
import Avatar from '../Avatar';
import { useNavigate } from 'react-router-dom';
import '../../css/dropdown.css';
import '../../css/avatarDropdown.css'
import AuthContext from '../../AuthProvider';
import { useContext } from 'react';

export default function AvatarAndUserProfile({ username, avatar }) {
  const navigate = useNavigate();
  const [shows, setShows] = useState(false);
  const { isLoggedIn, setIsLoggedIn } = useContext(AuthContext);
  console.log('AvatarAndUserProfile', avatar);
  const logout = async event => {
    console.log('Logging out');
    event.preventDefault();
    await fetch('/api/v3/logout')
      .then(response => {
        if (response.status === 200) {
          console.log('User logged out');
          setIsLoggedIn(false);
        } else if (response.status === 401) {
          console.log('User is not logged in');
        }        
        navigate('/login/');

      })
      .catch(error => {
        console.log(error);
      });
  };

  return <Nav style={{ alignItems: 'center', marginLeft: '20px', width: 50 }}>
    <NavDropdown
      title={<Avatar avatar={avatar} />}
      id="basic-nav-dropdown"
      className="custom-nav-dropdown"
      onMouseEnter={() => { setShows(true) }}
      onMouseLeave={() => { setShows(false) }}
      show={shows}
    >
      <NavDropdown.Item href={'/react/home/'} style={{ color: 'black' }}>
        <FormattedMessage id="LANDING_PAGE" />
      </NavDropdown.Item>
      <NavDropdown.Item href={'/myAccount.jsp'} style={{ color: 'black' }}>
        <FormattedMessage id="USER_PROFILE" />
      </NavDropdown.Item>
      <NavDropdown.Item onClick={logout} style={{ color: 'black' }}>
        <FormattedMessage id="LOGOUT" />
      </NavDropdown.Item>
    </NavDropdown>
  </Nav>
}


