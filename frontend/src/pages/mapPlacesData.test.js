import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { coordinates, distanceKm, normalizeDraftItineraryPlaces, normalizeLodgingSuggestions, normalizeRecommendations, normalizeSavedItineraryPlaces } from './mapPlacesData.js';

test('map markers are derived from the effective draft, including unsaved external and manual activities', () => {
  const places = normalizeDraftItineraryPlaces([{ id: 161, fecha: '2026-11-20', actividades: [
    { id: 'external:geoapify:long-id', nombre: 'Museum', latitud: 41.3, longitud: 2.1, orden: 2, horaLocal: '10:30' },
    { id: 'manual:42', nombre: 'Local note', latitud: 41.4, longitud: 2.2 },
    { id: 'catalog:8', nombre: 'Catalog place', latitud: 91, longitud: 2 },
  ] }]);
  assert.deepEqual(places.map((place) => place.id), ['geoapify:long-id', 'manual-42', 'catalog-8']);
  assert.equal(places[0].inDraft, true);
  assert.deepEqual(places[0].coordinates, [41.3, 2.1]);
  assert.equal(places[0].dayId, '161');
  assert.equal(places[0].dayNumber, 1);
  assert.equal(places[0].dayDate, '2026-11-20');
  assert.equal(places[0].order, 2);
  assert.equal(places[0].time, '10:30');
  assert.equal(places[2].coordinates, null);
});

test('coordinate normalization rejects absent, nonnumeric and out-of-range values', () => {
  assert.deepEqual(coordinates('4.7', '-74.1'), [4.7, -74.1]);
  assert.equal(coordinates(null, -74), null);
  assert.equal(coordinates('not-a-coordinate', -74), null);
  assert.equal(coordinates(91, -74), null);
  assert.equal(coordinates(4, 181), null);
});

test('lodging normalizer handles empty and partially invalid provider payloads', () => {
  assert.deepEqual(normalizeLodgingSuggestions(null), []);
  assert.deepEqual(normalizeLodgingSuggestions([]), []);
  assert.deepEqual(normalizeLodgingSuggestions([null, {}, { nombre: 'Sin coordenadas' },
    { nombre: '   ', latitud: 4, longitud: -74 },
    { nombre: ' Hotel Uno ', latitud: '4.7', longitud: '-74.1' }]), [
    { nombre: 'Hotel Uno', latitud: '4.7', longitud: '-74.1', coordinates: [4.7, -74.1] },
  ]);
});

test('recommendation normalizer skips malformed rows and preserves valid results without coordinates', () => {
  const result = normalizeRecommendations({
    actividades: [null, {}, { actividad: { id: 1, nombre: 'Museo', latitud: 4, longitud: -74 }, interesesCoincidentes: ['Cultura', null] },
      { actividad: { id: 2, nombre: 'Actividad sin punto' } }],
    recomendacionesExternas: [null, { id: 'osm:3', nombre: 'Plaza', latitud: 4.1, longitud: -74.2 }],
    avisosFuentes: 'invalido',
  });
  assert.equal(result.places.length, 3);
  assert.deepEqual(result.places[0].coordinates, [4, -74]);
  assert.deepEqual(result.places[0].interests, ['Cultura']);
  assert.equal(result.places[1].coordinates, null);
  assert.equal(result.unmappedCount, 1);
  assert.deepEqual(result.warnings, []);
});

test('map recommendations omit low-evidence generic attractions while preserving documented tourism records', () => {
  const normalized = normalizeRecommendations({ recomendacionesExternas: [
    { id: 'statue', nombre: 'Incidental statue', latitud: 51.5, longitud: -0.1, senalTuristica: 36 },
    { id: 'museum', nombre: 'Documented museum', latitud: 51.51, longitud: -0.12, senalTuristica: 88 },
  ] });
  assert.deepEqual(normalized.places.map((place) => place.name), ['Documented museum']);
});

