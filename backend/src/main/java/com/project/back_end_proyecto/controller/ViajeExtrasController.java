package com.project.back_end_proyecto.controller;

import com.project.back_end_proyecto.dto.*;
import com.project.back_end_proyecto.service.flights.FlightSearchService;
import com.project.back_end_proyecto.service.ViajeExtrasService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@Profile("!local-no-db")
public class ViajeExtrasController {
    private final ViajeExtrasService service;
    private final FlightSearchService flightSearchService;
    public ViajeExtrasController(ViajeExtrasService service, FlightSearchService flightSearchService) {
        this.service = service;
        this.flightSearchService = flightSearchService;
    }

    @GetMapping("/api/viajes/{viajeId}/alojamiento")
    public ResponseEntity<AlojamientoResponse> getLodging(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId) {
        return service.alojamiento(userId(jwt), viajeId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/api/viajes/{viajeId}/alojamiento")
    public AlojamientoResponse putLodging(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId,
                                          @Valid @RequestBody AlojamientoRequest request) {
        return service.guardarAlojamiento(userId(jwt), viajeId, request);
    }

    @GetMapping("/api/viajes/{viajeId}/vuelos")
    public List<VueloResponse> getFlights(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId) {
        return service.vuelos(userId(jwt), viajeId);
    }

    @GetMapping("/api/viajes/{viajeId}/vuelos/lugares")
    public List<FlightPlaceSuggestion> suggestFlightPlaces(@AuthenticationPrincipal Jwt jwt,
                                                           @PathVariable Long viajeId,
                                                           @RequestParam String query) {
        return flightSearchService.suggestPlaces(userId(jwt), viajeId, query);
    }

    @PostMapping("/api/viajes/{viajeId}/vuelos/buscar")
    public FlightSearchResponse searchFlights(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable Long viajeId,
                                              @Valid @RequestBody FlightSearchRequest request) {
        return flightSearchService.search(userId(jwt), viajeId, request);
    }

    @PostMapping("/api/viajes/{viajeId}/vuelos")
    public ResponseEntity<VueloResponse> createFlight(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId,
                                                       @Valid @RequestBody VueloRequest request) {
        VueloResponse response = service.crearVuelo(userId(jwt), viajeId, request);
        return ResponseEntity.created(URI.create("/api/viajes/" + viajeId + "/vuelos/" + response.id())).body(response);
    }

    @PostMapping("/api/viajes/{viajeId}/vuelos/ofertas")
    public ResponseEntity<VueloResponse> saveVerifiedDuffelOffer(@AuthenticationPrincipal Jwt jwt,
                                                                  @PathVariable Long viajeId,
                                                                  @Valid @RequestBody DuffelOfferSaveRequest request) {
        FlightSearchResponse.Offer offer = flightSearchService.verifyOfferForPersistence(userId(jwt), viajeId, request.offerId());
        FlightSearchResponse.Segment segment = offer.slices().get(0).segments().get(0);
        String airline = segment.marketingCarrier() != null ? segment.marketingCarrier() : segment.operatingCarrier();
        VueloRequest verifiedRequest = new VueloRequest(segment.origin(), segment.destination(),
                parseDuffelDateTime(segment.departureAt()), parseDuffelDateTime(segment.arrivalAt()), airline,
                segment.flightNumber(), offer.totalAmount());
        VueloResponse response = service.crearVuelo(userId(jwt), viajeId, verifiedRequest);
        return ResponseEntity.created(URI.create("/api/viajes/" + viajeId + "/vuelos/" + response.id())).body(response);
    }

    @PutMapping("/api/viajes/{viajeId}/vuelos/{vueloId}")
    public VueloResponse updateFlight(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId,
                                      @PathVariable Long vueloId, @Valid @RequestBody VueloRequest request) {
        return service.actualizarVuelo(userId(jwt), viajeId, vueloId, request);
    }

    @GetMapping("/api/viajes/{viajeId}/vuelos/{vueloId}/historial-precios")
    public List<HistorialPrecioResponse> priceHistory(@AuthenticationPrincipal Jwt jwt, @PathVariable Long viajeId,
                                                       @PathVariable Long vueloId) {
        return service.historial(userId(jwt), viajeId, vueloId);
    }

    private Long userId(Jwt jwt) {
        return jwt != null && jwt.getSubject() != null ? Long.valueOf(jwt.getSubject()) : 1L;
    }

    private static LocalDateTime parseDuffelDateTime(String value) {
        try { return OffsetDateTime.parse(value).toLocalDateTime(); }
        catch (DateTimeParseException ignored) {
            try { return LocalDateTime.parse(value); }
            catch (DateTimeParseException exception) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_GATEWAY,
                        "Duffel devolvió un horario que no se puede guardar en el esquema Oracle actual.");
            }
        }
    }
}
