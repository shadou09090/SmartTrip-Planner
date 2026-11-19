package com.project.back_end_proyecto.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AgregarActividadExistenteRequest(@NotNull @Positive Integer orden) { }
