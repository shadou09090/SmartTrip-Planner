const UNKNOWN_COST = { tipo: 'DESCONOCIDO' };

const toFiniteNumber = (value) => {
  if (value === null || value === undefined || value === '') return null;
  const number = Number(value);
  return Number.isFinite(number) ? number : null;
};

function normalizeLocalTime(value) {
  if (value == null || value === '') return null;
  const text = String(value);
  return /^\d{2}:\d{2}$/.test(text) ? `${text}:00` : text;
}

function activityKind(activity) {
  const id = String(activity?.id ?? '');
  if (id.startsWith('external:')) return { tipo: 'EXTERNA', idExterno: id.slice('external:'.length) };
  if (id.startsWith('catalog:')) {
    const catalogId = id.slice('catalog:'.length);
    if (/^\d+$/.test(catalogId)) return { tipo: 'INTERNA', actividadId: Number(catalogId) };
    return { tipo: 'MANUAL' };
  }
  if (activity?.fuente === 'Borrador manual' || id.startsWith('manual:')) return { tipo: 'MANUAL' };
  if (/^\d+$/.test(id)) return { tipo: 'INTERNA', actividadId: Number(id) };
  return { tipo: 'MANUAL' };
}

function costFor(activity) {
  const state = String(activity?.costoEstado || '').toUpperCase();
  const amount = toFiniteNumber(activity?.costo ?? activity?.importe);
  const currency = activity?.moneda || activity?.currency || null;
  if (state === 'FREE_VERIFIED' || state === 'GRATUITO_VERIFICADO' || state === 'MANUAL_FREE') {
    return { ...UNKNOWN_COST, tipo: state === 'MANUAL_FREE' ? 'GRATUITO_SUPUESTO_MANUAL' : 'GRATUITO_VERIFICADO', importe: 0, moneda: currency || null,
      fuente: activity?.fuentePrecio || activity?.fuente || null, evidencia: activity?.evidenciaPrecio || null };
  }
  const typeMap = {
    MANUAL_ESTIMATE: 'ESTIMACION_MANUAL', ESTIMACION_MANUAL: 'ESTIMACION_MANUAL',
    CONFIRMED: 'CONFIRMADO_POR_USUARIO', CONFIRMADO_POR_USUARIO: 'CONFIRMADO_POR_USUARIO',
    VERIFIED_QUOTE: 'COTIZACION_REAL', COTIZACION_REAL: 'COTIZACION_REAL',
    PUBLISHED_PRICE: 'PRECIO_PUBLICADO', PRECIO_PUBLICADO: 'PRECIO_PUBLICADO',
    SUPPORTED_ESTIMATE: 'ESTIMACION_SUSTENTADA', ESTIMACION_SUSTENTADA: 'ESTIMACION_SUSTENTADA',
    SANDBOX: 'SANDBOX',
  };
  const tipo = typeMap[state];
  if (!tipo || amount === null || !/^[A-Z]{3}$/.test(String(currency || ''))) return { ...UNKNOWN_COST };
  return { importe: amount, moneda: currency, tipo, unidad: activity.unidadCosto || null,
    cantidad: toFiniteNumber(activity.cantidadCosto), fuente: activity.fuentePrecio || activity.fuente || null,
    consultadoEn: activity.consultadoEn || null, evidencia: activity.evidenciaPrecio || null };
}

function expenseConfidence(value) {
  return ({ CONFIRMED: 'CONFIRMADO_POR_USUARIO', VERIFIED_QUOTE: 'COTIZACION_REAL',
    MANUAL_ESTIMATE: 'ESTIMACION_MANUAL', MANUAL_FREE: 'GRATUITO_SUPUESTO_MANUAL',
    VERIFIED_FREE: 'GRATUITO_VERIFICADO', SANDBOX: 'SANDBOX', UNKNOWN: 'DESCONOCIDO',
    DESCONOCIDO: 'DESCONOCIDO' })[value] || 'DESCONOCIDO';
}

