const PREFIX = 'smarttrip:package-selection:v1';
const record = (value) => value && typeof value === 'object' && !Array.isArray(value);
const text = (value) => typeof value === 'string' ? value : '';

export function packageSelectionStorageKey(userId, tripId) {
  if (userId == null || tripId == null || !String(userId).trim() || !String(tripId).trim()) return null;
  return `${PREFIX}:${encodeURIComponent(String(userId))}:${encodeURIComponent(String(tripId))}`;
}

export function readPackageSelections(storage, userId, tripId) {
  const key = packageSelectionStorageKey(userId, tripId);
  if (!storage || !key) return { selectedFlight: null, selectedLodging: null, status: 'unavailable' };
  try {
    const raw = storage.getItem(key);
    if (!raw) return { selectedFlight: null, selectedLodging: null, status: 'empty' };
    const parsed = JSON.parse(raw);
    if (parsed?.version !== 1 || parsed.userId !== String(userId) || parsed.tripId !== String(tripId)) {
      return { selectedFlight: null, selectedLodging: null, status: 'corrupt' };
    }
    return {
      selectedFlight: normalizeFlight(parsed.selectedFlight),
      selectedLodging: normalizeLodging(parsed.selectedLodging),
      status: 'restored',
    };
  } catch {
    // Keep malformed data untouched for explicit recovery; it never blocks rendering.
    return { selectedFlight: null, selectedLodging: null, status: 'corrupt' };
  }
}

export function writePackageSelections(storage, userId, tripId, selections) {
  const key = packageSelectionStorageKey(userId, tripId);
  if (!storage || !key) return false;
  const payload = {
    version: 1,
    userId: String(userId),
    tripId: String(tripId),
    selectedFlight: normalizeFlight(selections?.selectedFlight),
    selectedLodging: normalizeLodging(selections?.selectedLodging),
  };
  try { storage.setItem(key, JSON.stringify(payload)); return true; } catch { return false; }
}

function normalizeFlight(value) {
  if (!record(value) || value.id == null || !Array.isArray(value.slices)) return null;
  return {
    id: text(String(value.id)),
    totalAmount: text(String(value.totalAmount ?? '')),
    currency: text(value.currency),
    testData: value.testData === true,
    expiresAt: text(value.expiresAt),
    slices: value.slices.map((slice) => ({
      origin: text(slice?.origin), destination: text(slice?.destination), duration: text(slice?.duration),
      departureAt: text(slice?.departureAt), arrivalAt: text(slice?.arrivalAt), stops: Number(slice?.stops) || 0,
      segments: (Array.isArray(slice?.segments) ? slice.segments : []).map((segment) => ({
        origin: text(segment?.origin), destination: text(segment?.destination),
        departureAt: text(segment?.departureAt), arrivalAt: text(segment?.arrivalAt),
        operatingCarrier: text(segment?.operatingCarrier), marketingCarrier: text(segment?.marketingCarrier),
        flightNumber: text(segment?.flightNumber),
      })),
    })),
  };
}

function normalizeLodging(value) {
  if (!record(value) || value.id == null || typeof value.nombre !== 'string' || !value.nombre.trim()) return null;
  return {
    id: text(String(value.id)), nombre: value.nombre.trim(), direccion: text(value.direccion),
    latitud: finiteOrNull(value.latitud), longitud: finiteOrNull(value.longitud),
    categoria: text(value.categoria), tipo: text(value.tipo), imagenUrl: safeHttpUrl(value.imagenUrl),
  };
}

function finiteOrNull(value) {
  if (value == null || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
}

function safeHttpUrl(value) {
  if (typeof value !== 'string') return '';
  try { const url = new URL(value); return ['http:', 'https:'].includes(url.protocol) ? url.href : ''; }
  catch { return ''; }
}
