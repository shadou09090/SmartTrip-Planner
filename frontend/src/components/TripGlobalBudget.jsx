import React, { useEffect, useMemo, useRef, useState } from 'react';
import { apiRequest } from '../lib/api';
import { budgetEditorConfidence, calculateGlobalBudget, hotelBudgetExpenseId, mergeBudgetExpenses,
  resolveBudgetExpenseAmount } from '../lib/globalBudget';
import { budgetScenarioStorageKey, clearBudgetScenario, readBudgetScenario, writeBudgetScenario } from '../lib/budgetScenarioStorage';
import { fetchReferenceExchangeRate } from '../lib/referenceExchangeRate';
import { budgetPersistenceNotice } from '../lib/budgetPersistenceNotice';

const FIXED_CATEGORIES = [
  { id: 'flight', title: 'Vuelos', legacy: ['Vuelo'], hint: 'Ingresa el total del grupo o por viajero, incluyendo ida y regreso cuando aplique.' },
  { id: 'hotel', title: 'Alojamiento', legacy: ['Alojamiento'], hint: 'Puedes indicar tarifa por noche o total de la estancia.' },
  { id: 'food', title: 'Alimentación', legacy: ['Alimentación'], daily: true, hint: 'Presupuesto diario por persona × viajeros × días.' },
  { id: 'local-transport', title: 'Transporte local', legacy: ['Transporte'], daily: true, hint: 'No incluye vuelos ni excursiones.' },
  { id: 'excursions', title: 'Excursiones', legacy: ['Excursión'], hint: 'Costos conocidos o estimaciones del grupo.' },
  { id: 'other', title: 'Otros gastos', legacy: ['Otros'], hint: 'Gastos adicionales del viaje.' },
  { id: 'contingency', title: 'Fondo para imprevistos', legacy: ['Imprevistos'], hint: 'Reserva estimada para contingencias.' },
];
const CONFIDENCE_LABELS = {
  CONFIRMED: 'Importe confirmado por el usuario',
  VERIFIED_QUOTE: 'Cotización verificada',
  MANUAL_ESTIMATE: 'Estimación manual',
  MANUAL_FREE: 'Gratuito por supuesto manual',
  VERIFIED_FREE: 'Gratuito verificado',
  SANDBOX: 'Duffel Sandbox · excluido del gasto real',
  UNKNOWN: 'Pendiente de estimación',
};
const packagePersistenceEnabled = import.meta.env.VITE_SMARTTRIP_PACKAGE_PERSISTENCE === 'true';