const numericId = (id) => /^\d+$/.test(String(id ?? '')) ? Number(id) : null;

export function buildPackageSavePayload({ trip, days, selectedFlight, lodging, scenarioExpenses = [], pendingBudgetEntries = [], budgetTravelers,
  revisionEsperada = 0 }) {
  const dayRows = Array.isArray(days) ? days : [];
  const travelers = toFiniteNumber(budgetTravelers ?? trip?.travelers ?? trip?.numeroViajeros) || 1;
  const tripDays = dayRows.length || daysBetween(trip?.startDate, trip?.endDate) + 1 || 1;
  const tripNights = Math.max(0, daysBetween(trip?.startDate, trip?.endDate));
  const expenses = [...(Array.isArray(scenarioExpenses) ? scenarioExpenses : [])];
  const expenseIds = new Set(expenses.map((expense) => String(expense?.id || '')));
  for (const entry of Array.isArray(pendingBudgetEntries) ? pendingBudgetEntries : []) {
    if (!['Alimentación', 'Transporte local'].includes(entry?.category) || expenseIds.has(String(entry.id || ''))) continue;
    expenses.push({ id: entry.id, category: entry.category, label: entry.label, confidence: 'UNKNOWN' });
    expenseIds.add(String(entry.id || ''));
  }
  for (const [id, category] of [['food', 'Alimentación'], ['local-transport', 'Transporte local']]) {
    if (expenses.some((expense) => expense?.category === category)) continue;
    const stableId = `${id}:${trip?.id ?? 'trip'}`;
    expenses.push({ id: stableId, category, label: 'Presupuesto diario pendiente', confidence: 'UNKNOWN' });
    expenseIds.add(stableId);
  }
  return {
    revisionEsperada,
    viaje: {
      numeroViajeros: toFiniteNumber(budgetTravelers ?? trip?.travelers ?? trip?.numeroViajeros),
      monedaPresupuesto: /^[A-Z]{3}$/.test(String(trip?.budgetCurrency || trip?.monedaPresupuesto || ''))
        ? (trip.budgetCurrency || trip.monedaPresupuesto) : null,
      zonaHoraria: trip?.timezone || trip?.zonaHoraria || null,
      preferencias: trip?.preferences && typeof trip.preferences === 'object' ? trip.preferences : null,
      presupuestoTotal: toFiniteNumber(trip?.budget ?? trip?.presupuestoTotal),
    },
    dias: dayRows.map((day) => ({
      diaId: numericId(day?.id),
      fecha: String(day?.fecha || ''),
      actividades: (Array.isArray(day?.actividades) ? day.actividades : []).map((activity, index) => {
        const identity = activityKind(activity);
        return {
          claveCliente: String(activity?.claveCliente || activity?.id || `manual:${day?.fecha}:${index + 1}`).slice(0, 160),
          ...identity,
          fuente: identity.tipo === 'EXTERNA' ? String(activity?.proveedor || activity?.fuente || 'desconocida').slice(0, 40) : null,
          idExterno: identity.tipo === 'EXTERNA' ? identity.idExterno.slice(0, 500) : null,
          nombre: String(activity?.nombre || activity?.name || 'Actividad').slice(0, 150),
          descripcion: activity?.descripcion || null,
          ubicacion: activity?.ubicacion || activity?.direccion || null,
          latitud: toFiniteNumber(activity?.latitud),
          longitud: toFiniteNumber(activity?.longitud),
          orden: Number(activity?.orden) || index + 1,
          horaLocal: normalizeLocalTime(activity?.horaLocal || activity?.hora),
          duracionMinutos: toFiniteNumber(activity?.duracionMinutos),
          costo: costFor(activity),
        };
      }),
    })),
    vueloSeleccionado: selectedFlight ? serializeFlight(selectedFlight) : null,
    alojamiento: lodging ? serializeLodging(lodging) : null,
    gastos: expenses.slice(0, 500).map((expense) => ({
      claveEstable: String(expense.id || `expense:${expense.category}:${expense.label}`).slice(0, 160),
      categoria: String(expense.category || 'OTROS').slice(0, 30),
      concepto: String(expense.label || expense.category || 'Gasto de viaje').slice(0, 200),
      costo: {
        importe: toFiniteNumber(expense.unitAmount ?? expense.amount),
        moneda: /^[A-Z]{3}$/.test(String(expense.currency || '')) ? expense.currency : null,
        unidad: expense.pricingMode || 'total', cantidad: expenseQuantity(expense, travelers, tripDays, tripNights),
        tipo: expenseConfidence(expense.confidence),
        fuente: expense.source || null,
        consultadoEn: expense.queriedAt || null,
        evidencia: expense.rateSource ? `Conversión ${expense.rateToBudget} · ${expense.rateSource} · ${expense.rateDate || ''}` : null,
      },
    })),
    asignacionesEliminadas: [],
  };
}

