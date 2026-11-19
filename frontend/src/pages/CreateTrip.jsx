import React, { useEffect, useState } from 'react';
import {
  CalendarDays,
  CheckCircle2,
  CircleDollarSign,
  Heart,
  Map,
  MapPin,
  Navigation,
  Plane,
  Wallet,
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
  'Historia',
  'Compras',
  'Relajación',
];

const BUDGET_LEVELS = [
  { id: 'Económico', label: 'Económico', desc: 'Hostales y bajo costo' },
  { id: 'Moderado', label: 'Moderado', desc: 'Hoteles estándar y buen balance' },
  { id: 'Lujo', label: 'Lujo', desc: 'Hoteles exclusivos y alta gama' },
];

const initialForm = {
  origin: '',
  destination: '',
  startDate: '',
  endDate: '',
  budgetLevel: '',
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
  onViewItinerary,
  onPackageCreated,
  userId,
}) {
  const [formData, setFormData] = useState(initialTrip ? { ...initialForm, ...initialTrip } : initialForm);
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

  const handleBudgetLevel = (level) => {
    setFormData((current) => ({
      ...current,
      budgetLevel: current.budgetLevel === level ? '' : level,
    }));
  };

  const toggleInterest = (interest) => {
    setFormData((current) => ({
      ...current,
      interests: current.interests.includes(interest)
        ? current.interests.filter((item) => item !== interest)
        : [...current.interests, interest],
    }));
  };

  const validate = () => {
    const nextErrors = {};

    if (!formData.origin || !formData.origin.trim()) {
      nextErrors.origin = 'El origen es obligatorio. Por favor ingresa tu ciudad o punto de partida.';
    }

    if (!formData.destination || !formData.destination.trim()) {
      nextErrors.destination = 'El destino es obligatorio. Por favor ingresa la ciudad de llegada.';
    }

    if (destinationCandidates.length && !selectedDestinationId) {
      nextErrors.destination = 'Selecciona una coincidencia geográfica o modifica la búsqueda del destino.';
    }

    if (!formData.startDate) {
      nextErrors.startDate = 'La fecha de inicio es obligatoria.';
    }

    if (!formData.endDate) {
      nextErrors.endDate = 'La fecha de finalización es obligatoria.';
    }

    if (formData.startDate && formData.endDate && formData.endDate < formData.startDate) {
      nextErrors.dates = 'La fecha de finalización no puede ser anterior a la fecha de inicio.';
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

    if (!token) {
      const trip = {
        ...formData,
        origin: formData.origin.trim(),
        destination: formData.destination.trim(),
        budget: formData.budget ? Number(formData.budget) : null,
        budgetLevel: formData.budgetLevel || null,
        interests: formData.interests || [],
      };
      setCreatedTrip(trip);
      onTripCreated?.(trip);
      return;
    }

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
        budgetLevel: formData.budgetLevel || null,
        budgetCurrency: formData.budgetCurrency.toUpperCase(),
        travelers: Number(formData.travelers),
        flightCabinClass: formData.flightCabinClass,
        lodgingPreference: formData.lodgingPreference,
        interests: savedTrip.intereses || [],
        rolAcceso: savedTrip.rolAcceso || 'OWNER',
      };
      try { savePackagePreferences(window.localStorage, userId, trip.id, trip); } catch {}
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
          <p className="eyebrow">Viaje creado con éxito</p>
          <h1>¡Tu viaje de {createdTrip.origin} a {createdTrip.destination} está listo!</h1>
          <p className="trip-lead">Hemos guardado tus preferencias para comenzar a estructurar tu itinerario personalizado.</p>

          <dl className="trip-summary">
            <div>
              <dt><Navigation size={18} /> Origen</dt>
              <dd id="summary-origin">{createdTrip.origin}</dd>
            </div>
            <div>
              <dt><MapPin size={18} /> Destino</dt>
              <dd id="summary-destination">{createdTrip.destination}</dd>
            </div>
            <div>
              <dt><CalendarDays size={18} /> Fechas del viaje</dt>
              <dd id="summary-dates">{formatDate(createdTrip.startDate)} – {formatDate(createdTrip.endDate)}</dd>
            </div>
            <div>
              <dt><CircleDollarSign size={18} /> Presupuesto</dt>
              <dd id="summary-budget">
                {createdTrip.budgetLevel
                  ? `${createdTrip.budgetLevel}${createdTrip.budget ? ` (${formatBudget(createdTrip.budget, createdTrip.budgetCurrency)})` : ''}`
                  : (createdTrip.budget ? formatBudget(createdTrip.budget, createdTrip.budgetCurrency) : 'No especificado (Opcional)')}
              </dd>
            </div>
            <div><dt>Viajeros</dt><dd>{createdTrip.travelers || 1}</dd></div>
            <div><dt>Preferencias</dt><dd>{cabinLabel(createdTrip.flightCabinClass)} · {lodgingLabel(createdTrip.lodgingPreference)}</dd></div>
            <div className="summary-full-width">
              <dt><Heart size={18} /> Intereses y preferencias</dt>
              <dd id="summary-interests">
                {createdTrip.interests && createdTrip.interests.length > 0
                  ? createdTrip.interests.join(', ')
                  : 'Sin intereses específicos seleccionados (Perfil estándar)'}
              </dd>
            </div>
          </dl>

          <div className="trip-result-actions" style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', justifyContent: 'center' }}>
            {onViewItinerary && (
              <Button id="btn-to-itinerary" variant="primary" onClick={onViewItinerary}>
                <CalendarDays size={18} /> Planificar Itinerario
              </Button>
            )}
            <Button
              id="btn-to-map"
              variant={onViewItinerary ? 'secondary' : 'primary'}
              onClick={() => onPackageCreated ? onPackageCreated(createdTrip, userId) : onViewMap?.(createdTrip)}
            >
              <Map size={18} /> Ver lugares en el mapa
            </Button>
            <Button id="btn-create-another" variant="secondary" onClick={startAnotherTrip}>
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
            <h1>Define tus preferencias de viaje</h1>
            <p>Ingresa tus datos esenciales y opcionalmente personaliza tu experiencia para generar el mejor itinerario.</p>
          </div>
        </div>

        <form className="trip-form" onSubmit={handleSubmit} noValidate>
          <div className="field">
            <label htmlFor="origin">
              Origen <span className="required-star" title="Obligatorio">*</span>
            </label>
            <div className="input-with-icon">
              <Navigation size={19} />
              <input
                id="origin"
                name="origin"
                value={formData.origin}
                onChange={updateField}
                placeholder="Ej. Bogotá, Colombia"
                aria-required="true"
                aria-invalid={Boolean(errors.origin)}
                aria-describedby={errors.origin ? 'origin-error' : undefined}
              />
            </div>
            {errors.origin && <p id="origin-error" className="field-error" role="alert">{errors.origin}</p>}
          </div>

          <div className="field">
            <label htmlFor="destination">
              Destino <span className="required-star" title="Obligatorio">*</span>
            </label>
            <div className="input-with-icon">
              <MapPin size={19} />
              <input
                id="destination"
                name="destination"
                value={formData.destination}
                onChange={updateField}
                placeholder="Ej. Cartagena, Colombia"
                aria-required="true"
                aria-invalid={Boolean(errors.destination)}
                aria-describedby={errors.destination ? 'destination-error' : undefined}
              />
            </div>
            {token && (
              <button
                type="button"
                className="destination-search-button"
                onClick={searchDestinations}
                disabled={destinationLookupLoading || formData.destination.trim().length < 2}
              >
                {destinationLookupLoading ? 'Buscando ubicaciones…' : 'Buscar ubicación precisa'}
              </button>
            )}
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
            {errors.destination && <p id="destination-error" className="field-error" role="alert">{errors.destination}</p>}
          </div>

          <div className="field">
            <label htmlFor="startDate">
              Fecha de inicio <span className="required-star" title="Obligatorio">*</span>
            </label>
            <input
              id="startDate"
              type="date"
              name="startDate"
              value={formData.startDate}
              onChange={updateField}
              aria-required="true"
              aria-invalid={Boolean(errors.startDate || errors.dates)}
              aria-describedby={errors.startDate ? 'start-date-error' : undefined}
            />
            {errors.startDate && <p id="start-date-error" className="field-error" role="alert">{errors.startDate}</p>}
          </div>

          <div className="field">
            <label htmlFor="endDate">
              Fecha de finalización <span className="required-star" title="Obligatorio">*</span>
            </label>
            <input
              id="endDate"
              type="date"
              name="endDate"
              value={formData.endDate}
              min={formData.startDate || undefined}
              onChange={updateField}
              aria-required="true"
              aria-invalid={Boolean(errors.endDate || errors.dates)}
              aria-describedby={errors.endDate ? 'end-date-error' : undefined}
            />
            {errors.endDate && <p id="end-date-error" className="field-error" role="alert">{errors.endDate}</p>}
          </div>

          {errors.dates && (
            <p id="dates-error" className="form-error field-full" role="alert">
              {errors.dates}
            </p>
          )}
          {submitError && <p className="form-error field-full" role="alert">{submitError}</p>}

          <div className="field field-full budget-level-container">
            <label htmlFor="budget">
              Nivel de presupuesto <span className="optional-tag">(Opcional)</span>
            </label>
            <p className="budget-subtext">Elige un nivel general o ingresa un monto estimado para ajustar el perfil de recomendaciones.</p>

            <div className="budget-chips" role="group" aria-label="Nivel de presupuesto opcional">
              {BUDGET_LEVELS.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  id={`budget-level-${item.id.toLowerCase()}`}
                  className={`budget-chip ${formData.budgetLevel === item.id ? 'selected' : ''}`}
                  onClick={() => handleBudgetLevel(item.id)}
                  title={item.desc}
                >
                  <Wallet size={16} />
                  <span>{item.label}</span>
                </button>
              ))}
            </div>

            <div className="input-with-icon" style={{ marginTop: '4px' }}>
              <CircleDollarSign size={19} />
              <input
                id="budget"
                type="number"
                name="budget"
                min="0"
                step="50000"
                value={formData.budget}
                onChange={updateField}
                placeholder="Monto estimado en COP (opcional, ej. 2500000)"
                aria-invalid={Boolean(errors.budget)}
                aria-describedby={errors.budget ? 'budget-error' : undefined}
              />
            </div>
            {errors.budget && <p id="budget-error" className="field-error" role="alert">{errors.budget}</p>}
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
            <legend>
              Intereses y actividades <span className="optional-tag">(Opcional)</span>
            </legend>
            <p>Selecciona los tipos de actividades que prefieres para priorizarlas en tu itinerario.</p>
            <div className="interest-grid">
              {interestOptions.map((interest) => (
                <label key={interest} className={formData.interests.includes(interest) ? 'selected' : ''}>
                  <input
                    type="checkbox"
                    id={`interest-${interest.toLowerCase().replace(/\s+/g, '-')}`}
                    checked={formData.interests.includes(interest)}
                    onChange={() => toggleInterest(interest)}
                  />
                  {interest}
                </label>
              ))}
            </div>
          </fieldset>

          <Button type="submit" variant="submit" id="btn-submit-trip" className="field-full trip-submit" disabled={submitting}>
            <Plane size={19} /> {submitting ? 'Guardando viaje…' : 'Crear mi paquete'}
          </Button>
        </form>
      </div>
    </section>
  );
}

function formatDate(date) {
  if (!date) return '';
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
