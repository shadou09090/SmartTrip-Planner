package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.*;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.*;
import com.project.back_end_proyecto.service.publicplaces.LugarPublico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ExternalRecommendationAssignmentTest {
    private static final Long USER_ID = 7L;
    private static final Long TRIP_ID = 301L;
    private static final Long DAY_ID = 161L;
    private static final String EXTERNAL_ID = "geoapify:5165da04701a800140590fc2858a0fba4440f00102f901d823690a0000000092030f43617361206465206c274169677561";

    private TripPermissionService permissions;
    private DiaItinerarioRepository days;
    private ActividadRepository activities;
    private ItinerarioActividadRepository assignments;
    private ViajeInteresRepository tripInterests;
    private ExternalActivityRecommendationService recommendations;
    private ItinerarioService service;
    private DiaItinerario day;

    @BeforeEach
    void setUp() throws Exception {
        permissions = mock(TripPermissionService.class);
        days = mock(DiaItinerarioRepository.class);
        activities = mock(ActividadRepository.class);
        assignments = mock(ItinerarioActividadRepository.class);
        tripInterests = mock(ViajeInteresRepository.class);
        recommendations = mock(ExternalActivityRecommendationService.class);
        var itineraryRepository = mock(ItinerarioRepository.class);
        var tripRepository = mock(ViajeRepository.class);
        var activityInterests = mock(ActividadInteresRepository.class);
        var interests = mock(InteresRepository.class);
        service = new ItinerarioService(itineraryRepository, days, tripRepository, activities,
                activityInterests, assignments, interests, tripInterests, permissions,
                mock(ActivityRecommendationCatalogService.class), recommendations);

        Viaje trip = new Viaje(new User("User", "user@example.invalid", "hash"), "Bogotá", "Barcelona",
                LocalDate.parse("2026-11-20"), LocalDate.parse("2026-11-25"), null);
        day = new DiaItinerario(new Itinerario(trip), LocalDate.parse("2026-11-20"));
        setField(day, "id", DAY_ID);
        when(permissions.requireEdit(USER_ID, TRIP_ID)).thenReturn(trip);
        when(days.findForUpdateByIdAndTripId(DAY_ID, TRIP_ID)).thenReturn(Optional.of(day));
        when(tripInterests.findAllByViaje_Id(TRIP_ID)).thenReturn(List.of());
        when(assignments.findByDia_IdAndOrden(DAY_ID, 1)).thenReturn(Optional.empty());
        when(assignments.findByDia_IdAndActividad_Id(DAY_ID, 99L)).thenReturn(Optional.empty());
        when(activities.findAllByOrderByNombreAsc()).thenReturn(List.of());
        when(activities.save(any(Actividad.class))).thenAnswer(call -> {
            Actividad activity = call.getArgument(0);
            setField(activity, "id", 99L);
            return activity;
        });
        LugarPublico place = new LugarPublico(EXTERNAL_ID, "Casa de l'Aigua", "Descripción", "museum",
                "Barcelona", null, null, "https://www.openstreetmap.org/", Map.of());
        when(recommendations.selection(TRIP_ID, EXTERNAL_ID, "Barcelona", List.of()))
                .thenReturn(new ExternalActivityRecommendationService.Selection(place, List.of()));
    }

    @Test
    void assignsLongGeoapifyIdentifierWithoutTruncationOrPathParsing() {
        var response = service.agregarRecomendacionExterna(USER_ID, TRIP_ID, DAY_ID, EXTERNAL_ID, 1);

        assertEquals(99L, response.id());
        assertEquals("Casa de l'Aigua", response.nombre());
        verify(recommendations).selection(TRIP_ID, EXTERNAL_ID, "Barcelona", List.of());
        verify(assignments).save(any(ItinerarioActividad.class));
    }

    @Test
    void missingCandidateIsNotReportedAsSuccessfulAssignment() {
        when(recommendations.selection(TRIP_ID, EXTERNAL_ID, "Barcelona", List.of()))
                .thenThrow(new ResourceNotFoundException());

        assertThrows(ResourceNotFoundException.class,
                () -> service.agregarRecomendacionExterna(USER_ID, TRIP_ID, DAY_ID, EXTERNAL_ID, 1));
        verifyNoInteractions(activities);
        verify(assignments, never()).save(any(ItinerarioActividad.class));
    }

    @Test
    void rejectsDayOutsideTripBeforeLookingUpCandidate() {
        when(days.findForUpdateByIdAndTripId(DAY_ID, TRIP_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.agregarRecomendacionExterna(USER_ID, TRIP_ID, DAY_ID, EXTERNAL_ID, 1));
        verifyNoInteractions(recommendations, activities, assignments);
    }

    @Test
    void propagatesPersistenceFailureInsteadOfReturningAFalseSuccess() {
        doThrow(new IllegalStateException("persistence failure")).when(assignments).save(any(ItinerarioActividad.class));

        assertThrows(IllegalStateException.class,
                () -> service.agregarRecomendacionExterna(USER_ID, TRIP_ID, DAY_ID, EXTERNAL_ID, 1));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
