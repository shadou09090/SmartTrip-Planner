import { test, expect } from 'vitest';
import {
  calculateHaversineDistance,
  formatDuration,
  getEstimatedTravelTimes,
  clearTravelTimeCache,
  getCacheSize,
} from './travelTimeService.js';

test('calculateHaversineDistance computes reasonable distance', () => {
  const dist = calculateHaversineDistance(4.6019, -74.0720, 4.5981, -74.0758);
  expect(dist).toBeGreaterThan(0.4);
  expect(dist).toBeLessThan(1.5);
});

test('formatDuration formats minutes accurately', () => {
  expect(formatDuration(15)).toBe('15 min');
  expect(formatDuration(60)).toBe('1 h');
  expect(formatDuration(125)).toBe('2 h 5 min');
  expect(formatDuration(180)).toBe('3 h');
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
  expect(resultado).toBeTruthy();
  expect(resultado.modes.walking).toBeTruthy();
  expect(resultado.modes.transit).toBeTruthy();
  expect(resultado.modes.driving).toBeTruthy();

  expect(resultado.modes.walking.minutes).toBeGreaterThan(0);
  expect(resultado.modes.transit.minutes).toBeGreaterThan(0);
  expect(resultado.modes.driving.minutes).toBeGreaterThan(0);

  expect(resultado.fromCache).toBe(false);
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
  expect(resultado.modes.walking.minutes).toBeGreaterThan(120);
  expect(resultado.modes.walking.exceeds120).toBe(true);
  expect(resultado.hasAnyExceeds120).toBe(true);
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
  expect(res1.fromCache).toBe(false);
  expect(getCacheSize()).toBe(1);

  const t0 = performance.now();
  const res2 = getEstimatedTravelTimes(act1, act2);
  const t1 = performance.now();

  expect(res2.fromCache).toBe(true);
  expect(res2.distanceKm).toBe(res1.distanceKm);
  expect(res2.modes.walking.minutes).toBe(res1.modes.walking.minutes);
  expect(t1 - t0).toBeLessThan(100);
});
