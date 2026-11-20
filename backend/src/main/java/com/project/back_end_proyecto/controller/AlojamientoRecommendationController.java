package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.AlojamientoSugerenciaResponse;
import com.project.back_end_proyecto.dto.DestinoUbicacionResponse;
import com.project.back_end_proyecto.service.AlojamientoRecommendationService;
import com.project.back_end_proyecto.service.OBOHotelPriceService;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;

import java.util.List;

@RestController
@Profile("!local-no-db")
public class AlojamientoRecommendationController {
    private final AlojamientoRecommendationService service;
    private final OBOHotelPriceService priceService;

    public AlojamientoRecommendationController(AlojamientoRecommendationService service, OBOHotelPriceService priceService) {
        this.service = service;
        this.priceService = priceService;
    }

    private Long userId(Jwt jwt) {
        return jwt != null && jwt.getSubject() != null ? Long.valueOf(jwt.getSubject()) : 1L;
    }

    @GetMapping("/api/viajes/{viajeId}/alojamientos/sugerencias")
    public List<AlojamientoSugerenciaResponse> suggestions(@AuthenticationPrincipal Jwt jwt,
                                                            @PathVariable Long viajeId) {
        return service.buscar(userId(jwt), viajeId);
    }

    @GetMapping("/api/viajes/{viajeId}/alojamiento/ubicacion")
    public ResponseEntity<DestinoUbicacionResponse> lodgingLocation(@AuthenticationPrincipal Jwt jwt,
                                                                     @PathVariable Long viajeId,
                                                                     @RequestParam String query) {
        return service.ubicarAlojamiento(userId(jwt), viajeId, query)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping("/api/viajes/{viajeId}/alojamientos/tarifas")
    public com.project.back_end_proyecto.dto.HotelPriceSearchResponse livePrices(@AuthenticationPrincipal Jwt jwt,
            @PathVariable Long viajeId, @RequestParam(defaultValue = "1") int guests,
            @RequestParam(defaultValue = "1") int rooms, @RequestParam(defaultValue = "COP") String currency) {
        return priceService.search(userId(jwt), viajeId, guests, rooms, currency);
    }
}
