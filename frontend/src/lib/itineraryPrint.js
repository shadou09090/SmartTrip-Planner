function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>\"']/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '\"': '&quot;', "'": '&#39;',
  })[character]);
}

function formatMoney(value, currency = 'COP') {
  if (value == null || value === '') return 'Precio no disponible';
  if (!Number.isFinite(Number(value))) return 'Precio no disponible';
  try { return new Intl.NumberFormat('es-CO', { style: 'currency', currency: /^[A-Z]{3}$/.test(currency) ? currency : 'COP' }).format(Number(value)); }
  catch { return `${Number(value).toLocaleString('es-CO')} ${escapeHtml(currency)}`; }
}

export function buildItineraryPrintHtml({ trip, itinerary, lodging, flights = [], budget = null, travelers = null, isDraft = false }) {
  if (!trip?.destination || !trip?.startDate || !trip?.endDate) {
    throw new Error('El viaje debe tener destino y fechas para exportar el itinerario.');
  }
  if (!itinerary?.dias?.length || !itinerary.dias.some((day) => day.actividades?.length)) {
    throw new Error('Agrega al menos una actividad al borrador antes de exportar el PDF.');
  }

  const flightRows = flights.map((flight) => {
    const details = flightPrintDetails(flight);
    return `<li><strong>${escapeHtml(details.title)}</strong>${details.lines.map((line) => `<span>${escapeHtml(line)}</span>`).join('')}</li>`;
  }).join('');
  const dayRows = itinerary.dias.map((day, index) => `<section class="day"><h2>Día ${index + 1} · ${escapeHtml(day.fecha)}</h2>
    ${day.actividades?.length ? `<ol>${day.actividades.map((activity) => `<li><strong>${escapeHtml(activity.nombre)}</strong>
      ${activity.ubicacion ? `<span>${escapeHtml(activity.ubicacion)}</span>` : ''}
      ${activity.descripcion ? `<p>${escapeHtml(activity.descripcion)}</p>` : ''}
      <small>${escapeHtml(activityCostLabel(activity, budget))}</small>
      ${activity.fuente ? `<small>Fuente: ${escapeHtml(activity.fuente)}</small>` : ''}
      ${safeUrl(activity.urlFuente) ? `<small><a href="${escapeHtml(safeUrl(activity.urlFuente))}">Consultar fuente</a></small>` : ''}</li>`).join('')}</ol>` : '<p>Día sin actividades asignadas.</p>'}
    </section>`).join('');
  const budgetCategoryRows = budget ? Object.entries(budget.byCategory || {}).map(([category, amount]) =>
    `<li>${escapeHtml(category)}: ${formatMoney(amount, budget.currency)}</li>`).join('') : '';
  const budgetRows = budget ? `<section class="section"><h2>Presupuesto${isDraft ? ' · escenario local no sincronizado con Oracle' : ''}</h2>
    <p>Presupuesto total: ${budget.total == null ? 'No definido' : formatMoney(budget.total, budget.currency)}</p>
    <p>Gastos confirmados: ${formatMoney(budget.confirmed, budget.currency)} · Estimaciones incluidas: ${formatMoney(budget.estimated, budget.currency)}</p>
    <p>Saldo proyectado parcial: ${budget.remaining == null ? 'No calculado' : formatMoney(budget.remaining, budget.currency)}</p>
    <p>Costos desconocidos: ${Number(budget.unknown) || 0} · Importes sin conversión: ${Number(budget.foreignCurrency) || 0}</p>
    ${budgetCategoryRows ? `<h3>Totales por categoría</h3><ul>${budgetCategoryRows}</ul>` : ''}
    ${(budget.included || []).length ? `<h3>Partidas incluidas</h3><ul>${budget.included.map((entry) => `<li>${escapeHtml(entry.category || 'Gasto')}: ${escapeHtml(entry.label)} · ${formatMoney(entry.convertedAmount, budget.currency)} · ${escapeHtml(budgetConfidenceLabel(entry.confidence))}${entry.converted ? ` · referencia desde ${escapeHtml(entry.currency)} · ${escapeHtml(entry.rateSource || 'tasa manual')}${entry.rateDate ? ` · ${escapeHtml(entry.rateDate)}` : ''}; se mantiene como estimación` : ''}${entry.source ? ` · Fuente: ${escapeHtml(entry.source)}` : ''}${entry.queriedAt ? ` · Consulta: ${escapeHtml(entry.queriedAt)}` : ''}${entry.availability ? ` · ${escapeHtml(availabilityLabel(entry.availability))}` : ''}</li>`).join('')}</ul>` : ''}
    ${(budget.unknownEntries || []).length ? `<h3>Pendientes de verificar</h3><ul>${budget.unknownEntries.map((entry) => `<li>${escapeHtml(entry.category || 'Gasto')}: ${escapeHtml(entry.label || 'Importe desconocido')}${entry.source ? ` · Fuente: ${escapeHtml(entry.source)}` : ''}${entry.availability ? ` · ${escapeHtml(availabilityLabel(entry.availability))}` : ''}</li>`).join('')}</ul>` : ''}
    <p>Saldo parcial; faltan gastos por verificar. Las tasas manuales son estimaciones, no cotizaciones de cambio.</p></section>` : '<section class="section"><h2>Presupuesto</h2><p>Resumen de presupuesto no disponible en esta sesión.</p></section>';

  const html = `<!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Itinerario SmartTrip · ${escapeHtml(trip.destination)}</title><style>
      :root{color-scheme:light;font-family:Arial,sans-serif;color:#173b42}body{max-width:850px;margin:36px auto;padding:0 24px;line-height:1.5}
      h1{color:#126b68;margin-bottom:4px}.meta{color:#526b70;margin-top:0}.section{border-top:1px solid #dce7e6;padding:12px 0}.day{break-inside:avoid}
      li{margin:10px 0}li span,li small{display:block;color:#526b70}footer{margin-top:28px;color:#526b70;font-size:12px}
      @media print{body{margin:0 auto;padding:0 12mm}button{display:none}}
    </style></head><body><header><p>SMARTTRIP PLANNER</p><h1>${escapeHtml(trip.destination)}</h1>
      <p class="meta">${escapeHtml(trip.country ? `${trip.destination}, ${trip.country}` : trip.destination)} · ${escapeHtml(trip.startDate)} — ${escapeHtml(trip.endDate)}${travelers ? ` · ${escapeHtml(travelers)} viajero(s)` : ''}</p>
      <p class="meta">${isDraft ? 'Propuesta editable no guardada en Oracle' : 'Itinerario guardado'}</p></header>
      <section class="section"><h2>Vuelo</h2>${flightRows ? `<ul>${flightRows}</ul>` : '<p>No hay vuelos registrados para este viaje.</p>'}</section>
      <section class="section"><h2>Alojamiento</h2>${lodging ? `<p><strong>${escapeHtml(lodging.nombre)}</strong><br>${escapeHtml(lodging.direccion || 'Dirección no informada')}</p>` : '<p>No hay alojamiento registrado para este viaje.</p>'}</section>
      <section class="section"><h2>Itinerario por días</h2>${dayRows}</section>
      ${budgetRows}
      <footer>Exportado desde SmartTrip Planner. Horarios, precios, disponibilidad y traslados no informados no se estiman. La vista se imprime mediante el navegador y puede guardarse como PDF. El límite aplicado es 5 MB para el HTML preparado; el tamaño final del PDF depende del navegador.</footer>
    </body></html>`;
  if (new TextEncoder().encode(html).length > 5 * 1024 * 1024) {
    throw new Error('El documento supera 5 MB y no se puede preparar para impresión. Reduce el contenido del itinerario.');
  }
  return html;
}

