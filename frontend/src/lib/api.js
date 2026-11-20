const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL;
const API_BASE_URL = (configuredApiBaseUrl || (import.meta.env.PROD ? '' : 'http://localhost:8080')).replace(/\/+$/, '');

export async function apiRequest(path, { token, ...options } = {}) {
  if (!API_BASE_URL) {
    throw new Error('El backend no está configurado. Define VITE_API_BASE_URL con la URL HTTPS pública de Render.');
  }
  const headers = new Headers(options.headers || {});
  if (options.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) headers.set('Authorization', `Bearer ${token}`);

  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers });
  const body = await response.text();
  let result = body;
  if (body && response.headers.get('content-type')?.includes('application/json')) {
    try {
      result = JSON.parse(body);
    } catch {
      result = body;
    }
  }

  if (!response.ok) {
    const baseMessage = typeof result === 'object' && result !== null
      ? result.detail || result.message || 'La solicitud no pudo completarse.'
      : result || 'La solicitud no pudo completarse.';
    const fieldMessages = typeof result === 'object' && Array.isArray(result.fields)
      ? result.fields.filter((field) => field?.field && field?.message)
        .map((field) => `${field.field}: ${field.message}`).join('; ')
      : '';
    const message = fieldMessages ? `${baseMessage} Campos: ${fieldMessages}` : baseMessage;
    const method = (options.method || 'GET').toUpperCase();
    throw new Error(`${method} ${path} (HTTP ${response.status}): ${message}`);
  }
  return result;
}
