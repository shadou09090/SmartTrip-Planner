import test from 'node:test';
import assert from 'node:assert/strict';
import { normalizeDestinationCandidates } from './destinationCandidates.js';

test('keeps country and region context for ambiguous international destination choices', () => {
  const results = normalizeDestinationCandidates([
    { id: '1', nombreCompleto: 'San José, Costa Rica', ciudad: 'San José', pais: 'Costa Rica', latitud: '9.93', longitud: '-84.08' },
    { id: '2', nombreCompleto: 'San José, California, United States', ciudad: 'San José', region: 'California', pais: 'United States', latitud: '37.33', longitud: '-121.89' },
  ]);
  assert.equal(results.length, 2);
  assert.equal(results[0].pais, 'Costa Rica');
  assert.equal(results[1].region, 'California');
  assert.equal(results[1].latitud, 37.33);
});

test('rejects malformed candidates and out-of-range international coordinates', () => {
  assert.deepEqual(normalizeDestinationCandidates([
    { id: 'bad', nombreCompleto: 'Nowhere', latitud: 91, longitud: 0 },
    { id: 'missing', nombreCompleto: 'No point' },
    { id: 'ok', nombreCompleto: 'Tokyo, Japan', latitud: 35.6, longitud: 139.6 },
  ]).map((item) => item.id), ['ok']);
});
