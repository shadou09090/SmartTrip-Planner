const validPoint = (place) => place?.latitud != null && place?.latitud !== ''
  && place?.longitud != null && place?.longitud !== ''
  && Number.isFinite(Number(place?.latitud))
  && Number(place.latitud) >= -90 && Number(place.latitud) <= 90
  && Number.isFinite(Number(place?.longitud))
  && Number(place.longitud) >= -180 && Number(place.longitud) <= 180;

const distanceMeters = (first, second) => {
  const radians = (degrees) => degrees * Math.PI / 180;
  const lat1 = radians(Number(first.latitud));
  const lat2 = radians(Number(second.latitud));
  const dLat = lat2 - lat1;
  const dLon = radians(Number(second.longitud) - Number(first.longitud));
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
};

const normalize = (value) => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
  .toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();

function candidateCategory(place) {
  const category = normalize(place?.categoria || place?.category || '');
  return category && !['activity', 'actividad', 'atraccion', 'tourism attraction', 'poi'].includes(category)
    ? category.split(' ')[0] : `uncategorized:${place?.id || place?.nombre || place?.name || ''}`;
}

function isPrimaryCandidate(place) {
  // An explicit backend eligibility decision outranks its numeric score, while
  // the hard exclusions below still reject generic geography and incidental POIs.
  if (place?.elegibleItinerario !== true && Number.isFinite(place?._tourismSignal) && place._tourismSignal < 55) return false;
  const category = normalize(place?.categoria || place?.category || '');
  const role = normalize(place?.tourism_role || place?.tourismRole || '');
  const name = normalize(place?.nombre || place?.name || '');
  if (role === 'lodging' || /hotel|hostel|accommodation|guest house|parking|car park|office/.test(`${category} ${name}`)) return false;
  if (role === 'gastronomy' || /restaurant|cafe|coffee|bar|fast food|catering/.test(category)) return false;
  const importance = normalize(place?._importance || place?.clasificacionTuristica || '');
  const evidence = Array.isArray(place?._tourismEvidence) ? place._tourismEvidence.join(' ') : '';
  const explicitTourismEvidence = /tourism sights|tourist attraction|tourism\.attraction|landmark|heritage site|monument|national park|nature reserve|visitor centre|visitor center|tourism=/.test(normalize(evidence));
  const sourceTags = normalize(JSON.stringify(place?.etiquetas || place?.tags || {}));
  const accessSensitivePlace = /resguardo indigena|territorio indigena|indigenous reserve|indigenous territory|community land/.test(`${category} ${name}`);
  if (accessSensitivePlace && !explicitTourismEvidence) return false;
  const administrativeFeature = /administrative boundary|municipality|municipio|country|province|department|city|locality|settlement/.test(category)
    || /\b(place city|place municipality|boundary administrative|admin level)\b/.test(sourceTags);
  if (administrativeFeature && !explicitTourismEvidence) return false;
  const genericNatureName = /^(?:mangle|manglar|mangrove|wetland|marsh|swamp|forest|bosque|river|rio|island|isla|mountain|montana|landscape|paisaje)$/.test(name);
  const specificNatureExperience = /national park|nature reserve|protected area|visitor centre|visitor center|trail|boardwalk|tourism sights|tourist attraction|heritage site/.test(normalize(`${category} ${evidence} ${sourceTags}`));
  if (genericNatureName && !specificNatureExperience) return false;
  const incidental = /statue|sculpture|plaque|bust|memorial|artwork/.test(`${category} ${name}`);
  if (incidental && importance !== 'principal' && !explicitTourismEvidence) return false;
  if (/^(?:pa|poi|node|way|relation) ?\d{2,}$/.test(name)) return false;
  return true;
}

