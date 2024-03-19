import React, { useContext } from 'react';
import { Navbar, Nav, NavDropdown } from 'react-bootstrap';
import '../css/dropdown.css';
import { unAuthenticatedMenu } from '../constants/navMenu';
import DownIcon from './svg/DownIcon';
import Button from 'react-bootstrap/Button';
import MultiLanguageDropdown from './navBar/MultiLanguageDropdown';
import { FormattedMessage } from 'react-intl';

export default function AuthenticatedAppHeader() {
  const location = window.location;
  const navBarFilled = location.pathname === '/react/home' || location.pathname === '/react/';
  const backgroundColor = !navBarFilled ? '#00a1b2' : 'transparent';  

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
    <Navbar.Collapse id="basic-navbar-nav" style={{ marginLeft: '50%' }}>
      <Nav className="mr-auto" style={{ display: 'flex', justifyContent: 'flex-end', width: '100%' }}>
        {unAuthenticatedMenu.map((item, idx) => (
          <Nav className="me-auto">
            <NavDropdown title={
              <span style={{ color: 'white' }}>
                <FormattedMessage id={Object.keys(item)[0].toUpperCase()} />
                <DownIcon />
              </span>} id={`basic-nav-dropdown${item}`}
              style={{ color: 'white' }}>
              {Object.values(item)[0].map((subItem, idx) => {
                return <NavDropdown.Item href={subItem.href} style={{ color: 'black' }}>
                  {subItem.name}
                </NavDropdown.Item>
              })}
            </NavDropdown>
          </Nav>
        ))}

      </Nav>     

      <MultiLanguageDropdown />
      <Button
        variant="basic"
        style={{
          backgroundColor: 'transparent',
          color: 'white',
          border: 'none',
          // marginLeft: '10px',
          width: '100px',
        }}
        href={"/react/login"}>{
          <FormattedMessage id='LOGIN'/>
                        }
      </Button>

    </Navbar.Collapse>
  </Navbar>)
}