package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.Itinerario;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.dto.AlojamientoRequest;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.AlojamientoRepository;
import com.project.back_end_proyecto.repository.HistorialPrecioVueloRepository;
import com.project.back_end_proyecto.repository.ItinerarioRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import com.project.back_end_proyecto.repository.VueloRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ViajeExtrasServiceTest {
    private ItinerarioRepository itinerarioRepository;
    private AlojamientoRepository alojamientoRepository;
    private VueloRepository vueloRepository;
    private TripPermissionService permissions;
    private ViajeExtrasService service;

    @BeforeEach
    void setUp() {
        itinerarioRepository = mock(ItinerarioRepository.class);
        alojamientoRepository = mock(AlojamientoRepository.class);
        vueloRepository = mock(VueloRepository.class);
        permissions = mock(TripPermissionService.class);
        service = new ViajeExtrasService(mock(ViajeRepository.class), itinerarioRepository,
                alojamientoRepository, vueloRepository, mock(HistorialPrecioVueloRepository.class), permissions);
    }

    @Test
    void authorizedTripWithoutItineraryHasNoLodgingInsteadOfNotFound() {
        when(itinerarioRepository.findByViaje_Id(5L)).thenReturn(Optional.empty());

        assertTrue(service.alojamiento(9L, 5L).isEmpty());
        verify(permissions).requireRead(9L, 5L);
        verifyNoInteractions(alojamientoRepository);
    }

    @Test
    void itineraryWithoutLodgingHasAnEmptyResult() {
        Itinerario itinerary = mock(Itinerario.class);
        when(itinerary.getId()).thenReturn(12L);
        when(itinerarioRepository.findByViaje_Id(5L)).thenReturn(Optional.of(itinerary));
        when(alojamientoRepository.findByItinerario_Id(12L)).thenReturn(Optional.empty());

        assertTrue(service.alojamiento(9L, 5L).isEmpty());
    }

    @Test
    void inaccessibleTripStillReturnsNotFoundBeforeLookingUpLodging() {
        doThrow(new ResourceNotFoundException()).when(permissions).requireRead(9L, 5L);

        assertThrows(ResourceNotFoundException.class, () -> service.alojamiento(9L, 5L));
        verifyNoInteractions(itinerarioRepository, alojamientoRepository);
    }

    @Test
    void savingLodgingCreatesMissingItineraryThenPersistsLodging() {
        Viaje trip = mock(Viaje.class);
        Itinerario itinerary = mock(Itinerario.class);
        when(permissions.requireEdit(9L, 5L)).thenReturn(trip);
        when(itinerary.getId()).thenReturn(12L);
        when(itinerarioRepository.findByViaje_Id(5L)).thenReturn(Optional.empty());
        when(itinerarioRepository.save(any(Itinerario.class))).thenReturn(itinerary);
        when(alojamientoRepository.findByItinerario_Id(12L)).thenReturn(Optional.empty());
        when(alojamientoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.guardarAlojamiento(9L, 5L,
                new AlojamientoRequest("Hotel de prueba", "Dirección de prueba", new BigDecimal("24.4539"), new BigDecimal("54.3773")));

        assertEquals("Hotel de prueba", response.nombre());
        assertEquals(new BigDecimal("24.4539000"), response.latitud());
        verify(itinerarioRepository).save(any(Itinerario.class));
        verify(alojamientoRepository).save(any());
    }

    @Test
    void savingAnotherLodgingReplacesTheExistingOneWithoutCreatingAnotherItinerary() {
        Viaje trip = mock(Viaje.class);
        Itinerario itinerary = mock(Itinerario.class);
        com.project.back_end_proyecto.entity.Alojamiento existing = new com.project.back_end_proyecto.entity.Alojamiento(
                itinerary, "Hotel anterior", null, null, null);
        when(permissions.requireEdit(9L, 5L)).thenReturn(trip);
        when(itinerary.getId()).thenReturn(12L);
        when(itinerarioRepository.findByViaje_Id(5L)).thenReturn(Optional.of(itinerary));
        when(alojamientoRepository.findByItinerario_Id(12L)).thenReturn(Optional.of(existing));

        var response = service.guardarAlojamiento(9L, 5L,
                new AlojamientoRequest("Hotel nuevo", "Nueva dirección", null, null));

        assertEquals("Hotel nuevo", response.nombre());
        assertEquals("Nueva dirección", response.direccion());
        verify(itinerarioRepository, never()).save(any());
        verify(alojamientoRepository, never()).save(any());
    }

    @Test
    void tripWithoutFlightsReturnsAnEmptyList() {
        when(vueloRepository.findAllByViaje_IdOrderByFechaSalida(5L)).thenReturn(List.of());

        assertTrue(service.vuelos(9L, 5L).isEmpty());
        verify(permissions).requireRead(9L, 5L);
    }
}
