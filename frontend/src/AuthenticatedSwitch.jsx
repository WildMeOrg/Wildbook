import React from 'react';
import { get } from 'lodash-es';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import About from './About';
import NotFound from './NotFound';
import Login from './pages/Login';
import Profile from './pages/Profile';
import Footer from './components/Footer';


// import useSiteSettings from './models/site/useSiteSettings';
import AuthenticatedAppHeader from './components/AuthenticatedAppHeader';

import Home from './pages/Home';

// import Footer from './components/Footer';

export default function AuthenticatedSwitch({adminUserInitialized, loggedIn} ) {
//   const { data: siteSettings } = useSiteSettings();
//   const siteNeedsSetup = get(siteSettings, [
//     'site.needsSetup',
//     'value',
//   ]);

  return (
    <main>
             
      <AuthenticatedAppHeader />
      
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
          minHeight: 'calc(100vh - 40px)', // Assuming the header height is 64px
        }}
      >
        <Router>
          <Routes>
            <Route path="/profile" element={<Profile />} />
            <Route path="/about" element={<About />} />
            <Route path="/home" element={<Home />} />
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<Home />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Router>
        
      <Footer />
      
      </div>
    </main>
  );
}
