import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

test('App imports every React hook used at the application root', () => {
  const source = readFileSync(new URL('./App.jsx', import.meta.url), 'utf8');
  const reactImport = source.match(/import\s+React\s*,\s*\{([^}]+)\}\s*from\s*['"]react['"]/);
  assert.ok(reactImport, 'App must use an explicit React named-import list');
  const importedHooks = new Set(reactImport[1].split(',').map((name) => name.trim()));
  const usedHooks = [...source.matchAll(/\b(use[A-Z]\w*)\s*\(/g)].map((match) => match[1]);
  for (const hook of usedHooks) assert.ok(importedHooks.has(hook), `${hook} is used but is not imported from React`);
});

test('initial app entry points to the React root and its mount element exists', () => {
  const html = readFileSync(new URL('../index.html', import.meta.url), 'utf8');
  const entry = readFileSync(new URL('./main.jsx', import.meta.url), 'utf8');
  assert.match(html, /<div\s+id=["']root["']/);
  assert.match(html, /src=["']\/src\/main\.jsx["']/);
  assert.match(entry, /createRoot\(document\.getElementById\(['"]root['"]\)\)/);
  assert.match(entry, /<App\s*\/>/);
});

test('root entry has a recovery boundary that logs render errors and presents a retry action', () => {
  const entry = readFileSync(new URL('./main.jsx', import.meta.url), 'utf8');
  const boundary = readFileSync(new URL('./components/AppErrorBoundary.jsx', import.meta.url), 'utf8');
  assert.match(entry, /<AppErrorBoundary>\s*<App\s*\/>\s*<\/AppErrorBoundary>/);
  assert.match(boundary, /componentDidCatch\(/);
  assert.match(boundary, /console\.error\(/);
  assert.match(boundary, /window\.location\.reload\(\)/);
});

test('package details use accessible tabs and keep each existing stateful section mounted after first visit', () => {
  const app = readFileSync(new URL('./App.jsx', import.meta.url), 'utf8');
  const extras = readFileSync(new URL('./components/TripPlanningExtras.jsx', import.meta.url), 'utf8');
  const planner = readFileSync(new URL('./components/GeneradorItinerario.jsx', import.meta.url), 'utf8');
  const map = readFileSync(new URL('./pages/MapPlaces.jsx', import.meta.url), 'utf8');
  assert.match(app, /role="tablist" aria-label="Secciones del paquete"/);
  for (const tab of ['itinerary', 'flights', 'lodging', 'budget', 'summary', 'collaborators']) {
    assert.ok(app.includes(`'${tab}'`), `missing package tab ${tab}`);
  }
  assert.match(app, /setVisitedPackageTabs\(\(current\) => new Set\(\[\.\.\.current, tabId\]\)\)/);
  assert.match(app, /aria-selected=\{activePackageTab === id\}/);
  assert.match(app, /handleTabKeyDown/);
  assert.match(app, /<GeneradorItinerario key=\{activeTrip\.id\}/);
  assert.match(app, /<MapPlacesErrorBoundary tripKey=\{activeTrip\.id\}/);
  assert.match(app, /<TripPlanningExtras key=\{activeTrip\.id\}/);
  assert.match(extras, /hidden=\{panel === 'flights' \|\| panel === 'collaborators'\}/);
  assert.match(extras, /hidden=\{panel === 'lodging' \|\| panel === 'collaborators'\}/);
  assert.match(app, /<TripPackageWorkspaceProvider key=\{`\$\{session\.user\?\.id \|\| 'user'\}:\$\{activeTrip\.id\}`\}/);
  assert.match(planner, /useWorkspaceState\('draftDays'/);
  assert.match(planner, /setEffectiveItineraryDays\(draftItineraryDays\)/);
  assert.match(map, /normalizeDraftItineraryPlaces\(workspace\?\.effectiveItineraryDays \|\| \[\]\)/);
  assert.match(extras, /workspace \? workspace\.selectedFlight : localSelectedOffer/);
});

test('summary exports the effective draft and save labels distinguish pending writes from itinerary total', async () => {
  const planner = readFileSync(new URL('./components/GeneradorItinerario.jsx', import.meta.url), 'utf8');
  assert.match(planner, /aria-label="Resumen del paquete"/);
  assert.match(planner, /onClick=\{exportPdf\}/);
  assert.ok(planner.includes('Guardar itinerario (${pendingSaveCount} cambios · ${effectiveActivityCount} actividades)'));
  assert.match(planner, /saveInFlightRef\.current/);
  assert.match(planner, /El servidor respondió, pero la relectura no coincide/);
  assert.match(planner, /hidden=\{view !== 'budget'\}/);
  assert.match(planner, /hidden=\{view !== 'itinerary'\}/);
});
