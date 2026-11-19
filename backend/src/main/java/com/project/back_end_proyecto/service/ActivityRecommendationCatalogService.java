package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.ActividadRecomendacionResponse;
import com.project.back_end_proyecto.dto.ActividadResponse;
import com.project.back_end_proyecto.entity.Actividad;
import com.project.back_end_proyecto.repository.ActividadInteresRepository;
import com.project.back_end_proyecto.repository.ActividadRepository;
import com.project.back_end_proyecto.repository.ViajeInteresRepository;
import com.project.back_end_proyecto.service.publicplaces.CoordenadasPublicas;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Profile("oracle")
public class ActivityRecommendationCatalogService {
    private static final Logger log = LoggerFactory.getLogger(ActivityRecommendationCatalogService.class);
    private static final double MAX_LOCAL_ACTIVITY_DISTANCE_KM = 50d;
    private final TripPermissionService permissions;
    private final ViajeInteresRepository tripInterests;
    private final ActividadRepository activities;
    private final ActividadInteresRepository activityInterests;
    private final FuenteLugaresPublicos geocoder;
    private final TripDestinationCoordinateResolver tripCoordinates;

    @Autowired
    public ActivityRecommendationCatalogService(TripPermissionService permissions,
                                                ViajeInteresRepository tripInterests,
                                                ActividadRepository activities,
                                                ActividadInteresRepository activityInterests,
                                                FuenteLugaresPublicos geocoder,
                                                TripDestinationCoordinateResolver tripCoordinates) {
        this.permissions = permissions;
        this.tripInterests = tripInterests;
        this.activities = activities;
        this.activityInterests = activityInterests;
        this.geocoder = geocoder;
        this.tripCoordinates = tripCoordinates;
    }

    /** Compatibility constructor for tests that do not exercise geographic validation. */
    public ActivityRecommendationCatalogService(TripPermissionService permissions,
                                                ViajeInteresRepository tripInterests,
                                                ActividadRepository activities,
                                                ActividadInteresRepository activityInterests) {
        this(permissions, tripInterests, activities, activityInterests, null);
    }

    public ActivityRecommendationCatalogService(TripPermissionService permissions,
                                                ViajeInteresRepository tripInterests,
                                                ActividadRepository activities,
                                                ActividadInteresRepository activityInterests,
                                                FuenteLugaresPublicos geocoder) {
        this(permissions, tripInterests, activities, activityInterests, geocoder, null);
    }

    @Transactional(readOnly = true)
    public Snapshot load(Long userId, Long tripId) {
        var trip = permissions.requireRead(userId, tripId);
        CoordenadasPublicas destination = resolveDestinationForSnapshot(trip, tripId);
        List<TripInterest> selectedInterests = tripInterests.findAllByViaje_Id(tripId).stream()
                .map(item -> new TripInterest(item.getInteres().getId(), item.getInteres().getNombre())).toList();
        Set<Long> selectedIds = selectedInterests.stream().map(TripInterest::id).collect(Collectors.toSet());
        if (selectedIds.isEmpty()) {
            return new Snapshot(trip.getDestino(), trip.getFechaInicio(), trip.getFechaFin(), List.of(), true, List.of());
        }
        List<Actividad> dateCompatible = activities.findAllDisponiblesEnRango(trip.getFechaInicio(), trip.getFechaFin()).stream()
                .filter(activity -> DisponibilidadActividad.cruzaRango(activity, trip.getFechaInicio(), trip.getFechaFin()))
                .toList();
        List<Actividad> available = dateCompatible.stream()
                .filter(activity -> geocoder == null || isNearDestination(activity, destination))
                .toList();
        int unverifiedLocationCount = geocoder == null ? 0 : dateCompatible.size() - available.size();
        if (available.isEmpty()) {
            return new Snapshot(trip.getDestino(), trip.getFechaInicio(), trip.getFechaFin(),
                    selectedInterests.stream().map(TripInterest::name).toList(), false, List.of(), unverifiedLocationCount);
        }
        List<Long> ids = available.stream().map(Actividad::getId).toList();
        Map<Long, List<String>> matches = activityInterests.findAllByActividad_IdIn(ids).stream()
                .filter(item -> selectedIds.contains(item.getInteres().getId()))
                .collect(Collectors.groupingBy(item -> item.getActividad().getId(),
                        Collectors.mapping(item -> item.getInteres().getNombre(), Collectors.toList())));
        List<ActividadRecomendacionResponse.ActividadRecomendada> recommended = available.stream()
                .filter(activity -> matches.containsKey(activity.getId()))
                .map(activity -> new ActividadRecomendacionResponse.ActividadRecomendada(
                        new ActividadResponse(activity.getId(), activity.getNombre(), activity.getDescripcion(),
                                activity.getUbicacion(), activity.getLatitud(), activity.getLongitud(), activity.getCosto(),
                                activity.getFechaDisponibleInicio(), activity.getFechaDisponibleFin(), null,
                                activityInterests.findAllByActividad_Id(activity.getId()).stream()
                                        .map(item -> item.getInteres().getNombre()).toList()), matches.get(activity.getId())))
                .toList();
        return new Snapshot(trip.getDestino(), trip.getFechaInicio(), trip.getFechaFin(),
                selectedInterests.stream().map(TripInterest::name).toList(), false, recommended, unverifiedLocationCount);
    }

