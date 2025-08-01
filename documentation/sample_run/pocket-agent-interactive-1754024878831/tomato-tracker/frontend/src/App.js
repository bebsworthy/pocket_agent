import React from 'react';
import { BrowserRouter as Router, Routes, Route, Link } from 'react-router-dom';
import Dashboard from './components/Dashboard';
import Plants from './components/Plants';
import PlantDetail from './components/PlantDetail';
import AddPlant from './components/AddPlant';
import Harvests from './components/Harvests';
import './App.css';

function App() {
  return (
    <Router>
      <div className="App">
        <nav className="navbar">
          <div className="nav-container">
            <h1 className="nav-title">🍅 Tomato Tracker</h1>
            <div className="nav-links">
              <Link to="/" className="nav-link">Dashboard</Link>
              <Link to="/plants" className="nav-link">Plants</Link>
              <Link to="/harvests" className="nav-link">Harvests</Link>
              <Link to="/add-plant" className="nav-link add-plant-btn">+ Add Plant</Link>
            </div>
          </div>
        </nav>
        
        <main className="main-content">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/plants" element={<Plants />} />
            <Route path="/plants/:id" element={<PlantDetail />} />
            <Route path="/add-plant" element={<AddPlant />} />
            <Route path="/harvests" element={<Harvests />} />
          </Routes>
        </main>
      </div>
    </Router>
  );
}

export default App;