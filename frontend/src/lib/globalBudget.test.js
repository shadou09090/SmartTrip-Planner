import test from 'node:test';
import assert from 'node:assert/strict';
import { calculateGlobalBudget, hotelBudgetExpenseId } from './globalBudget.js';
import { buildItineraryPdf, buildItineraryPrintHtml } from './itineraryPrint.js';

test('global budget deduplicates stable IDs and separates unknown, sandbox, estimate, confirmed and currency conversion', () => {
  const result = calculateGlobalBudget({ total: 9_000_000, currency: 'COP', entries: [
    { id: 'hotel:8', category: 'Alojamiento', label: 'Hôtel du Levant', amount: 1000, currency: 'COP', confidence: 'MANUAL_ESTIMATE' },
    { id: 'hotel:8', category: 'Alojamiento', label: 'duplicado', amount: 1000, currency: 'COP', confidence: 'MANUAL_ESTIMATE' },
    { id: 'activity:external:a', category: 'Actividades y entradas', label: 'Museo', amount: 500, currency: 'COP', confidence: 'VERIFIED_QUOTE' },
    { id: 'food:8', category: 'Alimentación', label: 'diario', amount: 600, currency: 'USD', confidence: 'MANUAL_ESTIMATE', rateToBudget: 4000,
      rateSource: 'Frankfurter · tasa de referencia diaria', rateDate: '2026-11-13' },
    { id: 'flight:8', category: 'Vuelos', label: 'Sandbox', amount: 100, currency: 'COP', confidence: 'SANDBOX' },
    { id: 'stored-flight:4', category: 'Vuelos', label: 'vuelo sin moneda', confidence: 'UNKNOWN' },
  ] });
  assert.equal(result.confirmed, 500);
  assert.equal(result.estimated, 2_401_000);
  assert.equal(result.sandbox, 100);
  assert.equal(result.unknown, 1);
  assert.equal(result.remaining, 9_000_000 - 2_401_500);
  assert.equal(result.byCategory['Alojamiento'], 1000);
  assert.equal(result.included.find((entry) => entry.id === 'food:8').rateDate, '2026-11-13');
});

test('hotel estimates use a property identity that survives reload but does not carry over to a different hotel', () => {
  const first = hotelBudgetExpenseId(18, { nombre: 'Hôtel du Levant', direccion: '6 Rue de la Harpe' });
  const sameAfterReload = hotelBudgetExpenseId('18', { nombre: 'Hotel du Levant', direccion: '6 Rue de la Harpe' });
  const replacement = hotelBudgetExpenseId(18, { nombre: 'Another Hotel', direccion: 'Rue de Rivoli' });
  assert.equal(first, sameAfterReload);
  assert.notEqual(first, replacement);
  assert.notEqual(hotelBudgetExpenseId(18, null), first);
});

test('PDF includes a local financial breakdown and the exact effective draft activities', () => {
  const pdf = buildItineraryPdf({
    trip: { destination: 'Paris', country: 'France', startDate: '2026-11-20', endDate: '2026-11-24', budget: 9000000 },
    itinerary: { dias: [{ id: 'temporary:2026-11-20', fecha: '2026-11-20', actividades: [
      { id: 'external:louvre', nombre: 'Louvre Museum', categoria: 'Museum', moneda: 'EUR' },
    ] }] },
    budget: { currency: 'COP', total: 9000000, confirmed: 0, estimated: 150000, remaining: 8850000,
      unknown: 1, foreignCurrency: 0, byCategory: { Alojamiento: 150000 },
      included: [{ id: 'hotel:2', category: 'Alojamiento', label: 'Hotel du Levant', amount: 150000,
        convertedAmount: 150000, currency: 'COP', confidence: 'MANUAL_ESTIMATE' }],
      unknownEntries: [{ id: 'activity:external:louvre', category: 'Actividades y entradas', label: 'Louvre Museum' }] },
    isDraft: true,
  });
  const text = new TextDecoder('latin1').decode(pdf);
  assert.ok(pdf.length > 100);
  assert.ok(pdf.length <= 5 * 1024 * 1024);
  assert.match(text, /Presupuesto total/);
  assert.match(text, /Hotel du Levant/);
  assert.match(text, /Louvre Museum/);
  assert.match(text, /Importes por verificar/);
  assert.ok(text.indexOf('ITINERARIO POR DIA') < text.indexOf('PRESUPUESTO'));
  assert.match(text, /0\.07 0\.34 0\.38 rg/);
  assert.match(text, /Gu\\355a de viaje/);
  const html = buildItineraryPrintHtml({ trip: { destination: 'París', country: 'Francia', startDate: '2026-11-20', endDate: '2026-11-24' },
    itinerary: { dias: [{ fecha: '2026-11-20', actividades: [{ nombre: 'Musée du Louvre' }] }] }, budget: null, isDraft: true });
  assert.match(html, /Musée du Louvre/);
});

test('PDF presents Frankfurter conversion as dated estimate, not as confirmed expense', () => {
  const pdf = buildItineraryPdf({
    trip: { destination: 'Londres', startDate: '2026-11-20', endDate: '2026-11-20' },
    itinerary: { dias: [{ fecha: '2026-11-20', actividades: [{ nombre: 'British Museum' }] }] },
    budget: { currency: 'COP', total: 100000, confirmed: 0, estimated: 50000, remaining: 50000, unknown: 0, foreignCurrency: 0,
      included: [{ id: 'food:7', category: 'Alimentación', label: 'Comida', amount: 10, currency: 'GBP', confidence: 'MANUAL_ESTIMATE',
        convertedAmount: 50000, converted: true, rateSource: 'Frankfurter · tasa de referencia diaria', rateDate: '2026-11-13' }] },
  });
  const text = new TextDecoder('latin1').decode(pdf);
  assert.match(text, /Frankfurter/);
  assert.match(text, /2026-11-13/);
  assert.match(text, /se contabiliza como estimacion/);
});
