package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.Alojamiento;
import com.project.back_end_proyecto.entity.Itinerario;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.repository.AlojamientoRepository;
import com.project.back_end_proyecto.repository.ItinerarioActividadRepository;
import com.project.back_end_proyecto.repository.ItinerarioRepository;
import com.project.back_end_proyecto.service.publicplaces.CoordenadasPublicas;
import com.project.back_end_proyecto.service.publicplaces.DestinoCoordinateCache;
import com.project.back_end_proyecto.service.publicplaces.ErrorFuentePublica;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TripDestinationCoordinateResolverTest {
    @Test
    void reusesSavedLodgingCoordinatesWhenPublicGeocoderIsTemporarilyUnavailable() {
        var places = mock(FuenteLugaresPublicos.class);
        var itineraries = mock(ItinerarioRepository.class);
        var lodgings = mock(AlojamientoRepository.class);
        var assignments = mock(ItinerarioActividadRepository.class);
        var cache = new DestinoCoordinateCache();
        var resolver = new TripDestinationCoordinateResolver(places, itineraries, lodgings, assignments, cache);
        Viaje trip = mock(Viaje.class);
        Itinerario itinerary = mock(Itinerario.class);
        when(trip.getId()).thenReturn(322L);
        when(trip.getDestino()).thenReturn("Paris, France");
        when(places.geocodificarDestino("Paris, France")).thenThrow(new ErrorFuentePublica("Nominatim paused"));
        when(itineraries.findByViaje_Id(322L)).thenReturn(Optional.of(itinerary));
        when(itinerary.getId()).thenReturn(77L);
        when(lodgings.findByItinerario_Id(77L)).thenReturn(Optional.of(
                new Alojamiento(itinerary, "Hotel seleccionado", null,
                        new BigDecimal("48.8606"), new BigDecimal("2.3376"))));

        var resolved = resolver.resolve(trip).orElseThrow();

        assertEquals("coordenadas guardadas en el viaje (origen no registrado)", resolved.source());
        assertTrue(resolved.reusedFromTrip());
        assertEquals(new BigDecimal("48.8606"), resolved.coordinates().latitud());
        assertEquals(Optional.of(resolved.coordinates()), cache.resolve("Paris, France", "Geoapify", () -> {
            fail("trip coordinates must seed the shared destination cache"); return Optional.empty();
        }));
        verify(assignments, never()).findDistinctActivitiesByTripId(any());
    }

    @Test
    void propagatesProviderFailureWhenTripHasNoVerifiedCoordinatesToReuse() {
        var places = mock(FuenteLugaresPublicos.class);
        var itineraries = mock(ItinerarioRepository.class);
        var lodgings = mock(AlojamientoRepository.class);
        var assignments = mock(ItinerarioActividadRepository.class);
        var resolver = new TripDestinationCoordinateResolver(places, itineraries, lodgings, assignments,
                new DestinoCoordinateCache());
        Viaje trip = mock(Viaje.class);
        when(trip.getId()).thenReturn(322L);
        when(trip.getDestino()).thenReturn("Paris, France");
        var failure = new ErrorFuentePublica("temporary geocoder failure");
        when(places.geocodificarDestino("Paris, France")).thenThrow(failure);
        when(itineraries.findByViaje_Id(322L)).thenReturn(Optional.empty());

        assertSame(failure, assertThrows(ErrorFuentePublica.class, () -> resolver.resolve(trip)));
        verify(lodgings, never()).findByItinerario_Id(any());
    }
}
