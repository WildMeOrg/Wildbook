import React from 'react';
import { get } from 'lodash-es';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import About from './About';
import NotFound from './NotFound';
import Login from './pages/Login';


// import useSiteSettings from './models/site/useSiteSettings';
import Header from './components/NavBar';

import Home from './pages/Home';

// import Footer from './components/Footer';

export default function AuthenticatedSwitch() {
//   const { data: siteSettings } = useSiteSettings();
//   const siteNeedsSetup = get(siteSettings, [
//     'site.needsSetup',
//     'value',
//   ]);

  return (
    <main>
      <Header />
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
          minHeight: 'calc(100vh - 64px)', // Assuming the header height is 64px
        }}
      >
        <Router>
          <Routes>
            <Route path="/about" element={<About />} />
            <Route path="/home" element={<Home />} />
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<About />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Router>
        {/* <Footer authenticated /> */}
      </div>
    </main>
  );
}
