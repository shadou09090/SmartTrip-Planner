package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.DiaItinerario;
import com.project.back_end_proyecto.entity.Itinerario;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.repository.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("oracle")
public class ViajeDeletionService {
    private final TripPermissionService permissions;
    private final ViajeRepository viajes;
    private final ViajeInteresRepository tripInterests;
    private final InvitacionRepository invitations;
    private final ColaboradorViajeRepository collaborators;
    private final HistorialPrecioVueloRepository priceHistory;
    private final VueloRepository flights;
    private final ItinerarioRepository itineraries;
    private final AlojamientoRepository lodging;
    private final DiaItinerarioRepository days;
    private final ItinerarioActividadRepository dayActivities;

    public ViajeDeletionService(TripPermissionService permissions, ViajeRepository viajes,
                                ViajeInteresRepository tripInterests, InvitacionRepository invitations,
                                ColaboradorViajeRepository collaborators, HistorialPrecioVueloRepository priceHistory,
                                VueloRepository flights, ItinerarioRepository itineraries,
                                AlojamientoRepository lodging, DiaItinerarioRepository days,
                                ItinerarioActividadRepository dayActivities) {
        this.permissions = permissions; this.viajes = viajes; this.tripInterests = tripInterests;
        this.invitations = invitations; this.collaborators = collaborators; this.priceHistory = priceHistory;
        this.flights = flights; this.itineraries = itineraries; this.lodging = lodging;
        this.days = days; this.dayActivities = dayActivities;
    }

    @Transactional
    public void deleteOwnedTrip(Long ownerId, Long tripId) {
        Viaje trip = permissions.requireOwner(ownerId, tripId);
        tripInterests.deleteAllByViaje_Id(tripId);
        invitations.deleteAllByViaje_Id(tripId);
        collaborators.deleteAllByViaje_Id(tripId);
        priceHistory.deleteAllByVuelo_Viaje_Id(tripId);
        flights.deleteAllByViaje_Id(tripId);
        Itinerario itinerary = itineraries.findByViaje_Id(tripId).orElse(null);
        if (itinerary != null) {
            lodging.deleteByItinerario_Id(itinerary.getId());
            for (DiaItinerario day : days.findAllByItinerario_Id(itinerary.getId())) {
                dayActivities.deleteAllByDia_Id(day.getId());
            }
            days.deleteAllByItinerario_Id(itinerary.getId());
            itineraries.delete(itinerary);
        }
        viajes.delete(trip);
    }
}
