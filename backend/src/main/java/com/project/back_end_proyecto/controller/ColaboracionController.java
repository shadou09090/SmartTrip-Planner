package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.ColaboradorResponse;
import com.project.back_end_proyecto.dto.InvitacionCreatedResponse;
import com.project.back_end_proyecto.dto.InvitacionRequest;
import com.project.back_end_proyecto.dto.InvitacionResponse;
import com.project.back_end_proyecto.service.InvitacionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Profile("oracle")
public class ColaboracionController {
    private final InvitacionService service;
    public ColaboracionController(InvitacionService service) { this.service = service; }

    @GetMapping("/api/viajes/{viajeId}/colaboradores")
    public List<ColaboradorResponse> collaborators(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId) {
        return service.colaboradores(userId(jwt), viajeId);
    }

    @DeleteMapping("/api/viajes/{viajeId}/colaboradores/{usuarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCollaborator(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId, @PathVariable Long usuarioId) {
        service.quitarColaborador(userId(jwt), viajeId, usuarioId);
    }

    @GetMapping("/api/viajes/{viajeId}/invitaciones")
    public List<InvitacionResponse> invitations(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId) {
        return service.listar(userId(jwt), viajeId);
    }

    @PostMapping("/api/viajes/{viajeId}/invitaciones")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitacionCreatedResponse createInvitation(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId,
                                                       @Valid @RequestBody InvitacionRequest request) {
        return service.crear(userId(jwt), viajeId, request);
    }

    @GetMapping("/api/invitaciones/pendientes")
    public List<InvitacionResponse> pending(@AuthenticationPrincipal Jwt jwt) { return service.pendientes(userId(jwt)); }

    @PostMapping("/api/invitaciones/aceptar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InvitationTokenRequest request) {
        service.aceptar(userId(jwt), request.token());
    }

    @PostMapping("/api/invitaciones/rechazar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InvitationTokenRequest request) {
        service.rechazar(userId(jwt), request.token());
    }

    private Long userId(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    public record InvitationTokenRequest(@NotBlank @Size(max = 255) String token) { }
}
