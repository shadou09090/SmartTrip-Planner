import test from 'node:test';
import assert from 'node:assert/strict';
import { buildTemporaryTripDays } from './tourismItineraryDraft.js';
import { itineraryDraftStorageKey, readItineraryDraft, removeItineraryDraft, writeItineraryDraft } from './tripItineraryDraftStorage.js';

function memoryStorage() {
  const values = new Map();
  return {
    getItem: (key) => values.has(key) ? values.get(key) : null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
    has: (key) => values.has(key),
  };
}

const trip = { id: 163, destination: 'Barcelona, España', startDate: '2026-11-23', endDate: '2026-11-28' };
const userId = 'user-7';

test('restores a user and trip draft after reload with day order, manual activities, and assignments', () => {
  const storage = memoryStorage();
  const days = buildTemporaryTripDays(trip.startDate, trip.endDate);
  const draftDays = days.map((day, index) => ({ ...day, actividades: index === 0
    ? [{ id: 91, nombre: 'Saved activity', orden: 2, descripcion: 'Editable local order' }] : [] }));
  assert.equal(writeItineraryDraft(storage, { userId, trip, itinerary: null, days, draftDays,
    externalAssignments: { 'geo:1': days[1].id }, externalOrder: ['geo:1'], catalogAssignments: { 91: days[0].id },
    manual: [{ id: 'manual:1', dayId: days[2].id, request: { nombre: 'Manual stop', orden: 1 } }] }), true);

  const restored = readItineraryDraft(storage, { userId, trip, itinerary: null, currentDays: days });
  assert.equal(restored.status, 'restored');
  assert.deepEqual(restored.externalAssignments, { 'geo:1': days[1].id });
  assert.deepEqual(restored.externalOrder, ['geo:1']);
  assert.deepEqual(restored.catalogAssignments, { 91: days[0].id });
  assert.deepEqual(restored.manual[0], { id: 'manual:1', dayId: days[2].id, request: { nombre: 'Manual stop', orden: 1 } });
  assert.equal(restored.draftDays[0].actividades[0].orden, 2);
});

test('isolates stored drafts by both user and trip', () => {
  const storage = memoryStorage();
  const days = buildTemporaryTripDays(trip.startDate, trip.endDate);
  writeItineraryDraft(storage, { userId, trip, itinerary: null, days, externalAssignments: { 'geo:1': days[0].id } });
  assert.notEqual(itineraryDraftStorageKey(userId, trip.id), itineraryDraftStorageKey('other-user', trip.id));
  assert.notEqual(itineraryDraftStorageKey(userId, trip.id), itineraryDraftStorageKey(userId, 164));
  assert.equal(readItineraryDraft(storage, { userId: 'other-user', trip, itinerary: null, currentDays: days }).status, 'none');
});

test('does not reapply day assignments after dates change and preserves the old snapshot', () => {
  const storage = memoryStorage();
  const days = buildTemporaryTripDays(trip.startDate, trip.endDate);
  writeItineraryDraft(storage, { userId, trip, itinerary: null, days,
    externalAssignments: { 'geo:1': days[0].id }, externalOrder: ['geo:1'] });
  const newTrip = { ...trip, startDate: '2026-11-24', endDate: '2026-11-29' };
  const changed = readItineraryDraft(storage, { userId, trip: newTrip, itinerary: null,
    currentDays: buildTemporaryTripDays(newTrip.startDate, newTrip.endDate) });
  assert.equal(changed.status, 'dates-changed');
  assert.ok(storage.has(itineraryDraftStorageKey(userId, trip.id)));
});

test('maps legacy day IDs by date and retains removed-day references as recoverable orphans', () => {
  const storage = memoryStorage();
  const days = buildTemporaryTripDays(trip.startDate, trip.endDate);
  writeItineraryDraft(storage, { userId, trip, itinerary: null, days,
    externalAssignments: { 'geo:overlap': days[1].id, 'geo:removed': days[5].id },
    externalOrder: ['geo:overlap', 'geo:removed'] });
  const currentDays = days.slice(0, 5).map((day, index) => ({ ...day, id: `server-day-${index}` }));
  const restored = readItineraryDraft(storage, { userId, trip, itinerary: null, currentDays });
  assert.equal(restored.status, 'restored');
  assert.equal(restored.externalAssignments['geo:overlap'], 'server-day-1');
  assert.equal(restored.externalAssignments['geo:removed'], days[5].id);
  assert.equal(restored.orphanDayCount, 1);
});

test('protects a newer server itinerary and refuses a draft from another destination', () => {
  const storage = memoryStorage();
  const days = buildTemporaryTripDays(trip.startDate, trip.endDate);
  const oldItinerary = { dias: [{ id: 1, fecha: trip.startDate, actividades: [{ id: 5, nombre: 'Old', orden: 1 }] }] };
  const newerItinerary = { dias: [{ id: 1, fecha: trip.startDate, actividades: [{ id: 6, nombre: 'New', orden: 1 }] }] };
  writeItineraryDraft(storage, { userId, trip, itinerary: oldItinerary, days, draftDays: oldItinerary.dias,
    externalAssignments: { 'geo:1': days[0].id } });
  assert.equal(readItineraryDraft(storage, { userId, trip, itinerary: newerItinerary, currentDays: days }).status, 'server-changed');
  assert.equal(readItineraryDraft(storage, { userId, trip: { ...trip, destination: 'Madrid, España' },
    itinerary: oldItinerary, currentDays: days }).status, 'destination-changed');
  assert.ok(storage.has(itineraryDraftStorageKey(userId, trip.id)));
});

test('keeps corrupt storage for explicit recovery and deletes only on explicit removal', () => {
  const storage = memoryStorage();
  const key = itineraryDraftStorageKey(userId, trip.id);
  storage.setItem(key, '{invalid');
  assert.equal(readItineraryDraft(storage, { userId, trip, itinerary: null }).status, 'corrupt');
  assert.ok(storage.has(key));
  assert.equal(removeItineraryDraft(storage, userId, trip.id), true);
  assert.equal(storage.has(key), false);
});
