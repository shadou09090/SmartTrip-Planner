package com.project.back_end_proyecto.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.project.back_end_proyecto.dto.HotelPriceSearchResponse;
import com.project.back_end_proyecto.entity.Viaje;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Profile("oracle")
public class OBOHotelPriceService {
    private static final String MCP_URL = "https://onebookingonline.com/mcp";
    private static final int MAX_GUESTS = 8;
    private final TripPermissionService permissions;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final AtomicLong requestIds = new AtomicLong();

    public OBOHotelPriceService(TripPermissionService permissions, RestClient.Builder builder, ObjectMapper mapper) {
        this.permissions = permissions;
        this.client = builder.clone().baseUrl(MCP_URL).build();
        this.mapper = mapper;
    }

    public HotelPriceSearchResponse search(Long userId, Long tripId, int guests, int rooms, String currency) {
        Viaje trip = permissions.requireRead(userId, tripId);
        if (trip.getFechaInicio() == null || trip.getFechaFin() == null || !trip.getFechaFin().isAfter(trip.getFechaInicio())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El viaje necesita fechas válidas para consultar tarifas.");
        }
        if (guests < 1 || guests > MAX_GUESTS || rooms < 1 || rooms > 4) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La consulta admite de 1 a 8 viajeros y de 1 a 4 habitaciones.");
        }
        String quoteCurrency = currency == null || currency.isBlank() ? "COP" : currency.trim().toUpperCase(java.util.Locale.ROOT);
        if (!quoteCurrency.matches("[A-Z]{3}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La moneda debe ser un código ISO de tres letras.");
        }
        long id = requestIds.incrementAndGet();
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("destination", trip.getDestino());
        arguments.put("check_in", trip.getFechaInicio().toString());
        arguments.put("check_out", trip.getFechaFin().toString());
        arguments.put("adults", guests);
        arguments.put("rooms", rooms);
        arguments.put("currency", quoteCurrency);
        arguments.put("sort", "recommended");
        arguments.put("limit", 10);
        Map<String, Object> body = Map.of("jsonrpc", "2.0", "id", id, "method", "tools/call",
                "params", Map.of("name", "search_hotels", "arguments", arguments));
        Instant retrievedAt = Instant.now();
        try {
            JsonNode response = client.post().header("Accept", "application/json, text/event-stream")
                    .header("MCP-Protocol-Version", "2025-06-18").body(body).retrieve().body(JsonNode.class);
            if (response == null || response.has("error")) {
                String code = response == null ? "EMPTY_RESPONSE" : response.path("error").path("code").asText("");
                HttpStatus status = "RATE_LIMITED".equalsIgnoreCase(code) ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
                throw new ResponseStatusException(status, status == HttpStatus.TOO_MANY_REQUESTS
                        ? "El proveedor alcanzó su límite gratuito. Espera antes de reintentar."
                        : "El proveedor de tarifas no pudo completar la búsqueda.");
            }
            JsonNode result = response.path("result");
            JsonNode payload = result.hasNonNull("structuredContent") ? result.get("structuredContent") : parseTextContent(result);
            Instant expires = retrievedAt.plusSeconds(30 * 60L);
            return new HotelPriceSearchResponse("OBO Travel", trip.getDestino(), trip.getFechaInicio(),
                    trip.getFechaFin(), guests, rooms, quoteCurrency, retrievedAt, expires, payload);
        } catch (RestClientResponseException exception) {
            HttpStatus status = exception.getStatusCode().value() == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
            throw new ResponseStatusException(status, status == HttpStatus.TOO_MANY_REQUESTS
                    ? "El proveedor alcanzó su límite gratuito. Espera antes de reintentar."
                    : "El proveedor de tarifas devolvió un error HTTP.");
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No fue posible conectar con el proveedor gratuito de tarifas.");
        }
    }

    private JsonNode parseTextContent(JsonNode result) {
        JsonNode content = result.path("content");
        if (!content.isArray()) return result;
        for (JsonNode item : content) {
            String text = item.path("text").asText("");
            if (text.isBlank()) continue;
            try { return mapper.readTree(text); }
            catch (Exception ignored) { return mapper.valueToTree(Map.of("message", text)); }
        }
        return result;
    }
}