function serializeFlight(flight) {
  const slices = Array.isArray(flight.slices) ? flight.slices : [];
  const tramos = slices.flatMap((slice, sliceIndex) => (Array.isArray(slice.segments) ? slice.segments : [])
    .map((segment) => ({ ...segment, numeroTrayecto: sliceIndex + 1 })));
  const amount = toFiniteNumber(flight.totalAmount ?? flight.importe);
  const currency = flight.currency || flight.moneda || null;
  const sandbox = flight.testData === true || /sandbox/i.test(String(flight.provider || flight.proveedor || ''));
  return {
    vueloId: numericId(flight.id), proveedor: flight.provider || flight.proveedor || null,
    idOferta: flight.id == null ? null : String(flight.id).slice(0, 500),
    moneda: /^[A-Z]{3}$/.test(String(currency || '')) ? currency : null,
    importe: amount, tipo: sandbox ? 'SANDBOX' : amount !== null && currency ? 'COTIZACION_REAL' : 'DESCONOCIDO',
    origen: iataCode(flight.origin) || flight.origen || iataCode(slices[0]?.origin) || null,
    destino: iataCode(flight.destination) || flight.destino || iataCode(slices.at(-1)?.destination) || null,
    salida: tramos[0]?.departingAt || tramos[0]?.departureAt || tramos[0]?.salida || slices[0]?.departureAt || null,
    llegada: tramos.at(-1)?.arrivingAt || tramos.at(-1)?.arrivalAt || tramos.at(-1)?.llegada || slices.at(-1)?.arrivalAt || null,
    tramos: tramos.slice(0, 20).map((segment) => ({
      origenIata: iataCode(segment.origin) || segment.originIata,
      destinoIata: iataCode(segment.destination) || segment.destinationIata,
      salida: segment.departingAt || segment.departureAt || segment.salida,
      llegada: segment.arrivingAt || segment.arrivalAt || segment.llegada,
      aerolinea: segment.marketingCarrier?.name || segment.marketingCarrier || segment.operatingCarrier?.name
        || segment.operatingCarrier || segment.airline || null,
      numeroVuelo: segment.marketingCarrierFlightNumber || segment.flightNumber || segment.numeroVuelo || null,
      numeroTrayecto: segment.numeroTrayecto,
    })).filter((segment) => segment.origenIata && segment.destinoIata && segment.salida && segment.llegada),
  };
}

function iataCode(value) {
  if (typeof value === 'string') return value;
  return value?.iataCode || value?.iata_code || null;
}

function serializeLodging(lodging) {
  return { alojamientoId: numericId(lodging.id), fuente: lodging.proveedor || lodging.fuente || null,
    idExterno: lodging.id == null ? null : String(lodging.id).slice(0, 500),
    nombre: lodging.nombre || lodging.name || null, direccion: lodging.direccion || lodging.address || null,
    latitud: toFiniteNumber(lodging.latitud), longitud: toFiniteNumber(lodging.longitud), costo: { ...UNKNOWN_COST },
    noches: null, habitaciones: null, huespedes: null };
}