/** Shared quality gate for itinerary candidates and the primary map recommendation feed. */
export function hasSpecificTourismIdentity(place) {
  if (!place || typeof place !== 'object') return false;
  const signal = place._tourismSignal ?? place.senalTuristica;
  return isPrimaryCandidate({
    ...place,
    _tourismSignal: signal == null || signal === '' ? undefined : Number(signal),
    _importance: place._importance || place.clasificacionTuristica || null,
    _tourismEvidence: place._tourismEvidence || place.evidenciaTuristica || place.tourismEvidence || [],
  });
}

export function isPlannerEligibleCandidate(place) {
  if (!place || typeof place !== 'object') return false;
  const normalized = {
    ...place,
    _tourismSignal: place._tourismSignal ?? (place.senalTuristica == null || place.senalTuristica === '' ? undefined : Number(place.senalTuristica)),
    _importance: place._importance || place.clasificacionTuristica || null,
    _tourismEvidence: place._tourismEvidence || place.evidenciaTuristica || place.tourismEvidence || [],
  };
  return validPoint(normalized) && !isRegionalTourismCandidate(normalized)
    && normalized.elegibleItinerario !== false && isPrimaryCandidate(normalized);
}

export function summarizeRecommendationCandidates(places) {
  const rows = Array.isArray(places) ? places.filter((place) => place && typeof place === 'object') : [];
  const counts = { received: rows.length, withCoordinates: 0, principal: 0, complementary: 0,
    explorable: 0, backendEligible: 0, eligible: 0, regional: 0, discarded: 0 };
  const reasons = {};
  for (const place of rows) {
    if (validPoint(place)) counts.withCoordinates += 1;
    const scope = String(place.alcance || '').toUpperCase();
    if (['REGIONAL_ATTRACTION', 'REGIONAL_CANDIDATE', 'EXCURSION_OPTIONAL'].includes(scope)) counts.regional += 1;
    const tier = String(place.clasificacionTuristica || '').toUpperCase();
    if (tier === 'PRINCIPAL') counts.principal += 1;
    else if (tier === 'COMPLEMENTARIO') counts.complementary += 1;
    else counts.explorable += 1;
    if (place.elegibleItinerario === true) counts.backendEligible += 1;
    const eligible = isPlannerEligibleCandidate({
      ...place,
      _tourismSignal: place.senalTuristica == null || place.senalTuristica === '' ? undefined : Number(place.senalTuristica),
      _importance: place.clasificacionTuristica || place._importance,
      _tourismEvidence: place.evidenciaTuristica || place._tourismEvidence || [],
    });
    if (eligible) counts.eligible += 1;
    else {
      counts.discarded += 1;
      const reason = place.razonNoElegible || (!validPoint(place) ? 'Coordenadas no válidas o ausentes'
        : isRegionalTourismCandidate(place) ? 'Alcance regional u opcional' : 'No cumple criterios de elegibilidad');
      reasons[reason] = (reasons[reason] || 0) + 1;
    }
  }
  return { counts, reasons };
}

function candidateInterestCount(place) {
  return Array.isArray(place?.interesesCoincidentes) ? place.interesesCoincidentes.length
    : Array.isArray(place?.matchedInterests) ? place.matchedInterests.length : 0;
}

function sameCandidate(first, second) {
  const firstName = normalize(first.nombre || first.name);
  const secondName = normalize(second.nombre || second.name);
  if (firstName === secondName) {
    if (!validPoint(first) || !validPoint(second)) return true;
    return distanceMeters(first, second) <= 100;
  }
  if (!validPoint(first) || !validPoint(second)) return false;
  const distance = distanceMeters(first, second);
  if (distance > 75) return false;
  // Name variants such as "Museo del Oro" / "Museo del Oro Zenú" only merge
  // when the providers also place them at nearly the same point.
  const firstTokens = new Set(firstName.split(' ').filter((token) => token.length > 2));
  const secondTokens = new Set(secondName.split(' ').filter((token) => token.length > 2));
  const shared = [...firstTokens].filter((token) => secondTokens.has(token)).length;
  const similarity = shared / Math.max(1, Math.min(firstTokens.size, secondTokens.size));
  return firstName.includes(secondName) || secondName.includes(firstName) || similarity >= 0.8;
}

