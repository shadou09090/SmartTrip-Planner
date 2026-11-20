package com.project.back_end_proyecto.service.routes;

import com.project.back_end_proyecto.dto.RouteCalculationRequest;
import com.project.back_end_proyecto.dto.RouteCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Server-side ORS Directions V2 adapter. API keys never leave the backend. */
@Component
@Profile("!local-no-db")
public class OpenRouteServiceDurationProvider implements RouteDurationProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenRouteServiceDurationProvider.class);
    private static final String DEFAULT_BASE_URL = "https://api.heigit.org";
    private static final String DIRECTIONS_PATH = "/openrouteservice/v2/directions/{profile}/json";
    private final RestClient client;
    private final String apiKey;

    @Autowired
    public OpenRouteServiceDurationProvider(
            RestClient.Builder builder,
            @Value("${smarttrip.routes.ors.base-url:https://api.heigit.org}") String baseUrl,
            @Value("${smarttrip.routes.ors.api-key:}") String apiKey,
            @Value("${smarttrip.routes.ors.connect-timeout-ms:3000}") long connectTimeoutMillis,
            @Value("${smarttrip.routes.ors.read-timeout-ms:12000}") long readTimeoutMillis) {
        var requestFactory = com.project.back_end_proyecto.config.HttpRequestFactoryHelper.create(connectTimeoutMillis, readTimeoutMillis);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    OpenRouteServiceDurationProvider(RestClient client, String apiKey) {
        this.client = client;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public RouteCalculationResponse.ModeResult calculate(RouteCalculationRequest request, RouteTravelMode mode) {
        if (mode != RouteTravelMode.DRIVE) {
            return unavailable(mode, "ORS está configurado únicamente para rutas en automóvil.");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Cálculo de rutas sin configurar: define ORS_API_KEY en el backend.");
        }
        validateCoordinates(request);

        Map<String, Object> payload = Map.of(
                "coordinates", List.of(
                        List.of(request.origin().longitude().doubleValue(), request.origin().latitude().doubleValue()),
                        List.of(request.destination().longitude().doubleValue(), request.destination().latitude().doubleValue())),
                "instructions", false,
                "units", "m");
        try {
            JsonNode root = client.post().uri(DIRECTIONS_PATH, "driving-car")
                    .header("Authorization", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(payload).retrieve().body(JsonNode.class);
            JsonNode routes = root == null ? null : root.path("routes");
            if (routes == null || !routes.isArray() || routes.isEmpty()) {
                return unavailable(mode, "ORS no encontró una ruta para estas ubicaciones.");
            }
            JsonNode summary = routes.get(0).path("summary");
            if (!summary.path("distance").isNumber() || !summary.path("duration").isNumber()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "ORS devolvió una respuesta de ruta incompleta.");
            }
            double distance = summary.path("distance").asDouble();
            double duration = summary.path("duration").asDouble();
            if (!Double.isFinite(distance) || !Double.isFinite(duration) || distance < 0 || duration < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "ORS devolvió distancia o duración no válidas.");
            }
            long meters = Math.round(distance);
            long seconds = (long) Math.ceil(duration);
            boolean exceedsTwoHours = seconds > Duration.ofMinutes(120).toSeconds();
            return new RouteCalculationResponse.ModeResult(mode.name(), true, seconds, meters,
                    exceedsTwoHours, exceedsTwoHours ? "El traslado supera los 120 minutos." : null);
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().value();
            log.warn("ORS Directions returned HTTP {} for profile driving-car", status);
            if (status == 401 || status == 403) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "ORS rechazó la clave o el acceso al servicio Directions.");
            }
            if (status == 429) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "ORS alcanzó su límite temporal de consultas. Inténtalo más tarde.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ORS no pudo calcular la ruta (HTTP " + status + ").");
        } catch (RestClientException exception) {
            log.warn("ORS Directions request failed: exceptionType={}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ORS no está disponible temporalmente; revisa la conectividad e inténtalo de nuevo.");
        }
    }

    private static void validateCoordinates(RouteCalculationRequest request) {
        if (request == null || request.origin() == null || request.destination() == null
                || request.origin().latitude() == null || request.origin().longitude() == null
                || request.destination().latitude() == null || request.destination().longitude() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El origen y destino deben incluir latitud y longitud.");
        }
        validatePoint(request.origin().latitude().doubleValue(), request.origin().longitude().doubleValue());
        validatePoint(request.destination().latitude().doubleValue(), request.destination().longitude().doubleValue());
        if (request.origin().latitude().compareTo(request.destination().latitude()) == 0
                && request.origin().longitude().compareTo(request.destination().longitude()) == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El origen y destino deben ser ubicaciones diferentes.");
        }
    }

    private static void validatePoint(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Las coordenadas no son válidas: latitud debe estar entre -90 y 90 y longitud entre -180 y 180.");
        }
    }

    private static RouteCalculationResponse.ModeResult unavailable(RouteTravelMode mode, String message) {
        return new RouteCalculationResponse.ModeResult(mode.name(), false, null, null, false, message);
    }
}
