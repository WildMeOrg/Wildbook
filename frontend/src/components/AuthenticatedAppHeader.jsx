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
import useGetMe from '../models/auth/users/useGetMe';
import { FormattedMessage } from 'react-intl';
import { useNavigate } from 'react-router-dom';
import { Link } from 'react-router-dom';

export default function AuthenticatedAppHeader({username}) {
  const location = window.location;
  const path = location.pathname.endsWith('/') ? location.pathname : location.pathname + '/';
  const homePage = path === '/react/home/' || path === '/react/';
  const [ backgroundColor, setBackgroundColor ] = useState(homePage ? 'transparent' : '#00a1b2');
  const navigate = useNavigate();

  console.log('AuthenticatedAppHeader getting username', username);

  useEffect(() => {
    const handleScroll = () => {
      const currentScrollY = window.scrollY;
      if (homePage && currentScrollY > 40) {
        setBackgroundColor('#00a1b2');
      } else if(homePage){
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

  const handleMouseEnter = (id) => {
    setDropdownShows(prev => ({ ...prev, [id]: true }));
  };

  const handleMouseLeave = (id) => {
    setDropdownShows(prev => ({ ...prev, [id]: false }));
  };

  const isLoggedIn = useContext(AuthContext);
  console.log('=============>>>>>>>>>>>>>>>', isLoggedIn);

  const logout = async event => {
    console.log('Logging out');
    event.preventDefault();
    await fetch('/api/v3/logout')
      .then(response => {
        if (response.status === 200) {
          console.log('User logged out');
          window.location.href = '/react/home/';
        } else if (response.status === 401) {
          console.log('User is not logged in');
        }
      })
      .catch(error => {
        console.log(error);
      });
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
                  <FormattedMessage id={Object.keys(item)[0].toUpperCase()} />
                  <DownIcon color={'white'}/>
                </span>} 
              id={`basic-nav-dropdown${idx}`}
              style={{ color: 'white', height: 30 }}
              onMouseEnter={() => handleMouseEnter(`dropdown${idx + 1}`)}
              onMouseLeave={() => handleMouseLeave(`dropdown${idx + 1}`)}
              show={dropdownShows[`dropdown${idx + 1}`]  }
              >
              
              {
                Object.values(item)[0].map((subItem, idx) => {
                  return (
                  subItem.sub 
                  ? <NavDropdown title={
                      // <span style={{ color: 'black' }}>
                      //   {subItem.name}
                      //   <span style={{paddingLeft: '34px'}}><RightIcon /></span>
                      // </span>
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
                          <span style={{paddingLeft: '34px'}}>
                            <RightIcon />
                          </span>
                      </a>
                    }
                      style={{
                        paddingLeft: 8,
                        fontSize: '0.9rem'
                      }}
                      // href={subItem.href}
                      onMouseEnter={() => handleMouseEnter(`dropdown7`)}
                      onMouseLeave={() => handleMouseLeave(`dropdown7`)}
                      show={dropdownShows[`dropdown7`]  }
                      // onClick={(e) => {
                      //   e.preventDefault();
                      //   console.log('clicked', subItem.href); 
                      //   navigate(subItem.href);}}
                  > 
                  {subItem.sub.map((sub, idx) => {
                    return <NavDropdown.Item href={sub.href} style={{ color: 'black', fontSize: '0.9rem' }}>
                      {sub.name}
                    </NavDropdown.Item> 
                  }
                  )}
                </NavDropdown>
                : <NavDropdown.Item href={subItem.href} style={{ color: 'black', fontSize: '0.9rem'}}>
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
          drop="down"
          >
          <NavDropdown.Item href={'/react/home/'} style={{ color: 'black' }}>
           <FormattedMessage id="LANDING_PAGE"/>
          </NavDropdown.Item>
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