function safeUrl(value) {
  if (typeof value !== 'string') return null;
  try {
    const url = new URL(value);
    return ['https:', 'http:'].includes(url.protocol) ? url.toString() : null;
  } catch { return null; }
}

export function openItineraryPrint(html, printWindow) {
  if (!printWindow) throw new Error('El navegador bloqueó la ventana de impresión. Permite ventanas emergentes e inténtalo de nuevo.');
  printWindow.document.open();
  printWindow.document.write(html);
  printWindow.document.close();
  printWindow.focus();
  printWindow.setTimeout(() => printWindow.print(), 250);
  return printWindow;
}

const CP1252_EXTRA = new Map([[0x20ac, 0x80], [0x201a, 0x82], [0x0192, 0x83], [0x201e, 0x84],
  [0x2026, 0x85], [0x2020, 0x86], [0x2021, 0x87], [0x02c6, 0x88], [0x2030, 0x89],
  [0x0160, 0x8a], [0x2039, 0x8b], [0x0152, 0x8c], [0x017d, 0x8e], [0x2018, 0x91],
  [0x2019, 0x92], [0x201c, 0x93], [0x201d, 0x94], [0x2022, 0x95], [0x2013, 0x96],
  [0x2014, 0x97], [0x02dc, 0x98], [0x2122, 0x99], [0x0161, 0x9a], [0x203a, 0x9b],
  [0x0153, 0x9c], [0x017e, 0x9e], [0x0178, 0x9f]]);