export default function TripGlobalBudget({ trip, token, userId, lodging, selectedFlight = null, itineraryDays = [],
  persistedExpenses = [], persistedTravelers = null, onScenarioChange, packagePersistenceStatus = 'loading',
  packageDirty = false, packageSaveStatus = 'idle' }) {
  const [flights, setFlights] = useState([]);
  const [flightLoadError, setFlightLoadError] = useState('');
  const scenarioKey = budgetScenarioStorageKey(userId, trip.id);
  const [expenses, setExpenses] = useState([]);
  const [scenarioStorageStatus, setScenarioStorageStatus] = useState('loading');
  const [loadedScenarioKey, setLoadedScenarioKey] = useState(null);
  const lastScenarioTripId = useRef(String(trip.id ?? ''));
  const [scenarioTravelers, setScenarioTravelers] = useState(initialTravelers(trip));
  const travelers = scenarioTravelers;
  const baseCurrency = /^[A-Z]{3}$/.test(trip.budgetCurrency || '') ? trip.budgetCurrency : 'COP';
  const nights = Math.max(0, daysBetween(trip.startDate, trip.endDate));
  const tripDays = Math.max(1, daysBetween(trip.startDate, trip.endDate) + 1);
  const hotelExpenseId = categoryExpenseId(FIXED_CATEGORIES.find((item) => item.id === 'hotel'), trip.id, lodging);

  useEffect(() => {
    let active = true;
    const refreshFlights = () => apiRequest(`/api/viajes/${trip.id}/vuelos`, { token })
      .then((rows) => { if (active) { setFlights(Array.isArray(rows) ? rows : []); setFlightLoadError(''); } })
      .catch((error) => { if (active) setFlightLoadError(error.message); });
    const handleFlightUpdate = (event) => {
      if (String(event.detail?.tripId) === String(trip.id)) refreshFlights();
    };
    refreshFlights();
    window.addEventListener('smarttrip:flight-updated', handleFlightUpdate);
    return () => { active = false; window.removeEventListener('smarttrip:flight-updated', handleFlightUpdate); };
  }, [token, trip.id]);

  useEffect(() => {
    const currentTripId = String(trip.id ?? '');
    const tripChanged = lastScenarioTripId.current !== currentTripId;
    if (!scenarioKey) {
      // A temporarily missing account identity must not erase edits already in
      // this mounted trip. When the trip itself changes, clear the in-memory
      // scenario so it cannot bleed into a different trip.
      if (tripChanged) {
        setExpenses([]);
        setScenarioTravelers(initialTravelers(trip));
      }
      lastScenarioTripId.current = currentTripId;
      setScenarioStorageStatus(userId == null || String(userId).trim() === '' ? 'identity-missing' : 'trip-identity-missing');
      setLoadedScenarioKey(null);
      return;
    }
    lastScenarioTripId.current = currentTripId;
    const storage = localStorageOrNull();
    if (!storage) {
      if (tripChanged) {
        setExpenses([]);
        setScenarioTravelers(persistedTravelers || initialTravelers(trip));
      }
      setScenarioStorageStatus('storage-unavailable');
      setLoadedScenarioKey(null);
      return;
    }
    const restored = readBudgetScenario(storage, scenarioKey);
    const localExpenses = normalizeLegacyExpenses(restored.expenses, trip.id);
    setExpenses(mergeBudgetExpenses(localExpenses, persistedExpenses));
    setScenarioTravelers(restored.travelers || persistedTravelers || initialTravelers(trip));
    setScenarioStorageStatus(restored.status === 'unavailable' ? 'storage-unavailable' : restored.status);
    setLoadedScenarioKey(scenarioKey);
  }, [scenarioKey, trip.travelers, trip.numeroViajeros, persistedExpenses, persistedTravelers]);

  useEffect(() => {
    if (scenarioStorageStatus !== 'ready' || !scenarioKey || loadedScenarioKey !== scenarioKey) return;
    const storage = localStorageOrNull();
    if (!storage) { setScenarioStorageStatus('storage-unavailable'); return; }
    if (!writeBudgetScenario(storage, scenarioKey, expenses, travelers)) setScenarioStorageStatus('write-failed');
  }, [expenses, loadedScenarioKey, scenarioKey, scenarioStorageStatus, travelers]);

  const calculation = useMemo(() => {
    const byId = new Map(expenses.map((expense) => [expense.id, expense]));
    const activeActivities = uniqueActivities(itineraryDays);
    const entries = expenses.filter((expense) => !expense.id.startsWith('activity:')
      && (!isHotelScenario(expense, trip.id) || expense.id === hotelExpenseId))
      .map((expense) => ({ ...expense, amount: resolveBudgetExpenseAmount(expense, travelers, tripDays, nights) }));
    const hasCategoryScenario = (categoryId) => {
      const category = FIXED_CATEGORIES.find((item) => item.id === categoryId);
      return byId.has(categoryExpenseId(category, trip.id, lodging))
        || (categoryId !== 'hotel' && expenses.some((expense) => category.legacy.includes(expense.category)));
    };
    for (const activity of activeActivities) {
      const expenseId = `activity:${stableActivityId(activity)}`;
      const manual = byId.get(expenseId);
      if (manual) entries.push(manual);
      else if (activity.costo != null && activity.moneda && /^[A-Z]{3}$/.test(activity.moneda)
        && ['VERIFIED_QUOTE', 'CONFIRMED'].includes(activity.costoEstado)
        && Number.isFinite(Number(activity.costo)) && Number(activity.costo) >= 0) {
        // Existing catalog costs are stored in the application's trip-budget currency (COP).
        entries.push({ id: expenseId, category: 'Actividades y entradas', label: activity.nombre || 'Actividad',
          amount: Number(activity.costo), currency: activity.moneda || 'COP', confidence: activity.costoEstado === 'MANUAL_ESTIMATE' ? 'MANUAL_ESTIMATE' : 'VERIFIED_QUOTE',
          source: activity.fuente || 'Catálogo SmartTrip', queriedAt: activity.fechaConsulta || null, availability: 'not_provided' });
      } else entries.push({ id: expenseId, category: 'Actividades y entradas', label: activity.nombre || 'Actividad',
        source: activity.fuente || 'Sin fuente de precio', availability: activity.disponibilidad || 'not_provided', confidence: 'UNKNOWN' });
    }
    const hotelEstimate = byId.get(hotelExpenseId);
    if (!hotelEstimate) entries.push({ id: `hotel:${trip.id}`, category: 'Alojamiento',
      label: lodging?.nombre ? `Tarifa pendiente de verificar · ${lodging.nombre}` : 'Alojamiento pendiente de selección y tarifa',
      source: lodging ? 'Geoapify/OpenStreetMap · ubicación, sin tarifas' : 'Pendiente de selección', availability: 'unknown', confidence: 'UNKNOWN' });
    if (!hasCategoryScenario('food')) entries.push({ id: `food:${trip.id}`, category: 'Alimentación', label: 'Presupuesto diario pendiente', source: 'Pendiente de estimación', availability: 'unknown', confidence: 'UNKNOWN' });
    if (!hasCategoryScenario('local-transport')) entries.push({ id: `local-transport:${trip.id}`, category: 'Transporte local', label: 'Presupuesto diario pendiente', source: 'Pendiente de estimación', availability: 'unknown', confidence: 'UNKNOWN' });
    const flightEstimates = expenses.some((expense) => (expense.id === `flight:${trip.id}` || ['Vuelo', 'Vuelos'].includes(expense.category))
      && expense.confidence !== 'SANDBOX');
    const flightEntries = flights.filter((flight) => flight?.id != null);
    for (const flight of flightEntries) {
      const id = `stored-flight:${flight.id}`;
      if (flightEstimates) continue; // A group estimate replaces the unpriced saved records in this scenario.
      if (flight.precio != null && flight.moneda && /^[A-Z]{3}$/.test(flight.moneda) && flight.sandbox !== true) {
        entries.push({ id, category: 'Vuelos', label: `${flight.origen || 'Vuelo'} → ${flight.destino || ''}`,
          amount: Number(flight.precio), currency: flight.moneda, confidence: 'VERIFIED_QUOTE' });
      } else entries.push({ id, category: 'Vuelos', label: 'Vuelo guardado sin precio y moneda verificables', source: 'Esquema Oracle actual', availability: 'unknown', confidence: 'UNKNOWN' });
    }
    const hasFlightScenario = expenses.some((expense) => expense.id === `flight:${trip.id}` || ['Vuelo', 'Vuelos'].includes(expense.category));
    if (!flightEntries.length && !hasFlightScenario && !selectedFlight) entries.push({ id: `flight:${trip.id}`, category: 'Vuelos',
      label: 'Vuelo pendiente de selección o estimación', source: 'Pendiente de cotización o estimación', availability: 'unknown', confidence: 'UNKNOWN' });
    if (selectedFlight && !hasFlightScenario) {
      const sandbox = selectedFlight.testData === true;
      const expired = selectedFlight.expiresAt && Date.parse(selectedFlight.expiresAt) <= Date.now();
      if (sandbox || expired || !selectedFlight.currency || !/^[A-Z]{3}$/.test(selectedFlight.currency)
          || !Number.isFinite(Number(selectedFlight.totalAmount)) || Number(selectedFlight.totalAmount) < 0) {
        entries.push({ id: `selected-flight:${selectedFlight.id}`, category: 'Vuelos',
          label: sandbox ? 'Oferta Sandbox seleccionada · precio comercial real desconocido' : 'Oferta seleccionada sin cotización vigente verificable',
          source: selectedFlight.provider || 'Duffel', queriedAt: selectedFlight.queriedAt || null,
          availability: sandbox ? 'not_provided' : 'expired', confidence: 'UNKNOWN' });
      } else entries.push({ id: `selected-flight:${selectedFlight.id}`, category: 'Vuelos', label: 'Oferta vigente seleccionada · total del grupo',
        amount: Number(selectedFlight.totalAmount), currency: selectedFlight.currency, confidence: 'VERIFIED_QUOTE',
        source: selectedFlight.provider || 'Duffel', queriedAt: selectedFlight.queriedAt || null, availability: 'available' });
    }
    return { ...calculateGlobalBudget({ total: trip.budget > 0 ? trip.budget : null, currency: baseCurrency, entries }),
      travelers, savedFlightCount: flightEntries.length, scenarioExpenses: expenses };
  }, [baseCurrency, expenses, flights, hotelExpenseId, itineraryDays, lodging, nights, selectedFlight, travelers, trip.budget, trip.id, tripDays]);
  const persistenceNotice = budgetPersistenceNotice({ enabled: packagePersistenceEnabled,
    packageStatus: packagePersistenceStatus, packageDirty, scenarioReady: scenarioStorageStatus !== 'loading', saveStatus: packageSaveStatus });

  useEffect(() => { onScenarioChange?.(calculation); }, [calculation, onScenarioChange]);

  const upsertExpense = (expense) => setExpenses((current) => [
    ...current.filter((item) => item.id !== expense.id), expense,
  ]);
  const removeExpense = (id) => setExpenses((current) => current.filter((item) => item.id !== id));
  const resetCorruptScenario = () => {
    if (!window.confirm('El escenario local no se pudo leer. ¿Eliminar únicamente este escenario de este usuario y viaje?')) return;
    if (!clearBudgetScenario(localStorageOrNull(), scenarioKey)) return;
    setExpenses([]);
    setScenarioStorageStatus('ready');
  };

  const activeActivities = uniqueActivities(itineraryDays);
  const activityExpenseIds = new Set(activeActivities.map((activity) => `activity:${stableActivityId(activity)}`));
  const orphanedEstimates = expenses.filter((expense) => expense.id.startsWith('activity:') && !activityExpenseIds.has(expense.id));
  const fixedExpenseIds = new Set(FIXED_CATEGORIES.map((category) => categoryExpenseId(category, trip.id, lodging)));
  const unmatchedExpenses = expenses.filter((expense) => !fixedExpenseIds.has(expense.id)
    && !expense.id.startsWith('activity:'));

  return <section className="trip-global-budget" aria-label="Presupuesto global del viaje">
    <div className="activity-budget__title"><h3>Presupuesto de mi viaje</h3></div>
    <p>Escenario en {calculation.currency}. Los precios faltantes se mantienen como pendientes; una estimación no equivale a una tarifa confirmada.</p>
    <p className={`activity-budget__scope${['error', 'conflict'].includes(persistenceNotice.state) ? ' activity-budget__alert' : ''}`}
      data-persistence-state={persistenceNotice.state} role={persistenceNotice.role} aria-live="polite">
      {persistenceNotice.message} Seleccionar un vuelo o alojamiento no realiza una reserva.
    </p>
    <label className="budget-travelers">Viajeros incluidos
      <input type="number" min="1" max="20" step="1" value={travelers}
        onChange={(event) => setScenarioTravelers(Math.min(20, Math.max(1, Number(event.target.value) || 1)))} />
    </label>
    <div className="activity-budget__values">
      <p><span>Presupuesto disponible</span><strong>{calculation.hasBudget ? money(calculation.total, calculation.currency) : 'Sin presupuesto definido'}</strong></p>
      <p><span>Gastos confirmados</span><strong>{calculation.confirmed > 0 ? money(calculation.confirmed, calculation.currency) : 'Aún sin importe confirmado'}</strong></p>
      <p><span>Estimaciones incluidas</span><strong>{calculation.estimated > 0 ? money(calculation.estimated, calculation.currency) : 'Sin estimaciones incluidas'}</strong></p>
      <p><span>{calculation.unknown > 0 || calculation.foreignCurrency > 0 ? 'Saldo estimado parcial' : 'Saldo proyectado'}</span><strong>{calculation.remaining == null ? '—' : money(calculation.remaining, calculation.currency)}</strong></p>
    </div>
    {calculation.overBudget && <p role="alert" className="activity-budget__alert">El escenario supera el presupuesto por {money(Math.abs(calculation.remaining), calculation.currency)}.</p>}
    {(calculation.unknown > 0 || calculation.foreignCurrency > 0) && <p role="status" className="activity-budget__unknown">
      Saldo parcial; faltan gastos por verificar. Hay {calculation.unknown} costo(s) desconocido(s) y {calculation.foreignCurrency} importe(s) sin conversión utilizable.
    </p>}
    {calculation.sandbox > 0 && <p role="status">Importes Duffel Sandbox excluidos del gasto real: {money(calculation.sandbox, calculation.currency)}.</p>}
    {flightLoadError && <p role="status">No se pudo consultar los vuelos guardados: {flightLoadError}</p>}
    {flights.length > 0 && <p role="status">Vuelos guardados: {flights.length}. El esquema no almacena moneda ni origen Sandbox/Live; sus precios no se suman automáticamente. Puedes registrar una estimación del grupo.</p>}
    {selectedFlight?.testData && <p role="status">La tarifa seleccionada es de Duffel Sandbox ({selectedFlight.totalAmount} {selectedFlight.currency}); se excluye del presupuesto real. Registra una estimación real por separado si deseas proyectar el saldo.</p>}

    <div className="budget-category-grid">
      {FIXED_CATEGORIES.map((category) => <BudgetCategoryCard key={category.id} category={category} expenseId={categoryExpenseId(category, trip.id, lodging)}
        tripId={trip.id} existing={expenses.find((expense) => expense.id === categoryExpenseId(category, trip.id, lodging))}
        legacyEntries={expenses.filter((expense) => category.legacy.includes(expense.category))}
        travelers={travelers} days={category.daily ? tripDays : 1} nights={nights} lodgingName={lodging?.nombre} baseCurrency={baseCurrency}
        onSave={upsertExpense} onRemove={removeExpense} />)}
    </div>

    <section className="budget-activity-costs" aria-label="Costos de actividades del itinerario">
      <h4>Actividades y entradas</h4>
      {!activeActivities.length && <p>Añade actividades al itinerario para registrar estimaciones individuales.</p>}
      {activeActivities.map((activity) => {
        const stableId = stableActivityId(activity);
        const id = `activity:${stableId}`;
        const existing = expenses.find((expense) => expense.id === id);
        const catalogAmountUnverified = activity.costo != null && Number.isFinite(Number(activity.costo));
        const known = catalogAmountUnverified && Boolean(activity.moneda && /^[A-Z]{3}$/.test(activity.moneda))
          && ['VERIFIED_QUOTE', 'CONFIRMED'].includes(activity.costoEstado);
        const costSummary = existing ? `${existing.confidence === 'UNKNOWN' ? 'Pendiente de estimación' : money(resolveBudgetExpenseAmount(existing, travelers, 1, 0), existing.currency)} · ${CONFIDENCE_LABELS[existing.confidence] || 'Estimación'}`
          : known ? `${money(activity.costo, activity.moneda)} · precio registrado con moneda`
            : catalogAmountUnverified ? 'Importe en catálogo sin moneda verificable'
              : 'Precio desconocido';
        return <details className="budget-activity-details" key={id}>
          <summary><span>{activity.nombre || 'Actividad'}</span><small>{costSummary}</small></summary>
          <ActivityCostEditor activity={activity} existing={existing} known={known}
            catalogAmountUnverified={catalogAmountUnverified && !known} baseCurrency={baseCurrency}
            travelers={travelers} onSave={(expense) => upsertExpense({ ...expense, id, category: 'Actividades y entradas', label: activity.nombre || 'Actividad' })}
            onRemove={() => removeExpense(id)} />
        </details>;
      })}
      {orphanedEstimates.length > 0 && <details><summary>Estimaciones de actividades retiradas ({orphanedEstimates.length})</summary>
        <p>Se conservan para reutilizarlas si vuelves a añadir esas actividades; no se incluyen en el saldo actual.</p>
        {orphanedEstimates.map((expense) => <div className="budget-orphan" key={expense.id}><span>{expense.label}: {money(expense.amount, expense.currency)} · no incluido en el viaje actual</span>
          <button type="button" onClick={() => removeExpense(expense.id)}>Eliminar estimación</button></div>)}
      </details>}
    </section>

    {unmatchedExpenses.length > 0 && <details className="budget-breakdown" open><summary>Gastos locales previos / otras partidas ({unmatchedExpenses.length})</summary>
      <p>Ya están incluidos en el cálculo. Revísalos antes de agregar otra estimación del mismo rubro.</p>
      <ul>{unmatchedExpenses.map((expense) => <li key={expense.id}>{expense.category}: {expense.label} · {expense.confidence === 'UNKNOWN' ? 'Pendiente de estimación' : money(expense.amount, expense.currency)} · {CONFIDENCE_LABELS[expense.confidence] || 'Importe registrado'}
        <button type="button" onClick={() => removeExpense(expense.id)}>Quitar</button></li>)}</ul>
    </details>}

    {calculation.included.length > 0 && <details className="budget-breakdown"><summary>Desglose incluido en el saldo</summary>
      <ul>{calculation.included.map((entry) => <li key={entry.id}>{entry.category || 'Gasto'} · {entry.label}: {money(entry.convertedAmount, calculation.currency)}
        {entry.currency !== calculation.currency && ` (referencia de conversión desde ${entry.currency}${entry.rateSource ? ` · ${entry.rateSource}` : ''}${entry.rateDate ? ` · ${entry.rateDate}` : ''}; se contabiliza como estimación)`} · {CONFIDENCE_LABELS[entry.confidence] || 'Importe registrado'}
        {entry.source && ` · Fuente: ${entry.source}`}{entry.queriedAt && ` · Consultado: ${new Date(entry.queriedAt).toLocaleDateString('es-CO')}`}</li>)}</ul>
    </details>}
    {scenarioStorageStatus === 'corrupt' && <p role="alert" className="activity-budget__alert">El escenario local está dañado y se conservó sin sobrescribir. <button type="button" onClick={resetCorruptScenario}>Reiniciar solo este escenario</button></p>}
    {scenarioStorageStatus === 'identity-missing' && <p role="status" className="activity-budget__unknown">La sesión no entregó un identificador de usuario. Se conserva el escenario en esta vista, pero no se puede aislar ni recuperar localmente hasta restablecer la identidad.</p>}
    {scenarioStorageStatus === 'trip-identity-missing' && <p role="status" className="activity-budget__unknown">No se puede identificar este viaje para guardar su escenario local.</p>}
    {scenarioStorageStatus === 'storage-unavailable' && <p role="status" className="activity-budget__unknown">El navegador no permitió acceder al almacenamiento local. Los cambios actuales siguen en pantalla; evita recargar o cerrar esta pestaña hasta guardarlos en el paquete.</p>}
    {scenarioStorageStatus === 'write-failed' && <p role="status" className="activity-budget__unknown">Falló la escritura del escenario local. Los cambios siguen en pantalla; evita recargar o cerrar esta pestaña hasta guardarlos en el paquete.</p>}
  </section>;
}

