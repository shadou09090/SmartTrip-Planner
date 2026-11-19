package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.AlojamientoSugerenciaResponse;
import com.project.back_end_proyecto.dto.DestinoUbicacionResponse;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import com.project.back_end_proyecto.service.publicplaces.LugarPublico;
import com.project.back_end_proyecto.service.publicplaces.WikimediaCommonsPhotoService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

@Service
@Profile("oracle")
public class AlojamientoRecommendationService {
    private final TripPermissionService permissions;
    private final FuenteLugaresPublicos placesSource;
    private final WikimediaCommonsPhotoService photos;

    public AlojamientoRecommendationService(TripPermissionService permissions, FuenteLugaresPublicos placesSource,
                                            WikimediaCommonsPhotoService photos) {
        this.permissions = permissions;
        this.placesSource = placesSource;
        this.photos = photos;
    }

    public List<AlojamientoSugerenciaResponse> buscar(Long userId, Long tripId) {
        var trip = permissions.requireRead(userId, tripId);
        String destination = trip.getDestino();
        final var startDate = trip.getFechaInicio();
        final var endDate = trip.getFechaFin();
        List<LugarPublico> places = placesSource.buscarAlojamientos(destination).stream().limit(20).toList();
        var verifiedPhotos = photos.verifyPlaceImages(places);
        if (verifiedPhotos == null) verifiedPhotos = java.util.Map.of();
        var verifiedPhotoMap = verifiedPhotos;
        return places.stream()
                .map(place -> {
                    var photo = verifiedPhotoMap.get(place.id());
                    return new AlojamientoSugerenciaResponse(place.id(), place.nombre(), place.ubicacion(),
                            place.categoria(), place.latitud(), place.longitud(), place.urlFuente(),
                            photo == null ? null : photo.url(), photo == null ? null : photo.attribution(),
                            photo == null ? null : photo.license(), photo == null ? null : photo.licenseUrl(),
                            photo == null ? null : photo.sourceUrl(), place.etiquetas().get("website"),
                            priceSearchUrl(place.nombre(), place.ubicacion(), destination, startDate, endDate));
                })
                .toList();
    }

    private static String priceSearchUrl(String name, String address, String destination,
                                         java.time.LocalDate startDate, java.time.LocalDate endDate) {
        String search = String.join(", ", java.util.stream.Stream.of(name, address, destination)
                .filter(value -> value != null && !value.isBlank()).toList());
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString("https://www.booking.com/searchresults.html")
                .queryParam("ss", search);
        if (startDate != null && endDate != null && endDate.isAfter(startDate)) {
            builder.queryParam("checkin", startDate).queryParam("checkout", endDate);
        }
        return builder.build().encode().toUriString();
    }

    public Optional<DestinoUbicacionResponse> ubicarAlojamiento(Long userId, Long tripId, String query) {
        permissions.requireEdit(userId, tripId);
        if (query == null || query.isBlank() || query.length() > 300) {
            throw new IllegalArgumentException("Indica una dirección de alojamiento válida de hasta 300 caracteres.");
        }
        String normalizedQuery = query.trim();
        return placesSource.geocodificarDestino(normalizedQuery)
                .map(point -> new DestinoUbicacionResponse(normalizedQuery, point.latitud(), point.longitud()));
    }
}
