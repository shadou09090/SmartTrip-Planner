package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.Actividad;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.repository.ActividadInteresRepository;
import com.project.back_end_proyecto.repository.ActividadRepository;
import com.project.back_end_proyecto.repository.ViajeInteresRepository;
import com.project.back_end_proyecto.service.publicplaces.CoordenadasPublicas;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ActivityRecommendationCatalogServiceTest {
    @Test
    void activitiesEndpointExcludesDateCompatibleCatalogRowsOutsideResolvedDestination() {
        var permissions = mock(TripPermissionService.class);
        var activities = mock(ActividadRepository.class);
        var source = mock(FuenteLugaresPublicos.class);
        var start = LocalDate.of(2026, 11, 20);
        var end = LocalDate.of(2026, 11, 24);
        var trip = new Viaje(new User("Traveler", "traveler@example.invalid", "hash"), "Bogotá", "Paris, France",
                start, end, new BigDecimal("9000000"));
        when(permissions.requireRead(1L, 9L)).thenReturn(trip);
        var paris = new Actividad("Musée local", null, "Paris", new BigDecimal("48.8606"), new BigDecimal("2.3376"),
                null, null, null);
        var puertoColombia = new Actividad("Puerto Colombia", null, "Colombia", new BigDecimal("10.9878"),
                new BigDecimal("-74.9547"), null, null, null);
        var unverifiable = new Actividad("Sin ubicación", null, null, null, null, null, null, null);
        when(activities.findAllDisponiblesEnRango(start, end)).thenReturn(List.of(paris, puertoColombia, unverifiable));
        when(source.geocodificarDestino("Paris, France"))
                .thenReturn(Optional.of(new CoordenadasPublicas(new BigDecimal("48.8566"), new BigDecimal("2.3522"))));

        var service = new ActivityRecommendationCatalogService(permissions, mock(ViajeInteresRepository.class),
                activities, mock(ActividadInteresRepository.class), source);

        assertEquals(List.of(paris), service.listAvailableForTrip(1L, 9L));
        verify(permissions).requireRead(1L, 9L);
    }

    @Test
    void activitiesEndpointReportsThatLocationCouldNotBeVerifiedWhenGeocoderIsUnavailable() {
        var permissions = mock(TripPermissionService.class);
        var activities = mock(ActividadRepository.class);
        var source = mock(FuenteLugaresPublicos.class);
        var start = LocalDate.of(2026, 11, 20);
        var end = LocalDate.of(2026, 11, 24);
        var trip = new Viaje(new User("Traveler", "traveler@example.invalid", "hash"), "Bogotá", "Paris, France",
                start, end, null);
        when(permissions.requireRead(1L, 9L)).thenReturn(trip);
        when(source.geocodificarDestino("Paris, France")).thenReturn(Optional.empty());
        when(activities.findAllDisponiblesEnRango(start, end)).thenReturn(List.of(
                new Actividad("Sin coordenadas", null, null, null, null, null, null, null)));
        var service = new ActivityRecommendationCatalogService(permissions, mock(ViajeInteresRepository.class),
                activities, mock(ActividadInteresRepository.class), source);

        var error = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> service.listAvailableForTrip(1L, 9L));
        assertEquals("No se encontró la ubicación del destino; no se ofrecerán actividades internas sin validación geográfica.", error.getMessage());
    }
}
