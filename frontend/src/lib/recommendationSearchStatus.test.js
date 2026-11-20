import test from 'node:test';
import assert from 'node:assert/strict';
import { emptyRecommendationStatus } from './recommendationSearchStatus.js';

test('empty search reports the verifiable source counts without claiming generation success', () => {
  const message = emptyRecommendationStatus({
    fuentesConsultadas: ['Geoapify', 'Wikidata'],
    resultadosConservadosPorFuente: { Geoapify: 0, Wikidata: 0 },
    atractivosFinales: 0,
  });
  assert.match(message, /Búsqueda completada sin candidatos elegibles/);
  assert.match(message, /Geoapify: 0 conservados/);
  assert.match(message, /Wikidata: 0 conservados/);
  assert.doesNotMatch(message, /propuesta automática lista/i);
});

test('empty search distinguishes provider warnings from a confirmed empty result', () => {
  assert.match(emptyRecommendationStatus(null, ['Geoapify alcanzó temporalmente el límite']), /límite/);
  assert.match(emptyRecommendationStatus(null, [], 'HTTP 503'), /HTTP 503/);
  assert.match(emptyRecommendationStatus(null), /no proporcionó el desglose por fuente/);
});
