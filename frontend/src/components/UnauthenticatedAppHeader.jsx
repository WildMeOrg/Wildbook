import React from 'react';
import { Navbar, Nav, NavDropdown } from 'react-bootstrap';
import '../css/dropdown.css';
import menu from '../constants/navMenu';
import DownIcon from './svg/DownIcon';
import Button from 'react-bootstrap/Button';
import MultiLanguageDropdown from './navBar/MultiLanguageDropdown';

export default function UnauthenticatedAppHeader () {
    const location = window.location;
    const navBarFilled = location.pathname === '/';
    const backgroundColor = !navBarFilled ? '#00a1b2' : 'transparent';

    return (<Navbar variant="dark" expand="lg" style={{ 
          backgroundColor: backgroundColor,
          width: '100%', 
          height: '43px',
          display: 'flex', 
          justifyContent: 'space-between',
          overflow: 'visible',
          fontSize: '1rem',
          }}>
            <Navbar.Brand href="/" style={{ marginLeft: '1rem' }}>Amphibian Wildbook</Navbar.Brand>
            <Navbar.Toggle aria-controls="basic-navbar-nav" />
            <Navbar.Collapse id="basic-navbar-nav" style={{marginLeft: '20%'}}>
              <Nav className="mr-auto" style={{ display: 'flex', justifyContent: 'flex-end', width: '100%' }}>
                {menu.map((item, idx) => (
                  <Nav className="me-auto">                    
                    <NavDropdown title={
                      <span style={{color: 'white'}}>
                        {Object.keys(item)[0]}
                        <DownIcon />
                        </span>} id={`basic-nav-dropdown${item}`} 
                        style={{color: 'white'}}>
                      {Object.values(item)[0].map((subItem, idx) => {
                        return  <NavDropdown.Item href={subItem.href} style={{color: 'black'}}>
                          {subItem.name}
                          {/* {idx < Object.values(item)[0].length-1 && <NavDropdown.Divider />} */}
                          </NavDropdown.Item>                      
                      })}
                    </NavDropdown>
          </Nav>
                ))}

              </Nav>
              <Button 
                variant="basic" 
                style={{
                  backgroundColor: 'transparent',
                  color: 'white',
                  border: 'none',
                  marginLeft: '10px',
                }}
                href={"/login"}>Login
              </Button>
              <MultiLanguageDropdown />
              
            </Navbar.Collapse>
          </Navbar>)
}