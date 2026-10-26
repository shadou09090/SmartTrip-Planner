import React, { useState } from 'react';
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
  interests: [],
};

export default function CreateTrip({
  initialTrip = null,
  onTripCreated,
  onResetTrip,
  onViewMap,
  onViewItinerary,
}) {
  const [formData, setFormData] = useState(initialTrip ? { ...initialForm, ...initialTrip } : initialForm);
  const [errors, setErrors] = useState({});
  const [createdTrip, setCreatedTrip] = useState(initialTrip);

  const updateField = ({ target: { name, value } }) => {
    setFormData((current) => ({ ...current, [name]: value }));
    setErrors((current) => ({ ...current, [name]: undefined, dates: undefined }));
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

    if (!formData.startDate) {
      nextErrors.startDate = 'La fecha de inicio es obligatoria.';
    }

    if (!formData.endDate) {
      nextErrors.endDate = 'La fecha de finalización es obligatoria.';
    }

    if (formData.startDate && formData.endDate && formData.endDate < formData.startDate) {
      nextErrors.dates = 'La fecha de finalización no puede ser anterior a la fecha de inicio.';
    }

    if (formData.budget && Number(formData.budget) < 0) {
      nextErrors.budget = 'El monto de presupuesto no puede ser negativo.';
    }

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    if (!validate()) return;

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
              <dt><CircleDollarSign size={18} /> Nivel de presupuesto</dt>
              <dd id="summary-budget">
                {createdTrip.budgetLevel
                  ? `${createdTrip.budgetLevel}${createdTrip.budget ? ` (${formatBudget(createdTrip.budget)})` : ''}`
                  : (createdTrip.budget ? formatBudget(createdTrip.budget) : 'No especificado (Opcional)')}
              </dd>
            </div>
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
            <Button id="btn-to-map" variant={onViewItinerary ? 'secondary' : 'primary'} onClick={onViewMap}>
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
            <p className="eyebrow">HU-012 · Preferencias de Viaje</p>
            <h1>Define tus preferencias de viaje</h1>
            <p>Ingresa tus datos esenciales y opcionalmente personaliza tu experiencia para generar el mejor itinerario.</p>
          </div>
        </div>

        <form className="trip-form" onSubmit={handleSubmit} noValidate>
          {/* Origen (Obligatorio) */}
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

          {/* Destino (Obligatorio) */}
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
            {errors.destination && <p id="destination-error" className="field-error" role="alert">{errors.destination}</p>}
          </div>

          {/* Fecha de inicio (Obligatorio) */}
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

          {/* Fecha de finalización (Obligatorio) */}
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

          {/* Error de rango de fechas si endDate < startDate */}
          {errors.dates && (
            <p id="dates-error" className="form-error field-full" role="alert">
              {errors.dates}
            </p>
          )}

          {/* Nivel de presupuesto (Opcional) */}
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

          {/* Intereses y preferencias (Opcional) */}
          <fieldset className="interests field-full">
            <legend>
              Intereses y actividades <span className="optional-tag">(Opcional)</span>
            </legend>
            <p>Selecciona los tipos de actividades que prefieres para priorizarlas en tu itinerario.</p>
            <div className="interest-grid">
              {INTEREST_OPTIONS.map((interest) => (
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

          <Button type="submit" variant="submit" id="btn-submit-trip" className="field-full trip-submit">
            <Plane size={19} /> Guardar preferencias y continuar
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

function formatBudget(budget) {
  if (!budget && budget !== 0) return '';
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(budget);
}
