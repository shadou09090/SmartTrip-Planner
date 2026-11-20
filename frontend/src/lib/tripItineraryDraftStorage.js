const VERSION = 1;
const PREFIX = 'smarttrip:itinerary-draft:v1';
const isRecord = (value) => Boolean(value) && typeof value === 'object' && !Array.isArray(value);

function normalize(value) {
  return String(value ?? '').trim().toLocaleLowerCase();
}

export function itinerarySignature(itinerary) {
  const days = Array.isArray(itinerary?.dias) ? itinerary.dias : [];
  return JSON.stringify(days.map((day) => [String(day.id), String(day.fecha || ''),
    (Array.isArray(day.actividades) ? day.actividades : [])
      .map((activity) => [String(activity.id), Number(activity.orden) || 0, String(activity.nombre || ''),
        String(activity.ubicacion || ''), activity.latitud ?? null, activity.longitud ?? null])
      .sort(([firstId], [secondId]) => firstId.localeCompare(secondId)),
  ]));
}

export function itineraryDraftStorageKey(userId, tripId) {
  if (userId == null || String(userId).trim() === '' || tripId == null || String(tripId).trim() === '') return null;
  return `${PREFIX}:${encodeURIComponent(String(userId))}:${encodeURIComponent(String(tripId))}`;
}

export function writeItineraryDraft(storage, { userId, trip, itinerary, days, draftDays = days, externalAssignments,
  externalOrder, catalogAssignments, manual }) {
  const key = itineraryDraftStorageKey(userId, trip?.id);
  if (!storage || !key) return false;
  const payload = {
    version: VERSION,
    userId: String(userId),
    tripId: String(trip.id),
    context: { destination: normalize(trip.destination), startDate: String(trip.startDate || ''), endDate: String(trip.endDate || '') },
    baseItinerarySignature: itinerarySignature(itinerary),
    dayDates: (Array.isArray(days) ? days : []).map((day) => ({ id: String(day.id), date: String(day.fecha || '') })),
    updatedAt: new Date().toISOString(),
    draftDays: (Array.isArray(draftDays) ? draftDays : []).map((day) => ({ ...day, actividades: [...(day.actividades || [])] })),
    externalAssignments: externalAssignments && typeof externalAssignments === 'object' ? externalAssignments : {},
    externalOrder: Array.isArray(externalOrder) ? externalOrder : [],
    catalogAssignments: catalogAssignments && typeof catalogAssignments === 'object' ? catalogAssignments : {},
    manual: Array.isArray(manual) ? manual : [],
  };
  try {
    storage.setItem(key, JSON.stringify(payload));
    return true;
  } catch {
    return false;
  }
}

export function readItineraryDraft(storage, { userId, trip, itinerary, currentDays = [] }) {
  const key = itineraryDraftStorageKey(userId, trip?.id);
  if (!storage || !key) return { status: 'unavailable', key };
  let raw;
  try { raw = storage.getItem(key); } catch { return { status: 'unavailable', key }; }
  if (!raw) return { status: 'none', key };
  let saved;
  try { saved = JSON.parse(raw); } catch { return { status: 'corrupt', key }; }
  if (!saved || saved.version !== VERSION || saved.userId !== String(userId) || saved.tripId !== String(trip.id)
      || !isRecord(saved.context) || !Array.isArray(saved.dayDates) || !Array.isArray(saved.draftDays)
      || !Array.isArray(saved.externalOrder) || !Array.isArray(saved.manual)
      || !isRecord(saved.externalAssignments) || !isRecord(saved.catalogAssignments)) {
    return { status: 'corrupt', key };
  }
  if (saved.context.destination !== normalize(trip.destination)) return { status: 'destination-changed', key, saved };
  if (saved.baseItinerarySignature !== itinerarySignature(itinerary)) return { status: 'server-changed', key, saved };
  const changedDates = saved.context.startDate !== String(trip.startDate || '')
    || saved.context.endDate !== String(trip.endDate || '');
  // Preserve the old version for explicit recovery/discard; do not apply assignments
  // against days from another date range.
  if (changedDates) return { status: 'dates-changed', key, saved };

  const dateByOldId = new Map((Array.isArray(saved.dayDates) ? saved.dayDates : [])
    .map((day) => [String(day.id), String(day.date || '')]));
  const currentIdByDate = new Map((Array.isArray(currentDays) ? currentDays : [])
    .map((day) => [String(day.fecha || ''), day.id]));
  const remapDay = (id) => {
    const value = String(id);
    const date = dateByOldId.get(value) || (value.startsWith('temporary:') ? value.slice('temporary:'.length) : '');
    return date && currentIdByDate.has(date) ? currentIdByDate.get(date) : id;
  };
  const externalAssignments = Object.fromEntries(Object.entries(saved.externalAssignments)
    .map(([id, dayId]) => [id, remapDay(dayId)]));
  const catalogAssignments = Object.fromEntries(Object.entries(saved.catalogAssignments)
    .map(([id, dayId]) => [id, remapDay(dayId)]));
  const manual = saved.manual.map((item) => ({ ...item, dayId: remapDay(item.dayId) }));

  return {
    status: 'restored', key, savedAt: saved.updatedAt,
    // The exact destination/date context and server baseline were checked above, so
    // this retains local ordering/edits without replacing a newer persisted version.
    draftDays: saved.draftDays,
    externalAssignments,
    externalOrder: saved.externalOrder,
    catalogAssignments,
    manual,
    orphanDayCount: [...Object.values(externalAssignments), ...Object.values(catalogAssignments), ...manual.map((item) => item.dayId)]
      .filter((id) => !(Array.isArray(currentDays) ? currentDays : []).some((day) => String(day.id) === String(id))).length,
  };
}

export function removeItineraryDraft(storage, userId, tripId) {
  const key = itineraryDraftStorageKey(userId, tripId);
  if (!storage || !key) return false;
  try { storage.removeItem(key); return true; } catch { return false; }
}