function pdfString(value) {
  const bytes = [];
  for (const character of String(value ?? '')) {
    let code = character.codePointAt(0);
    if (code > 255) code = CP1252_EXTRA.get(code) ?? 63;
    if (code < 32 || code > 255) code = 63;
    const char = String.fromCharCode(code);
    bytes.push(char === '\\' || char === '(' || char === ')' ? `\\${char}`
      : code < 32 || code > 126 ? `\\${code.toString(8).padStart(3, '0')}` : char);
  }
  return `(${bytes.join('')})`;
}

function pdfLines({ trip, itinerary, lodging, flights = [], budget = null, travelers = null, isDraft = false }) {
  if (!trip?.destination || !trip?.startDate || !trip?.endDate) {
    throw new Error('El viaje debe tener destino y fechas para exportar el itinerario.');
  }
  const days = Array.isArray(itinerary?.dias) ? itinerary.dias : [];
  if (!days.length || !days.some((day) => Array.isArray(day.actividades) && day.actividades.length)) {
    throw new Error('Agrega al menos una actividad al borrador antes de exportar el PDF.');
  }
  const lines = [
    trip.nombre || trip.name || 'Itinerario de viaje',
    `Destino: ${trip.destination}${trip.country ? `, ${trip.country}` : ''}`,
    `Fechas: ${trip.startDate} - ${trip.endDate} · ${days.length} dias${travelers ? ` · ${travelers} viajero(s)` : ''}`,
    isDraft ? 'BORRADOR LOCAL · aun no guardado en Oracle' : 'Itinerario guardado', '',
    'RESUMEN DEL VIAJE',
    `Duración: ${days.length} días · Paradas planificadas: ${days.reduce((sum, day) => sum + (day.actividades?.length || 0), 0)}`,
    `Alojamiento: ${lodging?.nombre || 'Pendiente de seleccionar o verificar'} · Vuelos: ${flights.length ? `${flights.length} opción(es) registrada(s)` : 'Pendientes de confirmar'}`,
    'Las ilustraciones del encabezado son decorativas; no representan una fotografía del destino.', '',
    'VUELOS',
  ];
  if (flights.length) flights.forEach((flight) => {
    const details = flightPrintDetails(flight);
    lines.push(details.title, ...details.lines);
  });
  else lines.push('No hay vuelos registrados.');
  lines.push('', 'ALOJAMIENTO', lodging ? `${lodging.nombre || 'Alojamiento'} · ${lodging.direccion || 'Direccion no informada'}` : 'No hay alojamiento registrado.', '', 'ITINERARIO POR DIA');

  days.forEach((day, dayIndex) => {
    lines.push('', `DIA ${dayIndex + 1} · ${day.fecha || 'Fecha no informada'}`);
    if (!day.actividades?.length) { lines.push('Dia sin actividades asignadas.'); return; }
    const dayExpenses = (budget?.included || []).filter((entry) => day.actividades.some((activity) => entry.id === `activity:${String(activity.id)}`));
    const dayUnknown = (budget?.unknownEntries || []).filter((entry) => day.actividades.some((activity) => entry.id === `activity:${String(activity.id)}`));
    const dayTotal = dayExpenses.reduce((sum, entry) => sum + Number(entry.convertedAmount || 0), 0);
    if (dayExpenses.length) lines.push(`Gasto conocido/estimado del dia: ${formatMoney(dayTotal, budget.currency)}`);
    if (dayUnknown.length) lines.push(`${dayUnknown.length} costo(s) de actividad pendiente(s) de verificar.`);
    day.actividades.forEach((activity, activityIndex) => {
      const details = [activity.categoria, activity.zona || activity.ubicacion, activityCostLabel(activity, budget)]
        .filter(Boolean).join(' · ');
      lines.push(`${activityIndex + 1}. ${activity.nombre || 'Actividad sin nombre'}${details ? ` · ${details}` : ''}`);
      if (activity.descripcion) lines.push(`Detalle: ${activity.descripcion}`);
      if (activity.fuente) lines.push(`Fuente: ${activity.fuente}`);
      const photoAttribution = activity.atribucionFoto || activity.photoAttribution || activity.imageAttribution;
      const photoLicense = activity.licenciaFoto || activity.photoLicense || activity.imageLicense;
      if ((activity.imagenUrl || activity.photoUrl || activity.imageUrl) && photoAttribution && photoLicense) {
        lines.push(`Atribución de fotografía: ${photoAttribution} · Licencia: ${photoLicense}`);
      }
      const url = safeUrl(activity.urlFuente);
      const mapUrl = activityMapUrl(activity);
      if (url || mapUrl) lines.push(`LINK::${url || mapUrl}`);
    });
  });
  lines.push('', 'PRESUPUESTO');
  if (budget) {
    lines.push(`Presupuesto total: ${budget.total == null ? 'No definido' : formatMoney(budget.total, budget.currency)} · Gastos confirmados: ${formatMoney(budget.confirmed, budget.currency)}`,
      `Estimaciones: ${formatMoney(budget.estimated, budget.currency)}`,
      `Saldo proyectado parcial: ${budget.remaining == null ? 'No calculado' : formatMoney(budget.remaining, budget.currency)}${Number(budget.unknown) > 0 ? ' · faltan gastos por verificar' : ''}`,
      `Costos pendientes: ${Number(budget.unknown) || 0} · Importes sin conversion: ${Number(budget.foreignCurrency) || 0}`);
    Object.entries(budget.byCategory || {}).forEach(([category, amount]) => lines.push(`Subtotal ${category}: ${formatMoney(amount, budget.currency)}`));
    (budget.included || []).forEach((entry) => lines.push(`Incluido · ${entry.category || 'Gasto'} · ${entry.label}: ${formatMoney(entry.convertedAmount, budget.currency)} · ${budgetConfidenceLabel(entry.confidence)}${entry.converted ? ` · referencia desde ${entry.currency} · ${entry.rateSource || 'tasa manual'}${entry.rateDate ? ` · ${entry.rateDate}` : ''}; se contabiliza como estimacion` : ''}${entry.source ? ` · Fuente: ${entry.source}` : ''}${entry.queriedAt ? ` · Consulta: ${entry.queriedAt}` : ''}${entry.availability ? ` · ${availabilityLabel(entry.availability)}` : ''}`));
    const pendingByCategory = new Map();
    (budget.unknownEntries || []).forEach((entry) => {
      const category = entry.category || 'Otros gastos';
      pendingByCategory.set(category, (pendingByCategory.get(category) || 0) + 1);
    });
    if (pendingByCategory.size) {
      lines.push('Importes por verificar');
      pendingByCategory.forEach((count, category) => lines.push(`${category}: ${count} partida(s)`));
    }
  } else lines.push('Resumen de presupuesto no disponible.');
  lines.push('El presupuesto no garantiza el costo final si faltan precios o tasas.', '', 'INFORMACION PRACTICA Y FUENTES', 'Los horarios, traslados, precios y disponibilidad no informados deben verificarse.');
  return lines.flatMap((line) => line.startsWith('LINK::') ? [line] : wrapPdfLine(line, 94));
}

