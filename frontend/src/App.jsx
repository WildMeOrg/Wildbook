import logo from './logo.svg';
import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';import About from './About';
import NotFound from './NotFound';
import Home from './Home';

// import './App.css';

function App() {
  return (
    <div className="App">
      <Router>
        <Routes>
          <Route path="/wildbook/react/about" element={<About />} />
          <Route path="/wildbook/react" element={<Home />} />    
          <Route path="/wildbook/react/home" element={<Home />} />      
          <Route path="*" element={<NotFound />} />
        </Routes>
      </Router>
    </div>
  );
}

export default App;
