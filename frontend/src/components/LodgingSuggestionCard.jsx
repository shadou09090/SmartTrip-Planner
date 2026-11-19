import React, { useState } from 'react';
import { BedDouble, ExternalLink, ImageOff, MapPin } from 'lucide-react';

export default function LodgingSuggestionCard({ suggestion, selected = false, distanceKm, onView, onSelect }) {
  const [imageFailed, setImageFailed] = useState(false);
  const hasVerifiedPhoto = Boolean(suggestion.imagenUrl && suggestion.imagenLicencia
    && suggestion.imagenLicenciaUrl && suggestion.imagenFuenteUrl
    && (!suggestion.imagenLicencia.toLowerCase().includes('cc by') || suggestion.imagenAtribucion));

  return (
    <article className={`lodging-card ${selected ? 'is-selected' : ''}`}>
      <div className="lodging-card__media">
        {hasVerifiedPhoto && !imageFailed ? (
          <img src={suggestion.imagenUrl} alt={`Fotografía vinculada a ${suggestion.nombre}`} loading="lazy"
            referrerPolicy="no-referrer" onError={() => setImageFailed(true)} />
        ) : (
          <div className="lodging-card__placeholder" role="img" aria-label="No hay fotografía verificada para este alojamiento">
            <span><ImageOff size={19} /> Fotografía no disponible</span>
            <BedDouble aria-hidden="true" size={48} strokeWidth={1.25} />
            <small>SmartTrip · ubicación verificada</small>
          </div>
        )}
        {suggestion.categoria && <span className="lodging-card__type">{suggestion.categoria}</span>}
      </div>
      <div className="lodging-card__body">
        <div className="lodging-card__title-row">
          <h3>{suggestion.nombre}</h3>
          {selected && <span className="lodging-card__selected">Seleccionado</span>}
        </div>
        <p className="lodging-card__address"><MapPin size={15} /> {suggestion.direccion || 'Dirección no informada'}</p>
        {distanceKm != null && Number.isFinite(distanceKm) && (
          <small className="lodging-card__distance">A {distanceKm.toFixed(distanceKm < 10 ? 1 : 0)} km del punto de destino</small>
        )}
        <div className="lodging-card__disclaimer">
          <strong>Precio y disponibilidad por confirmar</strong>
          <span>SmartTrip no recibe tarifas hoteleras. Guardar el lugar no realiza una reserva.</span>
        </div>
        {hasVerifiedPhoto && (
          <small className="lodging-card__photo-credit">
            Foto: {suggestion.imagenAtribucion || 'Wikimedia Commons'} · {suggestion.imagenLicencia}
            {' · '}<a href={suggestion.imagenFuenteUrl} target="_blank" rel="noreferrer">fuente y licencia</a>
          </small>
        )}
        <div className="lodging-card__actions">
          {onView && <button type="button" className="lodging-card__secondary" onClick={() => onView(suggestion)}>Ver alojamiento</button>}
          {suggestion.urlAlojamiento && <a className="lodging-card__external" href={suggestion.urlAlojamiento}
            target="_blank" rel="noopener noreferrer">Sitio del alojamiento <ExternalLink size={14} /></a>}
          {suggestion.urlBusquedaTarifas && <a className="lodging-card__external lodging-card__rates" href={suggestion.urlBusquedaTarifas}
            target="_blank" rel="noopener noreferrer">Buscar precio y disponibilidad <ExternalLink size={14} />
            <small>Búsqueda externa por nombre, zona y fechas; verifica la propiedad, ocupación y tarifa.</small></a>}
          {suggestion.urlFuente && <a className="lodging-card__source" href={suggestion.urlFuente}
            target="_blank" rel="noopener noreferrer">Ver ficha en OpenStreetMap</a>}
          <button type="button" className="lodging-card__primary" aria-pressed={selected}
            onClick={() => onSelect(suggestion)}>{selected ? 'Alojamiento seleccionado' : 'Seleccionar alojamiento'}</button>
        </div>
      </div>
    </article>
  );
}
