package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.DiaItinerario;
import com.project.back_end_proyecto.entity.Actividad;
import com.project.back_end_proyecto.entity.ItinerarioActividad;
import com.project.back_end_proyecto.entity.Itinerario;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.dto.ActividadRecomendacionResponse;
import com.project.back_end_proyecto.dto.ActividadResponse;
import com.project.back_end_proyecto.dto.RecomendacionExterna;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.DiaItinerarioRepository;
import com.project.back_end_proyecto.repository.ActividadRepository;
import com.project.back_end_proyecto.repository.ActividadInteresRepository;
import com.project.back_end_proyecto.repository.InteresRepository;
import com.project.back_end_proyecto.repository.ItinerarioActividadRepository;
import com.project.back_end_proyecto.repository.ItinerarioRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import com.project.back_end_proyecto.repository.ViajeInteresRepository;
import com.project.back_end_proyecto.service.publicplaces.LugarPublico;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyLong;

class ItinerarioServiceTest {
    private ItinerarioRepository itinerarioRepository;
    private DiaItinerarioRepository diaRepository;
    private ViajeRepository viajeRepository;
    private TripPermissionService permissionService;
    private ItinerarioService service;

    @BeforeEach
    void setUp() {
        itinerarioRepository = mock(ItinerarioRepository.class);
        diaRepository = mock(DiaItinerarioRepository.class);
        viajeRepository = mock(ViajeRepository.class);
        permissionService = mock(TripPermissionService.class);
        service = new ItinerarioService(itinerarioRepository, diaRepository, viajeRepository,
                mock(ActividadRepository.class), mock(ActividadInteresRepository.class),
                mock(ItinerarioActividadRepository.class), mock(InteresRepository.class),
                mock(ViajeInteresRepository.class), permissionService,
                mock(ActivityRecommendationCatalogService.class), mock(ExternalActivityRecommendationService.class));
    }