function BudgetCategoryCard({ category, expenseId, existing, legacyEntries, travelers, days, nights, lodgingName, baseCurrency, onSave, onRemove }) {
  const [amount, setAmount] = useState(existing?.unitAmount == null ? (existing?.amount == null ? '' : String(existing.amount)) : String(existing.unitAmount));
  const [currency, setCurrency] = useState(existing?.currency || baseCurrency);
  const [confidence, setConfidence] = useState(budgetEditorConfidence(existing?.confidence));
  const [perPerson, setPerPerson] = useState(existing?.pricingMode === 'per_person');
  const [nightly, setNightly] = useState(existing?.pricingMode === 'per_night' || (!existing && category.id === 'hotel'));
  const [rooms, setRooms] = useState(Math.max(1, Number(existing?.rooms) || 1));
  const [rate, setRate] = useState(existing?.rateToBudget && existing.currency !== 'COP' ? String(existing.rateToBudget) : '');
  const [rateEvidence, setRateEvidence] = useState(existing?.rateSource ? { provider: existing.rateSource, date: existing.rateDate } : null);
  useEffect(() => {
    setAmount(existing?.unitAmount == null ? (existing?.amount == null ? '' : String(existing.amount)) : String(existing.unitAmount));
    setCurrency(existing?.currency || baseCurrency);
    setConfidence(budgetEditorConfidence(existing?.confidence));
    setRate(existing?.rateToBudget && existing.currency !== 'COP' ? String(existing.rateToBudget) : '');
    setRateEvidence(existing?.rateSource ? { provider: existing.rateSource, date: existing.rateDate } : null);
    setPerPerson(existing?.pricingMode === 'per_person');
    setNightly(existing?.pricingMode === 'per_night' || (!existing && category.id === 'hotel'));
    setRooms(Math.max(1, Number(existing?.rooms) || 1));
  }, [baseCurrency, existing?.id, existing?.amount, existing?.unitAmount, existing?.pricingMode, existing?.rooms, existing?.currency, existing?.confidence, existing?.rateToBudget, existing?.rateSource, existing?.rateDate]);
  const parsed = Number(amount);
  const multiplier = (category.daily ? travelers * days : (perPerson ? travelers : 1)) * (nightly ? nights * rooms : 1);
  const total = amount.trim() && Number.isFinite(parsed) && parsed >= 0 ? parsed * multiplier : null;
  const id = expenseId;
  const formula = category.id === 'hotel' ? `${nights} noche(s)${nightly ? ` × ${rooms} habitación(es)` : ''}`
    : category.daily ? `${travelers} viajero(s) × ${days} día(s)` : perPerson ? `${travelers} viajero(s)` : 'Total del grupo';
  const save = (event) => {
    event.preventDefault();
    if (!amount.trim() || total == null || (currency !== baseCurrency && (!(Number(rate) > 0) || !Number.isFinite(Number(rate))))) return;
    const label = `${category.title} · ${formula}`;
    onSave({ id, category: category.title, label, amount: total, unitAmount: parsed,
      pricingMode: category.daily ? 'daily_person' : (nightly ? 'per_night' : (perPerson ? 'per_person' : 'total')),
      travelers, nights, rooms, currency, confidence, rateToBudget: currency === baseCurrency ? 1 : Number(rate),
      source: 'Estimación manual del usuario', queriedAt: new Date().toISOString(), availability: 'unknown',
      rateSource: currency === baseCurrency ? null : (rateEvidence?.provider || 'Tasa ingresada manualmente'),
      rateDate: currency === baseCurrency ? null : (rateEvidence?.date || null) });
  };
  return <article className="budget-category-card">
    <header><h4>{category.title}</h4><p>{category.hint}</p></header>
    {category.id === 'hotel' && lodgingName && <p className="budget-category-card__hotel">Alojamiento seleccionado: <strong>{lodgingName}</strong></p>}
    {existing ? <p className="budget-category-card__current">{existing.confidence === 'UNKNOWN' ? 'Pendiente de estimación' : `${money(resolveBudgetExpenseAmount(existing, travelers, days, nights), existing.currency)} · ${CONFIDENCE_LABELS[existing.confidence]}`}</p>
      : <p className="budget-category-card__current">{category.id === 'hotel' ? 'Tarifa no disponible · pendiente de estimación' : 'Pendiente de estimación'}</p>}
    {legacyEntries?.length > 0 && <p className="budget-category-card__current">Ya hay {legacyEntries.length} partida(s) anterior(es) de este rubro incluidas abajo; revísalas para evitar doble conteo.</p>}
    {category.id === 'hotel' && <p>Viaje: {nights} noche(s) calculadas · {travelers} viajero(s)</p>}
    <form onSubmit={save}>
      <label>{nightly ? 'Costo por noche / unidad' : category.daily ? 'Presupuesto diario por persona' : 'Importe'}
        <input type="number" min="0" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="Ingresar importe" /></label>
      <label>Moneda (código ISO)<input required pattern="[A-Za-z]{3}" maxLength="3" value={currency} onChange={(event) => { setCurrency(event.target.value.toUpperCase()); setRateEvidence(null); }} aria-label={`Moneda para ${category.title}`} /></label>
      {category.id === 'flight' && <label className="budget-inline-check"><input type="checkbox" checked={perPerson} onChange={(event) => setPerPerson(event.target.checked)} /> Importe por persona</label>}
      {category.id === 'hotel' && <label className="budget-inline-check"><input type="checkbox" checked={nightly} onChange={(event) => setNightly(event.target.checked)} /> Calcular por noche (total por habitación)</label>}
      {category.id === 'hotel' && nightly && <label>Habitaciones<input type="number" min="1" max="20" step="1" value={rooms} onChange={(event) => setRooms(Math.max(1, Number(event.target.value) || 1))} /></label>}
      {category.daily && <p className="budget-formula">Cálculo: {formula}{total != null ? ` = ${money(total, currency)}` : ' · Pendiente de estimación'}</p>}
      {category.id === 'hotel' && <p className="budget-formula">Cálculo: {nightly ? `${nights} noches` : 'total estancia'}{total != null ? ` = ${money(total, currency)}` : ' · Pendiente de estimación'}</p>}
      {currency !== baseCurrency && <ReferenceRateInput currency={currency} baseCurrency={baseCurrency} rate={rate}
        onRateChange={setRate} evidence={rateEvidence} onEvidenceChange={setRateEvidence} />}
      <label>Tipo de importe<select value={confidence} onChange={(event) => setConfidence(event.target.value)}>
        <option value="MANUAL_ESTIMATE">Estimación manual</option><option value="CONFIRMED">Importe confirmado por mí</option>
      </select></label>
      <div className="budget-card-actions"><button type="submit" disabled={!existing && legacyEntries?.length > 0}>{existing ? 'Actualizar estimación' : legacyEntries?.length ? 'Revisa partidas anteriores' : 'Añadir al escenario'}</button>
        {existing && <button type="button" onClick={() => onRemove(id)}>Quitar</button>}</div>
    </form>
  </article>;
}

