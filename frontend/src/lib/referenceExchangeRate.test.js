import test from 'node:test';
import assert from 'node:assert/strict';
import { clearReferenceExchangeRateCache, fetchReferenceExchangeRate } from './referenceExchangeRate.js';

test('accepts a dated reference rate and caches requests by currency pair', async () => {
  clearReferenceExchangeRateCache();
  let requests = 0;
  const fetchImpl = async (url) => {
    requests += 1;
    assert.equal(url, 'https://api.frankfurter.dev/v2/rate/gbp/cop');
    return { ok: true, json: async () => ({ date: '2026-11-13', base: 'GBP', quote: 'COP', rate: 5000.25 }) };
  };
  const first = await fetchReferenceExchangeRate('GBP', 'COP', { fetchImpl, now: 10_000 });
  const second = await fetchReferenceExchangeRate('GBP', 'COP', { fetchImpl, now: 10_001 });
  assert.deepEqual(second, first);
  assert.equal(first.rate, 5000.25);
  assert.equal(first.date, '2026-11-13');
  assert.match(first.provider, /referencia diaria/);
  assert.equal(requests, 1);
});

test('rejects bad currencies, HTTP errors, and invalid rates without returning a fabricated value', async () => {
  await assert.rejects(() => fetchReferenceExchangeRate('GB', 'COP'), /códigos de moneda/);
  await assert.rejects(() => fetchReferenceExchangeRate('GBP', 'COP', {
    fetchImpl: async () => ({ ok: false, status: 503 }),
  }), /HTTP 503/);
  await assert.rejects(() => fetchReferenceExchangeRate('JPY', 'COP', {
    fetchImpl: async () => ({ ok: true, json: async () => ({ date: '2026-11-13', base: 'JPY', quote: 'COP', rate: 0 }) }),
  }), /inválida/);
});

test('uses identity rate locally when source and target currencies match', async () => {
  assert.deepEqual(await fetchReferenceExchangeRate('COP', 'COP', { fetchImpl: null }), {
    base: 'COP', quote: 'COP', rate: 1, date: null, provider: 'SmartTrip',
  });
});
