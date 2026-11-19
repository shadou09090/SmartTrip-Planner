package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.DestinoUbicacionResponse;
import com.project.back_end_proyecto.dto.DestinoCandidatoResponse;
import com.project.back_end_proyecto.exception.ResourceNotFoundException;
import com.project.back_end_proyecto.service.DestinoLocationService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@Profile("oracle")
public class DestinoLocationController {
    private final DestinoLocationService service;

    public DestinoLocationController(DestinoLocationService service) { this.service = service; }

    @GetMapping("/api/viajes/{viajeId}/destino/ubicacion")
    public DestinoUbicacionResponse location(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId) {
        return service.ubicar(Long.valueOf(jwt.getSubject()), viajeId).orElseThrow(ResourceNotFoundException::new);
    }

    @GetMapping("/api/destinos/buscar")
    public List<DestinoCandidatoResponse> suggest(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestParam("query") String query) {
        // Authentication is required by the application's stateless API security configuration.
        return service.sugerir(query);
    }
}
