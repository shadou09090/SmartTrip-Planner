package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.FlightPlaceSuggestion;
import com.project.back_end_proyecto.dto.FlightSearchRequest;
import com.project.back_end_proyecto.dto.FlightSearchResponse;
import com.project.back_end_proyecto.service.flights.DuffelSearchService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Stateless Duffel test endpoints; these never load or persist a trip. */
@RestController
@Profile("local-no-db & !oracle & !prod & !production")
public class LocalDuffelController {
    private final DuffelSearchService duffelSearch;

    public LocalDuffelController(DuffelSearchService duffelSearch) {
        this.duffelSearch = duffelSearch;
    }

    @GetMapping("/api/local/duffel/places")
    public List<FlightPlaceSuggestion> suggestPlaces(@AuthenticationPrincipal Jwt jwt,
                                                       @RequestParam String query) {
        requireAuthenticatedJwt(jwt);
        return duffelSearch.suggestPlaces(query);
    }

    @PostMapping("/api/local/duffel/search")
    public FlightSearchResponse search(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody FlightSearchRequest request) {
        requireAuthenticatedJwt(jwt);
        return duffelSearch.search(request);
    }

    private static void requireAuthenticatedJwt(Jwt jwt) {
        if (jwt == null) {
            throw new org.springframework.security.access.AccessDeniedException("Se requiere autenticación Bearer.");
        }
    }
}
