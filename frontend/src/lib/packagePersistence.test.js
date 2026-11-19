import test from 'node:test';
import assert from 'node:assert/strict';
import { buildPackageSavePayload, canonicalPersistedPackage, classifyPackageSnapshot, packageDifferencePaths, packageIdempotencyKey, packageSaveFailureMessage, restorePackageExpenses } from './packagePersistence.js';
import { budgetEditorConfidence, calculateGlobalBudget, resolveBudgetExpenseAmount } from './globalBudget.js';

test('serializes temporary six-day drafts without inventing costs or server day IDs', () => {
  const days = Array.from({ length: 6 }, (_, index) => ({ id: `temporary:2026-11-${20 + index}`,
    fecha: `2026-11-${20 + index}`, actividades: [{ id: `external:geoapify:long-${index}`,
      nombre: `Lugar ${index}`, fuente: 'Geoapify', costo: null, orden: 1 }] }));
  const payload = buildPackageSavePayload({ trip: { travelers: 1 }, days, revisionEsperada: 4 });
  assert.equal(payload.revisionEsperada, 4);
  assert.equal(payload.dias.length, 6);
  assert.equal(payload.dias[0].diaId, null);
  assert.equal(payload.dias[0].actividades[0].tipo, 'EXTERNA');
  assert.equal(payload.dias[0].actividades[0].idExterno, 'geoapify:long-0');
  assert.deepEqual(payload.dias[0].actividades[0].costo, { tipo: 'DESCONOCIDO' });
});

test('serializes manual and catalog activities and distinguishes Sandbox flight from hotel price', () => {
  const payload = buildPackageSavePayload({
    trip: { travelers: 2, budgetCurrency: 'COP' },
    days: [{ id: 101, fecha: '2026-11-20', actividades: [
      { id: 'catalog:12', nombre: 'Catálogo', orden: 1, costo: 9, moneda: 'EUR' },
      { id: 'manual:one', fuente: 'Borrador manual', nombre: 'Manual', orden: 2 },
    ] }],
    selectedFlight: { id: 'offer-1', provider: 'DuffelSandbox', testData: true, totalAmount: 100, currency: 'EUR', slices: [] },
    lodging: { id: 'geo:hotel-1', nombre: 'Hotel', direccion: 'Calle', latitud: 41.3, longitud: 2.1 },
  });
  assert.equal(payload.dias[0].actividades[0].tipo, 'INTERNA');
  assert.equal(payload.dias[0].actividades[0].actividadId, 12);
  assert.deepEqual(payload.dias[0].actividades[0].costo, { tipo: 'DESCONOCIDO' });
  assert.equal(payload.dias[0].actividades[1].tipo, 'MANUAL');
  assert.equal(payload.vueloSeleccionado.tipo, 'SANDBOX');
  assert.equal(payload.alojamiento.costo.tipo, 'DESCONOCIDO');
});

test('serializes Duffel API DTO segments using its origin, destination and departureAt fields', () => {
  const payload = buildPackageSavePayload({ trip: { travelers: 1 }, days: [], selectedFlight: {
    id: 'offer-sandbox', provider: 'DuffelSandbox', testData: true, totalAmount: 3216, currency: 'EUR',
    slices: [{ origin: 'BOG', destination: 'BCN', departureAt: '2026-11-24T18:00:00Z',
      arrivalAt: '2026-11-25T10:00:00Z', segments: [{ origin: 'BOG', destination: 'BCN',
        departureAt: '2026-11-24T18:00:00Z', arrivalAt: '2026-11-25T10:00:00Z',
        operatingCarrier: 'Sandbox Airways', marketingCarrier: 'Sandbox Airways', flightNumber: 'SB321' }] }],
  } });

  assert.equal(payload.vueloSeleccionado.tipo, 'SANDBOX');
  assert.equal(payload.vueloSeleccionado.origen, 'BOG');
  assert.equal(payload.vueloSeleccionado.destino, 'BCN');
  assert.equal(payload.vueloSeleccionado.tramos.length, 1);
  assert.deepEqual(payload.vueloSeleccionado.tramos[0], {
    origenIata: 'BOG', destinoIata: 'BCN', salida: '2026-11-24T18:00:00Z',
    llegada: '2026-11-25T10:00:00Z', aerolinea: 'Sandbox Airways', numeroVuelo: 'SB321', numeroTrayecto: 1,
  });
});

