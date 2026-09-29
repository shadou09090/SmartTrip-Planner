import React, { useState } from 'react';
import {
  CalendarDays,
  CheckCircle2,
  CircleDollarSign,
  Heart,
  MapPin,
  Plane,
} from 'lucide-react';
import { Button } from '../components/ui';

const INTEREST_OPTIONS = [
  'Aventura',
  'Cultura',
  'Gastronomía',
  'Naturaleza',
  'Playa',
  'Vida nocturna',
];

const initialForm = {
  destination: '',
  startDate: '',
  endDate: '',
  budget: '',
  interests: [],
};

export default function CreateTrip() {
  const [formData, setFormData] = useState(initialForm);
  const [errors, setErrors] = useState({});
  const [createdTrip, setCreatedTrip] = useState(null);

  const updateField = ({ target: { name, value } }) => {
    setFormData((current) => ({ ...current, [name]: value }));
    setErrors((current) => ({ ...current, [name]: undefined, dates: undefined }));
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

    if (!formData.destination.trim()) {
      nextErrors.destination = 'Ingresa un destino para crear el viaje.';
    }
    if (!formData.startDate) nextErrors.startDate = 'Selecciona la fecha de inicio.';
    if (!formData.endDate) nextErrors.endDate = 'Selecciona la fecha de regreso.';
    if (formData.startDate && formData.endDate && formData.endDate < formData.startDate) {
      nextErrors.dates = 'La fecha de regreso no puede ser anterior a la fecha de inicio.';
    }
    if (!formData.budget || Number(formData.budget) <= 0) {
      nextErrors.budget = 'Ingresa un presupuesto mayor que cero.';
    }
    if (formData.interests.length === 0) {
      nextErrors.interests = 'Selecciona al menos un interés.';
    }

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  };

  const handleSubmit = (event) => {
    event.preventDefault();
    if (!validate()) return;

    setCreatedTrip({
      ...formData,
      destination: formData.destination.trim(),
      budget: Number(formData.budget),
    });
  };

  const startAnotherTrip = () => {
    setFormData(initialForm);
    setErrors({});
    setCreatedTrip(null);
  };

  if (createdTrip) {
    return (
      <section className="trip-page" aria-live="polite">
        <div className="trip-result">
          <div className="success-icon"><CheckCircle2 size={34} /></div>
          <p className="eyebrow">Viaje creado</p>
          <h1>¡Tu viaje a {createdTrip.destination} está listo!</h1>
          <p className="trip-lead">Esta es la información con la que comenzarás tu planificación.</p>

          <dl className="trip-summary">
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
              <dd>{formatBudget(createdTrip.budget)}</dd>
            </div>
            <div>
              <dt><Heart size={18} /> Intereses</dt>
              <dd>{createdTrip.interests.join(', ')}</dd>
            </div>
          </dl>

          <Button variant="primary" onClick={startAnotherTrip}>
            <Plane size={18} /> Crear otro viaje
          </Button>
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
            <p className="eyebrow">Nuevo viaje</p>
            <h1>¿A dónde quieres ir?</h1>
            <p>Cuéntanos lo esencial para comenzar a planificar tu experiencia.</p>
          </div>
        </div>

        <form className="trip-form" onSubmit={handleSubmit} noValidate>
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

          <div className="field field-full">
            <label htmlFor="budget">Presupuesto total (COP)</label>
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
                placeholder="Ej. 2500000"
                aria-invalid={Boolean(errors.budget)}
              />
            </div>
            {errors.budget && <p className="field-error">{errors.budget}</p>}
          </div>

          <fieldset className="interests field-full">
            <legend>¿Qué te interesa?</legend>
            <p>Selecciona al menos una opción.</p>
            <div className="interest-grid">
              {INTEREST_OPTIONS.map((interest) => (
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

          <Button type="submit" variant="submit" className="field-full trip-submit">
            <Plane size={19} /> Crear viaje
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

function formatBudget(budget) {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency',
    currency: 'COP',
    maximumFractionDigits: 0,
  }).format(budget);
}
