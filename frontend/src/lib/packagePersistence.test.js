import test from 'node:test';
import assert from 'node:assert/strict';
import { buildPackageSavePayload, canonicalPersistedPackage, classifyPackageSnapshot, packageIdempotencyKey, packageSaveFailureMessage, restorePackageExpenses } from './packagePersistence.js';

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
