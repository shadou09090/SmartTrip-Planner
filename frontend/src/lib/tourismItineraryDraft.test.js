import test from 'node:test';
import assert from 'node:assert/strict';
import { buildDraftItinerary, buildTemporaryTripDays, buildTourismDraft, combineTourismCandidates, countSavedAssignmentChanges, excludeAssignedTourismCandidates, filterRecommendationCatalog, getSuggestedActivityCapacity, orderedAssignments, paginateRecommendationCatalog, reconcileDraftAssignments, resolveTripPlanningDays, summarizeDraftDays, summarizeRecommendationCandidates } from './tourismItineraryDraft.js';

const place = (id, latitud, longitud, alcance = 'CITY') => ({ id, latitud, longitud, alcance });

test('does not count empty-day metadata or string/number IDs as saveable activities', () => {
  const saved = [{ id: 161, fecha: '2026-11-20', actividades: [] }];
  const draft = [{ id: '161', fecha: '2026-11-20', actividades: [] }];
  assert.equal(countSavedAssignmentChanges(saved, draft), 0);
});

test('counts actual saved activity removals, moves, and reorderings individually', () => {
  const saved = [
    { id: 161, actividades: [{ id: 1, orden: 1 }, { id: 2, orden: 2 }] },
    { id: 162, actividades: [] },
  ];
  const draft = [
    { id: '161', actividades: [{ id: 2, orden: 1 }] },
    { id: '162', actividades: [{ id: 1, orden: 1 }] },
  ];
  assert.equal(countSavedAssignmentChanges(saved, draft), 2);
});

test('creates a temporary multi-day proposal, clusters nearby places, and excludes regional candidates', () => {
  const recommendations = [
    place('museum', 24.45, 54.37),
    place('nearby-mosque', 24.46, 54.38),
    place('island', 24.50, 54.60),
    place('nearby-island-attraction', 24.51, 54.61),
    place('day-trip', 25.20, 55.27, 'REGIONAL_ATTRACTION'),
    place('far-excursion', 25.25, 55.3, 'EXCURSION_OPTIONAL'),
  ];
  const days = [{ id: 11 }, { id: 12 }];

  const result = buildTourismDraft(recommendations, days);

  assert.equal(result.order.length, 4);
  assert.equal(result.regionalExcluded, 2);
  assert.equal(result.assignment['day-trip'], undefined);
  assert.equal(result.assignment['far-excursion'], undefined);
  assert.equal(result.assignment.museum, 11);
  assert.equal(result.assignment['nearby-mosque'], 11);
  assert.equal(result.assignment.island, 12);
  assert.equal(result.assignment['nearby-island-attraction'], 12);
});

test('ignores duplicate ids and invalid coordinates and returns empty without saved days', () => {
  const result = buildTourismDraft([
    place('one', 48.85, 2.35),
    place('one', 48.86, 2.36),
    place('bad', null, null),
  ], []);
  assert.deepEqual(result.assignment, {});
  assert.deepEqual(result.order, []);
});

test('repeated proposal regeneration cannot duplicate activity assignments or inflate save count', () => {
  const rows = orderedAssignments({ museum: 'day-1', park: 'day-2' }, ['museum', 'museum', 'removed', 'park', 'park']);
  assert.deepEqual(rows, [['museum', 'day-1'], ['park', 'day-2']]);
  assert.equal(rows.length, 2);
});

test('spreads enough local attractions across every day when the division is uneven', () => {
  const result = buildTourismDraft([
    place('one', 48.85, 2.35),
    place('two', 48.86, 2.36),
    place('three', 48.87, 2.37),
    place('four', 48.88, 2.38),
  ], [{ id: 1 }, { id: 2 }, { id: 3 }]);

  assert.equal(result.order.length, 4);
  assert.deepEqual(new Set(Object.values(result.assignment)), new Set([1, 2, 3]));
  assert.deepEqual([1, 2, 3].map((dayId) => Object.values(result.assignment).filter((id) => id === dayId).length), [1, 2, 1]);
});

