import React from 'react';
import { Compass, LogIn, LogOut, Map, MapPin, UserPlus, Home as HomeIcon, CalendarDays } from 'lucide-react';
import { Button } from '../ui';

export default function Navbar({ currentPage, isAuthenticated, hasTrip, onNavigate, onLogout, isLocalDevSession = false }) {
  return (
    <nav className="navbar">
      <button
        type="button"
        className="nav-brand"
        onClick={() => onNavigate('home')}
        aria-label="SmartTrip Planner, ir al inicio"
      >
        <span className="brand-icon">
          <Compass size={24} />
        </span>
        <span>SmartTrip Planner</span>
      </button>

      <div className="nav-links">
        {currentPage !== 'home' && (
          <Button variant="secondary" onClick={() => onNavigate('home')}>
            <HomeIcon size={18} />
            Inicio
          </Button>
        )}

        <Button
          variant={currentPage === 'itinerary' ? 'primary' : 'secondary'}
          onClick={() => onNavigate('itinerary')}
        >
          <CalendarDays size={18} />
          Itinerario
        </Button>
        
        {isAuthenticated && isLocalDevSession ? (
          <>
            <Button variant="primary" onClick={() => onNavigate('local-duffel')}>
              Búsqueda Duffel local
            </Button>
            <Button variant="secondary" onClick={onLogout}>
              <LogOut size={18} />
              Cerrar sesión
            </Button>
          </>
        ) : isAuthenticated ? (
          <>
            <Button
              variant={currentPage === 'create-trip' ? 'primary' : 'secondary'}
              onClick={() => onNavigate('create-trip')}
            >
              <MapPin size={18} />
              Crear viaje
            </Button>
            <Button
              variant={currentPage === 'trips' ? 'primary' : 'secondary'}
              onClick={() => onNavigate('trips')}
            >
              Mis viajes
            </Button>
            {hasTrip && (
              <Button
                variant={currentPage === 'map' ? 'primary' : 'secondary'}
                onClick={() => onNavigate('map')}
              >
                <Map size={18} />
                Mapa
              </Button>
            )}
            <Button variant="secondary" onClick={onLogout}>
              <LogOut size={18} />
              Cerrar sesión
            </Button>
          </>
        ) : (
          <>
            <Button
              variant={currentPage === 'login' ? 'primary' : 'secondary'}
              onClick={() => onNavigate('login')}
            >
              <LogIn size={18} />
              Iniciar sesión
            </Button>

            <Button
              variant={currentPage === 'register' ? 'primary' : 'secondary'}
              onClick={() => onNavigate('register')}
            >
              <UserPlus size={18} />
              Registrarse
            </Button>
          </>
        )}
      </div>
    </nav>
  );
}
