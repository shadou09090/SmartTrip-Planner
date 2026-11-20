import React, { useEffect, useMemo, useState } from 'react';
import { apiRequest } from '../lib/api';
import { Button } from './ui';
import { formatFriendlyError } from '../lib/errorUtils';
import '../styles/flights.css';

const initialPlace = (value) => ({ name: value || '', iataCode: '', type: '' });
const localDuffelMode = import.meta.env.VITE_DUFFEL_MODE === 'local-no-db';

export default function FlightSearchPanel({ trip, token, onSelectOffer, selectedOfferId }) {
  const placesEndpoint = localDuffelMode
    ? '/api/local/duffel/places'
    : `/api/viajes/${trip.id}/vuelos/lugares`;
  const searchEndpoint = localDuffelMode
    ? '/api/local/duffel/search'
    : `/api/viajes/${trip.id}/vuelos/buscar`;
  const [origin, setOrigin] = useState(() => initialPlace(trip.origin));
  const [destination, setDestination] = useState(() => initialPlace(trip.destination));
  const [originSuggestions, setOriginSuggestions] = useState([]);
  const [destinationSuggestions, setDestinationSuggestions] = useState([]);
  const [departureDate, setDepartureDate] = useState(trip.startDate || '');
  const [returnDate, setReturnDate] = useState(trip.endDate || '');
  const [adults, setAdults] = useState(() => Math.max(1, Math.min(9, Number(trip.travelers) || 1)));
  const [children, setChildren] = useState(0);
  const [infants, setInfants] = useState(0);
  const [clock, setClock] = useState(Date.now());
  const [cabinClass, setCabinClass] = useState(trip.flightCabinClass || 'economy');
  const [results, setResults] = useState(null);
  const [sort, setSort] = useState('price');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    const timer = window.setInterval(() => setClock(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    let active = true;
    const query = origin.name.trim();
    if (query.length < 2 || origin.iataCode) {
      setOriginSuggestions([]);
      return undefined;
    }
    const timer = window.setTimeout(() => {
      apiRequest(`${placesEndpoint}?query=${encodeURIComponent(query)}`, { token })
        .then((items) => { if (active) setOriginSuggestions(Array.isArray(items) ? items : []); })
        .catch(() => { if (active) setOriginSuggestions([]); });
    }, 350);
    return () => { active = false; window.clearTimeout(timer); };
  }, [origin.name, origin.iataCode, token, placesEndpoint]);

  useEffect(() => {
    let active = true;
    const query = destination.name.trim();
    if (query.length < 2 || destination.iataCode) {
      setDestinationSuggestions([]);
      return undefined;
    }
    const timer = window.setTimeout(() => {
      apiRequest(`${placesEndpoint}?query=${encodeURIComponent(query)}`, { token })
        .then((items) => { if (active) setDestinationSuggestions(Array.isArray(items) ? items : []); })
        .catch(() => { if (active) setDestinationSuggestions([]); });
    }, 350);
    return () => { active = false; window.clearTimeout(timer); };
  }, [destination.name, destination.iataCode, token, placesEndpoint]);

  const sortedOffers = useMemo(() => {
    if (!results?.offers) return [];
    return results.offers.filter((offer) => !offer.expiresAt || Date.parse(offer.expiresAt) > clock).sort((left, right) => {
      if (sort === 'price') return Number(left.totalAmount) - Number(right.totalAmount);
      if (sort === 'stops') return totalStops(left) - totalStops(right);
      if (sort === 'duration') return totalMinutes(left) - totalMinutes(right);
      return String(left.slices?.[0]?.departureAt || '').localeCompare(String(right.slices?.[0]?.departureAt || ''));
    });
  }, [results, sort, clock]);

  const search = async (event) => {
    event.preventDefault();
    setError('');
    setResults(null);
    if (!origin.iataCode || !destination.iataCode) {
      setError('Selecciona el origen y el destino de las sugerencias para usar sus códigos de aeropuerto o ciudad.');
      return;
    }
    const totalPassengers = Number(adults) + Number(children) + Number(infants);
    if (Number(adults) < 1 || Number(infants) > Number(adults) || totalPassengers > 9) {
      setError('Incluye al menos un adulto, máximo 9 pasajeros y no más infantes que adultos.');
      return;
    }
    setLoading(true);
    try {
      const body = await apiRequest(searchEndpoint, {
        method: 'POST', token,
        body: JSON.stringify({
          origin: origin.iataCode,
          destination: destination.iataCode,
          departureDate,
          returnDate: returnDate || null,
          passengers: totalPassengers,
          adults: Number(adults),
          children: Number(children),
          infants: Number(infants),
          cabinClass,
        }),
      });
      setResults(body);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <section className="flight-search-panel" aria-labelledby="flight-search-title">
      <div className="flight-search-heading">
        <div><p className="eyebrow">Búsqueda externa</p><h4 id="flight-search-title">Explorar ofertas de vuelos</h4></div>
        <span className="flight-provider-badge">Duffel</span>
      </div>
      <p className="flight-search-guidance">Selecciona un aeropuerto o ciudad de las sugerencias para confirmar su código IATA. No se elige automáticamente porque un nombre puede corresponder a varios aeropuertos.</p>
      {Number(trip.travelers) > 9 && <p className="flight-search-guidance" role="status">La búsqueda Duffel admite hasta 9 pasajeros por solicitud. Ajusta la cantidad antes de consultar; el presupuesto del viaje conserva los {trip.travelers} viajeros.</p>}
      <form className="flight-search-form" onSubmit={search}>
        <PlaceField label="Origen" place={origin} onChange={(name) => setOrigin({ name, iataCode: '' })}
          suggestions={originSuggestions} onSelect={(place) => { setOrigin(place); setOriginSuggestions([]); }} />
        <PlaceField label="Destino" place={destination} onChange={(name) => setDestination({ name, iataCode: '' })}
          suggestions={destinationSuggestions} onSelect={(place) => { setDestination(place); setDestinationSuggestions([]); }} />
        <label>Salida<input required type="date" min={today()} value={departureDate} onChange={(event) => setDepartureDate(event.target.value)} /></label>
        <label>Regreso (opcional)<input type="date" min={departureDate || today()} value={returnDate} onChange={(event) => setReturnDate(event.target.value)} /></label>
        <label>Adultos<input required type="number" min="1" max="9" value={adults} onChange={(event) => setAdults(event.target.value)} /></label>
        <label>Niños<input type="number" min="0" max="8" value={children} onChange={(event) => setChildren(event.target.value)} /></label>
        <label>Infantes sin asiento<input type="number" min="0" max="8" value={infants} onChange={(event) => setInfants(event.target.value)} /></label>
        <label>Cabina<select value={cabinClass} onChange={(event) => setCabinClass(event.target.value)}>
          <option value="economy">Económica</option><option value="premium_economy">Económica premium</option>
          <option value="business">Ejecutiva</option><option value="first">Primera clase</option>
        </select></label>
        <Button variant="primary" className="flight-search-button" type="submit" disabled={loading}>
          {loading ? 'Buscando ofertas…' : 'Buscar vuelos'}
        </Button>
      </form>
      {error && <div role="alert" className="flight-error"><p className="form-error">{formatFriendlyError(error, 'No fue posible completar la búsqueda de vuelos. Verifica las fechas o intenta más tarde.')}</p><Button variant="secondary" type="button" onClick={search} disabled={loading}>Reintentar búsqueda</Button></div>}
      {results && <>
        <p className={`flight-provider-notice ${results.testData ? 'sandbox' : ''}`} role="status">{results.notice}</p>
        <div className="flight-results-toolbar">
          <h5>{sortedOffers.length ? `${sortedOffers.length} ofertas` : results.offers?.length ? 'Las ofertas recibidas ya vencieron; realiza una nueva búsqueda.' : 'Sin ofertas para esta búsqueda'}</h5>
          {sortedOffers.length > 1 && <label>Ordenar por<select value={sort} onChange={(event) => setSort(event.target.value)}>
            <option value="price">Menor precio</option><option value="duration">Menor duración</option>
            <option value="stops">Menos escalas</option><option value="departure">Hora de salida</option>
          </select></label>}
        </div>
        <div className="planning-scroll-region planning-scroll-region--flights" aria-label="Resultados de vuelos" tabIndex="0">{sortedOffers.map((offer) => <article className={`flight-offer-card ${selectedOfferId === offer.id ? 'flight-offer-card--selected' : ''}`} key={offer.id}>
          <div className="flight-offer-price"><strong>{formatMoney(offer.totalAmount, offer.currency)}</strong><small>precio total indicado por el proveedor</small></div>
          <div className="flight-offer-slices">{offer.slices.map((slice, index) => <div className="flight-slice" key={`${offer.id}-${index}`}>
            <div><small>{index === 0 ? 'Ida' : `Regreso · tramo ${index + 1}`}</small><strong>{slice.origin || 'Origen no informado'} → {slice.destination || 'Destino no informado'}</strong>
              {slice.segments?.length ? slice.segments.map((segment, segmentIndex) => <span key={`${offer.id}-${index}-${segmentIndex}`}>
                {segment.operatingCarrier || 'Operador no informado'}{segment.marketingCarrier && segment.marketingCarrier !== segment.operatingCarrier ? ` · comercializa ${segment.marketingCarrier}` : ''}
                {segment.flightNumber ? ` · ${segment.flightNumber}` : ''}
              </span>) : <span>{slice.airline || 'Aerolínea no informada'}{slice.flightNumber ? ` · ${slice.flightNumber}` : ''}</span>}</div>
            <div><strong>{formatDateTime(slice.departureAt)} → {formatDateTime(slice.arrivalAt)}</strong>
              <span>{slice.duration || 'Duración no informada'} · {slice.stops === 0 ? 'Directo' : `${slice.stops} escala(s)`}</span>
              {offer.expiresAt && <small>Oferta expira: {formatDateTime(offer.expiresAt)}</small>}
              <small>Equipaje: {slice.baggageAllowance || 'No informado por el proveedor'}</small></div>
          </div>)}</div>
          <Button variant={selectedOfferId === offer.id ? 'secondary' : 'primary'} className="flight-select-button"
            onClick={() => onSelectOffer?.({ ...offer, testData: Boolean(results.testData), provider: results.provider || 'Duffel', queriedAt: new Date().toISOString() })}>
            {selectedOfferId === offer.id ? 'Oferta seleccionada' : 'Seleccionar vuelo'}
          </Button>
        </article>)}</div>
      </>}
      <p className="flight-search-disclaimer">Las tarifas pueden cambiar y no se reservan desde SmartTrip. No se guardan en Oracle salvo que registres manualmente un vuelo en el formulario inferior.</p>
    </section>
  );
}

function PlaceField({ label, place, onChange, suggestions, onSelect }) {
  return <label className="flight-place-field">{label}
    <input required maxLength="100" autoComplete="off" value={place.name}
      onChange={(event) => onChange(event.target.value)} placeholder="Ciudad o aeropuerto" />
    {place.iataCode && <small className="flight-iata-selected">{place.type === 'city' ? 'Ciudad' : 'Aeropuerto'} · {place.iataCode}</small>}
    {suggestions.length > 0 && <div className="flight-place-suggestions" role="listbox">
      {suggestions.map((item) => <button type="button" role="option" key={`${item.type}-${item.iataCode}`}
        onClick={() => onSelect({ name: item.name, iataCode: item.iataCode, type: item.type })}>
        <span>{item.name}</span><small>{item.type === 'city' ? 'Ciudad' : 'Aeropuerto'} · {item.iataCode}{item.countryCode ? ` · ${item.countryCode}` : ''}</small>
      </button>)}
    </div>}
  </label>;
}

function totalStops(offer) { return (offer.slices || []).reduce((sum, slice) => sum + (slice.stops || 0), 0); }
function totalMinutes(offer) {
  return (offer.slices || []).reduce((sum, slice) => {
    const match = (slice.duration || '').match(/(\d+)\s*h\s*(\d+)\s*min/);
    return sum + (match ? Number(match[1]) * 60 + Number(match[2]) : Number.MAX_SAFE_INTEGER / 100);
  }, 0);
}
function formatMoney(amount, currency) {
  if (amount == null || !currency) return 'Precio no informado';
  const value = Number(amount);
  return Number.isFinite(value) ? new Intl.NumberFormat('es-CO', { style: 'currency', currency }).format(value) : `${amount} ${currency}`;
}
function formatDateTime(value) {
  if (!value) return 'Horario no informado';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' }).format(date);
}
function today() { return new Date().toISOString().slice(0, 10); }
