import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ArrowDown, ArrowUp, CalendarDays, MapPin, Plus, Sparkles, Trash2 } from 'lucide-react';
import { apiRequest } from '../lib/api';
import { buildItineraryPdf, downloadItineraryPdf } from '../lib/itineraryPrint';
import { buildDraftItinerary, buildTourismDraft, combineTourismCandidates, countSavedAssignmentChanges, excludeAssignedTourismCandidates, filterRecommendationCatalog, isRegionalTourismCandidate, orderedAssignments, paginateRecommendationCatalog, reconcileDraftAssignments, resolveTripPlanningDays, summarizeDraftDays, summarizeRecommendationCandidates, tourismScopeLabel } from '../lib/tourismItineraryDraft';
import { partitionSettledResults } from '../lib/settledSections';
import { itinerarySignature, readItineraryDraft, removeItineraryDraft, writeItineraryDraft } from '../lib/tripItineraryDraftStorage';
import { decideWorkspaceHydration } from '../lib/packageWorkspaceSync';
import { buildPackageSavePayload, classifyPackageSnapshot, countPackageActivities, packageContentSignature, packageIdempotencyKey, packageSaveFailureMessage, restorePackageExpenses } from '../lib/packagePersistence';
import TripGlobalBudget from './TripGlobalBudget';
import { useTripPackageWorkspace, useWorkspaceState } from './TripPackageWorkspace';

