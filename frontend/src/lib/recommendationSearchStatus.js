/** Build an honest, user-visible explanation for a completed empty recommendation search. */
export function emptyRecommendationStatus(diagnostics, sourceWarnings = [], requestError = '') {
  const failure = typeof requestError === 'string' ? requestError.trim() : '';
  if (failure) return `No se pudo completar la consulta de recomendaciones: ${failure}`;

  const warnings = Array.isArray(sourceWarnings)
    ? sourceWarnings.filter((value) => typeof value === 'string' && value.trim())
    : [];
  if (warnings.length) {
    return `No se recibieron candidatos suficientes. Avisos de fuentes: ${warnings.join(' · ')}`;
  }

  if (!diagnostics) {
    return 'No se recibieron candidatos y el backend no proporcionó el desglose por fuente. Reintenta la búsqueda para obtener un diagnóstico actualizado.';
  }

  const sources = Array.isArray(diagnostics.fuentesConsultadas) ? diagnostics.fuentesConsultadas : [];
  const results = diagnostics.resultadosConservadosPorFuente || {};
  const sourceSummary = sources.map((source) => {
    const count = Number.isFinite(Number(results[source])) ? Number(results[source]) : null;
    return count == null ? `${source}: sin conteo conservado` : `${source}: ${count} conservados`;
  });
  const finalCount = Number.isFinite(Number(diagnostics.atractivosFinales))
    ? Number(diagnostics.atractivosFinales)
    : 0;
  const detail = sourceSummary.length ? ` Fuentes consultadas (${sourceSummary.join(' · ')}).` : ' El backend no registró fuentes consultadas.';
  return `Búsqueda completada sin candidatos elegibles: ${finalCount} atractivos finales.${detail}`;
}
