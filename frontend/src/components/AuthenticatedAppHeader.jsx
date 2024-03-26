import React, { useContext, useEffect, useState } from 'react';
import { Navbar, Nav, NavDropdown } from 'react-bootstrap';
import Avatar from './Avatar';
import '../css/dropdown.css';
import { authenticatedMenu } from '../constants/navMenu';
import DownIcon from './svg/DownIcon';
import RightIcon from './svg/RightIcon';
import NotificationButton from './navBar/NotificationButton';
import MultiLanguageDropdown from './navBar/MultiLanguageDropdown';
import AuthContext from '../AuthProvider';
import { FormattedMessage } from 'react-intl';
import AvatarAndUserProfile from './header/AvatarAndUserProfile';

export default function AuthenticatedAppHeader({ username, avatar }) {
  console.log('AuthenticatedAppHeader', avatar);
  const location = window.location;
  const path = location.pathname.endsWith('/') ? location.pathname : location.pathname + '/';
  const homePage = path === '/react/home/' || path === '/react/';
  const [backgroundColor, setBackgroundColor] = useState(homePage ? 'transparent' : '#00a1b2');


  useEffect(() => {
    const handleScroll = () => {
      const currentScrollY = window.scrollY;
      if (homePage && currentScrollY > 40) {
        setBackgroundColor('#00a1b2');
      } else if (homePage) {
        setBackgroundColor('transparent');
      }
    }
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  const [dropdownShows, setDropdownShows] = useState({
    dropdown1: false,
    dropdown2: false,
    dropdown3: false,
    dropdown4: false,
    dropdown5: false,
    dropdown6: false,
    dropdown7: false,
  });
  const [dropdownBorder, setDropdownBorder] = useState('2px solid transparent');

  const handleMouseEnter = (id) => {
    setDropdownShows(prev => ({ ...prev, [id]: true }));
    setDropdownBorder(prev => ({ ...prev, [id]: '2px solid white' }))
  };

  const handleMouseLeave = (id) => {
    setDropdownShows(prev => ({ ...prev, [id]: false }));
    setDropdownBorder(prev => ({ ...prev, [id]: '2px solid transparent' }))
  };

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
    <Navbar.Collapse id="basic-navbar-nav" style={{ marginLeft: '25%' }}>
      <Nav className="mr-auto" id='nav' style={{
        display: 'flex',
        justifyContent: 'flex-end',
        width: '100%',
      }}>
        {authenticatedMenu(username).map((item, idx) => (
          <Nav className="me-auto" key={idx}>
            <NavDropdown
              title={
                <span style={{ color: 'white' }}>
                  <FormattedMessage id={Object.keys(item)[0].toUpperCase()} />
                  {' '}
                  <DownIcon color={'white'} />
                </span>}
              id={`basic-nav-dropdown${idx}`}
              style={{ color: 'white', boxSizing: 'border-box', 
                borderBottom: dropdownBorder[`dropdown${idx + 1}`] || '2px solid transparent'}}
              onMouseEnter={() => handleMouseEnter(`dropdown${idx + 1}`)}
              onMouseLeave={() => handleMouseLeave(`dropdown${idx + 1}`)}
              show={dropdownShows[`dropdown${idx + 1}`]}
            >

              {
                Object.values(item)[0].map((subItem, idx) => {
                  return (
                    subItem.sub
                      ? <NavDropdown title={
                        <a
                          style={{
                            color: 'black',
                            fontSize: '0.9rem',
                            textDecoration: 'none'
                          }}
                          onClick={(e) => {
                            e.stopPropagation();
                            e.preventDefault();
                            window.location.href = subItem.href;
                          }}

                          href={subItem.href}>
                          {subItem.name}
                          <span style={{ paddingLeft: '34px' }}>
                            <RightIcon />
                          </span>
                        </a>
                      }
                        drop="end"
                        style={{
                          paddingLeft: 8,
                          fontSize: '0.9rem'
                        }}
                        onMouseEnter={() => handleMouseEnter(`dropdown7`)}
                        onMouseLeave={() => handleMouseLeave(`dropdown7`)}
                        show={dropdownShows[`dropdown7`]}
                      >
                        {subItem.sub.map((sub) => {
                          return <NavDropdown.Item href={sub.href} style={{ color: 'black', fontSize: '0.9rem' }}>
                            {sub.name}
                          </NavDropdown.Item>
                        }
                        )}
                      </NavDropdown>
                      : <NavDropdown.Item href={subItem.href} style={{ color: 'black', fontSize: '0.9rem' }}>
                        {subItem.name}
                      </NavDropdown.Item>)
                })}
            </NavDropdown>
          </Nav>
        ))}

      </Nav>
      <NotificationButton count={1} />
      <MultiLanguageDropdown />
      <AvatarAndUserProfile username={username} avatar={avatar} />
    </Navbar.Collapse>
  </Navbar>)
}