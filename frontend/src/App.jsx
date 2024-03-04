import { BrowserRouter as Router, Route, Routes } from 'react-router-dom';import About from './About';
import NotFound from './NotFound';
import Home from './pages/Home'
import Login from './pages/Login'

// import './App.css';

function App() {
  return (
    <div className="App">
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
    </div>
  );
}

export default App;