test('creates inclusive temporary days for a trip with no saved itinerary', () => {
  const days = buildTemporaryTripDays('2026-11-20', '2026-11-24');
  assert.equal(days.length, 5);
  assert.equal(days[0].fecha, '2026-11-20');
  assert.equal(days[4].fecha, '2026-11-24');
  assert.equal(days[0].temporal, true);
  assert.deepEqual(buildTemporaryTripDays('2026-11-24', '2026-11-20'), []);
  assert.deepEqual(buildTemporaryTripDays('bad', '2026-11-24'), []);
});

test('uses saved itinerary days when present and temporal days for first-time trips', () => {
  const saved = [{ id: 51, fecha: '2026-11-20', actividades: [{ id: 8 }] }];
  assert.equal(resolveTripPlanningDays({ dias: saved }, '2026-11-20', '2026-11-21'), saved);
  const firstTime = resolveTripPlanningDays(null, '2026-11-20', '2026-11-21');
  assert.equal(firstTime.length, 2);
  assert.equal(firstTime[0].temporal, true);
});

test('combines catalog and external candidates without duplicate places or lodging and food', () => {
  const combined = combineTourismCandidates([
    { id: 'osm-1', nombre: 'Louvre', categoria: 'Museum', latitud: 48.8606, longitud: 2.3376 },
    { id: 'food-1', nombre: 'Cafe near Louvre', categoria: 'Cafe', latitud: 48.86, longitud: 2.33 },
    { id: 'hotel-1', nombre: 'Grand Hotel', categoria: 'Hotel', latitud: 48.86, longitud: 2.33 },
  ], [{ actividad: { id: 10, nombre: 'Louvre', categoria: 'Museum', latitud: 48.8606, longitud: 2.3370 } }]);
  assert.equal(combined.length, 1);
  assert.equal(combined[0]._sourceKind, 'external');
});

test('builds a useful bounded draft with category and geographic signals, without repeating a category', () => {
  const places = [
    { id: 'museum-1', nombre: 'Museum A', categoria: 'Museum', latitud: 48.85, longitud: 2.34 },
    { id: 'museum-2', nombre: 'Museum B', categoria: 'Museum', latitud: 48.86, longitud: 2.35 },
    { id: 'museum-3', nombre: 'Museum C', categoria: 'Museum', latitud: 48.87, longitud: 2.36 },
    { id: 'park', nombre: 'Garden', categoria: 'Garden', latitud: 48.9, longitud: 2.4 },
    { id: 'monument', nombre: 'Monument', categoria: 'Monument', latitud: 48.88, longitud: 2.38 },
  ];
  const proposal = buildTourismDraft(places, [{ id: 1 }, { id: 2 }]);
  assert.ok(proposal.order.length <= 8);
  assert.ok(proposal.order.length >= 4);
  assert.equal(proposal.order.filter((id) => id.startsWith('museum-')).length, 2,
    'category variety can rank another category ahead without imposing a global exclusion cap');
  assert.equal(new Set(proposal.order).size, proposal.order.length);
});

test('distributes a full candidate set over 3, 5, 7, 10 and 15 days with no five-day cap', () => {
  for (const dayCount of [3, 5, 7, 10, 15]) {
    const days = Array.from({ length: dayCount }, (_, index) => ({ id: `temporary:${index}`, fecha: `2026-11-${String(index + 1).padStart(2, '0')}` }));
    const candidates = Array.from({ length: getSuggestedActivityCapacity(days) + 4 }, (_, index) => ({
      id: `place-${index}`, nombre: `Attraction ${index}`, categoria: 'Tourist attraction',
      latitud: 48.80 + (index % 12) * 0.005, longitud: 2.25 + Math.floor(index / 12) * 0.01,
    }));
    const proposal = buildTourismDraft(candidates, days);
    const counts = summarizeDraftDays(buildDraftItinerary({
      days, external: candidates, externalAssignments: proposal.assignment, externalOrder: proposal.order,
    }));
    assert.equal(proposal.order.length, getSuggestedActivityCapacity(days), `selected capacity for ${dayCount} days`);
    assert.ok(counts.every((day) => day.count > 0), `all ${dayCount} days receive candidates`);
    assert.equal(new Set(proposal.order).size, proposal.order.length);
    assert.ok(Math.max(...counts.map((day) => day.count)) <= 4);
    assert.ok(Math.min(...counts.map((day) => day.count)) >= 2);
  }
});