function activityMapUrl(activity) {
  const latitude = Number(activity?.latitud ?? activity?.latitude ?? activity?.lat);
  const longitude = Number(activity?.longitud ?? activity?.longitude ?? activity?.lon ?? activity?.lng);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
  return `https://www.openstreetmap.org/?mlat=${latitude}&mlon=${longitude}#map=16/${latitude}/${longitude}`;
}

function activityCostLabel(activity, budget) {
  const id = `activity:${String(activity?.id ?? '')}`;
  const included = budget?.included?.find((entry) => entry.id === id);
  if (included) return `${included.confidence === 'MANUAL_ESTIMATE' ? 'Estimacion manual' : included.confidence === 'MANUAL_FREE' ? 'Gratuito por supuesto manual' : 'Importe registrado'}: ${formatMoney(included.convertedAmount, budget.currency)}`;
  if (budget?.unknownEntries?.some((entry) => entry.id === id)) return 'Tarifa por verificar';
  if (activity?.costo == null) return 'Tarifa por verificar';
  if (!activity.moneda || !/^[A-Z]{3}$/.test(activity.moneda)) return 'Moneda por verificar';
  return `${activity.costoEstado === 'MANUAL_ESTIMATE' ? 'Estimacion manual' : 'Importe registrado'}: ${formatMoney(activity.costo, activity.moneda)}`;
}

