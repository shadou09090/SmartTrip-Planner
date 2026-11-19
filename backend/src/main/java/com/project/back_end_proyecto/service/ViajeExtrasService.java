package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.*;
import com.project.back_end_proyecto.entity.Alojamiento;
import com.project.back_end_proyecto.entity.HistorialPrecioVuelo;
import com.project.back_end_proyecto.entity.Itinerario;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.entity.Vuelo;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Profile("oracle")
public class ViajeExtrasService {
    private final ViajeRepository viajeRepository;
    private final ItinerarioRepository itinerarioRepository;
    private final AlojamientoRepository alojamientoRepository;
    private final VueloRepository vueloRepository;
    private final HistorialPrecioVueloRepository historialRepository;
    private final TripPermissionService tripPermissionService;

    public ViajeExtrasService(ViajeRepository viajeRepository, ItinerarioRepository itinerarioRepository,
                              AlojamientoRepository alojamientoRepository, VueloRepository vueloRepository,
                              HistorialPrecioVueloRepository historialRepository, TripPermissionService tripPermissionService) {
        this.viajeRepository = viajeRepository;
        this.itinerarioRepository = itinerarioRepository;
        this.alojamientoRepository = alojamientoRepository;
        this.vueloRepository = vueloRepository;
        this.historialRepository = historialRepository;
        this.tripPermissionService = tripPermissionService;
    }

    @Transactional(readOnly = true)
    public Optional<AlojamientoResponse> alojamiento(Long userId, Long tripId) {
        tripPermissionService.requireRead(userId, tripId);
        return itinerarioRepository.findByViaje_Id(tripId)
                .flatMap(itinerary -> alojamientoRepository.findByItinerario_Id(itinerary.getId()))
                .map(this::toResponse);
    }

    @Transactional
    public AlojamientoResponse guardarAlojamiento(Long userId, Long tripId, AlojamientoRequest request) {
        Viaje trip = tripPermissionService.requireEdit(userId, tripId);
        // An accommodation belongs to the itinerary; create the single permitted itinerary row if it does not exist yet.
        Itinerario itinerary = itinerarioRepository.findByViaje_Id(tripId)
                .orElseGet(() -> itinerarioRepository.save(new Itinerario(trip)));
        Alojamiento lodging = alojamientoRepository.findByItinerario_Id(itinerary.getId()).orElse(null);
        if (lodging == null) {
            lodging = alojamientoRepository.save(new Alojamiento(itinerary, request.nombre().trim(), request.direccion(),
                    request.latitud(), request.longitud()));
        } else {
            lodging.actualizar(request.nombre().trim(), request.direccion(), request.latitud(), request.longitud());
        }
        return toResponse(lodging);
    }

    @Transactional(readOnly = true)
    public List<VueloResponse> vuelos(Long userId, Long tripId) {
        tripPermissionService.requireRead(userId, tripId);
        return vueloRepository.findAllByViaje_IdOrderByFechaSalida(tripId).stream().map(this::toResponse).toList();
    }

    @Transactional
    public VueloResponse crearVuelo(Long userId, Long tripId, VueloRequest request) {
        Viaje trip = tripPermissionService.requireEdit(userId, tripId);
        Vuelo flight = vueloRepository.save(new Vuelo(trip, request.origen().trim(), request.destino().trim(),
                request.fechaSalida(), request.fechaLlegada(), request.aerolinea(), request.numeroVuelo(), request.precio()));
        registrarPrecioSiExiste(flight, request.precio());
        return toResponse(flight);
    }

    @Transactional
    public VueloResponse actualizarVuelo(Long userId, Long tripId, Long flightId, VueloRequest request) {
        tripPermissionService.requireEdit(userId, tripId);
        Vuelo flight = vueloRepository.findByIdAndViaje_Id(flightId, tripId).orElseThrow(ResourceNotFoundException::new);
        boolean priceChanged = request.precio() != null && (flight.getPrecio() == null
                || request.precio().compareTo(flight.getPrecio()) != 0);
        flight.actualizar(request.origen().trim(), request.destino().trim(), request.fechaSalida(), request.fechaLlegada(),
                request.aerolinea(), request.numeroVuelo(), request.precio());
        Vuelo saved = vueloRepository.save(flight);
        if (priceChanged) registrarPrecioSiExiste(saved, request.precio());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<HistorialPrecioResponse> historial(Long userId, Long tripId, Long flightId) {
        tripPermissionService.requireRead(userId, tripId);
        vueloRepository.findByIdAndViaje_Id(flightId, tripId).orElseThrow(ResourceNotFoundException::new);
        return historialRepository.findAllByVuelo_IdOrderByFechaRegistro(flightId).stream()
                .map(row -> new HistorialPrecioResponse(row.getId(), row.getPrecio(), row.getFechaRegistro())).toList();
    }

    private void registrarPrecioSiExiste(Vuelo flight, java.math.BigDecimal price) {
        if (price != null) historialRepository.save(new HistorialPrecioVuelo(flight, price));
    }

    private AlojamientoResponse toResponse(Alojamiento lodging) {
        return new AlojamientoResponse(lodging.getId(), lodging.getNombre(), lodging.getDireccion(), lodging.getLatitud(), lodging.getLongitud());
    }

    private VueloResponse toResponse(Vuelo flight) {
        return new VueloResponse(flight.getId(), flight.getOrigen(), flight.getDestino(), flight.getFechaSalida(),
                flight.getFechaLlegada(), flight.getAerolinea(), flight.getNumeroVuelo(), flight.getPrecio());
    }
}