/** Combines ranked external and catalog activities without making network calls or persisting. */
export function combineTourismCandidates(external, catalog) {
  const candidates = [
    ...(Array.isArray(external) ? external.map((place) => ({ ...place, id: `external:${place.id}`,
      nombre: place.nombre || place.name, latitud: firstCoordinate(place.latitud, place.latitude, place.lat, place.coordinates?.[1]),
      longitud: firstCoordinate(place.longitud, place.longitude, place.lon, place.coordinates?.[0]),
      _sourceKind: 'external',
      _tourismSignal: place.senalTuristica == null || place.senalTuristica === ''
        ? undefined : (Number.isFinite(Number(place.senalTuristica)) ? Number(place.senalTuristica) : undefined),
      _importance: place.clasificacionTuristica || null,
      _tourismEvidence: place.evidenciaTuristica || place.tourismEvidence || [],
      _eligibilityReason: place.razonNoElegible || null,
      elegibleItinerario: place.elegibleItinerario,
      _budgetCost: place.moneda === 'COP' && place.precio != null ? Number(place.precio) : null })) : []),
    ...(Array.isArray(catalog) ? catalog.map((item) => ({
      ...item.actividad,
      id: `catalog:${item.actividad?.id}`,
      nombre: item.actividad?.nombre,
      latitud: item.actividad?.latitud,
      longitud: item.actividad?.longitud,
      categoria: item.actividad?.categoria || 'Actividad del catálogo',
      _budgetCost: item.actividad?.costo == null ? null : Number(item.actividad.costo),
      interesesCoincidentes: item.interesesCoincidentes || [],
      _sourceKind: 'catalog',
    })) : []),
  ].filter((place) => place.nombre && place.id !== 'catalog:undefined');
  const qualityExcluded = candidates.filter((place) => !isPrimaryCandidate(place)).length;
  const eligible = candidates.filter(isPrimaryCandidate);
  const result = [];
  for (const candidate of eligible) {
    if (result.some((existing) => existing.id === candidate.id || sameCandidate(existing, candidate))) continue;
    result.push(candidate);
  }
  // Carry rejection diagnostics without exposing low-confidence records as selectable.
  result.qualityExcluded = qualityExcluded;
  result.contractExcluded = candidates.filter((place) => place.elegibleItinerario === false
    && !isRegionalTourismCandidate(place) && validPoint(place)).length;
  result.regionalExcluded = candidates.filter(isRegionalTourismCandidate).length;
  return result;
}

function firstCoordinate(...values) {
  return values.find((value) => value != null && value !== '');
}

export function excludeAssignedTourismCandidates(candidates, { externalIds = [], catalogIds = [] } = {}) {
  const assignedExternal = new Set(externalIds.map(String));
  const assignedCatalog = new Set(catalogIds.map(String));
  return (Array.isArray(candidates) ? candidates : []).filter((candidate) => {
    const id = String(candidate?.id || '');
    if (id.startsWith('external:')) return !assignedExternal.has(id.slice('external:'.length));
    if (id.startsWith('catalog:')) return !assignedCatalog.has(id.slice('catalog:'.length));
    return true;
  });
}

/** Returns an assignment map in requested order, ignoring stale or repeated IDs. */
export function orderedAssignments(assignments, order = []) {
  const source = assignments && typeof assignments === 'object' ? assignments : {};
  const seen = new Set();
  const ids = [];
  for (const value of Array.isArray(order) ? order : []) {
    const id = String(value);
    if (!seen.has(id) && Object.hasOwn(source, id)) { seen.add(id); ids.push(id); }
  }
  for (const value of Object.keys(source)) {
    const id = String(value);
    if (!seen.has(id)) { seen.add(id); ids.push(id); }
  }
  return ids.map((id) => [id, source[id]]);
}

export const isRegionalTourismCandidate = (place) => ['REGIONAL_ATTRACTION', 'REGIONAL_CANDIDATE', 'EXCURSION_OPTIONAL']
  .includes(place?.alcance);