test('extracts verified IATA codes from airport display labels in selected Duffel offers', () => {
  const payload = buildPackageSavePayload({ trip: { travelers: 1 }, days: [], selectedFlight: {
    id: 'offer-sandbox-labels', provider: 'DuffelSandbox', testData: true, totalAmount: 578.17, currency: 'USD',
    slices: [{ origin: 'El Dorado International Airport (BOG)', destination: 'Adolfo Suárez Madrid-Barajas Airport (MAD)',
      departureAt: '2026-12-05T03:05:00Z', arrivalAt: '2026-12-05T20:22:00Z', segments: [{
        origin: 'El Dorado International Airport (BOG)', destination: 'Adolfo Suárez Madrid-Barajas Airport (MAD)',
        departureAt: '2026-12-05T03:05:00Z', arrivalAt: '2026-12-05T20:22:00Z',
        operatingCarrier: 'American Airlines', flightNumber: '107',
      }] }],
  } });

  assert.equal(payload.vueloSeleccionado.tipo, 'SANDBOX');
  assert.equal(payload.vueloSeleccionado.tramos.length, 1);
  assert.equal(payload.vueloSeleccionado.tramos[0].origenIata, 'BOG');
  assert.equal(payload.vueloSeleccionado.tramos[0].destinoIata, 'MAD');
  assert.equal(payload.vueloSeleccionado.importe, 578.17);
  assert.equal(payload.vueloSeleccionado.moneda, 'USD');
});

test('package payload retains a six-day draft with twenty activities and its pending budget scenario', () => {
  const days = Array.from({ length: 6 }, (_, dayIndex) => ({ id: 180 + dayIndex,
    fecha: `2026-11-${String(25 + dayIndex).padStart(2, '0')}`,
    actividades: Array.from({ length: dayIndex < 2 ? 4 : 3 }, (_, activityIndex) => ({
      id: `external:geoapify:barcelona-${dayIndex}-${activityIndex}`, nombre: `Place ${dayIndex}-${activityIndex}`,
      fuente: 'Geoapify', orden: activityIndex + 1,
    })) }));
  const payload = buildPackageSavePayload({ trip: { id: 341, travelers: 1, budget: 20000000, budgetCurrency: 'COP' },
    days, budgetTravelers: 1, scenarioExpenses: [{ id: 'food:341', category: 'Alimentación', label: 'Comidas',
      amount: 450000, currency: 'COP', confidence: 'MANUAL_ESTIMATE' }] });

  assert.equal(payload.dias.length, 6);
  assert.equal(payload.dias.reduce((count, day) => count + day.actividades.length, 0), 20);
  assert.equal(payload.viaje.presupuestoTotal, 20000000);
  assert.equal(payload.viaje.numeroViajeros, 1);
  assert.equal(payload.gastos[0].categoria, 'Alimentación');
  assert.equal(payload.gastos[0].costo.tipo, 'ESTIMACION_MANUAL');
  assert.equal(payload.dias.flatMap((day) => day.actividades).filter((activity) => activity.costo.tipo === 'DESCONOCIDO').length, 20);
});

test('persists the trip budget and local expense evidence with its currency and confidence class', () => {
  const payload = buildPackageSavePayload({ trip: { travelers: 2, budget: 9000000, budgetCurrency: 'COP' }, days: [],
    budgetTravelers: 3,
    scenarioExpenses: [{ id: 'food:trip:1', category: 'Alimentación', label: 'Comidas', amount: 250, currency: 'EUR',
      confidence: 'MANUAL_ESTIMATE', source: 'Usuario', queriedAt: '2026-10-01T12:00:00.000Z',
      pricingMode: 'daily_person', rateToBudget: 4500, rateSource: 'Frankfurter', rateDate: '2026-09-30' }] });
  assert.equal(payload.viaje.presupuestoTotal, 9000000);
  assert.equal(payload.viaje.numeroViajeros, 3);
  assert.equal(payload.gastos[0].costo.importe, 250);
  assert.equal(payload.gastos[0].costo.moneda, 'EUR');
  assert.equal(payload.gastos[0].costo.tipo, 'ESTIMACION_MANUAL');
  assert.match(payload.gastos[0].costo.evidencia, /Frankfurter/);
});