test('a five-day package drafts only strong tourist evidence from a noisy 89-result provider page', () => {
  const days = buildTemporaryTripDays('2026-11-16', '2026-11-20');
  const noisy = Array.from({ length: 89 }, (_, index) => ({ id: `decorative-${index}`,
    nombre: `Character statue ${index}`, categoria: 'tourism.attraction', senalTuristica: 36,
    latitud: 51.508 + (index % 4) * 0.0002, longitud: -0.128 + (index % 4) * 0.0002 }));
  const principals = Array.from({ length: 10 }, (_, index) => ({ id: `landmark-${index}`,
    nombre: `Verified landmark ${index}`, categoria: index % 2 ? 'Museum' : 'Monument', senalTuristica: 88,
    latitud: 51.48 + Math.floor(index / 2) * 0.018 + (index % 2) * 0.001,
    longitud: -0.19 + Math.floor(index / 2) * 0.028 + (index % 2) * 0.001 }));
  const candidates = combineTourismCandidates([...noisy, ...principals], []);
  const proposal = buildTourismDraft(candidates, days);
  const draft = buildDraftItinerary({ days, external: candidates, externalAssignments: proposal.assignment, externalOrder: proposal.order });

  assert.equal(proposal.qualityExcluded, 89);
  assert.equal(proposal.order.length, 10);
  assert.ok(summarizeDraftDays(draft).every((day) => day.count > 0));
  assert.equal(new Set(proposal.order).size, 10);
  assert.ok(proposal.order.every((id) => id.startsWith('external:landmark-')));
});

test('runs the same quality and day-allocation rules on Colombian and European destination fixtures', () => {
  // Fixture-only records exercise the common planner contract; coordinates below are synthetic
  // city-local clusters and are not provider results or production destination data.
  const destinations = [
    ['Bogotá', ['Museo del Oro', 'Monserrate', 'La Candelaria']],
    ['Medellín', ['Museo de Antioquia', 'Jardín Botánico', 'Pueblito Paisa']],
    ['Cartagena', ['Castillo San Felipe', 'Centro Histórico Amurallado', 'Getsemaní']],
    ['Santa Marta', ['Quinta de San Pedro Alejandrino', 'Centro Histórico', 'Playa El Rodadero']],
    ['Salento', ['Calle Real', 'Valle de Cocora', 'Mirador de Salento']],
    ['Madrid', ['Museo del Prado', 'Palacio Real', 'Parque del Retiro']],
    ['París', ['Torre Eiffel', 'Museo del Louvre', 'Catedral de Notre-Dame']],
    ['Londres', ['Westminster Abbey', 'Tower Bridge', 'British Museum']],
  ];
  destinations.forEach(([destination, names], cityIndex) => {
    const days = Array.from({ length: 3 }, (_, index) => ({ id: `temporary:${destination}:${index}`, fecha: `2027-05-0${index + 1}` }));
    const candidates = names.map((nombre, index) => ({
      id: `${destination}:main:${index}`, nombre,
      categoria: ['Museum', 'Monument', 'Historic district'][index],
      latitud: 4 + cityIndex * 3 + Math.floor(index / 2) * 0.025,
      longitud: -20 + cityIndex * 4 + (index % 2) * 0.03,
      senalTuristica: 84 + index, clasificacionTuristica: 'PRINCIPAL',
      elegibleItinerario: true, alcance: 'LOCAL_ATTRACTION',
    }));
    candidates.push({ id: `${destination}:statue`, nombre: 'Incidental statue', categoria: 'Statue',
      latitud: candidates[0].latitud + 0.001, longitud: candidates[0].longitud + 0.001,
      senalTuristica: 30, clasificacionTuristica: 'EXPLORABLE', elegibleItinerario: false,
      alcance: 'LOCAL_ATTRACTION' });
    const proposal = buildTourismDraft(combineTourismCandidates(candidates, []), days);
    const draft = buildDraftItinerary({ days, external: candidates,
      externalAssignments: Object.fromEntries(Object.entries(proposal.assignment)
        .map(([id, dayId]) => [id.slice('external:'.length), dayId])),
      externalOrder: proposal.order.map((id) => id.slice('external:'.length)) });
    assert.equal(proposal.order.length, 3, `${destination} selects all three main fixture candidates`);
    assert.deepEqual(summarizeDraftDays(draft).map((day) => day.count), [1, 1, 1], `${destination} spreads visits over days`);
    assert.deepEqual(new Set(draft.flatMap((day) => day.actividades.map((activity) => activity.nombre))), new Set(names));
    assert.ok(draft.every((day) => !day.actividades.some((activity) => activity.nombre === 'Incidental statue')));
  });
});

