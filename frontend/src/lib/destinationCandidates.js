export function normalizeDestinationCandidates(rows) {
  if (!Array.isArray(rows)) return [];
  return rows.flatMap((candidate) => {
    const latitude = Number(candidate?.latitud);
    const longitude = Number(candidate?.longitud);
    if (!candidate?.id || typeof candidate?.nombreCompleto !== 'string' || !candidate.nombreCompleto.trim()
        || !Number.isFinite(latitude) || latitude < -90 || latitude > 90
        || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) return [];
    return [{
      ...candidate,
      nombreCompleto: candidate.nombreCompleto.trim(),
      latitud: latitude,
      longitud: longitude,
    }];
  });
}
