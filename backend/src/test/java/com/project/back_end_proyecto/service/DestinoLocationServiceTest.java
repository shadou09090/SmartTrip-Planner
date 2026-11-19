package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.service.publicplaces.CoordenadasPublicas;
import com.project.back_end_proyecto.service.publicplaces.DestinoCandidato;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DestinoLocationServiceTest {
    @Test
    void getsCoordinatesOnlyAfterTripReadPermissionAndMapsThePersistedDestination() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        FuenteLugaresPublicos source = mock(FuenteLugaresPublicos.class);
        Viaje trip = mock(Viaje.class);
        when(trip.getDestino()).thenReturn("Medellín");
        when(permissions.requireRead(2L, 7L)).thenReturn(trip);
        when(source.geocodificarDestino("Medellín"))
                .thenReturn(Optional.of(new CoordenadasPublicas(new BigDecimal("6.24"), new BigDecimal("-75.58"))));
        var service = new DestinoLocationService(permissions, source);

        var result = service.ubicar(2L, 7L).orElseThrow();

        assertEquals("Medellín", result.nombre());
        assertEquals(new BigDecimal("6.24"), result.latitud());
        assertEquals(new BigDecimal("-75.58"), result.longitud());
        verify(permissions).requireRead(2L, 7L);
    }

    @Test
    void representsNoGeocodingMatchAsEmpty() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        FuenteLugaresPublicos source = mock(FuenteLugaresPublicos.class);
        Viaje trip = mock(Viaje.class);
        when(trip.getDestino()).thenReturn("Lugar inexistente");
        when(permissions.requireRead(2L, 7L)).thenReturn(trip);
        when(source.geocodificarDestino("Lugar inexistente")).thenReturn(Optional.empty());

        assertTrue(new DestinoLocationService(permissions, source).ubicar(2L, 7L).isEmpty());
    }

    @Test
    void returnsInternationalGeocoderCandidatesWithAdministrativeContext() {
        FuenteLugaresPublicos source = mock(FuenteLugaresPublicos.class);
        when(source.buscarDestinos("Kyoto")).thenReturn(List.of(new DestinoCandidato("nominatim:1",
                "Kyoto, Kyoto Prefecture, Japan", "Kyoto", "Kyoto Prefecture", "Japan", "city",
                new BigDecimal("35.0116"), new BigDecimal("135.7681"), "Nominatim / OpenStreetMap")));

        var candidate = new DestinoLocationService(mock(TripPermissionService.class), source)
                .sugerir("Kyoto").get(0);

        assertEquals("Japan", candidate.pais());
        assertEquals("Kyoto Prefecture", candidate.region());
        assertEquals(new BigDecimal("35.0116"), candidate.latitud());
    }

    @Test
    void doesNotQueryGeocoderForTooShortOrOversizedDestinationStrings() {
        FuenteLugaresPublicos source = mock(FuenteLugaresPublicos.class);
        var service = new DestinoLocationService(mock(TripPermissionService.class), source);
        assertTrue(service.sugerir("x").isEmpty());
        assertTrue(service.sugerir("x".repeat(161)).isEmpty());
        verifyNoInteractions(source);
    }
}
