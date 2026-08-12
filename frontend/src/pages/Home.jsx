import React from 'react';
import { MapPin, Calendar, Sparkles, ArrowRight, ShieldCheck, Zap } from 'lucide-react';

export default function Home({ onNavigate }) {
  return (
    <div className="hero">
      <div className="badge">
        <Sparkles size={16} />
        Planificación de Viajes Inteligente
      </div>

      <h1 className="hero-title">
        Diseña tu viaje perfecto con <span>SmartTrip Planner</span>
      </h1>

      <p className="hero-description">
        Organiza itinerarios personalizados, gestiona tus reservas y descubre destinos increíbles con asistencia inteligente paso a paso.
      </p>

      <div className="hero-buttons">
        <button 
          className="nav-btn nav-btn-primary" 
          style={{ padding: '14px 28px', fontSize: '1rem' }}
          onClick={() => onNavigate('register')}
        >
          Empezar gratis
          <ArrowRight size={18} />
        </button>

        <button 
          className="btn-3d" 
          onClick={() => onNavigate('login')}
        >
          Iniciar sesión
        </button>
      </div>

      <div className="features-grid">
        <div className="feature-card">
          <div className="feature-icon">
            <MapPin size={24} />
          </div>
          <h3>Itinerarios Automáticos</h3>
          <p>Crea rutas optimizadas según tus preferencias de viaje, presupuestos y tiempos disponibles.</p>
        </div>

        <div className="feature-card">
          <div className="feature-icon">
            <Calendar size={24} />
          </div>
          <h3>Gestión de Calendario</h3>
          <p>Organiza todas tus actividades, vuelos y hoteles en un solo lugar sincronizado en tiempo real.</p>
        </div>

        <div className="feature-card">
          <div className="feature-icon">
            <ShieldCheck size={24} />
          </div>
          <h3>Acceso Seguro</h3>
          <p>Tus datos e itinerarios siempre protegidos y disponibles desde cualquier dispositivo.</p>
        </div>
      </div>
    </div>
  );
}
