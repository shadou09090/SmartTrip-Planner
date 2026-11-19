import React, { createContext, useCallback, useContext, useEffect, useMemo, useReducer, useState } from 'react';
import { readPackageSelections, writePackageSelections } from '../lib/tripPackageWorkspaceStorage';

const WorkspaceContext = createContext(null);
const initialDraftState = (selections) => ({
  draftDays: [],
  draftExternal: {},
  draftExternalOrder: [],
  draftCatalog: {},
  draftManual: [],
  effectiveItineraryDays: [],
  selectedMapActivityId: null,
  selectedMapDayId: 'all',
  destinationLocation: null,
  selectedFlight: selections.selectedFlight || null,
  selectedLodging: selections.selectedLodging || null,
  selectionStorageStatus: selections.status || 'unavailable',
  packageRevision: 0,
  savedPackageSignature: '',
});

function reducer(state, { key, value }) {
  const nextValue = typeof value === 'function' ? value(state[key]) : value;
  if (Object.is(nextValue, state[key])) return state;
  const selectionChanged = key === 'selectedFlight' || key === 'selectedLodging';
  return { ...state, [key]: nextValue, ...(selectionChanged ? { selectionStorageStatus: 'dirty' } : {}) };
}

export function TripPackageWorkspaceProvider({ userId, tripId, children }) {
  const [state, dispatch] = useReducer(reducer, null, () => initialDraftState(
    readPackageSelections(safeLocalStorage(), userId, tripId),
  ));
  const setField = useCallback((key, value) => dispatch({ key, value }), []);
  const actions = useMemo(() => ({
    setDraftDays: (value) => setField('draftDays', value),
    setDraftExternal: (value) => setField('draftExternal', value),
    setDraftExternalOrder: (value) => setField('draftExternalOrder', value),
    setDraftCatalog: (value) => setField('draftCatalog', value),
    setDraftManual: (value) => setField('draftManual', value),
    setEffectiveItineraryDays: (value) => setField('effectiveItineraryDays', value),
    setSelectedMapActivityId: (value) => setField('selectedMapActivityId', value),
    setSelectedMapDayId: (value) => setField('selectedMapDayId', value),
    setDestinationLocation: (value) => setField('destinationLocation', value),
    setSelectedFlight: (value) => setField('selectedFlight', value),
    setSelectedLodging: (value) => setField('selectedLodging', value),
    setSelectionStorageStatus: (value) => setField('selectionStorageStatus', value),
    setPackageRevision: (value) => setField('packageRevision', value),
    setSavedPackageSignature: (value) => setField('savedPackageSignature', value),
  }), [setField]);
  const value = useMemo(() => ({ ...state, ...actions }), [actions, state]);

  useEffect(() => {
    if (['corrupt', 'unavailable'].includes(state.selectionStorageStatus)) return;
    const saved = writePackageSelections(safeLocalStorage(), userId, tripId, {
      selectedFlight: state.selectedFlight,
      selectedLodging: state.selectedLodging,
    });
    const nextStatus = saved ? 'ready' : 'write-failed';
    if (nextStatus !== state.selectionStorageStatus) setField('selectionStorageStatus', nextStatus);
  }, [setField, state.selectedFlight, state.selectedLodging, state.selectionStorageStatus, tripId, userId]);

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useTripPackageWorkspace() {
  return useContext(WorkspaceContext);
}

export function useWorkspaceState(key, fallback) {
  const workspace = useTripPackageWorkspace();
  const [localValue, setLocalValue] = useState(fallback);
  const setterName = `set${key[0].toUpperCase()}${key.slice(1)}`;
  return workspace ? [workspace[key], workspace[setterName]] : [localValue, setLocalValue];
}

function safeLocalStorage() {
  try { return window.localStorage; } catch { return null; }
}
