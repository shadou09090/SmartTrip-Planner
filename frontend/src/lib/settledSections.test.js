import test from 'node:test';
import assert from 'node:assert/strict';
import { partitionSettledResults } from './settledSections.js';

test('keeps recommendation response when an unrelated trip section fails', () => {
  const lodgingError = new Error('GET /alojamiento (HTTP 404)');
  const { fulfilled, rejected } = partitionSettledResults(
    ['itinerary', 'recommendations', 'lodging'],
    [
      { status: 'fulfilled', value: { days: [] } },
      { status: 'fulfilled', value: { recomendacionesExternas: [{ id: 'osm:1' }] } },
      { status: 'rejected', reason: lodgingError },
    ],
  );

  assert.equal(fulfilled.recommendations.recomendacionesExternas.length, 1);
  assert.equal(rejected.lodging, lodgingError);
});

test('reports recommendation failure independently without discarding other sections', () => {
  const sourceError = new Error('HTTP 503');
  const { fulfilled, rejected } = partitionSettledResults(
    ['budget', 'recommendations'],
    [
      { status: 'fulfilled', value: { total: 0 } },
      { status: 'rejected', reason: sourceError },
    ],
  );

  assert.equal(fulfilled.budget.total, 0);
  assert.equal(rejected.recommendations, sourceError);
});
