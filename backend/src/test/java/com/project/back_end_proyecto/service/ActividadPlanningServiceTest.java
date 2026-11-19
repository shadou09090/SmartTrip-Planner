package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.ActividadCreateRequest;
import com.project.back_end_proyecto.entity.Actividad;
import com.project.back_end_proyecto.entity.ActividadInteres;
import com.project.back_end_proyecto.entity.DiaItinerario;
import com.project.back_end_proyecto.entity.Interes;
import com.project.back_end_proyecto.entity.Itinerario;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.entity.ViajeInteres;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.ActividadInteresRepository;
import com.project.back_end_proyecto.repository.ActividadRepository;
import com.project.back_end_proyecto.repository.DiaItinerarioRepository;
import com.project.back_end_proyecto.repository.InteresRepository;
import com.project.back_end_proyecto.repository.ItinerarioActividadRepository;
import com.project.back_end_proyecto.repository.ItinerarioRepository;
import com.project.back_end_proyecto.repository.ViajeInteresRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import com.project.back_end_proyecto.service.publicplaces.CoordenadasPublicas;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActividadPlanningServiceTest {
    private static final Long USER_ID = 5L;
    private static final Long TRIP_ID = 12L;
    private static final LocalDate TRIP_START = LocalDate.of(2026, 6, 10);
    private static final LocalDate TRIP_END = LocalDate.of(2026, 6, 14);

    private ActividadRepository actividadRepository;
    private ActividadInteresRepository actividadInteresRepository;
    private ViajeInteresRepository viajeInteresRepository;
    private ItinerarioActividadRepository assignmentRepository;
    private DiaItinerarioRepository dayRepository;
    private TripPermissionService permissions;
    private ItinerarioService service;
    private Viaje trip;

    @BeforeEach
    void setUp() {
        actividadRepository = mock(ActividadRepository.class);
        actividadInteresRepository = mock(ActividadInteresRepository.class);
        viajeInteresRepository = mock(ViajeInteresRepository.class);
        assignmentRepository = mock(ItinerarioActividadRepository.class);
        dayRepository = mock(DiaItinerarioRepository.class);
        permissions = mock(TripPermissionService.class);
        FuenteLugaresPublicos geocoder = mock(FuenteLugaresPublicos.class);
        when(geocoder.geocodificarDestino(anyString())).thenReturn(Optional.of(
                new CoordenadasPublicas(new BigDecimal("3.4516"), new BigDecimal("-76.5320"))));
        var catalogService = new ActivityRecommendationCatalogService(permissions, viajeInteresRepository,
                actividadRepository, actividadInteresRepository, geocoder);
        var externalService = mock(ExternalActivityRecommendationService.class);
        when(externalService.recommend(any(), any(), any(), any(), any()))
                .thenReturn(new ExternalActivityRecommendationService.Result(List.of(), List.of()));
        trip = new Viaje(new User("Owner", "owner@example.invalid", "hash"), "Bogotá", "Cali",
                TRIP_START, TRIP_END, new BigDecimal("100.00"));
        service = new ItinerarioService(mock(ItinerarioRepository.class), dayRepository, mock(ViajeRepository.class),
                actividadRepository, actividadInteresRepository, assignmentRepository, mock(InteresRepository.class),
                viajeInteresRepository, permissions, catalogService, externalService);
        when(permissions.requireRead(USER_ID, TRIP_ID)).thenReturn(trip);
        when(permissions.requireEdit(USER_ID, TRIP_ID)).thenReturn(trip);
    }

    @Test
    void availableActivitiesUseAuthorizedTripRangeAndReturnEmptyWhenNoneMatch() {
        when(actividadRepository.findAllDisponiblesEnRango(TRIP_START, TRIP_END)).thenReturn(List.of());

        assertTrue(service.listarDisponibles(USER_ID, TRIP_ID).isEmpty());
        verify(permissions).requireRead(USER_ID, TRIP_ID);
        verify(actividadRepository).findAllDisponiblesEnRango(TRIP_START, TRIP_END);
    }

    @Test
    void recommendationsReturnActivitiesSharingAtLeastOneTripInterest() {
        Interes interest = interest(30L, "Naturaleza");
        ViajeInteres tripInterest = mock(ViajeInteres.class);
        when(tripInterest.getInteres()).thenReturn(interest);
        when(viajeInteresRepository.findAllByViaje_Id(TRIP_ID)).thenReturn(List.of(tripInterest));
        Actividad activity = activity(70L, TRIP_START.minusDays(1), TRIP_START);
        when(activity.getLatitud()).thenReturn(new BigDecimal("3.4516"));
        when(activity.getLongitud()).thenReturn(new BigDecimal("-76.5320"));
        when(actividadRepository.findAllDisponiblesEnRango(TRIP_START, TRIP_END)).thenReturn(List.of(activity));
        ActividadInteres activityInterest = activityInterest(activity, interest);
        when(actividadInteresRepository.findAllByActividad_IdIn(List.of(70L))).thenReturn(List.of(activityInterest));
        when(actividadInteresRepository.findAllByActividad_Id(70L)).thenReturn(List.of(activityInterest));

        var result = service.recomendar(USER_ID, TRIP_ID);

        assertFalse(result.requiereSeleccionarIntereses());
        assertEquals(1, result.actividades().size());
        assertEquals(List.of("Naturaleza"), result.actividades().get(0).interesesCoincidentes());
        verify(actividadRepository, never()).save(any());
    }

    @Test
    void recommendationWithNoMatchingInterestsIsEmpty() {
        Interes tripInterestValue = interest(30L, "Naturaleza");
        Interes different = interest(31L, "Historia");
        ViajeInteres tripInterest = mock(ViajeInteres.class);
        when(tripInterest.getInteres()).thenReturn(tripInterestValue);
        when(viajeInteresRepository.findAllByViaje_Id(TRIP_ID)).thenReturn(List.of(tripInterest));
        Actividad activity = activity(70L, null, null);
        when(actividadRepository.findAllDisponiblesEnRango(TRIP_START, TRIP_END)).thenReturn(List.of(activity));
        ActividadInteres nonMatchingAssociation = activityInterest(activity, different);
        when(actividadInteresRepository.findAllByActividad_IdIn(List.of(70L))).thenReturn(List.of(nonMatchingAssociation));

        var result = service.recomendar(USER_ID, TRIP_ID);

        assertFalse(result.requiereSeleccionarIntereses());
        assertTrue(result.actividades().isEmpty());
    }

    @Test
    void tripWithoutInterestsRequestsSelectionAndDoesNotQueryActivityCatalog() {
        when(viajeInteresRepository.findAllByViaje_Id(TRIP_ID)).thenReturn(List.of());

        var result = service.recomendar(USER_ID, TRIP_ID);

        assertTrue(result.requiereSeleccionarIntereses());
        assertTrue(result.actividades().isEmpty());
        verifyNoInteractions(actividadRepository);
    }

    @Test
    void recommendationStillAppliesTheDateOverlapFilter() {
        Interes interest = interest(30L, "Naturaleza");
        ViajeInteres tripInterest = mock(ViajeInteres.class);
        when(tripInterest.getInteres()).thenReturn(interest);
        when(viajeInteresRepository.findAllByViaje_Id(TRIP_ID)).thenReturn(List.of(tripInterest));
        Actividad outsideTrip = activity(70L, TRIP_END.plusDays(1), TRIP_END.plusDays(2));
        // The service repeats the date predicate after the repository query as a defensive boundary check.
        when(actividadRepository.findAllDisponiblesEnRango(TRIP_START, TRIP_END)).thenReturn(List.of(outsideTrip));

        var result = service.recomendar(USER_ID, TRIP_ID);

        assertTrue(result.actividades().isEmpty());
        verifyNoInteractions(actividadInteresRepository);
    }

    @Test
    void catalogRecommendationsExcludeActivitiesOutsideVerifiedDestinationOrWithoutCoordinates() {
        Interes interest = interest(30L, "Cultura");
        ViajeInteres tripInterest = mock(ViajeInteres.class);
        when(tripInterest.getInteres()).thenReturn(interest);
        when(viajeInteresRepository.findAllByViaje_Id(TRIP_ID)).thenReturn(List.of(tripInterest));
        Actividad parisActivity = activityWithCoordinates(70L, new BigDecimal("48.86"), new BigDecimal("2.35"));
        Actividad puertoColombia = activityWithCoordinates(71L, new BigDecimal("11.01"), new BigDecimal("-74.96"));
        Actividad missingLocation = activityWithCoordinates(72L, null, null);
        when(actividadRepository.findAllDisponiblesEnRango(TRIP_START, TRIP_END))
                .thenReturn(List.of(parisActivity, puertoColombia, missingLocation));
        ActividadInteres matching = activityInterest(parisActivity, interest);
        when(actividadInteresRepository.findAllByActividad_IdIn(List.of(70L))).thenReturn(List.of(matching));
        when(actividadInteresRepository.findAllByActividad_Id(70L)).thenReturn(List.of(matching));
        FuenteLugaresPublicos geocoder = mock(FuenteLugaresPublicos.class);
        Viaje parisTrip = new Viaje(trip.getUsuario(), "Londres", "Paris, France", TRIP_START, TRIP_END,
                new BigDecimal("100.00"));
        when(permissions.requireRead(USER_ID, TRIP_ID)).thenReturn(parisTrip);
        when(geocoder.geocodificarDestino("Paris, France")).thenReturn(Optional.of(
                new CoordenadasPublicas(new BigDecimal("48.8566"), new BigDecimal("2.3522"))));
        var geographicallyScoped = new ActivityRecommendationCatalogService(permissions, viajeInteresRepository,
                actividadRepository, actividadInteresRepository, geocoder);

        var snapshot = geographicallyScoped.load(USER_ID, TRIP_ID);

        assertEquals(1, snapshot.catalogRecommendations().size());
        assertEquals(70L, snapshot.catalogRecommendations().get(0).actividad().id());
        assertEquals(2, snapshot.activitiesWithoutVerifiedDestination());
        verify(actividadInteresRepository).findAllByActividad_IdIn(List.of(70L));
    }

    @Test
    void addingActivityAndRefreshingSummaryReportsTheUpdatedTotal() {
        DiaItinerario day = day(TRIP_START);
        when(dayRepository.findForUpdateByIdAndTripId(4L, TRIP_ID)).thenReturn(Optional.of(day));
        when(actividadRepository.save(any(Actividad.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(assignmentRepository.findByDia_IdAndOrden(4L, 1)).thenReturn(Optional.empty());
        when(actividadInteresRepository.findAllByActividad_Id(any())).thenReturn(List.of());
        Actividad costed = pricedActivity(80L, new BigDecimal("25.50"));
        when(assignmentRepository.findDistinctActivitiesByTripId(TRIP_ID)).thenReturn(List.of(costed));

        service.agregarActividad(USER_ID, TRIP_ID, 4L,
                new ActividadCreateRequest("Visita", null, null, null, null, new BigDecimal("25.50"),
                        null, null, 1, List.of()));
        var summary = service.presupuestoActividades(USER_ID, TRIP_ID);

        verify(assignmentRepository).save(any());
        assertEquals(new BigDecimal("25.50"), summary.costoAcumulado());
        assertEquals(new BigDecimal("74.50"), summary.presupuestoRestante());
        assertEquals(new BigDecimal("25.50"), summary.porcentajeUtilizado());
    }

    @Test
    void deletingActivityAssignmentRecalculatesCostWithoutDeletingActivity() {
        DiaItinerario day = day(TRIP_START);
        when(dayRepository.findForUpdateByIdAndTripId(4L, TRIP_ID)).thenReturn(Optional.of(day));
        when(assignmentRepository.existsById(any())).thenReturn(true);
        when(assignmentRepository.findDistinctActivitiesByTripId(TRIP_ID)).thenReturn(List.of());

        service.eliminarAsignacion(USER_ID, TRIP_ID, 4L, 70L);
        var summary = service.presupuestoActividades(USER_ID, TRIP_ID);

        verify(assignmentRepository).deleteById(any());
        assertEquals(BigDecimal.ZERO, summary.costoAcumulado());
    }

    @Test
    void exceedingBudgetIsReportedButExactBudgetIsNotExceeded() {
        Actividad overBudget = pricedActivity(81L, new BigDecimal("100.01"));
        when(assignmentRepository.findDistinctActivitiesByTripId(TRIP_ID)).thenReturn(List.of(overBudget));
        assertTrue(service.presupuestoActividades(USER_ID, TRIP_ID).superaPresupuesto());

        Actividad exactBudget = pricedActivity(82L, new BigDecimal("100.00"));
        when(assignmentRepository.findDistinctActivitiesByTripId(TRIP_ID)).thenReturn(List.of(exactBudget));
        var exact = service.presupuestoActividades(USER_ID, TRIP_ID);
        assertFalse(exact.superaPresupuesto());
        assertEquals(0, BigDecimal.ZERO.compareTo(exact.presupuestoRestante()));
    }

    @Test
    void nullActivityCostsProduceZeroAndNullBudgetMeansUnbounded() {
        when(assignmentRepository.findDistinctActivitiesByTripId(TRIP_ID)).thenReturn(List.of());
        assertEquals(BigDecimal.ZERO, service.presupuestoActividades(USER_ID, TRIP_ID).costoAcumulado());

        Viaje noBudget = new Viaje(trip.getUsuario(), "Bogotá", "Cali", TRIP_START, TRIP_END, null);
        when(permissions.requireRead(USER_ID, TRIP_ID)).thenReturn(noBudget);
        var summary = service.presupuestoActividades(USER_ID, TRIP_ID);
        assertNull(summary.presupuestoRestante());
        assertFalse(summary.superaPresupuesto());
    }

    @Test
    void addingExistingActivityOutsideSelectedDayIsRejected() {
        DiaItinerario day = day(TRIP_START);
        Actividad unavailable = activity(70L, TRIP_START.plusDays(1), TRIP_END);
        when(dayRepository.findForUpdateByIdAndTripId(4L, TRIP_ID)).thenReturn(Optional.of(day));
        when(actividadRepository.findById(70L)).thenReturn(Optional.of(unavailable));

        assertThrows(IllegalArgumentException.class,
                () -> service.agregarActividadExistente(USER_ID, TRIP_ID, 4L, 70L, 1));
        verify(assignmentRepository, never()).save(any());
    }

    private static Actividad activity(Long id, LocalDate start, LocalDate end) {
        Actividad activity = mock(Actividad.class);
        when(activity.getId()).thenReturn(id);
        when(activity.getFechaDisponibleInicio()).thenReturn(start);
        when(activity.getFechaDisponibleFin()).thenReturn(end);
        when(activity.getNombre()).thenReturn("Actividad " + id);
        return activity;
    }

    private static Actividad activityWithCoordinates(Long id, BigDecimal latitude, BigDecimal longitude) {
        Actividad activity = activity(id, null, null);
        when(activity.getLatitud()).thenReturn(latitude);
        when(activity.getLongitud()).thenReturn(longitude);
        return activity;
    }

    private static Actividad pricedActivity(Long id, BigDecimal cost) {
        Actividad activity = new Actividad("Costed", null, null, null, null, cost, null, null);
        try {
            var field = Actividad.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(activity, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
        return activity;
    }

    private static Interes interest(Long id, String name) {
        Interes interest = mock(Interes.class);
        when(interest.getId()).thenReturn(id);
        when(interest.getNombre()).thenReturn(name);
        return interest;
    }

    private static ActividadInteres activityInterest(Actividad activity, Interes interest) {
        ActividadInteres association = mock(ActividadInteres.class);
        when(association.getActividad()).thenReturn(activity);
        when(association.getInteres()).thenReturn(interest);
        return association;
    }

    private static DiaItinerario day(LocalDate date) {
        DiaItinerario day = mock(DiaItinerario.class);
        when(day.getFecha()).thenReturn(date);
        return day;
    }
}
