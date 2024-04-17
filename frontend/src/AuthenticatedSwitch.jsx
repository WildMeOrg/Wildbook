import React from 'react';
import { Routes, Route } from 'react-router-dom';
import NotFound from './NotFound';
import Login from './pages/Login';
import Profile from './pages/Profile';
import Home from './pages/Home';
import Footer from './components/Footer';
 import AuthenticatedAppHeader from './components/AuthenticatedAppHeader';
import UnAuthenticatedAppHeader from './components/UnAuthenticatedAppHeader';
import useGetMe from './models/auth/users/useGetMe';

export default function AuthenticatedSwitch({ isLoggedIn }) {

  const { isFetched, data, error } = useGetMe();
  const username = data?.displayName;
  const avatar = data?.imageURL || '/react/images/Avatar.png';

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
                  {isLoggedIn ? <AuthenticatedAppHeader username={username} avatar={avatar}/>
                  : <UnAuthenticatedAppHeader />  
                }
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