test('map recommendations reject city articles, generic mangrove labels and access-sensitive territories', () => {
  const normalized = normalizeRecommendations({ recomendacionesExternas: [
    { id: 'city', nombre: 'Cartagena, Colombia', categoria: 'administrative boundary city', latitud: 10.4, longitud: -75.5, senalTuristica: 90 },
    { id: 'mangle', nombre: 'Mangle', categoria: 'natural feature', latitud: 10.4, longitud: -75.5, senalTuristica: 70 },
    { id: 'territory', nombre: 'Resguardo indígena Senú', categoria: 'indigenous territory', latitud: 10.4, longitud: -75.5, senalTuristica: 75 },
    { id: 'reserve', nombre: 'Mangrove Visitor Reserve', categoria: 'nature reserve', latitud: 10.41, longitud: -75.49, senalTuristica: 72 },
  ] });
  assert.deepEqual(normalized.places.map((place) => place.id), ['reserve']);
});

test('malformed recommendation envelope renders as an empty, non-error state', () => {
  assert.deepEqual(normalizeRecommendations(null), {
    places: [], unmappedCount: 0, warnings: [], requiresInterests: false,
  });
  assert.deepEqual(normalizeRecommendations({ actividades: {}, recomendacionesExternas: {} }).places, []);
});

test('saved itinerary activities become verified map markers and malformed or unlocated entries are ignored', () => {
  assert.deepEqual(normalizeSavedItineraryPlaces(null), []);
  assert.deepEqual(normalizeSavedItineraryPlaces({ dias: [{ id: 186, fecha: '2026-11-20', actividades: [null, {},
    { id: 4, nombre: ' Museo ', latitud: '48.86', longitud: '2.29', categoria: 'Cultura', orden: 3, horaLocal: '14:00' },
    { id: 5, nombre: 'Sin ubicación' }] }] }), [{
    id: 'catalog-4', name: 'Museo', location: 'Ubicación no informada', description: null,
    coordinates: [48.86, 2.29], source: 'Actividad guardada en el itinerario', interests: [],
    category: 'Cultura', dayId: '186', dayNumber: 1, dayDate: '2026-11-20', order: 3,
    time: '14:00', isSaved: true,
  }]);
});

