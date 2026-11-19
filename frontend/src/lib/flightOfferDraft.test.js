import test from 'node:test';
import assert from 'node:assert/strict';
import { createFlightDraftFromOffer, getDuffelSliceSummary, isSinglePersistableDuffelSlice } from './flightOfferDraft.js';

const directOffer = {
  id: 'off_direct', totalAmount: '125.50', currency: 'COP',
  slices: [{ origin: 'Bogotá (BOG)', destination: 'Medellín (MDE)', stops: 0,
    segments: [{ origin: 'Bogotá (BOG)', destination: 'Medellín (MDE)',
      departureAt: '2027-02-01T08:15:00-05:00', arrivalAt: '2027-02-01T09:10:00-05:00',
      operatingCarrier: 'Aerolínea de prueba', flightNumber: 'ST123' }] }],
};

test('maps a selected direct one-way offer to editable Oracle flight fields', () => {
  assert.deepEqual(createFlightDraftFromOffer(directOffer), {
    origen: 'Bogotá (BOG)', destino: 'Medellín (MDE)',
    fechaSalida: '2027-02-01T08:15', fechaLlegada: '2027-02-01T09:10',
    aerolinea: 'Aerolínea de prueba', numeroVuelo: 'ST123', precio: '125.50',
  });
  assert.equal(isSinglePersistableDuffelSlice(directOffer), true);
});

test('does not send an unlabelled foreign currency amount to Oracle PRICE', () => {
  const draft = createFlightDraftFromOffer({ ...directOffer, currency: 'USD' });
  assert.equal(draft.precio, '');
  assert.equal(isSinglePersistableDuffelSlice({ ...directOffer, currency: 'USD' }), false);
});

test('keeps connection details visible but prevents flattening multi-segment or round-trip offers', () => {
  const connection = { ...directOffer, slices: [{ ...directOffer.slices[0], stops: 1,
    segments: [...directOffer.slices[0].segments, { ...directOffer.slices[0].segments[0], flightNumber: 'ST456' }] }] };
  const roundTrip = { ...directOffer, slices: [directOffer.slices[0], directOffer.slices[0]] };
  assert.equal(isSinglePersistableDuffelSlice(connection), false);
  assert.equal(isSinglePersistableDuffelSlice(roundTrip), false);
  assert.equal(getDuffelSliceSummary(connection)[0].segments.length, 2);
  assert.equal(getDuffelSliceSummary(roundTrip).length, 2);
});
