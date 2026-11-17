package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.TrasladoRequest;
import com.project.back_end_proyecto.dto.TrasladoResponse;
import com.project.back_end_proyecto.entity.DiaItinerario;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.repository.ViajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ItinerarioService {

    @Autowired
    private ViajeRepository viajeRepository;

    // Caché en memoria para responder de inmediato en consultas repetidas (Criterio 3)
    private final ConcurrentHashMap<String, TrasladoResponse> cacheTraslados = new ConcurrentHashMap<>();

    public Viaje crearItinerarioVacio(LocalDate fechaInicio, LocalDate fechaFin) {
        Viaje nuevoViaje = new Viaje(fechaInicio, fechaFin);
        List<DiaItinerario> diasEstructurados = new ArrayList<>();

        long cantidadDias = ChronoUnit.DAYS.between(fechaInicio, fechaFin);

        for (int i = 0; i <= cantidadDias; i++) {
            LocalDate fechaDelDia = fechaInicio.plusDays(i);
            diasEstructurados.add(new DiaItinerario(fechaDelDia));
        }

        nuevoViaje.setDias(diasEstructurados);
        return viajeRepository.save(nuevoViaje);
    }

    public DiaItinerario agregarActividadADia(Long viajeId, Long diaId, String actividad) {
        Viaje viaje = viajeRepository.findById(viajeId).orElseThrow(() -> new RuntimeException("Viaje no encontrado"));
        for (DiaItinerario dia : viaje.getDias()) {
            if (dia.getId().equals(diaId)) {
                dia.agregarActividad(actividad);
                viajeRepository.save(viaje);
                return dia;
            }
        }
        throw new RuntimeException("Día no encontrado en el viaje");
    }

    /**
     * HU-010: Calcula el tiempo estimado de traslado entre dos puntos
     * para tres medios: a pie, transporte público y automóvil.
     * Incluye advertencia visual si supera los 120 minutos (Criterio 2)
     * y utiliza caché para responder sin demora perceptible (Criterio 3).
     */
    public TrasladoResponse calcularTraslado(TrasladoRequest request) {
        if (request == null || request.getOrigenLat() == null || request.getOrigenLng() == null
                || request.getDestinoLat() == null || request.getDestinoLng() == null) {
            throw new IllegalArgumentException("Coordenadas de origen y destino son requeridas");
        }

        String cacheKey = String.format("%.4f,%.4f->%.4f,%.4f",
                request.getOrigenLat(), request.getOrigenLng(),
                request.getDestinoLat(), request.getDestinoLng());

        // Criterio 3: Si ya se consultó antes, responder desde caché
        if (cacheTraslados.containsKey(cacheKey)) {
            TrasladoResponse cached = cacheTraslados.get(cacheKey);
            TrasladoResponse copy = copiarRespuesta(cached);
            copy.setDesdeCache(true);
            return copy;
        }

        double distancia = calcularDistanciaHaversine(
                request.getOrigenLat(), request.getOrigenLng(),
                request.getDestinoLat(), request.getDestinoLng());

        // Factor de desvío de calles urbanas (1.3x)
        double distanciaRutaKm = Math.round(distancia * 1.3 * 100.0) / 100.0;

        // 1. A pie (~4.6 km/h)
        int minPie = Math.max(1, (int) Math.round((distanciaRutaKm / 4.6) * 60));

        // 2. Transporte público (~18 km/h + 10 min de espera/transbordos)
        int minTransporte = Math.max(3, (int) Math.round(10 + (distanciaRutaKm / 18.0) * 60));

        // 3. Automóvil (~32 km/h + 4 min de tráfico urbano)
        int minAuto = Math.max(2, (int) Math.round(4 + (distanciaRutaKm / 32.0) * 60));

        // Criterio 2: Advertencia si supera los 120 minutos
        boolean pieSupera = minPie > 120;
        boolean transporteSupera = minTransporte > 120;
        boolean autoSupera = minAuto > 120;
        boolean cualquierSupera = pieSupera || transporteSupera || autoSupera;

        TrasladoResponse response = new TrasladoResponse();
        response.setOrigen(request.getOrigen() != null ? request.getOrigen() : "Origen");
        response.setDestino(request.getDestino() != null ? request.getDestino() : "Destino");
        response.setDistanciaKm(distanciaRutaKm);

        response.setTiempoPieMinutos(minPie);
        response.setTiempoPieFormato(formatearTiempo(minPie));
        response.setPieSupera120(pieSupera);

        response.setTiempoTransporteMinutos(minTransporte);
        response.setTiempoTransporteFormato(formatearTiempo(minTransporte));
        response.setTransporteSupera120(transporteSupera);

        response.setTiempoAutoMinutos(minAuto);
        response.setTiempoAutoFormato(formatearTiempo(minAuto));
        response.setAutoSupera120(autoSupera);

        response.setAdvertenciaVisualSupera120(cualquierSupera);
        response.setDesdeCache(false);

        cacheTraslados.put(cacheKey, response);
        return response;
    }

    private double calcularDistanciaHaversine(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // Radio de la tierra en km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private String formatearTiempo(int minutos) {
        if (minutos < 60) {
            return minutos + " min";
        }
        int horas = minutos / 60;
        int rem = minutos % 60;
        if (rem == 0) {
            return horas + " h";
        }
        return horas + " h " + rem + " min";
    }

    private TrasladoResponse copiarRespuesta(TrasladoResponse original) {
        TrasladoResponse copy = new TrasladoResponse();
        copy.setOrigen(original.getOrigen());
        copy.setDestino(original.getDestino());
        copy.setDistanciaKm(original.getDistanciaKm());
        copy.setTiempoPieMinutos(original.getTiempoPieMinutos());
        copy.setTiempoPieFormato(original.getTiempoPieFormato());
        copy.setPieSupera120(original.isPieSupera120());
        copy.setTiempoTransporteMinutos(original.getTiempoTransporteMinutos());
        copy.setTiempoTransporteFormato(original.getTiempoTransporteFormato());
        copy.setTransporteSupera120(original.isTransporteSupera120());
        copy.setTiempoAutoMinutos(original.getTiempoAutoMinutos());
        copy.setTiempoAutoFormato(original.getTiempoAutoFormato());
        copy.setAutoSupera120(original.isAutoSupera120());
        copy.setAdvertenciaVisualSupera120(original.isAdvertenciaVisualSupera120());
        copy.setDesdeCache(original.isDesdeCache());
        return copy;
    }
}
