package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.CrearViajeRequest;
import com.project.back_end_proyecto.dto.InteresesViajeRequest;
import com.project.back_end_proyecto.dto.ViajeCreateRequest;
import com.project.back_end_proyecto.dto.ViajeResponse;
import com.project.back_end_proyecto.entity.Interes;
import com.project.back_end_proyecto.entity.User;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.entity.ViajeInteres;
import com.project.back_end_proyecto.exception.DatosInvalidosException;
import com.project.back_end_proyecto.exception.RecursoNoEncontradoException;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.InteresRepository;
import com.project.back_end_proyecto.repository.UserRepository;
import com.project.back_end_proyecto.repository.ViajeInteresRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Profile("!local-no-db")
public class ViajeService {

    private final ViajeRepository viajeRepository;
    private final UserRepository userRepository;
    private final InteresRepository interesRepository;
    private final ViajeInteresRepository viajeInteresRepository;
    private final TripPermissionService tripPermissionService;
    private final ViajeDeletionService viajeDeletionService;

    public ViajeService(ViajeRepository viajeRepository) {
        this(viajeRepository, null, null, null, null, null);
    }

    @Autowired
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

    @Transactional
    public ViajeResponse crear(CrearViajeRequest request) {
        if (request.getFechaFin().isBefore(request.getFechaInicio())) {
            throw new DatosInvalidosException("La fecha de fin no puede ser anterior a la de inicio");
        }

        Viaje viaje = new Viaje(request.getFechaInicio(), request.getFechaFin());
        viaje.setDestino(request.getDestino() != null ? request.getDestino().trim() : "");
        viaje.setPresupuesto(request.getPresupuesto());

        List<String> intereses = new ArrayList<>();
        if (request.getIntereses() != null) {
            for (String interes : request.getIntereses()) {
                intereses.add(interes.trim());
            }
        }
        viaje.setIntereses(intereses);

        Viaje saved = viajeRepository.save(viaje);
        return new ViajeResponse(
                saved.getId(),
                saved.getDestino(),
                saved.getFechaInicio(),
                saved.getFechaFin(),
                saved.getPresupuesto(),
                new ArrayList<>(saved.getIntereses())
        );
    }

    @Transactional(readOnly = true)
    public ViajeResponse obtener(Long id) {
        Viaje viaje = viajeRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el viaje " + id));
        return new ViajeResponse(
                viaje.getId(),
                viaje.getDestino(),
                viaje.getFechaInicio(),
                viaje.getFechaFin(),
                viaje.getPresupuesto(),
                new ArrayList<>(viaje.getIntereses())
        );
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
        List<String> interestNames = (viaje.getId() == null || viajeInteresRepository == null)
                ? (viaje.getIntereses() != null ? viaje.getIntereses() : List.of())
                : viajeInteresRepository.findAllByViaje_Id(viaje.getId()).stream()
                .map(association -> association.getInteres().getNombre())
                .toList();
        String role = (tripPermissionService != null && userId != null)
                ? tripPermissionService.accessRole(userId, viaje)
                : "OWNER";
        return new ViajeResponse(
                viaje.getId(),
                viaje.getOrigen(),
                viaje.getDestino(),
                viaje.getFechaInicio(),
                viaje.getFechaFin(),
                viaje.getPresupuesto(),
                interestNames,
                role
        );
    }

    private void replaceInterests(Viaje viaje, List<String> requestedNames) {
        if (viajeInteresRepository == null || interesRepository == null) {
            return;
        }
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
