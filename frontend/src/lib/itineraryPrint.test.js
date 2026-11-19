import test from 'node:test';
import assert from 'node:assert/strict';
import { buildItineraryPdf, buildItineraryPrintHtml, downloadItineraryPdf } from './itineraryPrint.js';
import { buildDraftItinerary, buildTemporaryTripDays, summarizeDraftDays } from './tourismItineraryDraft.js';

const valid = {
  trip: { destination: 'Santa Marta <script>', startDate: '2026-11-20', endDate: '2026-11-24' },
  itinerary: { dias: [{ fecha: '2026-11-20', actividades: [{ nombre: 'Museo & parque', ubicacion: 'Centro', costo: null }] }] },
  lodging: { nombre: 'Hotel Bocano', direccion: 'Dirección de prueba' },
  flights: [{ origen: 'BOG', destino: 'SMR', fechaSalida: '2026-11-20T08:00', precio: 120 }],
};

test('creates a printable itinerary and escapes untrusted text', () => {
  const html = buildItineraryPrintHtml(valid);
  assert.match(html, /Santa Marta &lt;script&gt;/);
  assert.match(html, /Hotel Bocano/);
  assert.match(html, /Museo &amp; parque/);
  assert.match(html, /Tarifa por verificar/);
  assert.match(html, /BOG/);
  assert.match(html, /moneda no almacenada/);
});

test('exports an editable unsaved draft with budget, cost status and safe source link', () => {
  const html = buildItineraryPrintHtml({
    ...valid,
    itinerary: { dias: [{ fecha: '2026-11-20', actividades: [{ nombre: 'Museum', ubicacion: 'Center', costo: null,
      costoEstado: 'UNKNOWN', fuente: 'Wikidata', urlFuente: 'https://www.wikidata.org/wiki/Q1' }] }] },
    budget: { confirmed: 100, estimated: 25, remaining: 875, unknown: 1, foreignCurrency: 0, currency: 'COP' },
    travelers: 2,
    isDraft: true,
  });
  assert.match(html, /Propuesta editable no guardada en Oracle/);
  assert.match(html, /Wikidata/);
  assert.match(html, /Tarifa por verificar/);
  assert.match(html, /Gastos confirmados/);
  assert.match(html, /2 viajero/);
  assert.match(html, /https:\/\/www.wikidata.org\/wiki\/Q1/);
});

test('prints the currently selected Duffel Sandbox offer as a simulation, not a purchasable or budgeted fare', () => {
  const pdf = buildItineraryPdf({
    ...valid,
    flights: [{ id: 'offer-test-1', provider: 'Duffel', testData: true, totalAmount: '150.00', currency: 'EUR',
      queriedAt: '2026-11-15T12:00:00Z', slices: [{ origin: 'CDG', destination: 'JFK', departureAt: '2026-11-20T09:00:00Z', duration: '8h', stops: 1,
        segments: [{ origin: 'CDG', destination: 'JFK', operatingCarrier: 'Example Air', flightNumber: 'EA10' }] }] }],
    itinerary: { dias: [{ fecha: '2026-11-20', actividades: [{ nombre: 'Museum' }] }] },
    budget: { confirmed: 0, estimated: 0, remaining: 900, unknown: 1, foreignCurrency: 0, currency: 'COP' },
    isDraft: true,
  });
  const raw = Buffer.from(pdf).toString('latin1');
  assert.match(raw, /SIMULACI\\323N Sandbox/);
  assert.match(raw, /importe ficticio, no comprable/);
  assert.match(raw, /CDG/);
  assert.match(raw, /JFK/);
  assert.match(raw, /EUR/);
  assert.ok(pdf.length <= 5 * 1024 * 1024);
});

