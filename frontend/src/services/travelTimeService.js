const CACHE_STORAGE_KEY = 'smarttrip_travel_times_cache_v1';

const memoryCache = new Map();

try {
  if (typeof window !== 'undefined' && window.localStorage) {
    const persisted = localStorage.getItem(CACHE_STORAGE_KEY);
    if (persisted) {
      const parsed = JSON.parse(persisted);
      Object.entries(parsed).forEach(([key, val]) => memoryCache.set(key, val));
    }
  }
} catch (e) {
  console.warn('No se pudo acceder a localStorage para la caché de traslados:', e);
}

function persistCache() {
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      const obj = {};
      memoryCache.forEach((val, key) => {
        obj[key] = val;
      });
      localStorage.setItem(CACHE_STORAGE_KEY, JSON.stringify(obj));
    }
  } catch (e) {}
}

export function calculateHaversineDistance(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  const straightDistance = R * c;

  return Math.round(straightDistance * 1.3 * 100) / 100;
}

export function formatDuration(minutes) {
  if (!minutes || minutes <= 0) return '1 min';
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  if (remainingMinutes === 0) return `${hours} h`;
  return `${hours} h ${remainingMinutes} min`;
}

export function generateCacheKey(coords1, coords2, name1 = '', name2 = '') {
  if (coords1 && coords2) {
    const lat1 = Number(coords1[0]).toFixed(4);
    const lon1 = Number(coords1[1]).toFixed(4);
    const lat2 = Number(coords2[0]).toFixed(4);
    const lon2 = Number(coords2[1]).toFixed(4);
    return `coords:${lat1},${lon1}->${lat2},${lon2}`;
  }
  const clean1 = String(name1).trim().toLowerCase();
  const clean2 = String(name2).trim().toLowerCase();
  return `names:${clean1}->${clean2}`;
}

export function getEstimatedTravelTimes(origin, destination) {
  const coords1 = origin?.coordenadas || origin?.coordinates;
  const coords2 = destination?.coordenadas || destination?.coordinates;

  const name1 = origin?.ubicacion || origin?.texto || origin?.name || 'Origen';
  const name2 = destination?.ubicacion || destination?.texto || destination?.name || 'Destino';

  if (!coords1 || !coords2) {
    return null;
  }

  const cacheKey = generateCacheKey(coords1, coords2, name1, name2);

  if (memoryCache.has(cacheKey)) {
    const cached = memoryCache.get(cacheKey);
    return {
      ...cached,
      fromCache: true,
      cacheKey,
    };
  }

  const distanceKm = calculateHaversineDistance(
    coords1[0],
    coords1[1],
    coords2[0],
    coords2[1]
  );

  const walkingMinutes = Math.max(1, Math.round((distanceKm / 4.6) * 60));

  const transitMinutes = Math.max(
    3,
    Math.round(10 + (distanceKm / 18) * 60)
  );

  const drivingMinutes = Math.max(
    2,
    Math.round(4 + (distanceKm / 32) * 60)
  );

  const UMBRAL_ADVERTENCIA = 120;

  const result = {
    originName: name1,
    destinationName: name2,
    distanceKm,
    distanceText: distanceKm < 1 ? `${Math.round(distanceKm * 1000)} m` : `${distanceKm} km`,
    modes: {
      walking: {
        id: 'walking',
        label: 'A pie',
        icon: 'footprints',
        minutes: walkingMinutes,
        timeFormatted: formatDuration(walkingMinutes),
        exceeds120: walkingMinutes > UMBRAL_ADVERTENCIA,
        speedNote: 'Ritmo normal (4.6 km/h)',
      },
      transit: {
        id: 'transit',
        label: 'Transporte público',
        icon: 'bus',
        minutes: transitMinutes,
        timeFormatted: formatDuration(transitMinutes),
        exceeds120: transitMinutes > UMBRAL_ADVERTENCIA,
        speedNote: 'Incluye transbordos y espera estimada',
      },
      driving: {
        id: 'driving',
        label: 'En automóvil',
        icon: 'car',
        minutes: drivingMinutes,
        timeFormatted: formatDuration(drivingMinutes),
        exceeds120: drivingMinutes > UMBRAL_ADVERTENCIA,
        speedNote: 'Tráfico urbano moderado',
      },
    },
    hasAnyExceeds120:
      walkingMinutes > UMBRAL_ADVERTENCIA ||
      transitMinutes > UMBRAL_ADVERTENCIA ||
      drivingMinutes > UMBRAL_ADVERTENCIA,
    fromCache: false,
    calculatedAt: new Date().toISOString(),
  };

  memoryCache.set(cacheKey, result);
  persistCache();

  return result;
}

export function clearTravelTimeCache() {
  memoryCache.clear();
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      localStorage.removeItem(CACHE_STORAGE_KEY);
    }
  } catch (e) {}
}

export function getCacheSize() {
  return memoryCache.size;
}

export async function fetchBackendTravelTimes(origin, destination) {
  const coords1 = origin?.coordenadas || origin?.coordinates;
  const coords2 = destination?.coordenadas || destination?.coordinates;

  if (!coords1 || !coords2) return null;

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 2500);

    const res = await fetch('http://localhost:8080/api/itinerario/calcular-traslado', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        origen: origin.texto || origin.ubicacion || 'Origen',
        origenLat: coords1[0],
        origenLng: coords1[1],
        destino: destination.texto || destination.ubicacion || 'Destino',
        destinoLat: coords2[0],
        destinoLng: coords2[1],
      }),
      signal: controller.signal,
    });

    clearTimeout(timeout);

    if (res.ok) {
      const data = await res.json();
      return data;
    }
  } catch (err) {}
  return null;
}
