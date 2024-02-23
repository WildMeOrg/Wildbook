import React from 'react';
import { Navbar, Nav, NavDropdown } from 'react-bootstrap';
import { LinkContainer } from 'react-router-bootstrap';
import Avatar from './Avatar';

export default function Header() {
  const menu = ['Learn', 'Submit', 'Individuals', 'Sightings', 'Encounters', 'Administers'];

  return (
    <div 
            style = {{
                height: '500px', 
                backgroundImage: `url('/wildbook/react/forest.png')`, 
                backgroundSize: 'cover',
                backgroundPosition: 'center',
                backgroundRepeat: 'no-repeat'
            }
            }
        >
    <Navbar variant="dark" expand="lg" style={{ justifyContent: 'space-between' }}>
      <Navbar.Brand href="#home" style={{ marginLeft: '1rem' }}>Amphibian Wildbook</Navbar.Brand>
      <Navbar.Toggle aria-controls="basic-navbar-nav" />
      <Navbar.Collapse id="basic-navbar-nav">
        <Nav className="mr-auto" style={{ display: 'flex', justifyContent: 'flex-end', width: '100%' }}>
          {menu.map((item, idx) => (
            <LinkContainer to={'/wildbook/react/' + item.toLowerCase()} key={idx}>
              <NavDropdown title={item} id={item} >
              <NavDropdown.Item href="#action3">Action</NavDropdown.Item>
              <NavDropdown.Item href="#action4">
                Another action
              </NavDropdown.Item>
              <NavDropdown.Divider />
              <NavDropdown.Item href="#action5">
                Something else here
              </NavDropdown.Item>
            </NavDropdown>
            </LinkContainer>
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
    </Navbar>
    </div>
  );
}