test('persists unknown budget entries as pending instead of invalid zero or manual estimates', () => {
  const payload = buildPackageSavePayload({ trip: { travelers: 1, budget: 20000000, budgetCurrency: 'COP' }, days: [],
    scenarioExpenses: [{ id: 'hotel:341', category: 'Alojamiento', label: 'Tarifa pendiente',
      confidence: 'UNKNOWN' }] });
  assert.equal(payload.gastos[0].costo.tipo, 'DESCONOCIDO');
  assert.equal(payload.gastos[0].costo.importe, null);
  assert.equal(payload.gastos[0].costo.moneda, null);
});

test('unknown legacy budget entries discard stale amounts and currencies before package save', () => {
  const payload = buildPackageSavePayload({ trip: { id: 425, travelers: 1, budget: 3000000, budgetCurrency: 'COP' }, days: [],
    scenarioExpenses: [
      { id: 'food:425', category: 'Alimentación', label: 'Presupuesto diario', confidence: 'UNKNOWN',
        amount: 120000, unitAmount: 30000, currency: 'COP', pricingMode: 'daily_person' },
      { id: 'activity:425', category: 'Excursiones', label: 'Costo conocido', confidence: 'MANUAL_ESTIMATE',
        amount: 45000, unitAmount: 45000, currency: 'COP' },
    ] });
  const expense = payload.gastos.find((item) => item.claveEstable === 'food:425');
  assert.equal(expense.costo.tipo, 'DESCONOCIDO');
  assert.equal(expense.costo.importe, null);
  assert.equal(expense.costo.moneda, null);
  const known = payload.gastos.find((item) => item.claveEstable === 'activity:425');
  assert.equal(known.costo.tipo, 'ESTIMACION_MANUAL');
  assert.equal(known.costo.importe, 45000);
  assert.equal(known.costo.moneda, 'COP');
});

test('persists budget unit amount and calculation quantity instead of relabeling a total as a unit price', () => {
  const payload = buildPackageSavePayload({ trip: { travelers: 2, startDate: '2026-11-20', endDate: '2026-11-25',
    budget: 20000000, budgetCurrency: 'COP' }, days: Array.from({ length: 6 }, (_, index) => ({ fecha: `2026-11-${20 + index}`, actividades: [] })),
    scenarioExpenses: [{ id: 'food:341', category: 'Alimentación', label: 'Comidas', amount: 1200,
      unitAmount: 100, currency: 'COP', confidence: 'MANUAL_ESTIMATE', pricingMode: 'daily_person' }] });
  assert.equal(payload.gastos[0].costo.importe, 100);
  assert.equal(payload.gastos[0].costo.unidad, 'daily_person');
  assert.equal(payload.gastos[0].costo.cantidad, 12);
  const restored = restorePackageExpenses(payload.gastos, { startDate: '2026-11-20', endDate: '2026-11-25', travelers: 2 });
  assert.equal(restored[0].unitAmount, 100);
  assert.equal(restored[0].pricingMode, 'daily_person');
});

test('unknown food row becomes an explicit COP 30000 daily estimate and round-trips as COP 120000', () => {
  const trip = { id: 425, travelers: 1, startDate: '2027-02-02', endDate: '2027-02-05',
    budget: 3000000, budgetCurrency: 'COP' };
  const previous = restorePackageExpenses([{ claveEstable: 'food:425', categoria: 'Alimentación',
    concepto: 'Alimentación · 1 viajero(s) × 4 día(s)', costo: { tipo: 'DESCONOCIDO', importe: null, moneda: null } }], trip)[0];
  assert.equal(previous.confidence, 'UNKNOWN');
  const edited = { ...previous, amount: 120000, unitAmount: 30000, currency: 'COP',
    confidence: budgetEditorConfidence(previous.confidence), pricingMode: 'daily_person', travelers: 1,
    label: 'Alimentación · 1 viajero(s) × 4 día(s)', source: 'Estimación manual del usuario' };
  const payload = buildPackageSavePayload({ trip, days: Array.from({ length: 4 }, (_, index) => ({
    fecha: `2027-02-0${index + 2}`, actividades: [],
  })), scenarioExpenses: [edited] });
  const serialized = payload.gastos.find((expense) => expense.claveEstable === 'food:425');
  assert.deepEqual({ importe: serialized.costo.importe, moneda: serialized.costo.moneda,
    unidad: serialized.costo.unidad, cantidad: serialized.costo.cantidad, tipo: serialized.costo.tipo }, {
    importe: 30000, moneda: 'COP', unidad: 'daily_person', cantidad: 4, tipo: 'ESTIMACION_MANUAL',
  });
  const recovered = restorePackageExpenses(payload.gastos, trip);
  const food = recovered.find((expense) => expense.id === 'food:425');
  assert.equal(food.confidence, 'MANUAL_ESTIMATE');
  assert.equal(food.unitAmount, 30000);
  assert.equal(food.currency, 'COP');
  assert.equal(food.pricingMode, 'daily_person');
  const amount = resolveBudgetExpenseAmount(food, 1, 4, 0);
  assert.equal(amount, 120000);
  const budget = calculateGlobalBudget({ total: trip.budget, currency: 'COP', entries: recovered.map((expense) => ({
    ...expense, amount: resolveBudgetExpenseAmount(expense, 1, 4, 0),
  })) });
  assert.equal(budget.estimated, 120000);
  assert.equal(budget.unknown, 1); // The untouched pending local-transport row stays unknown.
  assert.equal(payload.gastos.find((expense) => expense.categoria === 'Transporte local').costo.tipo, 'DESCONOCIDO');
});

