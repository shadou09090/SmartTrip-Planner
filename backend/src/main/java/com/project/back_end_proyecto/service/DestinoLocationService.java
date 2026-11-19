package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.DestinoUbicacionResponse;
import com.project.back_end_proyecto.dto.DestinoCandidatoResponse;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.List;

@Service
@Profile("oracle")
public class DestinoLocationService {
    private final TripPermissionService permissions;
    private final FuenteLugaresPublicos placesSource;
    private final TripDestinationCoordinateResolver tripCoordinates;

    @Autowired
    public DestinoLocationService(TripPermissionService permissions, FuenteLugaresPublicos placesSource,
                                  TripDestinationCoordinateResolver tripCoordinates) {
        this.permissions = permissions;
        this.placesSource = placesSource;
        this.tripCoordinates = tripCoordinates;
    }

    public DestinoLocationService(TripPermissionService permissions, FuenteLugaresPublicos placesSource) {
        this(permissions, placesSource, null);
    }

    public Optional<DestinoUbicacionResponse> ubicar(Long userId, Long tripId) {
        var trip = permissions.requireRead(userId, tripId);
        String destination = trip.getDestino();
        if (tripCoordinates == null) {
            return placesSource.geocodificarDestino(destination)
                    .map(point -> new DestinoUbicacionResponse(destination, point.latitud(), point.longitud()));
        }
        return tripCoordinates.resolve(trip).map(resolved -> new DestinoUbicacionResponse(destination,
                resolved.coordinates().latitud(), resolved.coordinates().longitud(),
                resolved.source(), resolved.reusedFromTrip()));
    }

    public List<DestinoCandidatoResponse> sugerir(String consulta) {
        if (consulta == null || consulta.trim().length() < 2 || consulta.length() > 160) return List.of();
        return placesSource.buscarDestinos(consulta).stream()
                .map(item -> new DestinoCandidatoResponse(item.id(), item.nombreCompleto(), item.ciudad(),
                        item.region(), item.pais(), item.tipo(), item.latitud(), item.longitud(), item.proveedor()))
                .toList();
    }
}
