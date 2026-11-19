import { hasSpecificTourismIdentity } from '../lib/tourismItineraryDraft.js';

export function coordinates(latitude, longitude) {
  if (latitude === null || latitude === undefined || latitude === ''
      || longitude === null || longitude === undefined || longitude === '') return null;
  const lat = Number(latitude);
  const lon = Number(longitude);
  return Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180
    ? [lat, lon] : null;
}

export function normalizeLodgingSuggestions(payload) {
  if (!Array.isArray(payload)) return [];
  return payload.flatMap((place) => {
    if (!place || typeof place !== 'object' || Array.isArray(place)) return [];
    const point = coordinates(place.latitud, place.longitud);
    if (!point || typeof place.nombre !== 'string' || !place.nombre.trim()) return [];
    return [{ ...place, nombre: place.nombre.trim(), coordinates: point }];
  });
}

export function normalizeRecommendations(response) {
  const catalog = Array.isArray(response?.actividades) ? response.actividades : [];
  const external = Array.isArray(response?.recomendacionesExternas) ? response.recomendacionesExternas : [];
  const places = [];
  let unmappedCount = 0;

  for (const item of catalog) {
    const activity = item?.actividad;
    if (!activity || activity.id == null || typeof activity.nombre !== 'string' || !activity.nombre.trim()) continue;
    const point = coordinates(activity.latitud, activity.longitud);
    if (!point) unmappedCount++;
    places.push({
      id: `catalog-${activity.id}`,
      name: activity.nombre.trim(),
      location: typeof activity.ubicacion === 'string' && activity.ubicacion.trim() ? activity.ubicacion : 'Ubicación no informada',
      description: typeof activity.descripcion === 'string' ? activity.descripcion : null,
      coordinates: point,
      source: 'Catálogo SmartTrip',
      interests: Array.isArray(item.interesesCoincidentes) ? item.interesesCoincidentes.filter((value) => typeof value === 'string') : [],
      category: typeof activity.categoria === 'string' && activity.categoria.trim() ? activity.categoria : 'Actividad',
    });
  }

  for (const place of external) {
    if (!place || typeof place !== 'object' || Array.isArray(place)
        || place.id == null || typeof place.nombre !== 'string' || !place.nombre.trim()) continue;
    // The backend signal is category/provider evidence, not a rating. Do not crowd the
    // travel map with weak generic attraction tags such as incidental statues or plaques.
    if (!hasSpecificTourismIdentity(place)) continue;
    const point = coordinates(place.latitud, place.longitud);
    if (!point) unmappedCount++;
    places.push({
      id: String(place.id),
      name: place.nombre.trim(),
      location: typeof place.ubicacion === 'string' && place.ubicacion.trim() ? place.ubicacion : 'Ubicación no informada',
      description: typeof place.descripcion === 'string' ? place.descripcion : null,
      coordinates: point,
      source: typeof place.urlFuente === 'string' && place.urlFuente.trim() ? place.urlFuente : 'Geoapify / OpenStreetMap',
      interests: Array.isArray(place.interesesCoincidentes) ? place.interesesCoincidentes.filter((value) => typeof value === 'string') : [],
      category: typeof place.categoria === 'string' && place.categoria.trim() ? place.categoria : 'Lugar',
      imageUrl: typeof place.imagenUrl === 'string' ? place.imagenUrl : null,
    });
  }

  return {
    places,
    unmappedCount,
    warnings: Array.isArray(response?.avisosFuentes)
      ? response.avisosFuentes.filter((warning) => typeof warning === 'string' && warning.trim())
      : [],
    requiresInterests: response?.requiereSeleccionarIntereses === true,
  };
}

export function normalizeSavedItineraryPlaces(itinerary) {
  const days = Array.isArray(itinerary?.dias) ? itinerary.dias : [];
  return days.flatMap((day, dayIndex) => (Array.isArray(day?.actividades) ? day.actividades : [])
    .flatMap((activity, activityIndex) => {
      if (!activity || activity.id == null || typeof activity.nombre !== 'string' || !activity.nombre.trim()) return [];
      const point = coordinates(activity.latitud, activity.longitud);
      if (!point) return [];
      return [{
        id: `catalog-${activity.id}`,
        name: activity.nombre.trim(),
        location: typeof activity.ubicacion === 'string' && activity.ubicacion.trim() ? activity.ubicacion : 'Ubicación no informada',
        description: typeof activity.descripcion === 'string' ? activity.descripcion : null,
        coordinates: point,
        source: 'Actividad guardada en el itinerario',
        interests: [],
        category: typeof activity.categoria === 'string' && activity.categoria.trim() ? activity.categoria : 'Actividad',
        dayId: String(day.id ?? day.fecha ?? dayIndex),
        dayNumber: dayIndex + 1,
        dayDate: day.fecha || null,
        order: Number.isFinite(Number(activity.orden)) && Number(activity.orden) > 0 ? Number(activity.orden) : activityIndex + 1,
        time: activity.horaLocal || activity.hora || null,
        isSaved: true,
      }];
    }));
}

/** Converts the planner's canonical effective draft into the same map marker contract. */
export function normalizeDraftItineraryPlaces(days) {
  return (Array.isArray(days) ? days : []).flatMap((day, dayIndex) => (Array.isArray(day?.actividades) ? day.actividades : [])
    .flatMap((activity, activityIndex) => {
      if (!activity || activity.id == null || typeof activity.nombre !== 'string' || !activity.nombre.trim()) return [];
      const id = String(activity.id);
      const markerId = id.startsWith('external:') ? id.slice('external:'.length)
        : id.startsWith('catalog:') ? `catalog-${id.slice('catalog:'.length)}`
          : id.startsWith('manual:') ? `manual-${id.slice('manual:'.length)}` : `catalog-${id}`;
      return [{
        id: markerId,
        name: activity.nombre.trim(),
        location: typeof activity.ubicacion === 'string' && activity.ubicacion.trim() ? activity.ubicacion : 'Ubicación no informada',
        description: typeof activity.descripcion === 'string' ? activity.descripcion : null,
        coordinates: coordinates(activity.latitud, activity.longitud),
        source: activity.fuente || (id.startsWith('external:') ? 'Recomendación externa del borrador' : 'Borrador local'),
        interests: Array.isArray(activity.intereses) ? activity.intereses : [],
        category: typeof activity.categoria === 'string' && activity.categoria.trim() ? activity.categoria : 'Actividad',
        dayId: String(day.id ?? day.fecha ?? dayIndex),
        dayNumber: dayIndex + 1,
        dayDate: day.fecha || null,
        order: Number.isFinite(Number(activity.orden)) && Number(activity.orden) > 0 ? Number(activity.orden) : activityIndex + 1,
        time: activity.horaLocal || activity.hora || null,
        inDraft: true,
      }];
    }));
}

export function distanceKm(center, place) {
  const point = coordinates(place?.latitud, place?.longitud);
  if (!Array.isArray(center) || center.length !== 2 || !point
      || !center.every(Number.isFinite) || Math.abs(center[0]) > 90 || Math.abs(center[1]) > 180) {
    return Number.POSITIVE_INFINITY;
  }
  const radians = (degrees) => degrees * Math.PI / 180;
  const [lat1, lon1] = center;
  const [lat2, lon2] = point;
  const dLat = radians(lat2 - lat1);
  const dLon = radians(lon2 - lon1);
  const a = Math.min(1, Math.max(0, Math.sin(dLat / 2) ** 2
    + Math.cos(radians(lat1)) * Math.cos(radians(lat2)) * Math.sin(dLon / 2) ** 2));
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