function ActivityCostEditor({ activity, existing, known, catalogAmountUnverified, travelers, baseCurrency, onSave, onRemove }) {
  const [amount, setAmount] = useState(existing?.unitAmount == null ? (known ? String(activity.costo) : '') : String(existing.unitAmount));
  const [currency, setCurrency] = useState(existing?.currency || activity.moneda || baseCurrency);
  const [perPerson, setPerPerson] = useState(true);
  const [confidence, setConfidence] = useState(existing?.confidence || (known ? 'VERIFIED_QUOTE' : 'MANUAL_ESTIMATE'));
  const [rate, setRate] = useState(existing?.rateToBudget && existing.currency !== 'COP' ? String(existing.rateToBudget) : '');
  const [rateEvidence, setRateEvidence] = useState(existing?.rateSource ? { provider: existing.rateSource, date: existing.rateDate } : null);
  const value = Number(amount);
  const total = amount.trim() && Number.isFinite(value) && value >= 0 ? value * (perPerson ? travelers : 1) : null;
  useEffect(() => {
    setAmount(existing?.unitAmount == null ? (known ? String(activity.costo) : '') : String(existing.unitAmount));
    setCurrency(existing?.currency || activity.moneda || baseCurrency);
    setConfidence(existing?.confidence || (known ? 'VERIFIED_QUOTE' : 'MANUAL_ESTIMATE'));
    setRate(existing?.rateToBudget && existing.currency !== 'COP' ? String(existing.rateToBudget) : '');
    setRateEvidence(existing?.rateSource ? { provider: existing.rateSource, date: existing.rateDate } : null);
    setPerPerson(existing?.pricingMode !== 'total');
  }, [activity.costo, activity.moneda, baseCurrency, existing?.amount, existing?.unitAmount, existing?.pricingMode, existing?.currency, existing?.confidence, existing?.rateToBudget, existing?.rateSource, existing?.rateDate, known]);
  const submit = (event) => {
    event.preventDefault();
    const effectiveTotal = confidence === 'MANUAL_FREE' || confidence === 'VERIFIED_FREE' ? 0 : total;
    if ((confidence !== 'MANUAL_FREE' && confidence !== 'VERIFIED_FREE' && !amount.trim())
      || effectiveTotal == null || (currency !== baseCurrency && effectiveTotal > 0 && (!(Number(rate) > 0) || !Number.isFinite(Number(rate))))) return;
    onSave({ amount: effectiveTotal, unitAmount: effectiveTotal === 0 ? 0 : value, pricingMode: perPerson ? 'per_person' : 'total', travelers, currency, confidence, rateToBudget: currency === baseCurrency ? 1 : Number(rate),
      label: `${activity.nombre || 'Actividad'} · ${perPerson ? `por persona × ${travelers}` : 'total del grupo'}`,
      source: confidence === 'VERIFIED_FREE' ? (activity.fuente || 'Fuente verificada') : 'Estimación manual del usuario',
      rateSource: currency === baseCurrency ? null : (rateEvidence?.provider || 'Tasa ingresada manualmente'),
      rateDate: currency === baseCurrency ? null : (rateEvidence?.date || null),
      queriedAt: new Date().toISOString(), availability: confidence === 'VERIFIED_FREE' ? 'not_provided' : 'unknown' });
  };
  return <article className="budget-activity-row"><div><strong>{activity.nombre || 'Actividad'}</strong><p>{existing ? 'Escenario local: ' : known ? 'Importe con moneda conocida: ' : catalogAmountUnverified ? `El catálogo guarda ${activity.costo}, pero no conserva moneda o fuente verificable; no se suma automáticamente. ` : 'Costo desconocido. No se presume gratuito.'}{existing ? `${existing.confidence === 'UNKNOWN' ? 'Pendiente de estimación' : money(resolveBudgetExpenseAmount(existing, travelers, 1, 0), existing.currency)} · ${CONFIDENCE_LABELS[existing.confidence]}` : known ? money(activity.costo, activity.moneda) : ''}</p></div>
    <form onSubmit={submit}>
      <label>Costo por persona<input type="number" min="0" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} placeholder="Costo desconocido" /></label>
      <label>Moneda (código ISO)<input required pattern="[A-Za-z]{3}" maxLength="3" value={currency} onChange={(event) => { setCurrency(event.target.value.toUpperCase()); setRateEvidence(null); }} aria-label={`Moneda para ${activity.nombre || 'actividad'}`} /></label>
      <label className="budget-inline-check"><input type="checkbox" checked={perPerson} onChange={(event) => setPerPerson(event.target.checked)} /> Por persona × {travelers}</label>
      <label>Clasificación<select value={confidence} onChange={(event) => setConfidence(event.target.value)}>
        <option value="MANUAL_ESTIMATE">Estimación manual</option>
        <option value="CONFIRMED">Confirmado por mí</option><option value="MANUAL_FREE">Gratuito por supuesto manual</option>
      </select></label>
      {currency !== baseCurrency && <ReferenceRateInput currency={currency} baseCurrency={baseCurrency} rate={rate}
        onRateChange={setRate} evidence={rateEvidence} onEvidenceChange={setRateEvidence} />}
      <span>{total != null ? `Total calculado: ${money(total, currency)}` : 'Pendiente de estimación'}</span>
      <button type="submit">{existing || known ? 'Actualizar costo' : 'Añadir estimación'}</button>
      {existing && <button type="button" onClick={onRemove}>Quitar</button>}
    </form>
  </article>;
}