test('package comparison identifies a manual estimate that was incorrectly persisted as unknown', () => {
  const trip = { id: 425, travelers: 1, budget: 3000000, budgetCurrency: 'COP' };
  const estimated = buildPackageSavePayload({ trip, days: [], scenarioExpenses: [{ id: 'food:425',
    category: 'Alimentación', label: 'Alimentación · 1 viajero(s) × 4 día(s)', amount: 120000,
    unitAmount: 30000, currency: 'COP', confidence: 'MANUAL_ESTIMATE', pricingMode: 'daily_person', travelers: 1 }] });
  const unknown = structuredClone(estimated);
  unknown.gastos[0].costo = { tipo: 'DESCONOCIDO' };
  const paths = packageDifferencePaths(estimated, unknown);
  assert.ok(paths.includes('gastos[0].costo.importe'));
  assert.ok(paths.includes('gastos[0].costo.moneda'));
  assert.ok(paths.includes('gastos[0].costo.tipo'));
});

test('records default food and local transport as pending costs without fabricated amounts', () => {
  const payload = buildPackageSavePayload({ trip: { travelers: 1 }, days: [], pendingBudgetEntries: [
    { id: 'food:341', category: 'Alimentación', label: 'Presupuesto diario pendiente', confidence: 'UNKNOWN' },
    { id: 'local-transport:341', category: 'Transporte local', label: 'Presupuesto diario pendiente', confidence: 'UNKNOWN' },
  ] });
  assert.deepEqual(payload.gastos.map((expense) => [expense.claveEstable, expense.costo.tipo, expense.costo.importe]), [
    ['food:341', 'DESCONOCIDO', null], ['local-transport:341', 'DESCONOCIDO', null],
  ]);
});

test('does not fabricate Geoapify as the source of a manually saved lodging', () => {
  const payload = buildPackageSavePayload({ trip: { travelers: 1 }, days: [],
    lodging: { id: 15, nombre: 'Apartamentos en Sol', direccion: 'Madrid' } });
  assert.equal(payload.alojamiento.alojamientoId, 15);
  assert.equal(payload.alojamiento.nombre, 'Apartamentos en Sol');
  assert.equal(payload.alojamiento.fuente, null);
  assert.equal(payload.alojamiento.costo.tipo, 'DESCONOCIDO');
});

