package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.entity.ColaboradorViaje;
import com.project.back_end_proyecto.entity.RolColaborador;
import com.project.back_end_proyecto.entity.Viaje;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.ColaboradorViajeRepository;
import com.project.back_end_proyecto.repository.ViajeRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;

@Service
@Profile("!local-no-db")
public class TripPermissionService {
    private final ViajeRepository viajeRepository;
    private final ColaboradorViajeRepository colaboradorRepository;

    public TripPermissionService(ViajeRepository viajeRepository, ColaboradorViajeRepository colaboradorRepository) {
        this.viajeRepository = viajeRepository;
        this.colaboradorRepository = colaboradorRepository;
    }

    @Transactional(readOnly = true)
    public Viaje requireRead(Long userId, Long tripId) { return requireAccess(userId, tripId, false); }

    @Transactional(readOnly = true)
    public Viaje requireEdit(Long userId, Long tripId) { return requireAccess(userId, tripId, true); }

    @Transactional(readOnly = true)
    public Viaje requireOwner(Long userId, Long tripId) {
        Viaje trip = viajeRepository.findById(tripId).orElseThrow(ResourceNotFoundException::new);
        if (!trip.getUsuario().getId().equals(userId)) throw new ResourceNotFoundException();
        return trip;
    }

    @Transactional(readOnly = true)
    public String accessRole(Long userId, Viaje trip) {
        if (trip.getUsuario().getId().equals(userId)) return "OWNER";
        return colaboradorRepository.findById_ViajeIdAndId_UsuarioId(trip.getId(), userId)
                .map(link -> link.getRol().name()).orElse("NONE");
    }

    @Transactional(readOnly = true)
    public List<Viaje> listAccessible(Long userId) {
        LinkedHashMap<Long, Viaje> trips = new LinkedHashMap<>();
        viajeRepository.findAllByUsuario_IdOrderByFechaInicioDesc(userId).forEach(trip -> trips.put(trip.getId(), trip));
        colaboradorRepository.findAllByUsuario_Id(userId).forEach(link -> trips.put(link.getViaje().getId(), link.getViaje()));
        return trips.values().stream().sorted((left, right) -> right.getFechaInicio().compareTo(left.getFechaInicio())).toList();
    }

    private Viaje requireAccess(Long userId, Long tripId, boolean write) {
        Viaje trip = viajeRepository.findById(tripId).orElseThrow(ResourceNotFoundException::new);
        if (trip.getUsuario().getId().equals(userId)) return trip;
        ColaboradorViaje collaborator = colaboradorRepository.findById_ViajeIdAndId_UsuarioId(tripId, userId)
                .orElseThrow(ResourceNotFoundException::new);
        if (write && collaborator.getRol() != RolColaborador.EDITOR) throw new ResourceNotFoundException();
        return trip;
    }
}