export default function GeneradorItinerario({ token, trip, userId, view = 'itinerary', onSummaryChange, onNavigateTab, autoCreateDraft = false }) {
  const [itinerario, setItinerario] = useState(null);
  const [draftDays, setDraftDaysRaw] = useWorkspaceState('draftDays', []);
  const [globalBudgetScenario, setGlobalBudgetScenario] = useState(null);
  const [persistedPackageExpenses, setPersistedPackageExpenses] = useState([]);
  const [persistedPackageTravelers, setPersistedPackageTravelers] = useState(null);
  const [packageSyncStatus, setPackageSyncStatus] = useState('loading');
  const [packageConflict, setPackageConflict] = useState(null);
  const [lodging, setLodging] = useState(null);
  const [availableActivities, setAvailableActivities] = useState([]);
  const [recommendations, setRecommendations] = useState({ requiereSeleccionarIntereses: false, actividades: [] });
  const [interestCatalog, setInterestCatalog] = useState([]);
  const [selectedInterests, setSelectedInterests] = useState(trip.interests || []);
  const [customInterest, setCustomInterest] = useState('');
  const [selectedDays, setSelectedDays] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [savingInterests, setSavingInterests] = useState(false);
  const [addingActivity, setAddingActivity] = useState(null);
  const [updatingAssignment, setUpdatingAssignment] = useState(null);
  const [error, setError] = useState('');
  const [recommendationLoadError, setRecommendationLoadError] = useState('');
  const [notice, setNotice] = useState('');
  const [activityForms, setActivityForms] = useState({});
  const [draftExternal, setDraftExternalRaw] = useWorkspaceState('draftExternal', {});
  const [draftExternalOrder, setDraftExternalOrderRaw] = useWorkspaceState('draftExternalOrder', []);
  const [draftCatalog, setDraftCatalogRaw] = useWorkspaceState('draftCatalog', {});
  const [draftManual, setDraftManualRaw] = useWorkspaceState('draftManual', []);
  const [savingDraft, setSavingDraft] = useState(false);
  const [exportingPdf, setExportingPdf] = useState(false);
  const [generationPreview, setGenerationPreview] = useState(false);
  const [failedExternalImages, setFailedExternalImages] = useState({});
  const [confirmedRegionalPlaces, setConfirmedRegionalPlaces] = useState({});
  const [autoDraftStatus, setAutoDraftStatus] = useState('idle');
  const [generationDiagnostics, setGenerationDiagnostics] = useState(null);
  const [draftStorageStatus, setDraftStorageStatus] = useState('loading');
  const [recommendationSearch, setRecommendationSearch] = useState('');
  const [recommendationPage, setRecommendationPage] = useState(0);
  const workspace = useTripPackageWorkspace();
  const autoDraftStarted = useRef(false);
  const draftRestoreAttempted = useRef(false);
  const activeDraftContext = useRef(null);
  const draftRevision = useRef(0);
  const loadSequence = useRef(0);
  const pendingDraftRef = useRef(false);
  const recommendationContextRef = useRef(null);
  const saveInFlightRef = useRef(false);
  const selectionStorageStatusRef = useRef(workspace?.selectionStorageStatus);
  selectionStorageStatusRef.current = workspace?.selectionStorageStatus;
  const setDraftDays = useCallback((value) => { draftRevision.current += 1; setDraftDaysRaw(value); }, [setDraftDaysRaw]);
  const setDraftExternal = useCallback((value) => { draftRevision.current += 1; setDraftExternalRaw(value); }, [setDraftExternalRaw]);
  const setDraftExternalOrder = useCallback((value) => { draftRevision.current += 1; setDraftExternalOrderRaw(value); }, [setDraftExternalOrderRaw]);
  const setDraftCatalog = useCallback((value) => { draftRevision.current += 1; setDraftCatalogRaw(value); }, [setDraftCatalogRaw]);
  const setDraftManual = useCallback((value) => { draftRevision.current += 1; setDraftManualRaw(value); }, [setDraftManualRaw]);
  const selectedMapActivityId = workspace?.selectedMapActivityId || null;
  const showActivityOnMap = (activity, day) => {
    const markerId = mapMarkerIdForActivity(activity?.id);
    workspace?.setSelectedMapActivityId?.(markerId);
    workspace?.setSelectedMapDayId?.(String(day?.id ?? 'all'));
    onNavigateTab?.('lodging');
  };
  const packageLodging = workspace?.selectedLodging || null;
  const packageFlight = workspace?.selectedFlight || null;
  const packagePersistenceEnabled = import.meta.env.VITE_SMARTTRIP_PACKAGE_PERSISTENCE === 'true';
  const [, setEffectiveItineraryDays] = useWorkspaceState('effectiveItineraryDays', []);

  const planningDays = useMemo(() => resolveTripPlanningDays(
    itinerario ? { ...itinerario, dias: draftDays.length ? draftDays : itinerario.dias } : null,
    trip.startDate, trip.endDate,
  ), [draftDays, itinerario, trip.startDate, trip.endDate]);
  const draftReconciliation = useMemo(() => reconcileDraftAssignments({
    days: planningDays,
    external: recommendations.recomendacionesExternas,
    externalAssignments: draftExternal,
    catalog: recommendations.actividades,
    catalogAssignments: draftCatalog,
    manual: draftManual,
  }), [planningDays, recommendations.recomendacionesExternas, recommendations.actividades,
    draftExternal, draftCatalog, draftManual]);
  // All draft-facing features (summary, day cards, map ids, budget and export) derive
  // from this normalized model so temporary string day IDs are never coerced to NaN.
  const draftItineraryDays = useMemo(() => buildDraftItinerary({
    days: planningDays,
    savedDays: draftDays,
    external: recommendations.recomendacionesExternas,
    externalAssignments: draftReconciliation.external,
    externalOrder: draftExternalOrder,
    catalog: recommendations.actividades,
    catalogAssignments: draftReconciliation.catalog,
    manual: draftReconciliation.manual,
  }), [planningDays, draftDays, recommendations.recomendacionesExternas, recommendations.actividades,
    draftReconciliation, draftExternalOrder]);
  const draftDaySummary = useMemo(() => summarizeDraftDays(draftItineraryDays), [draftItineraryDays]);
  useEffect(() => { setEffectiveItineraryDays(draftItineraryDays); }, [draftItineraryDays, setEffectiveItineraryDays]);
  const filteredRecommendations = useMemo(() => filterRecommendationCatalog(
    recommendations.recomendacionesExternas, recommendationSearch), [recommendations.recomendacionesExternas, recommendationSearch]);
  const recommendationPageData = useMemo(() => paginateRecommendationCatalog(filteredRecommendations, recommendationPage),
    [filteredRecommendations, recommendationPage]);
  const recommendationCandidateSummary = useMemo(() => summarizeRecommendationCandidates(
    recommendations.recomendacionesExternas,
  ), [recommendations.recomendacionesExternas]);
  const normalizedCandidates = useMemo(() => combineTourismCandidates(
    recommendations.recomendacionesExternas, recommendations.actividades,
  ), [recommendations.recomendacionesExternas, recommendations.actividades]);
  const savedAssignmentChanges = countSavedAssignmentChanges(itinerario?.dias, draftDays);
  const dayEditsDirty = savedAssignmentChanges > 0;
  const hasPendingPlan = dayEditsDirty || Object.keys(draftReconciliation.external).length > 0
    || Object.keys(draftReconciliation.catalog).length > 0 || draftReconciliation.manual.length > 0;
  const hasSavablePlan = dayEditsDirty || Object.keys(draftReconciliation.external).length > 0
    || Object.keys(draftReconciliation.catalog).length > 0 || draftReconciliation.manual.length > 0;
  pendingDraftRef.current = hasPendingPlan;
  const updateBudgetScenario = useCallback((value) => setGlobalBudgetScenario(value), []);
  const effectiveActivityCount = draftDaySummary.reduce((sum, day) => sum + day.count, 0);
  const currentPackagePayload = useMemo(() => packagePersistenceEnabled ? buildPackageSavePayload({ trip,
    days: draftItineraryDays, selectedFlight: packageFlight, lodging: packageLodging || lodging,
    scenarioExpenses: globalBudgetScenario?.scenarioExpenses || [], pendingBudgetEntries: globalBudgetScenario?.unknownEntries || [],
    budgetTravelers: globalBudgetScenario?.travelers,
    revisionEsperada: workspace?.packageRevision || 0 }) : null,
  [packagePersistenceEnabled, trip, draftItineraryDays, packageFlight, packageLodging, lodging,
    globalBudgetScenario?.scenarioExpenses, globalBudgetScenario?.unknownEntries, globalBudgetScenario?.travelers, workspace?.packageRevision]);
  const packageContentDirty = packagePersistenceEnabled && Boolean(currentPackagePayload)
    && (workspace?.savedPackageSignature
      ? packageContentSignature(currentPackagePayload) !== workspace.savedPackageSignature
      : Boolean(packageFlight || packageLodging || globalBudgetScenario?.scenarioExpenses?.length || effectiveActivityCount));
  const packageSyncReady = !packagePersistenceEnabled || ['ready', 'not-found'].includes(packageSyncStatus);
  const hasSavablePackage = hasSavablePlan || packageContentDirty;
  const pendingSaveCount = orderedAssignments(draftReconciliation.external, draftExternalOrder).length
    + Object.keys(draftReconciliation.catalog).length + draftReconciliation.manual.length + savedAssignmentChanges
    + (packageContentDirty ? 1 : 0);

  useEffect(() => {
    onSummaryChange?.({
      tripId: String(trip.id),
      dayCount: draftDaySummary.length,
      activityCount: effectiveActivityCount,
      itineraryPersisted: Boolean(itinerario?.dias?.some((day) => day.actividades?.length)),
      draftPending: hasPendingPlan,
      orphanCount: draftReconciliation.orphanCount,
      savedFlightCount: globalBudgetScenario?.savedFlightCount ?? null,
      receivedCandidates: recommendationCandidateSummary.counts.received,
      backendEligibleCandidates: recommendationCandidateSummary.counts.backendEligible,
      plannerEligibleCandidates: recommendationCandidateSummary.counts.eligible,
      normalizedCandidates: normalizedCandidates.length,
      generationStatus: autoDraftStatus,
    });
  }, [autoDraftStatus, draftDaySummary.length, effectiveActivityCount, hasPendingPlan, itinerario, onSummaryChange,
    trip.id, draftReconciliation.orphanCount, globalBudgetScenario?.savedFlightCount, recommendationCandidateSummary,
    normalizedCandidates.length]);

  const load = useCallback(async ({ acceptServerDraft = false } = {}) => {
    const sequence = ++loadSequence.current;
    setLoading(true);
    if (packagePersistenceEnabled) setPackageSyncStatus('loading');
    setError('');
    setRecommendationLoadError('');
    const contextKey = JSON.stringify([userId == null ? null : String(userId), String(trip.id),
      String(trip.destination || '').trim().toLocaleLowerCase(), trip.startDate || '', trip.endDate || '']);
    if (activeDraftContext.current !== contextKey) {
      activeDraftContext.current = contextKey;
      if (recommendationContextRef.current !== contextKey) {
        recommendationContextRef.current = null;
        setRecommendations({ requiereSeleccionarIntereses: false, actividades: [], recomendacionesExternas: [] });
      }
      draftRestoreAttempted.current = false;
      draftRevision.current = 0;
      pendingDraftRef.current = false;
      setDraftStorageStatus('loading');
      setDraftExternalRaw({}); setDraftExternalOrderRaw([]); setDraftCatalogRaw({}); setDraftManualRaw([]);
      setDraftDaysRaw([]); setItinerario(null);
      setPackageConflict(null);
      setPackageSyncStatus(packagePersistenceEnabled ? 'loading' : 'disabled');
    }
    // A changed destination/date context starts a fresh revision baseline. Capture
    // it only after resetting that context, so the new server response can hydrate.
    const startingDraftRevision = draftRevision.current;
    const pendingAtStart = pendingDraftRef.current;
    try {
      const names = ['itinerary', 'available', 'recommendations', 'lodging'];
      if (packagePersistenceEnabled) names.push('package');
      const requests = [
        apiRequest(`/api/viajes/${trip.id}/itinerario`, { token }),
        apiRequest(`/api/viajes/${trip.id}/actividades/disponibles`, { token }),
        apiRequest(`/api/viajes/${trip.id}/actividades/recomendadas`, { token }),
        apiRequest(`/api/viajes/${trip.id}/alojamiento`, { token }),
      ];
      if (packagePersistenceEnabled) requests.push(apiRequest(`/api/viajes/${trip.id}/paquete`, { token }));
      const results = await Promise.allSettled([
        ...requests,
      ]);
      if (activeDraftContext.current !== contextKey) return;
      const hydrationDecision = decideWorkspaceHydration({ requestId: sequence, latestRequestId: loadSequence.current,
        draftRevision: draftRevision.current, requestDraftRevision: startingDraftRevision,
        pendingAtRequestStart: pendingAtStart, acceptServerDraft });
      if (hydrationDecision === 'stale-response') return;
      const { fulfilled, rejected } = partitionSettledResults(names, results);
      if (packagePersistenceEnabled) {
        if (Object.hasOwn(fulfilled, 'package')) setPackageSyncStatus('ready');
        else if (/HTTP\s+404/i.test(String(rejected.package?.message || ''))) {
          setPackageSyncStatus('not-found');
          workspace?.setPackageRevision?.(0);
          workspace?.setSavedPackageSignature?.('');
        } else setPackageSyncStatus('unavailable');
      }
      if (Object.hasOwn(fulfilled, 'itinerary') || Object.hasOwn(fulfilled, 'package')) {
        const savedPackage = fulfilled.package || null;
        const loadedItinerary = savedPackage?.paquete
          ? packageSnapshotToItinerary(savedPackage.paquete, trip.id)
          : fulfilled.itinerary || null;
        if (savedPackage && workspace?.setPackageRevision) {
          workspace.setPackageRevision(savedPackage.revision || 0);
          workspace.setSavedPackageSignature?.(packageContentSignature(savedPackage));
          setPersistedPackageExpenses(restorePackageExpenses(savedPackage.paquete?.gastos, {
            ...trip, travelers: savedPackage.paquete?.viaje?.numeroViajeros ?? trip.travelers,
          }));
          setPersistedPackageTravelers(savedPackage.paquete?.viaje?.numeroViajeros ?? null);
          const serverFlight = flightSelectionFromPackage(savedPackage.paquete?.vueloSeleccionado);
          const serverLodging = lodgingSelectionFromPackage(savedPackage.paquete?.alojamiento) || fulfilled.lodging || null;
          // A null snapshot field is absence of a package selection, not a command
          // to erase an unsaved selection restored from this user's local workspace.
          if (serverFlight) workspace.setSelectedFlight?.(serverFlight);
          if (serverLodging) workspace.setSelectedLodging?.(serverLodging);
        }
        if (!savedPackage) {
          setPersistedPackageExpenses([]);
          setPersistedPackageTravelers(null);
        }
        const currentDays = resolveTripPlanningDays(loadedItinerary, trip.startDate, trip.endDate);
        const serverDays = (loadedItinerary?.dias || []).map((day) => ({ ...day, actividades: [...(day.actividades || [])] }));
        if (hydrationDecision === 'apply-server') {
          setItinerario(loadedItinerary);
          setDraftDaysRaw(serverDays);
          if (!draftRestoreAttempted.current) {
            draftRestoreAttempted.current = true;
            const restored = readItineraryDraft(safeLocalStorage(), { userId, trip, itinerary: loadedItinerary, currentDays });
            if (restored.status === 'restored') {
              setDraftDays(restored.draftDays);
              setDraftExternal(restored.externalAssignments);
              setDraftExternalOrder(restored.externalOrder);
              setDraftCatalog(restored.catalogAssignments);
              setDraftManual(restored.manual);
              setDraftStorageStatus('restored');
              setNotice('Se recuperó el borrador local de este viaje. Revisa sus cambios; todavía no se guardaron en Oracle.');
            } else if (['destination-changed', 'dates-changed', 'server-changed', 'corrupt'].includes(restored.status)) {
              setDraftStorageStatus(restored.status);
              setNotice(restored.status === 'server-changed'
                ? 'Hay un borrador local anterior, pero el itinerario guardado cambió después. No se aplicó para proteger la versión más reciente.'
                : restored.status === 'dates-changed'
                  ? 'Las fechas del viaje cambiaron. El borrador anterior se conserva, pero no se aplicaron sus asignaciones a los nuevos días.'
                : restored.status === 'destination-changed'
                  ? 'Hay un borrador local de otro destino para este viaje. No se aplicó para evitar mezclar destinos.'
                  : 'Se encontró un borrador local que no se pudo validar. Se conserva sin aplicar ni sobrescribir.');
            } else {
              setDraftStorageStatus(restored.status === 'unavailable' ? 'unavailable' : 'none');
            }
          } else {
            // No pending local changes existed, so the server snapshot is now canonical.
          }
        } else {
          const serverChanged = itinerarySignature(itinerario) !== itinerarySignature(loadedItinerary);
          if (hydrationDecision === 'draft-edited-during-request') {
            const snapshotSaved = writeItineraryDraft(safeLocalStorage(), { userId, trip, itinerary: itinerario,
              days: resolveTripPlanningDays(itinerario, trip.startDate, trip.endDate), draftDays,
              externalAssignments: draftExternal, externalOrder: draftExternalOrder,
              catalogAssignments: draftCatalog, manual: draftManual });
            if (!serverChanged) setDraftStorageStatus(snapshotSaved ? 'stored' : 'write-failed');
          }
          if (serverChanged) {
            setDraftStorageStatus('server-changed');
            setNotice('El itinerario guardado cambió mientras editabas. Se conservaron tus cambios locales; resuelve el conflicto antes de guardar.');
          }
        }
      } else if (!draftRestoreAttempted.current) {
        draftRestoreAttempted.current = false;
        setDraftStorageStatus('server-unavailable');
      }
      if (Object.hasOwn(fulfilled, 'available')) {
        setAvailableActivities(Array.isArray(fulfilled.available) ? fulfilled.available : []);
      }
      if (Object.hasOwn(fulfilled, 'recommendations')) {
        setRecommendations(fulfilled.recommendations || { requiereSeleccionarIntereses: false, actividades: [] });
        recommendationContextRef.current = contextKey;
        setRecommendationPage(0);
      } else {
        if (recommendationContextRef.current !== contextKey) {
          setRecommendations({ requiereSeleccionarIntereses: false, actividades: [], recomendacionesExternas: [] });
        }
        setRecommendationLoadError(rejected.recommendations?.message || 'No se pudieron cargar las recomendaciones.');
      }
      if (Object.hasOwn(fulfilled, 'lodging')) setLodging(fulfilled.lodging || null);
      if (!Object.hasOwn(fulfilled, 'package') && packagePersistenceEnabled
          && Object.hasOwn(fulfilled, 'lodging') && fulfilled.lodging) {
        workspace?.setSelectedLodging?.(fulfilled.lodging || null);
      }

      const sectionErrors = Object.entries(rejected)
        .filter(([section, failure]) => section !== 'recommendations'
          && !(section === 'package' && String(failure?.message || '').includes('HTTP 404')))
        .map(([section, failure]) => `${section}: ${failure?.message || 'falló la solicitud'}`);
      if (sectionErrors.length) setError(`Algunas secciones no se pudieron cargar: ${sectionErrors.join(' · ')}`);
      const recommendationResult = fulfilled.recommendations;
      if (recommendationResult?.requiereSeleccionarIntereses) {
        try {
          const interests = await apiRequest('/api/intereses', { token });
          setInterestCatalog(Array.isArray(interests) ? interests.map((item) => item.nombre) : []);
        } catch (requestError) {
          setError((current) => [current, `No se pudo cargar el catálogo de intereses: ${requestError.message}`].filter(Boolean).join(' · '));
        }
      }
    } catch (requestError) {
      if (activeDraftContext.current !== contextKey) return;
      setError(requestError.message);
      if (!draftRestoreAttempted.current) setDraftStorageStatus('server-unavailable');
    } finally {
      if (activeDraftContext.current === contextKey && sequence === loadSequence.current) setLoading(false);
    }
  }, [token, trip.id, trip.destination, trip.startDate, trip.endDate, trip.travelers, userId, packagePersistenceEnabled,
    workspace?.setPackageRevision]);

  useEffect(() => {
    setSelectedInterests(trip.interests || []);
  }, [trip.id, trip.interests]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    const updatePackageLodging = (event) => {
      if (String(event.detail?.tripId) !== String(trip.id)) return;
      apiRequest(`/api/viajes/${trip.id}/alojamiento`, { token })
        .then((value) => {
          setLodging(value || null);
          if (!['dirty', 'restored'].includes(selectionStorageStatusRef.current)) {
            workspace?.setSelectedLodging?.(value || null);
          }
        })
        .catch((requestError) => setError(`No se pudo actualizar el alojamiento del presupuesto: ${requestError.message}`));
    };
    window.addEventListener('smarttrip:lodging-updated', updatePackageLodging);
    return () => window.removeEventListener('smarttrip:lodging-updated', updatePackageLodging);
  }, [token, trip.id, workspace?.setSelectedLodging]);

  useEffect(() => {
    if (draftStorageStatus === 'loading' || ['server-changed', 'destination-changed', 'dates-changed', 'corrupt', 'server-unavailable'].includes(draftStorageStatus)) return;
    const storage = safeLocalStorage();
    if (!storage || userId == null || trip?.id == null) return;
    if (!hasPendingPlan) {
      if (draftStorageStatus === 'stored' || draftStorageStatus === 'restored') {
        removeItineraryDraft(storage, userId, trip.id);
        setDraftStorageStatus('none');
      }
      return;
    }
    const saved = writeItineraryDraft(storage, { userId, trip, itinerary: itinerario, days: planningDays, draftDays,
      externalAssignments: draftExternal, externalOrder: draftExternalOrder,
      catalogAssignments: draftCatalog, manual: draftManual });
    if (!saved) setDraftStorageStatus('write-failed');
    else if (draftStorageStatus !== 'restored') setDraftStorageStatus('stored');
  }, [draftStorageStatus, hasPendingPlan, userId, trip, itinerario, planningDays, draftDays,
    draftExternal, draftExternalOrder, draftCatalog, draftManual]);

  useEffect(() => {
    window.dispatchEvent(new CustomEvent('smarttrip:unsaved-changes', { detail: { tripId: String(trip.id), pending: hasPendingPlan } }));
    return () => window.dispatchEvent(new CustomEvent('smarttrip:unsaved-changes', { detail: { tripId: String(trip.id), pending: false } }));
  }, [trip.id, hasPendingPlan]);

  useEffect(() => {
    if (!hasPendingPlan) return undefined;
    const warnBeforeLeaving = (event) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warnBeforeLeaving);
    return () => window.removeEventListener('beforeunload', warnBeforeLeaving);
  }, [hasPendingPlan]);

  const generarItinerario = async () => {
    setSaving(true); setError(''); setNotice('');
    try {
      const isFirstItinerary = !itinerario?.dias?.length;
      const hasSavedInterests = recommendations && !recommendations.requiereSeleccionarIntereses;
      const generated = !isFirstItinerary && hasSavedInterests
        ? await apiRequest('/api/itinerario/generar', {
          method: 'POST', token, body: JSON.stringify({ viajeId: trip.id }),
        })
        : await apiRequest(`/api/viajes/${trip.id}/itinerario`, { method: 'POST', token });
      setNotice(generated?.mensaje || (!isFirstItinerary && hasSavedInterests
        ? 'Itinerario actualizado.'
        : 'Días del itinerario creados. Revisa actividades y confirma por separado las que quieras agregar.'));
      window.dispatchEvent(new CustomEvent('smarttrip:itinerary-updated', { detail: { tripId: String(trip.id) } }));
      setGenerationPreview(false);
      await load();
    } catch (requestError) { setError(requestError.message); }
    finally { setSaving(false); }
  };

  const prepararGeneracion = () => {
    setGenerationPreview(true);
    setError(''); setNotice('');
  };

  const addManualActivity = async (event, day) => {
    event.preventDefault();
    const form = activityForms[day.id] || {};
    setError(''); setNotice('');
    const request = {
      nombre: form.nombre || '', descripcion: form.descripcion || null, ubicacion: form.ubicacion || null,
      costo: form.costo === '' || form.costo == null ? null : Number(form.costo),
      fechaDisponibleInicio: form.fechaDisponibleInicio || null,
      fechaDisponibleFin: form.fechaDisponibleFin || null,
      orden: Math.max(0, ...day.actividades.map((activity) => activity.orden || 0)) + 1,
      intereses: (form.intereses || '').split(',').map((value) => value.trim()).filter(Boolean),
    };
    if (!request.nombre.trim()) { setError('Escribe el nombre de la actividad.'); return; }
    if (!isAvailableOn(request, day.fecha)) { setError('Las fechas de disponibilidad no incluyen el día seleccionado.'); return; }
    setDraftManual((current) => [...current, { id: `manual:${Date.now()}:${Math.random().toString(36).slice(2)}`, dayId: day.id, request }]);
    setActivityForms((current) => ({ ...current, [day.id]: {} }));
    setNotice('Actividad añadida al borrador local. Pulsa «Guardar propuesta» para persistirla.');
    /* Existing rows and new trips use the same confirmation flow below. */
  };

  const removeDraftManual = (id) => setDraftManual((current) => current.filter((item) => item.id !== id));

  const removeActivity = async (day, activity) => {
    setDraftDays((current) => current.map((candidate) => candidate.id !== day.id ? candidate : {
      ...candidate, actividades: candidate.actividades.filter((row) => row.id !== activity.id),
    }));
    setNotice('Actividad quitada del borrador. Confirma «Guardar cambios» para persistirlo.');
  };

  const reorderActivity = async (day, activity, order) => {
    setDraftDays((current) => current.map((candidate) => {
      if (candidate.id !== day.id) return candidate;
      const activities = [...candidate.actividades];
      const sourceIndex = activities.findIndex((row) => row.id === activity.id);
      const targetIndex = Math.max(0, Math.min(activities.length - 1, Number(order) - 1));
      if (sourceIndex < 0 || sourceIndex === targetIndex) return candidate;
      const [moved] = activities.splice(sourceIndex, 1);
      activities.splice(targetIndex, 0, moved);
      return { ...candidate, actividades: activities.map((row, index) => ({ ...row, orden: index + 1 })) };
    }));
    setNotice('Orden actualizado en el borrador local. Confirma «Guardar cambios» para persistirlo.');
  };

  const moveActivity = async (sourceDay, activity, targetDayId) => {
    const targetId = Number(targetDayId);
    if (!targetId || targetId === sourceDay.id) return;
    const targetDay = itinerario?.dias.find((day) => day.id === targetId);
    if (!targetDay || !isAvailableOn(activity, targetDay.fecha)) {
      setError('La actividad no está disponible en la fecha del día seleccionado.');
      return;
    }
    setDraftDays((current) => {
      const source = current.find((candidate) => candidate.id === sourceDay.id);
      const target = current.find((candidate) => candidate.id === targetId);
      if (!source || !target) return current;
      const moving = source.actividades.find((row) => row.id === activity.id);
      if (!moving) return current;
      return current.map((candidate) => {
        if (candidate.id === sourceDay.id) return { ...candidate, actividades: candidate.actividades.filter((row) => row.id !== activity.id) };
        if (candidate.id === targetId) return { ...candidate, actividades: [...candidate.actividades, { ...moving, orden: candidate.actividades.length + 1 }] };
        return candidate;
      });
    });
    setNotice('Actividad movida en el borrador local. Confirma «Guardar cambios» para persistirlo.');
  };

  const addExistingActivity = async (activity) => {
    const dayId = selectedDays[activity.id] || planningDays[0]?.id;
    const day = planningDays.find((item) => String(item.id) === String(dayId));
    const alreadySaved = (itinerario?.dias || []).some((savedDay) => savedDay.actividades?.some((row) => row.id === activity.id));
    if (!day) { setError('No hay un día del viaje disponible para asignar esta actividad.'); return; }
    if (alreadySaved || Object.hasOwn(draftCatalog, activity.id)) { setNotice('La actividad ya está en el itinerario o en el borrador.'); return; }
    setDraftCatalog((current) => ({ ...current, [activity.id]: day.id }));
    setNotice('Actividad añadida al borrador local. Pulsa «Guardar propuesta» para persistirla.');
  };

  const addExternalRecommendation = async (recommendation) => {
    const key = `external:${recommendation.id}`;
    const dayId = selectedDays[key] || planningDays[0]?.id;
    const day = planningDays.find((item) => String(item.id) === String(dayId));
    if (!day) { setError('No hay un día del viaje disponible para asignar esta atracción.'); return; }
    setDraftExternal((current) => ({ ...current, [recommendation.id]: day.id }));
    setDraftExternalOrder((current) => current.includes(recommendation.id) ? current : [...current, recommendation.id]);
    setNotice('Lugar agregado a una propuesta temporal. Revisa la distribución y guarda cuando estés conforme.');
  };

  const buildSuggestedDraft = (requestedMode = null) => {
    let preserve = true;
    if (requestedMode === 'replace' && hasPendingPlan) {
      if (!window.confirm('Reemplazar el borrador local actual con otra propuesta? Los datos ya guardados en Oracle se conservarán.')) return false;
      preserve = false;
    } else if (requestedMode === 'keep') {
      preserve = true;
    } else if (hasPendingPlan) {
      const choice = window.prompt('Ya hay cambios en el borrador. Escribe 1 para conservarlos y completar con recomendaciones en días vacíos, 2 para reemplazar solo el borrador (se conservan los datos guardados), o Cancelar para no cambiarlo.');
      if (choice == null || !['1', '2'].includes(choice.trim())) return false;
      preserve = choice.trim() === '1';
    }
    const allCandidates = normalizedCandidates;
    const candidates = preserve ? excludeAssignedTourismCandidates(allCandidates, {
      externalIds: Object.keys(draftReconciliation.external),
      catalogIds: Object.keys(draftReconciliation.catalog),
    }) : allCandidates;
    candidates.qualityExcluded = allCandidates.qualityExcluded || 0;
    candidates.contractExcluded = allCandidates.contractExcluded || 0;
    candidates.regionalExcluded = allCandidates.regionalExcluded || 0;
    const occupiedDays = new Set();
    if (preserve) {
      planningDays.forEach((day) => {
        const occupied = (day.actividades || []).length
          || Object.values(draftReconciliation.external).some((id) => String(id) === String(day.id))
          || Object.values(draftReconciliation.catalog).some((id) => String(id) === String(day.id))
          || draftReconciliation.manual.some((item) => String(item.dayId) === String(day.id));
        if (occupied) occupiedDays.add(String(day.id));
      });
    }
    const targetDays = planningDays.filter((day) => !preserve || !occupiedDays.has(String(day.id)));
    if (preserve && !targetDays.length) {
      setNotice('Todos los días ya contienen actividades del borrador; no se modificaron. Elige reemplazarlo si quieres una propuesta nueva.');
      return false;
    }
    const proposal = buildTourismDraft(candidates, targetDays, { budgetRemaining: globalBudgetScenario?.remaining });
    const receivedCount = (recommendations.recomendacionesExternas || []).length + (recommendations.actividades || []).length;
    const plannedBaseDays = preserve ? draftDays : (itinerario?.dias || []).map((day) => ({ ...day, actividades: [...(day.actividades || [])] }));
    if (!proposal.order.length) {
      const reason = proposal.regionalExcluded
        ? `${proposal.regionalExcluded} opción(es) son regionales u opcionales y requieren validar el traslado.`
        : proposal.contractExcluded
          ? `${proposal.contractExcluded} opción(es) no fueron elegibles según la clasificación del backend.`
          : proposal.qualityExcluded || proposal.coordinateExcluded
            ? `${proposal.qualityExcluded || 0} opción(es) carecen de evidencia turística suficiente y ${proposal.coordinateExcluded || 0} de coordenadas válidas.`
            : 'No hay candidatos locales elegibles con coordenadas válidas.';
      setGenerationDiagnostics({ received: receivedCount, normalized: allCandidates.length, eligible: proposal.eligibleCount,
        discarded: proposal.discardedReasons, selected: 0, assigned: 0, stored: effectiveActivityCount,
        daysAssigned: 0, orphaned: draftReconciliation.orphanCount });
      setNotice(`No se generó una propuesta: ${reason} Se conservó el borrador anterior. Revisa los descartes o reintenta; no se añadieron lugares inventados.`);
      return false;
    }
    const externalAssignments = {};
    const catalogAssignments = {};
    for (const [candidateId, dayId] of Object.entries(proposal.assignment)) {
      if (candidateId.startsWith('external:')) externalAssignments[candidateId.slice('external:'.length)] = dayId;
      if (candidateId.startsWith('catalog:')) catalogAssignments[candidateId.slice('catalog:'.length)] = dayId;
    }
    const filteredExternal = externalAssignments;
    const filteredCatalog = catalogAssignments;
    const nextExternal = preserve ? { ...draftExternal, ...filteredExternal } : filteredExternal;
    const nextCatalog = preserve ? { ...draftCatalog, ...filteredCatalog } : filteredCatalog;
    const nextManual = preserve ? draftManual : [];
    const nextExternalOrder = [...new Set([
      ...proposal.order.filter((id) => id.startsWith('external:')).map((id) => id.slice('external:'.length)),
      ...(preserve ? draftExternalOrder : []),
    ])];
    const preview = buildDraftItinerary({
      days: planningDays,
      savedDays: plannedBaseDays,
      external: recommendations.recomendacionesExternas,
      externalAssignments: nextExternal,
      externalOrder: nextExternalOrder,
      catalog: recommendations.actividades,
      catalogAssignments: nextCatalog,
      manual: nextManual,
    });
    const newCandidateIds = new Set(proposal.order);
    const visibleNewActivityCount = preview.reduce((sum, day) => sum + (day.actividades || []).filter((activity) => {
      const candidateId = String(activity.id || '').startsWith('external:') ? `external:${String(activity.id).slice('external:'.length)}`
        : String(activity.id || '').startsWith('catalog:') ? `catalog:${String(activity.id).slice('catalog:'.length)}` : '';
      return newCandidateIds.has(candidateId);
    }).length, 0);
    const assignedCount = Object.keys(proposal.assignment).length;
    if (!assignedCount || visibleNewActivityCount === 0) {
      setGenerationDiagnostics({ received: receivedCount, normalized: allCandidates.length, eligible: proposal.eligibleCount,
        discarded: proposal.discardedReasons, selected: proposal.selectedCount, assigned: assignedCount,
        stored: effectiveActivityCount, daysAssigned: 0, orphaned: draftReconciliation.orphanCount,
        error: 'La propuesta no pudo reconciliar sus asignaciones con los días del viaje.' });
      setNotice('No se aplicó la propuesta porque sus asignaciones no corresponden a días actuales. Se conservó el borrador anterior.');
      return false;
    }
    if (!preserve) {
      setDraftExternal({}); setDraftExternalOrder([]); setDraftCatalog({}); setDraftManual([]);
      setDraftDays(plannedBaseDays);
    }
    // New valid assignments override stale day IDs for the same candidate. Other
    // orphan references stay in local state for diagnosis and are never saved.
    setDraftExternal((current) => ({ ...(preserve ? current : {}), ...filteredExternal }));
    setDraftExternalOrder((current) => [...new Set([
      ...proposal.order.filter((id) => id.startsWith('external:')).map((id) => id.slice('external:'.length)),
      ...(preserve ? current : []),
    ])]);
    setDraftCatalog((current) => ({ ...(preserve ? current : {}), ...filteredCatalog }));
    setGenerationDiagnostics({ received: receivedCount, normalized: allCandidates.length, eligible: proposal.eligibleCount,
      discarded: proposal.discardedReasons, selected: proposal.selectedCount, assigned: assignedCount,
      stored: visibleNewActivityCount, daysAssigned: new Set(Object.values(proposal.assignment).map(String)).size,
      orphaned: draftReconciliation.orphanCount });
    setNotice(`Propuesta temporal creada con ${visibleNewActivityCount} actividad(es) asignadas en ${new Set(Object.values(proposal.assignment).map(String)).size} día(s). Se agruparon por cercanía y diversificaron por categoría e intereses. Costos desconocidos no cuentan como cero; horarios y traslados quedan por verificar. Nada se guardó en Oracle.`);
    return { success: visibleNewActivityCount > 0, selected: proposal.selectedCount, assigned: assignedCount, visible: visibleNewActivityCount };
  };

  useEffect(() => {
    if (!autoCreateDraft || loading) return;
    if (['loading', 'server-changed', 'destination-changed', 'dates-changed', 'corrupt', 'server-unavailable'].includes(draftStorageStatus)) return;
    if (itinerario?.dias?.some((day) => day.actividades?.length)) {
      setAutoDraftStatus('saved');
      return;
    }
    const external = recommendations.recomendacionesExternas || [];
    const catalog = recommendations.actividades || [];
    if (!external.length && !catalog.length) {
      setAutoDraftStatus(recommendationLoadError ? 'error' : 'empty');
      return;
    }
    const signature = `${trip.id}:${trip.startDate}:${trip.endDate}:${external.map((row) => row.id).join(',')}:${catalog.map((row) => row.actividad?.id).join(',')}`;
    if (autoDraftStarted.current === signature) return;
    autoDraftStarted.current = signature;
    setAutoDraftStatus('generating');
    const result = buildSuggestedDraft('keep');
    setAutoDraftStatus(result?.success ? 'ready' : 'limited');
  }, [autoCreateDraft, loading, draftStorageStatus, itinerario, recommendations.recomendacionesExternas,
    recommendations.actividades, recommendationLoadError, trip.id, trip.startDate, trip.endDate]);

  const applyVerifiedPackage = (verifiedPackage, successMessage) => {
    const verifiedItinerary = packageSnapshotToItinerary(verifiedPackage.paquete, trip.id);
    setItinerario(verifiedItinerary);
    setDraftDays(verifiedItinerary.dias.map((day) => ({ ...day, actividades: [...day.actividades] })));
    setDraftExternal({}); setDraftExternalOrder([]); setDraftCatalog({}); setDraftManual([]);
    setPersistedPackageExpenses(restorePackageExpenses(verifiedPackage.paquete?.gastos, {
      ...trip, travelers: verifiedPackage.paquete?.viaje?.numeroViajeros ?? trip.travelers,
    }));
    setPersistedPackageTravelers(verifiedPackage.paquete?.viaje?.numeroViajeros ?? null);
    const savedLodging = lodgingSelectionFromPackage(verifiedPackage.paquete?.alojamiento);
    workspace?.setSelectedFlight?.(flightSelectionFromPackage(verifiedPackage.paquete?.vueloSeleccionado));
    if (savedLodging) {
      workspace?.setSelectedLodging?.(savedLodging);
      setLodging(savedLodging);
    } else workspace?.setSelectedLodging?.(null);
    workspace?.setPackageRevision?.(verifiedPackage.revision || 0);
    workspace?.setSavedPackageSignature?.(packageContentSignature(verifiedPackage));
    setPackageSyncStatus('ready');
    setPackageConflict(null);
    removeItineraryDraft(safeLocalStorage(), userId, trip.id);
    setDraftStorageStatus('none');
    setNotice(successMessage);
    window.dispatchEvent(new CustomEvent('smarttrip:itinerary-updated', { detail: { tripId: String(trip.id) } }));
  };

  const keepLocalDraftOnRemoteRevision = () => {
    if (!packageConflict) return;
    const remote = packageConflict.remotePackage;
    if (!window.confirm(`El borrador local se conservará. La siguiente acción Guardar podrá reemplazar explícitamente la revisión remota ${remote?.revision ?? 'eliminada'}; todavía no se escribirá nada. ¿Continuar?`)) return;
    workspace?.setPackageRevision?.(remote?.revision || 0);
    workspace?.setSavedPackageSignature?.(remote ? packageContentSignature(remote) : '');
    setPackageSyncStatus(remote ? 'ready' : 'not-found');
    setPackageConflict(null);
    setDraftStorageStatus('stored');
    setError('');
    setNotice(`El borrador local sigue intacto y ahora usa revisión base ${remote?.revision || 0}. Aún no se guardó; pulsa Guardar itinerario para confirmar el reemplazo.`);
  };

  const useRemotePackageVersion = () => {
    const remote = packageConflict?.remotePackage;
    if (!remote?.paquete) return;
    if (!window.confirm('Se descargará una copia JSON del paquete local actual. La copia de localStorage no se borrará; se mostrará la revisión guardada en Oracle. ¿Continuar?')) return;
    const backup = { tripId: String(trip.id), exportedAt: new Date().toISOString(), package: packageConflict.localPayload };
    const url = URL.createObjectURL(new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' }));
    const anchor = document.createElement('a');
    anchor.href = url; anchor.download = `smarttrip-trip-${trip.id}-borrador-conflicto.json`;
    anchor.click(); URL.revokeObjectURL(url);
    const remoteItinerary = packageSnapshotToItinerary(remote.paquete, trip.id);
    setItinerario(remoteItinerary);
    setDraftDays(remoteItinerary.dias.map((day) => ({ ...day, actividades: [...day.actividades] })));
    setDraftExternal({}); setDraftExternalOrder([]); setDraftCatalog({}); setDraftManual([]);
    setPersistedPackageExpenses(restorePackageExpenses(remote.paquete.gastos, {
      ...trip, travelers: remote.paquete.viaje?.numeroViajeros ?? trip.travelers,
    }));
    setPersistedPackageTravelers(remote.paquete.viaje?.numeroViajeros ?? null);
    workspace?.setSelectedFlight?.(flightSelectionFromPackage(remote.paquete.vueloSeleccionado));
    workspace?.setSelectedLodging?.(lodgingSelectionFromPackage(remote.paquete.alojamiento));
    workspace?.setPackageRevision?.(remote.revision || 0);
    workspace?.setSavedPackageSignature?.(packageContentSignature(remote));
    setPackageSyncStatus('ready'); setPackageConflict(null); setDraftStorageStatus('none');
    setError(''); setNotice(`Se cargó la revisión ${remote.revision} de Oracle. Se descargó un respaldo local y la clave de borrador del navegador no se eliminó.`);
  };

  const savePlanningDraft = async () => {
    if (saveInFlightRef.current || savingDraft || packageConflict
        || ['server-changed', 'destination-changed', 'dates-changed', 'corrupt', 'server-unavailable'].includes(draftStorageStatus)) return;
    const externalRows = orderedAssignments(draftReconciliation.external, draftExternalOrder);
    const catalogRows = Object.entries(draftReconciliation.catalog);
    const manualRows = [...draftReconciliation.manual];
    const packageSelectionPending = packageContentDirty;
    if (!externalRows.length && !catalogRows.length && !manualRows.length && !dayEditsDirty && !packageSelectionPending) return;
    if (!packagePersistenceEnabled && externalRows.length) {
      setError('El guardado de paquetes está desactivado en el frontend. No se enviaron actividades externas al endpoint antiguo; el borrador sigue intacto. Habilita VITE_SMARTTRIP_PACKAGE_PERSISTENCE=true y reinicia Vite después de confirmar el esquema.');
      return;
    }
    if (packagePersistenceEnabled && !['ready', 'not-found'].includes(packageSyncStatus)) {
      setError('No se pudo establecer una revisión base confiable del paquete. No se envió el PUT y el borrador permanece intacto. Reintenta la lectura del paquete antes de guardar.');
      return;
    }
    const total = externalRows.length + catalogRows.length + manualRows.length + savedAssignmentChanges + (packageSelectionPending ? 1 : 0);
    if (!window.confirm(`Guardar ${total} cambio(s) del borrador en el itinerario de este viaje?`)) return;
    saveInFlightRef.current = true;
    setSavingDraft(true); setError(''); setNotice('');
    let saved = 0;
    let packagePutConfirmed = false;
    try {
      if (packagePersistenceEnabled) {
        const localPayload = currentPackagePayload;
        let remotePackage = null;
        try {
          remotePackage = await apiRequest(`/api/viajes/${trip.id}/paquete`, { token });
        } catch (readError) {
          if (!/HTTP\s+404/i.test(String(readError?.message || ''))) {
            throw new Error(`No se pudo verificar la revisión actual del paquete antes de guardar (${readError.message}). No se envió el PUT y el borrador se conservó.`);
          }
        }
        const remoteState = classifyPackageSnapshot({ baselineSignature: workspace?.savedPackageSignature || '',
          localPayload, remotePackage });
        if (remoteState === 'conflict' || remoteState === 'remote-removed') {
          setPackageConflict({ remotePackage, localPayload, detectedAt: new Date().toISOString() });
          setPackageSyncStatus('conflict');
          setError(remoteState === 'remote-removed'
            ? 'El paquete que servía como base ya no está en Oracle. Se conservó el borrador y no se envió el PUT.'
            : 'El paquete remoto cambió desde que se abrió. Se descargó su revisión, se conservó el borrador y no se envió el PUT. Elige cómo resolver el conflicto.');
          return;
        }
        if (remoteState === 'already-saved' && remotePackage) {
          applyVerifiedPackage(remotePackage, 'Se reconoció una escritura anterior cuyo GET se había perdido; no se creó otra revisión.');
          return;
        }
        const baseRevision = remotePackage?.revision || 0;
        const payload = { ...localPayload, revisionEsperada: baseRevision };
        if (remotePackage && baseRevision !== (workspace?.packageRevision || 0)) {
          workspace?.setPackageRevision?.(baseRevision);
          workspace?.setSavedPackageSignature?.(packageContentSignature(remotePackage));
        }
        const key = packageIdempotencyKey(payload, trip.id);
        await apiRequest(`/api/viajes/${trip.id}/paquete`, { method: 'PUT', token,
          headers: { 'Idempotency-Key': key }, body: JSON.stringify(payload) });
        packagePutConfirmed = true;
        const verifiedPackage = await apiRequest(`/api/viajes/${trip.id}/paquete`, { token });
        if (packageContentSignature(payload) !== packageContentSignature(verifiedPackage)) {
          throw new Error('El servidor respondió, pero el paquete recuperado no coincide. Se conserva el borrador local.');
        }
        applyVerifiedPackage(verifiedPackage, `Paquete guardado y verificado mediante GET (revisión ${verifiedPackage.revision}).`);
        return;
      }
      let savedItinerary = itinerario;
      if (!savedItinerary?.dias?.length) {
        savedItinerary = await apiRequest(`/api/viajes/${trip.id}/itinerario`, { method: 'POST', token });
        if (!savedItinerary?.dias?.length) {
          throw new Error('El backend no devolvió los días guardados; no se intentó asignar actividades.');
        }
        setItinerario(savedItinerary);
      }
      const persistedDays = savedItinerary.dias;
      const resolveDay = (dayKey) => {
        const temporaryDate = String(dayKey).startsWith('temporary:') ? String(dayKey).slice('temporary:'.length) : null;
        return persistedDays.find((day) => temporaryDate ? day.fecha === temporaryDate : String(day.id) === String(dayKey));
      };
      const nextOrders = new Map(persistedDays.map((day) => {
        const draftDay = draftDays.find((candidate) => String(candidate.id) === String(day.id));
        const activities = draftDay?.actividades || day.actividades || [];
        return [String(day.id), Math.max(0, ...activities.map((activity) => Number(activity.orden) || 0))];
      }));
      const nextOrderFor = (day) => {
        const key = String(day.id);
        const next = (nextOrders.get(key) || 0) + 1;
        nextOrders.set(key, next);
        return next;
      };

      if (itinerario?.dias?.length) {
        for (const originalDay of itinerario.dias) {
          for (const originalActivity of originalDay.actividades || []) {
            const destinationDay = draftDays.find((candidate) => candidate.actividades?.some((row) => row.id === originalActivity.id));
            const wasMoved = destinationDay && destinationDay.id !== originalDay.id;
            if (!destinationDay) {
              await apiRequest(`/api/viajes/${trip.id}/itinerario/dias/${originalDay.id}/actividades/${originalActivity.id}`, { method: 'DELETE', token });
              saved += 1;
              continue;
            }
            if (wasMoved) {
              await apiRequest(`/api/viajes/${trip.id}/itinerario/dias/${originalDay.id}/actividades/${originalActivity.id}/dia`, {
                method: 'PUT', token, body: JSON.stringify({ diaDestinoId: destinationDay.id }),
              });
            }
            const finalActivityIndex = destinationDay.actividades.findIndex((row) => row.id === originalActivity.id);
            const finalOrder = finalActivityIndex + 1;
            if (wasMoved || Number(originalActivity.orden) !== finalOrder) {
              await apiRequest(`/api/viajes/${trip.id}/itinerario/dias/${destinationDay.id}/actividades/${originalActivity.id}`, {
                method: 'PUT', token, body: JSON.stringify({ orden: finalOrder }),
              });
            }
            if (wasMoved || Number(originalActivity.orden) !== finalOrder) saved += 1;
          }
        }
      }

      for (const [activityId, dayIdValue] of catalogRows) {
        const day = resolveDay(dayIdValue);
        if (!day) throw new Error('No se pudo asociar una actividad del catálogo a su fecha.');
        await apiRequest(`/api/viajes/${trip.id}/itinerario/dias/${day.id}/actividades/${activityId}`, {
          method: 'POST', token, body: JSON.stringify({ orden: nextOrderFor(day) }),
        });
        saved += 1;
        setDraftCatalog((current) => { const next = { ...current }; delete next[activityId]; return next; });
      }
      for (const item of manualRows) {
        const day = resolveDay(item.dayId);
        if (!day) throw new Error('No se pudo asociar una actividad manual a su fecha.');
        await apiRequest(`/api/viajes/${trip.id}/itinerario/dias/${day.id}/actividades`, {
          method: 'POST', token, body: JSON.stringify({ ...item.request, orden: nextOrderFor(day) }),
        });
        saved += 1;
        setDraftManual((current) => current.filter((row) => row.id !== item.id));
      }
      for (const [externalId, dayIdValue] of externalRows) {
        const day = resolveDay(dayIdValue);
        if (!day) throw new Error('No se pudo asociar un lugar recomendado a su fecha.');
        await apiRequest(`/api/viajes/${trip.id}/itinerario/dias/${day.id}/recomendaciones-externas/${encodeURIComponent(externalId)}`, {
          method: 'POST', token, body: JSON.stringify({ orden: nextOrderFor(day) }),
        });
        saved += 1;
        setDraftExternal((current) => { const next = { ...current }; delete next[externalId]; return next; });
        setDraftExternalOrder((current) => current.filter((id) => id !== externalId));
      }
      const verified = await apiRequest(`/api/viajes/${trip.id}/itinerario`, { token });
      const canonical = (days) => (Array.isArray(days) ? days : []).map((day) => ({
        date: String(day.fecha || ''),
        activities: (Array.isArray(day.actividades) ? day.actividades : []).map((activity) =>
          String(activity.nombre || activity.name || '').trim().normalize('NFC').toLocaleLowerCase()),
      })).sort((left, right) => left.date.localeCompare(right.date));
      const expectedState = canonical(draftItineraryDays);
      const verifiedState = canonical(verified?.dias);
      if (JSON.stringify(expectedState) !== JSON.stringify(verifiedState)) {
        throw new Error('El servidor respondió, pero la relectura no coincide con el borrador. Se conserva la copia local para revisión.');
      }
      removeItineraryDraft(safeLocalStorage(), userId, trip.id);
      setDraftStorageStatus('none');
      await load({ acceptServerDraft: true });
      window.dispatchEvent(new CustomEvent('smarttrip:itinerary-updated', { detail: { tripId: String(trip.id) } }));
      setNotice(`Itinerario guardado en Oracle y verificado mediante una nueva lectura (${effectiveActivityCount} actividades).`);
    } catch (requestError) {
      if (packagePutConfirmed) {
        setPackageSyncStatus('ready');
        setError(`Oracle aceptó el PUT, pero no se pudo confirmar la lectura GET (${requestError.message}). No se muestra éxito ni se elimina el borrador. Reintenta: se comparará el snapshot remoto y se reconocerá la misma escritura si ya quedó confirmada.`);
      } else setError(`${packageSaveFailureMessage(requestError)}${saved ? ` Se guardaron ${saved} cambio(s); el resto del borrador sigue visible. Recarga para confirmar el estado guardado antes de reintentar.` : ''}`);
    } finally { saveInFlightRef.current = false; setSavingDraft(false); }
  };

  const discardLocalDraft = () => {
    if (!window.confirm('¿Descartar el borrador local de este viaje? No se modificará Oracle.')) return;
    removeItineraryDraft(safeLocalStorage(), userId, trip.id);
    setDraftExternal({}); setDraftExternalOrder([]); setDraftCatalog({}); setDraftManual([]);
    setDraftDays((itinerario?.dias || []).map((day) => ({ ...day, actividades: [...(day.actividades || [])] })));
    setDraftStorageStatus('none');
    setNotice('Borrador local descartado. El itinerario guardado en Oracle no se modificó.');
  };

  const exportPdf = async () => {
    if (exportingPdf) return;
    setExportingPdf(true); setError(''); setNotice('');
    try {
      const printRows = await Promise.allSettled([
        apiRequest(`/api/viajes/${trip.id}/alojamiento`, { token }),
        apiRequest(`/api/viajes/${trip.id}/vuelos`, { token }),
      ]);
      const [lodgingResult, flightsResult] = printRows;
      const pdf = buildItineraryPdf({ trip, itinerary: { dias: draftItineraryDays },
        lodging: packageLodging || (lodgingResult.status === 'fulfilled' ? lodgingResult.value || lodging : lodging),
        flights: mergePackageFlight(flightsResult.status === 'fulfilled' && Array.isArray(flightsResult.value) ? flightsResult.value : [], packageFlight),
        budget: globalBudgetScenario, travelers: globalBudgetScenario?.travelers || trip.travelers || trip.numeroViajeros || null,
        isDraft: hasPendingPlan || !itinerario?.dias?.length });
      const filename = `smarttrip-${String(trip.destination || 'itinerario').normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-')}-itinerario.pdf`;
      downloadItineraryPdf(pdf, filename);
      const partial = printRows.some((result) => result.status === 'rejected');
      setNotice(`PDF descargado con ${draftDaySummary.reduce((sum, day) => sum + day.count, 0)} actividad(es) en ${draftDaySummary.length} día(s).${partial ? ' Algún dato guardado de alojamiento o vuelos no pudo consultarse y se omitió.' : ''}`);
    } catch (requestError) {
      setError(requestError.message);
    } finally { setExportingPdf(false); }
  };

  const toggleInterest = (name) => {
    setSelectedInterests((current) => current.includes(name)
      ? current.filter((item) => item !== name)
      : [...current, name]);
  };

  const addCustomInterests = (event) => {
    event.preventDefault();
    const additions = customInterest.split(',').map((name) => name.trim()).filter(Boolean);
    setSelectedInterests((current) => [...new Set([...current, ...additions])]);
    setInterestCatalog((current) => [...new Set([...current, ...additions])]);
    setCustomInterest('');
  };

  const saveInterests = async () => {
    if (!selectedInterests.length) { setError('Selecciona al menos un interés para recibir recomendaciones.'); return; }
    setSavingInterests(true); setError(''); setNotice('');
    try {
      await apiRequest(`/api/viajes/${trip.id}/intereses`, {
        method: 'PUT', token, body: JSON.stringify({ intereses: selectedInterests }),
      });
      setNotice('Intereses guardados en el viaje.');
      await load();
    } catch (requestError) { setError(requestError.message); }
    finally { setSavingInterests(false); }
  };

  const matchingByActivity = new Map((recommendations.actividades || []).map((item) => [item.actividad.id, item]));
  return (
    <section className="trip-page trip-page--wide trip-page--itinerary" aria-live="polite">
      {draftStorageStatus === 'restored' && <p className="trip-notice" role="status">Borrador local recuperado para este usuario y viaje; aún no está guardado en Oracle.</p>}
      {draftStorageStatus === 'unavailable' && hasPendingPlan && <p className="form-error" role="alert">El navegador no permitió acceder al almacenamiento local; los cambios actuales no sobrevivirán a una recarga.</p>}
      {draftStorageStatus === 'write-failed' && <p className="form-error" role="alert">No se pudo guardar el borrador en este navegador. Mantén esta pestaña abierta y copia los cambios antes de salir.</p>}
      {draftStorageStatus === 'server-unavailable' && <p className="form-error" role="alert">No se pudo comprobar el itinerario guardado; el borrador local no se restauró ni se sobrescribió.</p>}
      {packagePersistenceEnabled && packageSyncStatus === 'unavailable' && <p className="form-error" role="alert">No se pudo leer la revisión del paquete en Oracle. Guardar está bloqueado para evitar enviar una revisión obsoleta; el borrador local se conserva.</p>}
      {packageConflict && <div className="external-recommendations__warning" role="alert">
        <p>Conflicto detectado antes del PUT. Revisión remota: {packageConflict.remotePackage?.revision ?? 'el paquete remoto fue eliminado'}; actividades locales: {countPackageActivities(packageConflict.localPayload)}; actividades remotas: {countPackageActivities(packageConflict.remotePackage?.paquete)}. No se escribió nada y el borrador continúa intacto.</p>
        <div className="package-conflict-actions">
          <button type="button" onClick={keepLocalDraftOnRemoteRevision}>Mantener borrador local y confirmar reemplazo en un guardado posterior</button>
          {packageConflict.remotePackage?.paquete && <button type="button" onClick={useRemotePackageVersion}>Usar versión Oracle y descargar respaldo local</button>}
        </div>
      </div>}
      {['server-changed', 'destination-changed', 'dates-changed', 'corrupt'].includes(draftStorageStatus) && <div className="external-recommendations__warning" role="alert">
        <p>Hay un borrador local que no se aplicó para proteger el itinerario actual. Revísalo antes de continuar o descártalo explícitamente.</p>
        <button type="button" onClick={discardLocalDraft}>Descartar borrador local anterior</button>
      </div>}
      <section className="package-summary-view" hidden={view !== 'summary'} aria-label="Resumen del paquete">
        <p className="eyebrow">Revisa antes de confirmar</p>
        <h2>Resumen de tu viaje a {trip.destination}</h2>
        <p>{trip.startDate} — {trip.endDate} · {draftDaySummary.length} día(s) · {effectiveActivityCount} actividad(es).</p>
        <div className="package-summary-view__grid">
          <article><strong>Itinerario</strong><span>{itinerario?.dias?.some((day) => day.actividades?.length) ? 'Guardado en el viaje' : hasPendingPlan ? 'Borrador local sin guardar' : 'Aún no guardado'}</span></article>
          <article><strong>Alojamiento</strong><span>{packageLodging?.nombre || lodging?.nombre || 'Sin alojamiento seleccionado'}</span></article>
          <article><strong>Vuelos</strong><span>{packageFlight
            ? `Oferta seleccionada para revisar: ${packageFlight.totalAmount || ''} ${packageFlight.currency || ''}${packageFlight.testData ? ' · SIMULACIÓN Sandbox' : ''}`.trim()
            : globalBudgetScenario?.savedFlightCount > 0
              ? `Sin oferta seleccionada en esta sesión · ${globalBudgetScenario.savedFlightCount} vuelo(s) guardado(s) en el viaje`
              : globalBudgetScenario?.savedFlightCount === 0 ? 'Sin vuelos guardados ni oferta seleccionada' : 'Consultando vuelos guardados'}</span></article>
          <article><strong>Presupuesto</strong><span>{globalBudgetScenario?.hasBudget ? `${globalBudgetScenario.currency} · saldo ${globalBudgetScenario.remaining == null ? 'parcial no calculado' : Number(globalBudgetScenario.remaining).toLocaleString('es-CO')}` : `${trip.budgetCurrency || 'COP'} · ${trip.budget == null ? 'sin monto definido' : Number(trip.budget).toLocaleString('es-CO')}`}</span></article>
        </div>
        {(globalBudgetScenario?.unknown > 0 || globalBudgetScenario?.foreignCurrency > 0) && <p role="status">El saldo es parcial: hay {globalBudgetScenario.unknown} costo(s) pendiente(s) y {globalBudgetScenario.foreignCurrency} importe(s) sin conversión. No equivale a un costo final.</p>}
        {(packageLodging || lodging)?.nombre && !globalBudgetScenario?.included?.some((entry) => entry.category === 'Alojamiento')
          && <p role="status">{(packageLodging || lodging).nombre}: no hay tarifa en el escenario; añade una estimación manual en Presupuesto. Seleccionar el hotel no realiza una reserva.</p>}
        {packageFlight?.testData && <p role="status">La oferta Sandbox es simulada y no se incluye como precio real del viaje.</p>}
        {error && <p className="form-error" role="alert">{error}</p>}
        {notice && <p className="trip-notice" role="status">{notice}</p>}
        <div className="package-summary-view__actions">
          <button type="button" onClick={() => onNavigateTab?.('itinerary')}>Revisar itinerario</button>
          <button type="button" onClick={() => onNavigateTab?.('budget')}>Revisar presupuesto</button>
          <button type="button" disabled={exportingPdf || loading || effectiveActivityCount === 0} onClick={exportPdf}>
            {exportingPdf ? 'Preparando PDF…' : 'Exportar paquete a PDF'}
          </button>
          {hasSavablePackage && <button type="button" disabled={savingDraft || loading || !packageSyncReady || Boolean(packageConflict) || pendingSaveCount === 0 || ['server-changed', 'destination-changed', 'dates-changed', 'corrupt', 'server-unavailable'].includes(draftStorageStatus)} onClick={savePlanningDraft}>
            {savingDraft ? 'Guardando…' : `Guardar itinerario (${pendingSaveCount} cambios · ${effectiveActivityCount} actividades)`}
          </button>}
        </div>
      </section>
      <div className="package-itinerary-view" hidden={view !== 'itinerary'}>
      <div className={`trip-card trip-card--planner${autoCreateDraft ? ' trip-card--package' : ''}`}>
        <div className="trip-heading">
          <div>
            <p className="eyebrow">{hasPendingPlan ? 'Borrador local' : itinerario?.dias?.length ? 'Itinerario guardado' : 'Planificación lista'}</p>
            <h2>Itinerario de mi viaje</h2>
            <p>{hasPendingPlan ? 'Las actividades visibles incluyen cambios locales pendientes.'
              : itinerario?.dias?.length ? 'Mostrando días y actividades guardados en este viaje.'
                : 'Los días son temporales hasta que confirmes el guardado.'}</p>
          </div>
        </div>
        {error && <p className="form-error" role="alert">{error}</p>}
        {notice && <p className="trip-notice" role="status">{notice}</p>}
        {loading ? <p role="status">Consultando actividades y presupuesto…</p> : null}
        {autoCreateDraft && <p className="package-generation-status" role="status">
          {autoDraftStatus === 'idle' && loading && 'Buscando atractivos y preparando automáticamente la propuesta por días…'}
          {autoDraftStatus === 'generating' && 'Preparando automáticamente los días con atractivos disponibles…'}
          {autoDraftStatus === 'ready' && effectiveActivityCount > 0 && `Propuesta automática lista: ${effectiveActivityCount} actividad(es) distribuidas en ${draftDaySummary.filter((day) => day.count > 0).length} de ${draftDaySummary.length} día(s).`}
          {autoDraftStatus === 'ready' && effectiveActivityCount === 0 && 'La propuesta no contiene actividades actualmente. Puedes volver a generarla; no se guardará nada vacío.'}
          {autoDraftStatus === 'saved' && 'Este viaje ya tiene actividades guardadas. Se conservaron; puedes regenerar desde la sección de itinerario.'}
          {autoDraftStatus === 'limited' && 'No se pudo completar una propuesta automática con los datos actuales. Revisa los descartes y errores de fuentes; puedes reintentar.'}
          {autoDraftStatus === 'empty' && !loading && 'Las fuentes no entregaron candidatos para planificar. El itinerario sigue disponible para reintentar cuando el servicio responda.'}
          {autoDraftStatus === 'error' && !loading && 'Falló la consulta de recomendaciones. Las demás secciones siguen disponibles; puedes reintentar la búsqueda.'}
        </p>}

        {recommendations.requiereSeleccionarIntereses && (
          <section className="activity-interest-picker">
            <div className="activity-budget__title"><Sparkles size={20} /><h3>Elige intereses para recibir recomendaciones</h3></div>
            <p>Selecciona intereses existentes o agrega los tuyos. Se guardarán en este viaje.</p>
            <div className="activity-interest-picker__choices">
              {interestCatalog.map((name) => (
                <label className="activity-interest-chip" key={name}>
                  <input type="checkbox" checked={selectedInterests.includes(name)} onChange={() => toggleInterest(name)} />
                  <span>{name}</span>
                </label>
              ))}
              {!interestCatalog.length && <p>Aún no hay intereses en el catálogo. Puedes agregar uno abajo.</p>}
            </div>
            <form className="activity-interest-picker__add" onSubmit={addCustomInterests}>
              <label htmlFor={`new-interest-${trip.id}`}>Agregar intereses separados por coma</label>
              <input id={`new-interest-${trip.id}`} value={customInterest} onChange={(event) => setCustomInterest(event.target.value)} maxLength="500" placeholder="Naturaleza, gastronomía" />
              <button type="submit" disabled={!customInterest.trim()}>Agregar a la selección</button>
            </form>
            {selectedInterests.length > 0 && <p className="activity-interest-picker__selected">Seleccionados: {selectedInterests.join(', ')}</p>}
            <button type="button" onClick={saveInterests} disabled={savingInterests || selectedInterests.length === 0}>
              {savingInterests ? 'Guardando…' : 'Guardar intereses'}
            </button>
          </section>
        )}

        <section className="available-activities">
          <div className="activity-budget__title"><Sparkles size={20} /><h3>Actividades disponibles para el viaje</h3></div>
          <p className="trip-lead">{trip.startDate} a {trip.endDate}. Solo se muestran actividades cuya disponibilidad coincide con al menos un día.</p>
          {!availableActivities.length && !loading ? (
            <p className="activity-empty">No hay actividades disponibles para estas fechas.</p>
          ) : (
            <div className="activity-catalog">
              {availableActivities.map((activity) => {
                const recommendation = matchingByActivity.get(activity.id);
                const matchingInterests = recommendation?.interesesCoincidentes || [];
                const validDays = planningDays.filter((day) => isAvailableOn(activity, day.fecha));
                const selectedDay = selectedDays[activity.id] || validDays[0]?.id || '';
                return (
                  <article className="activity-catalog__item" key={activity.id}>
                    <div className="activity-catalog__details">
                      <strong>{activity.nombre}</strong>
                      {activity.descripcion && <p>{activity.descripcion}</p>}
                      {activity.ubicacion && <small>{activity.ubicacion}</small>}
                      <small>{availabilityLabel(activity)} · {activity.costo == null ? 'Precio no disponible' : money(activity.costo)}</small>
                      {activity.costo != null && globalBudgetScenario?.hasBudget && !draftReconciliation.catalog[activity.id]
                        && <small>{Number(activity.costo) <= Number(globalBudgetScenario.remaining)
                          ? 'El costo registrado cabe en el saldo conocido; faltan otros costos desconocidos.'
                          : 'El costo registrado supera el saldo conocido del escenario.'}</small>}
                      {matchingInterests.length > 0 && <span className="activity-recommendation-reason">Recomendada por: {matchingInterests.join(', ')}</span>}
                    </div>
                    {planningDays.length ? (
                      <div className="activity-catalog__action">
                        <select aria-label={`Día para ${activity.nombre}`} value={selectedDay} onChange={(event) => setSelectedDays((current) => ({ ...current, [activity.id]: event.target.value }))}>
                          {validDays.map((day, index) => <option key={day.id} value={day.id}>Día {index + 1} · {day.fecha}</option>)}
                        </select>
                        <button type="button" onClick={() => addExistingActivity(activity)} disabled={addingActivity === activity.id || validDays.length === 0 || (itinerario?.dias || []).some((day) => day.actividades?.some((row) => row.id === activity.id))}>
                          <Plus size={16} /> {draftReconciliation.catalog[activity.id] ? 'En la propuesta' : 'Añadir a propuesta'}
                        </button>
                      </div>
                    ) : <small>Las fechas del viaje no permiten crear días temporales.</small>}
                  </article>
                );
              })}
            </div>
          )}
          {recommendations.actividades?.length === 0 && !recommendations.requiereSeleccionarIntereses && !loading && (
            <p className="activity-empty">No encontramos actividades del catálogo interno que coincidan con intereses y fechas. Las opciones de fuentes públicas aparecen por separado.</p>
          )}
        </section>

        <section className="external-recommendations" aria-label="Lugares turísticos de fuentes públicas">
          <div className="activity-budget__title"><Sparkles size={20} /><h3>Lugares turísticos recomendados</h3></div>
          <p className="trip-lead">Atractivos descubiertos en fuentes públicas para {trip.destination}. Los proveedores pueden aportar categorías y cobertura distintas. Añádelos a una propuesta temporal; nada se guarda hasta que confirmes.</p>
          {import.meta.env.DEV && recommendations.diagnostico && <details className="recommendation-diagnostics">
            <summary>Diagnóstico de esta búsqueda</summary>
            <dl>
              <div><dt>Destino consultado</dt><dd>{recommendations.diagnostico.destinoConsultado}</dd></div>
              <div><dt>Centro geocodificado</dt><dd>{recommendations.diagnostico.latitudCentro != null && recommendations.diagnostico.longitudCentro != null
                ? `${recommendations.diagnostico.latitudCentro}, ${recommendations.diagnostico.longitudCentro}` : 'No disponible'}</dd></div>
              <div><dt>Fuentes consultadas</dt><dd>{recommendations.diagnostico.fuentesConsultadas?.join(', ') || 'Ninguna confirmada'}</dd></div>
              <div><dt>Área de búsqueda</dt><dd>{recommendations.diagnostico.areaGeografica || 'No informada por el backend'}</dd></div>
              <div><dt>Resultados conservados por fuente</dt><dd>{Object.entries(recommendations.diagnostico.resultadosConservadosPorFuente || {})
                .map(([provider, count]) => `${provider}: ${count}`).join(' · ') || 'Sin resultados registrados'}</dd></div>
              <div><dt>Descartes del ranking</dt><dd>{Object.entries(recommendations.diagnostico.descartes || {})
                .map(([reason, count]) => `${reason}: ${count}`).join(' · ') || 'Ninguno'}</dd></div>
              <div><dt>Atracciones finales</dt><dd>{recommendations.diagnostico.atractivosFinales}</dd></div>
              <div><dt>Elementos recibidos por React</dt><dd>{recommendations.recomendacionesExternas?.length || 0}</dd></div>
              <div><dt>Clasificación recibida</dt><dd>{Object.entries(recommendations.diagnostico.clasificacionCandidatos || {})
                .map(([name, count]) => `${name}: ${count}`).join(' · ') || 'El backend no envió desglose; cálculo frontend: ' +
                  `coordenadas ${recommendationCandidateSummary.counts.withCoordinates}, principales ${recommendationCandidateSummary.counts.principal}, complementarios ${recommendationCandidateSummary.counts.complementary}, explorables ${recommendationCandidateSummary.counts.explorable}, aptos ${recommendationCandidateSummary.counts.eligible}`}</dd></div>
              <div><dt>Razones de no elegibilidad</dt><dd>{Object.entries(recommendations.diagnostico.razonesExclusion || recommendationCandidateSummary.reasons)
                .map(([reason, count]) => `${reason}: ${count}`).join(' · ') || 'Ninguna'}</dd></div>
              <div><dt>Propuesta actual</dt><dd>{draftDaySummary.map((day) => `${day.label}: ${day.count}`).join(' · ')}</dd></div>
            </dl>
            {recommendations.diagnostico.errores?.map((diagnosticError, index) => <p role="status" key={`${index}:${diagnosticError}`}>{diagnosticError}</p>)}
          </details>}
          {recommendations.avisosFuentes?.map((warning) => <p className="external-recommendations__warning" role="status" key={warning}>{warning}</p>)}
          {draftReconciliation.orphanCount > 0 && <p className="external-recommendations__warning" role="alert">
            Hay {draftReconciliation.orphanCount} referencia(s) del borrador que apuntan a candidatos o días anteriores. Se conservan localmente, no cuentan como actividades y no se guardarán. La nueva propuesta puede reasignar candidatos que sigan disponibles.
          </p>}
          <p className="recommendation-eligibility-summary">
            {recommendationCandidateSummary.counts.backendEligible} marcados aptos por el backend · {recommendationCandidateSummary.counts.eligible} elegibles en el planificador · {recommendationCandidateSummary.counts.complementary} complementarios · {recommendationCandidateSummary.counts.explorable} explorables · {recommendationCandidateSummary.counts.regional} regionales u opcionales.
            {' '}Solo los elegibles con coordenadas y un día vigente pueden asignarse.
          </p>
          {import.meta.env.DEV && <details className="recommendation-diagnostics">
            <summary>Diagnóstico del borrador y la generación</summary>
            <dl>
              <div><dt>Recibidos por React</dt><dd>{recommendationCandidateSummary.counts.received + (recommendations.actividades || []).length}</dd></div>
              <div><dt>Normalizados y deduplicados</dt><dd>{normalizedCandidates.length}</dd></div>
              <div><dt>Elegibles backend / planificador</dt><dd>{recommendationCandidateSummary.counts.backendEligible} / {recommendationCandidateSummary.counts.eligible}</dd></div>
              <div><dt>Seleccionados / asignados</dt><dd>{generationDiagnostics ? `${generationDiagnostics.selected} / ${generationDiagnostics.assigned}` : 'Aún no se ha generado'}</dd></div>
              <div><dt>Actividades visibles en borrador</dt><dd>{effectiveActivityCount}</dd></div>
              <div><dt>Días con asignación</dt><dd>{draftDaySummary.filter((day) => day.count > 0).length} de {draftDaySummary.length}</dd></div>
              <div><dt>Referencias huérfanas conservadas</dt><dd>{draftReconciliation.orphanCount}</dd></div>
              <div><dt>Descartes del planificador</dt><dd>{generationDiagnostics?.discarded
                ? Object.entries(generationDiagnostics.discarded).map(([reason, count]) => `${reason}: ${count}`).join(' · ') || 'Ninguno'
                : 'Aún no se ha ejecutado la generación'}</dd></div>
              <div><dt>Errores frontend</dt><dd>{[error, recommendationLoadError].filter(Boolean).join(' · ') || generationDiagnostics?.error || 'Ninguno registrado'}</dd></div>
            </dl>
            {recommendations.diagnostico?.errores?.map((diagnosticError, index) => <p role="status" key={`pipeline-${index}:${diagnosticError}`}>{diagnosticError}</p>)}
          </details>}
          {recommendationLoadError && <p className="external-recommendations__warning" role="alert">{recommendationLoadError}</p>}
          {recommendationLoadError && <button type="button" onClick={load} disabled={loading}>Reintentar recomendaciones</button>}
          {recommendations.avisosFuentes?.length > 0 && !loading && (
            <button type="button" onClick={load} disabled={loading}>
              Reintentar búsqueda de lugares
            </button>
          )}
          <button type="button" className="itinerary-suggest-button" onClick={buildSuggestedDraft} disabled={loading || savingDraft || !planningDays.length}>
            <Sparkles size={16} /> Generar itinerario sugerido
          </button>
          {!recommendations.recomendacionesExternas?.length && !loading && !recommendations.requiereSeleccionarIntereses && !recommendations.avisosFuentes?.length && (
            <p className="activity-empty">No encontramos lugares públicos que coincidan con los intereses del viaje.</p>
          )}
          <details className="recommendation-catalog">
            <summary>Explorar más lugares · {filteredRecommendations.length} atractivos aptos para itinerario</summary>
            <label className="recommendation-catalog__search">Buscar por nombre, categoría o zona
              <input type="search" value={recommendationSearch} onChange={(event) => {
                setRecommendationSearch(event.target.value); setRecommendationPage(0);
              }} placeholder="Museo, parque, barrio…" />
            </label>
            <p>El plan automático usa primero señales turísticas verificables; esta lista secundaria permite explorar opciones adicionales.</p>
          <div className="activity-catalog">
            {recommendationPageData.items.map((place) => {
              const key = `external:${place.id}`;
              const days = planningDays;
              const selectedDay = selectedDays[key] || days[0]?.id || '';
              return (
                <article className="activity-catalog__item external-place-card" key={place.id}>
                  {place.imagenUrl && !failedExternalImages[place.id] && <figure className="external-place-card__image">
                    <img src={place.imagenUrl} alt={`Fotografía verificada de ${place.nombre}`} loading="lazy"
                      referrerPolicy="no-referrer" onError={() => setFailedExternalImages((current) => ({ ...current, [place.id]: true }))} />
                    {place.imagenAtribucion && <figcaption>
                      Foto: {place.imagenAtribucion}{place.imagenLicencia ? ` · ${place.imagenLicencia}` : ''}
                      {place.imagenFuenteUrl && <> · <a href={place.imagenFuenteUrl} target="_blank" rel="noreferrer">fuente</a></>}
                      {place.imagenLicenciaUrl && <> · <a href={place.imagenLicenciaUrl} target="_blank" rel="noreferrer">licencia</a></>}
                    </figcaption>}
                  </figure>}
                  <div className="activity-catalog__details">
                    <strong>{place.nombre}</strong>
                    {place.descripcion && <p>{place.descripcion}</p>}
                    <small>{place.categoria}{place.ubicacion ? ` · ${place.ubicacion}` : ''}</small>
                    {place.proveedor && <small>Fuente de datos: {place.proveedor}</small>}
                    <small>{tourismScopeLabel(place.alcance)}</small>
                    {place.distanciaDestinoKm != null && <small>{Number(place.distanciaDestinoKm).toFixed(1)} km del centro geocodificado</small>}
                    <small>{place.precio == null ? 'Precio no disponible' : money(place.precio)} · {place.disponibilidad}</small>
                    <span className="activity-recommendation-reason">{place.motivo}</span>
                    <a href={place.urlFuente} target="_blank" rel="noreferrer">Ver ficha geográfica</a>
                  </div>
                  {days.length > 0 ? (
                    <div className="activity-catalog__action">
                      {isRegionalTourismCandidate(place) && <label className="regional-candidate-confirmation">
                        <input type="checkbox" checked={Boolean(confirmedRegionalPlaces[place.id])}
                          onChange={(event) => setConfirmedRegionalPlaces((current) => ({ ...current, [place.id]: event.target.checked }))} />
                        <span>{place.alcance === 'EXCURSION_OPTIONAL'
                          ? 'Es una excursión opcional. Confirmo que revisaré la ruta, transporte y duración antes de proponerla.'
                          : 'Es una opción regional. Confirmo que revisaré el traslado y la duración antes de proponerla.'}</span>
                      </label>}
                      <select aria-label={`Día para ${place.nombre}`} value={selectedDay} onChange={(event) => setSelectedDays((current) => ({ ...current, [key]: event.target.value }))}>
                        {days.map((day, index) => <option key={day.id} value={day.id}>Día {index + 1} · {day.fecha}</option>)}
                      </select>
                      <button type="button" onClick={() => addExternalRecommendation(place)} disabled={Boolean(draftReconciliation.external[place.id]) || (isRegionalTourismCandidate(place) && !confirmedRegionalPlaces[place.id])}>
                        <Plus size={16} /> {draftReconciliation.external[place.id] ? 'En la propuesta' : 'Añadir a propuesta'}
                      </button>
                    </div>
                  ) : <small>Genera el itinerario para poder elegir un día.</small>}
                </article>
              );
            })}
          </div>
            <div className="recommendation-catalog__pages">
              <span>{recommendationPageData.total} resultado(s) · página {recommendationPageData.page + 1} de {recommendationPageData.pageCount}</span>
              <button type="button" disabled={recommendationPageData.page <= 0} onClick={() => setRecommendationPage((page) => Math.max(0, page - 1))}>Anterior</button>
              <button type="button" disabled={recommendationPageData.page + 1 >= recommendationPageData.pageCount}
                onClick={() => setRecommendationPage((page) => Math.min(recommendationPageData.pageCount - 1, page + 1))}>Siguiente</button>
            </div>
          </details>
          {hasPendingPlan && <section className="external-draft" aria-label="Acciones para cambios pendientes">
            <div className="activity-budget__title"><h3>Borrador local · cambios pendientes</h3></div>
            <p>Los cambios se muestran en el itinerario y el mapa. Todavía no se guardan en Oracle; confirma explícitamente para persistirlos.</p>
            <div className="itinerary-draft-actions">
              <button type="button" disabled={loading || savingDraft} onClick={() => buildSuggestedDraft('keep')}>Completar días vacíos</button>
              <button type="button" disabled={loading || savingDraft} onClick={() => buildSuggestedDraft('replace')}>Generar otra propuesta</button>
              <button type="button" onClick={() => document.getElementById('trip-itinerary-days')?.scrollIntoView({ behavior: 'smooth', block: 'start' })}>Editar itinerario</button>
              <button type="button" disabled={exportingPdf || loading} onClick={exportPdf}>{exportingPdf ? 'Preparando PDF…' : 'Exportar PDF'}</button>
            </div>
            {hasSavablePackage && <button type="button" disabled={savingDraft || loading || !packageSyncReady || Boolean(packageConflict) || pendingSaveCount === 0 || ['server-changed', 'destination-changed', 'dates-changed', 'corrupt', 'server-unavailable'].includes(draftStorageStatus)} onClick={savePlanningDraft}>
              {savingDraft ? 'Guardando itinerario…' : `Guardar itinerario (${pendingSaveCount} cambios · ${effectiveActivityCount} actividades)`}
            </button>}
            <button type="button" disabled={savingDraft} onClick={discardLocalDraft}>Cancelar cambios
            </button>
          </section>}
          <small className="osm-attribution">© OpenStreetMap contributors · Datos bajo licencia <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">ODbL</a>.</small>
        </section>

        <section className="saved-itinerary" id="trip-itinerary-days">
          <div className="activity-budget__title"><h3>Itinerario de mi viaje</h3>
            {itinerario && !recommendations?.requiereSeleccionarIntereses && <button type="button" disabled={saving || loading} onClick={prepararGeneracion}>Revisar actividades del catálogo</button>}
            {!itinerario && <button type="button" disabled={saving || loading || !planningDays.length} onClick={prepararGeneracion}>Crear días del viaje</button>}
            <button type="button" disabled={exportingPdf || loading} onClick={exportPdf}>
              {exportingPdf ? 'Preparando PDF…' : 'Exportar itinerario a PDF'}
            </button>
          </div>
          <p>{hasPendingPlan ? 'Los días muestran el borrador local pendiente de guardar.' : itinerario?.dias?.length ? 'Mostrando el itinerario guardado.' : 'Días temporales listos para planificar; todavía no hay datos guardados en Oracle.'}</p>
          {generationPreview && <section className="external-draft" aria-label="Propuesta del catálogo interno">
            <div className="activity-budget__title"><h3>Revisar propuesta del catálogo</h3></div>
            <p>{itinerario ? 'El backend añadirá actividades internas compatibles sin reemplazar las actividades guardadas.' : 'Los días que ves son temporales. Al confirmar, se guardarán los días del viaje; después podrás revisar y guardar actividades en un borrador separado.'} No se inventan horarios o disponibilidad.</p>
            {recommendations.actividades?.length ? <ul>{recommendations.actividades.map((item) => <li key={item.actividad.id}>
              <span><strong>{item.actividad.nombre}</strong> · disponible según la ficha interna; interés: {(item.interesesCoincidentes || []).join(', ') || 'sin coincidencia detallada'}</span>
              <small>{item.actividad.costo == null ? 'Precio no disponible' : money(item.actividad.costo)}</small>
            </li>)}</ul> : <p>No hay actividades del catálogo que coincidan con intereses y fechas. Los lugares externos se revisan y guardan por separado.</p>}
            <button type="button" disabled={saving || loading} onClick={generarItinerario}>{saving ? 'Guardando…' : itinerario ? 'Confirmar y guardar propuesta del catálogo' : 'Confirmar y crear días del itinerario'}</button>
            <button type="button" disabled={saving} onClick={() => setGenerationPreview(false)}>Cancelar</button>
          </section>}
          {!loading && !itinerario && <p>Todavía no hay un itinerario guardado. Los días siguientes son temporales y se guardarán junto con una propuesta confirmada.</p>}
          {!loading && !planningDays.length && !itinerario && <p>Las fechas del viaje no son válidas para crear días temporales.</p>}
          {!loading && draftItineraryDays.length ? <div className="planning-scroll-region planning-scroll-region--itinerary" aria-label="Días y actividades del itinerario" tabIndex="0">{draftItineraryDays.map((day, index) => (
            <article className="trip-list-item itinerary-day" key={day.id}>
              <div><strong>Día {index + 1}: {day.fecha}{day.temporal ? ' · temporal' : ''}</strong>
              {(day.actividades || []).length === 0 ? <p>Sin actividades asignadas.</p> : <ol className="saved-activity-list">{(day.actividades || []).map((activity, activityIndex) => {
                  const activityId = String(activity.id);
                  if (activityId.startsWith('external:') || activityId.startsWith('catalog:') || activityId.startsWith('manual:')) {
                    const externalId = activityId.startsWith('external:') ? activityId.slice('external:'.length) : null;
                    const catalogId = activityId.startsWith('catalog:') ? activityId.slice('catalog:'.length) : null;
                    const manualId = activityId.startsWith('manual:') ? activity.id : null;
                    const isExternal = externalId != null;
                    const isCatalog = catalogId != null;
                    const mapId = mapMarkerIdForActivity(activity.id);
                    return <li key={activity.id} className={`draft-activity-row${selectedMapActivityId === mapId ? ' map-selected-activity' : ''}`}>
                      <span><strong>{activity.orden}. {activity.nombre}</strong>{activity.categoria ? ` · ${activity.categoria}` : ''}{activity.ubicacion ? ` · ${activity.ubicacion}` : ''}</span>
                      <small>{activity.costo == null ? 'Costo desconocido' : `${activity.costoEstado === 'MANUAL_ESTIMATE' ? 'Estimación manual' : 'Importe registrado'}: ${money(activity.costo)} ${activity.moneda || 'COP'}`} · Horario flexible, por verificar</small>
                      {isExternal && activity.urlFuente && <a href={activity.urlFuente} target="_blank" rel="noreferrer">Fuente pública</a>}
                      <div className="saved-activity-actions">
                        {activity.latitud != null && activity.longitud != null && <button type="button" className="itinerary-map-link" onClick={() => showActivityOnMap(activity, day)}><MapPin size={15} /> Ver en mapa</button>}
                        {isExternal && <>
                          <button type="button" aria-label={`Subir ${activity.nombre}`} disabled={draftExternalOrder.indexOf(externalId) <= 0} onClick={() => setDraftExternalOrder((current) => {
                            const next = [...current]; const from = next.indexOf(externalId);
                            if (from > 0) [next[from - 1], next[from]] = [next[from], next[from - 1]];
                            return next;
                          })}>↑</button>
                          <button type="button" aria-label={`Bajar ${activity.nombre}`} disabled={draftExternalOrder.indexOf(externalId) === draftExternalOrder.length - 1} onClick={() => setDraftExternalOrder((current) => {
                            const next = [...current]; const from = next.indexOf(externalId);
                            if (from >= 0 && from < next.length - 1) [next[from + 1], next[from]] = [next[from], next[from + 1]];
                            return next;
                          })}>↓</button>
                        </>}
                        <label>Cambiar día<select aria-label={`Cambiar día de ${activity.nombre}`} value={day.id}
                          onChange={(event) => {
                            const target = event.target.value;
                            if (isExternal) setDraftExternal((current) => ({ ...current, [externalId]: target }));
                            else if (isCatalog) setDraftCatalog((current) => ({ ...current, [catalogId]: target }));
                            else setDraftManual((current) => current.map((item) => item.id === manualId ? { ...item, dayId: target } : item));
                          }}>
                          {draftItineraryDays.map((option, dayIndex) => <option key={option.id} value={option.id}>Día {dayIndex + 1} · {option.fecha}</option>)}
                        </select></label>
                        <button type="button" onClick={() => {
                          if (isExternal) {
                            setDraftExternal((current) => { const next = { ...current }; delete next[externalId]; return next; });
                            setDraftExternalOrder((current) => current.filter((id) => id !== externalId));
                          } else if (isCatalog) setDraftCatalog((current) => { const next = { ...current }; delete next[catalogId]; return next; });
                          else setDraftManual((current) => current.filter((item) => item.id !== manualId));
                        }}>Quitar del borrador</button>
                      </div>
                    </li>;
                  }
                  const assignmentKey = `${day.id}:${activity.id}`;
                  const isUpdating = updatingAssignment === assignmentKey;
                  const movableDays = (itinerario?.dias || []).filter((candidate) => candidate.id !== day.id && isAvailableOn(activity, candidate.fecha));
                  const mapId = mapMarkerIdForActivity(activity.id);
                  return <li key={activity.id} className={selectedMapActivityId === mapId ? 'map-selected-activity' : ''}>
                    {activity.orden}. {activity.nombre}{activity.ubicacion ? ` · ${activity.ubicacion}` : ''}
                    {activity.costo != null ? ` · ${money(activity.costo)}` : ' · Precio no disponible'}
                    {activity.fechaDisponibleInicio == null && activity.fechaDisponibleFin == null ? ' · Fechas no verificadas' : ''}
                    <div className="saved-activity-actions">
                      {activity.latitud != null && activity.longitud != null && <button type="button" className="itinerary-map-link" onClick={() => showActivityOnMap(activity, day)}><MapPin size={15} /> Ver en mapa</button>}
                      <button type="button" aria-label={`Subir ${activity.nombre}`} title="Subir actividad" disabled={isUpdating || activityIndex === 0} onClick={() => reorderActivity(day, activity, activityIndex)}><ArrowUp size={15} /></button>
                      <button type="button" aria-label={`Bajar ${activity.nombre}`} title="Bajar actividad" disabled={isUpdating || activityIndex === day.actividades.length - 1} onClick={() => reorderActivity(day, activity, activityIndex + 2)}><ArrowDown size={15} /></button>
                      {movableDays.length > 0 && <label className="saved-activity-move"><CalendarDays size={15} /><span className="visually-hidden">Mover {activity.nombre} a otro día</span>
                        <select aria-label={`Mover ${activity.nombre} a otro día`} value="" disabled={isUpdating} onChange={(event) => moveActivity(day, activity, event.target.value)}>
                          <option value="">Mover a otro día</option>
                          {movableDays.map((candidate) => <option value={candidate.id} key={candidate.id}>{candidate.fecha}</option>)}
                        </select>
                      </label>}
                      <button type="button" aria-label={`Quitar ${activity.nombre} del día`} disabled={isUpdating} onClick={() => removeActivity(day, activity)}><Trash2 size={15} /></button>
                    </div>
                  </li>
                })}
                </ol>}
                {!day.temporal && <DayTransfers token={token} tripId={trip.id} day={{ ...day, actividades: (day.actividades || []).filter((activity) => !String(activity.id).startsWith('external:') && !String(activity.id).startsWith('catalog:') && !String(activity.id).startsWith('manual:')) }} lodging={lodging} />}
                <details>
                  <summary>Agregar actividad manual</summary>
                  <form onSubmit={(event) => addManualActivity(event, day)} className="trip-edit-form">
                    <label>Nombre<input required maxLength="150" value={activityForms[day.id]?.nombre || ''} onChange={(event) => setActivityForms((current) => ({ ...current, [day.id]: { ...current[day.id], nombre: event.target.value } }))} /></label>
                    <label>Descripción<input maxLength="500" value={activityForms[day.id]?.descripcion || ''} onChange={(event) => setActivityForms((current) => ({ ...current, [day.id]: { ...current[day.id], descripcion: event.target.value } }))} /></label>
                    <label>Ubicación<input maxLength="250" value={activityForms[day.id]?.ubicacion || ''} onChange={(event) => setActivityForms((current) => ({ ...current, [day.id]: { ...current[day.id], ubicacion: event.target.value } }))} /></label>
                    <label>Costo<input type="number" min="0" step="0.01" value={activityForms[day.id]?.costo || ''} onChange={(event) => setActivityForms((current) => ({ ...current, [day.id]: { ...current[day.id], costo: event.target.value } }))} /></label>
                    <label>Disponible desde<input type="date" value={activityForms[day.id]?.fechaDisponibleInicio || ''} onChange={(event) => setActivityForms((current) => ({ ...current, [day.id]: { ...current[day.id], fechaDisponibleInicio: event.target.value } }))} /></label>
                    <label>Disponible hasta<input type="date" value={activityForms[day.id]?.fechaDisponibleFin || ''} onChange={(event) => setActivityForms((current) => ({ ...current, [day.id]: { ...current[day.id], fechaDisponibleFin: event.target.value } }))} /></label>
                    <label>Intereses separados por coma<input maxLength="500" value={activityForms[day.id]?.intereses || ''} onChange={(event) => setActivityForms((current) => ({ ...current, [day.id]: { ...current[day.id], intereses: event.target.value } }))} /></label>
                    <button type="submit">Guardar actividad</button>
                  </form>
                </details>
              </div>
            </article>
          ))}</div> : null}
        </section>
      </div>
      </div>
      <div className="package-budget-view" hidden={view !== 'budget'}>
        <TripGlobalBudget trip={trip} token={token} userId={userId} lodging={packageLodging || lodging} selectedFlight={packageFlight}
          itineraryDays={draftItineraryDays} persistedExpenses={persistedPackageExpenses}
          persistedTravelers={persistedPackageTravelers} onScenarioChange={updateBudgetScenario} />
      </div>
    </section>
  );
}

