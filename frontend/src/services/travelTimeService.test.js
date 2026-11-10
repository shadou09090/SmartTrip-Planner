import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateHaversineDistance,
  formatDuration,
  generateCacheKey,
  getEstimatedTravelTimes,
  clearTravelTimeCache,
  getCacheSize,
} from './travelTimeService.js';

test('calculateHaversineDistance computes reasonable distance', () => {
  // Museo del Oro to Plaza de Bolívar in Bogotá (~600m straight line)
  const dist = calculateHaversineDistance(4.6019, -74.0720, 4.5981, -74.0758);
  assert.ok(dist > 0.4 && dist < 1.5, `Distancia esperada entre 0.4 y 1.5 km, obtenida: ${dist}`);
});

test('formatDuration formats minutes accurately', () => {
  assert.equal(formatDuration(15), '15 min');
  assert.equal(formatDuration(60), '1 h');
  assert.equal(formatDuration(125), '2 h 5 min');
  assert.equal(formatDuration(180), '3 h');
});

test('Criterio 1: Devuelve tiempos estimados a pie, en transporte público y en automóvil', () => {
  clearTravelTimeCache();

  const act1 = {
    texto: 'Museo del Oro',
    coordenadas: [4.6019, -74.0720]
  };
  const act2 = {
    texto: 'Plaza de Bolívar',
    coordenadas: [4.5981, -74.0758]
  };

  const resultado = getEstimatedTravelTimes(act1, act2);
  assert.ok(resultado, 'El resultado debe existir');
  assert.ok(resultado.modes.walking, 'Debe incluir modo a pie');
  assert.ok(resultado.modes.transit, 'Debe incluir modo transporte público');
  assert.ok(resultado.modes.driving, 'Debe incluir modo automóvil');

  assert.ok(resultado.modes.walking.minutes > 0);
  assert.ok(resultado.modes.transit.minutes > 0);
  assert.ok(resultado.modes.driving.minutes > 0);

  assert.equal(resultado.fromCache, false, 'La primera consulta no debe venir de caché');
});

test('Criterio 2: Activa advertencia visual si el tiempo de traslado supera 120 minutos', () => {
  clearTravelTimeCache();

  // Distancia Bogotá a Zipaquirá (~50 km) donde caminando toma > 10 horas (> 600 min)
  const act1 = {
    texto: 'Plaza de Bolívar Bogotá',
    coordenadas: [4.5981, -74.0758]
  };
  const act2 = {
    texto: 'Catedral de Sal Zipaquirá',
    coordenadas: [5.0192, -74.0044]
  };

  const resultado = getEstimatedTravelTimes(act1, act2);
  assert.ok(resultado.modes.walking.minutes > 120, 'Caminando debe superar 120 minutos');
  assert.equal(resultado.modes.walking.exceeds120, true, 'exceeds120 debe ser true para walking');
  assert.equal(resultado.hasAnyExceeds120, true, 'hasAnyExceeds120 debe ser true');
});

test('Criterio 3: Recupera de caché en consultas repetidas sin demora', () => {
  clearTravelTimeCache();

  const act1 = {
    texto: 'Monserrate',
    coordenadas: [4.6057, -74.0555]
  };
  const act2 = {
    texto: 'Museo del Oro',
    coordenadas: [4.6019, -74.0720]
  };

  // Primera consulta (calcula y guarda)
  const res1 = getEstimatedTravelTimes(act1, act2);
  assert.equal(res1.fromCache, false);
  assert.equal(getCacheSize(), 1);

  // Segunda consulta (obtiene de caché sin demora perceptible)
  const t0 = performance.now();
  const res2 = getEstimatedTravelTimes(act1, act2);
  const t1 = performance.now();

  assert.equal(res2.fromCache, true, 'Debe ser retornado desde caché');
  assert.equal(res2.distanceKm, res1.distanceKm);
  assert.equal(res2.modes.walking.minutes, res1.modes.walking.minutes);
  assert.ok(t1 - t0 < 5, `Tiempo de recuperación debe ser prácticamente 0ms (${t1 - t0}ms)`);
});