export const tourismScopeLabel = (scope) => ({
  LOCAL_ATTRACTION: 'Atracción local',
  CITY: 'Atracción local',
  REGIONAL_ATTRACTION: 'Atracción regional',
  REGIONAL_CANDIDATE: 'Atracción regional',
  EXCURSION_OPTIONAL: 'Excursión opcional · traslado por validar',
})[scope] || 'Alcance geográfico no indicado';

export function buildTemporaryTripDays(startDate, endDate, maxDays = 90) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(startDate || '') || !/^\d{4}-\d{2}-\d{2}$/.test(endDate || '')) return [];
  const start = new Date(`${startDate}T00:00:00Z`);
  const end = new Date(`${endDate}T00:00:00Z`);
  if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || end < start) return [];
  const count = Math.floor((end - start) / 86_400_000) + 1;
  if (count > maxDays) return [];
  return Array.from({ length: count }, (_, index) => {
    const date = new Date(start.getTime() + index * 86_400_000).toISOString().slice(0, 10);
    return { id: `temporary:${date}`, fecha: date, actividades: [], temporal: true };
  });
}

export function resolveTripPlanningDays(itinerary, startDate, endDate) {
  if (Array.isArray(itinerary?.dias) && itinerary.dias.length) return itinerary.dias;
  return buildTemporaryTripDays(startDate, endDate);
}

