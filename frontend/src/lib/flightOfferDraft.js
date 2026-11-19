const formatDateTimeLocal = (value) => value ? String(value).replace(/(?:Z|[+-]\d{2}:\d{2})$/, '').slice(0, 16) : '';

export function createFlightDraftFromOffer(offer) {
  const outbound = offer?.slices?.[0];
  const segments = Array.isArray(outbound?.segments) ? outbound.segments : [];
  const first = segments[0];
  const last = segments.at(-1);
  const carriers = [...new Set(segments.map((segment) => segment.operatingCarrier || segment.marketingCarrier).filter(Boolean))];
  const numbers = segments.map((segment) => segment.flightNumber).filter(Boolean);
  const carrierText = carriers.join(' / ');
  const numberText = numbers.join(' / ');

  return {
    origen: first?.origin || outbound?.origin || '',
    destino: last?.destination || outbound?.destination || '',
    fechaSalida: formatDateTimeLocal(first?.departureAt || outbound?.departureAt),
    fechaLlegada: formatDateTimeLocal(last?.arrivalAt || outbound?.arrivalAt),
    aerolinea: carrierText.length <= 150 ? carrierText : '',
    numeroVuelo: numberText.length <= 50 ? numberText : '',
    // VUELO.PRECIO has no currency column. Keep foreign-currency price in the
    // selected-offer preview and never persist an unlabelled numeric amount.
    precio: offer?.currency === 'COP' ? String(offer.totalAmount ?? '') : '',
  };
}

export function isSinglePersistableDuffelSlice(offer) {
  return offer?.currency === 'COP'
    && offer?.slices?.length === 1
    && offer.slices[0]?.segments?.length === 1;
}

export function getDuffelSliceSummary(offer) {
  return (offer?.slices || []).map((slice, index) => ({
    label: index === 0 ? 'Ida' : `Regreso ${index}`,
    origin: slice.origin || 'Origen no informado',
    destination: slice.destination || 'Destino no informado',
    duration: slice.duration || 'Duración no informada',
    stops: Number(slice.stops || 0),
    segments: slice.segments || [],
  }));
}

export function restoreSelectedFlightFromPackage(flight) {
  if (!flight) return null;
  const tramos = (flight.tramos || []).map((segment) => ({
    origin: segment.origenIata || '', destination: segment.destinoIata || '',
    departureAt: segment.salida || '', arrivalAt: segment.llegada || '', numeroTrayecto: segment.numeroTrayecto,
    marketingCarrier: segment.aerolinea || '', flightNumber: segment.numeroVuelo || '',
  }));
  const sliceNumbers = [...new Set(tramos.map((segment) => segment.numeroTrayecto || 1))].sort((a, b) => a - b);
  return { id: flight.idOferta || flight.vueloId, provider: flight.proveedor,
    totalAmount: flight.importe, currency: flight.moneda, testData: flight.tipo === 'SANDBOX',
    origin: flight.origen || '', destination: flight.destino || '',
    slices: sliceNumbers.map((sliceNumber) => {
      const segments = tramos.filter((segment) => (segment.numeroTrayecto || 1) === sliceNumber);
      return { origin: segments[0]?.origin || flight.origen || '',
        destination: segments.at(-1)?.destination || flight.destino || '',
        departureAt: segments[0]?.departureAt || flight.salida || '',
        arrivalAt: segments.at(-1)?.arrivalAt || flight.llegada || '', segments };
    }) };
}
