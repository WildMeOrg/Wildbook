import React, { useContext } from 'react';
import { get } from 'lodash-es';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import About from './About';
import NotFound from './NotFound';
import Login from './pages/Login';
import Profile from './pages/Profile';
import Home from './pages/Home';
import Footer from './components/Footer';
import AuthContext from './AuthProvider';
import AuthenticatedAppHeader from './components/AuthenticatedAppHeader';
import UnAuthenticatedAppHeader from './components/UnAuthenticatedAppHeader';
import useGetMe from './models/auth/users/useGetMe';

// import useSiteSettings from './models/site/useSiteSettings';
export default function AuthenticatedSwitch({ adminUserInitialized, loggedIn }) {
  //   const { data: siteSettings } = useSiteSettings();
  //   const siteNeedsSetup = get(siteSettings, [
  //     'site.needsSetup',
  //     'value',
  //   ]);

  console.log('AuthenticatedSwitch');

  const isLoggedIn = useContext(AuthContext);
  const result = useGetMe();
  const username = result.data?.displayName;

  // const siteName = useSite

  console.log('isLoggedIn: ', isLoggedIn);
  return (
    <main>

      <div style={{
                  position: 'fixed',
                  top: 0,
                  maxWidth: '1440px',
                  marginLeft: 'auto',
                  marginRight: 'auto',
                  zIndex: '100',
                  width: '100%',
                }}>            
                  {/* <UnAuthenticatedAppHeader /> */}
                  <AuthenticatedAppHeader username={username}/>
            </div>

      <div
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          justifyContent: 'center',
          alignItems: 'center',
          overflow: 'hidden',
          boxSizing: 'border-box',
          width: '100%',
          minHeight: 'calc(100vh - 40px)', // Assuming the header height is 40px
        }}
      >
        <Routes>
          <Route path="/profile" element={<Profile />} />
          {/* <Route path="/about" element={<About />} /> */}
          <Route path="/home" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/" element={<Home />} />
          <Route path="*" element={<NotFound />} />
        </Routes>

        <Footer />

      </div>
    </main>
  );
}
