import React, { useContext, useEffect, useState } from 'react';
import { Navbar, Nav } from 'react-bootstrap';
import '../css/dropdown.css';
import NotificationButton from './navBar/NotificationButton';
import MultiLanguageDropdown from './navBar/MultiLanguageDropdown';
import AuthContext from '../AuthProvider';
import AvatarAndUserProfile from './header/AvatarAndUserProfile';
import { debounce } from 'lodash';
import Menu from './header/Menu';

export default function AuthenticatedAppHeader({ username, avatar, showAlert}) {
  const location = window.location;
  const path = location.pathname.endsWith('/') ? location.pathname : location.pathname + '/';
  const homePage = path === '/react/home/' || path === '/react/';
  const [backgroundColor, setBackgroundColor] = useState(homePage ? 'transparent' : '#00a1b2');

  const {
    count,
    collaborationTitle,
    collaborationData,
    mergeData,
    getAllNotifications,
  } = useContext(AuthContext);

  useEffect(() => {
    const handleScroll = debounce(() => {
      const currentScrollY = window.scrollY;
      setBackgroundColor(homePage && currentScrollY > 40 ? '#00a1b2' : 'transparent');
    }, 200);
    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);


  return (
    <Navbar variant="dark" expand="lg"
      style={{
        backgroundColor: backgroundColor,
        height: '43px',
        fontSize: '1rem',
        position: 'fixed',
        top: showAlert? 60 : 0,
        maxWidth: '1440px',
        marginLeft: 'auto',
        marginRight: 'auto',
        zIndex: '100',
        width: '100%',
        paddingRight: '20px',
      }}
    >
      <Navbar.Brand href="/" style={{ marginLeft: '1rem' }}>{process.env.SITE_NAME}</Navbar.Brand>
      <Navbar.Toggle aria-controls="basic-navbar-nav" />
      <Navbar.Collapse id="basic-navbar-nav" style={{ marginLeft: '20%' }}>
        <Nav className="mr-auto" id='nav' style={{
          display: 'flex',
          justifyContent: 'flex-end',
          width: '100%',
        }}>
          <Menu username={username}/>
        </Nav>
        <NotificationButton
          collaborationTitle={collaborationTitle}
          collaborationData={collaborationData}
          count={count}
          mergeData={mergeData}
          getAllNotifications={getAllNotifications}
        />
        <MultiLanguageDropdown />
        <AvatarAndUserProfile username={username} avatar={avatar} />
      </Navbar.Collapse>
    </Navbar>)
}