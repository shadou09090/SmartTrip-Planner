package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.ViajeCreateRequest;
import com.project.back_end_proyecto.dto.ViajeResponse;
import com.project.back_end_proyecto.dto.InteresesViajeRequest;
import com.project.back_end_proyecto.service.ViajeService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/viajes")
@Profile("oracle")
public class ViajeController {

    private final ViajeService viajeService;

    public ViajeController(ViajeService viajeService) {
        this.viajeService = viajeService;
    }

    @PostMapping
    public ResponseEntity<ViajeResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ViajeCreateRequest request
    ) {
        ViajeResponse response = viajeService.create(authenticatedUserId(jwt), request);
        return ResponseEntity.created(URI.create("/api/viajes/" + response.id())).body(response);
    }

    @GetMapping
    public List<ViajeResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return viajeService.listForUser(authenticatedUserId(jwt));
    }

    @GetMapping("/{id}")
    public ViajeResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return viajeService.getForUser(authenticatedUserId(jwt), id);
    }

    @PutMapping("/{id}")
    public ViajeResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody ViajeCreateRequest request
    ) {
        return viajeService.updateForUser(authenticatedUserId(jwt), id, request);
    }

    @PutMapping("/{id}/intereses")
    public List<String> updateInterests(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long id,
            @Valid @RequestBody InteresesViajeRequest request
    ) {
        return viajeService.actualizarIntereses(authenticatedUserId(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        viajeService.deleteForUser(authenticatedUserId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private Long authenticatedUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
