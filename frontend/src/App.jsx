import React, { useState } from 'react';
import { Navbar, Footer } from './components/layout';
import { CreateTrip, Home, Login, MapPlaces, Register } from './pages';
import GeneradorItinerario from './components/GeneradorItinerario';

export default function App() {
  const [currentPage, setCurrentPage] = useState('home');
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [activeTrip, setActiveTrip] = useState(null);

  const handleLogin = () => {
    setIsAuthenticated(true);
    setCurrentPage('create-trip');
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setActiveTrip(null);
    setCurrentPage('home');
  };

  const handleNavigate = (page) => {
    if ((page === 'create-trip' || page === 'map') && !isAuthenticated) {
      setCurrentPage('login');
      return;
    }

    if (page === 'map' && !activeTrip) {
      setCurrentPage('create-trip');
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
          ? (
            <CreateTrip
              initialTrip={activeTrip}
              onTripCreated={setActiveTrip}
              onResetTrip={() => setActiveTrip(null)}
              onViewMap={() => setCurrentPage('map')}
            />
          )
          : <Login onNavigate={handleNavigate} onLogin={handleLogin} />;
      case 'map':
        return isAuthenticated && activeTrip
          ? <MapPlaces trip={activeTrip} onBack={() => setCurrentPage('create-trip')} />
          : <CreateTrip />;
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
        hasTrip={Boolean(activeTrip)}
        onNavigate={handleNavigate}
        onLogout={handleLogout}
      />
      <main style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        <GeneradorItinerario />
        {renderPage()}
      </main>
      <Footer />
    </>
  );
}
