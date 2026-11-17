import React, { useEffect, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import {
  Check,
  Clock,
  Compass,
  MapPin,
  Navigation,
  Sparkles,
  X,
} from 'lucide-react';
import { findDestination, FALLBACK_DESTINATION } from '../data/destinations';
import { Button } from './ui';

export default function MapActivityPicker({
  destinationName = '',
  dayNumber = 1,
  onSelectPlace,
  onClose,
}) {
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const markersMapRef = useRef(new Map());
  const customMarkerRef = useRef(null);

  const [destInfo, setDestInfo] = useState(() => findDestination(destinationName) || FALLBACK_DESTINATION);
  const [selectedPlace, setSelectedPlace] = useState(null);
  const [hora, setHora] = useState('');
  const [loadingGeo, setLoadingGeo] = useState(false);

  // Intentar geolocalizar el destino si no está en la base predefinida
  useEffect(() => {
    const known = findDestination(destinationName);
    if (known) {
      setDestInfo(known);
      return;
    }

    if (!destinationName.trim()) return;

    setLoadingGeo(true);
    const controller = new AbortController();

    fetch(
      `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(destinationName)}`,
      { signal: controller.signal, headers: { 'Accept-Language': 'es' } }
    )
      .then((res) => res.json())
      .then(([item]) => {
        if (item) {
          setDestInfo({
            name: item.display_name,
            center: [Number(item.lat), Number(item.lon)],
            places: [],
          });
        }
      })
      .catch(() => {})
      .finally(() => setLoadingGeo(false));

    return () => controller.abort();
  }, [destinationName]);

  // Inicializar Leaflet map
  useEffect(() => {
    if (!mapContainerRef.current) return;

    if (mapInstanceRef.current) {
      mapInstanceRef.current.remove();
      mapInstanceRef.current = null;
    }

    const initialCenter = destInfo.center || [4.5709, -74.2973];
    const initialZoom = destInfo.places?.length ? 13 : 11;

    const map = L.map(mapContainerRef.current, {
      zoomControl: true,
      scrollWheelZoom: true,
    }).setView(initialCenter, initialZoom);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
      maxZoom: 19,
    }).addTo(map);

    markersMapRef.current.clear();

    // Crear marcadores para lugares sugeridos
    if (destInfo.places && destInfo.places.length > 0) {
      destInfo.places.forEach((place, index) => {
        const marker = L.marker(place.coordinates, {
          icon: L.divIcon({
            className: 'picker-marker-wrapper',
            html: `<span class="picker-marker"><b>${index + 1}</b></span>`,
            iconSize: [32, 38],
            iconAnchor: [16, 38],
          }),
        }).addTo(map);

        marker.bindTooltip(place.name, { direction: 'top', offset: [0, -32] });

        marker.on('click', () => {
          setSelectedPlace(place);
        });

        markersMapRef.current.set(place.id, marker);
      });

      map.fitBounds([destInfo.center, ...destInfo.places.map((p) => p.coordinates)], {
        padding: [30, 30],
      });
    }

    // Permitir hacer clic en cualquier lugar del mapa para crear un punto personalizado
    map.on('click', (e) => {
      const { lat, lng } = e.latlng;
      if (customMarkerRef.current) {
        customMarkerRef.current.remove();
      }

      const newCustomMarker = L.circleMarker([lat, lng], {
        radius: 9,
        color: '#2563eb',
        fillColor: '#3b82f6',
        fillOpacity: 0.9,
        weight: 3,
      }).addTo(map);

      newCustomMarker.bindTooltip('Ubicación seleccionada', { direction: 'top' }).openTooltip();
      customMarkerRef.current = newCustomMarker;

      setSelectedPlace({
        id: 'custom-spot',
        name: `Lugar en (${lat.toFixed(3)}, ${lng.toFixed(3)})`,
        location: `${destinationName || 'Destino'}`,
        category: 'Personalizado',
      });
    });

    mapInstanceRef.current = map;
    window.setTimeout(() => map.invalidateSize(), 150);

    return () => {
      map.remove();
      mapInstanceRef.current = null;
      markersMapRef.current.clear();
      if (customMarkerRef.current) {
        customMarkerRef.current = null;
      }
    };
  }, [destInfo]);

  const handleSelectPlaceCard = (place) => {
    setSelectedPlace(place);
    if (mapInstanceRef.current && place.coordinates) {
      mapInstanceRef.current.flyTo(place.coordinates, 15, { duration: 0.5 });
      markersMapRef.current.get(place.id)?.openTooltip();
    }
  };

  const handleConfirmAdd = () => {
    if (!selectedPlace) return;
    onSelectPlace({
      texto: selectedPlace.name,
      ubicacion: selectedPlace.location || '',
      hora: hora.trim() || undefined,
    });
    onClose();
  };

  return (
    <div className="map-picker-backdrop" role="dialog" aria-modal="true" aria-labelledby="picker-title">
      <div className="map-picker-modal">
        {/* Cabecera del modal */}
        <div className="map-picker-header">
          <div>
            <span className="eyebrow">
              <Compass size={14} style={{ display: 'inline', marginRight: 4 }} />
              Explorador en el mapa
            </span>
            <h3 id="picker-title">
              Seleccionar actividad para el Día {dayNumber}
            </h3>
            <p>
              Haz clic en cualquier punto del mapa o selecciona uno de los lugares sugeridos en {destinationName || 'tu destino'}.
            </p>
          </div>
          <button
            type="button"
            className="btn-picker-close"
            onClick={onClose}
            aria-label="Cerrar mapa"
          >
            <X size={20} />
          </button>
        </div>

        {/* Contenido principal: Lista de lugares a la izquierda y mapa a la derecha */}
        <div className="map-picker-body">
          <aside className="picker-places-list" aria-label="Lugares sugeridos">
            <h4>
              <Sparkles size={16} /> Lugares recomendados ({destInfo.places?.length || 0})
            </h4>

            {destInfo.places && destInfo.places.length > 0 ? (
              <div className="picker-cards-scroll">
                {destInfo.places.map((place, index) => {
                  const isSelected = selectedPlace?.id === place.id;
                  return (
                    <div
                      key={place.id}
                      className={`picker-place-card ${isSelected ? 'selected' : ''}`}
                      onClick={() => handleSelectPlaceCard(place)}
                      role="button"
                      tabIndex={0}
                    >
                      <div className="picker-place-number">{index + 1}</div>
                      <div className="picker-place-info">
                        <strong>{place.name}</strong>
                        <small>{place.location}</small>
                        {place.category && <span className="picker-category-tag">{place.category}</span>}
                      </div>
                      {isSelected && (
                        <span className="picker-check-badge">
                          <Check size={16} />
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="picker-empty-places">
                <p>
                  {loadingGeo
                    ? 'Cargando coordenadas del destino...'
                    : 'Haz clic directamente en el mapa para marcar el punto donde deseas realizar tu actividad.'}
                </p>
              </div>
            )}
          </aside>

          <div className="picker-map-wrapper">
            <div ref={mapContainerRef} className="picker-leaflet-map" />
          </div>
        </div>

        {/* Barra inferior de confirmación */}
        <div className="map-picker-footer">
          <div className="picker-selected-summary">
            {selectedPlace ? (
              <div className="picker-selection-info">
                <MapPin size={20} className="selection-pin-icon" />
                <div>
                  <small>Lugar seleccionado:</small>
                  <strong>{selectedPlace.name}</strong>
                </div>
              </div>
            ) : (
              <span className="picker-no-selection">
                👉 Selecciona un marcador en el mapa o haz clic en un lugar de la lista.
              </span>
            )}
          </div>

          <div className="picker-actions-group">
            <div className="picker-time-input">
              <Clock size={16} />
              <input
                type="text"
                placeholder="Hora (ej. 10:00 AM)"
                value={hora}
                onChange={(e) => setHora(e.target.value)}
              />
            </div>

            <Button
              type="button"
              variant="secondary"
              onClick={onClose}
            >
              Cancelar
            </Button>

            <Button
              type="button"
              variant="primary"
              disabled={!selectedPlace}
              onClick={handleConfirmAdd}
            >
              <Check size={18} /> Añadir al Día {dayNumber}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