test('classifies remote package revisions to distinguish a lost response from a true concurrent edit', () => {
  const base = { dias: [], gastos: [], vueloSeleccionado: null, alojamiento: null };
  const pending = { ...base, dias: [{ fecha: '2026-12-05', actividades: [{ claveCliente: 'a', orden: 1 }] }] };
  const remoteBase = { revision: 3, paquete: base };
  const remotePending = { revision: 4, paquete: pending };
  const remoteOther = { revision: 4, paquete: { ...base, alojamiento: { nombre: 'Hotel externo' } } };
  assert.equal(classifyPackageSnapshot({ baselineSignature: '', localPayload: base, remotePackage: null }), 'create');
  assert.equal(classifyPackageSnapshot({ baselineSignature: 'old', localPayload: pending, remotePackage: null }), 'remote-removed');
  assert.equal(classifyPackageSnapshot({ baselineSignature: 'old', localPayload: pending, remotePackage: remoteBase }), 'conflict');
  assert.equal(classifyPackageSnapshot({ baselineSignature: '', localPayload: pending, remotePackage: remotePending }), 'already-saved');
  assert.equal(classifyPackageSnapshot({ baselineSignature: canonicalPersistedPackage(base), localPayload: pending,
    remotePackage: remoteBase }), 'safe-to-save');
  assert.equal(classifyPackageSnapshot({ baselineSignature: canonicalPersistedPackage(base), localPayload: pending,
    remotePackage: remoteOther }), 'conflict');
});

test('restores unknown package expenses without turning them into zero-valued estimates', () => {
  const restored = restorePackageExpenses([{ claveEstable: 'hotel:341', categoria: 'Alojamiento',
    concepto: 'Tarifa pendiente', costo: { tipo: 'DESCONOCIDO', importe: null, moneda: null } }]);
  assert.equal(restored.length, 1);
  assert.equal(restored[0].id, 'hotel:341');
  assert.equal(restored[0].category, 'Alojamiento');
  assert.equal(restored[0].confidence, 'UNKNOWN');
  assert.equal(restored[0].amount, null);
  assert.equal(restored[0].currency, null);
});

test('canonical comparison checks the complete persisted package independent of object key order', () => {
  const body = { revisionEsperada: 0, dias: [{ fecha: '2026-11-20', actividades: [{ claveCliente: 'a', orden: 1,
    costo: { tipo: 'DESCONOCIDO' } }] }], vueloSeleccionado: null, alojamiento: null };
  assert.equal(canonicalPersistedPackage(body), canonicalPersistedPackage({ paquete: {
    alojamiento: null, vueloSeleccionado: null, dias: [{ actividades: [{ costo: { tipo: 'DESCONOCIDO' }, orden: 1,
      claveCliente: 'a' }], fecha: '2026-11-20' }], revisionEsperada: 0,
  } }));
  assert.notEqual(canonicalPersistedPackage(body), canonicalPersistedPackage({ ...body,
    dias: [{ ...body.dias[0], actividades: [{ ...body.dias[0].actividades[0], orden: 2 }] }] }));
});

test('package mismatch diagnostics report field paths without exposing values', () => {
  const paths = packageDifferencePaths({ dias: [{ fecha: '2026-12-05', actividades: [{ nombre: 'Secret place' }] }], vueloSeleccionado: null },
    { paquete: { dias: [{ fecha: '2026-12-06', actividades: [{ nombre: 'Other place' }] }], vueloSeleccionado: null } });
  assert.deepEqual(paths, ['dias[0].fecha', 'dias[0].actividades[0].nombre']);
  assert.equal(paths.some((path) => /Secret|Other/.test(path)), false);
});

test('package verification treats JSON key order, absent/null fields, and numeric representation as equivalent but preserves activity order', () => {
  const request = { revisionEsperada: 1, viaje: { numeroViajeros: 1, monedaPresupuesto: 'COP' },
    dias: [{ fecha: '2026-12-05', actividades: [
      { claveCliente: 'a', orden: 1, latitud: 40.4, horaLocal: '09:30:00', descripcion: null },
      { claveCliente: 'b', orden: 2, latitud: 40.5, horaLocal: null },
    ] }], gastos: [], vueloSeleccionado: null, alojamiento: null };
  const fromGet = { revision: 2, paquete: { alojamiento: null, vueloSeleccionado: null, gastos: [],
    dias: [{ actividades: [
      { horaLocal: '09:30:00', latitud: 40.4, orden: 1, claveCliente: 'a' },
      { horaLocal: null, latitud: 40.5, orden: 2, claveCliente: 'b' },
    ], fecha: '2026-12-05' }], viaje: { monedaPresupuesto: 'COP', numeroViajeros: 1 } } };
  assert.deepEqual(packageDifferencePaths(request, fromGet), []);
  const reordered = structuredClone(fromGet);
  reordered.paquete.dias[0].actividades.reverse();
  const paths = packageDifferencePaths(request, reordered);
  assert.ok(paths.includes('dias[0].actividades[0].claveCliente'));
  assert.ok(paths.includes('dias[0].actividades[0].orden'));
  assert.ok(paths.includes('dias[0].actividades[1].claveCliente'));
  assert.ok(paths.includes('dias[0].actividades[1].orden'));
});

