import React, { useState, useEffect, useMemo } from 'react';
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
  AlertCircle,
  Navigation,
  Compass
} from 'lucide-react';
import TravelTimeCard from './TravelTimeCard';
import {
  findDestination,
  resolveCoordinates,
  DESTINATIONS
} from '../data/destinations';

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
  const [nuevaActividadUbicacion, setNuevaActividadUbicacion] = useState('');

  // Lugares recomendados para el destino actual
  const lugaresRecomendados = useMemo(() => {
    const dest = findDestination(destino);
    return dest ? dest.places : [];
  }, [destino]);

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
            ? dia.actividades.map((act, aIdx) => {
                if (typeof act === 'string') {
                  const coords = resolveCoordinates(act, destino);
                  return {
                    id: `act-${aIdx}`,
                    texto: act,
                    hora: '',
                    ubicacion: act,
                    coordenadas: coords
                  };
                }
                return {
                  ...act,
                  coordenadas: act.coordenadas || resolveCoordinates(act.ubicacion || act.texto, destino)
                };
              })
            : []
        }));

        setItinerario(diasBackend);
        setConexionBackend(true);
        if (diasBackend.length > 0) {
          setDiaSeleccionado(diasBackend[0].id);
        }
      } else {
        throw new Error('Respuesta no satisfactoria del backend');
      }
    } catch (err) {
      // Fallback local resiliente
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

  // Seleccionar un día específico
  const handleSeleccionarDia = (diaId) => {
    setDiaSeleccionado((prev) => (prev === diaId ? null : diaId));
    setNuevaActividadTexto('');
    setNuevaActividadHora('');
    setNuevaActividadUbicacion('');
  };

  // Cargar actividad desde chip recomendado
  const handleSelectChipLugar = (lugar) => {
    setNuevaActividadTexto(lugar.name);
    setNuevaActividadUbicacion(lugar.name);
  };

  // Agregar actividad al día seleccionado con soporte de ubicación y coordenadas
  const handleAgregarActividad = (diaId, e) => {
    e?.preventDefault();

    if (!nuevaActividadTexto.trim()) return;

    const ubicacionFinal = (nuevaActividadUbicacion || nuevaActividadTexto).trim();
    const coordenadasResueltas = resolveCoordinates(ubicacionFinal, destino);

    setItinerario((prevDias) =>
      prevDias.map((dia) => {
        if (dia.id === diaId) {
          const nuevaAct = {
            id: `act-${Date.now()}-${Math.random().toString(36).substr(2, 4)}`,
            texto: nuevaActividadTexto.trim(),
            hora: nuevaActividadHora.trim(),
            ubicacion: ubicacionFinal,
            coordenadas: coordenadasResueltas
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
    setNuevaActividadUbicacion('');
  };

  // Cargar actividades de demostración con traslados para probar inmediatamente la HU-010
  const handleCargarDemoActividades = (diaId) => {
    const dest = findDestination(destino);
    let actividadesDemo = [];

    if (dest && dest.keys.includes('cartagena')) {
      actividadesDemo = [
        {
          id: `demo-${Date.now()}-1`,
          texto: 'Recorrido histórico por la Ciudad Amurallada',
          hora: '09:00 AM',
          ubicacion: 'Ciudad Amurallada, Cartagena',
          coordenadas: [10.4236, -75.5503]
        },
        {
          id: `demo-${Date.now()}-2`,
          texto: 'Almuerzo y artesanías en Getsemaní',
          hora: '12:30 PM',
          ubicacion: 'Barrio Getsemaní, Cartagena',
          coordenadas: [10.4204, -75.5445]
        },
        {
          id: `demo-${Date.now()}-3`,
          texto: 'Excursión a las Islas del Rosario (Traslado prolongado)',
          hora: '03:30 PM',
          ubicacion: 'Islas del Rosario, Cartagena',
          coordenadas: [10.1789, -75.7486]
        }
      ];
    } else {
      // Default demo en Bogotá con trayecto corto y trayecto largo (+120 min caminando para Criterio 2)
      actividadesDemo = [
        {
          id: `demo-${Date.now()}-1`,
          texto: 'Visita guiada al Museo del Oro',
          hora: '09:00 AM',
          ubicacion: 'Museo del Oro, Bogotá',
          coordenadas: [4.6019, -74.0720]
        },
        {
          id: `demo-${Date.now()}-2`,
          texto: 'Paseo histórico por la Plaza de Bolívar',
          hora: '11:00 AM',
          ubicacion: 'Plaza de Bolívar, Bogotá',
          coordenadas: [4.5981, -74.0758]
        },
        {
          id: `demo-${Date.now()}-3`,
          texto: 'Excursión a la Catedral de Sal de Zipaquirá',
          hora: '02:30 PM',
          ubicacion: 'Catedral de Sal de Zipaquirá',
          coordenadas: [5.0192, -74.0044]
        }
      ];
    }

    setItinerario((prevDias) =>
      prevDias.map((dia) => {
        if (dia.id === diaId) {
          return {
            ...dia,
            actividades: [...dia.actividades, ...actividadesDemo]
          };
        }
        return dia;
      })
    );
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
                <span className="eyebrow">Itinerario y Viabilidad del Viaje (HU-010)</span>
                <h1>
                  {destino
                    ? `Itinerario para ${destino}`
                    : 'Planificador de Itinerario'}
                </h1>
                <p>
                  Organiza tus actividades por día y visualiza los tiempos estimados de traslado entre ellas para evaluar la viabilidad de tu cronograma.
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
                💡 Haz clic en cualquier día para seleccionarlo, agregar actividades con ubicación y calcular tiempos de traslado automáticos.
              </div>
            </div>

            {/* Lista dividida por cada uno de los días */}
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

                      <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
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

                    {/* Cuerpo: Actividades y tarjetas de traslado entre consecutivas */}
                    <div className="dia-cuerpo" onClick={(e) => e.stopPropagation()}>
                      {tieneActividades ? (
                        <div className="actividades-lista">
                          {dia.actividades.map((act, actIdx) => {
                            const nextAct = dia.actividades[actIdx + 1];
                            const originCoords = act.coordenadas || resolveCoordinates(act.ubicacion || act.texto, destino);
                            const destCoords = nextAct
                              ? (nextAct.coordenadas || resolveCoordinates(nextAct.ubicacion || nextAct.texto, destino))
                              : null;

                            // Criterio 1: Dos actividades consecutivas para el mismo día con ubicación válida
                            const puedeCalcularTraslado = Boolean(originCoords && destCoords);

                            return (
                              <React.Fragment key={act.id}>
                                <div className="actividad-item">
                                  <div className="actividad-left">
                                    {act.hora && (
                                      <span className="actividad-hora">
                                        <Clock size={13} /> {act.hora}
                                      </span>
                                    )}
                                    <span className="actividad-texto">{act.texto}</span>
                                    {act.ubicacion && (
                                      <span className="actividad-ubicacion-pill" title={`Ubicación: ${act.ubicacion}`}>
                                        <MapPin size={12} /> {act.ubicacion}
                                      </span>
                                    )}
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

                                {/* Criterio 1, 2 y 3: Tarjeta de traslado entre actividades consecutivas */}
                                {puedeCalcularTraslado && (
                                  <TravelTimeCard
                                    originActivity={{ ...act, coordenadas: originCoords }}
                                    destinationActivity={{ ...nextAct, coordenadas: destCoords }}
                                  />
                                )}
                              </React.Fragment>
                            );
                          })}
                        </div>
                      ) : (
                        <div className="dia-vacio-aviso">
                          <span>
                            Este día aún no tiene actividades programadas.
                          </span>
                          <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', justifyContent: 'center', marginTop: '4px' }}>
                            <button
                              type="button"
                              className="btn-seleccionar-dia"
                              onClick={() => handleSeleccionarDia(dia.id)}
                            >
                              <Plus size={16} />
                              {esSeleccionado ? 'Día seleccionado' : 'Seleccionar día'}
                            </button>
                            <button
                              type="button"
                              className="btn-demo-itinerario"
                              onClick={() => handleCargarDemoActividades(dia.id)}
                              title="Cargar actividades con traslados cercanos y prolongados para probar viabilidad"
                            >
                              <Compass size={15} /> Cargar ejemplo con traslados (HU-010)
                            </button>
                          </div>
                        </div>
                      )}

                      {/* Formulario para agregar actividad */}
                      {esSeleccionado && (
                        <form
                          className="form-agregar-actividad"
                          onSubmit={(e) => handleAgregarActividad(dia.id, e)}
                        >
                          <div className="form-agregar-actividad-header">
                            <h4>
                              <Plus size={16} /> Agregar actividad al Día {index + 1}
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

                          {/* Chips de sugerencias para lugares conocidos del destino */}
                          {lugaresRecomendados.length > 0 && (
                            <div className="place-chips-section">
                              <span className="place-chips-label">
                                Lugares recomendados en {destino || 'el destino'} (con ubicación válida):
                              </span>
                              <div className="place-chips-container">
                                {lugaresRecomendados.map((lugar) => (
                                  <button
                                    key={lugar.id}
                                    type="button"
                                    className={`place-chip ${
                                      nuevaActividadTexto === lugar.name ? 'active' : ''
                                    }`}
                                    onClick={() => handleSelectChipLugar(lugar)}
                                  >
                                    <MapPin size={12} /> {lugar.name}
                                  </button>
                                ))}
                              </div>
                            </div>
                          )}

                          <div className="form-actividad-grid">
                            <input
                              type="text"
                              placeholder="Ej. Visita al museo, Almuerzo típico, Recorrido histórico..."
                              value={nuevaActividadTexto}
                              onChange={(e) => setNuevaActividadTexto(e.target.value)}
                              autoFocus
                              required
                            />
                            <input
                              type="text"
                              placeholder="Hora (ej. 09:00 AM o 14:30)"
                              value={nuevaActividadHora}
                              onChange={(e) => setNuevaActividadHora(e.target.value)}
                            />
                            <input
                              type="text"
                              placeholder="Ubicación / Lugar o coordenadas (ej. Monserrate, Calle 10...)"
                              value={nuevaActividadUbicacion}
                              onChange={(e) => setNuevaActividadUbicacion(e.target.value)}
                            />
                          </div>

                          <div className="form-actividad-acciones">
                            <button
                              type="button"
                              className="btn-demo-itinerario"
                              onClick={() => handleCargarDemoActividades(dia.id)}
                              style={{ marginRight: 'auto' }}
                            >
                              <Compass size={15} /> Cargar ejemplo con traslados
                            </button>
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
                        <div style={{ display: 'flex', gap: '8px', marginTop: '12px', flexWrap: 'wrap' }}>
                          <button
                            type="button"
                            className="btn-abrir-actividad"
                            style={{ flex: 1, marginTop: 0 }}
                            onClick={() => handleSeleccionarDia(dia.id)}
                          >
                            <Plus size={16} /> + Agregar otra actividad a este día
                          </button>
                        </div>
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
