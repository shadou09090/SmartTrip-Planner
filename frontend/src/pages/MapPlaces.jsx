import React, { useEffect, useMemo, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { ArrowLeft, CalendarDays, Check, LocateFixed, MapPin, Navigation } from 'lucide-react';
import { Button } from '../components/ui';
import { DESTINATIONS, FALLBACK_DESTINATION, findDestination } from '../data/destinations';

export default function MapPlaces({ trip, onBack, onAddActivityToItinerary, onGoToItinerary }) {
  const mapElementRef = useRef(null);
  const mapRef = useRef(null);
  const markersRef = useRef(new Map());
  const [selectedPlace, setSelectedPlace] = useState(null);
  const [destination, setDestination] = useState(() => findDestination(trip.destination));
  const [locationStatus, setLocationStatus] = useState(destination ? 'ready' : 'loading');

  const mapData = useMemo(() => destination || {
    ...FALLBACK_DESTINATION,
    name: trip.destination,
  }, [destination, trip.destination]);

  useEffect(() => {
    const knownDestination = findDestination(trip.destination);
    if (knownDestination) {
      setDestination(knownDestination);
      setLocationStatus('ready');
      return undefined;
    }

    const controller = new AbortController();
    setLocationStatus('loading');

    fetch(`https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(trip.destination)}`, {
      signal: controller.signal,
      headers: { 'Accept-Language': 'es' },
    })
      .then((response) => {
        if (!response.ok) throw new Error('No fue posible consultar el destino.');
        return response.json();
      })
      .then(([result]) => {
        if (!result) throw new Error('Destino no encontrado.');
        setDestination({
          name: result.display_name,
          center: [Number(result.lat), Number(result.lon)],
          places: [],
        });
        setLocationStatus('ready');
      })
      .catch((error) => {
        if (error.name !== 'AbortError') setLocationStatus('error');
      });

    return () => controller.abort();
  }, [trip.destination]);

  useEffect(() => {
    if (!mapElementRef.current) return undefined;

    if (mapRef.current) {
      mapRef.current.remove();
      mapRef.current = null;
    }

    const map = L.map(mapElementRef.current, {
      zoomControl: true,
      scrollWheelZoom: true,
    }).setView(mapData.center, mapData.places.length ? 12 : 10);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(map);

    const destinationMarker = L.circleMarker(mapData.center, {
      radius: 10,
      color: '#ffffff',
      weight: 3,
      fillColor: '#2563eb',
      fillOpacity: 1,
    }).addTo(map);
    destinationMarker.bindTooltip(`Destino: ${trip.destination}`, { direction: 'top' });

    markersRef.current.clear();
    mapData.places.forEach((place, index) => {
      const marker = L.marker(place.coordinates, {
        icon: L.divIcon({
          className: 'recommendation-marker-wrapper',
          html: `<span class="recommendation-marker"><b>${index + 1}</b></span>`,
          iconSize: [36, 42],
          iconAnchor: [18, 42],
        }),
      }).addTo(map);

      marker.bindTooltip(place.name, { direction: 'top', offset: [0, -35] });
      marker.on('click', () => setSelectedPlace(place));
      markersRef.current.set(place.id, marker);
    });

    if (mapData.places.length) {
      map.fitBounds([mapData.center, ...mapData.places.map((place) => place.coordinates)], {
        padding: [45, 45],
      });
    }

    mapRef.current = map;
    window.setTimeout(() => map.invalidateSize(), 0);

    return () => {
      map.remove();
      mapRef.current = null;
      markersRef.current.clear();
    };
  }, [mapData, trip.destination]);

  const selectPlace = (place) => {
    setSelectedPlace(place);
    mapRef.current?.flyTo(place.coordinates, 15, { duration: 0.6 });
    markersRef.current.get(place.id)?.openTooltip();
  };

  return (
    <section className="map-page">
      <div className="map-page-header">
        <Button variant="secondary" onClick={onBack}>
          <ArrowLeft size={18} /> Volver al viaje
        </Button>
        <div>
          <p className="eyebrow">Explora tu destino</p>
          <h1>Lugares recomendados en {trip.destination}</h1>
          <p>Selecciona un marcador para consultar la información del lugar.</p>
        </div>
      </div>

      <div className="map-layout">
        <aside className="places-panel" aria-label="Lugares recomendados">
          <div className="destination-card">
            <span className="destination-symbol"><LocateFixed size={20} /></span>
            <div>
              <small>Destino seleccionado</small>
              <strong>{trip.destination}</strong>
              <span>{locationStatus === 'loading' ? 'Buscando ubicación…' : mapData.name}</span>
            </div>
          </div>

          <h2>Lugares recomendados</h2>
          {mapData.places.length > 0 ? (
            <ol className="places-list">
              {mapData.places.map((place, index) => (
                <li key={place.id}>
                  <button
                    type="button"
                    className={selectedPlace?.id === place.id ? 'active' : ''}
                    onClick={() => selectPlace(place)}
                  >
                    <span className="place-number">{index + 1}</span>
                    <span>
                      <strong>{place.name}</strong>
                      <small>{place.location}</small>
                    </span>
                  </button>
                </li>
              ))}
            </ol>
          ) : (
            <p className="places-empty">
              {locationStatus === 'error'
                ? 'No pudimos localizar este destino. Verifica el nombre e inténtalo nuevamente.'
                : locationStatus === 'loading'
                  ? 'Estamos ubicando tu destino en el mapa.'
                  : 'Todavía no hay lugares recomendados para este destino.'}
            </p>
          )}
        </aside>

        <div className="map-content">
          <div ref={mapElementRef} className="places-map" aria-label={`Mapa de ${trip.destination}`} />

          <div className={`place-detail ${selectedPlace ? 'visible' : ''}`} aria-live="polite">
            {selectedPlace ? (
              <>
                <span className="place-detail-icon"><MapPin size={22} /></span>
                <div>
                  <small>Lugar seleccionado</small>
                  <h2>{selectedPlace.name}</h2>
                  <p><Navigation size={16} /> {selectedPlace.location}</p>
                </div>
                {onAddActivityToItinerary && (
                  <div style={{ marginLeft: 'auto', display: 'flex', gap: '8px', alignItems: 'center' }}>
                    <Button
                      variant="primary"
                      onClick={() => onAddActivityToItinerary(selectedPlace.name, selectedPlace.location)}
                    >
                      <CalendarDays size={16} /> Añadir a mi Itinerario
                    </Button>
                  </div>
                )}
              </>
            ) : (
              <p><MapPin size={18} /> Selecciona un marcador para ver el nombre y la ubicación.</p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