test('legacy Jackson @AssertTrue bean properties do not cause false PUT/GET conflicts, while package fields remain strict', () => {
  const request = { revisionEsperada: 0, viaje: { numeroViajeros: 1, monedaPresupuesto: 'COP',
    zonaHoraria: null, presupuestoTotal: 3000000 }, dias: [{ fecha: '2027-01-12', ordenUnico: undefined,
    actividades: [{ claveCliente: 'place-1', tipo: 'EXTERNA', fuente: 'Wikipedia', idExterno: 'Q123',
      nombre: 'Verified attraction', orden: 1, costo: { tipo: 'DESCONOCIDO', estadoConsistente: undefined,
        fechaConsultaValida: undefined }, identidadValida: undefined, parCoordenadasValido: undefined }] }],
    vueloSeleccionado: null, alojamiento: null, gastos: [], fechasUnicas: undefined, clavesAsignacionUnicas: undefined };
  const legacyGet = { revision: 1, paquete: { ...request, revisionEsperada: 0,
    viaje: { ...request.viaje, presupuestoConMoneda: true, zonaHorariaValida: true },
    fechasUnicas: true, clavesAsignacionUnicas: true,
    dias: [{ ...request.dias[0], ordenUnico: true, actividades: [{ ...request.dias[0].actividades[0],
      identidadValida: true, parCoordenadasValido: true,
      costo: { ...request.dias[0].actividades[0].costo, estadoConsistente: true, fechaConsultaValida: true } }] }],
  } };
  assert.deepEqual(packageDifferencePaths(request, legacyGet), []);
  const changed = structuredClone(legacyGet);
  changed.paquete.dias[0].actividades[0].nombre = 'Changed attraction';
  assert.deepEqual(packageDifferencePaths(request, changed), ['dias[0].actividades[0].nombre']);
});

test('package idempotency key is stable for an identical retry and changes with package content', () => {
  const first = { revisionEsperada: 2, dias: [], vueloSeleccionado: null, alojamiento: null };
  assert.equal(packageIdempotencyKey(first, 42), packageIdempotencyKey(first, 42));
  assert.notEqual(packageIdempotencyKey(first, 42), packageIdempotencyKey({ ...first, dias: [{ fecha: '2026-11-20' }] }, 42));
  assert.match(packageIdempotencyKey(first, 42), /^[A-Za-z0-9._:-]{8,80}$/);
});

test('package save errors explain network/CORS, auth, validation, revision conflict and server failures without clearing the draft', () => {
  assert.match(packageSaveFailureMessage(new TypeError('Failed to fetch')), /red o un bloqueo CORS.*borrador local se conservó/i);
  assert.match(packageSaveFailureMessage(new Error('HTTP 401')), /sesión venció.*borrador local se conservó/i);
  assert.match(packageSaveFailureMessage(new Error('PUT paquete (HTTP 422): fecha inválida')), /rechazó datos.*fecha inválida/i);
  assert.match(packageSaveFailureMessage(new Error('PUT paquete (HTTP 400): numeric value out of bounds (<3 digits>.<7 digits> expected)')),
    /precisión de un valor numérico.*borrador local se conservó/i);
  assert.match(packageSaveFailureMessage(new Error('PUT paquete (HTTP 400): El paquete contiene datos inválidos. Campos: vueloSeleccionado.tramos: No puede estar vacío.')),
    /vuelo seleccionado llegó sin tramos.*borrador local se conservó/i);
  assert.match(packageSaveFailureMessage(new Error('PUT paquete (HTTP 409): La revisión del paquete cambió.')), /revisión base obsoleta.*borrador local se conservó/i);
  assert.match(packageSaveFailureMessage(new Error('PUT paquete (HTTP 409): Idempotency-Key ya fue usado con otro contenido.')), /clave de reintento se reutilizó con otro contenido/i);
  assert.match(packageSaveFailureMessage(new Error('HTTP 409')), /conflicto de integridad.*borrador/i);
  assert.match(packageSaveFailureMessage(new Error('HTTP 500')), /logs de Spring Boot.*borrador local se conservó/i);
});
