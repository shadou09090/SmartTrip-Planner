package com.project.back_end_proyecto.dto;
import com.project.back_end_proyecto.entity.EstadoInvitacion;
import com.project.back_end_proyecto.entity.RolColaborador;
import java.time.LocalDateTime;
public record InvitacionResponse(Long id, String correo, RolColaborador rol, EstadoInvitacion estado, LocalDateTime expiresAt) { }