function mapMarkerIdForActivity(id) {
  const value = String(id ?? '');
  if (value.startsWith('external:')) return value.slice('external:'.length);
  if (value.startsWith('catalog:')) return `catalog-${value.slice('catalog:'.length)}`;
  if (value.startsWith('manual:')) return `manual-${value.slice('manual:'.length)}`;
  return `catalog-${value}`;
}

function isAvailableOn(activity, date) {
  return (!activity.fechaDisponibleInicio || activity.fechaDisponibleInicio <= date)
    && (!activity.fechaDisponibleFin || activity.fechaDisponibleFin >= date);
}

function availabilityLabel(activity) {
  if (!activity.fechaDisponibleInicio && !activity.fechaDisponibleFin) return 'Fechas no informadas (no verificadas)';
  return `Disponible ${activity.fechaDisponibleInicio || 'sin límite'} – ${activity.fechaDisponibleFin || 'sin límite'}`;
}

function money(amount) {
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 2 }).format(Number(amount || 0));
}

function DayTransfers({ token, tripId, day, lodging }) {
  const [results, setResults] = useState({});
  const [loadingKey, setLoadingKey] = useState('');
  const [error, setError] = useState('');
  const activities = day.actividades || [];
  const legs = [];
  if (lodging && activities.length && hasCoordinates(lodging)) {
    if (hasCoordinates(activities[0])) legs.push({ key: `hotel-start-${activities[0].id}`, label: `${lodging.nombre} → ${activities[0].nombre}`, from: lodging, to: activities[0] });
  }
  for (let index = 0; index < activities.length - 1; index += 1) {
    legs.push({ key: `activities-${activities[index].id}-${activities[index + 1].id}`,
      label: `${activities[index].nombre} → ${activities[index + 1].nombre}`, from: activities[index], to: activities[index + 1] });
  }
  if (lodging && activities.length && hasCoordinates(lodging) && hasCoordinates(activities.at(-1))) {
    legs.push({ key: `hotel-return-${activities.at(-1).id}`, label: `${activities.at(-1).nombre} → ${lodging.nombre}`, from: activities.at(-1), to: lodging });
  }

  const calculate = async (leg) => {
    if (!window.confirm('Se consultará OpenRouteService para calcular la ruta en automóvil. ¿Continuar?')) return;
    setError(''); setLoadingKey(leg.key);
    try {
      const response = await apiRequest(`/api/viajes/${tripId}/traslados`, {
        method: 'POST', token,
        body: JSON.stringify({ origin: point(leg.from), destination: point(leg.to) }),
      });
      setResults((current) => ({ ...current, [leg.key]: response.routes || [] }));
    } catch (requestError) { setError(requestError.message); }
    finally { setLoadingKey(''); }
  };

  if (!activities.length) return null;
  return <section className="itinerary-transfers" aria-label={`Tiempos de traslado para ${day.fecha}`}>
    <h4>Tiempos entre ubicaciones</h4>
    <p>Las rutas de automóvil se calculan mediante OpenRouteService. El proveedor y la cobertura pueden no reflejar cierres o condiciones de tráfico en tiempo real.</p>
    {!legs.length && <small>Agrega coordenadas a las actividades y al alojamiento para calcular traslados.</small>}
    {error && <p className="form-error" role="alert">{error}</p>}
    {legs.map((leg) => <article key={leg.key}>
      <strong>{leg.label}</strong>
      <button type="button" disabled={loadingKey === leg.key} onClick={() => calculate(leg)}>
        {loadingKey === leg.key ? 'Calculando…' : 'Calcular distancia y tiempo en automóvil (ORS)'}
      </button>
      {results[leg.key]?.map((route) => <p key={route.mode}>
        {routeLabel(route.mode)}: {route.available ? `${Math.ceil(route.durationSeconds / 60)} min${route.distanceMeters ? ` · ${(route.distanceMeters / 1000).toFixed(1)} km` : ''}` : route.message}
        {route.exceedsTwoHours && <strong role="alert"> · Advertencia: supera 120 minutos</strong>}
      </p>)}
    </article>)}
  </section>;
}

