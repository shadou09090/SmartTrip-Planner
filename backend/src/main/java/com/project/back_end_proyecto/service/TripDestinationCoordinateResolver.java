package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.Actividad;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.repository.AlojamientoRepository;
import com.project.back_end_proyecto.repository.ItinerarioActividadRepository;
import com.project.back_end_proyecto.repository.ItinerarioRepository;
import com.project.back_end_proyecto.service.publicplaces.CoordenadasPublicas;
import com.project.back_end_proyecto.service.publicplaces.DestinoCoordinateCache;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves a trip destination from providers, then from coordinates already
 * saved on that trip.
 */
@Service
@Profile("!local-no-db")
public class TripDestinationCoordinateResolver {
    private static final Logger log = LoggerFactory.getLogger(TripDestinationCoordinateResolver.class);
    private final FuenteLugaresPublicos places;
    private final ItinerarioRepository itineraries;
    private final AlojamientoRepository lodgings;
    private final ItinerarioActividadRepository assignments;
    private final DestinoCoordinateCache cache;

    public TripDestinationCoordinateResolver(FuenteLugaresPublicos places, ItinerarioRepository itineraries,
            AlojamientoRepository lodgings,
            ItinerarioActividadRepository assignments,
            DestinoCoordinateCache cache) {
        this.places = places;
        this.itineraries = itineraries;
        this.lodgings = lodgings;
        this.assignments = assignments;
        this.cache = cache;
    }

    public Optional<Resolution> resolve(Viaje trip) {
        RuntimeException providerFailure = null;
        try {
            Optional<CoordenadasPublicas> result = places.geocodificarDestino(trip.getDestino());
            if (result != null && result.isPresent() && valid(result.get())) {
                return Optional.of(new Resolution(result.get(), "proveedor público", false));
            }
        } catch (RuntimeException error) {
            providerFailure = error;
            log.info(
                    "trip_destination_coordinates provider=unavailable fallback=persisted_trip_coordinates tripId={} exceptionType={}",
                    trip.getId(), error.getClass().getSimpleName());
        }

        Optional<CoordenadasPublicas> persisted = persistedAnchor(trip);
        if (persisted.isPresent()) {
            cache.remember(trip.getDestino(), persisted.get(), "persisted trip coordinates");
            log.info("trip_destination_coordinates source=persisted_trip_coordinates cached=true tripId={}",
                    trip.getId());
            return Optional.of(
                    new Resolution(persisted.get(), "coordenadas guardadas en el viaje (origen no registrado)", true));
        }
        if (providerFailure != null)
            throw providerFailure;
        return Optional.empty();
    }

    private Optional<CoordenadasPublicas> persistedAnchor(Viaje trip) {
        var itinerary = itineraries.findByViaje_Id(trip.getId()).orElse(null);
        if (itinerary == null)
            return Optional.empty();
        var lodging = lodgings.findByItinerario_Id(itinerary.getId()).orElse(null);
        if (lodging != null) {
            CoordenadasPublicas coordinates = new CoordenadasPublicas(lodging.getLatitud(), lodging.getLongitud());
            if (valid(coordinates))
                return Optional.of(coordinates);
        }
        List<Actividad> saved = assignments.findDistinctActivitiesByTripId(trip.getId()).stream()
                .filter(activity -> valid(activity.getLatitud(), activity.getLongitud()))
                .sorted(Comparator.comparing(Actividad::getId)).toList();
        if (saved.isEmpty())
            return Optional.empty();
        // Median coordinate is robust against one distant saved day trip and uses only
        // existing trip records.
        List<BigDecimal> latitudes = saved.stream().map(Actividad::getLatitud).sorted().toList();
        List<BigDecimal> longitudes = saved.stream().map(Actividad::getLongitud).sorted().toList();
        int middle = saved.size() / 2;
        BigDecimal latitude = saved.size() % 2 == 1 ? latitudes.get(middle)
                : latitudes.get(middle - 1).add(latitudes.get(middle)).divide(BigDecimal.valueOf(2));
        BigDecimal longitude = saved.size() % 2 == 1 ? longitudes.get(middle)
                : longitudes.get(middle - 1).add(longitudes.get(middle)).divide(BigDecimal.valueOf(2));
        CoordenadasPublicas coordinates = new CoordenadasPublicas(latitude, longitude);
        return valid(coordinates) ? Optional.of(coordinates) : Optional.empty();
    }

    private static boolean valid(CoordenadasPublicas point) {
        return point != null && valid(point.latitud(), point.longitud());
    }

    private static boolean valid(BigDecimal latitude, BigDecimal longitude) {
        return latitude != null && longitude != null
                && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0 && latitude.compareTo(BigDecimal.valueOf(90)) <= 0
                && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
                && longitude.compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    public record Resolution(CoordenadasPublicas coordinates, String source, boolean reusedFromTrip) {
    }
}