test('rejects generic geographic articles and unqualified natural features without excluding documented visitor experiences', () => {
  const results = combineTourismCandidates([
    { id: 'city', nombre: 'Cartagena, Colombia', categoria: 'administrative boundary city', latitud: 10.4, longitud: -75.5, senalTuristica: 88 },
    { id: 'mangle', nombre: 'Mangle', categoria: 'natural feature', latitud: 10.4, longitud: -75.5, senalTuristica: 60 },
    { id: 'community', nombre: 'Resguardo indígena Senú', categoria: 'indigenous territory', latitud: 10.4, longitud: -75.5, senalTuristica: 70 },
    { id: 'reserve', nombre: 'Mangrove boardwalk visitor reserve', categoria: 'nature reserve visitor centre', latitud: 10.41, longitud: -75.49, senalTuristica: 72 },
  ], []);
  assert.deepEqual(results.map((place) => place.nombre), ['Mangrove boardwalk visitor reserve']);
});

test('merges provider name variants only when both identity text and location support a match', () => {
  const nearby = combineTourismCandidates([
    { id: 'geo:1', nombre: 'Museo del Oro', categoria: 'museum', latitud: 4.601, longitud: -74.072, senalTuristica: 88 },
    { id: 'wiki:2', nombre: 'Museo del Oro Zenú', categoria: 'museum', latitud: 4.6012, longitud: -74.0721, senalTuristica: 88 },
    { id: 'wiki:3', nombre: 'Museo del Oro Zenú', categoria: 'museum', latitud: 10.42, longitud: -75.55, senalTuristica: 88 },
  ], []);
  assert.deepEqual(nearby.map((place) => place.id), ['external:geo:1', 'external:wiki:3']);
});

test('recommendation catalog search and pagination keep large provider lists secondary', () => {
  const places = Array.from({ length: 89 }, (_, index) => ({ nombre: `London place ${index}`, categoria: 'Museum',
    latitud: 51.5, longitud: -0.1, elegibleItinerario: true }));
  const filtered = filterRecommendationCatalog(places, 'place 8');
  const page = paginateRecommendationCatalog(places, 2, 24);
  assert.equal(filtered.length, 10);
  assert.equal(page.items.length, 24);
  assert.equal(page.pageCount, 4);
  assert.equal(page.total, 89);
  assert.equal(paginateRecommendationCatalog(places, 99, 24).page, 3);
});

test('secondary recommendation catalog excludes provider results with weak tourism evidence', () => {
  const places = [
    { nombre: 'Character statue', senalTuristica: 36, latitud: 51.5, longitud: -0.1 },
    { nombre: 'Documented museum', senalTuristica: 88, latitud: 51.51, longitud: -0.11 },
    { nombre: 'Legacy response without score', latitud: 51.52, longitud: -0.12 },
  ];
  assert.deepEqual(filterRecommendationCatalog(places).map((place) => place.nombre), [
    'Documented museum', 'Legacy response without score',
  ]);
});

