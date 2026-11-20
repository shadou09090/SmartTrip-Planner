package com.project.back_end_proyecto.service.flights;

import com.project.back_end_proyecto.service.ProviderFailureDiagnostics;
import com.project.back_end_proyecto.dto.FlightPlaceSuggestion;
import com.project.back_end_proyecto.dto.FlightSearchRequest;
import com.project.back_end_proyecto.dto.FlightSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class DuffelFlightSearchProvider implements FlightSearchProvider {
    private static final Logger log = LoggerFactory.getLogger(DuffelFlightSearchProvider.class);
    private static final String VERSION = "v2";
    private static final long SEARCH_INTERVAL_MILLIS = 2_000;
    private static final long SUGGESTION_CACHE_MILLIS = Duration.ofMinutes(10).toMillis();

    private final RestClient client;
    private final String accessToken;
    private final AtomicLong lastSearchMillis = new AtomicLong(0L);
    private final Map<String, CachedSuggestions> suggestionCache = new ConcurrentHashMap<>();

    @Autowired
    public DuffelFlightSearchProvider(RestClient.Builder builder,
                                     @Value("${smarttrip.flights.duffel.base-url:https://api.duffel.com}") String baseUrl,
                                     @Value("${smarttrip.flights.duffel.access-token:}") String accessToken) {
        var requestFactory = com.project.back_end_proyecto.config.HttpRequestFactoryHelper.create(3000, 15000);
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Duffel-Version", VERSION).build();
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    DuffelFlightSearchProvider(RestClient client, String accessToken) {
        this.client = client;
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    @Override
    public List<FlightPlaceSuggestion> suggestPlaces(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 2 || !StringUtils.hasText(accessToken)) return List.of();
        String cacheKey = normalized.toLowerCase(java.util.Locale.ROOT);
        CachedSuggestions cached = suggestionCache.get(cacheKey);
        if (cached != null && cached.expiresAtMillis() > System.currentTimeMillis()) return cached.places();
        long startedNanos = System.nanoTime();
        try {
            JsonNode root = client.get()
                    .uri(UriComponentsBuilder.fromPath("/places/suggestions").queryParam("query", normalized).build().encode().toUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(JsonNode.class);
            List<FlightPlaceSuggestion> places = new ArrayList<>();
            JsonNode data = root == null ? null : root.path("data");
            if (data != null && data.isArray()) {
                for (JsonNode item : data) {
                    String iata = text(item, "iata_code");
                    String name = text(item, "name");
                    String type = text(item, "type");
                    if (iata != null && iata.matches("[A-Z]{3}") && name != null
                            && ("city".equals(type) || "airport".equals(type))) {
                        places.add(new FlightPlaceSuggestion(name, iata, type, text(item, "iata_country_code")));
                    }
                    if (places.size() == 8) break;
                }
            }
            List<FlightPlaceSuggestion> result = List.copyOf(places);
            suggestionCache.put(cacheKey, new CachedSuggestions(result, System.currentTimeMillis() + SUGGESTION_CACHE_MILLIS));
            log.info("provider_request provider=Duffel operation=place_suggestions endpoint=api.duffel.com/places/suggestions api_version={} status=200 duration_ms={} results={}",
                    VERSION, ProviderFailureDiagnostics.elapsedMillis(startedNanos), result.size());
            return result;
        } catch (RestClientResponseException exception) {
            log.warn("provider_request provider=Duffel operation=place_suggestions endpoint=api.duffel.com/places/suggestions api_version={} status={} stage=http_{} duration_ms={} exceptionTypes={}",
                    VERSION, exception.getStatusCode().value(), exception.getStatusCode().value(),
                    ProviderFailureDiagnostics.elapsedMillis(startedNanos),
                    ProviderFailureDiagnostics.exceptionTypes(exception));
            throw translateProviderError("place suggestions", exception);
        } catch (RestClientException exception) {
            log.warn("provider_request provider=Duffel operation=place_suggestions endpoint=api.duffel.com/places/suggestions api_version={} status=no_http_response stage={} duration_ms={} exceptionTypes={}",
                    VERSION, ProviderFailureDiagnostics.stage(exception),
                    ProviderFailureDiagnostics.elapsedMillis(startedNanos),
                    ProviderFailureDiagnostics.exceptionTypes(exception));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El proveedor de aeropuertos no está disponible temporalmente.");
        }
    }

    @Override
    public FlightSearchResponse search(FlightSearchRequest request) {
        requireConfigured();
        long now = System.currentTimeMillis();
        long last = lastSearchMillis.get();
        if (now - last < SEARCH_INTERVAL_MILLIS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Espera un momento antes de realizar otra búsqueda de vuelos.");
        }
        if (!lastSearchMillis.compareAndSet(last, now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Espera un momento antes de realizar otra búsqueda de vuelos.");
        }

        List<Map<String, String>> slices = new ArrayList<>();
        slices.add(Map.of("origin", request.origin().toUpperCase(java.util.Locale.ROOT),
                "destination", request.destination().toUpperCase(java.util.Locale.ROOT),
                "departure_date", request.departureDate().toString()));
        if (request.returnDate() != null) {
            slices.add(Map.of("origin", request.destination().toUpperCase(java.util.Locale.ROOT),
                    "destination", request.origin().toUpperCase(java.util.Locale.ROOT),
                    "departure_date", request.returnDate().toString()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("slices", slices);
        List<Map<String, String>> passengers = new ArrayList<>();
        for (int i = 0; i < request.effectiveAdults(); i++) passengers.add(Map.of("type", "adult"));
        for (int i = 0; i < request.effectiveChildren(); i++) passengers.add(Map.of("type", "child"));
        for (int i = 0; i < request.effectiveInfants(); i++) passengers.add(Map.of("type", "infant_without_seat"));
        data.put("passengers", List.copyOf(passengers));
        String cabin = StringUtils.hasText(request.cabinClass()) ? request.cabinClass().trim().toLowerCase(java.util.Locale.ROOT) : "economy";
        if (!List.of("economy", "premium_economy", "business", "first").contains(cabin)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cabina seleccionada no es válida.");
        }
        data.put("cabin_class", cabin);
        long startedNanos = System.nanoTime();
        try {
            JsonNode root = client.post().uri("/air/offer_requests?return_offers=true&supplier_timeout=10000")
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON).body(Map.of("data", data)).retrieve().body(JsonNode.class);
            List<FlightSearchResponse.Offer> offers = parseOffers(root == null ? null : root.path("data").path("offers"));
            boolean test = accessToken.startsWith("duffel_test_");
            log.info("provider_request provider=Duffel operation=flight_search endpoint=api.duffel.com/air/offer_requests api_version={} status=200 duration_ms={} offers={} sandbox={}",
                    VERSION, ProviderFailureDiagnostics.elapsedMillis(startedNanos), offers.size(), test);
            String notice = test
                    ? "Modo de prueba de Duffel: los horarios y precios son datos de sandbox, no ofertas reales disponibles."
                    : "Resultados sujetos a cambios; confirma precio y disponibilidad con el proveedor antes de comprar. SmartTrip no realiza reservas.";
            return new FlightSearchResponse("Duffel", test, offers, notice);
        } catch (RestClientResponseException exception) {
            log.warn("provider_request provider=Duffel operation=flight_search endpoint=api.duffel.com/air/offer_requests api_version={} status={} stage=http_{} duration_ms={} exceptionTypes={}",
                    VERSION, exception.getStatusCode().value(), exception.getStatusCode().value(),
                    ProviderFailureDiagnostics.elapsedMillis(startedNanos),
                    ProviderFailureDiagnostics.exceptionTypes(exception));
            throw translateProviderError("flight search", exception);
        } catch (RestClientException exception) {
            log.warn("provider_request provider=Duffel operation=flight_search endpoint=api.duffel.com/air/offer_requests api_version={} status=no_http_response stage={} duration_ms={} exceptionTypes={}",
                    VERSION, ProviderFailureDiagnostics.stage(exception),
                    ProviderFailureDiagnostics.elapsedMillis(startedNanos),
                    ProviderFailureDiagnostics.exceptionTypes(exception));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "El proveedor de vuelos no respondió. Inténtalo nuevamente más tarde.");
        }
    }

    @Override
    public FlightSearchResponse.Offer retrieveOffer(String offerId) {
        requireConfigured();
        if (!StringUtils.hasText(offerId) || !offerId.matches("off_[A-Za-z0-9_-]{3,}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador de oferta no es válido.");
        }
        try {
            JsonNode root = client.get()
                    .uri(UriComponentsBuilder.fromPath("/air/offers/{id}").buildAndExpand(offerId).encode().toUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve().body(JsonNode.class);
            JsonNode data = root == null ? null : root.path("data");
            FlightSearchResponse.Offer offer = parseOffer(data);
            if (offer == null || !offerId.equals(offer.id()) || offer.totalAmount().signum() < 0
                    || !offer.currency().matches("[A-Z]{3}") || offer.expiresAt() == null
                    || !offer.expiresAt().isAfter(OffsetDateTime.now())) {
                throw new ResponseStatusException(HttpStatus.GONE,
                        "La oferta ya no existe, venció o Duffel devolvió información incompleta. Realiza una nueva búsqueda.");
            }
            return offer;
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404 || exception.getStatusCode().value() == 410) {
                throw new ResponseStatusException(HttpStatus.GONE,
                        "La oferta ya no está disponible. Realiza una nueva búsqueda.");
            }
            throw translateProviderError("offer verification", exception);
        } catch (RestClientException exception) {
            log.warn("Duffel offer verification failed: exceptionType={}", exception.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Duffel no pudo verificar la oferta. Inténtalo nuevamente.");
        }
    }

    private List<FlightSearchResponse.Offer> parseOffers(JsonNode offersNode) {
        if (offersNode == null || !offersNode.isArray()) return List.of();
        List<FlightSearchResponse.Offer> offers = new ArrayList<>();
        for (JsonNode offer : offersNode) {
            FlightSearchResponse.Offer parsed = parseOffer(offer);
            if (parsed != null) offers.add(parsed);
        }
        return offers.stream().sorted(Comparator.comparing(FlightSearchResponse.Offer::totalAmount)).limit(30).toList();
    }

    private FlightSearchResponse.Offer parseOffer(JsonNode offer) {
        if (offer == null || !offer.isObject()) return null;
        try {
            BigDecimal amount = new BigDecimal(offer.path("total_amount").asText());
            String currency = text(offer, "total_currency");
            String id = text(offer, "id");
            if (id == null || currency == null) return null;
            List<FlightSearchResponse.Slice> mappedSlices = new ArrayList<>();
            JsonNode slices = offer.path("slices");
            if (slices.isArray()) for (JsonNode slice : slices) mappedSlices.add(parseSlice(slice));
            if (mappedSlices.isEmpty()) return null;
            return new FlightSearchResponse.Offer(id, amount, currency,
                    dateTime(text(offer, "expires_at")), List.copyOf(mappedSlices));
        } catch (NumberFormatException ignored) {
            log.warn("Duffel returned an offer with an invalid amount; the offer was omitted.");
            return null;
        }
    }

    private FlightSearchResponse.Slice parseSlice(JsonNode slice) {
        JsonNode segments = slice.path("segments");
        JsonNode first = segments.isArray() && !segments.isEmpty() ? segments.get(0) : null;
        JsonNode last = segments.isArray() && !segments.isEmpty() ? segments.get(segments.size() - 1) : null;
        String origin = first == null ? null : airport(first.path("origin"));
        String destination = last == null ? null : airport(last.path("destination"));
        String airline = first == null ? null : text(first.path("marketing_carrier"), "name");
        String flightNumber = first == null ? null : text(first, "marketing_carrier_flight_number");
        String departure = first == null ? null : text(first, "departing_at");
        String arrival = last == null ? null : text(last, "arriving_at");
        String duration = humanDuration(text(slice, "duration"));
        int stops = segments.isArray() ? Math.max(0, segments.size() - 1) : 0;
        String baggage = baggage(first);
        List<FlightSearchResponse.Segment> mappedSegments = new ArrayList<>();
        if (segments.isArray()) for (JsonNode segment : segments) {
            mappedSegments.add(new FlightSearchResponse.Segment(
                    airport(segment.path("origin")), airport(segment.path("destination")),
                    text(segment.path("operating_carrier"), "name"),
                    text(segment.path("marketing_carrier"), "name"),
                    text(segment, "marketing_carrier_flight_number"),
                    text(segment, "departing_at"), text(segment, "arriving_at")));
        }
        return new FlightSearchResponse.Slice(origin, destination, airline, flightNumber, departure, arrival,
                duration, stops, baggage, List.copyOf(mappedSegments));
    }

    private static String baggage(JsonNode segment) {
        if (segment == null) return null;
        JsonNode passengers = segment.path("passengers");
        if (!passengers.isArray() || passengers.isEmpty()) return "No informado por el proveedor";
        JsonNode bags = passengers.get(0).path("baggages");
        if (!bags.isArray() || bags.isEmpty()) return "No informado por el proveedor";
        List<String> labels = new ArrayList<>();
        for (JsonNode bag : bags) {
            String type = text(bag, "type");
            String quantity = bag.path("quantity").isNumber() ? bag.path("quantity").asText() : null;
            if (type != null) labels.add((quantity == null ? "" : quantity + " ") + type);
        }
        return labels.isEmpty() ? "No informado por el proveedor" : String.join(", ", labels);
    }

    private static String airport(JsonNode node) {
        String code = text(node, "iata_code");
        String name = text(node, "name");
        if (code == null) return name;
        return name == null ? code : name + " (" + code + ")";
    }

    private static String humanDuration(String value) {
        if (value == null) return null;
        try {
            Duration duration = Duration.parse(value);
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            return hours + " h " + minutes + " min";
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void requireConfigured() {
        if (!StringUtils.hasText(accessToken)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Búsqueda de vuelos sin configurar: define DUFFEL_ACCESS_TOKEN en el backend.");
        }
    }

    private ResponseStatusException translateProviderError(String operation, RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        log.warn("Duffel {} returned HTTP {}", operation, status);
        if (status == 429) return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "El proveedor alcanzó su límite temporal de consultas. Inténtalo más tarde.");
        if (status == 401 || status == 403) return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "La credencial configurada para el proveedor de vuelos no fue aceptada.");
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "No fue posible completar la consulta con el proveedor de vuelos.");
    }

    private static String text(JsonNode node, String property) {
        if (node == null || node.path(property).isMissingNode() || node.path(property).isNull()) return null;
        String value = node.path(property).asText();
        return value.isBlank() ? null : value;
    }

    private static OffsetDateTime dateTime(String value) {
        if (value == null) return null;
        try { return OffsetDateTime.parse(value); } catch (RuntimeException ignored) { return null; }
    }

    private record CachedSuggestions(List<FlightPlaceSuggestion> places, long expiresAtMillis) { }
}
