package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.service.publicplaces.CoordenadasPublicas;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import com.project.back_end_proyecto.service.publicplaces.LugarPublico;
import com.project.back_end_proyecto.service.publicplaces.WikimediaCommonsPhotoService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlojamientoRecommendationServiceTest {
    @Test
    void requiresTripReadPermissionAndMapsOnlySourceFields() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        FuenteLugaresPublicos source = mock(FuenteLugaresPublicos.class);
        WikimediaCommonsPhotoService photos = mock(WikimediaCommonsPhotoService.class);
        Viaje trip = mock(Viaje.class);
        when(trip.getDestino()).thenReturn("Bogotá");
        when(trip.getFechaInicio()).thenReturn(LocalDate.of(2026, 12, 1));
        when(trip.getFechaFin()).thenReturn(LocalDate.of(2026, 12, 5));
        when(permissions.requireRead(4L, 8L)).thenReturn(trip);
        var place = new LugarPublico("osm:node:1", "Hotel de prueba", null, "hotel", null,
                new BigDecimal("4.6"), new BigDecimal("-74.1"), "https://www.openstreetmap.org/node/1", Map.of());
        when(source.buscarAlojamientos("Bogotá")).thenReturn(List.of(place));
        when(photos.verifyPlaceImages(List.of(place))).thenReturn(Map.of());
        var service = new AlojamientoRecommendationService(permissions, source, photos);

        var result = service.buscar(4L, 8L);

        assertEquals(1, result.size());
        assertEquals("Hotel de prueba", result.get(0).nombre());
        assertEquals(new BigDecimal("4.6"), result.get(0).latitud());
        assertEquals("https://www.openstreetmap.org/node/1", result.get(0).urlFuente());
        assertNull(result.get(0).imagenUrl());
        assertTrue(result.get(0).urlBusquedaTarifas().contains("booking.com/searchresults.html"));
        assertTrue(result.get(0).urlBusquedaTarifas().contains("checkin=2026-12-01"));
        assertTrue(result.get(0).urlBusquedaTarifas().contains("checkout=2026-12-05"));
        verify(photos).verifyPlaceImages(List.of(place));
        verify(permissions).requireRead(4L, 8L);
    }

    @Test
    void doesNotQueryPublicSourceWhenTripCannotBeRead() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        FuenteLugaresPublicos source = mock(FuenteLugaresPublicos.class);
        when(permissions.requireRead(4L, 8L)).thenThrow(new ResourceNotFoundException());
        var service = new AlojamientoRecommendationService(permissions, source, mock(WikimediaCommonsPhotoService.class));

        assertThrows(ResourceNotFoundException.class, () -> service.buscar(4L, 8L));
        verifyNoInteractions(source);
    }

    @Test
    void geocodesManualLodgingOnlyAfterEditPermissionAndReturnsVerifiedCoordinates() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        FuenteLugaresPublicos source = mock(FuenteLugaresPublicos.class);
        when(source.geocodificarDestino("Hotel, Calle 10, Bogotá"))
                .thenReturn(java.util.Optional.of(new CoordenadasPublicas(new BigDecimal("4.6"), new BigDecimal("-74.1"))));
        var service = new AlojamientoRecommendationService(permissions, source, mock(WikimediaCommonsPhotoService.class));

        var result = service.ubicarAlojamiento(4L, 8L, "Hotel, Calle 10, Bogotá");

        assertTrue(result.isPresent());
        assertEquals(new BigDecimal("4.6"), result.orElseThrow().latitud());
        verify(permissions).requireEdit(4L, 8L);
        verify(source).geocodificarDestino("Hotel, Calle 10, Bogotá");
    }

    @Test
    void rejectsBlankOrOversizedLodgingGeocodingQuery() {
        var service = new AlojamientoRecommendationService(mock(TripPermissionService.class),
                mock(FuenteLugaresPublicos.class), mock(WikimediaCommonsPhotoService.class));
        assertThrows(IllegalArgumentException.class, () -> service.ubicarAlojamiento(4L, 8L, "  "));
        assertThrows(IllegalArgumentException.class, () -> service.ubicarAlojamiento(4L, 8L, "x".repeat(301)));
    }
}
