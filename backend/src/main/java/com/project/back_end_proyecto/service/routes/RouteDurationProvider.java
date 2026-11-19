package com.project.back_end_proyecto.service.routes;

import com.project.back_end_proyecto.dto.RouteCalculationRequest;
import com.project.back_end_proyecto.dto.RouteCalculationResponse;

public interface RouteDurationProvider {
    RouteCalculationResponse.ModeResult calculate(RouteCalculationRequest request, RouteTravelMode mode);
}
