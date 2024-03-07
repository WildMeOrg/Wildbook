import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';import About from './About';
import NotFound from './NotFound';
import Home from './pages/Home'
import Login from './pages/Login'
import { IntlProvider } from 'react-intl';
import messagesEn from './locale/en.json';
import 'bootstrap/dist/css/bootstrap.min.css';
import NavBar from './components/NavBar';

function App() {
  const messageMap = {
    en: messagesEn,
  };
  const locale = 'en';
  const containerStyle = {
    maxWidth: '1440px',
    marginLeft: 'auto',
    marginRight: 'auto',
    width: '100%',
    position: 'relative',
  }

  const location = window.location;
  const showNavBar = location.pathname !== '*';
  // const notFound = window.location.pathname === '/notFound';
  return (
    <div className="App" 
      style={containerStyle}
      >
      {showNavBar && <div style={{
            position: 'absolute',
            top: 0,
            left: 0,
            zIndex: '100',
            width: '100%',
          }}>            
            <NavBar />
          </div>}
      <IntlProvider 
        locale="en"
        defaultLocale="en"
        messages={messageMap[locale]}
      >
        <div>
          <Router>
            <Routes>
              <Route path="/about" element={<About />} />
              <Route path="/home" element={<Home />} navBarFilled/>
              <Route path="/login" element={<Login />} />
              <Route path="/" element={<Home />} />      
              <Route path="*" element={<NotFound />} />
            </Routes>
          </Router>
        </div>
      </IntlProvider>
    </div>
  );
}

export default App;
