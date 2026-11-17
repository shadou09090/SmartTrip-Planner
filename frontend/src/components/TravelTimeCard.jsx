import React, { useState, useMemo } from 'react';
import {
  Footprints,
  Bus,
  Car,
  AlertTriangle,
  Zap,
  Navigation,
  ArrowRight,
  Clock,
  Info
} from 'lucide-react';
import { getEstimatedTravelTimes } from '../services/travelTimeService';

export default function TravelTimeCard({
  originActivity,
  destinationActivity
}) {
  const [selectedMode, setSelectedMode] = useState('driving');

  const travelData = useMemo(() => {
    if (!originActivity || !destinationActivity) return null;
    return getEstimatedTravelTimes(originActivity, destinationActivity);
  }, [originActivity, destinationActivity]);

  if (!travelData) return null;

  const { distanceText, modes, hasAnyExceeds120, fromCache } = travelData;

  const originTitle = originActivity.texto || originActivity.ubicacion || 'Actividad anterior';
  const destTitle = destinationActivity.texto || destinationActivity.ubicacion || 'Siguiente actividad';

  return (
    <div className={`travel-time-card ${hasAnyExceeds120 ? 'has-warning' : ''}`} aria-label="Tiempo de traslado entre actividades">
      {/* Conectores visuales superior e inferior */}
      <div className="travel-connector-line" />

      <div className="travel-card-inner">
        {/* Encabezado del traslado */}
        <div className="travel-card-header">
          <div className="travel-route-info">
            <span className="travel-badge">
              <Navigation size={13} /> Traslado entre actividades
            </span>
            <div className="travel-endpoints">
              <span className="endpoint-origin" title={originTitle}>
                {originTitle}
              </span>
              <ArrowRight size={14} className="endpoint-arrow" />
              <span className="endpoint-dest" title={destTitle}>
                {destTitle}
              </span>
            </div>
          </div>

          <div className="travel-meta-tags">
            <span className="travel-distance-tag">
              Distancia aprox: <strong>{distanceText}</strong>
            </span>
            {fromCache && (
              <span className="travel-cache-tag" title="Respuesta instantánea desde memoria / almacenamiento previo (Criterio 3)">
                <Zap size={12} /> Sin demora perceptible (Caché)
              </span>
            )}
          </div>
        </div>

        {/* Tarjetas de los tres modos (Criterio 1: a pie, transporte público, automóvil) */}
        <div className="travel-modes-grid">
          {/* 1. A pie */}
          <div
            className={`travel-mode-item ${selectedMode === 'walking' ? 'active' : ''} ${
              modes.walking.exceeds120 ? 'mode-warning' : ''
            }`}
            onClick={() => setSelectedMode('walking')}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') setSelectedMode('walking');
            }}
          >
            <div className="mode-header">
              <div className="mode-icon-title">
                <Footprints size={18} className="mode-icon" />
                <span className="mode-name">{modes.walking.label}</span>
              </div>
            </div>

            <div className="mode-time-container">
              <span className="mode-time">{modes.walking.timeFormatted}</span>
              {/* Criterio 2: Advertencia visual si supera 120 minutos */}
              {modes.walking.exceeds120 && (
                <span className="warning-pill" title="El tiempo de traslado supera los 120 minutos (2 horas)">
                  <AlertTriangle size={13} /> +120 min
                </span>
              )}
            </div>

            <span className="mode-note">{modes.walking.speedNote}</span>
          </div>

          {/* 2. Transporte público */}
          <div
            className={`travel-mode-item ${selectedMode === 'transit' ? 'active' : ''} ${
              modes.transit.exceeds120 ? 'mode-warning' : ''
            }`}
            onClick={() => setSelectedMode('transit')}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') setSelectedMode('transit');
            }}
          >
            <div className="mode-header">
              <div className="mode-icon-title">
                <Bus size={18} className="mode-icon" />
                <span className="mode-name">{modes.transit.label}</span>
              </div>
            </div>

            <div className="mode-time-container">
              <span className="mode-time">{modes.transit.timeFormatted}</span>
              {/* Criterio 2: Advertencia visual si supera 120 minutos */}
              {modes.transit.exceeds120 && (
                <span className="warning-pill" title="El tiempo de traslado supera los 120 minutos (2 horas)">
                  <AlertTriangle size={13} /> +120 min
                </span>
              )}
            </div>

            <span className="mode-note">{modes.transit.speedNote}</span>
          </div>

          {/* 3. En automóvil */}
          <div
            className={`travel-mode-item ${selectedMode === 'driving' ? 'active' : ''} ${
              modes.driving.exceeds120 ? 'mode-warning' : ''
            }`}
            onClick={() => setSelectedMode('driving')}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') setSelectedMode('driving');
            }}
          >
            <div className="mode-header">
              <div className="mode-icon-title">
                <Car size={18} className="mode-icon" />
                <span className="mode-name">{modes.driving.label}</span>
              </div>
            </div>

            <div className="mode-time-container">
              <span className="mode-time">{modes.driving.timeFormatted}</span>
              {/* Criterio 2: Advertencia visual si supera 120 minutos */}
              {modes.driving.exceeds120 && (
                <span className="warning-pill" title="El tiempo de traslado supera los 120 minutos (2 horas)">
                  <AlertTriangle size={13} /> +120 min
                </span>
              )}
            </div>

            <span className="mode-note">{modes.driving.speedNote}</span>
          </div>
        </div>

        {/* Criterio 2: Mensaje de advertencia para evaluar viabilidad de la planificación */}
        {hasAnyExceeds120 && (
          <div className="travel-warning-banner" role="alert">
            <AlertTriangle size={18} className="warning-banner-icon" />
            <div className="warning-banner-text">
              <strong>Advertencia de traslado prolongado:</strong>
              <span>
                {modes[selectedMode].exceeds120
                  ? `El traslado seleccionado en este modo toma más de 120 minutos (${modes[selectedMode].timeFormatted}). Evalúa si esta planificación diaria es viable o considera reorganizar el orden de las actividades.`
                  : 'Al menos uno de los medios de traslado disponibles supera los 120 minutos. Planifica con anticipación para asegurar la viabilidad de tu día.'}
              </span>
            </div>
          </div>
        )}
      </div>

      <div className="travel-connector-line" />
    </div>
  );
}