test('London regression: explorer signal is not itinerary eligibility; local candidates plan when scope contract agrees', () => {
  const days = buildTemporaryTripDays('2027-01-15', '2027-01-19');
  const regional = Array.from({ length: 64 }, (_, index) => ({
    id: `london-${index}`, nombre: `London attraction ${index}`, categoria: 'Museum',
    senalTuristica: 88, clasificacionTuristica: 'PRINCIPAL', alcance: 'REGIONAL_ATTRACTION',
    elegibleItinerario: false, razonNoElegible: 'El proveedor lo clasifica como regional',
    latitud: 51.48 + Math.floor(index / 8) * 0.01, longitud: -0.2 + (index % 8) * 0.02,
  }));
  const summary = summarizeRecommendationCandidates(regional);
  assert.equal(summary.counts.received, 64);
  assert.equal(summary.counts.principal, 64);
  assert.equal(summary.counts.eligible, 0);
  assert.equal(filterRecommendationCatalog(regional).length, 0,
    'explorer must not claim that ineligible records are itinerary ready');
  const blocked = buildTourismDraft(regional, days);
  assert.equal(blocked.order.length, 0);
  assert.equal(blocked.regionalExcluded, 64);

  const local = regional.map((place) => ({ ...place, alcance: 'LOCAL_ATTRACTION', elegibleItinerario: true,
    razonNoElegible: null }));
  const eligibleSummary = summarizeRecommendationCandidates(local);
  const proposal = buildTourismDraft(local, days);
  const visible = summarizeDraftDays(buildDraftItinerary({
    days, external: local, externalAssignments: proposal.assignment, externalOrder: proposal.order,
  }));
  assert.equal(eligibleSummary.counts.eligible, 64);
  assert.equal(proposal.order.length, 16);
  assert.ok(visible.every((day) => day.count > 0));
  assert.equal(new Set(proposal.order).size, proposal.order.length);
});

test('Barcelona regression: 28 eligible places recover from 21 orphaned old-day assignments and populate six days', () => {
  const days = buildTemporaryTripDays('2026-11-23', '2026-11-28');
  const external = Array.from({ length: 28 }, (_, index) => ({
    id: `barcelona:${index}`, nombre: `Barcelona documented attraction ${index}`,
    categoria: 'Museum', alcance: 'LOCAL_ATTRACTION', clasificacionTuristica: 'COMPLEMENTARIO',
    senalTuristica: 42, elegibleItinerario: true, razonNoElegible: null,
    latitud: 41.36 + Math.floor(index / 7) * 0.012, longitud: 2.12 + (index % 7) * 0.014,
  }));
  const staleAssignments = Object.fromEntries(external.slice(0, 21)
    .map((place) => [place.id, 'temporary:2026-11-18']));
  const before = reconcileDraftAssignments({ days, external, externalAssignments: staleAssignments });
  assert.equal(before.orphanCount, 21);
  assert.equal(Object.keys(before.external).length, 0);

  const normalized = combineTourismCandidates(external, []);
  assert.equal(normalized.length, 28);
  const eligible = excludeAssignedTourismCandidates(normalized, { externalIds: Object.keys(before.external) });
  const proposal = buildTourismDraft(eligible, days);
  assert.ok(proposal.selectedCount > 0);
  assert.equal(proposal.assignedCount, proposal.selectedCount);
  assert.equal(new Set(Object.values(proposal.assignment).map(String)).size, 6);

  const reassigned = Object.fromEntries(Object.entries(proposal.assignment)
    .filter(([id]) => id.startsWith('external:')).map(([id, dayId]) => [id.slice('external:'.length), dayId]));
  const merged = { ...staleAssignments, ...reassigned }; // The new mapping must override an obsolete day ID.
  const after = reconcileDraftAssignments({ days, external, externalAssignments: merged });
  const draft = buildDraftItinerary({ days, external, externalAssignments: after.external, externalOrder: proposal.order });
  const counts = summarizeDraftDays(draft).map((day) => day.count);
  assert.equal(counts.reduce((sum, count) => sum + count, 0), proposal.assignedCount);
  assert.ok(counts.every((count) => count > 0));
  assert.equal(after.orphanCount, Object.keys(staleAssignments).filter((id) => !Object.hasOwn(reassigned, id)).length);
});

