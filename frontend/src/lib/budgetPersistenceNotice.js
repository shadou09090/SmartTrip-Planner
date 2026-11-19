export function budgetPersistenceNotice({
  enabled,
  packageStatus,
  packageDirty,
  scenarioReady,
  saveStatus = 'idle',
}) {
  if (!enabled) {
    return {
      state: 'local-only',
      role: 'status',
      message: 'Este escenario se guarda localmente por usuario y viaje; la persistencia integral está desactivada y no se sincroniza con Oracle.',
    };
  }

  if (saveStatus === 'saving') {
    return { state: 'saving', role: 'status', message: 'Guardando el paquete; se confirmará solo después de verificarlo mediante GET.' };
  }
  if (saveStatus === 'failed') {
    return { state: 'error', role: 'alert', message: 'Falló el guardado o la verificación del paquete. Los cambios siguen locales y no se consideran guardados en Oracle.' };
  }
  if (packageStatus === 'unavailable') {
    return { state: 'error', role: 'alert', message: 'No se pudo leer el paquete en Oracle. El escenario visible se conserva localmente y su sincronización no está verificada.' };
  }
  if (packageStatus === 'conflict') {
    return { state: 'conflict', role: 'alert', message: 'Hay un conflicto con la versión del paquete en Oracle. Los cambios locales se conservan y no se presentan como guardados.' };
  }
  if (packageStatus === 'loading' || !scenarioReady) {
    return { state: 'checking', role: 'status', message: 'Comprobando la sincronización del presupuesto con Oracle…' };
  }
  if (packageStatus === 'not-found') {
    return { state: 'pending', role: 'status', message: 'Aún no hay un paquete confirmado en Oracle. Las estimaciones actuales son locales hasta guardar y verificar el paquete.' };
  }
  if (packageDirty) {
    return { state: 'pending', role: 'status', message: 'Hay cambios locales pendientes en el paquete. No se consideran guardados en Oracle hasta completar PUT y GET.' };
  }

  return {
    state: 'saved',
    role: 'status',
    message: 'Presupuesto sincronizado y verificado con Oracle. Las estimaciones nuevas quedan locales hasta guardar y verificar el paquete.',
  };
}
