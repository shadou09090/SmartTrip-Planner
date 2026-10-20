import React, { useEffect, useMemo, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { ArrowLeft, LocateFixed, MapPin, Navigation } from 'lucide-react';
import { Button } from '../components/ui';

const DESTINATIONS = [
  {
    keys: ['cartagena'],
    name: 'Cartagena, Colombia',
    center: [10.391, -75.4794],
    places: [
      { id: 'ciudad-amurallada', name: 'Ciudad Amurallada', location: 'Centro histórico de Cartagena', coordinates: [10.4236, -75.5503] },
      { id: 'castillo-san-felipe', name: 'Castillo de San Felipe de Barajas', location: 'Pie del Cerro, Cartagena', coordinates: [10.4225, -75.5392] },
      { id: 'getsemani', name: 'Barrio Getsemaní', location: 'Getsemaní, Cartagena', coordinates: [10.4204, -75.5445] },
      { id: 'bocagrande', name: 'Playa de Bocagrande', location: 'Bocagrande, Cartagena', coordinates: [10.4012, -75.557] },
    ],
  },
  {
    keys: ['bogota', 'bogotá'],
    name: 'Bogotá, Colombia',
    center: [4.6486, -74.0822],
    places: [
      { id: 'monserrate', name: 'Monserrate', location: 'Cerro de Monserrate, Bogotá', coordinates: [4.6057, -74.0555] },
      { id: 'museo-oro', name: 'Museo del Oro', location: 'La Candelaria, Bogotá', coordinates: [4.6019, -74.072] },
      { id: 'plaza-bolivar', name: 'Plaza de Bolívar', location: 'Centro histórico, Bogotá', coordinates: [4.5981, -74.0758] },
      { id: 'jardin-botanico', name: 'Jardín Botánico de Bogotá', location: 'Engativá, Bogotá', coordinates: [4.6687, -74.1005] },
    ],
  },
  {
    keys: ['medellin', 'medellín'],
    name: 'Medellín, Colombia',
    center: [6.2442, -75.5812],
    places: [
      { id: 'comuna-13', name: 'Comuna 13', location: 'San Javier, Medellín', coordinates: [6.2566, -75.6217] },
      { id: 'plaza-botero', name: 'Plaza Botero', location: 'La Candelaria, Medellín', coordinates: [6.2525, -75.5684] },
      { id: 'parque-arvi', name: 'Parque Arví', location: 'Santa Elena, Medellín', coordinates: [6.2811, -75.502] },
      { id: 'pueblito-paisa', name: 'Pueblito Paisa', location: 'Cerro Nutibara, Medellín', coordinates: [6.2351, -75.5805] },
    ],
  },
  {
    keys: ['santa marta'],
    name: 'Santa Marta, Colombia',
    center: [11.2408, -74.199],
    places: [
      { id: 'parque-tayrona', name: 'Parque Nacional Natural Tayrona', location: 'Magdalena, cerca de Santa Marta', coordinates: [11.306, -73.922] },
      { id: 'quinta-san-pedro', name: 'Quinta de San Pedro Alejandrino', location: 'Mamatoco, Santa Marta', coordinates: [11.2284, -74.178] },
      { id: 'bahia-santa-marta', name: 'Bahía de Santa Marta', location: 'Centro histórico, Santa Marta', coordinates: [11.2435, -74.2167] },
      { id: 'taganga', name: 'Taganga', location: 'Distrito de Santa Marta, Magdalena', coordinates: [11.2672, -74.191] },
    ],
  },
];

const FALLBACK_DESTINATION = {
  name: 'Destino seleccionado',
  center: [4.5709, -74.2973],
  places: [],
};

export default function MapPlaces({ trip, onBack }) {
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

function findDestination(destination) {
  const normalizedDestination = destination.toLocaleLowerCase('es');
  return DESTINATIONS.find(({ keys }) => (
    keys.some((key) => normalizedDestination.includes(key))
  )) || null;
}
