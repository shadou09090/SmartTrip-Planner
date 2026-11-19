package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.ItinerarioResponse;
import com.project.back_end_proyecto.dto.ActividadCreateRequest;
import com.project.back_end_proyecto.dto.ActividadResponse;
import com.project.back_end_proyecto.dto.ActividadRecomendacionResponse;
import com.project.back_end_proyecto.dto.PresupuestoActividadesResponse;
import com.project.back_end_proyecto.entity.Actividad;
import com.project.back_end_proyecto.entity.ActividadInteres;
import com.project.back_end_proyecto.entity.DiaItinerario;
import com.project.back_end_proyecto.entity.Itinerario;
import com.project.back_end_proyecto.entity.ItinerarioActividad;
import com.project.back_end_proyecto.entity.ItinerarioActividadId;
import com.project.back_end_proyecto.entity.Interes;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.ActividadInteresRepository;
import com.project.back_end_proyecto.repository.ActividadRepository;
import com.project.back_end_proyecto.repository.DiaItinerarioRepository;
import com.project.back_end_proyecto.repository.InteresRepository;
import com.project.back_end_proyecto.repository.ItinerarioActividadRepository;
import com.project.back_end_proyecto.repository.ItinerarioRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import com.project.back_end_proyecto.repository.ViajeInteresRepository;
import com.project.back_end_proyecto.service.publicplaces.RelevanceScorer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

@Service
@Profile("oracle")
public class ItinerarioService {
    private final ItinerarioRepository itinerarioRepository;
    private final DiaItinerarioRepository diaRepository;
    private final ViajeRepository viajeRepository;
    private final ActividadRepository actividadRepository;
    private final ActividadInteresRepository actividadInteresRepository;
    private final ItinerarioActividadRepository itinerarioActividadRepository;
    private final InteresRepository interesRepository;
    private final ViajeInteresRepository viajeInteresRepository;
    private final TripPermissionService tripPermissionService;
    private final ActivityRecommendationCatalogService recommendationCatalogService;
    private final ExternalActivityRecommendationService externalRecommendationService;

    public ItinerarioService(ItinerarioRepository itinerarioRepository,
                             DiaItinerarioRepository diaRepository,
                             ViajeRepository viajeRepository,
                             ActividadRepository actividadRepository,
                             ActividadInteresRepository actividadInteresRepository,
                             ItinerarioActividadRepository itinerarioActividadRepository,
                             InteresRepository interesRepository,
                             ViajeInteresRepository viajeInteresRepository,
                             TripPermissionService tripPermissionService,
                             ActivityRecommendationCatalogService recommendationCatalogService,
                             ExternalActivityRecommendationService externalRecommendationService) {
        this.itinerarioRepository = itinerarioRepository;
        this.diaRepository = diaRepository;
        this.viajeRepository = viajeRepository;
        this.actividadRepository = actividadRepository;
        this.actividadInteresRepository = actividadInteresRepository;
        this.itinerarioActividadRepository = itinerarioActividadRepository;
        this.interesRepository = interesRepository;
        this.viajeInteresRepository = viajeInteresRepository;
        this.tripPermissionService = tripPermissionService;
        this.recommendationCatalogService = recommendationCatalogService;
        this.externalRecommendationService = externalRecommendationService;
    }

    @Transactional
    public ItinerarioResponse crearParaViaje(Long authenticatedUserId, Long viajeId) {
        Viaje viaje = tripPermissionService.requireEdit(authenticatedUserId, viajeId);
        Itinerario itinerario = itinerarioRepository.findByViaje_Id(viajeId)
                .orElseGet(() -> itinerarioRepository.save(new Itinerario(viaje)));

        Set<LocalDate> existingDates = new HashSet<>();
        diaRepository.findAllByItinerario_IdOrderByFecha(itinerario.getId())
                .forEach(day -> existingDates.add(day.getFecha()));
        LocalDate fecha = viaje.getFechaInicio();
        while (!fecha.isAfter(viaje.getFechaFin())) {
            if (!existingDates.contains(fecha)) {
                diaRepository.save(new DiaItinerario(itinerario, fecha));
            }
            fecha = fecha.plusDays(1);
        }
        return obtener(itinerario);
    }

