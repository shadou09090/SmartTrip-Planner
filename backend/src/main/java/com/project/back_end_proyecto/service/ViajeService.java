package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.ViajeCreateRequest;
import com.project.back_end_proyecto.dto.ViajeResponse;
import com.project.back_end_proyecto.dto.InteresesViajeRequest;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.entity.Interes;
import com.project.back_end_proyecto.entity.ViajeInteres;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.UserRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import com.project.back_end_proyecto.repository.InteresRepository;
import com.project.back_end_proyecto.repository.ViajeInteresRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@Profile("oracle")
public class ViajeService {

    private final ViajeRepository viajeRepository;
    private final UserRepository userRepository;
    private final InteresRepository interesRepository;
    private final ViajeInteresRepository viajeInteresRepository;
    private final TripPermissionService tripPermissionService;
    private final ViajeDeletionService viajeDeletionService;

    public ViajeService(ViajeRepository viajeRepository, UserRepository userRepository,
                        InteresRepository interesRepository, ViajeInteresRepository viajeInteresRepository,
                        TripPermissionService tripPermissionService, ViajeDeletionService viajeDeletionService) {
        this.viajeRepository = viajeRepository;
        this.userRepository = userRepository;
        this.interesRepository = interesRepository;
        this.viajeInteresRepository = viajeInteresRepository;
        this.tripPermissionService = tripPermissionService;
        this.viajeDeletionService = viajeDeletionService;
    }

    @Transactional
    public ViajeResponse create(Long authenticatedUserId, ViajeCreateRequest request) {
        User owner = userRepository.findById(authenticatedUserId)
                .orElseThrow(ResourceNotFoundException::new);
        Viaje viaje = new Viaje(
                owner,
                request.origen().trim(),
                request.destino().trim(),
                request.fechaInicio(),
                request.fechaFin(),
                request.presupuesto()
        );
        Viaje saved = viajeRepository.save(viaje);
        replaceInterests(saved, request.intereses());
        return toResponse(saved, authenticatedUserId);
    }

    @Transactional(readOnly = true)
    public List<ViajeResponse> listForUser(Long authenticatedUserId) {
        return tripPermissionService.listAccessible(authenticatedUserId).stream()
                .map(trip -> toResponse(trip, authenticatedUserId))
                .toList();
    }

    @Transactional(readOnly = true)
    public ViajeResponse getForUser(Long authenticatedUserId, Long viajeId) {
        return toResponse(tripPermissionService.requireRead(authenticatedUserId, viajeId), authenticatedUserId);
    }

    @Transactional
    public ViajeResponse updateForUser(Long authenticatedUserId, Long viajeId, ViajeCreateRequest request) {
        Viaje viaje = tripPermissionService.requireEdit(authenticatedUserId, viajeId);
        viaje.actualizar(
                request.origen().trim(),
                request.destino().trim(),
                request.fechaInicio(),
                request.fechaFin(),
                request.presupuesto()
        );
        if (request.intereses() != null) {
            replaceInterests(viaje, request.intereses());
        }
        return toResponse(viajeRepository.save(viaje), authenticatedUserId);
    }

    @Transactional
    public List<String> actualizarIntereses(Long authenticatedUserId, Long viajeId, InteresesViajeRequest request) {
        Viaje viaje = tripPermissionService.requireEdit(authenticatedUserId, viajeId);
        replaceInterests(viaje, request.intereses());
        return viajeInteresRepository.findAllByViaje_Id(viajeId).stream()
                .map(association -> association.getInteres().getNombre()).toList();
    }

    @Transactional
    public void deleteForUser(Long authenticatedUserId, Long viajeId) {
        viajeDeletionService.deleteOwnedTrip(authenticatedUserId, viajeId);
    }

    private ViajeResponse toResponse(Viaje viaje, Long userId) {
        List<String> interestNames = viaje.getId() == null ? List.of() : viajeInteresRepository.findAllByViaje_Id(viaje.getId()).stream()
                .map(association -> association.getInteres().getNombre())
                .toList();
        return new ViajeResponse(
                viaje.getId(),
                viaje.getOrigen(),
                viaje.getDestino(),
                viaje.getFechaInicio(),
                viaje.getFechaFin(),
                viaje.getPresupuesto(),
                interestNames,
                tripPermissionService.accessRole(userId, viaje)
        );
    }

    private void replaceInterests(Viaje viaje, List<String> requestedNames) {
        viajeInteresRepository.deleteAllByViaje_Id(viaje.getId());
        if (requestedNames == null) return;
        Map<String, String> uniqueNames = new LinkedHashMap<>();
        requestedNames.stream().map(String::trim).filter(name -> !name.isEmpty())
                .forEach(name -> uniqueNames.putIfAbsent(name.toLowerCase(Locale.ROOT), name));
        uniqueNames.values().forEach(name -> {
            Interes interes = interesRepository.findByNombreIgnoreCase(name)
                    .orElseGet(() -> interesRepository.save(new Interes(name)));
            viajeInteresRepository.save(new ViajeInteres(viaje, interes));
        });
    }
}
