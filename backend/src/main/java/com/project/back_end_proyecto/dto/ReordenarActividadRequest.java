package com.project.back_end_proyecto.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
public record ReordenarActividadRequest(@NotNull @Positive Integer orden) { }
