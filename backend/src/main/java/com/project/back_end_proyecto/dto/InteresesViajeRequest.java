package com.project.back_end_proyecto.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record InteresesViajeRequest(
        @NotEmpty(message = "Selecciona al menos un interés.")
        @Size(max = 30, message = "No puedes seleccionar más de 30 intereses.")
        List<@NotBlank @Size(max = 100) String> intereses
) { }
