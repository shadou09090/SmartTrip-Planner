import React, { useEffect, useMemo, useRef, useState } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { ArrowLeft, LocateFixed, MapPin, Navigation, BedDouble, LayoutGrid, Map as MapIcon, Search, ExternalLink, Maximize2, Minimize2, Crosshair } from 'lucide-react';
import { Button } from '../components/ui';
import LodgingSuggestionCard from '../components/LodgingSuggestionCard';
import { apiRequest } from '../lib/api';
import { formatHotelPrice, freshHotelLivePrices, isHotelQuoteFresh, normalizeHotelLivePrices } from '../lib/hotelLivePrices';
import { coordinates, distanceKm, normalizeDraftItineraryPlaces, normalizeLodgingSuggestions, normalizeRecommendations, normalizeSavedItineraryPlaces } from './mapPlacesData';
import { useTripPackageWorkspace, useWorkspaceState } from '../components/TripPackageWorkspace';

export default function MapPlaces({ trip, token, onBack, onPackageLodgingSelect, onAddActivityToItinerary, onGoToItinerary }) {
  const workspace = useTripPackageWorkspace();
  const mapElementRef = useRef(null);
  const mapRef = useRef(null);
  const markersRef = useRef(new globalThis.Map());
  const wheelZoomEnabledRef = useRef(false);
  const [selectedPlace, setSelectedPlace] = useState(null);
  const [mapExpanded, setMapExpanded] = useState(false);
  const [wheelZoomEnabled, setWheelZoomEnabled] = useState(false);
  const [selectedMapDayId, setSelectedMapDayId] = useWorkspaceState('selectedMapDayId', 'all');
  const [selectedMapActivityId, setSelectedMapActivityId] = useWorkspaceState('selectedMapActivityId', null);
  const [destinationLocation, setDestinationLocation] = useWorkspaceState('destinationLocation', null);
  const [destination, setDestination] = useState(() => destinationLocation?.name === trip.destination ? destinationLocation : null);
  const [lodging, setLodging] = useState(null);
  const [lodgingSuggestions, setLodgingSuggestions] = useState([]);
  const [lodgingStatus, setLodgingStatus] = useState('loading');
  const [lodgingError, setLodgingError] = useState('');
  const [lodgingNotice, setLodgingNotice] = useState('');
  const [hotelPrices, setHotelPrices] = useState(null);
  const [hotelPriceStatus, setHotelPriceStatus] = useState('idle');
  const [hotelPriceError, setHotelPriceError] = useState('');
  const [savingLodging, setSavingLodging] = useState(false);
  const [categoryFilter, setCategoryFilter] = useState('all');
  const [lodgingSearch, setLodgingSearch] = useState('');
  const [lodgingTypeFilter, setLodgingTypeFilter] = useState(() => trip.lodgingPreference || 'all');
  const [lodgingSort, setLodgingSort] = useState('name');
  const [lodgingView, setLodgingView] = useState('map');
  const [selectedLodging, setSelectedLodging] = useWorkspaceState('selectedLodging', () => {
    try { return JSON.parse(sessionStorage.getItem(`smarttrip-lodging-draft-${trip.id}`) || 'null'); }
    catch { return null; }
  });
  const [locationStatus, setLocationStatus] = useState('loading');
  const [recommendedPlaces, setRecommendedPlaces] = useState([]);
  const [savedItineraryPlaces, setSavedItineraryPlaces] = useState([]);
  const [savedItineraryDays, setSavedItineraryDays] = useState([]);
  const [recommendationWarnings, setRecommendationWarnings] = useState([]);
  const [recommendationError, setRecommendationError] = useState('');
  const [locationError, setLocationError] = useState('');
  const [mapError, setMapError] = useState('');
  const [unmappedPlaceCount, setUnmappedPlaceCount] = useState(0);
  const [reloadKey, setReloadKey] = useState(0);
  const [itineraryReloadKey, setItineraryReloadKey] = useState(0);
  const [mapAttempt, setMapAttempt] = useState(0);
  const normalizedHotelOffers = useMemo(() => normalizeHotelLivePrices(hotelPrices), [hotelPrices]);
  const liveHotelOffers = useMemo(() => freshHotelLivePrices(hotelPrices), [hotelPrices]);

  useEffect(() => {
    if (!hotelPrices?.retrievedAt) return undefined;
    const expiresAt = Date.parse(hotelPrices.expiresAt || '') || (Date.parse(hotelPrices.retrievedAt) + 30 * 60 * 1000);
    const remainingMs = expiresAt - Date.now();
    if (remainingMs <= 0) {
      setHotelPrices(null);
      setHotelPriceStatus('expired');
      return undefined;
    }
    const timer = setTimeout(() => {
      setHotelPrices(null);
      setHotelPriceStatus('expired');
    }, remainingMs);
    return () => clearTimeout(timer);
  }, [hotelPrices]);

  const searchHotelPrices = async () => {
    if (hotelPriceStatus === 'loading') return;
    setHotelPriceStatus('loading'); setHotelPriceError(''); setHotelPrices(null);
    const guests = Math.min(8, Math.max(1, Number(trip.travelers || trip.numeroViajeros || 1)));
    const currency = /^[A-Z]{3}$/.test(trip.budgetCurrency || '') ? trip.budgetCurrency : 'COP';
    try {
      const result = await apiRequest(`/api/viajes/${trip.id}/alojamientos/tarifas?guests=${guests}&rooms=1&currency=${currency}`, { token });
      setHotelPrices(result);
      setHotelPriceStatus('ready');
    } catch (requestError) {
      setHotelPriceError(requestError.message || 'No se pudieron consultar tarifas ahora.');
      setHotelPriceStatus('error');
    }
  };

  const draftPlaces = useMemo(() => normalizeDraftItineraryPlaces(workspace?.effectiveItineraryDays || []),
    [workspace?.effectiveItineraryDays]);
  const itineraryDays = workspace?.effectiveItineraryDays?.length ? workspace.effectiveItineraryDays : savedItineraryDays;
  const itineraryActivityCount = itineraryDays.reduce((count, day) => count + (day?.actividades || []).length, 0);
  const unmappedItineraryCount = useMemo(() => (workspace?.effectiveItineraryDays || [])
    .reduce((count, day) => count + (day?.actividades || []).filter((activity) => !coordinates(activity?.latitud, activity?.longitud)).length, 0),
  [workspace?.effectiveItineraryDays]);
  const savedPlaceIds = useMemo(() => new Set(savedItineraryPlaces.map((place) => place.id)), [savedItineraryPlaces]);
  const itineraryDayOptions = useMemo(() => {
    const days = workspace?.effectiveItineraryDays || [];
    const rows = new Map();
    days.forEach((day, index) => rows.set(String(day.id ?? day.fecha ?? index), {
      id: String(day.id ?? day.fecha ?? index), number: index + 1, date: day.fecha || null,
    }));
    savedItineraryPlaces.forEach((place) => {
      if (place.dayId != null && !rows.has(String(place.dayId))) rows.set(String(place.dayId), {
        id: String(place.dayId), number: place.dayNumber || rows.size + 1, date: place.dayDate || null,
      });
    });
    return [...rows.values()].sort((left, right) => left.number - right.number);
  }, [savedItineraryPlaces, workspace?.effectiveItineraryDays]);

  const mapData = useMemo(() => {
    const allPlaces = [...new globalThis.Map([...recommendedPlaces, ...savedItineraryPlaces, ...draftPlaces]
      .map((place) => [place.id, place])).values()]
      .map((place) => ({ ...place, inDraft: Boolean(place.inDraft) && !savedPlaceIds.has(place.id),
        isSaved: Boolean(place.isSaved) || savedPlaceIds.has(place.id) }))
      .filter((place) => categoryFilter === 'all' || place.category === categoryFilter);
    const places = selectedMapDayId === 'all' ? allPlaces
      : allPlaces.filter((place) => String(place.dayId ?? '') === String(selectedMapDayId));
    return {
      name: destination?.name || trip.destination,
      center: destination?.center || coordinates(lodging?.latitud, lodging?.longitud)
        || coordinates(lodgingSuggestions[0]?.latitud, lodgingSuggestions[0]?.longitud)
        || places.find((place) => place.coordinates)?.coordinates
        || allPlaces.find((place) => place.coordinates)?.coordinates || null,
      places,
    };
  }, [categoryFilter, destination, draftPlaces, lodging, lodgingSuggestions, recommendedPlaces,
    savedItineraryPlaces, savedPlaceIds, selectedMapDayId, trip.destination]);

  useEffect(() => {
    if (selectedMapDayId !== 'all' && !itineraryDayOptions.some((day) => day.id === String(selectedMapDayId))) {
      setSelectedMapDayId('all');
    }
  }, [itineraryDayOptions, selectedMapDayId, setSelectedMapDayId]);

  const visibleLodgings = useMemo(() => {
    const query = lodgingSearch.trim().toLocaleLowerCase('es');
    const rows = lodgingSuggestions.filter((place) => {
      const matchesName = !query || `${place.nombre} ${place.direccion || ''}`.toLocaleLowerCase('es').includes(query);
      const matchesType = lodgingMatchesPreference(place, lodgingTypeFilter);
      return matchesName && matchesType;
    });
    if (lodgingSort === 'distance' && destination?.center) {
      rows.sort((left, right) => distanceKm(destination.center, left) - distanceKm(destination.center, right));
    } else {
      rows.sort((left, right) => left.nombre.localeCompare(right.nombre, 'es'));
    }
    return rows;
  }, [destination, lodgingSearch, lodgingSort, lodgingSuggestions, lodgingTypeFilter]);

  useEffect(() => {
    let active = true;
    apiRequest(`/api/viajes/${trip.id}/alojamiento`, { token })
      .then((result) => { if (active) setLodging(result || null); })
      .catch(() => { if (active) setLodging(null); });
    return () => { active = false; };
  }, [token, trip.id]);

  useEffect(() => {
    let active = true;
    apiRequest(`/api/viajes/${trip.id}/itinerario`, { token })
      .then((result) => {
        if (active) {
          setSavedItineraryPlaces(normalizeSavedItineraryPlaces(result));
          setSavedItineraryDays(Array.isArray(result?.dias) ? result.dias : []);
        }
      })
      .catch(() => { /* Keep the last verified markers while the server is temporarily unavailable. */ });
    return () => { active = false; };
  }, [token, trip.id, itineraryReloadKey]);

  useEffect(() => {
    const handleItineraryUpdate = (event) => {
      if (String(event.detail?.tripId) === String(trip.id)) setItineraryReloadKey((current) => current + 1);
    };
    window.addEventListener('smarttrip:itinerary-updated', handleItineraryUpdate);
    return () => window.removeEventListener('smarttrip:itinerary-updated', handleItineraryUpdate);
  }, [trip.id]);

  useEffect(() => {
    const handlePackageTab = (event) => {
      if (String(event.detail?.tripId) === String(trip.id) && event.detail?.tabId === 'lodging') {
        window.requestAnimationFrame(() => mapRef.current?.invalidateSize({ pan: false }));
      }
    };
    window.addEventListener('smarttrip:package-tab', handlePackageTab);
    return () => window.removeEventListener('smarttrip:package-tab', handlePackageTab);
  }, [trip.id]);

  useEffect(() => {
    let active = true;
    setLodgingStatus('loading');
    setLodgingError('');
    apiRequest(`/api/viajes/${trip.id}/alojamientos/sugerencias`, { token })
      .then((result) => {
        if (!active) return;
        const rows = normalizeLodgingSuggestions(result);
        setLodgingSuggestions(rows);
        setLodgingStatus(rows.length ? 'ready' : 'empty');
      })
      .catch((requestError) => {
        if (active) { setLodgingError(requestError.message); setLodgingStatus('error'); }
      });
    return () => { active = false; };
  }, [token, trip.id, reloadKey]);

  useEffect(() => {
    if (workspace) return;
    const key = `smarttrip-lodging-draft-${trip.id}`;
    try {
      if (selectedLodging) sessionStorage.setItem(key, JSON.stringify(selectedLodging));
      else sessionStorage.removeItem(key);
    } catch { /* Selection remains in component state if browser storage is unavailable. */ }
  }, [selectedLodging, trip.id, workspace]);

  useEffect(() => { onPackageLodgingSelect?.(selectedLodging); }, [selectedLodging, onPackageLodgingSelect]);

  useEffect(() => {
    if (!selectedLodging) return;
    setSelectedPlace({ id: `lodging:${selectedLodging.id}`, name: selectedLodging.nombre,
      location: selectedLodging.direccion || 'Dirección no informada', category: selectedLodging.categoria || 'Alojamiento',
      coordinates: coordinates(selectedLodging.latitud, selectedLodging.longitud), source: 'Geoapify / OpenStreetMap',
      imageUrl: selectedLodging.imagenUrl || null, lodging: true, interests: [] });
  }, [selectedLodging]);

  useEffect(() => {
    let active = true;
    setRecommendationError('');
    apiRequest(`/api/viajes/${trip.id}/actividades/recomendadas`, { token })
      .then((response) => {
        if (!active) return;
        const normalized = normalizeRecommendations(response);
        setRecommendedPlaces(normalized.places.filter((place) => place.coordinates));
        setUnmappedPlaceCount(normalized.unmappedCount);
        setRecommendationWarnings(normalized.requiresInterests
          ? ['Selecciona intereses para recibir lugares recomendados.'] : normalized.warnings);
      })
      .catch((requestError) => { if (active) setRecommendationError(requestError.message); });
    return () => { active = false; };
  }, [token, trip.id, reloadKey]);

  useEffect(() => {
    const normalizedTripDestination = normalizeDestination(trip.destination);
    if (destinationLocation?.center && normalizeDestination(destinationLocation.name) === normalizedTripDestination) {
      setDestination(destinationLocation);
      setLocationStatus('ready');
      setLocationError('');
      return undefined;
    }
    if (!destination?.center || normalizeDestination(destination.name) !== normalizedTripDestination) setDestination(null);
    let active = true;
    setLocationStatus('loading');
    setLocationError('');
    apiRequest(`/api/viajes/${trip.id}/destino/ubicacion`, { token })
      .then((result) => {
        if (!active) return;
        const center = coordinates(result?.latitud, result?.longitud);
        if (!center) throw new Error('No se encontraron coordenadas verificadas para el destino.');
        const resolved = { name: result.nombre || trip.destination, center, resolvedAt: new Date().toISOString(),
          source: result.fuenteCoordenadas || 'proveedor público', reusedFromTrip: Boolean(result.coordenadasReutilizadas) };
        setDestination(resolved);
        setDestinationLocation(resolved);
        setLocationStatus('ready');
      })
      .catch((requestError) => {
        if (!active) return;
        const temporary = /HTTP (429|502|503|504)|timeout|timed out|temporalmente|pausa|no está disponible/i.test(requestError.message || '');
        const previousMatches = destination?.center
          && normalizeDestination(destination.name) === normalizedTripDestination;
        if (previousMatches) {
          setLocationStatus('stale');
          setLocationError('El proveedor de geocodificación no está disponible temporalmente; se conserva la ubicación verificada anterior.');
        } else if (temporary) {
          setLocationStatus('provider-unavailable');
          setLocationError('El proveedor de geocodificación está temporalmente no disponible. No se inventó una ubicación.');
        } else {
          setLocationStatus('error');
          setLocationError(requestError.message);
        }
      });

    return () => { active = false; };
  }, [token, trip.id, trip.destination, reloadKey, destinationLocation, setDestinationLocation]);

  useEffect(() => {
    if (!mapElementRef.current) return undefined;
    if (!mapData.center) {
      if (mapRef.current) {
        mapRef.current.remove();
        mapRef.current = null;
      }
      markersRef.current.clear();
      return undefined;
    }

    if (mapRef.current) {
      mapRef.current.remove();
      mapRef.current = null;
    }

    let map;
    let invalidateTimer;
    setMapError('');
    markersRef.current.clear();
    try {
      map = L.map(mapElementRef.current, {
        zoomControl: true,
        scrollWheelZoom: wheelZoomEnabledRef.current,
      }).setView(mapData.center, mapData.places.length ? 12 : 10);
      const activateWheelZoom = () => {
        wheelZoomEnabledRef.current = true;
        map.scrollWheelZoom.enable();
        setWheelZoomEnabled(true);
        map.off('click', activateWheelZoom);
      };
      map.on('click', activateWheelZoom);

      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; OpenStreetMap contributors',
        maxZoom: 19,
      }).addTo(map);

      if (destination?.center) {
        const destinationMarker = L.circleMarker(destination.center, {
          radius: 10,
          color: '#ffffff',
          weight: 3,
          fillColor: '#2563eb',
          fillOpacity: 1,
        }).addTo(map);
        destinationMarker.bindTooltip(`Destino: ${trip.destination}`, { direction: 'top' });
      }

      const lodgingCoordinates = coordinates(lodging?.latitud, lodging?.longitud);
      if (lodging && lodgingCoordinates) {
        const lodgingMarker = L.circleMarker(lodgingCoordinates, {
          radius: 9, color: '#ffffff', weight: 3, fillColor: '#d97706', fillOpacity: 1,
        }).addTo(map);
        lodgingMarker.bindTooltip(`Alojamiento: ${lodging.nombre}`, { direction: 'top' });
        lodgingMarker.on('click', () => setSelectedPlace({
          id: `lodging-${lodging.id}`, name: lodging.nombre, location: lodging.direccion || 'Dirección no informada',
          description: null, source: 'Alojamiento guardado en SmartTrip', interests: [], coordinates: lodgingCoordinates,
        }));
      }

      visibleLodgings.forEach((place) => {
        const point = coordinates(place.latitud, place.longitud);
        if (!point) return;
        const marker = L.marker(point, { icon: L.divIcon({
          className: 'lodging-marker-wrapper',
          html: '<span class="lodging-map-marker"><span>⌂</span></span>',
          iconSize: [42, 44], iconAnchor: [21, 44],
        }) }).addTo(map);
        marker.bindTooltip(place.nombre, { direction: 'top', offset: [0, -36] });
        marker.on('click', () => chooseLodging(place));
        markersRef.current.set(`lodging:${place.id}`, marker);
      });

      mapData.places.forEach((place, index) => {
        if (!Array.isArray(place.coordinates) || place.coordinates.length !== 2) return;
        const markerNumber = place.order || index + 1;
        const selected = String(selectedMapActivityId || '') === String(place.id);
        const color = dayMarkerColor(place.dayNumber);
        const marker = L.marker(place.coordinates, {
          icon: L.divIcon({
            className: 'recommendation-marker-wrapper',
            html: `<span class="recommendation-marker${place.isSaved ? ' recommendation-marker--saved' : ''}${place.inDraft ? ' recommendation-marker--draft' : ''}${selected ? ' recommendation-marker--selected' : ''}" style="--day-marker-color:${color}"><b>${markerNumber}</b></span>`,
            iconSize: [36, 42],
            iconAnchor: [18, 42],
          }),
        }).addTo(map);

        marker.bindTooltip(`${place.dayNumber ? `Día ${place.dayNumber} · ` : ''}${markerNumber}. ${place.name}`, { direction: 'top', offset: [0, -35] });
        marker.on('click', () => {
          setSelectedPlace(place);
          setSelectedMapActivityId(place.id);
          if (place.dayId != null) setSelectedMapDayId(String(place.dayId));
        });
        markersRef.current.set(place.id, marker);
      });

      const lodgingPoints = visibleLodgings.map((place) => coordinates(place.latitud, place.longitud)).filter(Boolean);
      const mappedPlaceCoordinates = mapData.places.map((place) => place.coordinates).filter((point) => Array.isArray(point) && point.length === 2);
      if (mappedPlaceCoordinates.length || lodgingPoints.length) {
        map.fitBounds([mapData.center, ...mappedPlaceCoordinates, ...lodgingPoints,
          ...(lodging && lodgingCoordinates ? [lodgingCoordinates] : [])], {
          padding: [45, 45],
        });
      }

      mapRef.current = map;
      invalidateTimer = window.setTimeout(() => map.invalidateSize(), 0);
    } catch (error) {
      if (map) map.remove();
      mapRef.current = null;
      markersRef.current.clear();
      console.error('Leaflet map initialization failed.', error);
      setMapError('No se pudo dibujar el mapa. La lista de lugares sigue disponible; puedes volver a intentarlo.');
      return undefined;
    }

    return () => {
      window.clearTimeout(invalidateTimer);
      map.remove();
      mapRef.current = null;
      markersRef.current.clear();
    };
  }, [lodging, visibleLodgings, mapData, trip.destination, mapAttempt]);

  useEffect(() => {
    markersRef.current.forEach((marker, id) => {
      if (id.startsWith('lodging:')) {
        const lodgingId = id.slice('lodging:'.length);
        const isSelected = String(selectedLodging?.id ?? '') === lodgingId
          || selectedPlace?.id === `lodging-${lodgingId}` || selectedPlace?.id === `lodging:${lodgingId}`;
        marker.getElement()?.querySelector('.lodging-map-marker')?.classList.toggle('lodging-map-marker--selected', isSelected);
        return;
      }
      const element = marker.getElement()?.querySelector('.recommendation-marker');
      element?.classList.toggle('recommendation-marker--selected', String(id) === String(selectedMapActivityId || ''));
    });
  }, [selectedMapActivityId, selectedLodging, selectedPlace, mapData]);

  useEffect(() => {
    const container = mapElementRef.current?.parentElement;
    if (!container || typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(() => mapRef.current?.invalidateSize({ pan: false }));
    observer.observe(container);
    return () => observer.disconnect();
  }, [mapData.center, mapExpanded, mapAttempt]);

  useEffect(() => {
    if (!selectedMapActivityId) return;
    const place = mapData.places.find((candidate) => String(candidate.id) === String(selectedMapActivityId));
    if (!place) return;
    setSelectedPlace(place);
    if (place.coordinates) mapRef.current?.flyTo(place.coordinates, 15, { duration: 0.6 });
    markersRef.current.get(place.id)?.openTooltip();
  }, [mapData, selectedMapActivityId]);

  const selectPlace = (place) => {
    setSelectedPlace(place);
    setSelectedMapActivityId(place.id);
    if (place.dayId != null) setSelectedMapDayId(String(place.dayId));
    if (place.coordinates) mapRef.current?.flyTo(place.coordinates, 15, { duration: 0.6 });
    markersRef.current.get(place.id)?.openTooltip();
  };

  const selectItineraryActivity = (activity, day) => {
    const markerId = mapMarkerIdForTripActivity(activity?.id);
    setSelectedMapDayId(String(day?.id ?? 'all'));
    setSelectedMapActivityId(markerId);
    const place = mapData.places.find((candidate) => String(candidate.id) === markerId);
    if (place) selectPlace(place);
    else setSelectedPlace({ id: markerId, name: activity?.nombre || 'Actividad',
      location: activity?.ubicacion || 'Ubicación no informada', coordinates: null,
      time: activity?.horaLocal || activity?.hora || null, order: activity?.orden || null,
      dayNumber: itineraryDays.findIndex((candidate) => String(candidate.id) === String(day?.id)) + 1,
      category: activity?.categoria || 'Actividad', source: activity?.fuente || 'Itinerario', interests: [] });
  };

  const fitVisiblePlaces = () => {
    if (!mapRef.current) return;
    const points = mapData.places.filter((place) => Array.isArray(place.coordinates)).map((place) => place.coordinates);
    if (points.length > 1) mapRef.current.fitBounds(points, { padding: [28, 28], maxZoom: 15 });
    else if (points.length === 1) mapRef.current.setView(points[0], 15);
    else if (mapData.center) mapRef.current.setView(mapData.center, 11);
  };

  const toggleWheelZoom = () => {
    const next = !wheelZoomEnabledRef.current;
    wheelZoomEnabledRef.current = next;
    if (next) mapRef.current?.scrollWheelZoom.enable();
    else mapRef.current?.scrollWheelZoom.disable();
    setWheelZoomEnabled(next);
  };

  const chooseLodging = (place) => {
    const point = coordinates(place.latitud, place.longitud);
    const selected = { ...place, coordinates: point };
    setSelectedLodging(selected);
    setSelectedPlace({ id: `lodging:${place.id}`, name: place.nombre, location: place.direccion || 'Dirección no informada',
      category: place.categoria || 'Alojamiento', coordinates: point, source: 'Geoapify / OpenStreetMap',
      imageUrl: place.imagenUrl || null, lodging: true, interests: [] });
    mapRef.current?.flyTo(point, 16, { duration: 0.6 });
    markersRef.current.get(`lodging:${place.id}`)?.openTooltip();
  };

  const viewLodging = (place) => {
    setLodgingView('map');
    const point = coordinates(place.latitud, place.longitud);
    if (!point) return;
    setSelectedPlace({ id: `lodging:${place.id}`, name: place.nombre,
      location: place.direccion || 'Dirección no informada', category: place.categoria || 'Alojamiento',
      coordinates: point, source: 'Geoapify / OpenStreetMap', imageUrl: null, lodging: false, interests: [] });
    mapRef.current?.flyTo(point, 16, { duration: 0.6 });
    markersRef.current.get(`lodging:${place.id}`)?.openTooltip();
  };

  const saveSelectedLodging = async () => {
    if (!selectedLodging || savingLodging) return;
    setSavingLodging(true); setLodgingError(''); setLodgingNotice('');
    try {
      const saved = await apiRequest(`/api/viajes/${trip.id}/alojamiento`, {
        method: 'PUT', token,
        body: JSON.stringify({ nombre: selectedLodging.nombre, direccion: selectedLodging.direccion || null,
          latitud: Number(selectedLodging.latitud), longitud: Number(selectedLodging.longitud) }),
      });
      setLodging(saved);
      setSelectedLodging(null);
      window.dispatchEvent(new CustomEvent('smarttrip:lodging-updated', { detail: { tripId: String(trip.id) } }));
      setSelectedPlace((current) => current ? { ...current, lodging: false, source: 'Alojamiento guardado en SmartTrip' } : current);
      setLodgingNotice('Alojamiento guardado en Oracle para este itinerario. Precio y disponibilidad no verificados.');
    } catch (requestError) { setLodgingError(requestError.message); }
    finally { setSavingLodging(false); }
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

      <div className={`map-layout${lodgingView === 'cards' ? ' lodging-cards-mode' : ''}${mapExpanded ? ' is-map-expanded' : ''}`}>
        <aside className="trip-map-panel trip-map-itinerary-panel" aria-label="Itinerario y actividades">
          <header className="trip-map-panel__header">
            <div><h2>Itinerario y actividades</h2><small>{itineraryActivityCount} actividad(es) · {itineraryDays.length} día(s)</small></div>
            <button type="button" onClick={() => setSelectedMapDayId('all')} aria-pressed={selectedMapDayId === 'all'}>Todos</button>
          </header>
          <div className="trip-map-panel__scroll trip-map-itinerary-list" aria-label="Actividades organizadas por día" tabIndex="0">
            {itineraryDays.length ? itineraryDays.map((day, dayIndex) => {
              const dayId = String(day.id ?? day.fecha ?? dayIndex);
              return <section className="map-itinerary-day" key={dayId}>
                <button type="button" className="map-itinerary-day__heading" aria-pressed={selectedMapDayId === dayId}
                  onClick={() => setSelectedMapDayId(dayId)}>
                  <span>Día {dayIndex + 1}{day.fecha ? ` · ${day.fecha}` : ''}</span><small>{(day.actividades || []).length}</small>
                </button>
                {(day.actividades || []).length ? <ol className="map-itinerary-activities">{day.actividades.map((activity, activityIndex) => {
                  const markerId = mapMarkerIdForTripActivity(activity?.id);
                  const selected = String(selectedMapActivityId || '') === markerId;
                  return <li key={`${dayId}:${activity?.id ?? activityIndex}`}>
                    <button type="button" className={`map-itinerary-activity${selected ? ' is-selected' : ''}`}
                      aria-pressed={selected} onClick={() => selectItineraryActivity(activity, { ...day, id: dayId })}>
                      <span className="place-number">{activity?.orden || activityIndex + 1}</span>
                      <span><strong>{activity?.nombre || 'Actividad sin nombre'}</strong>
                        {(activity?.horaLocal || activity?.hora) && <small>{activity.horaLocal || activity.hora}</small>}
                        {activity?.ubicacion && <small>{activity.ubicacion}</small>}
                        {!coordinates(activity?.latitud, activity?.longitud) && <small className="map-itinerary-no-coordinates">Sin coordenadas verificadas</small>}
                      </span>
                    </button>
                  </li>;
                })}</ol> : <p className="map-itinerary-day__empty">Sin actividades asignadas.</p>}
              </section>;
            }) : <p className="map-itinerary-day__empty">Todavía no hay días ni actividades en el borrador efectivo.</p>}
            <details className="map-explore-places">
              <summary>Explorar lugares recomendados · {mapData.places.length}</summary>
              <div className="map-explore-places__content">
          <div className="destination-card">
            <span className="destination-symbol"><LocateFixed size={20} /></span>
            <div>
              <small>Destino seleccionado</small>
              <strong>{trip.destination}</strong>
              <span>{locationStatus === 'loading' ? 'Buscando ubicación…' : mapData.name}</span>
              {destination?.reusedFromTrip && <small>Centro del mapa basado en coordenadas ya guardadas en este viaje; no es una nueva geocodificación.</small>}
            </div>
          </div>

          <h2>Lugares recomendados</h2>
          <label className="map-category-filter">Filtrar lugares
            <select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}>
              <option value="all">Todas las categorías</option>
              {[...new Set(recommendedPlaces.map((place) => place.category).filter(Boolean))].sort().map((category) =>
                <option key={category} value={category}>{category}</option>)}
            </select>
          </label>
          {(recommendationError || locationError || recommendationWarnings.some((warning) => /OpenStreetMap|servicios públicos|temporalmente/i.test(warning))) && <button type="button" className="map-retry-button"
            onClick={() => setReloadKey((current) => current + 1)}>Reintentar carga de mapa y lugares</button>}
          {recommendationError && <p role="alert" className="form-error">{recommendationError}</p>}
          {locationError && <p role="alert" className="form-error">{locationError}</p>}
          {recommendationWarnings.map((warning, index) => <p className="places-empty" key={`${warning}-${index}`}>{warning}</p>)}
          {mapData.places.length > 0 ? (
            <ol className="places-list">
              {mapData.places.map((place, index) => (
                <li key={place.id}>
                  <button
                    type="button"
                    className={`${selectedPlace?.id === place.id ? 'active' : ''}${place.inDraft ? ' in-planning-draft' : ''}`}
                    onClick={() => selectPlace(place)}
                  >
                    <span className="place-number">{place.order || index + 1}</span>
                    <span>
                      <strong>{place.name}</strong>
                      {place.dayNumber && <small>Día {place.dayNumber}{place.order ? ` · visita ${place.order}` : ''}</small>}
                      <small>{place.location}</small>
                      {place.inDraft && <small>En borrador del itinerario</small>}
                      {place.isSaved && <small>Guardada en el itinerario</small>}
                     {place.category && <small>{place.category}</small>}
                     {place.coordinates && <small>{place.coordinates[0].toFixed(5)}, {place.coordinates[1].toFixed(5)}</small>}
                    </span>
                  </button>
                </li>
              ))}
            </ol>
          ) : (
            <p className="places-empty">
              {locationStatus === 'provider-unavailable'
                ? 'El servicio de ubicación está temporalmente no disponible. Conservamos los lugares manuales o guardados; puedes reintentar.'
                : locationStatus === 'error'
                ? 'No pudimos localizar este destino. Verifica el nombre e inténtalo nuevamente.'
                : locationStatus === 'loading'
                  ? 'Estamos ubicando tu destino en el mapa.'
                  : 'Todavía no hay lugares recomendados para este destino.'}
            </p>
          )}
          {unmappedPlaceCount > 0 && <p className="places-empty" role="status">
            {unmappedPlaceCount} recomendación(es) no aparecen como marcador porque no tienen coordenadas verificadas.
          </p>}
              </div>
            </details>
          </div>
        </aside>

        <aside className="trip-map-panel trip-map-lodging-panel" aria-label="Alojamientos y hoteles">
          <header className="trip-map-panel__header"><div><h2>Alojamientos</h2><small>{visibleLodgings.length} opción(es) en la lista</small></div></header>
          <div className="trip-map-panel__scroll trip-map-lodging-list" aria-label="Lista desplazable de alojamientos" tabIndex="0">
          <section className="map-lodging-results" aria-label="Alojamientos disponibles en el proveedor">
            <section className="hotel-live-prices" aria-label="Tarifas hoteleras en vivo">
              <div><strong>Consultar tarifas para estas fechas</strong>
                <p>Consulta en vivo para {trip.startDate} – {trip.endDate}. La tarifa y disponibilidad se confirman en el proveedor; SmartTrip no reserva.</p>
              </div>
              <button type="button" className="map-retry-button" onClick={searchHotelPrices} disabled={hotelPriceStatus === 'loading'}>
                <Search size={16} /> {hotelPriceStatus === 'loading' ? 'Consultando tarifas…' : hotelPriceStatus === 'ready' ? 'Actualizar tarifas' : 'Buscar precios y disponibilidad'}
              </button>
              <small>Precios en vivo de OBO Travel · consulta de solo lectura · disponibles como máximo durante 30 minutos.</small>
              {hotelPriceError && <p role="alert" className="form-error">{hotelPriceError}</p>}
              {hotelPriceStatus === 'ready' && !liveHotelOffers.length && <p role="status" className="places-empty">
                {normalizedHotelOffers.length ? 'La tarifa superó el límite de vigencia; vuelve a consultar antes de usarla.'
                  : hotelPrices?.results ? 'El proveedor no devolvió tarifas con importe y moneda reconocibles para esta búsqueda.'
                    : 'No se encontraron tarifas para estas fechas. Puedes reintentar más tarde.'}
              </p>}
              {hotelPriceStatus === 'expired' && <p role="status" className="places-empty">La consulta venció. Actualiza para obtener precios y disponibilidad recientes.</p>}
              {liveHotelOffers.length > 0 && <ul className="hotel-live-prices__list">
                {liveHotelOffers.map((offer) => <li key={offer.id} className="hotel-live-prices__offer">
                  {offer.photoUrl && <img src={offer.photoUrl} alt={`Imagen del alojamiento ${offer.name}`} loading="lazy" referrerPolicy="no-referrer" />}
                  <div><strong>{offer.name}</strong>{offer.address && <small>{offer.address}</small>}
                    <p><b>{formatHotelPrice(offer.total, offer.currency)}</b> total de estancia
                      {offer.nightly != null && <> · {formatHotelPrice(offer.nightly, offer.currency)} por noche</>}</p>
                    <small>{offer.taxesIncluded == null ? 'Impuestos: confirmar condiciones' : `Impuestos ${offer.taxesIncluded ? 'incluidos' : 'no incluidos'}`}
                      {offer.feesIncluded == null ? ' · cargos: revisar proveedor' : ` · cargos ${offer.feesIncluded ? 'incluidos' : 'no incluidos'}`}</small>
                    {offer.extraCharges.length > 0 && <small>Cargos adicionales reportados por el proveedor</small>}
                    <small>Tarifa consultada {offer.quotedAt ? new Date(offer.quotedAt).toLocaleString('es-CO') : 'ahora'} · disponibilidad sujeta a reconfirmación</small>
                    {offer.bookingUrl && <a href={offer.bookingUrl} target="_blank" rel="noreferrer"><ExternalLink size={14} /> Ver tarifa y continuar en OBO Travel</a>}
                  </div>
                </li>)}
              </ul>}
              {hotelPriceStatus === 'ready' && hotelPrices?.retrievedAt && <small role="status">
                {isHotelQuoteFresh(hotelPrices.retrievedAt) ? 'Consulta vigente en sesión.' : 'La consulta superó 30 minutos.'}
              </small>}
            </section>
            {lodging && <div className="lodging-saved-card" role="status">
              <span><strong>Guardado en este viaje</strong><small>{lodging.nombre} · {lodging.direccion || 'Dirección no informada'}</small></span>
              <small>Seleccionar un lugar no crea una reserva.</small>
            </div>}
            {lodgingError && <p role="alert" className="form-error">{lodgingError}</p>}
            {lodgingNotice && <p role="status" className="places-empty">{lodgingNotice}</p>}
            <div className="lodging-toolbar">
              <label className="lodging-toolbar__search">Buscar por nombre o zona
                <input type="search" value={lodgingSearch} onChange={(event) => setLodgingSearch(event.target.value)}
                  placeholder="Hotel, barrio o dirección" />
              </label>
              <label>Tipo de alojamiento
                <select value={lodgingTypeFilter} onChange={(event) => setLodgingTypeFilter(event.target.value)}>
                  <option value="all">Todos los tipos</option>
                  <option value="hotel">Hoteles</option><option value="hostel">Hostales</option>
                  <option value="apartment">Apartamentos</option><option value="guest">Casas de huéspedes</option>
                  <option value="motel">Moteles</option>
                </select>
              </label>
              <label>Ordenar
                <select value={lodgingSort} onChange={(event) => setLodgingSort(event.target.value)}>
                  <option value="name">Nombre</option>
                  <option value="distance" disabled={!destination?.center}>Distancia al destino</option>
                </select>
              </label>
              <div className="lodging-view-toggle" role="group" aria-label="Vista de alojamientos">
                <button type="button" aria-pressed={lodgingView === 'map'} onClick={() => setLodgingView('map')}><MapIcon size={16} /> Mapa</button>
                <button type="button" aria-pressed={lodgingView === 'cards'} onClick={() => setLodgingView('cards')}><LayoutGrid size={16} /> Tarjetas</button>
              </div>
            </div>
            {lodgingStatus === 'loading' && <div className="lodging-skeleton-list" aria-label="Cargando alojamientos">
              {[0, 1, 2].map((item) => <div className="lodging-skeleton" key={item}><span /><i /><i /></div>)}
            </div>}
            {lodgingStatus === 'empty' && <p className="places-empty">No se encontraron alojamientos con ubicación válida. Puedes registrar uno manualmente desde el panel del viaje.</p>}
            {lodgingStatus === 'error' && <button type="button" className="map-retry-button" onClick={() => setReloadKey((current) => current + 1)}>Reintentar búsqueda</button>}
            {lodgingStatus === 'ready' && !visibleLodgings.length && <p className="places-empty">No hay alojamientos que coincidan con estos filtros.</p>}
            {visibleLodgings.length > 0 && <ul className={`lodging-map-list ${lodgingView === 'cards' ? 'is-grid' : ''}`}>
              {visibleLodgings.map((place) => <li key={place.id}>
                <LodgingSuggestionCard suggestion={place} selected={selectedLodging?.id === place.id}
                  distanceKm={destination?.center ? distanceKm(destination.center, place) : null}
                  onView={viewLodging} onSelect={chooseLodging} />
              </li>)}
            </ul>}
            {selectedLodging && <div className="lodging-selection-bar" role="status">
              <span><strong>Listo para guardar:</strong> {selectedLodging.nombre}<small>La selección no representa una reserva.</small></span>
              <Button variant="primary" type="button" disabled={savingLodging} onClick={saveSelectedLodging}>
                {savingLodging ? 'Guardando…' : 'Guardar alojamiento en el viaje'}
              </Button>
            </div>}
            <small className="places-attribution">Powered by <a href="https://www.geoapify.com/" target="_blank" rel="noreferrer">Geoapify</a> · © OpenStreetMap contributors</small>
          </section>
          </div>
        </aside>

        <div className={`map-content${mapExpanded ? ' map-content--expanded' : ''}`}>
          <section className="map-card" aria-label="Mapa del itinerario turístico">
            <div className="map-card__toolbar">
              <div className="map-card__heading"><strong>Mapa turístico</strong><small>{mapData.places.filter((place) => place.coordinates).length} lugares con coordenadas verificadas</small></div>
              <div className="map-card__controls">
                <label>Día
                  <select aria-label="Filtrar marcadores por día" value={selectedMapDayId}
                    onChange={(event) => setSelectedMapDayId(event.target.value)}>
                    <option value="all">Todos los días</option>
                    {itineraryDayOptions.map((day) => <option key={day.id} value={day.id}>Día {day.number}{day.date ? ` · ${day.date}` : ''}</option>)}
                  </select>
                </label>
                <button type="button" onClick={fitVisiblePlaces}><Crosshair size={16} /> Ajustar vista</button>
                <button type="button" aria-pressed={wheelZoomEnabled}
                  aria-label={wheelZoomEnabled ? 'Desactivar zoom con rueda' : 'Activar zoom con rueda'}
                  title={wheelZoomEnabled ? 'Desactivar zoom con rueda' : 'Activar zoom con rueda'} onClick={toggleWheelZoom}>
                  {wheelZoomEnabled ? 'Rueda activa' : 'Zoom rueda'}
                </button>
                <button type="button" aria-expanded={mapExpanded} onClick={() => setMapExpanded((value) => !value)}>
                  {mapExpanded ? <><Minimize2 size={16} /> Vista compacta</> : <><Maximize2 size={16} /> Expandir mapa</>}
                </button>
              </div>
            </div>
            <div className="map-legend" aria-label="Leyenda del mapa">
              <span><i className="map-legend__destination" /> Destino</span>
              <span><i className="map-legend__lodging" /> Alojamiento</span>
              {selectedMapDayId === 'all' ? itineraryDayOptions.map((day) => <span key={day.id}><i style={{ '--day-marker-color': dayMarkerColor(day.number) }} /> Día {day.number}</span>)
                : <span><i style={{ '--day-marker-color': dayMarkerColor(itineraryDayOptions.find((day) => day.id === String(selectedMapDayId))?.number) }} /> Número = orden de visita</span>}
              <small>Número = orden dentro del día · borde punteado = cambio en borrador · contorno amarillo = lugar seleccionado.</small>
              <small>Sin ruta dibujada: el servicio disponible calcula duración y distancia, pero no proporciona geometría de trayecto.</small>
            </div>
            <div className="map-canvas">
              {mapData.center
                ? <div ref={mapElementRef} className="places-map" role="application" aria-label={`Mapa interactivo de ${trip.destination}`} />
                : <div ref={mapElementRef} className="places-map places-map-empty" role="status">{locationStatus === 'loading' ? 'Ubicando el destino…' : 'No hay coordenadas verificadas disponibles para mostrar el mapa.'}</div>}
              {selectedMapDayId !== 'all' && !mapData.places.some((place) => place.coordinates) && <div className="map-filter-empty" role="status">Este día aún no tiene actividades con coordenadas verificadas.</div>}
              {unmappedItineraryCount > 0 && <div className="map-coordinate-notice" role="status">{unmappedItineraryCount} actividad(es) del itinerario no tienen coordenadas verificadas y no aparecen como marcador.</div>}
              {mapError && <div className="map-render-error" role="alert"><p>{mapError}</p>
                <button type="button" onClick={() => setMapAttempt((current) => current + 1)}>Reintentar mapa</button>
              </div>}

              <div className={`place-detail ${selectedPlace ? 'visible' : ''}`} aria-live="polite">
                {selectedPlace ? (
                  <>
                    <span className="place-detail-icon"><MapPin size={22} /></span>
                    <div>
                      <small>Lugar seleccionado</small>
                      <h2>{selectedPlace.name}</h2>
                      <p><Navigation size={16} /> {selectedPlace.location}</p>
                      {selectedPlace.dayNumber && <small>Día {selectedPlace.dayNumber}{selectedPlace.dayDate ? ` · ${selectedPlace.dayDate}` : ''} · Visita {selectedPlace.order || '—'}</small>}
                      {selectedPlace.time && <small>Horario del borrador: {selectedPlace.time}</small>}
                      {selectedPlace.description && <p>{selectedPlace.description}</p>}
                      {selectedPlace.category && <small>Categoría: {selectedPlace.category}</small>}
                      {selectedPlace.coordinates && <small>Coordenadas: {selectedPlace.coordinates[0].toFixed(5)}, {selectedPlace.coordinates[1].toFixed(5)}</small>}
                      {selectedPlace.imageUrl && <img className="place-detail-image" src={selectedPlace.imageUrl} alt={`Imagen de ${selectedPlace.name}`} loading="lazy" referrerPolicy="no-referrer" />}
                      <small>Fuente: {selectedPlace.source}</small>
                      {Array.isArray(selectedPlace.interests) && selectedPlace.interests.length > 0 && <small>Coincide con: {selectedPlace.interests.join(', ')}</small>}
                      {selectedPlace.lodging && <small>Consultar precio y disponibilidad externamente. Seleccionar no es reservar.</small>}
                    </div>
                  </>
                ) : (
                   <p><MapPin size={18} /> Selecciona un marcador para consultar el lugar; haz clic en el mapa para activar el zoom con rueda.</p>
                )}
              </div>
            </div>
          </section>
        </div>
      </div>
    </section>
  );
}

