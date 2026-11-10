// Calcular en centavos evita alertas falsas por redondeo decimal.
export function costoEnCentavos(valor) {
  if (typeof valor !== 'number' && typeof valor !== 'string') return null;
  if (typeof valor === 'string' && !/^\d+(\.\d{1,2})?$/.test(valor.trim())) return null;
  const numero = Number(valor);
  const centavos = Math.round(numero * 100);
  return Number.isFinite(numero) && numero >= 0 && Number.isSafeInteger(centavos)
    ? centavos : null;
}

export function resumirPresupuesto(dias, presupuesto) {
  const totalCentavos = dias.reduce((total, dia) => total + (dia.actividades || [])
    .reduce((subtotal, actividad) => subtotal + (costoEnCentavos(actividad.costo) ?? 0), 0), 0);
  const limiteCentavos = costoEnCentavos(presupuesto);
  const tienePresupuesto = limiteCentavos !== null && limiteCentavos > 0;
  return {
    total: totalCentavos / 100,
    limite: tienePresupuesto ? limiteCentavos / 100 : null,
    excedido: tienePresupuesto && totalCentavos > limiteCentavos,
    diferencia: tienePresupuesto ? (limiteCentavos - totalCentavos) / 100 : null,
  };
}

export function formatearCosto(valor) {
  return new Intl.NumberFormat('es-CO', {
    style: 'currency', currency: 'COP', maximumFractionDigits: 2,
  }).format(valor);
}
