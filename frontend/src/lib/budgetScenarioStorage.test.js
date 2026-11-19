import test from 'node:test';
import assert from 'node:assert/strict';
import { budgetScenarioStorageKey, clearBudgetScenario, readBudgetScenario, writeBudgetScenario } from './budgetScenarioStorage.js';

function memoryStorage(seed = {}) {
  const values = new Map(Object.entries(seed));
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };
}

const expense = { id: 'manual-1', category: 'Vuelo', label: 'Estimación vuelo', amount: 250, currency: 'USD', confidence: 'MANUAL_ESTIMATE', rateToBudget: 4000 };

test('isolates a local budget by user and trip and restores expenses', () => {
  const storage = memoryStorage();
  const firstKey = budgetScenarioStorageKey(7, 19);
  const otherUser = budgetScenarioStorageKey(8, 19);
  const otherTrip = budgetScenarioStorageKey(7, 20);
  assert.notEqual(firstKey, otherUser);
  assert.notEqual(firstKey, otherTrip);
  assert.equal(writeBudgetScenario(storage, firstKey, [expense]), true);
  assert.deepEqual(readBudgetScenario(storage, firstKey), { expenses: [expense], status: 'ready' });
  assert.deepEqual(readBudgetScenario(storage, otherUser), { expenses: [], status: 'ready' });
});

test('deduplicates stored expenses and leaves corrupt data untouched for explicit recovery', () => {
  const key = budgetScenarioStorageKey(7, 19);
  const storage = memoryStorage({ [key]: JSON.stringify({ version: 1, expenses: [expense, expense] }) });
  assert.equal(readBudgetScenario(storage, key).expenses.length, 1);
  storage.setItem(key, '{broken');
  assert.deepEqual(readBudgetScenario(storage, key), { expenses: [], status: 'corrupt' });
  assert.equal(storage.getItem(key), '{broken');
  assert.equal(clearBudgetScenario(storage, key), true);
  assert.deepEqual(readBudgetScenario(storage, key), { expenses: [], status: 'ready' });
});

test('does not persist credentials and rejects malformed expense payloads', () => {
  const storage = memoryStorage();
  const key = budgetScenarioStorageKey(7, 19);
  assert.equal(writeBudgetScenario(storage, key, [{ ...expense, token: 'not-allowed' }]), false);
  assert.equal(writeBudgetScenario(storage, key, [{ ...expense, amount: -1 }]), false);
  assert.equal(storage.getItem(key), null);
});

test('persists the scenario traveler count without mixing it with expense amounts', () => {
  const storage = memoryStorage();
  const key = budgetScenarioStorageKey(7, 19);
  assert.equal(writeBudgetScenario(storage, key, [expense], 3), true);
  assert.deepEqual(readBudgetScenario(storage, key), { expenses: [expense], status: 'ready', travelers: 3 });
  assert.equal(writeBudgetScenario(storage, key, [expense], 0), false);
});

test('round-trips an unknown package expense as pending without requiring an amount or currency', () => {
  const storage = memoryStorage();
  const key = budgetScenarioStorageKey('user-1', 'trip-341');
  const pending = { id: 'hotel:341:apartments', category: 'Alojamiento', label: 'Tarifa pendiente',
    amount: null, currency: null, confidence: 'UNKNOWN' };
  assert.equal(writeBudgetScenario(storage, key, [pending]), true);
  const restored = readBudgetScenario(storage, key);
  assert.equal(restored.status, 'ready');
  assert.deepEqual(restored.expenses, [pending]);
});

test('persists dated reference-rate evidence without storing credentials', () => {
  const storage = memoryStorage();
  const key = budgetScenarioStorageKey(7, 19);
  const referenced = { ...expense, rateSource: 'Frankfurter · tasa de referencia diaria', rateDate: '2026-11-13' };
  assert.equal(writeBudgetScenario(storage, key, [referenced]), true);
  assert.deepEqual(readBudgetScenario(storage, key).expenses, [referenced]);
  assert.equal(writeBudgetScenario(storage, key, [{ ...referenced, rateDate: 'yesterday' }]), false);
});
