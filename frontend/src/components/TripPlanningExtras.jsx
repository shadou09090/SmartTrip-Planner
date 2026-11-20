import React, { useCallback, useEffect, useRef, useState } from 'react';
import { apiRequest } from '../lib/api';
import FlightSearchPanel from './FlightSearchPanel';
import { Button } from './ui';
import LodgingSuggestionCard from './LodgingSuggestionCard';
import { createFlightDraftFromOffer, getDuffelSliceSummary, isSinglePersistableDuffelSlice } from '../lib/flightOfferDraft';
import { useTripPackageWorkspace } from './TripPackageWorkspace';
import { formatFriendlyError } from '../lib/errorUtils';

const emptyLodging = { nombre: '', direccion: '', latitud: '', longitud: '' };
const emptyFlight = { origen: '', destino: '', fechaSalida: '', fechaLlegada: '', aerolinea: '', numeroVuelo: '', precio: '' };

export default function TripPlanningExtras({ trip, token, panel = 'all', onPackageLodgingSelect, onPackageFlightSelect }) {
  const [lodging, setLodging] = useState(null);
  const [lodgingForm, setLodgingForm] = useState(emptyLodging);
  const [lodgingSuggestions, setLodgingSuggestions] = useState([]);
  const [searchingLodging, setSearchingLodging] = useState(false);
  const [lodgingSearchStatus, setLodgingSearchStatus] = useState('idle');
  const [lodgingSearchError, setLodgingSearchError] = useState('');
  const [selectedLodgingSuggestion, setSelectedLodgingSuggestion] = useState('');
  const [lodgingTypeFilter, setLodgingTypeFilter] = useState(trip.lodgingPreference || 'all');
  const [flights, setFlights] = useState([]);
  const [extrasLoaded, setExtrasLoaded] = useState(false);
  const [flightForm, setFlightForm] = useState(emptyFlight);
  const [localSelectedOffer, setLocalSelectedOffer] = useState(null);
  const [editingFlightId, setEditingFlightId] = useState(null);
  const [history, setHistory] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState('READER');
  const [issuedInvitation, setIssuedInvitation] = useState(null);
  const [invitations, setInvitations] = useState([]);
  const [pendingInvitations, setPendingInvitations] = useState([]);
  const [collaborators, setCollaborators] = useState([]);
  const [incomingToken, setIncomingToken] = useState('');
  const [clock, setClock] = useState(Date.now());
  const flightSaveLock = useRef(false);
  const workspace = useTripPackageWorkspace();
  const selectedOffer = workspace ? workspace.selectedFlight : localSelectedOffer;
  const setSelectedOffer = workspace ? workspace.setSelectedFlight : setLocalSelectedOffer;
  const selectedOfferExpired = Boolean(selectedOffer?.expiresAt && Date.parse(selectedOffer.expiresAt) <= clock);

  useEffect(() => {
    const timer = window.setInterval(() => setClock(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  const load = useCallback(async () => {
    setError('');
    setExtrasLoaded(false);
    const [lodgingResult, flightsResult] = await Promise.allSettled([
      apiRequest(`/api/viajes/${trip.id}/alojamiento`, { token }),
      apiRequest(`/api/viajes/${trip.id}/vuelos`, { token }),
    ]);
    if (lodgingResult.status === 'fulfilled') {
      const savedLodging = lodgingResult.value;
      setLodging(savedLodging || null);
      setLodgingForm(savedLodging ? {
        nombre: savedLodging.nombre || '', direccion: savedLodging.direccion || '',
        latitud: savedLodging.latitud ?? '', longitud: savedLodging.longitud ?? '',
      } : emptyLodging);
    }
    if (flightsResult.status === 'fulfilled') setFlights(Array.isArray(flightsResult.value) ? flightsResult.value : []);
    setExtrasLoaded(true);

    const pendingResult = await Promise.allSettled([apiRequest('/api/invitaciones/pendientes', { token })]);
    if (pendingResult[0].status === 'fulfilled') setPendingInvitations(Array.isArray(pendingResult[0].value) ? pendingResult[0].value : []);
    if (trip.rolAcceso === 'OWNER') {
      const [collaboratorResult, invitationResult] = await Promise.allSettled([
        apiRequest(`/api/viajes/${trip.id}/colaboradores`, { token }),
        apiRequest(`/api/viajes/${trip.id}/invitaciones`, { token }),
      ]);
      if (collaboratorResult.status === 'fulfilled') setCollaborators(Array.isArray(collaboratorResult.value) ? collaboratorResult.value : []);
      if (invitationResult.status === 'fulfilled') setInvitations(Array.isArray(invitationResult.value) ? invitationResult.value : []);
    }
  }, [token, trip.id, trip.rolAcceso]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    if (!workspace?.selectedLodging) return;
    const selection = workspace.selectedLodging;
    setLodgingForm({ nombre: selection.nombre || '', direccion: selection.direccion || '',
      latitud: selection.latitud ?? '', longitud: selection.longitud ?? '' });
    setSelectedLodgingSuggestion(selection.id || '');
  }, [workspace?.selectedLodging]);

  const saveLodging = async (event) => {
    event.preventDefault();
    setBusy(true); setError(''); setNotice('');
    try {
      let coordinates = { latitud: lodgingForm.latitud, longitud: lodgingForm.longitud };
      let locationNote = '';
      if (coordinates.latitud === '' || coordinates.longitud === '') {
        const query = [lodgingForm.nombre, lodgingForm.direccion, trip.destination].filter(Boolean).join(', ');
        try {
          const verified = await apiRequest(`/api/viajes/${trip.id}/alojamiento/ubicacion?query=${encodeURIComponent(query)}`, { token });
          if (verified && verified.latitud != null && verified.longitud != null) coordinates = verified;
          else locationNote = ' No se encontraron coordenadas verificadas; el alojamiento se guardó sin ubicación de mapa.';
        } catch {
          locationNote = ' No fue posible verificar coordenadas con OpenStreetMap; el alojamiento se guardó sin ubicación de mapa.';
        }
      }
      const data = await apiRequest(`/api/viajes/${trip.id}/alojamiento`, {
        method: 'PUT', token,
        body: JSON.stringify({
          ...lodgingForm,
          latitud: coordinates.latitud === '' ? null : Number(coordinates.latitud),
          longitud: coordinates.longitud === '' ? null : Number(coordinates.longitud),
        }),
      });
      setLodging(data);
      // Keep the just-persisted lodging in the shared package workspace so a
      // package save immediately after this request cannot race a later GET.
      workspace?.setSelectedLodging(data);
      onPackageLodgingSelect?.(data);
      window.dispatchEvent(new CustomEvent('smarttrip:lodging-updated', { detail: { tripId: String(trip.id) } }));
      setSelectedLodgingSuggestion('');
      setLodgingForm({ nombre: data.nombre || '', direccion: data.direccion || '',
        latitud: data.latitud ?? '', longitud: data.longitud ?? '' });
      setNotice(`Alojamiento guardado en el itinerario.${locationNote}`);
    } catch (requestError) { setError(requestError.message); }
    finally { setBusy(false); }
  };

  const searchLodging = async () => {
    setSearchingLodging(true); setError(''); setLodgingSearchError(''); setLodgingSearchStatus('loading');
    try {
      const suggestions = await apiRequest(`/api/viajes/${trip.id}/alojamientos/sugerencias`, { token });
      const rows = Array.isArray(suggestions) ? suggestions : [];
      setLodgingSuggestions(rows);
      setLodgingSearchStatus(rows.length ? 'ready' : 'empty');
      if (!rows.length) setNotice('No se encontraron alojamientos con ubicación verificable en las fuentes disponibles.');
    } catch (requestError) { setLodgingSearchError(requestError.message); setLodgingSearchStatus('error'); }
    finally { setSearchingLodging(false); }
  };

  const visibleLodgingSuggestions = lodgingTypeFilter === 'all' ? lodgingSuggestions : lodgingSuggestions.filter((suggestion) => {
    const type = `${suggestion.tipo || ''} ${suggestion.categoria || ''}`.toLowerCase();
    const terms = { hotel: ['hotel'], hostel: ['hostel', 'hostal', 'albergue'], apartment: ['apartment', 'apartamento', 'flat'],
      guest_house: ['guest house', 'guesthouse', 'casa de huéspedes', 'bed and breakfast', 'b&b'] }[lodgingTypeFilter] || [];
    return terms.some((term) => type.includes(term));
  });

  const selectLodging = (suggestion) => {
    setLodgingForm({ nombre: suggestion.nombre, direccion: suggestion.direccion || '',
      latitud: suggestion.latitud ?? '', longitud: suggestion.longitud ?? '' });
    setSelectedLodgingSuggestion(suggestion.id);
    workspace?.setSelectedLodging(suggestion);
    onPackageLodgingSelect?.(suggestion);
    setNotice('Alojamiento seleccionado. Guarda el formulario para asociarlo a este itinerario.');
  };

  const selectFlightOffer = (offer) => {
    if (offer.expiresAt && Date.parse(offer.expiresAt) <= Date.now()) {
      setError('Esta oferta venció. Realiza una nueva búsqueda antes de seleccionarla.');
      return;
    }
    setSelectedOffer(offer);
    onPackageFlightSelect?.(offer);
    setEditingFlightId(null);
    setFlightForm(createFlightDraftFromOffer(offer));
    setError('');
    setNotice('Oferta seleccionada. Revisa y edita los datos antes de guardar; esto no reserva ni compra un billete.');
  };

  const updateFlightField = (field, value) => {
    setFlightForm((current) => ({ ...current, [field]: value }));
    if (selectedOffer) {
      setSelectedOffer(null);
      onPackageFlightSelect?.(null);
      setNotice('Editaste los datos del vuelo; se desvinculó la oferta Duffel. El guardado será manual y no se verificará como oferta vigente.');
    }
  };

  const saveFlight = async (event) => {
    event.preventDefault();
    if (flightSaveLock.current || busy) return;
    if (selectedOfferExpired) {
      setError('La oferta seleccionada venció. Realiza una nueva búsqueda; no se guardó.');
      return;
    }
    if (selectedOffer && !isSinglePersistableDuffelSlice(selectedOffer)) {
      setError('El esquema VUELO solo puede guardar una oferta directa, de ida y en COP. No se guardó para evitar perder moneda, escalas o regreso.');
      return;
    }
    flightSaveLock.current = true;
    setBusy(true); setError(''); setNotice('');
    try {
      if (selectedOffer) {
        await apiRequest(`/api/viajes/${trip.id}/vuelos/ofertas`, {
          method: 'POST', token, body: JSON.stringify({ offerId: selectedOffer.id }),
        });
      } else {
        const payload = {
          ...flightForm,
          fechaSalida: flightForm.fechaSalida,
          fechaLlegada: flightForm.fechaLlegada || null,
          aerolinea: flightForm.aerolinea || null,
          numeroVuelo: flightForm.numeroVuelo || null,
          precio: flightForm.precio === '' ? null : Number(flightForm.precio),
        };
        await apiRequest(editingFlightId
          ? `/api/viajes/${trip.id}/vuelos/${editingFlightId}`
          : `/api/viajes/${trip.id}/vuelos`, {
          method: editingFlightId ? 'PUT' : 'POST', token, body: JSON.stringify(payload),
        });
      }
      setEditingFlightId(null); setFlightForm(emptyFlight); setSelectedOffer(null); onPackageFlightSelect?.(null);
      window.dispatchEvent(new CustomEvent('smarttrip:flight-updated', { detail: { tripId: String(trip.id) } }));
      await load();
      setNotice(selectedOffer
        ? 'Oferta verificada nuevamente con Duffel y guardada en Oracle. SmartTrip no realizó una reserva ni compra.'
        : 'Vuelo guardado en Oracle.');
    } catch (requestError) { setError(requestError.message); }
    finally { flightSaveLock.current = false; setBusy(false); }
  };

  const showHistory = async (flight) => {
    setError('');
    try {
      const rows = await apiRequest(`/api/viajes/${trip.id}/vuelos/${flight.id}/historial-precios`, { token });
      setHistory({ vueloId: flight.id, rows });
    } catch (requestError) { setError(requestError.message); }
  };

  const editFlight = (flight) => {
    setEditingFlightId(flight.id);
    setFlightForm({ ...flight, fechaSalida: toInputDateTime(flight.fechaSalida),
      fechaLlegada: toInputDateTime(flight.fechaLlegada), precio: flight.precio ?? '' });
  };

  const createInvitation = async (event) => {
    event.preventDefault(); setError(''); setNotice(''); setIssuedInvitation(null);
    try {
      const created = await apiRequest(`/api/viajes/${trip.id}/invitaciones`, {
        method: 'POST', token, body: JSON.stringify({ correo: inviteEmail, rol: inviteRole }),
      });
      setIssuedInvitation(created); setInviteEmail(''); setNotice('Invitación creada. Copia el código y envíaselo al invitado por un canal privado.');
      const rows = await apiRequest(`/api/viajes/${trip.id}/invitaciones`, { token });
      setInvitations(rows);
    } catch (requestError) { setError(requestError.message); }
  };

  const removeCollaborator = async (userId) => {
    if (!window.confirm('¿Quitar el acceso de este colaborador al viaje?')) return;
    try {
      await apiRequest(`/api/viajes/${trip.id}/colaboradores/${userId}`, { method: 'DELETE', token });
      setCollaborators((current) => current.filter((item) => item.usuarioId !== userId));
    } catch (requestError) { setError(requestError.message); }
  };

  const respondToInvitation = async (action) => {
    setError(''); setNotice('');
    try {
      await apiRequest(`/api/invitaciones/${action}`, {
        method: 'POST', token, body: JSON.stringify({ token: incomingToken }),
      });
      setIncomingToken(''); setNotice(action === 'aceptar' ? 'Invitación aceptada.' : 'Invitación rechazada.');
      await load();
    } catch (requestError) { setError(requestError.message); }
  };

  return (
    <section className="trip-page trip-page--wide trip-page--extras">
      <div className="trip-card">
        <p className="eyebrow">Reservas</p><h2>Alojamiento y vuelos</h2>
        <p>Estos datos se consultan y guardan en Oracle para este viaje.</p>
        {error && <p role="alert" className="form-error">{formatFriendlyError(error)}</p>}
        {notice && <p role="status" className="trip-notice">{notice}</p>}

        <section className="extras-lodging-panel" aria-label="Alojamiento" hidden={panel === 'flights' || panel === 'collaborators'}>
        <h3>Alojamiento (uno por itinerario)</h3>
        {lodging && <div className="lodging-saved-card" role="status">
          <span><strong>Guardado en este viaje</strong><small>{lodging.nombre} · {lodging.direccion || 'Dirección no informada'}</small></span>
          <small>Precio, disponibilidad y reserva no verificados.</small>
        </div>}
        {extrasLoaded && !lodging && <p className="lodging-empty-state">Aún no hay un alojamiento guardado. Busca una opción o registra los datos manualmente.</p>}
        <Button variant="primary" type="button" disabled={searchingLodging} onClick={searchLodging}>
          {searchingLodging ? 'Buscando cerca del destino…' : `Buscar alojamientos en ${trip.destination}`}
        </Button>
        {lodgingSearchError && <p role="alert" className="form-error">{formatFriendlyError(lodgingSearchError)}</p>}
        {lodgingSearchStatus === 'loading' && <div className="lodging-suggestions lodging-skeleton-list" aria-label="Cargando alojamientos">
          {[0, 1, 2].map((item) => <div className="lodging-skeleton" key={item}><span /><i /><i /></div>)}
        </div>}
        {lodgingSearchStatus === 'empty' && <p className="lodging-empty-state">No hay resultados con ubicación válida. Puedes probar otro nombre de destino o completar el formulario manual.</p>}
        {lodgingSuggestions.length > 0 && <div className="lodging-suggestions" aria-label="Alojamientos sugeridos">
          <p>Opciones geográficas de Geoapify/OpenStreetMap. Sin precios ni disponibilidad verificados; elegirlas no reserva alojamiento.</p>
          <label>Tipo de alojamiento
            <select value={lodgingTypeFilter} onChange={(event) => setLodgingTypeFilter(event.target.value)}>
              <option value="all">Todos</option><option value="hotel">Hoteles</option><option value="hostel">Hostales</option>
              <option value="apartment">Apartamentos</option><option value="guest_house">Casas de huéspedes</option>
            </select>
          </label>
          {!visibleLodgingSuggestions.length && <p role="status">No hay coincidencias verificables para ese tipo. Prueba otra categoría o muestra todos.</p>}
          <div className="planning-scroll-region planning-scroll-region--lodging lodging-suggestions__grid" aria-label="Lista de alojamientos disponibles" tabIndex="0">
            {visibleLodgingSuggestions.map((suggestion) => <LodgingSuggestionCard key={suggestion.id}
              suggestion={suggestion} selected={selectedLodgingSuggestion === suggestion.id}
              onSelect={selectLodging} />)}
          </div>
          <small>Geoapify · © OpenStreetMap contributors</small>
        </div>}
        <form className="trip-edit-form" onSubmit={saveLodging}>
          <label>Nombre<input required maxLength="200" value={lodgingForm.nombre} onChange={(e) => setLodgingForm({ ...lodgingForm, nombre: e.target.value })} /></label>
          <label>Dirección<input maxLength="300" value={lodgingForm.direccion} onChange={(e) => setLodgingForm({ ...lodgingForm, direccion: e.target.value })} /></label>
          <small className="field-full">La ubicación se obtiene de la sugerencia seleccionada o se verifica en el backend. Las coordenadas se administran internamente. Guardar no realiza una reserva.</small>
          <Button variant="primary" type="submit" disabled={busy}>{busy ? 'Guardando…' : lodging ? 'Actualizar alojamiento' : 'Guardar alojamiento'}</Button>
        </form>
        </section>

        <section className="extras-flight-panel" aria-label="Vuelos" hidden={panel === 'lodging' || panel === 'collaborators'}>
        <h3>Vuelos</h3>
        <FlightSearchPanel trip={trip} token={token} selectedOfferId={selectedOffer?.id} onSelectOffer={selectFlightOffer} />
        <h4>Vuelos registrados para el viaje</h4>
        {flights.length === 0 ? <p>No hay vuelos registrados.</p> : (
          <ul className="planning-scroll-region planning-scroll-region--flight-records" aria-label="Vuelos registrados" tabIndex="0">{flights.map((flight) => (
            <li key={flight.id} className="flight-record-item">
              <span>{flight.origen} → {flight.destino}, {flight.fechaSalida}{flight.precio != null ? ` · ${flight.precio}` : ''}</span>
              <div className="flight-record-actions">
                <Button variant="secondary" type="button" onClick={() => editFlight(flight)}>Editar</Button>
                <Button variant="secondary" type="button" onClick={() => showHistory(flight)}>Historial de precios</Button>
              </div>
              {history?.vueloId === flight.id && <ul>{history.rows.map((row) => <li key={row.id}>{row.precio} · {row.fechaRegistro}</li>)}</ul>}
            </li>
          ))}</ul>
        )}
        {selectedOffer && <section className="selected-flight-review" aria-label="Revisión de oferta seleccionada">
          <div className="selected-flight-review__heading"><strong>Oferta seleccionada para revisar</strong>
            <span>{selectedOffer.totalAmount} {selectedOffer.currency} · Duffel{selectedOffer.testData ? ' (sandbox)' : ''}</span></div>
          {getDuffelSliceSummary(selectedOffer).map((slice) => <article key={slice.label}>
            <strong>{slice.label}: {slice.origin} → {slice.destination}</strong>
            <span>{slice.duration} · {slice.stops === 0 ? 'Directo' : `${slice.stops} escala(s)`}</span>
            {slice.segments.map((segment, index) => <small key={`${slice.label}-${index}`}>
              {segment.origin} → {segment.destination} · {segment.operatingCarrier || segment.marketingCarrier || 'Operador no informado'}
              {segment.flightNumber ? ` · ${segment.flightNumber}` : ''}
            </small>)}
          </article>)}
          <p>Oracle solo puede guardar ofertas directas de ida en COP. Al guardar, el backend vuelve a consultar Duffel y usa sus datos vigentes; no se realiza una reserva.</p>
          {selectedOffer.currency !== 'COP' && <p>La tabla no almacena moneda; quita esta selección para registrar un vuelo manual o busca una oferta en COP.</p>}
          {selectedOfferExpired && <p role="alert">Esta oferta ya venció; no se puede guardar como vigente.</p>}
          <Button variant="secondary" onClick={() => { setSelectedOffer(null); setFlightForm(emptyFlight); }}>Quitar selección y registrar manualmente</Button>
        </section>}
        <form className="trip-edit-form" onSubmit={saveFlight}>
          <h4>{editingFlightId ? 'Editar vuelo' : 'Registrar vuelo'}</h4>
          <label>Origen<input required maxLength="150" value={flightForm.origen} onChange={(e) => updateFlightField('origen', e.target.value)} /></label>
          <label>Destino<input required maxLength="150" value={flightForm.destino} onChange={(e) => updateFlightField('destino', e.target.value)} /></label>
          <label>Salida<input required type="datetime-local" value={flightForm.fechaSalida} onChange={(e) => updateFlightField('fechaSalida', e.target.value)} /></label>
          <label>Llegada<input type="datetime-local" value={flightForm.fechaLlegada} onChange={(e) => updateFlightField('fechaLlegada', e.target.value)} /></label>
          <label>Aerolínea<input maxLength="150" value={flightForm.aerolinea} onChange={(e) => updateFlightField('aerolinea', e.target.value)} /></label>
          <label>Número de vuelo<input maxLength="50" value={flightForm.numeroVuelo} onChange={(e) => updateFlightField('numeroVuelo', e.target.value)} /></label>
          <label>Precio{selectedOffer ? ' (verificado al guardar)' : ''}<input type="number" min="0" step="0.01" value={flightForm.precio} onChange={(e) => updateFlightField('precio', e.target.value)} /></label>
          <Button variant="primary" type="submit" disabled={busy || selectedOfferExpired || (selectedOffer && !isSinglePersistableDuffelSlice(selectedOffer))}>
            {busy ? 'Guardando…' : selectedOfferExpired ? 'Oferta vencida' : selectedOffer ? 'Guardar vuelo seleccionado' : editingFlightId ? 'Guardar cambios' : 'Guardar vuelo'}
          </Button>
          {selectedOffer && !isSinglePersistableDuffelSlice(selectedOffer) && <small>La estructura actual de Oracle no permite guardar fielmente esta oferta (debe ser directa, de ida y estar en COP).</small>}
          {editingFlightId && <Button variant="secondary" type="button" onClick={() => { setEditingFlightId(null); setFlightForm(emptyFlight); setSelectedOffer(null); }}>Cancelar</Button>}
        </form>
        </section>

        <section className="extras-collaborator-panel" aria-label="Colaboradores" hidden={panel === 'lodging' || panel === 'flights'}>
        {trip.rolAcceso === 'OWNER' && <>
          <h3>Colaboradores</h3>
          {collaborators.length === 0 ? <p className="places-empty">No hay colaboradores registrados en este viaje.</p> : <ul className="collaborators-list">{collaborators.map((item) => (
            <li key={item.usuarioId} className="collaborator-item">
              <span><strong>{item.nombre}</strong> · {item.correo} · <em>{item.rol}</em></span>
              <Button variant="secondary" type="button" onClick={() => removeCollaborator(item.usuarioId)}>Quitar acceso</Button>
            </li>
          ))}</ul>}
          <form className="trip-edit-form" onSubmit={createInvitation}>
            <h4>Invitar por correo</h4>
            <label>Correo del usuario<input type="email" required maxLength="150" value={inviteEmail} onChange={(e) => setInviteEmail(e.target.value)} /></label>
            <label>Permiso<select value={inviteRole} onChange={(e) => setInviteRole(e.target.value)}><option value="READER">Lector</option><option value="EDITOR">Editor</option></select></label>
            <Button variant="primary" type="submit">Crear invitación</Button>
          </form>
          {issuedInvitation && <p role="status" className="trip-notice">Código de invitación (se muestra solo al crearla): <code>{issuedInvitation.invitationToken}</code></p>}
          <h4>Invitaciones enviadas</h4>
          <ul className="sent-invitations-list">{invitations.filter((item) => item.correo).map((item) => <li key={item.id}>{item.correo} · {item.rol} · {item.estado}</li>)}</ul>
        </>}

        <h3>Responder una invitación</h3>
        {pendingInvitations.map((item) => <p key={item.id} className="trip-notice">Invitación pendiente para {item.correo} · {item.rol} · vence {item.expiresAt}</p>)}
        <form className="trip-edit-form invitation-response-form" onSubmit={(event) => { event.preventDefault(); respondToInvitation('aceptar'); }}>
          <label>Código recibido<input required maxLength="255" value={incomingToken} onChange={(e) => setIncomingToken(e.target.value)} /></label>
          <div className="invitation-action-buttons">
            <Button variant="primary" type="submit">Aceptar invitación</Button>
            <Button variant="secondary" type="button" onClick={() => respondToInvitation('rechazar')}>Rechazar</Button>
          </div>
        </form>
        </section>
      </div>
    </section>
  );
}

function toInputDateTime(value) {
  return value ? value.slice(0, 16) : '';
}
