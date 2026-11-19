import React, { useEffect, useState } from 'react';
import {
  CalendarDays,
  CheckCircle2,
  CircleDollarSign,
  Heart,
  Map,
  MapPin,
  Plane,
} from 'lucide-react';
import { Button } from '../components/ui';
import { apiRequest } from '../lib/api';
import { normalizeDestinationCandidates } from '../lib/destinationCandidates';
import { savePackagePreferences } from '../lib/packagePreferences';

const INTEREST_OPTIONS = [
  'Aventura',
  'Cultura',
  'Gastronomía',
  'Naturaleza',
  'Playa',
  'Vida nocturna',
];

const initialForm = {
  origin: '',
  destination: '',
  startDate: '',
  endDate: '',
  budget: '',
  budgetCurrency: 'COP',
  travelers: '1',
  flightCabinClass: 'economy',
  lodgingPreference: 'all',
  interests: [],
};

export default function CreateTrip({
  token,
  initialTrip = null,
  onTripCreated,
  onResetTrip,
  onViewMap,
  onPackageCreated,
  userId,
}) {
  const [formData, setFormData] = useState(initialForm);
  const [errors, setErrors] = useState({});
  const [createdTrip, setCreatedTrip] = useState(initialTrip);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const [interestOptions, setInterestOptions] = useState(INTEREST_OPTIONS);
  const [destinationCandidates, setDestinationCandidates] = useState([]);
  const [destinationLookupLoading, setDestinationLookupLoading] = useState(false);
  const [destinationLookupError, setDestinationLookupError] = useState('');
  const [selectedDestinationId, setSelectedDestinationId] = useState('');

  useEffect(() => {
    if (!token) return;
    apiRequest('/api/intereses', { token })
      .then((rows) => {
        const stored = Array.isArray(rows) ? rows.map((row) => row.nombre) : [];
        setInterestOptions([...new Set([...INTEREST_OPTIONS, ...stored])]);
      })
      .catch((requestError) => setSubmitError(requestError.message));
  }, [token]);

  const updateField = ({ target: { name, value } }) => {
    setFormData((current) => ({ ...current, [name]: value }));
    setErrors((current) => ({ ...current, [name]: undefined, dates: undefined }));
    if (name === 'destination') {
      setSelectedDestinationId('');
      setDestinationCandidates([]);
      setDestinationLookupError('');
    }
  };

  const searchDestinations = async () => {
    const query = formData.destination.trim();
    if (query.length < 2) { setDestinationLookupError('Escribe al menos dos caracteres para buscar.'); return; }
    setDestinationLookupLoading(true);
    setDestinationLookupError('');
    setDestinationCandidates([]);
    try {
      const rows = await apiRequest(`/api/destinos/buscar?query=${encodeURIComponent(query)}`, { token });
      const candidates = normalizeDestinationCandidates(rows);
      setDestinationCandidates(candidates);
      if (!candidates.length) setDestinationLookupError('El geocodificador no devolvió coincidencias. Puedes conservar el nombre escrito y continuar; la búsqueda turística dependerá de que las fuentes puedan ubicarlo.');
    } catch (requestError) {
      setDestinationLookupError(requestError.message || 'No se pudieron consultar coincidencias del destino.');
    } finally { setDestinationLookupLoading(false); }
  };

  const selectDestination = (candidate) => {
    setFormData((current) => ({ ...current, destination: candidate.nombreCompleto }));
    setSelectedDestinationId(candidate.id);
    setDestinationCandidates([]);
    setDestinationLookupError('');
    setErrors((current) => ({ ...current, destination: undefined }));
  };

  const toggleInterest = (interest) => {
    setFormData((current) => ({
      ...current,
      interests: current.interests.includes(interest)
        ? current.interests.filter((item) => item !== interest)
        : [...current.interests, interest],
    }));
    setErrors((current) => ({ ...current, interests: undefined }));
  };

  const validate = () => {
    const nextErrors = {};

    if (!formData.origin.trim()) {
      nextErrors.origin = 'Ingresa la ciudad de origen.';
    }
    if (!formData.destination.trim()) {
      nextErrors.destination = 'Ingresa un destino para crear el viaje.';
    }
    if (destinationCandidates.length && !selectedDestinationId) {
      nextErrors.destination = 'Selecciona una coincidencia geográfica o modifica la búsqueda del destino.';
    }
    if (!formData.startDate) nextErrors.startDate = 'Selecciona la fecha de inicio.';
    if (!formData.endDate) nextErrors.endDate = 'Selecciona la fecha de regreso.';
    if (formData.startDate && formData.endDate && formData.endDate < formData.startDate) {
      nextErrors.dates = 'La fecha de regreso no puede ser anterior a la fecha de inicio.';
    }
    if (formData.budget !== '' && (!Number.isFinite(Number(formData.budget))
        || Number(formData.budget) < 0 || Number(formData.budget) > 9999999999.99)) {
      nextErrors.budget = 'El presupuesto debe estar entre cero y 9.999.999.999,99.';
    }
    if (!Number.isInteger(Number(formData.travelers)) || Number(formData.travelers) < 1 || Number(formData.travelers) > 20) {
      nextErrors.travelers = 'Indica entre 1 y 20 viajeros.';
    }
    if (!/^[A-Za-z]{3}$/.test(formData.budgetCurrency)) {
      nextErrors.budgetCurrency = 'Usa el código ISO 4217 de tres letras, por ejemplo COP, USD o EUR.';
    }

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!validate()) return;

    setSubmitError('');
    setSubmitting(true);
    try {
      const savedTrip = await apiRequest('/api/viajes', {
        method: 'POST',
        token,
        body: JSON.stringify({
          origen: formData.origin.trim(),
          destino: formData.destination.trim(),
          fechaInicio: formData.startDate,
          fechaFin: formData.endDate,
          presupuesto: formData.budget === '' ? null : Number(formData.budget),
          intereses: formData.interests,
        }),
      });
      const trip = {
        id: savedTrip.id,
        origin: savedTrip.origen,
        destination: savedTrip.destino,
        startDate: savedTrip.fechaInicio,
        endDate: savedTrip.fechaFin,
        budget: savedTrip.presupuesto == null ? null : Number(savedTrip.presupuesto),
        budgetCurrency: formData.budgetCurrency.toUpperCase(),
        travelers: Number(formData.travelers),
        flightCabinClass: formData.flightCabinClass,
        lodgingPreference: formData.lodgingPreference,
        interests: savedTrip.intereses || [],
        rolAcceso: savedTrip.rolAcceso || 'OWNER',
      };
      try { savePackagePreferences(window.localStorage, userId, trip.id, trip); } catch { /* Package settings remain available for this session. */ }
      setCreatedTrip(trip);
      onTripCreated?.(trip);
      onPackageCreated?.(trip, userId);
    } catch (requestError) {
      setSubmitError(requestError.message);
    } finally {
      setSubmitting(false);
    }
  };

  const startAnotherTrip = () => {
    setFormData(initialForm);
    setErrors({});
    setCreatedTrip(null);
    onResetTrip?.();
  };

  if (createdTrip) {
    return (
      <section className="trip-page" aria-live="polite">
        <div className="trip-result">
          <div className="success-icon"><CheckCircle2 size={34} /></div>
          <p className="eyebrow">Paquete base listo</p>
          <h1>Preparemos tu viaje a {createdTrip.destination}</h1>
          <p className="trip-lead">SmartTrip consultará vuelos, alojamientos y atractivos por separado. Elegir una opción no reserva ni compra servicios.</p>

          <dl className="trip-summary">
            <div>
              <dt><MapPin size={18} /> Origen</dt>
              <dd>{createdTrip.origin}</dd>
            </div>
            <div>
              <dt><MapPin size={18} /> Destino</dt>
              <dd>{createdTrip.destination}</dd>
            </div>
            <div>
              <dt><CalendarDays size={18} /> Fechas</dt>
              <dd>{formatDate(createdTrip.startDate)} – {formatDate(createdTrip.endDate)}</dd>
            </div>
            <div>
              <dt><CircleDollarSign size={18} /> Presupuesto</dt>
              <dd>{formatBudget(createdTrip.budget, createdTrip.budgetCurrency)}</dd>
            </div>
            <div><dt>Viajeros</dt><dd>{createdTrip.travelers}</dd></div>
            <div><dt>Preferencias</dt><dd>{cabinLabel(createdTrip.flightCabinClass)} · {lodgingLabel(createdTrip.lodgingPreference)}</dd></div>
            <div>
              <dt><Heart size={18} /> Intereses seleccionados</dt>
              <dd>{createdTrip.interests.join(', ')}</dd>
            </div>
          </dl>

          <div className="trip-result-actions">
            <Button variant="primary" onClick={() => onPackageCreated ? onPackageCreated(createdTrip, userId) : onViewMap?.(createdTrip)}>
              <Map size={18} /> Crear mi paquete
            </Button>
            <Button variant="secondary" onClick={startAnotherTrip}>
              <Plane size={18} /> Crear otro viaje
            </Button>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="trip-page">
      <div className="trip-card">
        <div className="trip-heading">
          <div className="trip-heading-icon"><Plane size={26} /></div>
          <div>
            <p className="eyebrow">Planificación de viaje</p>
            <h1>Crear mi paquete</h1>
            <p>Define el viaje una vez. SmartTrip reunirá las opciones disponibles y preparará una propuesta revisable.</p>
          </div>
        </div>

        <form className="trip-form" onSubmit={handleSubmit} noValidate>
          <div className="field field-full">
            <label htmlFor="origin">Origen</label>
            <div className="input-with-icon">
              <MapPin size={19} />
              <input
                id="origin"
                name="origin"
                value={formData.origin}
                onChange={updateField}
                placeholder="Ej. Bogotá, Colombia"
                aria-invalid={Boolean(errors.origin)}
                aria-describedby={errors.origin ? 'origin-error' : undefined}
              />
            </div>
            {errors.origin && <p id="origin-error" className="field-error">{errors.origin}</p>}
          </div>

          <div className="field field-full">
            <label htmlFor="destination">Destino</label>
            <div className="input-with-icon">
              <MapPin size={19} />
              <input
                id="destination"
                name="destination"
                value={formData.destination}
                onChange={updateField}
                placeholder="Ej. Cartagena, Colombia"
                aria-invalid={Boolean(errors.destination)}
                aria-describedby={errors.destination ? 'destination-error' : undefined}
              />
            </div>
            <button type="button" className="destination-search-button" onClick={searchDestinations}
              disabled={destinationLookupLoading || formData.destination.trim().length < 2}>
              {destinationLookupLoading ? 'Buscando ubicaciones…' : 'Buscar ubicación precisa'}
            </button>
            {selectedDestinationId && <small className="destination-verified" role="status">Ubicación elegida del geocodificador. Se conservaron sus divisiones geográficas en el nombre del destino.</small>}
            {destinationLookupError && <p className="field-error" role="status">{destinationLookupError}</p>}
            {destinationCandidates.length > 0 && <ul className="destination-candidates" aria-label="Coincidencias de destino">
              {destinationCandidates.map((candidate) => <li key={candidate.id}>
                <button type="button" onClick={() => selectDestination(candidate)}>
                  <strong>{candidate.nombreCompleto}</strong>
                  <small>{[candidate.ciudad, candidate.region, candidate.pais, candidate.tipo].filter(Boolean).join(' · ')}</small>
                </button>
              </li>)}
            </ul>}
            {errors.destination && <p id="destination-error" className="field-error">{errors.destination}</p>}
          </div>

          <div className="field">
            <label htmlFor="startDate">Fecha de inicio</label>
            <input
              id="startDate"
              type="date"
              name="startDate"
              value={formData.startDate}
              onChange={updateField}
              aria-invalid={Boolean(errors.startDate || errors.dates)}
            />
            {errors.startDate && <p className="field-error">{errors.startDate}</p>}
          </div>

          <div className="field">
            <label htmlFor="endDate">Fecha de regreso</label>
            <input
              id="endDate"
              type="date"
              name="endDate"
              value={formData.endDate}
              min={formData.startDate || undefined}
              onChange={updateField}
              aria-invalid={Boolean(errors.endDate || errors.dates)}
            />
            {errors.endDate && <p className="field-error">{errors.endDate}</p>}
          </div>

          {errors.dates && <p className="form-error field-full" role="alert">{errors.dates}</p>}
          {submitError && <p className="form-error field-full" role="alert">{submitError}</p>}

          <div className="field field-full">
            <label htmlFor="budget">Presupuesto total</label>
            <div className="input-with-icon">
              <CircleDollarSign size={19} />
              <input
                id="budget"
                type="number"
                name="budget"
                min="1"
                step="1000"
                value={formData.budget}
                onChange={updateField}
                placeholder="Opcional · Ej. 2500000"
                aria-invalid={Boolean(errors.budget)}
              />
            </div>
            <label htmlFor="budgetCurrency">Moneda del presupuesto (ISO 4217)</label>
            <input id="budgetCurrency" name="budgetCurrency" maxLength="3" value={formData.budgetCurrency}
              onChange={(event) => updateField({ target: { name: 'budgetCurrency', value: event.target.value.toUpperCase() } })}
              aria-invalid={Boolean(errors.budgetCurrency)} />
            {errors.budget && <p className="field-error">{errors.budget}</p>}
            {errors.budgetCurrency && <p className="field-error">{errors.budgetCurrency}</p>}
          </div>

          <div className="field">
            <label htmlFor="travelers">Viajeros</label>
            <input id="travelers" name="travelers" type="number" min="1" max="20" step="1"
              value={formData.travelers} onChange={updateField} aria-invalid={Boolean(errors.travelers)} />
            {errors.travelers && <p className="field-error">{errors.travelers}</p>}
          </div>
          <div className="field">
            <label htmlFor="flightCabinClass">Preferencia de cabina</label>
            <select id="flightCabinClass" name="flightCabinClass" value={formData.flightCabinClass} onChange={updateField}>
              <option value="economy">Económica</option><option value="premium_economy">Económica premium</option>
              <option value="business">Ejecutiva</option><option value="first">Primera clase</option>
            </select>
          </div>
          <div className="field field-full">
            <label htmlFor="lodgingPreference">Tipo de alojamiento preferido</label>
            <select id="lodgingPreference" name="lodgingPreference" value={formData.lodgingPreference} onChange={updateField}>
              <option value="all">Cualquier tipo</option><option value="hotel">Hotel</option>
              <option value="hostel">Hostal</option><option value="apartment">Apartamento</option>
              <option value="guest_house">Casa de huéspedes</option>
            </select>
            <small>La preferencia filtra ubicaciones encontradas; Geoapify y OpenStreetMap no proporcionan tarifas ni disponibilidad.</small>
          </div>
          <p className="field-full package-local-preferences-note">La base actual no almacena moneda del presupuesto ni cantidad de viajeros. SmartTrip conserva esas preferencias localmente en este navegador, separadas por usuario y viaje; no se sincronizan con Oracle.</p>

          <fieldset className="interests field-full">
            <legend>¿Qué te interesa?</legend>
            <p>Opcional: puedes seleccionar intereses ahora o agregarlos después.</p>
            <div className="interest-grid">
              {interestOptions.map((interest) => (
                <label key={interest} className={formData.interests.includes(interest) ? 'selected' : ''}>
                  <input
                    type="checkbox"
                    checked={formData.interests.includes(interest)}
                    onChange={() => toggleInterest(interest)}
                  />
                  {interest}
                </label>
              ))}
            </div>
            {errors.interests && <p className="field-error">{errors.interests}</p>}
          </fieldset>

          <Button type="submit" variant="submit" className="field-full trip-submit" disabled={submitting}>
            <Plane size={19} /> {submitting ? 'Guardando viaje…' : 'Crear mi paquete'}
          </Button>
        </form>
      </div>
    </section>
  );
}

function formatDate(date) {
  return new Intl.DateTimeFormat('es-CO', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${date}T00:00:00Z`));
}

function formatBudget(budget, currency = 'COP') {
  if (budget == null) return 'Sin presupuesto definido';
  try { return new Intl.NumberFormat('es-CO', { style: 'currency', currency, maximumFractionDigits: 0 }).format(budget); }
  catch { return `${Number(budget).toLocaleString('es-CO')} ${currency}`; }
}

function cabinLabel(value) { return ({ economy: 'Económica', premium_economy: 'Económica premium', business: 'Ejecutiva', first: 'Primera clase' })[value] || 'Económica'; }
function lodgingLabel(value) { return ({ all: 'cualquier alojamiento', hotel: 'hotel', hostel: 'hostal', apartment: 'apartamento', guest_house: 'casa de huéspedes' })[value] || 'cualquier alojamiento'; }
