import React, { useState, useEffect } from 'react';
import {
  Calendar,
  CalendarDays,
  CheckCircle2,
  Clock,
  MapPin,
  Plus,
  Sparkles,
  Trash2,
  X,
  AlertCircle
} from 'lucide-react';

export default function GeneradorItinerario({ tripData = null, onNavigate = null }) {
  const [fechaInicio, setFechaInicio] = useState(tripData?.startDate || '');
  const [fechaFin, setFechaFin] = useState(tripData?.endDate || '');
  const [destino, setDestino] = useState(tripData?.destination || '');
  const [itinerario, setItinerario] = useState([]);
  const [diaSeleccionado, setDiaSeleccionado] = useState(null);
  const [cargando, setCargando] = useState(false);
  const [error, setError] = useState('');
  const [conexionBackend, setConexionBackend] = useState(null); // true = backend, false = local

  // Campos para la nueva actividad en el día seleccionado
  const [nuevaActividadTexto, setNuevaActividadTexto] = useState('');
  const [nuevaActividadHora, setNuevaActividadHora] = useState('');

  // Si se reciben datos de un viaje creado previamente, auto-generar el espacio
  useEffect(() => {
    if (tripData?.startDate && tripData?.endDate) {
      setFechaInicio(tripData.startDate);
      setFechaFin(tripData.endDate);
      if (tripData.destination) setDestino(tripData.destination);
      generarEspacioItinerario(tripData.startDate, tripData.endDate);
    }
  }, [tripData]);

  // Función para calcular días en el cliente como fallback resiliente
  const generarDiasLocalmente = (inicioStr, finStr) => {
    const inicio = new Date(`${inicioStr}T00:00:00`);
    const fin = new Date(`${finStr}T00:00:00`);
    const dias = [];
    let actual = new Date(inicio);
    let diaNum = 1;

    while (actual <= fin) {
      const year = actual.getFullYear();
      const month = String(actual.getMonth() + 1).padStart(2, '0');
      const day = String(actual.getDate()).padStart(2, '0');
      const fechaIso = `${year}-${month}-${day}`;

      dias.push({
        id: `local-dia-${diaNum}-${fechaIso}`,
        numero: diaNum,
        fecha: fechaIso,
        actividades: []
      });

      actual.setDate(actual.getDate() + 1);
      diaNum++;
    }

    return dias;
  };

  const generarEspacioItinerario = async (inicio, fin) => {
    setError('');

    if (!inicio || !fin) {
      setError('Por favor ingresa tanto la fecha de inicio como la de fin.');
      return;
    }

    if (fin < inicio) {
      setError('La fecha de regreso no puede ser anterior a la fecha de inicio.');
      return;
    }

    setCargando(true);

    try {
      // Intento de conexión con el backend de Spring Boot (HU-004)
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 3500);

      const respuesta = await fetch('http://localhost:8080/api/itinerario/generar', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fechaInicio: inicio, fechaFin: fin }),
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (respuesta.ok) {
        const datos = await respuesta.json();
        // Mapear días desde el backend
        const diasBackend = (datos.dias || []).map((dia, idx) => ({
          id: dia.id || `dia-${idx + 1}`,
          numero: idx + 1,
          fecha: dia.fecha,
          actividades: Array.isArray(dia.actividades)
            ? dia.actividades.map((act, aIdx) =>
                typeof act === 'string'
                  ? { id: `act-${aIdx}`, texto: act, hora: '' }
                  : act
              )
            : []
        }));

        setItinerario(diasBackend);
        setConexionBackend(true);
        // Seleccionar por defecto el día 1 para facilitar la interacción
        if (diasBackend.length > 0) {
          setDiaSeleccionado(diasBackend[0].id);
        }
      } else {
        throw new Error('Respuesta no satisfactoria del backend');
      }
    } catch (err) {
      // Si el backend no está iniciado o falla, usar fallback local inteligente
      console.warn('Backend no disponible, generando itinerario en modo local:', err);
      const diasCalculados = generarDiasLocalmente(inicio, fin);
      setItinerario(diasCalculados);
      setConexionBackend(false);
      if (diasCalculados.length > 0) {
        setDiaSeleccionado(diasCalculados[0].id);
      }
    } finally {
      setCargando(false);
    }
  };

  const handleSubmitForm = (e) => {
    e.preventDefault();
    generarEspacioItinerario(fechaInicio, fechaFin);
  };

  // Criterio 2: Seleccionar un día específico
  const handleSeleccionarDia = (diaId) => {
    setDiaSeleccionado((prev) => (prev === diaId ? null : diaId));
    setNuevaActividadTexto('');
    setNuevaActividadHora('');
  };

  // Criterio 2: Agregar actividad de forma manual al día seleccionado
  const handleAgregarActividad = (diaId, e) => {
    e?.preventDefault();

    if (!nuevaActividadTexto.trim()) return;

    setItinerario((prevDias) =>
      prevDias.map((dia) => {
        if (dia.id === diaId) {
          const nuevaAct = {
            id: `act-${Date.now()}-${Math.random().toString(36).substr(2, 4)}`,
            texto: nuevaActividadTexto.trim(),
            hora: nuevaActividadHora.trim()
          };
          return {
            ...dia,
            actividades: [...dia.actividades, nuevaAct]
          };
        }
        return dia;
      })
    );

    setNuevaActividadTexto('');
    setNuevaActividadHora('');
  };

  // Eliminar actividad
  const handleEliminarActividad = (diaId, actividadId) => {
    setItinerario((prevDias) =>
      prevDias.map((dia) => {
        if (dia.id === diaId) {
          return {
            ...dia,
            actividades: dia.actividades.filter((a) => a.id !== actividadId)
          };
        }
        return dia;
      })
    );
  };

  const formatearFecha = (fechaStr) => {
    if (!fechaStr) return '';
    try {
      const fecha = new Date(`${fechaStr}T00:00:00Z`);
      return new Intl.DateTimeFormat('es-CO', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
        timeZone: 'UTC'
      }).format(fecha);
    } catch {
      return fechaStr;
    }
  };

  const totalActividades = itinerario.reduce(
    (acc, dia) => acc + (dia.actividades?.length || 0),
    0
  );

  return (
    <section className="itinerario-page">
      <div className="itinerario-container">
        {/* Encabezado */}
        <div className="itinerario-header">
          <div className="itinerario-header-top">
            <div className="itinerario-title-group">
              <div className="itinerario-icon-badge">
                <CalendarDays size={26} />
              </div>
              <div>
                <span className="eyebrow">Historia de Usuario #4</span>
                <h1>
                  {destino
                    ? `Itinerario para ${destino}`
                    : 'Planificador de Itinerario'}
                </h1>
                <p>
                  Crea un espacio estructurado por días y agrega actividades manualmente a cada uno.
                </p>
              </div>
            </div>

            {itinerario.length > 0 && (
              <span
                className={`itinerario-status-pill ${
                  conexionBackend === false ? 'offline' : ''
                }`}
              >
                <CheckCircle2 size={16} />
                {itinerario.length} {itinerario.length === 1 ? 'día generado' : 'días generados'}
              </span>
            )}
          </div>

          {/* Formulario para ingresar fechas y generar el itinerario */}
          <form onSubmit={handleSubmitForm} className="itinerario-form">
            <div className="itinerario-input-group">
              <label htmlFor="itin-fecha-inicio">
                <Calendar size={16} /> Fecha de inicio
              </label>
              <div className="itinerario-input-wrapper">
                <Calendar size={18} />
                <input
                  id="itin-fecha-inicio"
                  type="date"
                  value={fechaInicio}
                  onChange={(e) => setFechaInicio(e.target.value)}
                  required
                />
              </div>
            </div>

            <div className="itinerario-input-group">
              <label htmlFor="itin-fecha-fin">
                <Calendar size={16} /> Fecha de fin
              </label>
              <div className="itinerario-input-wrapper">
                <Calendar size={18} />
                <input
                  id="itin-fecha-fin"
                  type="date"
                  min={fechaInicio || undefined}
                  value={fechaFin}
                  onChange={(e) => setFechaFin(e.target.value)}
                  required
                />
              </div>
            </div>

            <button
              type="submit"
              className="btn-generar"
              disabled={cargando}
            >
              <Sparkles size={18} />
              {cargando ? 'Generando...' : 'Generar Espacio'}
            </button>
          </form>

          {error && (
            <div className="itinerario-alerta" role="alert">
              <AlertCircle size={18} />
              <span>{error}</span>
            </div>
          )}
        </div>

        {/* Visualización del Itinerario Estructurado */}
        {itinerario.length > 0 ? (
          <div className="itinerario-content">
            <div className="itinerario-meta-bar">
              <div className="itinerario-counter">
                Mostrando <span>{itinerario.length} días</span> estructurados ·{' '}
                <span>{totalActividades}</span> {totalActividades === 1 ? 'actividad' : 'actividades'} agregadas
              </div>
              <div className="itinerario-hint">
                💡 Haz clic en cualquier día para seleccionarlo y habilitar la opción de agregar actividades.
              </div>
            </div>

            {/* Lista dividida por cada uno de los días (Criterio 1) */}
            <div className="dias-lista">
              {itinerario.map((dia, index) => {
                const esSeleccionado = diaSeleccionado === dia.id;
                const tieneActividades = dia.actividades && dia.actividades.length > 0;

                return (
                  <div
                    key={dia.id}
                    className={`dia-card ${esSeleccionado ? 'seleccionado' : ''}`}
                    onClick={() => handleSeleccionarDia(dia.id)}
                    tabIndex={0}
                    role="button"
                    aria-pressed={esSeleccionado}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        handleSeleccionarDia(dia.id);
                      }
                    }}
                  >
                    {/* Encabezado del día */}
                    <div className="dia-card-header">
                      <div className="dia-card-title-group">
                        <div className="dia-numero-badge">
                          {index + 1}
                        </div>
                        <div className="dia-info">
                          <h3>Día {index + 1}</h3>
                          <p>{formatearFecha(dia.fecha)}</p>
                        </div>
                      </div>

                      <div>
                        {tieneActividades ? (
                          <span className="dia-tag-activo">
                            {dia.actividades.length}{' '}
                            {dia.actividades.length === 1 ? 'actividad' : 'actividades'}
                          </span>
                        ) : (
                          <span className="dia-tag-vacio">
                            Día vacío · Sin actividades
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Cuerpo: Actividades o aviso de vacío estructurado */}
                    <div className="dia-cuerpo" onClick={(e) => e.stopPropagation()}>
                      {tieneActividades ? (
                        <div className="actividades-lista">
                          {dia.actividades.map((act) => (
                            <div key={act.id} className="actividad-item">
                              <div className="actividad-left">
                                {act.hora && (
                                  <span className="actividad-hora">
                                    <Clock size={13} /> {act.hora}
                                  </span>
                                )}
                                <span className="actividad-texto">{act.texto}</span>
                              </div>
                              <button
                                type="button"
                                className="btn-eliminar-actividad"
                                title="Eliminar actividad"
                                onClick={() => handleEliminarActividad(dia.id, act.id)}
                              >
                                <Trash2 size={16} />
                              </button>
                            </div>
                          ))}
                        </div>
                      ) : (
                        <div className="dia-vacio-aviso">
                          <span>
                            Este día aún no tiene actividades programadas.
                          </span>
                          <button
                            type="button"
                            className="btn-seleccionar-dia"
                            onClick={() => handleSeleccionarDia(dia.id)}
                          >
                            <Plus size={16} />
                            {esSeleccionado ? 'Día seleccionado' : 'Seleccionar día'}
                          </button>
                        </div>
                      )}

                      {/* Criterio 2: Al seleccionar el día, se habilita la opción de agregar actividad manual */}
                      {esSeleccionado && (
                        <form
                          className="form-agregar-actividad"
                          onSubmit={(e) => handleAgregarActividad(dia.id, e)}
                        >
                          <div className="form-agregar-actividad-header">
                            <h4>
                              <Plus size={16} /> Agregar actividad manual al Día {index + 1}
                            </h4>
                            <button
                              type="button"
                              className="btn-eliminar-actividad"
                              title="Cerrar formulario"
                              onClick={() => handleSeleccionarDia(dia.id)}
                            >
                              <X size={16} />
                            </button>
                          </div>

                          <div className="form-actividad-grid">
                            <input
                              type="text"
                              placeholder="Ej. Desayuno típico en la plaza, Visita al museo..."
                              value={nuevaActividadTexto}
                              onChange={(e) => setNuevaActividadTexto(e.target.value)}
                              autoFocus
                              required
                            />
                            <input
                              type="text"
                              placeholder="Hora (ej. 09:00 AM o Tarde)"
                              value={nuevaActividadHora}
                              onChange={(e) => setNuevaActividadHora(e.target.value)}
                            />
                          </div>

                          <div className="form-actividad-acciones">
                            <button
                              type="button"
                              className="btn-cancelar-actividad"
                              onClick={() => handleSeleccionarDia(dia.id)}
                            >
                              Cerrar
                            </button>
                            <button
                              type="submit"
                              className="btn-guardar-actividad"
                            >
                              <Plus size={16} /> Guardar actividad
                            </button>
                          </div>
                        </form>
                      )}

                      {/* Botón rápido si no está abierto el formulario pero ya hay actividades */}
                      {!esSeleccionado && tieneActividades && (
                        <button
                          type="button"
                          className="btn-abrir-actividad"
                          onClick={() => handleSeleccionarDia(dia.id)}
                        >
                          <Plus size={16} /> + Agregar otra actividad a este día
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ) : (
          /* Estado vacío inicial */
          <div className="itinerario-empty-state">
            <div className="itinerario-empty-icon">
              <CalendarDays size={32} />
            </div>
            <h3>Comienza tu itinerario</h3>
            <p>
              Ingresa las fechas de inicio y regreso de tu viaje arriba y presiona{' '}
              <strong>"Generar Espacio"</strong> para estructurar automáticamente
              los días vacíos de tu aventura.
            </p>
          </div>
        )}
      </div>
    </section>
  );
}