test('draft reconciliation preserves old references but excludes missing IDs and old dates from savable assignments', () => {
  const days = buildTemporaryTripDays('2026-11-23', '2026-11-28');
  const external = [
    { id: 'present', nombre: 'Current museum', latitud: 41.38, longitud: 2.17 },
    { id: 'presentOnOldDate', nombre: 'Another museum', latitud: 41.4, longitud: 2.19 },
  ];
  const state = reconcileDraftAssignments({ days, external, externalAssignments: {
    present: days[2].id, missingCandidate: days[0].id, presentOnOldDate: 'temporary:2026-11-18',
  }, manual: [{ id: 'manual:old', dayId: 'temporary:2026-11-18' }] });
  assert.deepEqual(state.external, { present: days[2].id });
  assert.equal(state.orphanCount, 3);
  assert.deepEqual(state.orphanedExternal.map((item) => item.reason), [
    'El candidato ya no está cargado', 'El día guardado ya no existe en las fechas actuales',
  ]);
  assert.equal(state.orphanedManual[0].reason, 'La actividad manual apunta a un día anterior');
});

test('normalizes a Paris five-day draft so three day-one activities count in summary and export model', () => {
  const days = buildTemporaryTripDays('2026-11-20', '2026-11-24');
  const external = ['Eiffel Tower', 'Louvre Museum', 'Notre-Dame'].map((nombre, index) => ({
    id: `wikidata:Q${index + 1}`, nombre, categoria: 'Monument', latitud: 48.85, longitud: 2.35 + index * 0.001,
    proveedor: 'Wikidata', precio: null,
  }));
  const assignments = Object.fromEntries(external.map((place) => [place.id, days[0].id]));
  const unified = buildDraftItinerary({ days, savedDays: [], external, externalAssignments: assignments });
  const summary = summarizeDraftDays(unified);
  assert.deepEqual(summary.map((day) => day.count), [3, 0, 0, 0, 0]);
  assert.deepEqual(unified[0].actividades.map((activity) => activity.nombre), external.map((place) => place.nombre));
  assert.equal(unified.length, 5);
  assert.equal(unified.flatMap((day) => day.actividades).length, 3);
});

test('completes empty trip days without selecting the three external activities already assigned to day one', () => {
  const days = buildTemporaryTripDays('2026-11-20', '2026-11-24');
  const external = Array.from({ length: 15 }, (_, index) => ({
    id: `candidate-${index}`, nombre: `Paris attraction ${index}`, categoria: 'Tourist attraction',
    latitud: 48.80 + index * 0.002, longitud: 2.25 + index * 0.002,
  }));
  const combined = combineTourismCandidates(external, []);
  const alreadyAssigned = combined.slice(0, 3);
  const newCandidates = excludeAssignedTourismCandidates(combined, { externalIds: alreadyAssigned.map((place) => place.id.slice(9)) });
  const emptyDays = days.slice(1);
  const proposal = buildTourismDraft(newCandidates, emptyDays);
  const newAssignments = Object.fromEntries(Object.entries(proposal.assignment)
    .map(([id, dayId]) => [id.slice('external:'.length), dayId]));
  const draft = buildDraftItinerary({
    days,
    external,
    externalAssignments: {
      ...Object.fromEntries(alreadyAssigned.map((place) => [place.id.slice(9), days[0].id])),
      ...newAssignments,
    },
  });
  assert.equal(Object.keys(proposal.assignment).length, getSuggestedActivityCapacity(emptyDays));
  assert.deepEqual(summarizeDraftDays(draft).map((day) => day.count), [3, 2, 4, 4, 2]);
  assert.ok(proposal.order.every((id) => !alreadyAssigned.some((place) => place.id === id)));
});

test('builds a printable itinerary from a local unsaved draft', () => {
  const days = [{ id: 'temporary:2026-11-20', fecha: '2026-11-20', temporal: true, actividades: [] }];
  const draft = buildDraftItinerary({
    days,
    external: [{ id: 'wikidata:Q1', nombre: 'Museum', categoria: 'Museum', precio: null, urlFuente: 'https://example.org' }],
    externalAssignments: { 'wikidata:Q1': days[0].id },
    manual: [{ id: 'manual:1', dayId: days[0].id, request: { nombre: 'Dinner', costo: null } }],
  });
  assert.equal(draft.length, 1);
  assert.deepEqual(draft[0].actividades.map((activity) => activity.nombre), ['Museum', 'Dinner']);
  assert.equal(draft[0].actividades[0].costoEstado, 'UNKNOWN');
});
