package com.project.back_end_proyecto.dto;
import com.project.back_end_proyecto.entity.RolColaborador;
public record ColaboradorResponse(Long usuarioId, String nombre, String correo, RolColaborador rol) { }
