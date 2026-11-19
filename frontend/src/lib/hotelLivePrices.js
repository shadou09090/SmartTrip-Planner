const asCurrency = (value) => /^[A-Z]{3}$/.test(String(value || '').toUpperCase()) ? String(value).toUpperCase() : null;
const positiveAmount = (value) => {
  if (typeof value === 'number' && Number.isFinite(value) && value >= 0) return value;
  if (typeof value === 'string' && value.trim() && Number.isFinite(Number(value)) && Number(value) >= 0) return Number(value);
  return null;
};

function objectAmount(value) {
  if (value && typeof value === 'object') return {
    amount: positiveAmount(value.amount ?? value.value ?? value.total),
    currency: asCurrency(value.currency ?? value.currency_code),
  };
  return { amount: positiveAmount(value), currency: null };
}

function safeBookingUrl(value) {
  try {
    const url = new URL(value);
    return url.protocol === 'https:' && url.hostname === 'onebookingonline.com' ? url.toString() : null;
  } catch { return null; }
}

function findRows(value, depth = 0) {
  if (!value || depth > 7) return [];
  if (Array.isArray(value)) {
    const hotels = value.filter((row) => row && typeof row === 'object' && typeof (row.hotel_name || row.name || row.hotelName) === 'string');
    if (hotels.length) return hotels;
    for (const row of value) { const nested = findRows(row, depth + 1); if (nested.length) return nested; }
    return [];
  }
  if (typeof value === 'object') {
    for (const key of ['hotels', 'results', 'offers', 'properties', 'items', 'data']) {
      if (key in value) { const nested = findRows(value[key], depth + 1); if (nested.length) return nested; }
    }
    for (const nestedValue of Object.values(value)) { const nested = findRows(nestedValue, depth + 1); if (nested.length) return nested; }
  }
  return [];
}

export function normalizeHotelLivePrices(response) {
  const raw = response?.results ?? response;
  const rows = findRows(raw);
  return rows.map((row) => {
    const total = objectAmount(row.total_price ?? row.totalPrice ?? row.total ?? row.sell_total ?? row.price_total);
    const nightly = objectAmount(row.per_night ?? row.nightly_price ?? row.price_per_night ?? row.perNight);
    const currency = asCurrency(row.currency ?? row.currency_code) || total.currency || nightly.currency || response?.currency || null;
    const quotedAt = row.freshness?.quoted_at ?? row.quoted_at ?? row.quotedAt ?? response?.retrievedAt ?? null;
    const bookingUrl = safeBookingUrl(row.booking_url ?? row.bookingUrl);
    return {
      id: String(row.hotel_token ?? row.id ?? `${row.hotel_name || row.name}-${row.address || ''}`),
      name: String(row.hotel_name ?? row.hotelName ?? row.name),
      address: String(row.address ?? row.location ?? row.neighborhood ?? ''),
      total: total.amount,
      nightly: nightly.amount,
      currency,
      taxesIncluded: row.taxes_included ?? row.taxesIncluded ?? null,
      feesIncluded: row.fees_included ?? row.feesIncluded ?? null,
      extraCharges: Array.isArray(row.extra_charges) ? row.extra_charges : [],
      distance: positiveAmount(row.distance_km ?? row.distanceKm),
      photoUrl: typeof row.photo_url === 'string' && row.photo_url.startsWith('https://') ? row.photo_url : null,
      bookingUrl,
      quotedAt,
      ageSeconds: positiveAmount(row.freshness?.age_seconds ?? row.age_seconds),
      availability: row.availability ?? null,
      rateLabel: row.rate_label ?? row.room_name ?? row.roomName ?? null,
    };
  }).filter((offer) => offer.name && offer.total != null && offer.currency);
}

export function isHotelQuoteFresh(retrievedAt, now = Date.now()) {
  const timestamp = Date.parse(retrievedAt || '');
  return Number.isFinite(timestamp) && now - timestamp >= 0 && now - timestamp < 30 * 60 * 1000;
}

export function freshHotelLivePrices(response, now = Date.now()) {
  return normalizeHotelLivePrices(response).filter((offer) =>
    isHotelQuoteFresh(offer.quotedAt || response?.retrievedAt, now));
}

export function formatHotelPrice(amount, currency, locale = 'es-CO') {
  try { return new Intl.NumberFormat(locale, { style: 'currency', currency }).format(amount); }
  catch { return `${Number(amount).toLocaleString(locale)} ${currency || ''}`.trim(); }
}
