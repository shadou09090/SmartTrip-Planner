package com.project.back_end_proyecto.dto;

import java.util.List;

public record RouteCalculationResponse(List<ModeResult> routes) {
    public record ModeResult(String mode, boolean available, Long durationSeconds, Long distanceMeters,
                             boolean exceedsTwoHours, String message) { }
}