/** Makes a local draft only. It does not claim route time, opening hours, or availability. */
export function buildTourismDraft(recommendations, days, { budgetRemaining = null } = {}) {
  const validDays = Array.isArray(days) ? days.filter((day) => day?.id != null) : [];
  const unique = new Map();
  let qualityExcluded = Number(recommendations?.qualityExcluded) || 0;
  let contractExcluded = Number(recommendations?.contractExcluded) || 0;
  let regionalExcluded = 0;
  let coordinateExcluded = 0;
  const discardedReasons = {};
  const discard = (reason) => { discardedReasons[reason] = (discardedReasons[reason] || 0) + 1; };
  for (const place of Array.isArray(recommendations) ? recommendations : []) {
    if (!place?.id) { discard('Identificador ausente'); continue; }
    if (isRegionalTourismCandidate(place)) { regionalExcluded += 1; discard('Alcance regional u opcional'); continue; }
    if (!validPoint(place)) { coordinateExcluded += 1; discard('Coordenadas inválidas o ausentes'); continue; }
    if (place.elegibleItinerario === false) { contractExcluded += 1; discard(place._eligibilityReason || 'Backend marcó candidato como no elegible'); continue; }
    if (!isPrimaryCandidate(place)) { qualityExcluded += 1; discard('Evidencia turística insuficiente o categoría excluida'); continue; }
    if (!unique.has(place.id)) unique.set(place.id, place);
    else discard('Duplicado');
  }
  const remaining = [...unique.values()];
  if (!validDays.length || !remaining.length) {
    return { assignment: {}, order: [], receivedCount: Array.isArray(recommendations) ? recommendations.length : 0,
      eligibleCount: unique.size, selectedCount: 0, assignedCount: 0, discardedReasons,
      regionalExcluded, contractExcluded, qualityExcluded, coordinateExcluded };
  }

  // Arrival and departure days are deliberately lighter. Other days have a target of
  // up to four primary visits, but the actual count is bounded by verified candidates.
  // There is no global category cap: it previously caused a city with generic "attraction"
  // categories to be limited to two activities total, regardless of trip length.
  const selectionLimit = Math.min(remaining.length, getSuggestedActivityCapacity(validDays));
  const selected = [];
  const categoryCounts = new Map();
  let selectedKnownCost = 0;
  while (selected.length < selectionLimit) {
    let bestIndex = -1;
    let bestScore = Number.NEGATIVE_INFINITY;
    remaining.forEach((candidate, index) => {
      const category = candidateCategory(candidate);
      const nearestKm = selected.length ? Math.min(...selected.map((item) => distanceMeters(candidate, item))) / 1000 : 0;
      const categoryBonus = categoryCounts.has(category) ? 0 : 8;
      const rankPenalty = index * 0.15;
      const cost = candidate._budgetCost == null ? Number.NaN : Number(candidate._budgetCost);
      const hasKnownCost = Number.isFinite(cost) && cost >= 0;
      const hasBudget = budgetRemaining != null && budgetRemaining !== '' && Number.isFinite(Number(budgetRemaining));
      const remainingBudget = hasBudget ? Number(budgetRemaining) - selectedKnownCost : null;
      const budgetSignal = remainingBudget == null || !hasKnownCost ? 0 : cost <= remainingBudget ? 12 : -26;
      const providerSignal = Number.isFinite(candidate._tourismSignal) ? candidate._tourismSignal : 55;
      const importance = normalize(candidate._importance || candidate.clasificacionTuristica || '');
      const importanceBonus = importance === 'principal' ? 18 : importance === 'complementario' ? 5 : 0;
      const interestBonus = Math.min(10, candidateInterestCount(candidate) * 5);
      // Geographic spread should diversify similarly relevant places without making a
      // remote secondary stop outrank a documented principal attraction.
      const distanceBonus = Math.min(15, nearestKm * 0.4);
      const score = providerSignal + importanceBonus + interestBonus + categoryBonus
        + distanceBonus + budgetSignal - rankPenalty;
      if (score > bestScore) { bestScore = score; bestIndex = index; }
    });
    if (bestIndex < 0) break;
    const [picked] = remaining.splice(bestIndex, 1);
    selected.push(picked);
    if (picked._budgetCost != null && Number.isFinite(Number(picked._budgetCost)) && Number(picked._budgetCost) >= 0) selectedKnownCost += Number(picked._budgetCost);
    const category = candidateCategory(picked);
    categoryCounts.set(category, (categoryCounts.get(category) || 0) + 1);
  }

  // Cluster the selected places along short nearest-neighbour chains before splitting days.
  const unvisited = [...selected];
  const route = [unvisited.shift()];
  while (unvisited.length) {
    const current = route[route.length - 1];
    let nearestIndex = 0;
    for (let index = 1; index < unvisited.length; index += 1) {
      if (distanceMeters(current, unvisited[index]) < distanceMeters(current, unvisited[nearestIndex])) nearestIndex = index;
    }
    route.push(unvisited.splice(nearestIndex, 1)[0]);
  }

  const capacities = validDays.map((_, index) => dayCapacity(index, validDays.length));
  const dayLoads = validDays.map(() => 0);
  route.forEach((_, routeIndex) => {
    let selectedDay;
    if (routeIndex < validDays.length) {
      // If there are enough candidates, guarantee at least one for every date before
      // allocating a second visit anywhere.
      selectedDay = routeIndex;
    } else {
      const available = validDays.map((_, index) => index).filter((index) => dayLoads[index] < capacities[index]);
      selectedDay = available.sort((first, second) => {
        const fillDelta = dayLoads[first] / capacities[first] - dayLoads[second] / capacities[second];
        if (Math.abs(fillDelta) > 0.0001) return fillDelta;
        if (dayLoads[first] !== dayLoads[second]) return dayLoads[first] - dayLoads[second];
        const center = (validDays.length - 1) / 2;
        return Math.abs(first - center) - Math.abs(second - center) || first - second;
      })[0];
    }
    if (selectedDay == null) return;
    dayLoads[selectedDay] += 1;
  });
  // Allocate contiguous portions of the nearest-neighbour route to each day. This keeps
  // nearby stops together instead of round-robin interleaving distant clusters.
  const assignment = {};
  let routeIndex = 0;
  validDays.forEach((day, dayIndex) => {
    for (let index = 0; index < dayLoads[dayIndex]; index += 1) {
      assignment[route[routeIndex].id] = day.id;
      routeIndex += 1;
    }
  });
  const order = route.map((place) => place.id);
  return { assignment, order, receivedCount: Array.isArray(recommendations) ? recommendations.length : 0,
    eligibleCount: unique.size, selectedCount: order.length, assignedCount: Object.keys(assignment).length,
    discardedReasons, regionalExcluded, contractExcluded, qualityExcluded, coordinateExcluded };
}

