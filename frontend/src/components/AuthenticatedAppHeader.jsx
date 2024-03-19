import React, { useContext } from 'react';
import { Navbar, Nav, NavDropdown } from 'react-bootstrap';
import Avatar from './Avatar';
import '../css/dropdown.css';
import { authenticatedMenu } from '../constants/navMenu';
import DownIcon from './svg/DownIcon';
import RightIcon from './svg/RightIcon';
import Button from 'react-bootstrap/Button';
import NotificationButton from './navBar/NotificationButton';
import MultiLanguageDropdown from './navBar/MultiLanguageDropdown';
import AuthContext from '../AuthProvider';
import useGetMe from '../models/auth/users/useGetMe';
import { FormattedMessage } from 'react-intl';



export default function AuthenticatedAppHeader() {
  const location = window.location;
  const navBarFilled = location.pathname === '/react/home' || location.pathname === '/react/';
  const backgroundColor = !navBarFilled ? '#00a1b2' : 'transparent';

  const isLoggedIn = useContext(AuthContext);
  console.log('=============>>>>>>>>>>>>>>>', isLoggedIn);


  const logout = async event => {
    console.log('Logging out');
    event.preventDefault();
    await fetch('/api/v3/logout')
      .then(response => {
        if (response.status === 200) {
          console.log('User logged out');
          window.location.href = '/';
        } else if (response.status === 401) {
          console.log('User is not logged in');
        }
      })
      .catch(error => {
        console.log(error);
      });
  };
  
  const result = useGetMe();
  const username = result.data?.displayName;
  console.log('useGetMe result', result);
  return (<Navbar variant="dark" expand="lg"
    style={{
      backgroundColor: backgroundColor,
      height: '43px',
      fontSize: '1rem',
      position: 'fixed',
      top: 0,
      maxWidth: '1440px',
      marginLeft: 'auto',
      marginRight: 'auto',
      zIndex: '100',
      width: '100%',
    }}
  >
    <Navbar.Brand href="/" style={{ marginLeft: '1rem' }}>Amphibian Wildbook</Navbar.Brand>
    <Navbar.Toggle aria-controls="basic-navbar-nav" />
    <Navbar.Collapse id="basic-navbar-nav" style={{ marginLeft: '25%'}}>
      <Nav className="mr-auto" id='nav' style={{ 
        display: 'flex', 
        justifyContent: 'flex-end', 
        width: '100%' ,

        }}>
        {authenticatedMenu(username).map((item, idx) => (
          <Nav className="me-auto">
            <NavDropdown 
              title={
                <span style={{ color: 'white' }}>
                  {/* {Object.keys(item)[0]} */}
                  <FormattedMessage id={Object.keys(item)[0].toUpperCase()} />
                  <DownIcon color={'white'}/>
                </span>} 
              id={`basic-nav-dropdown${item}`}
              style={{ color: 'white' }}>
              {
                Object.values(item)[0].map((subItem, idx) => {
                  return (
                  subItem.sub 
                  ? <NavDropdown title={
                    <span style={{ color: 'black' }}>
                      {subItem.name}
                      <span style={{paddingLeft: '34px'}}><RightIcon /></span>
                    </span>}
                    style={{
                      paddingLeft: 8,
                      fontSize: '0.9rem'
                    }}
                  > 
                  {subItem.sub.map((sub, idx) => {
                    return <NavDropdown.Item href={sub.href} style={{ color: 'black', fontSize: '0.9rem' }}>
                      {sub.name}
                    </NavDropdown.Item> 
                  }
                  )}
                </NavDropdown>
                : <NavDropdown.Item href={subItem.href} style={{ color: 'black', fontSize: '0.9rem'  }}>
                  {subItem.name}
                </NavDropdown.Item>)
              })}
            </NavDropdown>
          </Nav>
        ))}

      </Nav>
      <NotificationButton count={1} />
      <MultiLanguageDropdown />
      <Nav style={{ alignItems: 'center', marginLeft: '20px', width: 50 }}>
        <NavDropdown 
          title={<Avatar />} 
          id="basic-nav-dropdown" 
          drop="start"
          >
          <NavDropdown.Item href={'/myAccount.jsp'} style={{ color: 'black' }}>
          <FormattedMessage id="USER_PROFILE" />
          </NavDropdown.Item>
          <NavDropdown.Item onClick={logout} style={{ color: 'black' }}>
            <FormattedMessage id="LOGOUT" />
          </NavDropdown.Item>
          {/* <NavDropdown.Divider /> */}

        </NavDropdown>
      </Nav>
    </Navbar.Collapse>
  </Navbar>)
}