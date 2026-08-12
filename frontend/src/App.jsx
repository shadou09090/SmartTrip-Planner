import React, { useState } from 'react';
import { Navbar, Footer } from './components/layout';
import { Home, Login, Register } from './pages';

export default function App() {
  const [currentPage, setCurrentPage] = useState('home');

  const renderPage = () => {
    switch (currentPage) {
      case 'login':
        return <Login onNavigate={setCurrentPage} />;
      case 'register':
        return <Register onNavigate={setCurrentPage} />;
      case 'home':
      default:
        return <Home onNavigate={setCurrentPage} />;
    }
  };

  return (
    <>
      <Navbar currentPage={currentPage} onNavigate={setCurrentPage} />
      <main style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        {renderPage()}
      </main>
      <Footer />
    </>
  );
}