function dayCapacity(index, dayCount) {
  if (dayCount === 1) return 4;
  if (index === 0 || index === dayCount - 1) return 2;
  return 4;
}

export function getSuggestedActivityCapacity(days) {
  const count = Array.isArray(days) ? days.length : 0;
  if (!count) return 0;
  return Array.from({ length: count }, (_, index) => dayCapacity(index, count)).reduce((sum, value) => sum + value, 0);
}

export function filterRecommendationCatalog(places, search = '') {
  const query = normalize(search);
  return (Array.isArray(places) ? places : []).filter((place) => !query || normalize([
    place?.nombre, place?.categoria, place?.ubicacion, place?.proveedor,
  ].filter(Boolean).join(' ')).includes(query))
    .filter(isPlannerEligibleCandidate);
}

/** Keep orphaned draft references recoverable, but exclude them from planning and persistence. */
export function reconcileDraftAssignments({ days = [], external = [], externalAssignments = {}, catalog = [], catalogAssignments = {}, manual = [] } = {}) {
  const dayIds = new Set((Array.isArray(days) ? days : []).map((day) => String(day?.id)).filter((id) => id !== 'undefined'));
  const externalIds = new Set((Array.isArray(external) ? external : []).map((place) => String(place?.id)).filter((id) => id !== 'undefined'));
  const catalogIds = new Set((Array.isArray(catalog) ? catalog : []).map((item) => String(item?.actividad?.id)).filter((id) => id !== 'undefined'));
  const reconcile = (assignments, candidates) => {
    const valid = {};
    const orphaned = [];
    for (const [id, dayId] of Object.entries(assignments && typeof assignments === 'object' ? assignments : {})) {
      if (!candidates.has(String(id))) orphaned.push({ id, dayId, reason: 'El candidato ya no está cargado' });
      else if (!dayIds.has(String(dayId))) orphaned.push({ id, dayId, reason: 'El día guardado ya no existe en las fechas actuales' });
      else valid[id] = dayId;
    }
    return { valid, orphaned };
  };
  const ext = reconcile(externalAssignments, externalIds);
  const cat = reconcile(catalogAssignments, catalogIds);
  const validManual = (Array.isArray(manual) ? manual : []).filter((item) => dayIds.has(String(item?.dayId)));
  const orphanManual = (Array.isArray(manual) ? manual : []).filter((item) => !dayIds.has(String(item?.dayId)))
    .map((item) => ({ id: item?.id, dayId: item?.dayId, reason: 'La actividad manual apunta a un día anterior' }));
  return { external: ext.valid, catalog: cat.valid, manual: validManual,
    orphanedExternal: ext.orphaned, orphanedCatalog: cat.orphaned, orphanedManual: orphanManual,
    orphanCount: ext.orphaned.length + cat.orphaned.length + orphanManual.length };
}

export function paginateRecommendationCatalog(places, page = 0, pageSize = 24) {
  const safeSize = Math.max(1, Math.min(50, Number(pageSize) || 24));
  const pageCount = Math.max(1, Math.ceil((Array.isArray(places) ? places.length : 0) / safeSize));
  const safePage = Math.max(0, Math.min(pageCount - 1, Number(page) || 0));
  return { items: (Array.isArray(places) ? places : []).slice(safePage * safeSize, (safePage + 1) * safeSize),
    page: safePage, pageCount, total: Array.isArray(places) ? places.length : 0 };
}

/** Returns the normalized activity counts used by the draft UI and export validation. */
export function summarizeDraftDays(days) {
  return (Array.isArray(days) ? days : []).map((day, index) => ({
    dayId: day?.id,
    date: day?.fecha || '',
    label: `Día ${index + 1}`,
    count: Array.isArray(day?.actividades) ? day.actividades.length : 0,
  }));
}

