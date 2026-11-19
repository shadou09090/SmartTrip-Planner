package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.dto.InvitacionCreatedResponse;
import com.project.back_end_proyecto.dto.InvitacionRequest;
import com.project.back_end_proyecto.dto.InvitacionResponse;
import com.project.back_end_proyecto.dto.ColaboradorResponse;
import com.project.back_end_proyecto.entity.*;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.repository.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service @Profile("oracle")
public class InvitacionService {
    private final ViajeRepository viajeRepository;
    private final UserRepository userRepository;
    private final ColaboradorViajeRepository colaboradorRepository;
    private final InvitacionRepository invitacionRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    public InvitacionService(ViajeRepository viajes, UserRepository users, ColaboradorViajeRepository collaborators,
                             InvitacionRepository invitations) {
        this.viajeRepository = viajes; this.userRepository = users; this.colaboradorRepository = collaborators;
        this.invitacionRepository = invitations;
    }

    @Transactional
    public InvitacionCreatedResponse crear(Long ownerId, Long tripId, InvitacionRequest request) {
        Viaje trip = viajeRepository.findByIdAndUsuario_Id(tripId, ownerId).orElseThrow(ResourceNotFoundException::new);
        String email = request.correo().trim().toLowerCase(Locale.ROOT);
        User owner = userRepository.findById(ownerId).orElseThrow(ResourceNotFoundException::new);
        if (owner.getEmail().equalsIgnoreCase(email)) throw new IllegalArgumentException("No puedes invitar a la cuenta propietaria.");
        userRepository.findByEmailIgnoreCase(email).ifPresent(invited -> {
            if (colaboradorRepository.existsById_ViajeIdAndId_UsuarioId(tripId, invited.getId()))
                throw new IllegalArgumentException("Este usuario ya es colaborador del viaje.");
        });
        byte[] random = new byte[32];
        String rawToken;
        String tokenHash;
        do {
            secureRandom.nextBytes(random);
            rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            tokenHash = sha256(rawToken);
        } while (invitacionRepository.existsByToken(tokenHash));
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(48);
        Invitacion invitation = invitacionRepository.save(new Invitacion(trip, email, request.rol(), tokenHash, expiresAt));
        // One-time link token is returned only to the authenticated owner and is never logged or stored in plaintext.
        return new InvitacionCreatedResponse(invitation.getId(), email, request.rol(), rawToken, expiresAt);
    }

    @Transactional(readOnly = true)
    public List<InvitacionResponse> listar(Long ownerId, Long tripId) {
        viajeRepository.findByIdAndUsuario_Id(tripId, ownerId).orElseThrow(ResourceNotFoundException::new);
        return invitacionRepository.findAllByViaje_IdOrderByIdDesc(tripId).stream().map(this::toResponse).toList();
    }

    @Transactional(noRollbackFor = ResourceNotFoundException.class)
    public void aceptar(Long userId, String rawToken) {
        User user = userRepository.findById(userId).orElseThrow(ResourceNotFoundException::new);
        Invitacion invitation = invitacionRepository.findByToken(sha256(rawToken)).orElseThrow(ResourceNotFoundException::new);
        validatePending(invitation);
        if (!invitation.getCorreoInvitado().equalsIgnoreCase(user.getEmail())) throw new ResourceNotFoundException();
        if (!colaboradorRepository.existsById_ViajeIdAndId_UsuarioId(invitation.getViaje().getId(), userId))
            colaboradorRepository.save(new ColaboradorViaje(invitation.getViaje(), user, invitation.getRol()));
        invitation.setEstado(EstadoInvitacion.ACEPTADA);
    }

    @Transactional(noRollbackFor = ResourceNotFoundException.class)
    public void rechazar(Long userId, String rawToken) {
        User user = userRepository.findById(userId).orElseThrow(ResourceNotFoundException::new);
        Invitacion invitation = invitacionRepository.findByToken(sha256(rawToken)).orElseThrow(ResourceNotFoundException::new);
        validatePending(invitation);
        if (!invitation.getCorreoInvitado().equalsIgnoreCase(user.getEmail())) throw new ResourceNotFoundException();
        invitation.setEstado(EstadoInvitacion.RECHAZADA);
    }

    @Transactional(readOnly = true)
    public List<InvitacionResponse> pendientes(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(ResourceNotFoundException::new);
        return invitacionRepository.findAllByCorreoInvitadoIgnoreCaseAndEstado(user.getEmail(), EstadoInvitacion.PENDIENTE)
                .stream().filter(invitation -> invitation.getFechaExpiracion().isAfter(LocalDateTime.now()))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ColaboradorResponse> colaboradores(Long ownerId, Long tripId) {
        viajeRepository.findByIdAndUsuario_Id(tripId, ownerId).orElseThrow(ResourceNotFoundException::new);
        return colaboradorRepository.findAllByViaje_Id(tripId).stream()
                .map(link -> new ColaboradorResponse(link.getUsuario().getId(), link.getUsuario().getName(),
                        link.getUsuario().getEmail(), link.getRol())).toList();
    }

    @Transactional
    public void quitarColaborador(Long ownerId, Long tripId, Long userId) {
        viajeRepository.findByIdAndUsuario_Id(tripId, ownerId).orElseThrow(ResourceNotFoundException::new);
        if (!colaboradorRepository.existsById_ViajeIdAndId_UsuarioId(tripId, userId)) throw new ResourceNotFoundException();
        colaboradorRepository.deleteById_ViajeIdAndId_UsuarioId(tripId, userId);
    }

    private void validatePending(Invitacion invitation) {
        if (invitation.getEstado() != EstadoInvitacion.PENDIENTE) throw new ResourceNotFoundException();
        if (!invitation.getFechaExpiracion().isAfter(LocalDateTime.now())) {
            invitation.setEstado(EstadoInvitacion.EXPIRADA);
            throw new ResourceNotFoundException();
        }
    }
    private InvitacionResponse toResponse(Invitacion invitation) {
        return new InvitacionResponse(invitation.getId(), invitation.getCorreoInvitado(), invitation.getRol(), invitation.getEstado(), invitation.getFechaExpiracion());
    }
    private String sha256(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) { throw new IllegalStateException("No se pudo procesar la invitación."); }
    }
}
