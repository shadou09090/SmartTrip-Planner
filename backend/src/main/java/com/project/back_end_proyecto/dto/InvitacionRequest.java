package com.project.back_end_proyecto.dto;
import com.project.back_end_proyecto.entity.RolColaborador;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record InvitacionRequest(@Email @NotNull @Size(max = 150) String correo, @NotNull RolColaborador rol) { }