/** Counts persisted activity assignments whose day/order actually changed.
 * Day IDs are normalized because API and local drafts may represent them as
 * numbers or strings. Empty-day metadata changes are not saveable activities.
 */
export function countSavedAssignmentChanges(savedDays, draftDays) {
  const assignments = (days) => {
    const result = new Map();
    for (const day of Array.isArray(days) ? days : []) {
      for (const [index, activity] of (Array.isArray(day?.actividades) ? day.actividades : []).entries()) {
        if (activity?.id == null) continue;
        result.set(String(activity.id), {
          dayId: String(day.id),
          order: Number(activity.orden) || index + 1,
        });
      }
    }
    return result;
  };
  const saved = assignments(savedDays);
  const draft = assignments(draftDays);
  let changes = 0;
  for (const [activityId, original] of saved) {
    const current = draft.get(activityId);
    if (!current || current.dayId !== original.dayId || current.order !== original.order) changes += 1;
  }
  return changes;
}

export function buildDraftItinerary({ days, savedDays = [], external = [], externalAssignments = {},
  externalOrder = [], catalog = [], catalogAssignments = {}, manual = [] }) {
  const baseDays = Array.isArray(savedDays) && savedDays.length ? savedDays : days || [];
  const externalById = new Map((external || []).map((place) => [String(place.id), place]));
  const catalogById = new Map((catalog || []).map((item) => [String(item.actividad?.id), item.actividad]));
  return baseDays.map((day) => {
    const activities = [...(day.actividades || [])];
    const seen = new Set(activities.map((activity) => String(activity.id)));
    const assignedExternal = Object.entries(externalAssignments || {});
    const orderIndex = new Map(externalOrder.map((id, index) => [String(id), index]));
    assignedExternal.sort(([first], [second]) => (orderIndex.get(String(first)) ?? Number.MAX_SAFE_INTEGER)
      - (orderIndex.get(String(second)) ?? Number.MAX_SAFE_INTEGER));
    for (const [id, dayId] of assignedExternal) {
      if (String(dayId) !== String(day.id)) continue;
      const place = externalById.get(String(id));
      const normalizedId = `external:${id}`;
      if (!place || seen.has(normalizedId)) continue;
      seen.add(normalizedId);
      activities.push({ id: normalizedId, nombre: place.nombre, descripcion: place.descripcion,
        ubicacion: place.ubicacion, latitud: place.latitud, longitud: place.longitud, costo: place.precio ?? null,
        moneda: place.moneda || null, urlFuente: place.urlFuente || null, categoria: place.categoria || 'Atracción',
        fuente: place.proveedor || 'Fuente externa', costoEstado: place.precio == null ? 'UNKNOWN' : 'VERIFIED_QUOTE' });
    }
    for (const [id, dayId] of Object.entries(catalogAssignments || {})) {
      if (String(dayId) !== String(day.id)) continue;
      const activity = catalogById.get(String(id));
      const normalizedId = `catalog:${id}`;
      if (activity && !seen.has(normalizedId)) activities.push({ ...activity, id: normalizedId, fuente: 'Catálogo interno',
        costoEstado: activity.costo == null ? 'UNKNOWN' : 'VERIFIED_QUOTE' });
      if (activity) seen.add(normalizedId);
    }
    for (const item of manual || []) {
      if (String(item.dayId) !== String(day.id)) continue;
      if (seen.has(String(item.id))) continue;
      seen.add(String(item.id));
      activities.push({ ...item.request, id: item.id, fuente: 'Borrador manual',
        costoEstado: item.request.costo == null ? 'UNKNOWN' : 'MANUAL_ESTIMATE' });
    }
    return { ...day, actividades: activities.map((activity, index) => ({ ...activity, orden: activity.orden ?? index + 1 })) };
  });
}
