import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';import About from './About';
import NotFound from './NotFound';
import Home from './pages/Home'
import Login from './pages/Login'
import { IntlProvider } from 'react-intl';
import messagesEn from './locale/en.json';


function App() {
  const messageMap = {
    en: messagesEn,
  };
  const locale = 'en';

  return (
    <div className="App">
      <IntlProvider 
        locale="en"
        defaultLocale="en"
        messages={messageMap[locale]}
      >
        <Router>
          <Routes>
            <Route path="/react/about" element={<About />} />
            <Route path="/react" element={<Home />} />    
            <Route path="/home" element={<Home />} />
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<Home />} />      
            <Route path="*" element={<NotFound />} />
          </Routes>
        </Router>
      </IntlProvider>
    </div>
  );
}

export default App;