export function canonicalPersistedPackage(value) {
  const pkg = value?.paquete || value;
  return stableStringify(pkg);
}

export function packageContentSignature(value) {
  const pkg = value?.paquete || value;
  if (!pkg || typeof pkg !== 'object') return '';
  const { revisionEsperada: _revisionEsperada, ...content } = pkg;
  return stableStringify(content);
}

export function packageIdempotencyKey(payload, tripId) {
  const text = canonicalPersistedPackage(payload);
  let hash = 0xcbf29ce484222325n;
  for (const byte of new TextEncoder().encode(text)) {
    hash ^= BigInt(byte);
    hash = BigInt.asUintN(64, hash * 0x100000001b3n);
  }
  return `pkg-${String(tripId).replace(/[^A-Za-z0-9._:-]/g, '').slice(0, 20)}-${payload?.revisionEsperada || 0}-${hash.toString(16).padStart(16, '0')}`.slice(0, 80);
}

export function packageSaveFailureMessage(error) {
  const message = String(error?.message || error || 'No se pudo guardar el paquete.');
  if (/failed to fetch|networkerror|load failed|err_network/i.test(message)) {
    return 'No se pudo conectar con el servidor. Puede ser una caída de red o un bloqueo CORS; revisa la consola y la preflight OPTIONS. El borrador local se conservó.';
  }
  const status = Number(message.match(/HTTP\s+(\d{3})/i)?.[1]);
  if (status === 401 || status === 403) return 'La sesión venció o no tienes permiso para editar este viaje. Inicia sesión o revisa tus permisos. El borrador local se conservó.';
  if (status === 409 && /idempotency-key.*(otro contenido|usado)/i.test(message)) {
    return 'El servidor detectó que una clave de reintento se reutilizó con otro contenido. No se asumirá que es un conflicto de revisión; conserva el borrador y vuelve a leer el paquete antes de reintentar.';
  }
  if (status === 409 && /revisi[oó]n.*(cambi[oó]|actual)|paquete.*(conflicto|cambi[oó])/i.test(message)) {
    return 'El servidor rechazó una revisión base obsoleta. El borrador local se conservó; SmartTrip debe leer la versión remota y ofrecer resolución explícita antes de otro PUT.';
  }
  if (status === 409) return 'El servidor reportó un conflicto de integridad. Se conservó el borrador; revisa la respuesta antes de reintentar.';
  if (/numeric value out of bounds|digits?\s*\(integer|precision.*decimal/i.test(message)) {
    return 'El servidor rechazó la precisión de un valor numérico del paquete. Comprueba qué campo señala el error y que Spring Boot tenga la validación actualizada; el borrador local se conservó.';
  }
  if (/vueloSeleccionado\.tramos/i.test(message)) {
    return 'El vuelo seleccionado llegó sin tramos utilizables. Vuelve a seleccionar una oferta que incluya origen, destino y horarios de cada segmento. El vuelo Sandbox seguirá identificado como simulación y el borrador local se conservó.';
  }
  if (status === 400 || status === 422) return `El servidor rechazó datos del paquete (${message}). Revisa fechas, días y actividades. El borrador local se conservó.`;
  if (status >= 500 && status <= 599) return `El servidor no pudo guardar el paquete (HTTP ${status}). Revisa los logs de Spring Boot; el borrador local se conservó.`;
  return `${message} El borrador local se conservó.`;
}

export function restorePackageExpenses(expenses, trip = {}) {
  const confidenceByType = { CONFIRMADO_POR_USUARIO: 'CONFIRMED', COTIZACION_REAL: 'VERIFIED_QUOTE',
    PRECIO_PUBLICADO: 'VERIFIED_QUOTE', ESTIMACION_SUSTENTADA: 'MANUAL_ESTIMATE',
    ESTIMACION_MANUAL: 'MANUAL_ESTIMATE', GRATUITO_VERIFICADO: 'VERIFIED_FREE',
    GRATUITO_SUPUESTO_MANUAL: 'MANUAL_FREE', SANDBOX: 'SANDBOX', DESCONOCIDO: 'UNKNOWN' };
  return (Array.isArray(expenses) ? expenses : []).filter((expense) => {
    if (expense?.costo?.tipo === 'DESCONOCIDO') return true;
    return expense?.costo?.importe != null && /^[A-Z]{3}$/.test(String(expense.costo.moneda || ''));
  }).map((expense) => {
    const cost = expense.costo || {};
    const evidence = String(cost.evidencia || '');
    const conversion = evidence.match(/^Conversión ([0-9.eE+-]+) · (.+?) · (\d{4}-\d{2}-\d{2})$/);
    const amount = toFiniteNumber(cost.importe);
    const nights = Math.max(0, daysBetween(trip?.startDate, trip?.endDate));
    const storedQuantity = toFiniteNumber(cost.cantidad);
    const rooms = cost.unidad === 'per_night' && nights > 0 && storedQuantity != null
      ? Math.max(1, Math.round(storedQuantity / nights)) : null;
    return { id: expense.claveEstable, category: expense.categoria, label: expense.concepto,
      amount, unitAmount: amount,
      currency: /^[A-Z]{3}$/.test(String(cost.moneda || '')) ? cost.moneda : null,
      confidence: confidenceByType[cost.tipo] || (amount === null ? 'UNKNOWN' : 'MANUAL_ESTIMATE'),
      pricingMode: ['total', 'per_person', 'daily_person', 'per_night'].includes(cost.unidad)
        ? cost.unidad : 'total',
      travelers: toFiniteNumber(trip?.travelers ?? trip?.numeroViajeros) || 1,
      ...(nights ? { nights } : {}), ...(rooms ? { rooms } : {}),
      source: cost.fuente || null, queriedAt: cost.consultadoEn || null,
      ...(conversion ? { rateToBudget: Number(conversion[1]), rateSource: conversion[2], rateDate: conversion[3] } : {}),
      availability: 'unknown',
    };
  });
}

export function classifyPackageSnapshot({ baselineSignature = '', localPayload, remotePackage }) {
  const localSignature = packageContentSignature(localPayload);
  if (!remotePackage) return baselineSignature ? 'remote-removed' : 'create';
  const remoteSignature = packageContentSignature(remotePackage);
  if (localSignature && localSignature === remoteSignature) return 'already-saved';
  if (baselineSignature && baselineSignature === remoteSignature) return 'safe-to-save';
  return 'conflict';
}

function expenseQuantity(expense, travelers, days, nights) {
  const configuredTravelers = Math.max(1, toFiniteNumber(expense.travelers) || travelers);
  switch (expense.pricingMode) {
    case 'per_person': return configuredTravelers;
    case 'daily_person': return configuredTravelers * days;
    case 'per_night': return Math.max(1, toFiniteNumber(expense.rooms) || 1) * (toFiniteNumber(expense.nights) ?? nights);
    default: return toFiniteNumber(expense.quantity) ?? 1;
  }
}

function daysBetween(startDate, endDate) {
  if (!startDate || !endDate) return 0;
  const start = Date.parse(`${startDate}T00:00:00Z`);
  const end = Date.parse(`${endDate}T00:00:00Z`);
  return Number.isFinite(start) && Number.isFinite(end) ? Math.round((end - start) / 86400000) : 0;
}

export function countPackageActivities(value) {
  const pkg = value?.paquete || value;
  return (Array.isArray(pkg?.dias) ? pkg.dias : []).reduce((total, day) => total
    + (Array.isArray(day?.actividades) ? day.actividades.length : 0), 0);
}

function stableStringify(value) {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).filter((key) => value[key] !== undefined && value[key] !== null).sort()
      .map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}
