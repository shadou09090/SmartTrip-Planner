/**
 * Sanitizes technical API and network errors into clean, user-friendly Spanish messages.
 * Strips raw endpoints (GET /api/..., HTTP 500, references, etc.).
 */
export function formatFriendlyError(errorOrMessage, defaultMessage = 'No fue posible completar la operación en este momento. Inténtalo de nuevo más tarde.') {
  if (!errorOrMessage) return '';
  const raw = typeof errorOrMessage === 'string'
    ? errorOrMessage
    : errorOrMessage?.userMessage || errorOrMessage?.message || '';

  if (!raw.trim()) return '';

  // Network or connection errors
  if (/failed to fetch|networkerror|load failed|err_network|socket|econnrefused/i.test(raw)) {
    return 'No se pudo conectar con el servidor. Revisa tu conexión a internet o asegúrate de que el backend esté en ejecución.';
  }

  // Session expired or authentication errors
  if (/HTTP\s*(401|403)|unauthorized|forbidden|jwt|token/i.test(raw) && /venci|expir|inv[aá]lid/i.test(raw)) {
    return 'Tu sesión ha expirado o no tienes permisos suficientes. Por favor, inicia sesión nuevamente.';
  }

  // Remove technical prefixes like:
  // "GET /api/viajes/1/alojamiento (HTTP 500): "
  // "POST /api/vuelos (HTTP 400): "
  let cleaned = raw
    .replace(/(?:GET|POST|PUT|DELETE|PATCH)\s+\/api\/[^\s:]+(?:\s*\(HTTP\s*\d+\))?:?\s*/gi, '')
    .replace(/\(HTTP\s*\d+\):?\s*/gi, '')
    .replace(/Referencia:\s*[a-f0-9-]+/gi, '')
    .replace(/Error:\s*/gi, '')
    .trim();

  // Clean trailing punctuation / bullets
  cleaned = cleaned.replace(/^[\s·:,-]+|[\s·:,-]+$/g, '').trim();

  // If the error was just a generic server failure or reference ID
  if (!cleaned || /^(la solicitud no pudo completarse|internal server error|an error occurred)\.?$/i.test(cleaned)) {
    return defaultMessage;
  }

  return cleaned;
}
