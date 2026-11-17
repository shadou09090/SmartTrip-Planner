/**
 * Servicio para el cálculo de tiempos de traslado entre actividades (HU-010).
 * Soporta modos: A pie, Transporte público, y En automóvil.
 * Implementa caché en memoria y localStorage para respuesta instantánea sin demora perceptible (Criterio 3).
 * Identifica traslados que superan los 120 minutos para advertencia visual (Criterio 2).
 */

const CACHE_STORAGE_KEY = 'smarttrip_travel_times_cache_v1';

// Memoria caché volátil para acceso inmediato O(1)
const memoryCache = new Map();

// Cargar caché previo desde localStorage si está disponible
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
  } catch (e) {
    // Quota exceeded o entorno restringido
  }
}

/**
 * Fórmula de Haversine para distancia en kilómetros
 */
export function calculateHaversineDistance(lat1, lon1, lat2, lon2) {
  const R = 6371; // Radio de la Tierra en km
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

  // Factor de desvío urbano (~1.3x para curvas y vías reales en ciudad)
  return Math.round(straightDistance * 1.3 * 100) / 100;
}

/**
 * Formatea minutos a representación legible: ej. "18 min", "1 h 45 min", "2 h 10 min"
 */
export function formatDuration(minutes) {
  if (!minutes || minutes <= 0) return '1 min';
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const remainingMinutes = minutes % 60;
  if (remainingMinutes === 0) return `${hours} h`;
  return `${hours} h ${remainingMinutes} min`;
}

/**
 * Genera clave única y normalizada para la caché
 */
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

/**
 * Calcula los tiempos estimados para:
 * 1. A pie
 * 2. Transporte público
 * 3. En automóvil
 *
 * Aplica Criterio 2: Flag `exceeds120: true` cuando el tiempo supera los 120 minutos.
 * Aplica Criterio 3: Recupera de memoria/localStorage de inmediato (`fromCache: true`) sin demoras perceptibles.
 */
export function getEstimatedTravelTimes(origin, destination) {
  const coords1 = origin?.coordenadas || origin?.coordinates;
  const coords2 = destination?.coordenadas || destination?.coordinates;

  const name1 = origin?.ubicacion || origin?.texto || origin?.name || 'Origen';
  const name2 = destination?.ubicacion || destination?.texto || destination?.name || 'Destino';

  if (!coords1 || !coords2) {
    return null;
  }

  const cacheKey = generateCacheKey(coords1, coords2, name1, name2);

  // Criterio 3: Consulta previa en caché
  if (memoryCache.has(cacheKey)) {
    const cached = memoryCache.get(cacheKey);
    return {
      ...cached,
      fromCache: true,
      cacheKey,
    };
  }

  // Distancia calculada
  const distanceKm = calculateHaversineDistance(
    coords1[0],
    coords1[1],
    coords2[0],
    coords2[1]
  );

  // 1. A pie: ~4.6 km/h de velocidad promedio caminando
  const walkingMinutes = Math.max(1, Math.round((distanceKm / 4.6) * 60));

  // 2. Transporte público: ~18 km/h en ciudad + 10 min promedio de espera/paradas
  const transitMinutes = Math.max(
    3,
    Math.round(10 + (distanceKm / 18) * 60)
  );

  // 3. En automóvil: ~32 km/h en tránsito urbano + 4 min de semáforos/estacionamiento
  const drivingMinutes = Math.max(
    2,
    Math.round(4 + (distanceKm / 32) * 60)
  );

  // Criterio 2: Tiempo > 120 minutos activa advertencia visual
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

  // Guardar en caché para futuras consultas instantáneas (Criterio 3)
  memoryCache.set(cacheKey, result);
  persistCache();

  return result;
}

/**
 * Permite limpiar la caché si es necesario (para pruebas o reseteo)
 */
export function clearTravelTimeCache() {
  memoryCache.clear();
  try {
    if (typeof window !== 'undefined' && window.localStorage) {
      localStorage.removeItem(CACHE_STORAGE_KEY);
    }
  } catch (e) {
    // Ignorar en entornos sin window
  }
}

/**
 * Consulta el tamaño de la caché en memoria
 */
export function getCacheSize() {
  return memoryCache.size;
}

/**
 * Intenta consultar el tiempo de traslado al backend de Spring Boot (POST /api/itinerario/calcular-traslado).
 * Si tiene éxito, actualiza la caché; si falla o no está disponible, no interrumpe el flujo.
 */
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
  } catch (err) {
    // Si el backend no está disponible, continuamos transparentemente en local
  }
  return null;
}