function hasCoordinates(value) {
  return Number.isFinite(Number(value?.latitud)) && Number.isFinite(Number(value?.longitud))
    && value?.latitud !== null && value?.latitud !== '' && value?.longitud !== null && value?.longitud !== '';
}

function point(value) { return { latitude: Number(value.latitud), longitude: Number(value.longitud) }; }
function routeLabel(mode) { return ({ WALK: 'Caminando', TRANSIT: 'Transporte público', DRIVE: 'Automóvil' })[mode] || mode; }
function safeLocalStorage() {
  try { return typeof window === 'undefined' ? null : window.localStorage; } catch { return null; }
}
function mergePackageFlight(savedFlights, selectedFlight) {
  if (!selectedFlight?.id || savedFlights.some((flight) => String(flight.id) === String(selectedFlight.id))) return savedFlights;
  return [...savedFlights, selectedFlight];
}

function packageSnapshotToItinerary(snapshot, tripId) {
  if (!snapshot || !Array.isArray(snapshot.dias)) return null;
  return { viajeId: Number(tripId), paquetePersistido: true, dias: snapshot.dias.map((day) => ({
    id: day.diaId ?? `package:${tripId}:${day.fecha}`,
    fecha: day.fecha,
    actividades: (day.actividades || []).map((activity) => ({
      id: activity.tipo === 'EXTERNA' ? `external:${activity.idExterno}`
        : activity.tipo === 'INTERNA' ? `catalog:${activity.actividadId}` : activity.claveCliente,
      claveCliente: activity.claveCliente,
      nombre: activity.nombre,
      descripcion: activity.descripcion,
      ubicacion: activity.ubicacion,
      latitud: activity.latitud,
      longitud: activity.longitud,
      orden: activity.orden,
      horaLocal: activity.horaLocal,
      duracionMinutos: activity.duracionMinutos,
      costo: activity.costo?.importe ?? null,
      moneda: activity.costo?.moneda ?? null,
      costoEstado: activity.costo?.tipo || 'DESCONOCIDO',
      fuente: activity.fuente || (activity.tipo === 'MANUAL' ? 'Borrador manual' : 'Catálogo interno'),
      proveedor: activity.fuente,
      idExterno: activity.idExterno,
      tipoActividad: activity.tipo,
    })),
  })) };
}

