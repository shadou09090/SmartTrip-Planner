import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { budgetPersistenceNotice } from './budgetPersistenceNotice.js';

test('budget notice says Oracle verified only when the package is ready and clean', () => {
  const notice = budgetPersistenceNotice({ enabled: true, packageStatus: 'ready', packageDirty: false, scenarioReady: true });
  assert.equal(notice.state, 'saved');
  assert.match(notice.message, /sincronizado y verificado con Oracle/);
  assert.match(notice.message, /estimaciones nuevas quedan locales/);
});

test('budget notice marks local edits pending and never presents them as saved', () => {
  const notice = budgetPersistenceNotice({ enabled: true, packageStatus: 'ready', packageDirty: true, scenarioReady: true });
  assert.equal(notice.state, 'pending');
  assert.match(notice.message, /cambios locales pendientes/);
  assert.doesNotMatch(notice.message, /sincronizado y verificado/);
});

test('budget notice reports saving, failed verification, conflict, unavailable and missing package states', () => {
  const get = (overrides) => budgetPersistenceNotice({ enabled: true, packageStatus: 'ready', packageDirty: false, scenarioReady: true, ...overrides });
  assert.equal(get({ saveStatus: 'saving' }).state, 'saving');
  assert.equal(get({ saveStatus: 'failed' }).role, 'alert');
  assert.equal(get({ packageStatus: 'unavailable' }).state, 'error');
  assert.equal(get({ packageStatus: 'conflict' }).state, 'conflict');
  assert.equal(get({ packageStatus: 'not-found' }).state, 'pending');
  assert.equal(get({ packageStatus: 'loading' }).state, 'checking');
});

test('disabled persistence explicitly labels the scenario local-only', () => {
  const notice = budgetPersistenceNotice({ enabled: false, packageStatus: 'ready', packageDirty: false, scenarioReady: true });
  assert.equal(notice.state, 'local-only');
  assert.match(notice.message, /no se sincroniza con Oracle/);
});

test('budget panel renders the persistence notice state accessibly and retains reservation warning', async () => {
  const source = await readFile(new URL('../components/TripGlobalBudget.jsx', import.meta.url), 'utf8');
  assert.match(source, /budgetPersistenceNotice\(/);
  assert.match(source, /aria-live="polite"/);
  assert.match(source, /Seleccionar un vuelo o alojamiento no realiza una reserva/);
});
