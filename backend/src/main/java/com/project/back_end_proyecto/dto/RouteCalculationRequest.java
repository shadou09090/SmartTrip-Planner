package com.project.back_end_proyecto.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RouteCalculationRequest(@Valid @NotNull RoutePoint origin, @Valid @NotNull RoutePoint destination) { }