function ReferenceRateInput({ currency, baseCurrency, rate, onRateChange, evidence, onEvidenceChange }) {
  const [status, setStatus] = useState('');
  const [loading, setLoading] = useState(false);
  const lookup = async () => {
    setLoading(true);
    setStatus('Consultando tasa de referencia…');
    try {
      const result = await fetchReferenceExchangeRate(currency, baseCurrency);
      onRateChange(String(result.rate));
      onEvidenceChange({ provider: result.provider, date: result.date });
      setStatus(`${result.provider}: ${result.rate} ${baseCurrency} por ${currency}${result.date ? ` · fecha ${result.date}` : ''}. Es una referencia, no una cotización de pago.`);
    } catch (error) {
      setStatus(`${error.message} Puedes ingresar una tasa manual si la conoces.`);
    } finally {
      setLoading(false);
    }
  };
  return <div className="budget-reference-rate">
    <label>Tasa a {baseCurrency} (tasa de referencia)<input required type="number" min="0.000001" step="any" value={rate}
      onChange={(event) => { onRateChange(event.target.value); onEvidenceChange(null); setStatus('Tasa modificada manualmente; se clasificará como estimación.'); }}
      placeholder={`${baseCurrency} por unidad`} /></label>
    <button type="button" onClick={lookup} disabled={loading || !/^[A-Z]{3}$/.test(currency) || !/^[A-Z]{3}$/.test(baseCurrency)}>
      {loading ? 'Consultando…' : 'Consultar referencia gratuita'}
    </button>
    {(status || evidence) && <p role="status">{status || `${evidence.provider}${evidence.date ? ` · ${evidence.date}` : ''}. La conversión permanece estimada.`}</p>}
  </div>;
}

