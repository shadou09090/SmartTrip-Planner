import React, { useCallback, useEffect, useState } from 'react';
import { CalendarDays, CircleDollarSign, MapPin, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react';
import { Button } from '../components/ui';
import { apiRequest } from '../lib/api';

const emptyForm = { origen: '', destino: '', fechaInicio: '', fechaFin: '', presupuesto: '' };

export default function MyTrips({ token, onCreateTrip, onSelectTrip, openTripId = null, onEditTrip = onSelectTrip }) {
  const [trips, setTrips] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [editForm, setEditForm] = useState(emptyForm);
  const [saving, setSaving] = useState(false);
  const [openedTripId, setOpenedTripId] = useState(null);

  const loadTrips = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await apiRequest('/api/viajes', { token });
      setTrips(Array.isArray(result) ? result : []);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  }, [token]);

  useEffect(() => { loadTrips(); }, [loadTrips]);

  useEffect(() => {
    if (loading || !openTripId || String(openedTripId) === String(openTripId)) return;
    const requestedTrip = trips.find((item) => String(item.id) === String(openTripId));
    if (!requestedTrip) {
      if (trips.length) setError('El viaje solicitado no está disponible para esta cuenta.');
      return;
    }
    setOpenedTripId(openTripId);
    onEditTrip(toTripModel(requestedTrip));
  }, [loading, openTripId, openedTripId, trips, onEditTrip]);

  const beginEdit = (trip) => {
    setEditingId(trip.id);
    setEditForm({
      origen: trip.origen,
      destino: trip.destino,
      fechaInicio: trip.fechaInicio,
      fechaFin: trip.fechaFin,
      presupuesto: trip.presupuesto ?? '',
    });
  };

  const saveEdit = async (event) => {
    event.preventDefault();
    setError('');
    if (editForm.fechaFin < editForm.fechaInicio) {
      setError('La fecha de fin no puede ser anterior a la fecha de inicio.');
      return;
    }
    if (editForm.presupuesto !== '' && Number(editForm.presupuesto) < 0) {
      setError('El presupuesto no puede ser negativo.');
      return;
    }
    setSaving(true);
    try {
      await apiRequest(`/api/viajes/${editingId}`, {
        method: 'PUT', token,
        body: JSON.stringify({ ...editForm, presupuesto: editForm.presupuesto === '' ? null : Number(editForm.presupuesto) }),
      });
      setEditingId(null);
      await loadTrips();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setSaving(false);
    }
  };

  const deleteTrip = async (trip) => {
    if (!window.confirm(`¿Eliminar el viaje a ${trip.destino} y sus intereses, itinerario, actividades, vuelos, alojamiento e invitaciones? Esta acción no se puede deshacer.`)) return;
    setError('');
    try {
      await apiRequest(`/api/viajes/${trip.id}`, { method: 'DELETE', token });
      await loadTrips();
    } catch (requestError) {
      setError(requestError.message);
    }
  };

  return (
    <section className="trip-page">
      <div className="trip-card trips-card">
        <div className="trip-heading">
          <div className="trip-heading-icon"><MapPin size={26} /></div>
          <div>
            <p className="eyebrow">Tu cuenta</p>
            <h1>Mis viajes</h1>
            <p>Estos viajes se consultan desde tu cuenta en SmartTrip.</p>
          </div>
        </div>

        <div className="trips-toolbar">
          <Button variant="secondary" onClick={loadTrips} disabled={loading}>
            <RefreshCw size={17} /> Actualizar
          </Button>
          <Button variant="primary" onClick={onCreateTrip}>
            <Plus size={18} /> Crear viaje
          </Button>
        </div>

        {error && <p className="form-error" role="alert">{error}</p>}
        {loading ? <p role="status">Cargando tus viajes…</p> : trips.length === 0 ? (
          <p className="trips-empty">Todavía no tienes viajes guardados.</p>
        ) : (
          <div className="trip-list">
            {trips.map((trip) => (
              <article className="trip-list-item" key={trip.id}>
                {editingId === trip.id ? (
                  <form className="trip-edit-form" onSubmit={saveEdit}>
                    <label>Origen<input required maxLength="150" value={editForm.origen} onChange={(e) => setEditForm({ ...editForm, origen: e.target.value })} /></label>
                    <label>Destino<input required maxLength="150" value={editForm.destino} onChange={(e) => setEditForm({ ...editForm, destino: e.target.value })} /></label>
                    <label>Inicio<input required type="date" value={editForm.fechaInicio} onChange={(e) => setEditForm({ ...editForm, fechaInicio: e.target.value })} /></label>
                    <label>Fin<input required type="date" value={editForm.fechaFin} onChange={(e) => setEditForm({ ...editForm, fechaFin: e.target.value })} /></label>
                    <label>Presupuesto<input type="number" min="0" step="0.01" value={editForm.presupuesto} onChange={(e) => setEditForm({ ...editForm, presupuesto: e.target.value })} /></label>
                    <div className="trip-list-actions">
                      <Button type="submit" variant="primary" disabled={saving}>{saving ? 'Guardando…' : 'Guardar'}</Button>
                      <Button type="button" variant="secondary" onClick={() => setEditingId(null)}>Cancelar</Button>
                    </div>
                  </form>
                ) : (
                  <>
                    <button className="trip-list-main" type="button" onClick={() => onSelectTrip(toTripModel(trip))}>
                      <strong>{trip.origen} → {trip.destino}</strong>
                      <span><CalendarDays size={16} /> {formatDate(trip.fechaInicio)} – {formatDate(trip.fechaFin)}</span>
                      <span><CircleDollarSign size={16} /> {trip.presupuesto == null ? 'Sin presupuesto' : formatBudget(trip.presupuesto)}</span>
                    </button>
                    <div className="trip-list-actions">
                      {trip.rolAcceso !== 'READER' && <Button variant="secondary" onClick={() => onEditTrip(toTripModel(trip))}><Pencil size={16} /> Editar</Button>}
                      {trip.rolAcceso !== 'READER' && <Button variant="ghost" onClick={() => beginEdit(trip)}><Pencil size={16} /> Editar datos</Button>}
                      {trip.rolAcceso === 'OWNER' && <Button variant="secondary" onClick={() => deleteTrip(trip)}><Trash2 size={16} /> Eliminar</Button>}
                    </div>
                  </>
                )}
              </article>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

function toTripModel(trip) {
  return { id: trip.id, origin: trip.origen, destination: trip.destino, startDate: trip.fechaInicio, endDate: trip.fechaFin, budget: Number(trip.presupuesto || 0), interests: trip.intereses || [], rolAcceso: trip.rolAcceso || 'OWNER' };
}

function formatDate(date) {
  return new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(`${date}T00:00:00Z`));
}

function formatBudget(amount) {
  return new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 }).format(amount);
}
