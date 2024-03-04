import React from 'react';
import { Navbar, Nav, NavDropdown } from 'react-bootstrap';
import { LinkContainer } from 'react-router-bootstrap';
import Avatar from './Avatar';
import '../css/dropdown.css';
import menu from '../constants/navMenu';
import DownIcon from './svg/DownIcon';

export default function NavBar () {
    return (<Navbar variant="dark" expand="lg" style={{ 
          width: '100%', 
          display: 'flex', 
          justifyContent: 'space-between' }}>
            <Navbar.Brand href="#home" style={{ marginLeft: '1rem' }}>Amphibian Wildbook</Navbar.Brand>
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
                        return  <NavDropdown.Item href={subItem.href} style={{color: 'black'}}>{subItem.name}</NavDropdown.Item>                      
                      })}
                    </NavDropdown>
          </Nav>
                ))}
              </Nav>
              <Nav style={{ alignItems: 'center', marginLeft: '35px' }}>          
                <NavDropdown title={<Avatar />} id="basic-nav-dropdown">
                  <LinkContainer to="/profile">
                    <NavDropdown.Item>Profile</NavDropdown.Item>
                  </LinkContainer>
                  <LinkContainer to="/settings">
                    <NavDropdown.Item>Settings</NavDropdown.Item>
                  </LinkContainer>
                  <NavDropdown.Divider />
                  <LinkContainer to="/logout">
                    <NavDropdown.Item>Logout</NavDropdown.Item>
                  </LinkContainer>
                </NavDropdown>
              </Nav>
            </Navbar.Collapse>
          </Navbar>)
}