function flightPrintDetails(flight) {
  const isOffer = Array.isArray(flight?.slices);
  const sandbox = flight?.testData === true || flight?.sandbox === true;
  const amount = isOffer ? flight.totalAmount : flight.precio;
  const currency = isOffer ? flight.currency : flight.moneda;
  const title = isOffer
    ? `Oferta Duffel · ${flight.slices.map((slice) => `${slice.origin || 'Origen'} → ${slice.destination || 'Destino'}`).join(' / ')}`
    : `${flight?.origen || 'Origen'} → ${flight?.destino || 'Destino'}`;
  const lines = [];
  if (isOffer) {
    flight.slices.forEach((slice, index) => {
      lines.push(`${index ? 'Regreso' : 'Ida'} · ${slice.departureAt || 'Horario no informado'} · ${slice.duration || 'Duración no informada'} · ${slice.stops == null ? 'Escalas no informadas' : `${slice.stops} escala(s)`}`);
      (slice.segments || []).forEach((segment) => lines.push(`${segment.origin || ''} → ${segment.destination || ''} · ${segment.operatingCarrier || segment.marketingCarrier || 'Aerolínea no informada'}${segment.flightNumber ? ` · ${segment.flightNumber}` : ''}`));
    });
  } else lines.push(`${flight?.fechaSalida || 'Fecha no informada'} · ${flight?.aerolinea || 'Aerolínea no informada'}${flight?.numeroVuelo ? ` · ${flight.numeroVuelo}` : ''}`);
  if (amount != null && !currency && Number.isFinite(Number(amount))) lines.push(`Importe registrado: ${amount}; moneda no almacenada.`);
  else if (amount == null || !currency || !/^[A-Z]{3}$/.test(currency) || !Number.isFinite(Number(amount))) lines.push('Precio o moneda no disponible.');
  else lines.push(`${sandbox ? 'SIMULACIÓN Sandbox · importe ficticio, no comprable' : isOffer ? 'Cotización del proveedor · verificar vigencia' : 'Importe registrado'}: ${formatMoney(amount, currency)}`);
  if (flight?.expiresAt) lines.push(`Vigencia informada por proveedor: ${flight.expiresAt}`);
  if (sandbox) lines.push('Excluido del presupuesto real; no representa una tarifa comercial.');
  return { title, lines };
}

function wrapPdfLine(value, width) {
  const text = String(value ?? '');
  if (text.length <= width) return [text];
  const words = text.split(/\s+/);
  const lines = [];
  let current = '';
  for (const word of words) {
    if (current && `${current} ${word}`.length > width) { lines.push(current); current = word; }
    else current = current ? `${current} ${word}` : word;
  }
  if (current) lines.push(current);
  return lines;
}

