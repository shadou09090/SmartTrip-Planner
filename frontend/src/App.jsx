import React, { useState } from 'react';
import { Navbar, Footer } from './components/layout';
import { CreateTrip, Home, Login, Register } from './pages';
import GeneradorItinerario from './components/GeneradorItinerario';

export default function App() {
  const [currentPage, setCurrentPage] = useState('home');
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [selectedTrip, setSelectedTrip] = useState(null);

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

    // Si se navega a itinerario manualmente sin viaje previo, permitir crear uno nuevo
    if (page !== 'itinerary' && currentPage === 'itinerary') {
      // mantener estado si se desea
    }

    setCurrentPage(page);
  };

  const handleGoToItinerary = (trip) => {
    setSelectedTrip(trip);
    setCurrentPage('itinerary');
  };

  const renderPage = () => {
    switch (currentPage) {
      case 'login':
        return <Login onNavigate={handleNavigate} onLogin={handleLogin} />;
      case 'register':
        return <Register onNavigate={handleNavigate} />;
      case 'create-trip':
        return isAuthenticated
          ? <CreateTrip onGoToItinerary={handleGoToItinerary} />
          : <Login onNavigate={handleNavigate} onLogin={handleLogin} />;
      case 'itinerary':
        return (
          <GeneradorItinerario
            tripData={selectedTrip}
            onNavigate={handleNavigate}
          />
        );
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
