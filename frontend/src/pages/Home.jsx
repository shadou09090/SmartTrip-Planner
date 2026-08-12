import React from 'react';
import { MapPin, Calendar, Sparkles, ArrowRight, ShieldCheck } from 'lucide-react';
import { Button, FeatureCard } from '../components/ui';

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
        <Button 
          variant="primary" 
          style={{ padding: '14px 28px', fontSize: '1rem' }}
          onClick={() => onNavigate('register')}
        >
          Empezar gratis
          <ArrowRight size={18} />
        </Button>

        <Button 
          variant="3d" 
          onClick={() => onNavigate('login')}
        >
          Iniciar sesión
        </Button>
      </div>

      <div className="features-grid">
        <FeatureCard
          icon={MapPin}
          title="Itinerarios Automáticos"
          description="Crea rutas optimizadas según tus preferencias de viaje, presupuestos y tiempos disponibles."
        />

        <FeatureCard
          icon={Calendar}
          title="Gestión de Calendario"
          description="Organiza todas tus actividades, vuelos y hoteles en un solo lugar sincronizado en tiempo real."
        />

        <FeatureCard
          icon={ShieldCheck}
          title="Acceso Seguro"
          description="Tus datos e itinerarios siempre protegidos y disponibles desde cualquier dispositivo."
        />
      </div>
    </div>
  );
}
