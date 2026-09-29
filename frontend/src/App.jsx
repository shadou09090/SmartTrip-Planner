import React, { useState } from 'react';
import { Navbar, Footer } from './components/layout';
import { CreateTrip, Home, Login, Register } from './pages';

export default function App() {
  const [currentPage, setCurrentPage] = useState('home');
  const [isAuthenticated, setIsAuthenticated] = useState(false);

  const handleLogin = () => {
    setIsAuthenticated(true);
    setCurrentPage('create-trip');
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setCurrentPage('home');
  };

  const handleNavigate = (page) => {
    if (page === 'create-trip' && !isAuthenticated) {
      setCurrentPage('login');
      return;
    }

    setCurrentPage(page);
  };

  const renderPage = () => {
    switch (currentPage) {
      case 'login':
        return <Login onNavigate={handleNavigate} onLogin={handleLogin} />;
      case 'register':
        return <Register onNavigate={handleNavigate} />;
      case 'create-trip':
        return isAuthenticated
          ? <CreateTrip />
          : <Login onNavigate={handleNavigate} onLogin={handleLogin} />;
      case 'home':
      default:
        return <Home onNavigate={handleNavigate} isAuthenticated={isAuthenticated} />;
    }
  };

  return (
    <>
      <Navbar
        currentPage={currentPage}
        isAuthenticated={isAuthenticated}
        onNavigate={handleNavigate}
        onLogout={handleLogout}
      />
      <main style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        {renderPage()}
      </main>
      <Footer />
    </>
  );
}
