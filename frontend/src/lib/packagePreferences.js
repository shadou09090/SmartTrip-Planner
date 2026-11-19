const PREFIX = 'smarttrip.package-preferences.v1';

export function packagePreferencesKey(userId, tripId) {
  if (userId == null || tripId == null) return null;
  return `${PREFIX}:${encodeURIComponent(String(userId))}:${encodeURIComponent(String(tripId))}`;
}

export function savePackagePreferences(storage, userId, tripId, preferences) {
  const key = packagePreferencesKey(userId, tripId);
  if (!key || !storage?.setItem) return false;
  const safe = normalizePreferences(preferences);
  try {
    storage.setItem(key, JSON.stringify({ version: 1, ...safe }));
    return true;
  } catch { return false; }
}

export function loadPackagePreferences(storage, userId, tripId) {
  const key = packagePreferencesKey(userId, tripId);
  if (!key || !storage?.getItem) return null;
  try {
    const stored = storage.getItem(key);
    if (!stored) return null;
    const value = JSON.parse(stored);
    return value?.version === 1 ? normalizePreferences(value) : null;
  } catch { return null; }
}

function normalizePreferences(value = {}) {
  const currency = String(value.budgetCurrency || 'COP').trim().toUpperCase();
  const travelers = Number(value.travelers);
  const cabin = ['economy', 'premium_economy', 'business', 'first'].includes(value.flightCabinClass)
    ? value.flightCabinClass : 'economy';
  const hotel = ['all', 'hotel', 'hostel', 'apartment', 'guest_house'].includes(value.lodgingPreference)
    ? value.lodgingPreference : 'all';
  return {
    budgetCurrency: /^[A-Z]{3}$/.test(currency) ? currency : 'COP',
    travelers: Number.isInteger(travelers) ? Math.min(20, Math.max(1, travelers)) : 1,
    flightCabinClass: cabin,
    lodgingPreference: hotel,
  };
}