    /** Activities endpoint consumed by React: verify destination as well as date before offering a catalog row. */
    @Transactional(readOnly = true)
    public List<Actividad> listAvailableForTrip(Long userId, Long tripId) {
        var trip = permissions.requireRead(userId, tripId);
        List<Actividad> dateCompatible = activities.findAllDisponiblesEnRango(trip.getFechaInicio(), trip.getFechaFin()).stream()
                .filter(activity -> DisponibilidadActividad.cruzaRango(activity, trip.getFechaInicio(), trip.getFechaFin()))
                .toList();
        CoordenadasPublicas destination = resolveDestination(trip, tripId);
        if (destination == null) {
            throw new IllegalStateException("No se encontró la ubicación del destino; no se ofrecerán actividades internas sin validación geográfica.");
        }
        return dateCompatible.stream().filter(activity -> isNearDestination(activity, destination)).toList();
    }

    private CoordenadasPublicas resolveDestination(com.project.back_end_proyecto.entity.Viaje trip, Long tripId) {
        if (tripCoordinates != null) {
            try { return tripCoordinates.resolve(trip).map(TripDestinationCoordinateResolver.Resolution::coordinates).orElse(null); }
            catch (RuntimeException failure) {
                throw new IllegalStateException("El proveedor de ubicación está temporalmente no disponible y el viaje no tiene coordenadas persistidas verificables.", failure);
            }
        }
        if (geocoder == null) return null;
        try { return geocoder.geocodificarDestino(trip.getDestino()).orElse(null); }
        catch (RuntimeException unavailable) {
            throw new IllegalStateException("El proveedor de ubicación está temporalmente no disponible; no se ofrecerán actividades internas sin validación geográfica.", unavailable);
        }
    }

    private CoordenadasPublicas resolveDestinationForSnapshot(com.project.back_end_proyecto.entity.Viaje trip,
                                                               Long tripId) {
        try { return resolveDestination(trip, tripId); }
        catch (RuntimeException unavailable) {
            log.info("catalog_destination_validation tripId={} status=temporarily_unavailable exceptionType={}",
                    tripId, unavailable.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean isNearDestination(Actividad activity, CoordenadasPublicas destination) {
        if (destination == null || !validPoint(destination.latitud(), destination.longitud())
                || !validPoint(activity.getLatitud(), activity.getLongitud())) return false;
        double lat1 = Math.toRadians(destination.latitud().doubleValue());
        double lat2 = Math.toRadians(activity.getLatitud().doubleValue());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(activity.getLongitud().doubleValue() - destination.longitud().doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double distanceKm = 6_371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return distanceKm <= MAX_LOCAL_ACTIVITY_DISTANCE_KM;
    }

    private static boolean validPoint(java.math.BigDecimal latitude, java.math.BigDecimal longitude) {
        return latitude != null && longitude != null
                && latitude.compareTo(java.math.BigDecimal.valueOf(-90)) >= 0
                && latitude.compareTo(java.math.BigDecimal.valueOf(90)) <= 0
                && longitude.compareTo(java.math.BigDecimal.valueOf(-180)) >= 0
                && longitude.compareTo(java.math.BigDecimal.valueOf(180)) <= 0;
    }

    public record TripInterest(Long id, String name) { }
    public record Snapshot(String destination, LocalDate startDate, LocalDate endDate,
                           List<String> interests, boolean requiresInterests,
                           List<ActividadRecomendacionResponse.ActividadRecomendada> catalogRecommendations,
                           int activitiesWithoutVerifiedDestination) {
        public Snapshot(String destination, LocalDate startDate, LocalDate endDate, List<String> interests,
                        boolean requiresInterests,
                        List<ActividadRecomendacionResponse.ActividadRecomendada> catalogRecommendations) {
            this(destination, startDate, endDate, interests, requiresInterests, catalogRecommendations, 0);
        }
    }
}
