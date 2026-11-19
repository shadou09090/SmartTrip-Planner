package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.RouteCalculationRequest;
import com.project.back_end_proyecto.dto.RouteCalculationResponse;
import com.project.back_end_proyecto.service.routes.RouteDurationService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("oracle")
public class RouteDurationController {
    private final RouteDurationService service;

    public RouteDurationController(RouteDurationService service) { this.service = service; }

    @PostMapping("/api/viajes/{viajeId}/traslados")
    public RouteCalculationResponse calculate(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId,
                                              @Valid @RequestBody RouteCalculationRequest request) {
        return service.calculate(Long.valueOf(jwt.getSubject()), viajeId, request);
    }
}
