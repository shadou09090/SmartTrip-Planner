import test from 'node:test';
import assert from 'node:assert/strict';
import { formatFriendlyError } from './errorUtils.js';

test('formatFriendlyError sanitizes raw technical HTTP 500 errors', () => {
  const technical = 'GET /api/viajes/1/alojamiento (HTTP 500): La solicitud no pudo completarse. Referencia: a2179ed3-01d3-4797-a89e-391e2d7038a6';
  const friendly = formatFriendlyError(technical);
  assert.equal(friendly, 'No fue posible completar la operación en este momento. Inténtalo de nuevo más tarde.');
  assert.equal(friendly.includes('GET'), false);
  assert.equal(friendly.includes('500'), false);
  assert.equal(friendly.includes('Referencia'), false);
});

test('formatFriendlyError handles connection failure', () => {
  assert.equal(formatFriendlyError(new Error('Failed to fetch')),
    'No se pudo conectar con el servidor. Revisa tu conexión a internet o asegúrate de que el backend esté en ejecución.');
});

test('formatFriendlyError preserves user-facing validation messages while removing HTTP prefix', () => {
  const validation = 'POST /api/viajes/1/vuelos (HTTP 400): La fecha de salida es obligatoria.';
  assert.equal(formatFriendlyError(validation), 'La fecha de salida es obligatoria.');
});

test('formatFriendlyError handles empty inputs safely', () => {
  assert.equal(formatFriendlyError(null), '');
  assert.equal(formatFriendlyError(''), '');
  assert.equal(formatFriendlyError(undefined), '');
});
