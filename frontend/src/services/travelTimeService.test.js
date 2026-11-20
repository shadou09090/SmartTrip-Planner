import test from 'node:test';
import assert from 'node:assert/strict';
import {
  calculateHaversineDistance,
  formatDuration,
  getEstimatedTravelTimes,
  clearTravelTimeCache,
  getCacheSize,
} from './travelTimeService.js';

test('calculateHaversineDistance computes reasonable distance', () => {
  const dist = calculateHaversineDistance(4.6019, -74.0720, 4.5981, -74.0758);
  assert.ok(dist > 0.4);
  assert.ok(dist < 1.5);
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
  assert.ok(resultado);
  assert.ok(resultado.modes.walking);
  assert.ok(resultado.modes.transit);
  assert.ok(resultado.modes.driving);

  assert.ok(resultado.modes.walking.minutes > 0);
  assert.ok(resultado.modes.transit.minutes > 0);
  assert.ok(resultado.modes.driving.minutes > 0);

  assert.equal(resultado.fromCache, false);
});

test('Criterio 2: Activa advertencia visual si el tiempo de traslado supera 120 minutos', () => {
  clearTravelTimeCache();

  const act1 = {
    texto: 'Plaza de Bolívar Bogotá',
    coordenadas: [4.5981, -74.0758]
  };
  const act2 = {
    texto: 'Catedral de Sal Zipaquirá',
    coordenadas: [5.0192, -74.0044]
  };

  const resultado = getEstimatedTravelTimes(act1, act2);
  assert.ok(resultado.modes.walking.minutes > 120);
  assert.equal(resultado.modes.walking.exceeds120, true);
  assert.equal(resultado.hasAnyExceeds120, true);
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

  const res1 = getEstimatedTravelTimes(act1, act2);
  assert.equal(res1.fromCache, false);
  assert.equal(getCacheSize(), 1);

  const t0 = performance.now();
  const res2 = getEstimatedTravelTimes(act1, act2);
  const t1 = performance.now();

  assert.equal(res2.fromCache, true);
  assert.equal(res2.distanceKm, res1.distanceKm);
  assert.equal(res2.modes.walking.minutes, res1.modes.walking.minutes);
  assert.ok(t1 - t0 < 100);
});