function uniqueActivities(days) {
  const map = new Map();
  (Array.isArray(days) ? days : []).forEach((day) => (day?.actividades || []).forEach((activity) => {
    if (activity?.id != null) map.set(String(activity.id), activity);
  }));
  return [...map.values()];
}

function stableActivityId(activity) { return String(activity.id).replace(/^(external|catalog|manual):/, '$1:'); }
function daysBetween(start, end) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(start || '') || !/^\d{4}-\d{2}-\d{2}$/.test(end || '')) return 0;
  const delta = Date.parse(`${end}T00:00:00Z`) - Date.parse(`${start}T00:00:00Z`);
  return Number.isFinite(delta) && delta >= 0 ? Math.floor(delta / 86_400_000) : 0;
}
function initialTravelers(trip) {
  const value = Number(trip?.travelers || trip?.numeroViajeros || 1);
  return Number.isInteger(value) && value >= 1 && value <= 20 ? value : 1;
}
function normalizeLegacyExpenses(expenses, tripId) {
  let result = [...expenses];
  for (const category of FIXED_CATEGORIES) {
    // An old hotel estimate has no property identity, so never silently attach it to the currently selected hotel.
    if (category.id === 'hotel') continue;
    const stableId = `${category.id}:${tripId}`;
    if (result.some((expense) => expense.id === stableId)) continue;
    const matches = result.filter((expense) => category.legacy.includes(expense.category));
    if (matches.length !== 1) continue;
    const [legacy] = matches;
    result = result.map((expense) => expense.id !== legacy.id ? expense : {
      ...expense, id: stableId, category: category.title,
      unitAmount: expense.unitAmount ?? expense.amount,
      pricingMode: expense.pricingMode || 'total',
    });
  }
  return result;
}
function categoryExpenseId(category, tripId, lodging) {
  if (category?.id !== 'hotel') return `${category?.id}:${tripId}`;
  return hotelBudgetExpenseId(tripId, lodging);
}
function isHotelScenario(expense, tripId) {
  return expense.id === `hotel:${tripId}` || expense.id.startsWith(`hotel:${tripId}:`) || expense.category === 'Alojamiento';
}
function money(amount, currency = 'COP') {
  try { return new Intl.NumberFormat('es-CO', { style: 'currency', currency, maximumFractionDigits: 2 }).format(Number(amount) || 0); }
  catch { return `${Number(amount) || 0} ${currency}`; }
}
function localStorageOrNull() { try { return window.localStorage; } catch { return null; } }