test('rejects unsafe external links and labels HTML size limit accurately', () => {
  const html = buildItineraryPrintHtml({ ...valid,
    itinerary: { dias: [{ actividades: [{ nombre: 'Place', costo: 0, urlFuente: 'javascript:alert(1)' }] }] },
  });
  assert.doesNotMatch(html, /href="javascript:/);
  assert.match(html, /tamaño final del PDF depende del navegador/);
});

test('refuses to export without trip dates', () => {
  assert.throws(() => buildItineraryPrintHtml({ ...valid, trip: { destination: 'Santa Marta' } }), /destino y fechas/);
});

test('refuses to export an itinerary with no saved activities', () => {
  assert.throws(() => buildItineraryPrintHtml({ ...valid, itinerary: { dias: [{ actividades: [] }] } }), /al menos una actividad/);
});

test('generates a valid under-5MB PDF from the unsaved five-day draft, including all current activities', () => {
  const days = buildTemporaryTripDays('2026-11-20', '2026-11-24');
  const external = [
    { id: 'source-1', nombre: 'Eiffel Tower', categoria: 'Monument', ubicacion: 'Champ de Mars', latitud: 48.8584, longitud: 2.2945, precio: null, proveedor: 'Wikidata' },
    { id: 'source-2', nombre: 'Louvre Museum', categoria: 'Museum', ubicacion: 'Rue de Rivoli', precio: null, proveedor: 'Geoapify' },
  ];
  const currentDraft = buildDraftItinerary({
    days,
    external,
    externalAssignments: { 'source-1': days[0].id, 'source-2': days[0].id },
    manual: [{ id: 'manual:3', dayId: days[0].id, request: { nombre: 'Seine Walk', costo: 0, moneda: 'COP' } }],
  });
  const daySummary = summarizeDraftDays(currentDraft);
  assert.deepEqual(daySummary.map((day) => day.count), [3, 0, 0, 0, 0]);
  const pdf = buildItineraryPdf({
    trip: { name: 'Paris escape', destination: 'Paris', country: 'France', startDate: days[0].fecha, endDate: days[4].fecha },
    itinerary: { dias: currentDraft }, travelers: 2, isDraft: true,
    budget: { confirmed: 0, estimated: 0, remaining: 9000000, unknown: 2, foreignCurrency: 0, currency: 'COP' },
  });
  const raw = Buffer.from(pdf).toString('latin1');
  const pageCount = [...raw.matchAll(/<< \/Type \/Page /g)].length;
  const pageStreams = [...raw.matchAll(/stream\n([\s\S]*?)\nendstream/g)].map((match) => match[1]);
  const startXref = Number(raw.match(/startxref\n(\d+)\n%%EOF/)?.[1]);
  assert.equal(raw.slice(0, 8), '%PDF-1.4');
  assert.equal(raw.slice(startXref, startXref + 4), 'xref');
  assert.ok(pageCount >= 1, 'the itinerary PDF must contain at least one page');
  assert.match(pageStreams[0], /DIA 1/);
  assert.ok(pageStreams.some((stream) => /DIA 2/.test(stream)));
  for (const date of days.map((day) => day.fecha)) assert.ok(raw.includes(date));
  for (const name of ['Eiffel Tower', 'Louvre Museum', 'Seine Walk']) assert.ok(raw.includes(name));
  assert.match(raw, /Tarifa por verificar/);
  assert.match(raw, /Estimacion manual/);
  assert.match(raw, /Gastos confirmados/);
  assert.match(raw, /\/Subtype \/Link/);
  assert.match(raw, /https:\/\/www\.openstreetmap\.org\//);
  assert.match(pageStreams[0], /Abrir mapa \/ fuente/);
  assert.ok(pdf.length > 1000);
  assert.ok(pdf.length <= 5 * 1024 * 1024);
  assert.ok(raw.endsWith('%%EOF'));
});

test('download helper creates a PDF download rather than only opening print UI', () => {
  const calls = [];
  const fakeDocument = { createElement: () => ({ click: () => calls.push('click'), set href(value) { calls.push(value); }, set download(value) { calls.push(value); } }) };
  const fakeUrlApi = { createObjectURL: (blob) => { calls.push(blob.type); return 'blob:pdf'; }, revokeObjectURL: (value) => calls.push(value) };
  downloadItineraryPdf(new Uint8Array(Buffer.from('%PDF-1.4\n%%EOF')), 'trip.pdf', fakeDocument, fakeUrlApi, (callback) => callback());
  assert.deepEqual(calls, ['application/pdf', 'blob:pdf', 'trip.pdf', 'click', 'blob:pdf']);
});
