package com.project.back_end_proyecto.dto;
import com.project.back_end_proyecto.entity.RolColaborador;
import java.time.LocalDateTime;
public record InvitacionCreatedResponse(Long id, String correo, RolColaborador rol, String invitationToken, LocalDateTime expiresAt) { }