test('map exposes compact, expandable, day-filtered controls and shares selected activity with the planner', () => {
  const component = readFileSync(new URL('./MapPlaces.jsx', import.meta.url), 'utf8');
  const styles = readFileSync(new URL('../styles/map.css', import.meta.url), 'utf8');
  const planner = readFileSync(new URL('../components/GeneradorItinerario.jsx', import.meta.url), 'utf8');
  assert.match(component, /Filtrar marcadores por día/);
  assert.match(component, /Expandir mapa/);
  assert.match(component, /Vista compacta/);
  assert.match(component, /Ajustar vista/);
  assert.match(component, /setSelectedMapActivityId\(place\.id\)/);
  assert.match(component, /scrollWheelZoom:\s*wheelZoomEnabledRef\.current/);
  assert.match(component, /Día \$\{place\.dayNumber\}.*Visita|markerNumber/);
  assert.match(component, /Sin ruta dibujada/);
  assert.match(planner, /selectedMapActivityId === mapId \? ' map-selected-activity'/);
  assert.match(planner, /setSelectedMapDayId\?\.\(String\(day\?\.id/);
  assert.match(styles, /grid-template-columns:\s*minmax\(0,\s*26fr\)\s+minmax\(0,\s*48fr\)\s+minmax\(0,\s*26fr\)/);
  assert.match(styles, /grid-template-areas:\s*"itinerary map lodging"/);
  assert.match(styles, /\.trip-map-panel\s*\{[^}]*height:\s*530px/s);
  assert.match(styles, /\.trip-map-panel__scroll\s*\{[^}]*overflow-y:\s*auto/s);
  assert.match(styles, /\.map-content\s*\{[^}]*height:\s*530px/s);
  assert.match(styles, /\.map-card\s*\{[^}]*height:\s*530px/s);
  assert.match(styles, /\.map-card__toolbar\s*\{[^}]*padding:\s*8px 10px 5px/s);
  assert.match(styles, /\.map-canvas\s*\{[^}]*flex:\s*1 1 auto/s);
  assert.match(styles, /@media\s*\(max-width:\s*820px\)[\s\S]*?grid-template-areas:\s*"map"\s+"itinerary"\s+"lodging"/);
  assert.match(styles, /@media\s*\(max-width:\s*520px\)[\s\S]*?\.map-card,\s*\.trip-map-panel\s*\{\s*height:\s*360px/s);
  assert.match(styles, /\.map-content--expanded\s*\{[^}]*position:\s*fixed/s);
  assert.match(component, /mapMarkerIdForTripActivity\(activity\?\.id\)/);
  assert.match(component, /lodging-map-marker--selected/);
});

test('map icon import cannot shadow the built-in Map used for marker storage', () => {
  const component = readFileSync(new URL('./MapPlaces.jsx', import.meta.url), 'utf8');
  assert.match(component, /Map as MapIcon/);
  assert.match(component, /new globalThis\.Map\(\)/);
  assert.doesNotMatch(component, /import\s*\{[^}]*\bMap\s*\}/s);
});

test('distance is calculated only from valid coordinates', () => {
  assert.ok(distanceKm([4.7, -74.1], { latitud: 4.71, longitud: -74.1 }) > 0);
  assert.equal(distanceKm([91, -74], { latitud: 4, longitud: -74 }), Number.POSITIVE_INFINITY);
  assert.equal(distanceKm([4, -74], {}), Number.POSITIVE_INFINITY);
});

test('map reuses the trip-scoped verified destination and does not erase it after temporary provider errors', () => {
  const component = readFileSync(new URL('./MapPlaces.jsx', import.meta.url), 'utf8');
  assert.match(component, /useWorkspaceState\('destinationLocation', null\)/);
  assert.match(component, /normalizeDestination\(destinationLocation\.name\) === normalizedTripDestination/);
  const locationEffect = component.slice(component.indexOf('apiRequest\(`/api/viajes/\$\{trip\.id\}/destino/ubicacion'),
    component.indexOf('}, [token, trip.id, trip.destination, reloadKey'));
  assert.match(locationEffect, /setLocationStatus\('provider-unavailable'\)/);
  assert.match(locationEffect, /setLocationStatus\('stale'\)/);
  assert.doesNotMatch(locationEffect, /setDestination\(null\)/);
});

test('saving the itinerary refreshes only saved activities, while explicit retry refreshes provider reads', () => {
  const component = readFileSync(new URL('./MapPlaces.jsx', import.meta.url), 'utf8');
  assert.match(component, /setItineraryReloadKey\(\(current\) => current \+ 1\)/);
  assert.match(component, /\[token, trip\.id, itineraryReloadKey\]/);
  assert.match(component, /\[token, trip\.id, reloadKey\]/);
  assert.match(component, /Reintentar carga de mapa y lugares/);
});

test('map and lodging search errors preserve previously verified recommendations and lodging rows', () => {
  const map = readFileSync(new URL('./MapPlaces.jsx', import.meta.url), 'utf8');
  const extras = readFileSync(new URL('../components/TripPlanningExtras.jsx', import.meta.url), 'utf8');
  const recommendationFailure = map.slice(map.indexOf('.catch((requestError) => { if (active) setRecommendationError'),
    map.indexOf('return () => { active = false; };', map.indexOf('.catch((requestError) => { if (active) setRecommendationError')));
  assert.doesNotMatch(recommendationFailure, /setRecommendedPlaces\(\[\]\)/);
  const searchBody = extras.slice(extras.indexOf('const searchLodging = async () =>'), extras.indexOf('const visibleLodgingSuggestions'));
  assert.doesNotMatch(searchBody, /setLodgingSuggestions\(\[\]\)/);
});