    @Test
    void createsItineraryAndOnePersistedDayPerTripDateForOwner() throws Exception {
        Viaje viaje = new Viaje(new User("Owner", "owner@example.invalid", "hash"), "Bogotá", "Cali",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null);
        when(viajeRepository.findByIdAndUsuario_Id(12L, 5L)).thenReturn(Optional.of(viaje));
        when(permissionService.requireEdit(5L, 12L)).thenReturn(viaje);
        when(itinerarioRepository.findByViaje_Id(12L)).thenReturn(Optional.empty());
        when(itinerarioRepository.save(any(Itinerario.class))).thenAnswer(invocation -> {
            Itinerario saved = invocation.getArgument(0);
            setField(saved, "id", 29L);
            return saved;
        });
        List<DiaItinerario> savedDays = new ArrayList<>();
        when(diaRepository.findAllByItinerario_IdOrderByFecha(29L)).thenAnswer(invocation -> List.copyOf(savedDays));
        when(diaRepository.save(any(DiaItinerario.class))).thenAnswer(invocation -> {
            DiaItinerario saved = invocation.getArgument(0);
            savedDays.add(saved);
            return saved;
        });

        var response = service.crearParaViaje(5L, 12L);

        assertEquals(29L, response.id());
        assertEquals(List.of(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)),
                response.dias().stream().map(day -> day.fecha()).toList());
        verify(diaRepository, times(2)).save(any(DiaItinerario.class));
    }

    @Test
    void fillsOnlyMissingTripDatesWithoutDuplicatingExistingDays() throws Exception {
        Viaje viaje = new Viaje(new User("Owner", "owner@example.invalid", "hash"), "Bogotá", "Cali",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3), null);
        when(permissionService.requireEdit(5L, 12L)).thenReturn(viaje);
        Itinerario itinerary = new Itinerario(viaje);
        setField(itinerary, "id", 29L);
        when(itinerarioRepository.findByViaje_Id(12L)).thenReturn(Optional.of(itinerary));
        DiaItinerario existing = new DiaItinerario(itinerary, LocalDate.of(2026, 8, 1));
        setField(existing, "id", 41L);
        List<DiaItinerario> days = new ArrayList<>(List.of(existing));
        when(diaRepository.findAllByItinerario_IdOrderByFecha(29L)).thenAnswer(invocation -> List.copyOf(days));
        when(diaRepository.save(any(DiaItinerario.class))).thenAnswer(invocation -> {
            DiaItinerario saved = invocation.getArgument(0);
            days.add(saved);
            return saved;
        });

        var response = service.crearParaViaje(5L, 12L);

        assertEquals(List.of(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), LocalDate.of(2026, 8, 3)),
                response.dias().stream().map(day -> day.fecha()).toList());
        verify(diaRepository, times(2)).save(any(DiaItinerario.class));
    }

    @Test
    void doesNotCreateItineraryForAnotherUsersTrip() {
        when(permissionService.requireEdit(5L, 12L)).thenThrow(new ResourceNotFoundException());

        assertThrows(ResourceNotFoundException.class, () -> service.crearParaViaje(5L, 12L));
        verifyNoInteractions(itinerarioRepository, diaRepository);
    }

    @Test
    void keepsExternalPlacesAsSuggestionsInsteadOfSilentlySavingThemWhenCatalogHasNoMatches() throws Exception {
        LocalDate start = LocalDate.of(2026, 11, 20);
        LocalDate end = LocalDate.of(2026, 11, 21);
        Viaje trip = new Viaje(new User("Owner", "owner@example.invalid", "hash"), "Bogotá", "Santa Marta",
                start, end, new BigDecimal("7500000.00"));
        Itinerario itinerary = new Itinerario(trip);
        setField(itinerary, "id", 29L);
        DiaItinerario day = new DiaItinerario(itinerary, start);
        setField(day, "id", 41L);
        List<DiaItinerario> days = List.of(day);
        when(permissionService.requireEdit(5L, 12L)).thenReturn(trip);
        when(itinerarioRepository.findByViaje_Id(12L)).thenReturn(Optional.empty(), Optional.of(itinerary));
        when(itinerarioRepository.save(any(Itinerario.class))).thenReturn(itinerary);
        when(diaRepository.findAllByItinerario_IdOrderByFecha(29L)).thenReturn(List.of(), days, days);
        ActivityRecommendationCatalogService catalog = mock(ActivityRecommendationCatalogService.class);
        when(catalog.load(5L, 12L)).thenReturn(new ActivityRecommendationCatalogService.Snapshot(
                "Santa Marta", start, end, List.of("naturaleza"), false, List.of()));
        ExternalActivityRecommendationService external = mock(ExternalActivityRecommendationService.class);
        var place = new RecomendacionExterna("osm:1", "Parque real", null, "parque", "Santa Marta",
                null, null, null, null, "https://www.openstreetmap.org/", null,
                List.of("naturaleza"), "Coincide con el interés", "Disponibilidad no informada");
        when(external.recommend(12L, "Santa Marta", start, end, List.of("naturaleza")))
                .thenReturn(new ExternalActivityRecommendationService.Result(List.of(place), List.of()));
        ItinerarioActividadRepository assignments = mock(ItinerarioActividadRepository.class);
        when(assignments.findAllByDia_IdOrderByOrden(41L)).thenReturn(List.of());
        ActividadRepository activities = mock(ActividadRepository.class);
        when(activities.findAllByOrderByNombreAsc()).thenReturn(List.of());
        service = new ItinerarioService(itinerarioRepository, diaRepository, viajeRepository, activities,
                mock(ActividadInteresRepository.class), assignments, mock(InteresRepository.class),
                mock(ViajeInteresRepository.class), permissionService, catalog, external);

        var result = service.generarParaViaje(5L, 12L);

        assertEquals(1, result.dias().size());
        assertTrue(result.dias().get(0).actividades().isEmpty());
        assertTrue(result.mensaje().contains("no ofreció actividades compatibles"));
        assertTrue(result.mensaje().contains("1 lugares públicos"));
        verify(assignments, never()).save(any(ItinerarioActividad.class));
    }

    @Test
    void reportsUnknownActivityPricesSeparatelyFromKnownSpend() throws Exception {
        Viaje trip = new Viaje(new User("Owner", "owner@example.invalid", "hash"), "Bogotá", "Cali",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), new BigDecimal("5000000.00"));
        when(permissionService.requireRead(5L, 12L)).thenReturn(trip);
        ItinerarioActividadRepository assignments = mock(ItinerarioActividadRepository.class);
        Actividad known = activity(90L, "Known");
        setField(known, "costo", new BigDecimal("12500.00"));
        Actividad unknown = activity(91L, "Unknown");
        when(assignments.findDistinctActivitiesByTripId(12L)).thenReturn(List.of(known, unknown));
        ItinerarioService budgetService = new ItinerarioService(itinerarioRepository, diaRepository, viajeRepository,
                mock(ActividadRepository.class), mock(ActividadInteresRepository.class), assignments,
                mock(InteresRepository.class), mock(ViajeInteresRepository.class), permissionService,
                mock(ActivityRecommendationCatalogService.class), mock(ExternalActivityRecommendationService.class));

        var result = budgetService.presupuestoActividades(5L, 12L);

        assertEquals(new BigDecimal("12500.00"), result.costoAcumulado());
        assertEquals(1L, result.actividadesSinPrecio());
        assertEquals(new BigDecimal("0.25"), result.porcentajeUtilizado());
        assertFalse(result.superaPresupuesto());
    }

    @Test
    void budgetDoesNotCountTheSameActivityTwiceWhenItAppearsOnMultipleDays() throws Exception {
        Viaje trip = new Viaje(new User("Owner", "owner@example.invalid", "hash"), "Bogotá", "Cali",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), new BigDecimal("5000000.00"));
        when(permissionService.requireRead(5L, 12L)).thenReturn(trip);
        ItinerarioActividadRepository assignments = mock(ItinerarioActividadRepository.class);
        Actividad repeated = activity(92L, "Repeated");
        setField(repeated, "costo", new BigDecimal("12500.00"));
        Actividad withoutPrice = activity(93L, "Unknown");
        when(assignments.findDistinctActivitiesByTripId(12L)).thenReturn(List.of(repeated, repeated, withoutPrice));
        ItinerarioService budgetService = new ItinerarioService(itinerarioRepository, diaRepository, viajeRepository,
                mock(ActividadRepository.class), mock(ActividadInteresRepository.class), assignments,
                mock(InteresRepository.class), mock(ViajeInteresRepository.class), permissionService,
                mock(ActivityRecommendationCatalogService.class), mock(ExternalActivityRecommendationService.class));

        var result = budgetService.presupuestoActividades(5L, 12L);

        assertEquals(new BigDecimal("12500.00"), result.costoAcumulado());
        assertEquals(1L, result.actividadesSinPrecio());
        assertEquals(new BigDecimal("0.25"), result.porcentajeUtilizado());
    }

    @Test
    void reordersActivitiesWithoutViolatingTheUniqueDayOrderConstraint() throws Exception {
        Long tripId = 12L, dayId = 7L;
        DiaItinerario day = new DiaItinerario(new Itinerario(
                new Viaje(new User("Owner", "owner@example.invalid", "hash"), "A", "B",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2), null)), LocalDate.of(2026, 8, 1));
        setField(day, "id", dayId);
        when(permissionService.requireEdit(5L, tripId)).thenReturn(null);
        when(diaRepository.findForUpdateByIdAndTripId(dayId, tripId)).thenReturn(Optional.of(day));
        Actividad first = activity(21L, "First");
        Actividad second = activity(22L, "Second");
        ItinerarioActividad firstAssignment = new ItinerarioActividad(day, first, 1);
        ItinerarioActividad secondAssignment = new ItinerarioActividad(day, second, 2);
        ItinerarioActividadRepository assignments = mock(ItinerarioActividadRepository.class);
        when(assignments.findAllByDia_IdOrderByOrden(dayId)).thenReturn(List.of(firstAssignment, secondAssignment));
        service = new ItinerarioService(itinerarioRepository, diaRepository, viajeRepository,
                mock(ActividadRepository.class), mock(ActividadInteresRepository.class), assignments,
                mock(InteresRepository.class), mock(ViajeInteresRepository.class), permissionService,
                mock(ActivityRecommendationCatalogService.class), mock(ExternalActivityRecommendationService.class));

        service.reordenarActividad(5L, tripId, dayId, 22L, 1);

        assertEquals(1, secondAssignment.getOrden());
        assertEquals(2, firstAssignment.getOrden());
        verify(assignments).saveAllAndFlush(anyList());
        verify(assignments).saveAll(anyList());
    }

    @Test
    void movesActivityOnlyToAnotherDayOfTheSameTripAndCompactsSourceOrders() throws Exception {
        Long tripId = 12L, sourceId = 7L, targetId = 8L;
        DiaItinerario source = dayWithId(sourceId, LocalDate.of(2026, 8, 1));
        DiaItinerario target = dayWithId(targetId, LocalDate.of(2026, 8, 2));
        when(permissionService.requireEdit(5L, tripId)).thenReturn(null);
        when(diaRepository.findForUpdateByIdAndTripId(sourceId, tripId)).thenReturn(Optional.of(source));
        when(diaRepository.findForUpdateByIdAndTripId(targetId, tripId)).thenReturn(Optional.of(target));
        Actividad moved = activity(31L, "Moved");
        Actividad remaining = activity(32L, "Remaining");
        ItinerarioActividad movedAssignment = new ItinerarioActividad(source, moved, 1);
        ItinerarioActividad remainingAssignment = new ItinerarioActividad(source, remaining, 2);
        ItinerarioActividadRepository assignments = mock(ItinerarioActividadRepository.class);
        when(assignments.findByDia_IdAndActividad_Id(sourceId, 31L)).thenReturn(Optional.of(movedAssignment));
        when(assignments.findAllByDia_IdOrderByOrden(sourceId)).thenReturn(List.of(movedAssignment, remainingAssignment));
        when(assignments.findAllByDia_IdOrderByOrden(targetId)).thenReturn(List.of());
        service = new ItinerarioService(itinerarioRepository, diaRepository, viajeRepository,
                mock(ActividadRepository.class), mock(ActividadInteresRepository.class), assignments,
                mock(InteresRepository.class), mock(ViajeInteresRepository.class), permissionService,
                mock(ActivityRecommendationCatalogService.class), mock(ExternalActivityRecommendationService.class));

        service.moverActividad(5L, tripId, sourceId, 31L, targetId);

        assertEquals(1, remainingAssignment.getOrden());
        verify(assignments).deleteById(any());
        verify(assignments).flush();
        verify(assignments).save(argThat(link -> link.getActividad().getId().equals(31L)
                && link.getOrden() == 1));
    }

    @Test
    void refusesToMoveActivityToADayOutsideTheAuthorizedTrip() throws Exception {
        Long tripId = 12L, sourceId = 7L, foreignDayId = 8L;
        DiaItinerario source = dayWithId(sourceId, LocalDate.of(2026, 8, 1));
        when(permissionService.requireEdit(5L, tripId)).thenReturn(null);
        when(diaRepository.findForUpdateByIdAndTripId(sourceId, tripId)).thenReturn(Optional.of(source));
        when(diaRepository.findForUpdateByIdAndTripId(foreignDayId, tripId)).thenReturn(Optional.empty());
        ItinerarioActividadRepository assignments = mock(ItinerarioActividadRepository.class);
        service = new ItinerarioService(itinerarioRepository, diaRepository, viajeRepository,
                mock(ActividadRepository.class), mock(ActividadInteresRepository.class), assignments,
                mock(InteresRepository.class), mock(ViajeInteresRepository.class), permissionService,
                mock(ActivityRecommendationCatalogService.class), mock(ExternalActivityRecommendationService.class));

        assertThrows(ResourceNotFoundException.class,
                () -> service.moverActividad(5L, tripId, sourceId, 31L, foreignDayId));
        verifyNoInteractions(assignments);
    }

    @Test
    void externalRecommendationUsesFreeAppendOrderWhenClientOrderIsStale() throws Exception {
        Long tripId = 322L, dayId = 186L;
        Viaje trip = new Viaje(new User("Owner", "owner@example.invalid", "hash"), "Bogotá", "París",
                LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 21), null);
        DiaItinerario day = dayWithId(dayId, LocalDate.of(2026, 11, 17));
        when(permissionService.requireEdit(5L, tripId)).thenReturn(trip);
        when(diaRepository.findForUpdateByIdAndTripId(dayId, tripId)).thenReturn(Optional.of(day));

        var external = mock(ExternalActivityRecommendationService.class);
        String externalId = "geoapify:517909a936931a034059a42ab7fea7724840f00103f901";
        var place = new LugarPublico(externalId, "Cité des Sciences et de l'Industrie", null, "museum",
                "Paris", new BigDecimal("48.895"), new BigDecimal("2.387"), null, Map.of());
        when(external.selection(eq(tripId), eq(externalId), eq("París"), anyList()))
                .thenReturn(new ExternalActivityRecommendationService.Selection(place, List.of()));

        Actividad occupied = activity(44L, "Existing activity");
        Actividad added = activity(45L, place.nombre());
        setField(added, "ubicacion", place.ubicacion());
        setField(added, "latitud", place.latitud());
        setField(added, "longitud", place.longitud());
        ActividadRepository activities = mock(ActividadRepository.class);
        when(activities.findAllByOrderByNombreAsc()).thenReturn(List.of(occupied));
        when(activities.save(any(Actividad.class))).thenReturn(added);

        ItinerarioActividadRepository assignments = mock(ItinerarioActividadRepository.class);
        ViajeInteresRepository viajeInteresRepository = mock(ViajeInteresRepository.class);
        when(assignments.findAllByDia_IdOrderByOrden(dayId))
                .thenReturn(List.of(new ItinerarioActividad(day, occupied, 1)));
        when(assignments.findByDia_IdAndActividad_Id(dayId, 45L)).thenReturn(Optional.empty());
        when(viajeInteresRepository.findAllByViaje_Id(tripId)).thenReturn(List.of());
        var activityInterests = mock(ActividadInteresRepository.class);
        ItinerarioService target = new ItinerarioService(itinerarioRepository, diaRepository, viajeRepository,
                activities, activityInterests, assignments, mock(InteresRepository.class), viajeInteresRepository,
                permissionService, mock(ActivityRecommendationCatalogService.class), external);

        ActividadResponse result = target.agregarRecomendacionExterna(5L, tripId, dayId, externalId, 1);

        assertEquals(2, result.orden());
        verify(assignments).save(argThat(link -> link.getDia().getId().equals(dayId)
                && link.getActividad().getId().equals(45L) && link.getOrden() == 2));
    }

    @Test
    void retryOfAlreadyAssignedExternalRecommendationReturnsExistingOrderWithoutDuplicate() throws Exception {
        Long tripId = 322L, dayId = 186L;
        Viaje trip = new Viaje(new User("Owner", "owner@example.invalid", "hash"), "Bogotá", "París",
                LocalDate.of(2026, 11, 17), LocalDate.of(2026, 11, 21), null);
        DiaItinerario day = dayWithId(dayId, LocalDate.of(2026, 11, 17));
        when(permissionService.requireEdit(5L, tripId)).thenReturn(trip);
        when(diaRepository.findForUpdateByIdAndTripId(dayId, tripId)).thenReturn(Optional.of(day));
        String externalId = "geoapify:long-external-id";
        var place = new LugarPublico(externalId, "Cité des Sciences et de l'Industrie", null, "museum",
                "Paris", null, null, null, Map.of());
        var external = mock(ExternalActivityRecommendationService.class);
        when(external.selection(eq(tripId), eq(externalId), eq("París"), anyList()))
                .thenReturn(new ExternalActivityRecommendationService.Selection(place, List.of()));
        Actividad saved = activity(46L, place.nombre());
        setField(saved, "ubicacion", place.ubicacion());
        ActividadRepository activities = mock(ActividadRepository.class);
        when(activities.findAllByOrderByNombreAsc()).thenReturn(List.of(saved));

        var activityInterests = mock(ActividadInteresRepository.class);
        when(activityInterests.findAllByActividad_Id(46L)).thenReturn(List.of());
        ItinerarioActividadRepository assignments = mock(ItinerarioActividadRepository.class);
        when(assignments.findByDia_IdAndActividad_Id(dayId, 46L))
                .thenReturn(Optional.of(new ItinerarioActividad(day, saved, 3)));
        var viajesInterests = mock(ViajeInteresRepository.class);
        when(viajesInterests.findAllByViaje_Id(tripId)).thenReturn(List.of());
        ItinerarioService target = new ItinerarioService(itinerarioRepository, diaRepository, viajeRepository,
                activities, activityInterests, assignments, mock(InteresRepository.class), viajesInterests,
                permissionService, mock(ActivityRecommendationCatalogService.class), external);

        ActividadResponse result = target.agregarRecomendacionExterna(5L, tripId, dayId, externalId, 1);

        assertEquals(3, result.orden());
        verify(assignments, never()).save(any(ItinerarioActividad.class));
    }

    private static DiaItinerario dayWithId(Long id, LocalDate date) throws Exception {
        DiaItinerario day = new DiaItinerario(new Itinerario(
                new Viaje(new User("Owner", "owner@example.invalid", "hash"), "A", "B", date, date, null)), date);
        setField(day, "id", id);
        return day;
    }

    private static Actividad activity(Long id, String name) throws Exception {
        Actividad activity = new Actividad(name, null, null, null, null, null, null, null);
        setField(activity, "id", id);
        return activity;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
