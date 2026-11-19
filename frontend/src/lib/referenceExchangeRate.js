const RATE_API = 'https://api.frankfurter.dev/v2/rate';
const CACHE_TTL_MS = 24 * 60 * 60 * 1000;
const cache = new Map();

/** Fetches a daily reference rate (never a transaction quote); no API key is used. */
export async function fetchReferenceExchangeRate(base, quote, {
  fetchImpl = globalThis.fetch,
  now = Date.now(),
} = {}) {
  const source = String(base || '').toUpperCase();
  const target = String(quote || '').toUpperCase();
  if (!/^[A-Z]{3}$/.test(source) || !/^[A-Z]{3}$/.test(target)) {
    throw new Error('Selecciona códigos de moneda ISO válidos para consultar la tasa.');
  }
  if (source === target) return { base: source, quote: target, rate: 1, date: null, provider: 'SmartTrip' };
  if (typeof fetchImpl !== 'function') throw new Error('Este navegador no permite consultar tasas de referencia.');

  const key = `${source}:${target}`;
  const cached = cache.get(key);
  if (cached && now - cached.fetchedAt < CACHE_TTL_MS) return cached.value;

  const controller = typeof AbortController === 'function' ? new AbortController() : null;
  const timer = controller ? setTimeout(() => controller.abort(), 5000) : null;
  try {
    const response = await fetchImpl(`${RATE_API}/${source.toLowerCase()}/${target.toLowerCase()}`, {
      headers: { Accept: 'application/json' },
      ...(controller ? { signal: controller.signal } : {}),
    });
    if (!response?.ok) throw new Error(`La fuente de tasas respondió HTTP ${response?.status || 'sin estado'}.`);
    const data = await response.json();
    const rate = Number(data?.rate);
    if (!Number.isFinite(rate) || rate <= 0 || data?.base?.toUpperCase() !== source
      || data?.quote?.toUpperCase() !== target || !/^\d{4}-\d{2}-\d{2}$/.test(data?.date || '')) {
      throw new Error('La fuente devolvió una tasa de referencia incompleta o inválida.');
    }
    const value = { base: source, quote: target, rate, date: data.date, provider: 'Frankfurter · tasa de referencia diaria' };
    cache.set(key, { value, fetchedAt: now });
    if (cache.size > 250) cache.clear();
    return value;
  } catch (error) {
    if (error?.name === 'AbortError') throw new Error('La consulta de tasa de referencia superó el tiempo límite.');
    throw error;
  } finally {
    if (timer) clearTimeout(timer);
  }
}

export function clearReferenceExchangeRateCache() { cache.clear(); }
