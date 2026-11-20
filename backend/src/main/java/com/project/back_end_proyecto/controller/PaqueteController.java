package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.packagepersistence.PaqueteGuardarRequest;
import com.project.back_end_proyecto.dto.packagepersistence.PaqueteGuardarResponse;
import com.project.back_end_proyecto.service.packagepersistence.PaquetePersistenciaService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!local-no-db")
@ConditionalOnProperty(name = "smarttrip.package-persistence.enabled", havingValue = "true")
@RequestMapping("/api/viajes/{viajeId}/paquete")
public class PaqueteController {
    private final PaquetePersistenciaService service;

    public PaqueteController(PaquetePersistenciaService service) { this.service = service; }

    private Long userId(Jwt jwt) {
        return jwt != null && jwt.getSubject() != null ? Long.valueOf(jwt.getSubject()) : 1L;
    }

    @PutMapping
    public PaqueteGuardarResponse save(@AuthenticationPrincipal Jwt jwt,
                                       @PathVariable long viajeId,
                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                       @Valid @RequestBody PaqueteGuardarRequest request) {
        return service.guardar(userId(jwt), viajeId, idempotencyKey, request);
    }

    @GetMapping
    public PaqueteGuardarResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable long viajeId) {
        return service.recuperar(userId(jwt), viajeId);
    }
}
