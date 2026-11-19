import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import React from 'react';
import { renderToString } from 'react-dom/server';
import { createServer } from 'vite';
import { fileURLToPath } from 'node:url';

test('PDF export builds from initialized saved state plus the current local draft', async () => {
  const source = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  assert.match(source, /const draftItineraryDays = useMemo\(\(\) => buildDraftItinerary/);
  assert.match(source, /itinerary:\s*\{\s*dias:\s*draftItineraryDays\s*\}/);
  assert.match(source, /draftDaySummary\.reduce/);
  assert.match(source, /downloadItineraryPdf\(pdf, filename\)/);
  assert.match(source, /draftItineraryDays\.map/);
  assert.match(source, /Itinerario de mi viaje/);
  assert.doesNotMatch(source, /Tu itinerario sugerido para/);
  assert.doesNotMatch(source, /Number\(dayId\)\s*===\s*Number\(day\.id\)/);
});

test('integral package save is opt-in, idempotent, GET-verified, and keeps the local draft until verification', async () => {
  const source = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  assert.match(source, /VITE_SMARTTRIP_PACKAGE_PERSISTENCE === 'true'/);
  assert.match(source, /if \(!packagePersistenceEnabled && externalRows\.length\)/);
  assert.match(source, /No se enviaron actividades externas al endpoint antiguo; el borrador sigue intacto/);
  const saveBranch = source.slice(source.indexOf('if (packagePersistenceEnabled) {', source.indexOf('const savePlanningDraft')),
    source.indexOf('let savedItinerary = itinerario;'));
  assert.match(saveBranch, /classifyPackageSnapshot/);
  assert.match(saveBranch, /No se envió el PUT/);
  assert.match(saveBranch, /method: 'PUT'/);
  assert.match(saveBranch, /Idempotency-Key/);
  const readBack = saveBranch.indexOf('const verifiedPackage = await apiRequest');
  const compare = saveBranch.indexOf('packageContentSignature(payload) !== packageContentSignature(verifiedPackage)');
  const clearDraft = source.indexOf('removeItineraryDraft(safeLocalStorage(), userId, trip.id)', source.indexOf('const applyVerifiedPackage'));
  assert.ok(readBack >= 0 && compare > readBack && clearDraft > compare);
  assert.match(source, /setNotice\(successMessage\)/);
  assert.match(source, /Oracle aceptó el PUT, pero no se pudo confirmar la lectura GET/);
});

test('lodging persistence keeps the saved hotel in shared package selection to prevent a tab-save race', async () => {
  const extras = await readFile(new URL('./TripPlanningExtras.jsx', import.meta.url), 'utf8');
  const serializer = await readFile(new URL('../lib/packagePersistence.js', import.meta.url), 'utf8');
  assert.match(extras, /setLodging\(data\);\s*\/\/ Keep the just-persisted lodging in the shared package workspace[\s\S]*workspace\?\.setSelectedLodging\(data\)/);
  assert.match(serializer, /alojamientoId: numericId\(lodging\.id\)/);
  assert.match(serializer, /fuente: lodging\.proveedor \|\| lodging\.fuente \|\| null/);
});

test('package revision conflict offers explicit local or Oracle resolution without clearing the draft by default', async () => {
  const source = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  assert.match(source, /Mantener borrador local y confirmar reemplazo en un guardado posterior/);
  assert.match(source, /Usar versión Oracle y descargar respaldo local/);
  assert.match(source, /clave de borrador del navegador no se eliminó/);
  assert.match(source, /if \(remoteState === 'conflict' \|\| remoteState === 'remote-removed'\)[\s\S]*setPackageConflict/);
});

test('a newly created package enters the existing dashboard and builds only a local first-draft', async () => {
  const app = await readFile(new URL('../App.jsx', import.meta.url), 'utf8');
  const source = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  const extras = await readFile(new URL('./TripPlanningExtras.jsx', import.meta.url), 'utf8');
  assert.match(app, /onPackageCreated=\{\(trip\) => openTrip\(trip, \{ startPackage: true \}\)\}/);
  assert.match(app, /autoCreateDraft=\{autoPackageTripId === String\(activeTrip\.id\)\}/);
  assert.match(source, /if \(!autoCreateDraft \|\| loading\) return/);
  assert.match(source, /autoDraftStarted\.current === signature/);
  assert.match(source, /itinerario\?\.dias\?\.some\(\(day\) => day\.actividades\?\.length\)/);
  assert.match(source, /buildSuggestedDraft\('keep'\)/);
  assert.match(extras, /Promise\.allSettled\(\[/);
  assert.doesNotMatch(source, /autoCreateDraft[\s\S]{0,220}savePlanningDraft\(/);
});

test('zero assigned activities are a recoverable generation failure and orphaned draft refs are excluded from save counts', async () => {
  const source = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  assert.match(source, /result\?\.success \? 'ready' : 'limited'/);
  assert.match(source, /if \(!assignedCount \|\| visibleNewActivityCount === 0\)/);
  assert.match(source, /Se conservó el borrador anterior/);
  assert.match(source, /reconcileDraftAssignments/);
  assert.match(source, /orderedAssignments\(draftReconciliation\.external/);
  assert.match(source, /disabled=\{savingDraft \|\| loading \|\| !packageSyncReady \|\| Boolean\(packageConflict\)/);
  const pendingPlan = source.match(/const hasPendingPlan =([\s\S]*?);/);
  assert.ok(pendingPlan);
  assert.doesNotMatch(pendingPlan[1], /orphanCount/);
  assert.match(source, /'server-changed', 'destination-changed', 'dates-changed'/);
  assert.match(source, /Seleccionados \/ asignados/);
  assert.match(source, /Actividades visibles en borrador/);
});

test('empty itinerary day metadata cannot appear as one saveable item when there are zero activities', async () => {
  const source = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  const draft = await readFile(new URL('../lib/tourismItineraryDraft.js', import.meta.url), 'utf8');
  assert.match(source, /countSavedAssignmentChanges\(itinerario\?\.dias, draftDays\)/);
  assert.match(source, /\+ savedAssignmentChanges/);
  assert.match(draft, /export function countSavedAssignmentChanges/);
  assert.match(draft, /Day IDs are normalized/);
  assert.match(source, /if \(!externalRows\.length && !catalogRows\.length && !manualRows\.length && !dayEditsDirty && !packageSelectionPending\) return/);
});

test('package summary differentiates saved flights from a currently selected flight offer', async () => {
  const source = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  const budget = await readFile(new URL('./TripGlobalBudget.jsx', import.meta.url), 'utf8');
  assert.match(source, /savedFlightCount: globalBudgetScenario\?\.savedFlightCount/);
  assert.match(source, /Sin oferta seleccionada en esta sesión/);
  assert.match(budget, /savedFlightCount: flightEntries\.length/);
});

test('blank cost inputs stay pending rather than showing a calculated zero', async () => {
  const budget = await readFile(new URL('./TripGlobalBudget.jsx', import.meta.url), 'utf8');
  assert.match(budget, /amount\.trim\(\) && Number\.isFinite\(parsed\)/);
  assert.match(budget, /amount\.trim\(\) && Number\.isFinite\(value\)/);
  assert.match(budget, /Pendiente de estimación/);
  assert.match(budget, /Sin estimaciones incluidas/);
});

test('itinerary, lodging and flight result lists share bounded accessible scrolling', async () => {
  const itinerary = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  const extras = await readFile(new URL('./TripPlanningExtras.jsx', import.meta.url), 'utf8');
  const flights = await readFile(new URL('./FlightSearchPanel.jsx', import.meta.url), 'utf8');
  const styles = await readFile(new URL('../styles/package-tabs.css', import.meta.url), 'utf8');
  assert.match(itinerary, /planning-scroll-region planning-scroll-region--itinerary/);
  assert.match(extras, /planning-scroll-region planning-scroll-region--lodging/);
  assert.match(flights, /planning-scroll-region planning-scroll-region--flights/);
  assert.match(styles, /max-height: min\(70vh, 48rem\)/);
  assert.match(styles, /scrollbar-width: thin/);
  assert.match(styles, /@media \(max-width: 480px\)[\s\S]*planning-scroll-region \{ max-height: 58vh/);
});

test('planner restores local drafts, protects a newer server version, and requires confirmation to discard', async () => {
  const source = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  const storage = await readFile(new URL('../lib/tripItineraryDraftStorage.js', import.meta.url), 'utf8');
  assert.match(source, /readItineraryDraft\(safeLocalStorage\(\)/);
  assert.match(source, /writeItineraryDraft\(storage/);
  assert.match(source, /removeItineraryDraft\(safeLocalStorage\(\), userId, trip\.id\)/);
  assert.match(source, /¿Descartar el borrador local de este viaje\?/);
  assert.match(source, /dates-changed/);
  assert.match(source, /server-changed/);
  assert.match(storage, /itinerarySignature\(itinerary\)/);
  assert.match(storage, /smarttrip:itinerary-draft:v1/);
  assert.doesNotMatch(storage, /token|password|jwt/i);
});

test('a destination or date change resets draft context before capturing the new hydration revision', async () => {
  const source = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  const loadBody = source.slice(source.indexOf('const load = useCallback'), source.indexOf('useEffect(() => {\n    setSelectedInterests'));
  const resetContext = loadBody.indexOf('activeDraftContext.current = contextKey;');
  const resetRevision = loadBody.indexOf('draftRevision.current = 0;');
  const captureRevision = loadBody.indexOf('const startingDraftRevision = draftRevision.current;');
  const capturePending = loadBody.indexOf('const pendingAtStart = pendingDraftRef.current;');
  assert.ok(resetContext >= 0 && resetRevision > resetContext);
  assert.ok(captureRevision > resetRevision && capturePending > captureRevision);
  assert.match(loadBody, /pendingDraftRef\.current = false;/);
});

test('a temporary recommendation API failure preserves results for the same trip context and clears only on context change', async () => {
  const source = await readFile(new URL('./GeneradorItinerario.jsx', import.meta.url), 'utf8');
  assert.match(source, /recommendationContextRef\.current !== contextKey/);
  assert.match(source, /recommendationContextRef\.current = contextKey/);
  const failedRecommendations = source.slice(source.indexOf('if \(Object\.hasOwn\(fulfilled, \'recommendations\'\)\)'),
    source.indexOf('const sectionErrors ='));
  assert.match(failedRecommendations, /if \(recommendationContextRef\.current !== contextKey\)/);
});

test('GeneradorItinerario renders initial Barcelona trip props without an undefined itinerary reference', async (t) => {
  const vite = await createServer({
    configFile: fileURLToPath(new URL('../../vite.config.js', import.meta.url)),
    appType: 'custom',
    server: { middlewareMode: true },
  });
  t.after(() => vite.close());
  const { default: GeneradorItinerario } = await vite.ssrLoadModule('/src/components/GeneradorItinerario.jsx');
  const trip = {
    id: 163,
    destination: 'Barcelona, España',
    startDate: '2026-11-23',
    endDate: '2026-11-28',
    budget: 14997001,
    budgetCurrency: 'COP',
    travelers: 1,
    interests: [],
  };

  let html;
  assert.doesNotThrow(() => {
    html = renderToString(React.createElement(GeneradorItinerario, {
      token: null, trip, userId: 'regression-user', view: 'itinerary',
    }));
  });
  assert.match(html, /Itinerario de mi viaje/);
  assert.match(html, /2026-11-23/);
});
