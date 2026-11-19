import test from 'node:test';
import assert from 'node:assert/strict';
import { packageSelectionStorageKey, readPackageSelections, writePackageSelections } from './tripPackageWorkspaceStorage.js';

function memoryStorage() {
  const values = new Map();
  return { values, getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value) };
}

test('package selections persist and restore by both user and trip without credentials', () => {
  const storage = memoryStorage();
  const selectedFlight = { id: 'offer-1', totalAmount: '340.00', currency: 'GBP', testData: true,
    accessToken: 'must-not-persist', slices: [{ origin: 'BOG', destination: 'BCN', segments: [
      { origin: 'BOG', destination: 'BCN', flightNumber: 'AB123', secret: 'omit' },
    ] }] };
  const selectedLodging = { id: 'hotel-1', nombre: 'Hotel', direccion: 'Barcelona', latitud: 41.3,
    longitud: 2.1, imagenUrl: 'javascript:alert(1)', apiKey: 'omit' };
  assert.equal(writePackageSelections(storage, 'user-a', 301, { selectedFlight, selectedLodging }), true);
  assert.deepEqual(readPackageSelections(storage, 'user-a', 301).selectedFlight.slices[0].segments[0], {
    origin: 'BOG', destination: 'BCN', departureAt: '', arrivalAt: '', operatingCarrier: '',
    marketingCarrier: '', flightNumber: 'AB123',
  });
  assert.equal(readPackageSelections(storage, 'user-a', 301).selectedLodging.imagenUrl, '');
  assert.equal(readPackageSelections(storage, 'user-a', 302).selectedFlight, null);
  assert.equal(readPackageSelections(storage, 'user-b', 301).selectedLodging, null);
  const serialized = storage.getItem(packageSelectionStorageKey('user-a', 301));
  assert.doesNotMatch(serialized, /must-not-persist|apiKey|secret|accessToken/);
});

test('malformed package selection remains untouched and safely restores empty selections', () => {
  const storage = memoryStorage();
  const key = packageSelectionStorageKey('user-a', 301);
  storage.setItem(key, '{broken');
  assert.deepEqual(readPackageSelections(storage, 'user-a', 301), { selectedFlight: null, selectedLodging: null, status: 'corrupt' });
  assert.equal(storage.getItem(key), '{broken');
});