function asciiBytes(text) { return Uint8Array.from([...text].map((character) => character.charCodeAt(0) & 0xff)); }

function joinBytes(parts) {
  const length = parts.reduce((sum, part) => sum + part.length, 0);
  const output = new Uint8Array(length);
  let offset = 0;
  parts.forEach((part) => { output.set(part, offset); offset += part.length; });
  return output;
}

/** Builds a valid, text-only PDF from the exact current draft. No Oracle or network access. */
export function buildItineraryPdf(input) {
  const rows = pdfLines(input);
  const linesPerPage = 46;
  const pages = paginatePdfRows(rows, linesPerPage);
  const pageIds = pages.map((_, index) => 5 + index * 2);
  const objects = new Map();
  let nextAnnotationId = 5 + pages.length * 2;
  objects.set(1, asciiBytes('<< /Type /Catalog /Pages 2 0 R >>'));
  objects.set(2, asciiBytes(`<< /Type /Pages /Kids [${pageIds.map((id) => `${id} 0 R`).join(' ')}] /Count ${pages.length} >>`));
  objects.set(3, asciiBytes('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>'));
  objects.set(4, asciiBytes('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>'));
  pages.forEach((page, index) => {
    const pageId = pageIds[index];
    const contentId = pageId + 1;
    const destination = input.trip.destination + (input.trip.country ? ` · ${input.trip.country}` : '');
    const annotations = [];
    const bodyCommands = page.flatMap((line, lineIndex) => {
      const y = 753 - lineIndex * 15;
      if (line.startsWith('LINK::')) {
        const url = safeUrl(line.slice('LINK::'.length));
        if (!url) return [];
        const annotationId = nextAnnotationId++;
        annotations.push(annotationId);
        objects.set(annotationId, asciiBytes(`<< /Type /Annot /Subtype /Link /Rect [48 ${y - 3} 250 ${y + 10}] /Border [0 0 0] /A << /S /URI /URI ${pdfString(url)} >> >>`));
        return [`BT /F2 8 Tf 48 ${y} Td ${pdfString('Abrir mapa / fuente')} Tj ET`];
      }
      if (isPdfSectionHeading(line)) return [
        `q 0.91 0.96 0.95 rg 40 ${y - 3} 532 17 re f Q`,
        `BT /F2 10 Tf 48 ${y} Td ${pdfString(line)} Tj ET`,
      ];
      if (/^\d+\.\s/.test(line)) return [
        `q 0.97 0.985 0.98 rg 43 ${y - 4} 526 16 re f Q`,
        `BT /F1 9 Tf 48 ${y} Td ${pdfString(line)} Tj ET`,
      ];
      return [`BT /F1 9 Tf 48 ${y} Td ${pdfString(line)} Tj ET`];
    });
    const commands = [
      'q 0.07 0.34 0.38 rg 0 772 612 70 re f Q',
      // Abstract route-map artwork is a SmartTrip vector decoration, not a destination photo.
      'q 0.19 0.52 0.54 rg 392 772 m 445 824 l 485 790 l 538 842 l 590 772 l h f Q',
      'q 0.31 0.66 0.62 rg 418 772 m 454 803 l 476 782 l 512 815 l 540 790 l 558 804 l 585 772 l h f Q',
      'q 0.98 0.78 0.42 rg 518 812 m 522 812 525 815 525 819 c 525 823 522 826 518 826 c 514 826 511 823 511 819 c 511 815 514 812 518 812 c f Q',
      'q 1 0.91 0.72 RG 1.6 w 407 784 m 438 800 l 462 783 l 488 811 l 525 819 l S Q',
      `BT /F2 17 Tf 48 815 Td ${pdfString('SMARTTRIP PLANNER')} Tj ET`,
      `BT /F1 10 Tf 48 794 Td ${pdfString(destination)} Tj ET`,
      'q 0.84 0.92 0.91 rg 48 44 516 0.7 re f Q',
      `BT /F1 8 Tf 48 29 Td ${pdfString(`Guía de viaje · ${index + 1} / ${pages.length}`)} Tj ET`,
      ...bodyCommands,
    ].join('\n');
    const stream = asciiBytes(commands);
    const annotationRefs = annotations.length ? `/Annots [${annotations.map((id) => `${id} 0 R`).join(' ')}]` : '';
    objects.set(pageId, asciiBytes(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${contentId} 0 R ${annotationRefs} >>`));
    objects.set(contentId, joinBytes([asciiBytes(`<< /Length ${stream.length} >>\nstream\n`), stream, asciiBytes('\nendstream')]));
  });
  const maxId = Math.max(...objects.keys());
  const parts = [asciiBytes('%PDF-1.4\n')];
  const offsets = new Array(maxId + 1).fill(0);
  let byteLength = parts[0].length;
  for (let id = 1; id <= maxId; id += 1) {
    const body = objects.get(id);
    if (!body) throw new Error('No se pudo construir una pagina del PDF.');
    const item = joinBytes([asciiBytes(`${id} 0 obj\n`), body, asciiBytes('\nendobj\n')]);
    offsets[id] = byteLength;
    parts.push(item);
    byteLength += item.length;
  }
  const xrefOffset = byteLength;
  let xref = `xref\n0 ${maxId + 1}\n0000000000 65535 f \n`;
  for (let id = 1; id <= maxId; id += 1) xref += `${String(offsets[id]).padStart(10, '0')} 00000 n \n`;
  parts.push(asciiBytes(`${xref}trailer\n<< /Size ${maxId + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF`));
  const pdf = joinBytes(parts);
  if (pdf.length > 5 * 1024 * 1024) throw new Error('El PDF supera el limite de 5 MB. Reduce el contenido del itinerario.');
  return pdf;
}

function paginatePdfRows(rows, linesPerPage) {
  const pages = [[]];
  for (const line of rows) {
    const page = pages[pages.length - 1];
    const startsDay = /^DIA \d+ ·/.test(line);
    // Keep a day's heading with its first activities instead of splitting a
    // visit midway through a page. Long individual days still wrap safely.
    if (page.length >= linesPerPage || (startsDay && page.length > linesPerPage - 5)) pages.push([]);
    pages[pages.length - 1].push(line);
  }
  return pages.filter((page) => page.length > 0);
}

function isPdfSectionHeading(line) {
  return /^(VUELOS|ALOJAMIENTO|PRESUPUESTO|ITINERARIO POR DIA|INFORMACION PRACTICA Y FUENTES|DIA \d+ ·)/.test(String(line || ''));
}

function budgetConfidenceLabel(value) {
  return ({ CONFIRMED: 'Confirmado por el usuario', VERIFIED_QUOTE: 'Cotización verificada',
    MANUAL_ESTIMATE: 'Estimación manual', MANUAL_FREE: 'Gratuito por supuesto manual',
    VERIFIED_FREE: 'Gratuito verificado', SANDBOX: 'Sandbox · dato de prueba', UNKNOWN: 'Desconocido' })[value]
    || 'Importe registrado';
}

function availabilityLabel(value) {
  return ({ available: 'Disponible según la última consulta', unknown: 'Pendiente de verificar',
    expired: 'Cotización vencida', not_provided: 'La fuente no informa disponibilidad' })[value]
    || 'Pendiente de verificar';
}

export function downloadItineraryPdf(pdfBytes, filename, documentRef = globalThis.document, urlApi = globalThis.URL,
  schedule = globalThis.setTimeout) {
  if (!(pdfBytes instanceof Uint8Array) || pdfBytes.length < 8 || new TextDecoder().decode(pdfBytes.slice(0, 8)) !== '%PDF-1.4') {
    throw new Error('No se pudo generar un archivo PDF valido.');
  }
  if (!documentRef?.createElement || !urlApi?.createObjectURL) throw new Error('Este navegador no permite descargar el PDF.');
  const url = urlApi.createObjectURL(new Blob([pdfBytes], { type: 'application/pdf' }));
  const anchor = documentRef.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  if (typeof schedule === 'function') schedule(() => urlApi.revokeObjectURL(url), 1000);
  else urlApi.revokeObjectURL(url);
}