function lodgingMatchesPreference(place, preference) {
  if (!preference || preference === 'all') return true;
  const type = `${place.tipo || ''} ${place.categoria || ''}`.toLocaleLowerCase('es');
  const terms = {
    hotel: ['hotel'],
    hostel: ['hostel', 'hostal', 'albergue'],
    apartment: ['apartment', 'apartamento', 'flat'],
    guest_house: ['guest house', 'guesthouse', 'casa de huéspedes', 'bed and breakfast', 'b&b'],
  }[preference] || [preference];
  return terms.some((term) => type.includes(term));
}

function normalizeDestination(value) {
  return String(value || '').trim().toLocaleLowerCase('es').replace(/\s+/g, ' ');
}

function mapMarkerIdForTripActivity(id) {
  const value = String(id ?? '');
  if (value.startsWith('external:')) return value.slice('external:'.length);
  if (value.startsWith('catalog:')) return `catalog-${value.slice('catalog:'.length)}`;
  if (value.startsWith('manual:')) return `manual-${value.slice('manual:'.length)}`;
  return `catalog-${value}`;
}

function dayMarkerColor(dayNumber) {
  const palette = ['#176b87', '#7a4eab', '#26734d', '#b45309', '#bd3d61', '#5263bd', '#087e8b'];
  const day = Number(dayNumber);
  return Number.isFinite(day) && day > 0 ? palette[(Math.floor(day) - 1) % palette.length] : '#5367d8';
}
