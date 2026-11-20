import React, { useEffect, useMemo, useState } from 'react';
import { BedDouble, CalendarDays, ClipboardList, Plane, Users, Wallet } from 'lucide-react';
import { Navbar, Footer } from './components/layout';
import { CreateTrip, Home, Login, MapPlaces, MyTrips, Register } from './pages';
import MapPlacesErrorBoundary from './components/MapPlacesErrorBoundary';
import GeneradorItinerario from './components/GeneradorItinerario';
import TripPlanningExtras from './components/TripPlanningExtras';
import FlightSearchPanel from './components/FlightSearchPanel';
import { TripPackageWorkspaceProvider } from './components/TripPackageWorkspace';
import { loadPackagePreferences } from './lib/packagePreferences';
import './styles/package-tabs.css';

const SESSION_KEY = 'smarttrip.session';

function restoreSession() {
  try {
    const saved = sessionStorage.getItem(SESSION_KEY);
    if (!saved) return null;
    const session = JSON.parse(saved);
    if (!session.accessToken || session.expiresAt <= Date.now()) {
      sessionStorage.removeItem(SESSION_KEY);
      return null;
    }
    return session;
  } catch {
    sessionStorage.removeItem(SESSION_KEY);
    return null;
  }
}

export default function App() {
  const [session, setSession] = useState(restoreSession);
  const [currentPage, setCurrentPage] = useState(() => session?.localDevSession ? 'local-duffel' : session ? 'trips' : 'home');
  const [activeTrip, setActiveTrip] = useState(null);
  const [autoPackageTripId, setAutoPackageTripId] = useState(null);
  const [openTripId, setOpenTripId] = useState(() => new URLSearchParams(window.location.search).get('tripId'));
  const [hasUnsavedTripChanges, setHasUnsavedTripChanges] = useState(false);
  const [activePackageTab, setActivePackageTab] = useState('itinerary');
  const [visitedPackageTabs, setVisitedPackageTabs] = useState(() => new Set(['itinerary']));
  const [packageSummary, setPackageSummary] = useState(null);
  const isAuthenticated = Boolean(session?.accessToken);

  const updateTripUrl = (tripId, replace = false) => {
    const url = new URL(window.location.href);
    if (tripId == null) url.searchParams.delete('tripId');
    else url.searchParams.set('tripId', String(tripId));
    window.history[replace ? 'replaceState' : 'pushState']({ tripId }, '', `${url.pathname}${url.search}${url.hash}`);
    setOpenTripId(tripId == null ? null : String(tripId));
  };

  const openTrip = (trip, { startPackage = false } = {}) => {
    const preferences = loadPackagePreferences(safeLocalStorage(), session?.user?.id, trip.id);
    const preparedTrip = { ...trip, ...(preferences || {}) };
    setActiveTrip(preparedTrip);
    setAutoPackageTripId(startPackage ? String(trip.id) : null);
    setActivePackageTab('itinerary');
    setVisitedPackageTabs(new Set(['itinerary']));
    setPackageSummary(null);
    setCurrentPage('map');
    const currentId = new URLSearchParams(window.location.search).get('tripId');
    updateTripUrl(trip.id, currentId === String(trip.id));
  };

  const backToTrips = () => {
    if (hasUnsavedTripChanges && !window.confirm('Hay cambios locales pendientes. Si sales, el borrador seguirá guardado en este navegador y no se enviará a Oracle. ¿Continuar?')) return;
    updateTripUrl(null);
    setActiveTrip(null);
    setAutoPackageTripId(null);
    setPackageSummary(null);
    setCurrentPage('trips');
  };

  useEffect(() => {
    const handlePendingChanges = (event) => {
      setHasUnsavedTripChanges(Boolean(event.detail?.pending));
    };
    window.addEventListener('smarttrip:unsaved-changes', handlePendingChanges);
    return () => window.removeEventListener('smarttrip:unsaved-changes', handlePendingChanges);
  }, []);

  useEffect(() => {
    const handlePopState = () => {
      const id = new URLSearchParams(window.location.search).get('tripId');
      setOpenTripId(id);
      if (!id) {
        setActiveTrip(null);
        setCurrentPage(session?.accessToken ? 'trips' : 'home');
      } else if (String(activeTrip?.id) === id) {
        setCurrentPage('map');
      } else {
        setCurrentPage('trips');
      }
    };
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [activeTrip?.id, session?.accessToken]);

  const handleLogin = (loginResponse) => {
    const nextSession = {
      ...loginResponse,
      expiresAt: Date.now() + (loginResponse.expiresIn * 1000),
    };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(nextSession));
    setSession(nextSession);
    setCurrentPage(nextSession.localDevSession ? 'local-duffel' : 'trips');
  };

  const handleLogout = () => {
    if (hasUnsavedTripChanges && !window.confirm('Hay cambios locales pendientes. El borrador seguirá guardado en este navegador y no se enviará a Oracle. ¿Cerrar sesión?')) return;
    updateTripUrl(null, true);
    sessionStorage.removeItem(SESSION_KEY);
    setSession(null);
    setActiveTrip(null);
    setAutoPackageTripId(null);
    setPackageSummary(null);
    setCurrentPage('home');
  };

  const handleNavigate = (page) => {
    if (hasUnsavedTripChanges && page !== 'map'
        && !window.confirm('Hay cambios locales pendientes. El borrador seguirá guardado en este navegador y no se enviará a Oracle. ¿Salir de este viaje?')) return;
    if (session?.localDevSession && !['local-duffel', 'home'].includes(page)) {
      setCurrentPage('local-duffel');
      return;
    }
    if ((page === 'create-trip' || page === 'map' || page === 'trips') && !isAuthenticated) {
      setCurrentPage('login');
      return;
    }

    if (page === 'map' && !activeTrip) {
      setCurrentPage('create-trip');
      return;
    }

    if (page !== 'map' && openTripId) updateTripUrl(null);

    setCurrentPage(page);
  };

  const handleAddActivityFromMap = (placeName, placeLocation) => {
    const formatted = placeLocation ? `${placeName} · ${placeLocation}` : placeName;
    setActiveTrip((prev) => ({
      ...prev,
      pendingActivity: {
        id: `map-${Date.now()}`,
        texto: formatted,
        hora: 'Tarde',
      },
    }));
    if (activeTrip?.id) {
      selectPackageTab('itinerary');
    } else {
      setCurrentPage('itinerary');
    }
  };

  const packageTabs = useMemo(() => [
    { id: 'itinerary', label: 'Itinerario', Icon: CalendarDays },
    { id: 'flights', label: 'Vuelos', Icon: Plane },
    { id: 'lodging', label: 'Alojamientos y mapa', Icon: BedDouble },
    { id: 'budget', label: 'Presupuesto', Icon: Wallet },
    { id: 'summary', label: 'Resumen y PDF', Icon: ClipboardList },
    { id: 'collaborators', label: 'Colaboradores', Icon: Users },
  ], []);

  const selectPackageTab = (tabId) => {
    setActivePackageTab(tabId);
    setVisitedPackageTabs((current) => new Set([...current, tabId]));
    window.dispatchEvent(new CustomEvent('smarttrip:package-tab', { detail: { tripId: String(activeTrip?.id), tabId } }));
  };

  const tripDuration = (trip) => {
    const start = Date.parse(`${trip?.startDate}T00:00:00Z`);
    const end = Date.parse(`${trip?.endDate}T00:00:00Z`);
    return Number.isFinite(start) && Number.isFinite(end) && end >= start
      ? Math.floor((end - start) / 86_400_000) + 1 : null;
  };

  const renderPage = () => {
    switch (currentPage) {
      case 'login':
        return <Login onNavigate={handleNavigate} onLogin={handleLogin} />;
      case 'register':
        return <Register onNavigate={handleNavigate} />;
      case 'local-duffel':
        return session?.localDevSession
          ? <div className="local-duffel-page">
              <p className="message">Sesión temporal sin persistencia: solo búsqueda externa de lugares y ofertas.</p>
              <FlightSearchPanel
                trip={{ id: null, origin: '', destination: '', startDate: '', endDate: '' }}
                token={session.accessToken}
              />
            </div>
          : <Login onNavigate={handleNavigate} onLogin={handleLogin} />;
      case 'create-trip':
        return isAuthenticated
          ? (
            <CreateTrip
              token={session.accessToken}
              initialTrip={activeTrip}
              onTripCreated={setActiveTrip}
              onResetTrip={() => setActiveTrip(null)}
              userId={session.user?.id}
              onPackageCreated={(trip) => openTrip(trip, { startPackage: true })}
              onViewMap={() => activeTrip ? openTrip(activeTrip) : setCurrentPage('map')}
              onViewItinerary={() => setCurrentPage('itinerary')}
            />
          )
          : <Login onNavigate={handleNavigate} onLogin={handleLogin} />;
      case 'trips':
        return isAuthenticated
          ? <MyTrips token={session.accessToken} openTripId={openTripId}
              onCreateTrip={() => setCurrentPage('create-trip')} onSelectTrip={openTrip} onEditTrip={openTrip} />
          : <Login onNavigate={handleNavigate} onLogin={handleLogin} />;
      case 'map':
        if (!activeTrip) return <CreateTrip onTripCreated={setActiveTrip} />;
        if (isAuthenticated && activeTrip.id) {
          return (
            <TripPackageWorkspaceProvider key={`${session.user?.id || 'user'}:${activeTrip.id}`}
              userId={session.user?.id} tripId={activeTrip.id}>
              <div className={`trip-dashboard${autoPackageTripId === String(activeTrip.id) ? ' trip-dashboard--package' : ''}`}>
                <header className="package-header">
                  <div className="package-header__title">
                    <p className="eyebrow">SmartTrip · paquete de viaje</p>
                    <h1>{activeTrip.origin ? `${activeTrip.origin} → ` : ''}{activeTrip.destination}{activeTrip.country ? `, ${activeTrip.country}` : ''}</h1>
                    <p>{activeTrip.startDate} — {activeTrip.endDate} · {activeTrip.travelers || activeTrip.numeroViajeros || 1} viajero(s)
                      {tripDuration(activeTrip) ? ` · ${Math.max(0, tripDuration(activeTrip) - 1)} noche(s)` : ''}</p>
                  </div>
                  <div className="package-header__facts" aria-label="Estado del viaje">
                    <span><strong>Presupuesto</strong>{activeTrip.budget == null ? 'Sin monto' : `${activeTrip.budgetCurrency || 'COP'} ${Number(activeTrip.budget).toLocaleString('es-CO')}`}</span>
                    <span><strong>Viaje</strong>{activeTrip.id ? 'Guardado' : 'Local'}</span>
                    <span><strong>Itinerario</strong>{packageSummary?.itineraryPersisted ? 'Guardado' : packageSummary?.activityCount ? 'Borrador local' : 'Por planificar'}</span>
                    <span><strong>Actividades</strong>{packageSummary?.activityCount ?? '—'} · {packageSummary?.dayCount ?? tripDuration(activeTrip) ?? '—'} días</span>
                    {hasUnsavedTripChanges && <span className="package-header__pending" role="status">Cambios sin guardar</span>}
                  </div>
                </header>
                <nav className="package-tabs" role="tablist" aria-label="Secciones del paquete">
                  {packageTabs.map(({ id, label, Icon }) => <button key={id} id={`package-tab-${id}`} type="button" role="tab"
                    aria-selected={activePackageTab === id} aria-controls={['itinerary', 'budget', 'summary'].includes(id) ? 'package-panel-planner'
                      : ['flights', 'collaborators'].includes(id) ? 'package-panel-related' : `package-panel-${id}`}
                    tabIndex={activePackageTab === id ? 0 : -1}
                    onClick={() => selectPackageTab(id)} onKeyDown={(event) => handleTabKeyDown(event, packageTabs, selectPackageTab)}>
                    <Icon size={17} aria-hidden="true" /><span>{label}</span>
                  </button>)}
                </nav>
                <div className="package-tabpanels">
                  <section id="package-panel-planner" role="tabpanel" aria-labelledby={`package-tab-${activePackageTab}`}
                    hidden={!['itinerary', 'budget', 'summary'].includes(activePackageTab)}>
                    {['itinerary', 'budget', 'summary'].some((tab) => visitedPackageTabs.has(tab)) && <GeneradorItinerario key={activeTrip.id}
                      token={session.accessToken} trip={activeTrip} userId={session.user?.id} view={activePackageTab}
                      onSummaryChange={setPackageSummary} onNavigateTab={selectPackageTab}
                      autoCreateDraft={autoPackageTripId === String(activeTrip.id)} />}
                  </section>
                  <section id="package-panel-lodging" role="tabpanel" aria-labelledby="package-tab-lodging" hidden={activePackageTab !== 'lodging'}>
                    {visitedPackageTabs.has('lodging') && <MapPlacesErrorBoundary tripKey={activeTrip.id} onBack={backToTrips}>
                      <MapPlaces
                        trip={activeTrip}
                        token={session.accessToken}
                        onBack={backToTrips}
                        onAddActivityToItinerary={handleAddActivityFromMap}
                        onGoToItinerary={() => selectPackageTab('itinerary')}
                      />
                    </MapPlacesErrorBoundary>}
                  </section>
                  <section id="package-panel-related" role="tabpanel"
                    aria-labelledby={`package-tab-${activePackageTab === 'collaborators' ? 'collaborators' : 'flights'}`}
                    hidden={!['flights', 'collaborators'].includes(activePackageTab)}>
                    {(visitedPackageTabs.has('flights') || visitedPackageTabs.has('collaborators')) && <TripPlanningExtras key={activeTrip.id} token={session.accessToken}
                      trip={activeTrip} panel={activePackageTab === 'collaborators' ? 'collaborators' : 'flights'}
                      />}
                  </section>
                </div>
              </div>
            </TripPackageWorkspaceProvider>
          );
        }
        return (
          <MapPlaces
            trip={activeTrip}
            onBack={() => setCurrentPage('create-trip')}
            onAddActivityToItinerary={handleAddActivityFromMap}
            onGoToItinerary={() => setCurrentPage('itinerary')}
          />
        );
      case 'itinerary':
        return (
          <GeneradorItinerario
            tripData={activeTrip}
            trip={activeTrip}
            token={session?.accessToken}
            userId={session?.user?.id}
            onNavigate={handleNavigate}
          />
        );
      case 'home':
      default:
        return <Home onNavigate={handleNavigate} isAuthenticated={isAuthenticated} />;
    }
  };

  return (
    <>
      <Navbar
        currentPage={currentPage}
        isAuthenticated={isAuthenticated}
        hasTrip={Boolean(activeTrip)}
        onNavigate={handleNavigate}
        onLogout={handleLogout}
        isLocalDevSession={Boolean(session?.localDevSession)}
      />
      <main style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        {renderPage()}
      </main>
      <Footer />
    </>
  );
}

function handleTabKeyDown(event, tabs, selectTab) {
  if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
  event.preventDefault();
  const currentIndex = tabs.findIndex((tab) => tab.id === event.currentTarget.id.replace('package-tab-', ''));
  const nextIndex = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1
    : (currentIndex + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
  selectTab(tabs[nextIndex].id);
  requestAnimationFrame(() => document.getElementById(`package-tab-${tabs[nextIndex].id}`)?.focus());
}

function safeLocalStorage() {
  try { return window.localStorage; } catch { return null; }
}
