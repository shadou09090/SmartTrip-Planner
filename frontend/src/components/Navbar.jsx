import React from 'react';
import { Compass, LogIn, UserPlus, Home as HomeIcon } from 'lucide-react';

export default function Navbar({ currentPage, onNavigate }) {
  return (
    <nav className="navbar">
      <div className="nav-brand" onClick={() => onNavigate('home')}>
        <div className="brand-icon">
          <Compass size={24} />
        </div>
        <span>SmartTrip Planner</span>
      </div>

      <div className="nav-links">
        {currentPage !== 'home' && (
          <button 
            className="nav-btn nav-btn-secondary"
            onClick={() => onNavigate('home')}
          >
            <HomeIcon size={18} />
            Inicio
          </button>
        )}
        
        <button 
          className={`nav-btn ${currentPage === 'login' ? 'nav-btn-primary' : 'nav-btn-secondary'}`}
          onClick={() => onNavigate('login')}
        >
          <LogIn size={18} />
          Login
        </button>

        <button 
          className={`nav-btn ${currentPage === 'register' ? 'nav-btn-primary' : 'nav-btn-secondary'}`}
          onClick={() => onNavigate('register')}
        >
          <UserPlus size={18} />
          Sign in
        </button>
      </div>
    </nav>
  );
}
