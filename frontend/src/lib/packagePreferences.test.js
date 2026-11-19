import test from 'node:test';
import assert from 'node:assert/strict';
import { loadPackagePreferences, packagePreferencesKey, savePackagePreferences } from './packagePreferences.js';

function memoryStorage() {
  const values = new Map();
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), values };
}

test('stores safe package preferences independently by user and trip', () => {
  const storage = memoryStorage();
  assert.equal(savePackagePreferences(storage, 7, 101, {
    travelers: 3, budgetCurrency: 'eur', flightCabinClass: 'business', lodgingPreference: 'apartment',
    token: 'must-not-persist',
  }), true);
  assert.deepEqual(loadPackagePreferences(storage, 7, 101), {
    travelers: 3, budgetCurrency: 'EUR', flightCabinClass: 'business', lodgingPreference: 'apartment',
  });
  assert.equal(loadPackagePreferences(storage, 8, 101), null);
  assert.equal(loadPackagePreferences(storage, 7, 102), null);
  assert.doesNotMatch(storage.values.get(packagePreferencesKey(7, 101)), /must-not-persist|token/);
});

test('normalizes invalid preferences and ignores malformed or versioned data', () => {
  const storage = memoryStorage();
  savePackagePreferences(storage, 'user', 'trip', { travelers: 99, budgetCurrency: 'bad-code', flightCabinClass: 'admin', lodgingPreference: 'anywhere' });
  assert.deepEqual(loadPackagePreferences(storage, 'user', 'trip'), {
    travelers: 20, budgetCurrency: 'COP', flightCabinClass: 'economy', lodgingPreference: 'all',
  });
  storage.setItem(packagePreferencesKey('user', 'trip'), '{bad json');
  assert.equal(loadPackagePreferences(storage, 'user', 'trip'), null);
  storage.setItem(packagePreferencesKey('user', 'trip'), JSON.stringify({ version: 9, travelers: 2 }));
  assert.equal(loadPackagePreferences(storage, 'user', 'trip'), null);
});

test('does not write when user or trip identity is missing', () => {
  const storage = memoryStorage();
  assert.equal(savePackagePreferences(storage, null, 10, { travelers: 2 }), false);
  assert.equal(savePackagePreferences(storage, 1, null, { travelers: 2 }), false);
  assert.equal(storage.values.size, 0);
});
