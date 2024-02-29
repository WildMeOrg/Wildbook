import React from 'react';
import { Navbar, Nav, NavDropdown } from 'react-bootstrap';
import { LinkContainer } from 'react-router-bootstrap';
import Avatar from './Avatar';
import '../css/dropdown.css';
import menu from '../constants/navMenu';

const DownIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor" class="bi bi-chevron-down" viewBox="0 0 16 16">
  <path fill-rule="evenodd" d="M1.646 4.646a.5.5 0 0 1 .708 0L8 10.293l5.646-5.647a.5.5 0 0 1 .708.708l-6 6a.5.5 0 0 1-.708 0l-6-6a.5.5 0 0 1 0-.708"/>
</svg>
);

export default function NavBar () {
    // const menu = ['Learn', 'Submit', 'Individuals', 'Sightings', 'Encounters', 'Administers'];

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

                      {/* <NavDropdown.Item href="#action/3.1">Action</NavDropdown.Item>
                      <NavDropdown.Item href="#action/3.2">
                        Another action
                      </NavDropdown.Item>
                      <NavDropdown.Item href="#action/3.3">Something</NavDropdown.Item>
                      <NavDropdown.Divider />
                      <NavDropdown.Item href="#action/3.4">
                        Separated link
                      </NavDropdown.Item> */}
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