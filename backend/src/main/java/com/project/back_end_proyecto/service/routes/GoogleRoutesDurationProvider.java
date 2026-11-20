package com.project.back_end_proyecto.service.routes;

import com.project.back_end_proyecto.dto.RouteCalculationRequest;
import com.project.back_end_proyecto.dto.RouteCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.Map;

/**
 * Legacy, manually constructible Google adapter retained for compatibility with
 * local work.
 * It is deliberately not a Spring component; the oracle profile uses
 * OpenRouteServiceDurationProvider.
 */
@Deprecated
public class GoogleRoutesDurationProvider implements RouteDurationProvider {
    private static final Logger log = LoggerFactory.getLogger(GoogleRoutesDurationProvider.class);
    private static final String BASE_URL = "https://routes.googleapis.com";
    private static final String ROUTE_PATH = "/directions/v2:computeRoutes";
    private static final String FIELD_MASK = "routes.duration,routes.distanceMeters";
    private final RestClient client;
    private final String apiKey;

    public GoogleRoutesDurationProvider(RestClient.Builder builder, String baseUrl, String apiKey) {
        var requestFactory = com.project.back_end_proyecto.config.HttpRequestFactoryHelper.create(3000, 10000);
        this.client = builder.clone().baseUrl(baseUrl == null ? BASE_URL : baseUrl).requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    GoogleRoutesDurationProvider(RestClient client, String apiKey) {
        this.client = client;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public RouteCalculationResponse.ModeResult calculate(RouteCalculationRequest request, RouteTravelMode mode) {
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cálculo de rutas Google sin configurar: define SMARTTRIP_GOOGLE_MAPS_API_KEY.");
        }
        Map<String, Object> payload = Map.of(
                "origin",
                location(request.origin().latitude().doubleValue(), request.origin().longitude().doubleValue()),
                "destination",
                location(request.destination().latitude().doubleValue(),
                        request.destination().longitude().doubleValue()),
                "travelMode", mode.name());
        try {
            JsonNode root = client.post().uri(ROUTE_PATH)
                    .header("X-Goog-Api-Key", apiKey)
                    .header("X-Goog-FieldMask", FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(JsonNode.class);
            JsonNode route = root == null ? null : root.path("routes").path(0);
            if (route == null || route.isMissingNode()) {
                return new RouteCalculationResponse.ModeResult(mode.name(), false, null, null, false,
                        "No se encontró una ruta para este medio en esta región.");
            }
            long seconds = parseDurationSeconds(route.path("duration").asText());
            long meters = route.path("distanceMeters").asLong(-1L);
            boolean longRoute = seconds > Duration.ofMinutes(120).toSeconds();
            return new RouteCalculationResponse.ModeResult(mode.name(), true, seconds, meters < 0 ? null : meters,
                    longRoute, longRoute ? "El traslado supera los 120 minutos." : null);
        } catch (RestClientResponseException exception) {
            log.warn("Legacy Google Routes returned HTTP {} for travel mode {}", exception.getStatusCode().value(),
                    mode);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Routes no pudo calcular el traslado.");
        } catch (RestClientException exception) {
            log.warn("Legacy Google Routes request failed: exceptionType={}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Google Routes no está disponible temporalmente.");
        }
    }

    private static Map<String, Object> location(double latitude, double longitude) {
        return Map.of("location", Map.of("latLng", Map.of("latitude", latitude, "longitude", longitude)));
    }

    private static long parseDurationSeconds(String value) {
        if (value == null || !value.matches("\\d+(\\.\\d+)?s")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Routes devolvió una duración no válida.");
        }
        try {
            return (long) Math.ceil(Double.parseDouble(value.substring(0, value.length() - 1)));
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Google Routes devolvió una duración no válida.");
        }
    }
}