    @Transactional
    public ItinerarioResponse generarParaViaje(Long authenticatedUserId, Long viajeId) {
        Viaje viaje = tripPermissionService.requireEdit(authenticatedUserId, viajeId);
        ActivityRecommendationCatalogService.Snapshot snapshot = recommendationCatalogService.load(authenticatedUserId, viajeId);

        crearParaViaje(authenticatedUserId, viajeId);
        Itinerario itinerary = itinerarioRepository.findByViaje_Id(viajeId).orElseThrow(ResourceNotFoundException::new);
        List<DiaItinerario> days = diaRepository.findAllByItinerario_IdOrderByFecha(itinerary.getId());
        if (days.isEmpty()) throw new IllegalStateException("No fue posible crear los días del itinerario.");

        List<ActividadRecomendacionResponse.ActividadRecomendada> catalogCandidates = snapshot.catalogRecommendations().stream()
                .sorted(Comparator.comparingInt((ActividadRecomendacionResponse.ActividadRecomendada item) ->
                                item.interesesCoincidentes() == null ? 0 : item.interesesCoincidentes().size()).reversed()
                        .thenComparing(item -> item.actividad().id()))
                .toList();
        ExternalActivityRecommendationService.Result externalResult = externalRecommendationService.recommend(
                viajeId, snapshot.destination(), snapshot.startDate(), snapshot.endDate(), snapshot.interests());
        int externalOptions = externalResult == null ? 0 : externalResult.recommendations().size();
        Map<Long, Integer> assignmentsPerDay = new HashMap<>();
        Map<Long, Integer> nextOrderPerDay = new HashMap<>();
        Map<Long, Actividad> lastActivityPerDay = new HashMap<>();
        Set<Long> alreadyAssignedActivities = new HashSet<>();
        Set<Long> budgetedActivityIds = new HashSet<>();
        days.forEach(day -> assignmentsPerDay.put(day.getId(), 0));
        BigDecimal spent = BigDecimal.ZERO;
        int activitiesWithoutPrice = 0;
        for (DiaItinerario day : days) {
            List<ItinerarioActividad> existing = itinerarioActividadRepository.findAllByDia_IdOrderByOrden(day.getId());
            assignmentsPerDay.put(day.getId(), existing.size());
            int nextOrder = 1;
            for (ItinerarioActividad assignment : existing) {
                alreadyAssignedActivities.add(assignment.getActividad().getId());
                if (budgetedActivityIds.add(assignment.getActividad().getId())) {
                    BigDecimal existingCost = assignment.getActividad().getCosto();
                    if (existingCost != null) spent = spent.add(existingCost);
                    else activitiesWithoutPrice++;
                }
                if (assignment.getOrden() != null) nextOrder = Math.max(nextOrder, assignment.getOrden() + 1);
            }
            if (!existing.isEmpty()) lastActivityPerDay.put(day.getId(), existing.get(existing.size() - 1).getActividad());
            nextOrderPerDay.put(day.getId(), nextOrder);
        }
        int addedFromCatalog = 0;
        int skippedByBudget = 0;
        Map<Long, Actividad> knownActivities = new LinkedHashMap<>();
        actividadRepository.findAllByOrderByNombreAsc().forEach(activity -> knownActivities.put(activity.getId(), activity));

        for (ActividadRecomendacionResponse.ActividadRecomendada candidate : catalogCandidates) {
            if (alreadyAssignedActivities.contains(candidate.actividad().id())) continue;
            Actividad activity = knownActivities.get(candidate.actividad().id());
            if (activity == null) continue;
            BigDecimal cost = activity.getCosto();
            if (cost != null && viaje.getPresupuesto() != null && spent.add(cost).compareTo(viaje.getPresupuesto()) > 0) {
                skippedByBudget++;
                continue;
            }

            DiaItinerario day = chooseDay(days, activity, assignmentsPerDay, lastActivityPerDay, true);
            if (day == null) continue;

            assign(activity, day, assignmentsPerDay, nextOrderPerDay, lastActivityPerDay, alreadyAssignedActivities);
            if (cost != null) spent = spent.add(cost); else activitiesWithoutPrice++;
            addedFromCatalog++;
        }

        ItinerarioResponse generated = obtener(itinerary);
        String message = addedFromCatalog == 0
                ? "Se generaron los días, pero el catálogo interno no ofreció actividades compatibles para estas fechas e intereses."
                : "Itinerario actualizado con " + addedFromCatalog + " actividades del catálogo interno.";
        if (externalOptions > 0) message += " Hay " + externalOptions
                + " lugares públicos disponibles para que los revises y agregues individualmente; no se guardaron automáticamente y su disponibilidad por fecha no está verificada.";
        else if (externalResult != null && externalResult.warnings().isEmpty()) message += " Las fuentes públicas no devolvieron opciones compatibles.";
        if (skippedByBudget > 0) message += " Se omitieron " + skippedByBudget + " actividades con precio conocido para respetar el presupuesto.";
        if (activitiesWithoutPrice > 0) message += " Hay " + activitiesWithoutPrice
                + " actividades sin precio verificado; no se suman al costo acumulado.";
        if (externalResult != null && !externalResult.warnings().isEmpty()) message += " Fuentes externas: " + String.join(" ", externalResult.warnings());
        return new ItinerarioResponse(generated.id(), generated.viajeId(), generated.fechaCreacion(), generated.dias(), message);
    }

