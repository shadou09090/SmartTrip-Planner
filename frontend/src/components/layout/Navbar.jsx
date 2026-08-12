import React from 'react';
import { Compass, LogIn, UserPlus, Home as HomeIcon } from 'lucide-react';
import { Button } from '../ui';

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
          <Button variant="secondary" onClick={() => onNavigate('home')}>
            <HomeIcon size={18} />
            Inicio
          </Button>
        )}
        
        <Button 
          variant={currentPage === 'login' ? 'primary' : 'secondary'}
          onClick={() => onNavigate('login')}
        >
          <LogIn size={18} />
          Login
        </Button>

        <Button 
          variant={currentPage === 'register' ? 'primary' : 'secondary'}
          onClick={() => onNavigate('register')}
        >
          <UserPlus size={18} />
          Sign in
        </Button>
      </div>
    </nav>
  );
}