function flightSelectionFromPackage(flight) {
  if (!flight) return null;
  const tramos = (flight.tramos || []).map((segment) => ({
    origin: { iataCode: segment.origenIata }, destination: { iataCode: segment.destinoIata },
    departingAt: segment.salida, arrivingAt: segment.llegada, numeroTrayecto: segment.numeroTrayecto,
    marketingCarrier: { name: segment.aerolinea }, marketingCarrierFlightNumber: segment.numeroVuelo,
  }));
  return { id: flight.idOferta || flight.vueloId, provider: flight.proveedor,
    totalAmount: flight.importe, currency: flight.moneda, testData: flight.tipo === 'SANDBOX',
    origin: { iataCode: flight.origen }, destination: { iataCode: flight.destino },
    slices: [...new Set(tramos.map((segment) => segment.numeroTrayecto || 1))].sort((a, b) => a - b).map((sliceNumber) => {
      const segments = tramos.filter((segment) => (segment.numeroTrayecto || 1) === sliceNumber);
      return { origin: segments[0]?.origin || { iataCode: flight.origen },
        destination: segments.at(-1)?.destination || { iataCode: flight.destino },
        departureAt: segments[0]?.departingAt || flight.salida,
        arrivalAt: segments.at(-1)?.arrivingAt || flight.llegada, segments };
    }) };
}

function lodgingSelectionFromPackage(lodging) {
  if (!lodging) return null;
  return { id: lodging.idExterno || lodging.alojamientoId, proveedor: lodging.fuente,
    nombre: lodging.nombre, direccion: lodging.direccion, latitud: lodging.latitud, longitud: lodging.longitud };
}