    private static DiaItinerario chooseDay(List<DiaItinerario> days, Actividad activity,
                                           Map<Long, Integer> assignments, Map<Long, Actividad> lastActivity,
                                           boolean requireKnownDateAvailability) {
        return days.stream()
                .filter(day -> !requireKnownDateAvailability || DisponibilidadActividad.disponibleEnFecha(activity, day.getFecha()))
                .min(Comparator.comparingInt((DiaItinerario day) -> assignments.getOrDefault(day.getId(), 0))
                        .thenComparingDouble(day -> distanceFromLast(activity, lastActivity.get(day.getId())))
                        .thenComparing(DiaItinerario::getFecha))
                .orElse(null);
    }

    private static double distanceFromLast(Actividad activity, Actividad previous) {
        if (previous == null || activity.getLatitud() == null || activity.getLongitud() == null
                || previous.getLatitud() == null || previous.getLongitud() == null) return Double.MAX_VALUE;
        return distanceMeters(activity.getLatitud().doubleValue(), activity.getLongitud().doubleValue(),
                previous.getLatitud().doubleValue(), previous.getLongitud().doubleValue());
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6_371_000d;
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void assign(Actividad activity, DiaItinerario day, Map<Long, Integer> assignments,
                        Map<Long, Integer> nextOrder, Map<Long, Actividad> lastActivity, Set<Long> assignedIds) {
        int order = nextOrder.getOrDefault(day.getId(), 1);
        itinerarioActividadRepository.save(new ItinerarioActividad(day, activity, order));
        assignments.merge(day.getId(), 1, Integer::sum);
        nextOrder.put(day.getId(), order + 1);
        lastActivity.put(day.getId(), activity);
        assignedIds.add(activity.getId());
    }

    @Transactional(readOnly = true)
    public Optional<ItinerarioResponse> consultarParaViaje(Long authenticatedUserId, Long viajeId) {
        tripPermissionService.requireRead(authenticatedUserId, viajeId);
        return itinerarioRepository.findByViaje_Id(viajeId).map(this::obtener);
    }

    @Transactional
    public ActividadResponse agregarActividad(Long usuarioId, Long viajeId, Long diaId, ActividadCreateRequest request) {
        tripPermissionService.requireEdit(usuarioId, viajeId);
        DiaItinerario dia = diaRepository.findForUpdateByIdAndTripId(diaId, viajeId)
                .orElseThrow(ResourceNotFoundException::new);
        int order = resolveAppendOrder(diaId, request.orden());
        Actividad actividad = actividadRepository.save(new Actividad(
                request.nombre().trim(), request.descripcion(), request.ubicacion(), request.latitud(), request.longitud(),
                request.costo(), request.fechaDisponibleInicio(), request.fechaDisponibleFin()
        ));
        validarDisponibleEnDia(actividad, dia);
        List<String> activityInterests = request.intereses() == null ? List.of() : request.intereses().stream()
                .map(String::trim).filter(name -> !name.isEmpty()).distinct().toList();
        for (String name : activityInterests) {
            Interes interes = interesRepository.findByNombreIgnoreCase(name)
                    .orElseGet(() -> interesRepository.save(new Interes(name)));
            actividadInteresRepository.save(new ActividadInteres(actividad, interes));
        }
        itinerarioActividadRepository.save(new ItinerarioActividad(dia, actividad, order));
        return toActivityResponse(actividad, order, activityInterests);
    }

    @Transactional
    public ActividadResponse agregarActividadExistente(Long usuarioId, Long viajeId, Long diaId,
                                                        Long actividadId, Integer orden) {
        tripPermissionService.requireEdit(usuarioId, viajeId);
        DiaItinerario dia = diaRepository.findForUpdateByIdAndTripId(diaId, viajeId)
                .orElseThrow(ResourceNotFoundException::new);
        Actividad actividad = actividadRepository.findById(actividadId).orElseThrow(ResourceNotFoundException::new);
        validarDisponibleEnDia(actividad, dia);
        ItinerarioActividad alreadyAssigned = itinerarioActividadRepository
                .findByDia_IdAndActividad_Id(diaId, actividadId).orElse(null);
        if (alreadyAssigned != null) {
            return toActivityResponse(actividad, alreadyAssigned.getOrden(), interesesActividad(actividadId));
        }
        int resolvedOrder = resolveAppendOrder(diaId, orden);
        itinerarioActividadRepository.save(new ItinerarioActividad(dia, actividad, resolvedOrder));
        List<String> tags = actividadInteresRepository.findAllByActividad_Id(actividadId).stream()
                .map(item -> item.getInteres().getNombre()).toList();
        return toActivityResponse(actividad, resolvedOrder, tags);
    }

    @Transactional(readOnly = true)
    public List<ActividadResponse> listarDisponibles(Long usuarioId, Long viajeId) {
        return recommendationCatalogService.listAvailableForTrip(usuarioId, viajeId).stream()
                .map(actividad -> toActivityResponse(actividad, null, interesesActividad(actividad.getId())))
                .toList();
    }

    public ActividadRecomendacionResponse recomendar(Long usuarioId, Long viajeId) {
        ActivityRecommendationCatalogService.Snapshot snapshot = recommendationCatalogService.load(usuarioId, viajeId);
        ExternalActivityRecommendationService.Result external = externalRecommendationService.recommend(
                viajeId, snapshot.destination(), snapshot.startDate(), snapshot.endDate(), snapshot.interests());
        List<String> sourceWarnings = new ArrayList<>(external.warnings());
        if (snapshot.activitiesWithoutVerifiedDestination() > 0) {
            sourceWarnings.add(snapshot.activitiesWithoutVerifiedDestination()
                    + " actividad(es) del catálogo se excluyeron porque su ubicación no pudo confirmarse cerca del destino.");
        }
        return new ActividadRecomendacionResponse(snapshot.requiresInterests(), snapshot.catalogRecommendations(),
                external.recommendations(), List.copyOf(sourceWarnings), external.diagnostics());
    }

    @Transactional
    public ActividadResponse agregarRecomendacionExterna(Long usuarioId, Long viajeId, Long diaId,
                                                          String externalId, Integer orden) {
        Viaje viaje = tripPermissionService.requireEdit(usuarioId, viajeId);
        DiaItinerario dia = diaRepository.findForUpdateByIdAndTripId(diaId, viajeId)
                .orElseThrow(ResourceNotFoundException::new);
        List<String> currentInterests = viajeInteresRepository.findAllByViaje_Id(viajeId).stream()
                .map(association -> association.getInteres().getNombre()).toList();
        ExternalActivityRecommendationService.Selection selection = externalRecommendationService.selection(
                viajeId, externalId, viaje.getDestino(), currentInterests);
        var place = selection.place();
        if (place.nombre().length() > 150 || (place.descripcion() != null && place.descripcion().length() > 500)
                || (place.ubicacion() != null && place.ubicacion().length() > 250)) {
            throw new IllegalArgumentException("El lugar supera los límites de longitud del esquema ACTIVIDAD y no se puede guardar.");
        }
        String normalizedName = RelevanceScorer.normalize(place.nombre());
        String normalizedLocation = RelevanceScorer.normalize(place.ubicacion());
        Actividad activity = actividadRepository.findAllByOrderByNombreAsc().stream()
                .filter(existing -> RelevanceScorer.normalize(existing.getNombre()).equals(normalizedName)
                        && RelevanceScorer.normalize(existing.getUbicacion()).equals(normalizedLocation))
                .findFirst().orElse(null);
        // An HTTP retry after a lost response must be idempotent. Resolve the place first,
        // then return the existing assignment before considering the caller's stale order.
        if (activity != null) {
            ItinerarioActividad existingAssignment = itinerarioActividadRepository
                    .findByDia_IdAndActividad_Id(diaId, activity.getId()).orElse(null);
            if (existingAssignment != null) {
                return toActivityResponse(activity, existingAssignment.getOrden(), interesesActividad(activity.getId()));
            }
        }
        int resolvedOrder = resolveAppendOrder(diaId, orden);
        if (activity == null) {
            activity = actividadRepository.save(new Actividad(place.nombre(), place.descripcion(), place.ubicacion(),
                    place.latitud(), place.longitud(), null, null, null));
        }
        List<String> tags = actividadInteresRepository.findAllByActividad_Id(activity.getId()).stream()
                .map(item -> item.getInteres().getNombre()).toList();
        for (String interestName : selection.matchedInterests()) {
            if (tags.stream().anyMatch(tag -> tag.equalsIgnoreCase(interestName))) continue;
            Interes interest = interesRepository.findByNombreIgnoreCase(interestName).orElse(null);
            if (interest != null) {
                actividadInteresRepository.save(new ActividadInteres(activity, interest));
                tags = new java.util.ArrayList<>(tags);
                tags.add(interestName);
            }
        }
        if (itinerarioActividadRepository.findByDia_IdAndActividad_Id(diaId, activity.getId()).isEmpty()) {
            itinerarioActividadRepository.save(new ItinerarioActividad(dia, activity, resolvedOrder));
        } else {
            ItinerarioActividad existingAssignment = itinerarioActividadRepository
                    .findByDia_IdAndActividad_Id(diaId, activity.getId()).orElseThrow();
            return toActivityResponse(activity, existingAssignment.getOrden(), tags);
        }
        return toActivityResponse(activity, resolvedOrder, tags);
    }

    @Transactional(readOnly = true)
    public PresupuestoActividadesResponse presupuestoActividades(Long usuarioId, Long viajeId) {
        Viaje viaje = tripPermissionService.requireRead(usuarioId, viajeId);
        Map<Long, Actividad> uniqueById = new LinkedHashMap<>();
        itinerarioActividadRepository.findDistinctActivitiesByTripId(viajeId)
                .forEach(activity -> uniqueById.putIfAbsent(activity.getId(), activity));
        List<Actividad> uniqueActivities = List.copyOf(uniqueById.values());
        BigDecimal spent = uniqueActivities.stream().map(Actividad::getCosto).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal budget = viaje.getPresupuesto();
        BigDecimal remaining = budget == null ? null : budget.subtract(spent);
        boolean overBudget = budget != null && spent.compareTo(budget) > 0;
        long activitiesWithoutPrice = uniqueActivities.stream().filter(activity -> activity.getCosto() == null).count();
        BigDecimal percentage = budget == null || budget.signum() <= 0 ? null
                : spent.multiply(BigDecimal.valueOf(100)).divide(budget, 2, java.math.RoundingMode.HALF_UP);
        return new PresupuestoActividadesResponse(budget, spent, remaining, overBudget, activitiesWithoutPrice, percentage);
    }

    @Transactional
    public void reordenarActividad(Long usuarioId, Long viajeId, Long diaId, Long actividadId, Integer order) {
        tripPermissionService.requireEdit(usuarioId, viajeId);
        diaRepository.findForUpdateByIdAndTripId(diaId, viajeId).orElseThrow(ResourceNotFoundException::new);
        List<ItinerarioActividad> assignments = new ArrayList<>(
                itinerarioActividadRepository.findAllByDia_IdOrderByOrden(diaId));
        int currentIndex = -1;
        for (int index = 0; index < assignments.size(); index++) {
            if (assignments.get(index).getActividad().getId().equals(actividadId)) {
                currentIndex = index;
                break;
            }
        }
        if (currentIndex < 0) throw new ResourceNotFoundException();
        if (order == null || order < 1 || order > assignments.size()) {
            throw new IllegalArgumentException("El orden debe corresponder a una posición existente del día.");
        }
        int targetIndex = order - 1;
        if (currentIndex == targetIndex) return;

        ItinerarioActividad moved = assignments.remove(currentIndex);
        assignments.add(targetIndex, moved);
        persistDenseOrder(diaId, assignments);
    }

    @Transactional
    public void moverActividad(Long usuarioId, Long viajeId, Long diaOrigenId, Long actividadId, Long diaDestinoId) {
        tripPermissionService.requireEdit(usuarioId, viajeId);
        // Lock in a stable order to serialize moves with additions and avoid lock inversion.
        Long firstDayId = Math.min(diaOrigenId, diaDestinoId);
        Long secondDayId = Math.max(diaOrigenId, diaDestinoId);
        DiaItinerario firstLocked = diaRepository.findForUpdateByIdAndTripId(firstDayId, viajeId)
                .orElseThrow(ResourceNotFoundException::new);
        DiaItinerario secondLocked = diaRepository.findForUpdateByIdAndTripId(secondDayId, viajeId)
                .orElseThrow(ResourceNotFoundException::new);
        DiaItinerario source = diaOrigenId.equals(firstDayId) ? firstLocked : secondLocked;
        DiaItinerario target = diaDestinoId.equals(firstDayId) ? firstLocked : secondLocked;
        if (diaOrigenId.equals(diaDestinoId)) return;

        ItinerarioActividad assignment = itinerarioActividadRepository
                .findByDia_IdAndActividad_Id(diaOrigenId, actividadId).orElseThrow(ResourceNotFoundException::new);
        validarDisponibleEnDia(assignment.getActividad(), target);
        List<ItinerarioActividad> sourceAssignments = new ArrayList<>(
                itinerarioActividadRepository.findAllByDia_IdOrderByOrden(diaOrigenId));
        List<ItinerarioActividad> targetAssignments = itinerarioActividadRepository
                .findAllByDia_IdOrderByOrden(diaDestinoId);

        itinerarioActividadRepository.deleteById(new ItinerarioActividadId(diaOrigenId, actividadId));
        itinerarioActividadRepository.flush();
        sourceAssignments.removeIf(item -> item.getActividad().getId().equals(actividadId));
        persistDenseOrder(diaOrigenId, sourceAssignments);

        int targetOrder = targetAssignments.stream().map(ItinerarioActividad::getOrden)
                .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
        itinerarioActividadRepository.save(new ItinerarioActividad(target, assignment.getActividad(), targetOrder));
    }

    private void persistDenseOrder(Long dayId, List<ItinerarioActividad> ordered) {
        if (ordered.isEmpty()) return;
        int stagingBase = ordered.stream().map(ItinerarioActividad::getOrden)
                .filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0) + ordered.size() + 1;
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).cambiarOrden(stagingBase + index);
        }
        itinerarioActividadRepository.saveAllAndFlush(ordered);
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).cambiarOrden(index + 1);
        }
        itinerarioActividadRepository.saveAll(ordered);
    }

    @Transactional
    public void eliminarAsignacion(Long usuarioId, Long viajeId, Long diaId, Long actividadId) {
        tripPermissionService.requireEdit(usuarioId, viajeId);
        diaRepository.findForUpdateByIdAndTripId(diaId, viajeId).orElseThrow(ResourceNotFoundException::new);
        ItinerarioActividadId id = new ItinerarioActividadId(diaId, actividadId);
        if (!itinerarioActividadRepository.existsById(id)) throw new ResourceNotFoundException();
        itinerarioActividadRepository.deleteById(id);
        itinerarioActividadRepository.flush();
        List<ItinerarioActividad> remaining = itinerarioActividadRepository.findAllByDia_IdOrderByOrden(diaId);
        persistDenseOrder(diaId, remaining);
    }

    @Transactional(readOnly = true)
    public List<ActividadResponse> listarActividades() {
        return actividadRepository.findAllByOrderByNombreAsc().stream()
                .map(actividad -> toActivityResponse(actividad, null,
                        actividadInteresRepository.findAllByActividad_Id(actividad.getId()).stream()
                                .map(item -> item.getInteres().getNombre()).toList()))
                .toList();
    }

    private List<Actividad> actividadesDisponibles(Viaje viaje) {
        return actividadRepository.findAllDisponiblesEnRango(viaje.getFechaInicio(), viaje.getFechaFin()).stream()
                .filter(actividad -> DisponibilidadActividad.cruzaRango(
                        actividad, viaje.getFechaInicio(), viaje.getFechaFin()))
                .toList();
    }

    private List<String> interesesActividad(Long activityId) {
        return actividadInteresRepository.findAllByActividad_Id(activityId).stream()
                .map(item -> item.getInteres().getNombre()).toList();
    }

    private void validarDisponibleEnDia(Actividad actividad, DiaItinerario dia) {
        if (!DisponibilidadActividad.disponibleEnFecha(actividad, dia.getFecha())) {
            throw new IllegalArgumentException("La actividad no está disponible en la fecha del día seleccionado.");
        }
    }

    private int resolveAppendOrder(Long dayId, Integer requestedOrder) {
        if (requestedOrder == null || requestedOrder < 1) {
            throw new IllegalArgumentException("El orden solicitado debe ser un entero positivo.");
        }
        List<ItinerarioActividad> current = itinerarioActividadRepository.findAllByDia_IdOrderByOrden(dayId);
        boolean requestedIsFree = current.stream().noneMatch(item -> requestedOrder.equals(item.getOrden()));
        if (requestedIsFree) return requestedOrder;
        int append = current.stream().map(ItinerarioActividad::getOrden).filter(java.util.Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
        // The day row is locked by the caller, so concurrent additions serialize here.
        return append;
    }

    private ItinerarioResponse obtener(Itinerario itinerario) {
        List<ItinerarioResponse.DiaResponse> dias = diaRepository
                .findAllByItinerario_IdOrderByFecha(itinerario.getId()).stream()
                .map(dia -> new ItinerarioResponse.DiaResponse(dia.getId(), dia.getFecha(),
                        itinerarioActividadRepository.findAllByDia_IdOrderByOrden(dia.getId()).stream()
                                .map(item -> toActivityResponse(item.getActividad(), item.getOrden(),
                                        actividadInteresRepository.findAllByActividad_Id(item.getActividad().getId()).stream()
                                                .map(interes -> interes.getInteres().getNombre()).toList()))
                                .toList()))
                .toList();
        return new ItinerarioResponse(itinerario.getId(), itinerario.getViaje().getId(),
                itinerario.getFechaCreacion(), dias);
    }

    private ActividadResponse toActivityResponse(Actividad actividad, Integer orden, List<String> intereses) {
        return new ActividadResponse(actividad.getId(), actividad.getNombre(), actividad.getDescripcion(),
                actividad.getUbicacion(), actividad.getLatitud(), actividad.getLongitud(), actividad.getCosto(),
                actividad.getFechaDisponibleInicio(), actividad.getFechaDisponibleFin(), orden, intereses);
    }

    /** Retained for the former dates-only method; it cannot create a trip safely. */
    public Viaje crearItinerarioVacio(LocalDate fechaInicio, LocalDate fechaFin) {
        if (fechaInicio == null || fechaFin == null) {
            throw new IllegalArgumentException("Las fechas de inicio y fin son obligatorias.");
        }
        if (fechaFin.isBefore(fechaInicio)) {
            throw new IllegalArgumentException("La fecha de fin debe ser igual o posterior a la fecha de inicio.");
        }
        throw new IllegalArgumentException(
                "Esta solicitud solo envía fechas; debe asociarse a un viaje existente del usuario autenticado."
        );
    }
}
