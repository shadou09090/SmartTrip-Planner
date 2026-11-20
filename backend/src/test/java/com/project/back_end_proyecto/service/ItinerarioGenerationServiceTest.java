package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.ActividadRecomendacionResponse;
import com.project.back_end_proyecto.dto.ActividadResponse;
import com.project.back_end_proyecto.entity.*;
import com.project.back_end_proyecto.repository.*;
import com.project.back_end_proyecto.service.publicplaces.LugarPublico;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ItinerarioGenerationServiceTest {
    @Test
    void generatesActivitiesByInterestAvailabilityAndBudgetWithoutExceedingTheTripBudget() throws Exception {
        var itineraryRepository = mock(ItinerarioRepository.class);
        var dayRepository = mock(DiaItinerarioRepository.class);
        var tripRepository = mock(ViajeRepository.class);
        var activityRepository = mock(ActividadRepository.class);
        var activityInterestRepository = mock(ActividadInteresRepository.class);
        var itineraryActivityRepository = mock(ItinerarioActividadRepository.class);
        var interestRepository = mock(InteresRepository.class);
        var tripInterestRepository = mock(ViajeInteresRepository.class);
        var permissions = mock(TripPermissionService.class);
        var catalog = mock(ActivityRecommendationCatalogService.class);
        var user = new User("Owner", "owner@example.invalid", "hash");
        var trip = new Viaje(user, "Bogotá", "Cali", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 1), new BigDecimal("25.00"));
        when(permissions.requireEdit(5L, 12L)).thenReturn(trip);
        when(catalog.load(5L, 12L)).thenReturn(new ActivityRecommendationCatalogService.Snapshot(
                "Cali", trip.getFechaInicio(), trip.getFechaFin(), List.of("Cultura"), false,
                List.of(candidate(1L, "Museo", List.of("Cultura", "Historia")),
                        candidate(2L, "Tour", List.of("Cultura")))));
        var externalRecommendations = mock(ExternalActivityRecommendationService.class);
        when(externalRecommendations.recommend(any(), any(), any(), any(), any()))
                .thenReturn(new ExternalActivityRecommendationService.Result(List.of(), List.of()));
        Actividad museum = activity(1L, "Museo", "20.00");
        Actividad tour = activity(2L, "Tour", "10.00");
        when(activityRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(museum, tour));
        when(activityRepository.findById(1L)).thenReturn(Optional.of(museum));
        when(activityRepository.findById(2L)).thenReturn(Optional.of(tour));

        AtomicReference<Itinerario> savedItinerary = new AtomicReference<>();
        when(itineraryRepository.findByViaje_Id(12L)).thenAnswer(call -> Optional.ofNullable(savedItinerary.get()));
        when(itineraryRepository.save(any(Itinerario.class))).thenAnswer(call -> {
            Itinerario value = call.getArgument(0); setId(value, "id", 29L); savedItinerary.set(value); return value;
        });
        List<DiaItinerario> days = new ArrayList<>();
        when(dayRepository.findAllByItinerario_IdOrderByFecha(29L)).thenAnswer(call -> List.copyOf(days));
        when(dayRepository.save(any(DiaItinerario.class))).thenAnswer(call -> {
            DiaItinerario value = call.getArgument(0); setId(value, "id", 4L); days.add(value); return value;
        });
        List<ItinerarioActividad> assignments = new ArrayList<>();
        when(itineraryActivityRepository.findAllByDia_IdOrderByOrden(4L)).thenAnswer(call -> List.copyOf(assignments));
        when(itineraryActivityRepository.save(any(ItinerarioActividad.class))).thenAnswer(call -> {
            ItinerarioActividad value = call.getArgument(0); assignments.add(value); return value;
        });
        when(activityInterestRepository.findAllByActividad_Id(any())).thenReturn(List.of());

        var service = new ItinerarioService(itineraryRepository, dayRepository, tripRepository, activityRepository,
                activityInterestRepository, itineraryActivityRepository, interestRepository, tripInterestRepository,
                permissions, catalog, externalRecommendations);

        var result = service.generarParaViaje(5L, 12L);

        assertEquals(1, assignments.size());
        assertEquals(1L, assignments.get(0).getActividad().getId());
        assertEquals(1, assignments.get(0).getOrden());
        assertTrue(result.mensaje().contains("1 actividades del catálogo"));
        verify(itineraryActivityRepository, times(1)).save(any(ItinerarioActividad.class));

        service.generarParaViaje(5L, 12L);
        assertEquals(1, assignments.size(), "Running generation again must preserve and not duplicate saved choices");
        verify(itineraryActivityRepository, times(1)).save(any(ItinerarioActividad.class));
    }

    @Test
    void keepsExternalPlacesAvailableForExplicitSelectionWithoutPersistingThemAutomatically() throws Exception {
        var itineraryRepository = mock(ItinerarioRepository.class);
        var dayRepository = mock(DiaItinerarioRepository.class);
        var activityRepository = mock(ActividadRepository.class);
        var activityInterestRepository = mock(ActividadInteresRepository.class);
        var itineraryActivityRepository = mock(ItinerarioActividadRepository.class);
        var interestRepository = mock(InteresRepository.class);
        var tripInterestRepository = mock(ViajeInteresRepository.class);
        var permissions = mock(TripPermissionService.class);
        var catalog = mock(ActivityRecommendationCatalogService.class);
        var external = mock(ExternalActivityRecommendationService.class);
        var owner = new User("Owner", "owner@example.invalid", "hash");
        var trip = new Viaje(owner, "Bogotá", "Cali", LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 2), new BigDecimal("5000000.00"));
        when(permissions.requireEdit(5L, 12L)).thenReturn(trip);
        when(catalog.load(5L, 12L)).thenReturn(new ActivityRecommendationCatalogService.Snapshot(
                "Cali", trip.getFechaInicio(), trip.getFechaFin(), List.of("Cultura"), false, List.of()));
        var first = new com.project.back_end_proyecto.dto.RecomendacionExterna("geoapify:1", "Museo público", null,
                "museum", "Centro", new BigDecimal("3.4"), new BigDecimal("-76.5"), null, null,
                "https://www.openstreetmap.org/?mlat=3.4&mlon=-76.5", null, List.of("Cultura"), "Cultura", "No verificada");
        var second = new com.project.back_end_proyecto.dto.RecomendacionExterna("geoapify:2", "Plaza pública", null,
                "historic", "Centro", new BigDecimal("3.401"), new BigDecimal("-76.501"), null, null,
                "https://www.openstreetmap.org/?mlat=3.401&mlon=-76.501", null, List.of("Cultura"), "Cultura", "No verificada");
        when(external.recommend(12L, "Cali", trip.getFechaInicio(), trip.getFechaFin(), List.of("Cultura")))
                .thenReturn(new ExternalActivityRecommendationService.Result(List.of(first, second), List.of()));
        when(activityRepository.findAllByOrderByNombreAsc()).thenReturn(List.of());

        AtomicReference<Itinerario> savedItinerary = new AtomicReference<>();
        when(itineraryRepository.findByViaje_Id(12L)).thenAnswer(call -> Optional.ofNullable(savedItinerary.get()));
        when(itineraryRepository.save(any(Itinerario.class))).thenAnswer(call -> {
            Itinerario value = call.getArgument(0); setId(value, "id", 29L); savedItinerary.set(value); return value;
        });
        List<DiaItinerario> days = new ArrayList<>();
        AtomicReference<Long> dayIds = new AtomicReference<>(3L);
        when(dayRepository.findAllByItinerario_IdOrderByFecha(29L)).thenAnswer(call -> List.copyOf(days));
        when(dayRepository.save(any(DiaItinerario.class))).thenAnswer(call -> {
            DiaItinerario value = call.getArgument(0); setId(value, "id", dayIds.getAndUpdate(id -> id + 1)); days.add(value); return value;
        });
        List<ItinerarioActividad> assignments = new ArrayList<>();
        when(itineraryActivityRepository.findAllByDia_IdOrderByOrden(any())).thenAnswer(call -> assignments.stream()
                .filter(item -> item.getDia().getId().equals(call.getArgument(0))).toList());
        when(itineraryActivityRepository.save(any(ItinerarioActividad.class))).thenAnswer(call -> {
            ItinerarioActividad value = call.getArgument(0); assignments.add(value); return value;
        });

        var service = new ItinerarioService(itineraryRepository, dayRepository, mock(ViajeRepository.class),
                activityRepository, activityInterestRepository, itineraryActivityRepository, interestRepository,
                tripInterestRepository, permissions, catalog, external);

        var result = service.generarParaViaje(5L, 12L);

        assertEquals(0, assignments.size());
        assertEquals(2, result.dias().size());
        assertTrue(result.dias().stream().allMatch(day -> day.actividades().isEmpty()));
        assertTrue(result.mensaje().contains("2 lugares públicos"));
        assertTrue(result.mensaje().contains("no se guardaron automáticamente"));
        verify(activityRepository, never()).save(any(Actividad.class));
        verify(external, never()).selection(any(), anyString(), anyString(), anyList());
    }

    private static ActividadRecomendacionResponse.ActividadRecomendada candidate(Long id, String name, List<String> matches) {
        return new ActividadRecomendacionResponse.ActividadRecomendada(new ActividadResponse(id, name, null, null,
                null, null, null, null, null, null, List.of()), matches);
    }

    private static Actividad activity(Long id, String name, String cost) throws Exception {
        Actividad activity = new Actividad(name, null, null, null, null, new BigDecimal(cost),
                null, null);
        setId(activity, "id", id);
        return activity;
    }

    private static void setId(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
}
