package com.project.back_end_proyecto.service.publicplaces;

import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Clock;
import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import javax.net.ssl.SSLException;

@Component
@Profile("!local-no-db")
public class OpenStreetMapPlaceSource implements FuenteLugaresPublicos {
    private static final String NOMINATIM = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT = "SmartTripPlanner/1.0 (https://github.com/shadou09090/SmartTrip-Planner)";
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration DEFAULT_ENDPOINT_COOLDOWN = Duration.ofMinutes(3);
    private static final Duration NOMINATIM_NETWORK_COOLDOWN = Duration.ofMinutes(1);
    private static final int DEFAULT_DAILY_OVERPASS_LIMIT = 80;
    private static final Logger log = LoggerFactory.getLogger(OpenStreetMapPlaceSource.class);
    private final RestClient client;
    private final OpenStreetMapParser parser;
    private final DestinoCoordinateCache destinationCoordinates;
    private final List<URI> overpassEndpoints;
    private final Clock clock;
    private final Duration endpointCooldown;
    private final int dailyOverpassLimit;
    private final Map<URI, Instant> endpointUnavailableUntil = new ConcurrentHashMap<>();
    private final Map<String, CachedPlaces> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedPlaces> lodgingCache = new ConcurrentHashMap<>();
    private final Map<String, CachedGeoPoint> geocodingCache = new ConcurrentHashMap<>();
    private final Map<String, CachedDestinations> destinationSearchCache = new ConcurrentHashMap<>();
    private Instant lastGeocodeRequest = Instant.EPOCH;
    private LocalDate requestDay = LocalDate.now(ZoneOffset.UTC);
    private int overpassRequestsToday;
    private Instant nominatimBlockedUntil = Instant.EPOCH;

    @Autowired
    public OpenStreetMapPlaceSource(RestClient.Builder builder, OpenStreetMapParser parser,
                                    DestinoCoordinateCache destinationCoordinates,
                                    @Value("${smarttrip.places.overpass-url:https://overpass-api.de/api/interpreter}") String overpassEndpoint,
                                    @Value("${smarttrip.places.overpass-alternates:https://overpass.private.coffee/api/interpreter}") String overpassAlternates,
                                    @Value("${smarttrip.places.connect-timeout-ms:3000}") long connectTimeoutMillis,
                                    @Value("${smarttrip.places.read-timeout-ms:15000}") long readTimeoutMillis,
                                    @Value("${smarttrip.places.endpoint-cooldown-seconds:180}") long endpointCooldownSeconds,
                                    @Value("${smarttrip.places.daily-overpass-request-limit:80}") int dailyOverpassLimit) {
        var requestFactory = com.project.back_end_proyecto.config.HttpRequestFactoryHelper.create(connectTimeoutMillis, readTimeoutMillis);
        this.client = builder.clone().requestFactory(requestFactory).build();
        this.parser = parser;
        this.destinationCoordinates = destinationCoordinates;
        this.overpassEndpoints = parseEndpoints(overpassEndpoint, overpassAlternates);
        this.clock = Clock.systemUTC();
        this.endpointCooldown = Duration.ofSeconds(Math.max(1, endpointCooldownSeconds));
        this.dailyOverpassLimit = Math.max(1, dailyOverpassLimit);
    }

    OpenStreetMapPlaceSource(RestClient client, OpenStreetMapParser parser, String overpassEndpoint) {
        this(client, parser, List.of(URI.create(overpassEndpoint)));
    }

    OpenStreetMapPlaceSource(RestClient client, OpenStreetMapParser parser, List<URI> overpassEndpoints) {
        this(client, parser, overpassEndpoints, Clock.systemUTC(), DEFAULT_ENDPOINT_COOLDOWN,
                DEFAULT_DAILY_OVERPASS_LIMIT);
    }

    OpenStreetMapPlaceSource(RestClient client, OpenStreetMapParser parser, List<URI> overpassEndpoints,
                             Clock clock, Duration endpointCooldown, int dailyOverpassLimit) {
        this(client, parser, overpassEndpoints, clock, endpointCooldown, dailyOverpassLimit,
                new DestinoCoordinateCache(clock));
    }

    OpenStreetMapPlaceSource(RestClient client, OpenStreetMapParser parser, List<URI> overpassEndpoints,
                             Clock clock, Duration endpointCooldown, int dailyOverpassLimit,
                             DestinoCoordinateCache destinationCoordinates) {
        this.client = client;
        this.parser = parser;
        this.destinationCoordinates = destinationCoordinates;
        this.overpassEndpoints = List.copyOf(overpassEndpoints);
        this.clock = clock;
        this.endpointCooldown = endpointCooldown;
        this.dailyOverpassLimit = dailyOverpassLimit;
    }

    private static List<URI> parseEndpoints(String primary, String alternates) {
        LinkedHashMap<String, URI> endpoints = new LinkedHashMap<>();
        addEndpoint(endpoints, primary);
        if (alternates != null) {
            for (String alternate : alternates.split(",")) addEndpoint(endpoints, alternate);
        }
        if (endpoints.isEmpty()) throw new IllegalArgumentException("At least one Overpass endpoint must be configured");
        return List.copyOf(endpoints.values());
    }

    private static void addEndpoint(Map<String, URI> endpoints, String value) {
        if (value == null || value.isBlank()) return;
        URI endpoint = URI.create(value.trim());
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || !endpoint.getPath().endsWith("/interpreter")) {
            throw new IllegalArgumentException("Overpass endpoints must be HTTPS interpreter URLs");
        }
        endpoints.putIfAbsent(endpoint.toString(), endpoint);
    }

    @Override
    public synchronized List<LugarPublico> buscar(String destino) {
        String key = normalizar(destino);
        CachedPlaces cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached.places();
        try {
            OpenStreetMapParser.GeoPoint point = geocodificar(destino);
            if (point == null) return List.of();
            List<LugarPublico> places = consultarOverpass(point);
            if (cache.size() >= 200) cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.instant()));
            if (cache.size() >= 200) cache.clear();
            cache.put(key, new CachedPlaces(places, clock.instant().plus(CACHE_TTL)));
            return places;
        } catch (ErrorFuentePublica error) {
            throw error;
        } catch (RuntimeException error) {
            log.warn("OpenStreetMap response processing failed: exceptionType={}, rootCauseType={}",
                    error.getClass().getSimpleName(), rootCause(error).getClass().getSimpleName());
            throw new ErrorFuentePublica("OpenStreetMap no está disponible temporalmente.", error);
        }
    }

    @Override
    public synchronized List<LugarPublico> buscarAlojamientos(String destino) {
        String key = normalizar(destino);
        CachedPlaces cached = lodgingCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached.places();
        try {
            OpenStreetMapParser.GeoPoint point = geocodificar(destino);
            if (point == null) return List.of();
            List<LugarPublico> places = consultarAlojamientos(point);
            if (lodgingCache.size() >= 200) lodgingCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.instant()));
            if (lodgingCache.size() >= 200) lodgingCache.clear();
            lodgingCache.put(key, new CachedPlaces(places, clock.instant().plus(CACHE_TTL)));
            return places;
        } catch (ErrorFuentePublica error) {
            throw error;
        } catch (RuntimeException error) {
            log.warn("OpenStreetMap lodging response processing failed: exceptionType={}, rootCauseType={}",
                    error.getClass().getSimpleName(), rootCause(error).getClass().getSimpleName());
            throw new ErrorFuentePublica("La búsqueda pública de alojamientos no está disponible temporalmente.", error);
        }
    }

    @Override
    public synchronized java.util.Optional<CoordenadasPublicas> geocodificarDestino(String destino) {
        OpenStreetMapParser.GeoPoint point = geocodificar(destino);
        return point == null ? java.util.Optional.empty()
                : java.util.Optional.of(new CoordenadasPublicas(point.latitude(), point.longitude()));
    }

    @Override
    public synchronized List<DestinoCandidato> buscarDestinos(String consulta) {
        String query = consulta == null ? "" : consulta.trim();
        if (query.length() < 2 || query.length() > 160) return List.of();
        String key = normalizar(query);
        CachedDestinations cached = destinationSearchCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached.candidates();
        Instant now = clock.instant();
        if (now.isBefore(nominatimBlockedUntil)) {
            throw new ErrorFuentePublica("Nominatim solicitó una pausa temporal; inténtalo más tarde.");
        }
        if (Duration.between(lastGeocodeRequest, now).compareTo(Duration.ofSeconds(1)) < 0) {
            throw new ErrorFuentePublica("Nominatim limita las búsquedas a una solicitud por segundo; inténtalo de nuevo.");
        }
        lastGeocodeRequest = now;
        URI uri = UriComponentsBuilder.fromUriString(NOMINATIM)
                .queryParam("q", query)
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1)
                .queryParam("limit", 5)
                .build().encode().toUri();
        try {
            List<?> rows = client.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://github.com/shadou09090/SmartTrip-Planner")
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            List<DestinoCandidato> candidates = parseDestinationCandidates(rows == null ? List.of() : rows);
            Instant expiresAt = clock.instant().plus(Duration.ofHours(24));
            destinationSearchCache.put(key, new CachedDestinations(candidates, expiresAt));
            for (DestinoCandidato candidate : candidates) {
                if (candidate.nombreCompleto() != null && candidate.latitud() != null && candidate.longitud() != null) {
                    destinationCoordinates.remember(candidate.nombreCompleto(),
                            new CoordenadasPublicas(candidate.latitud(), candidate.longitud()), "Nominatim destination search");
                }
            }
            if (destinationSearchCache.size() > 300) {
                destinationSearchCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.instant()));
            }
            return candidates;
        } catch (RestClientResponseException error) {
            throw handleHttpError(error, "Nominatim");
        } catch (RestClientException error) {
            throw handleNetworkError(error, "Nominatim");
        }
    }

    private static List<DestinoCandidato> parseDestinationCandidates(List<?> rows) {
        List<DestinoCandidato> candidates = new ArrayList<>();
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> item)) continue;
            BigDecimal lat = decimal(item.get("lat"));
            BigDecimal lon = decimal(item.get("lon"));
            if (!validCoordinate(lat, new BigDecimal("-90"), new BigDecimal("90"))
                    || !validCoordinate(lon, new BigDecimal("-180"), new BigDecimal("180"))) continue;
            String display = text(item.get("display_name"));
            if (display == null || display.isBlank()) continue;
            Map<?, ?> address = item.get("address") instanceof Map<?, ?> value ? value : Map.of();
            String city = firstText(address, "city", "town", "village", "municipality", "hamlet");
            String region = firstText(address, "state", "region", "county", "state_district");
            String country = text(address.get("country"));
            String canonicalName = canonicalDestinationName(city, region, country, display);
            String category = firstNonBlank(text(item.get("type")), text(item.get("class")));
            String placeId = text(item.get("place_id"));
            String id = placeId != null && placeId.matches("\\d+") ? "nominatim:" + placeId
                    : "nominatim:" + lat.stripTrailingZeros().toPlainString() + ":" + lon.stripTrailingZeros().toPlainString();
            candidates.add(new DestinoCandidato(id, canonicalName, city, region, country, category, lat, lon, "Nominatim / OpenStreetMap"));
        }
        return List.copyOf(candidates);
    }

    private static String firstText(Map<?, ?> values, String... keys) {
        for (String key : keys) {
            String value = text(values.get(key));
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String canonicalDestinationName(String city, String region, String country, String fallback) {
        List<String> parts = new ArrayList<>();
        for (String part : List.of(city == null ? "" : city, region == null ? "" : region,
                country == null ? "" : country)) {
            if (!part.isBlank() && parts.stream().noneMatch(existing -> existing.equalsIgnoreCase(part))) parts.add(part);
        }
        return parts.isEmpty() ? fallback.trim() : String.join(", ", parts);
    }

    private OpenStreetMapParser.GeoPoint geocodificar(String destination) {
        String cacheKey = normalizar(destination);
        CachedGeoPoint cached = geocodingCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached.point();
        Optional<CoordenadasPublicas> resolved = destinationCoordinates.resolve(destination, "Nominatim",
                () -> geocodeNominatim(destination));
        if (resolved.isEmpty()) return null;
        OpenStreetMapParser.GeoPoint point = new OpenStreetMapParser.GeoPoint(
                resolved.get().latitud(), resolved.get().longitud());
        geocodingCache.put(cacheKey, new CachedGeoPoint(point, clock.instant().plus(CACHE_TTL)));
        return point;
    }

    private Optional<CoordenadasPublicas> geocodeNominatim(String destination) {
        Instant now = clock.instant();
        if (now.isBefore(nominatimBlockedUntil)) {
            throw new ErrorFuentePublica("Nominatim solicitó una pausa temporal; inténtalo más tarde.");
        }
        if (Duration.between(lastGeocodeRequest, now).compareTo(Duration.ofSeconds(1)) < 0) {
            throw new ErrorFuentePublica("Nominatim solicita al menos un segundo entre consultas; inténtalo de nuevo más tarde.");
        }
        lastGeocodeRequest = now;
        URI uri = UriComponentsBuilder.fromUriString(NOMINATIM)
                .queryParam("q", destination)
                .queryParam("format", "jsonv2")
                .queryParam("limit", 1)
                .build().encode().toUri();
        try {
            List<?> result = client.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "https://github.com/shadou09090/SmartTrip-Planner")
                    .retrieve().body(new ParameterizedTypeReference<>() { });
            OpenStreetMapParser.GeoPoint point = parser.parsearGeocodificacion(result == null ? List.of() : result);
            if (point == null) return Optional.empty();
            if (geocodingCache.size() > 500) geocodingCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.instant()));
            return Optional.of(new CoordenadasPublicas(point.latitude(), point.longitude()));
        } catch (RestClientResponseException error) {
            throw handleHttpError(error, "Nominatim");
        } catch (RestClientException error) {
            throw handleNetworkError(error, "Nominatim");
        }
    }

    private List<LugarPublico> consultarAlojamientos(OpenStreetMapParser.GeoPoint point) {
        String lat = point.latitude().toPlainString();
        String lon = point.longitude().toPlainString();
        String query = "[out:json][timeout:10];(" +
                "nwr(around:5000," + lat + "," + lon + ")[\"name\"][\"tourism\"~\"hotel|motel|hostel|guest_house|apartment\"];" +
                ");out tags center 100;";
        return consultarOverpass(query, "Overpass lodging search");
    }

    private List<LugarPublico> consultarOverpass(OpenStreetMapParser.GeoPoint point) {
        String lat = point.latitude().toPlainString();
        String lon = point.longitude().toPlainString();
        String query = "[out:json][timeout:10];(" +
                "nwr(around:15000," + lat + "," + lon + ")[\"name\"][\"tourism\"~\"attraction|museum|gallery|viewpoint|zoo|theme_park|artwork\"];" +
                "nwr(around:15000," + lat + "," + lon + ")[\"name\"][\"historic\"~\"monument|castle|archaeological_site|memorial|palace\"];" +
                "nwr(around:15000," + lat + "," + lon + ")[\"name\"][\"leisure\"~\"park|garden|nature_reserve\"];" +
                "nwr(around:15000," + lat + "," + lon + ")[\"name\"][\"natural\"~\"beach|cape|peak\"];" +
                "nwr(around:15000," + lat + "," + lon + ")[\"name\"][\"amenity\"~\"place_of_worship|theatre|arts_centre\"];" +
                ");out tags center 100;";
        return consultarOverpass(query, "Overpass");
    }

    private List<LugarPublico> consultarOverpass(String query, String operation) {
        LinkedMultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("data", query);
        List<String> failures = new ArrayList<>();
        Instant now = clock.instant();
        for (URI endpoint : overpassEndpoints) {
            Instant unavailableUntil = endpointUnavailableUntil.get(endpoint);
            if (unavailableUntil != null && unavailableUntil.isAfter(now)) continue;
            aplicarLimiteDiario();
            long startedNanos = System.nanoTime();
            try {
                Map<String, Object> payload = client.post().uri(endpoint)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "https://github.com/shadou09090/SmartTrip-Planner")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .accept(MediaType.APPLICATION_JSON).body(body).retrieve()
                        .body(new ParameterizedTypeReference<>() { });
                if (payload == null || !(payload.get("elements") instanceof List<?>)) {
                    throw new InvalidOverpassResponseException("Overpass returned an invalid JSON structure");
                }
                endpointUnavailableUntil.remove(endpoint);
                List<LugarPublico> parsed = parser.parsearOverpass(payload);
                log.info("provider_request provider=Overpass operation={} endpoint={} status=200 duration_ms={} results={}",
                        operation, endpoint.getHost() + endpoint.getPath(),
                        (System.nanoTime() - startedNanos) / 1_000_000L, parsed.size());
                return parsed;
            } catch (RestClientResponseException error) {
                int status = error.getStatusCode().value();
                log.warn("provider_request provider=Overpass operation={} endpoint={} status={} stage=http_{} duration_ms={} exceptionTypes={}",
                        operation, endpoint.getHost() + endpoint.getPath(), status, status,
                        (System.nanoTime() - startedNanos) / 1_000_000L, exceptionTypes(error));
                failures.add(endpoint.getHost() + " HTTP " + status);
                if (isRetryableStatus(status)) {
                    endpointUnavailableUntil.put(endpoint, retryAt(error, status));
                    continue;
                }
                throw new ErrorFuentePublica(operation + " respondió con HTTP " + status + ".", error);
            } catch (RestClientException | InvalidOverpassResponseException error) {
                boolean invalidJson = error instanceof InvalidOverpassResponseException;
                log.warn("provider_request provider=Overpass operation={} endpoint={} status={} stage={} duration_ms={} exceptionTypes={}",
                        operation, endpoint.getHost() + endpoint.getPath(), invalidJson ? 200 : "no_http_response",
                        invalidJson ? "invalid_json" : failureStage(error),
                        (System.nanoTime() - startedNanos) / 1_000_000L, exceptionTypes(error));
                    endpointUnavailableUntil.put(endpoint, clock.instant().plus(endpointCooldown));
                failures.add(endpoint.getHost() + " unavailable");
            }
        }
        String detail = failures.isEmpty() ? "All configured Overpass endpoints are in cooldown" : String.join(", ", failures);
        log.warn("{} unavailable: {}", operation, detail);
        throw new ErrorFuentePublica("Los servicios públicos de lugares no están disponibles temporalmente; puedes reintentar más tarde.");
    }

    private static boolean isRetryableStatus(int status) {
        return status == 404 || status == 406 || status == 429 || status >= 500;
    }

    private void aplicarLimiteDiario() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(requestDay)) {
            requestDay = today;
            overpassRequestsToday = 0;
        }
        if (overpassRequestsToday >= dailyOverpassLimit) {
            throw new ErrorFuentePublica("Se alcanzó el límite diario conservador de consultas públicas; las recomendaciones se reanudan mañana.");
        }
        overpassRequestsToday++;
    }

    private ErrorFuentePublica handleHttpError(RestClientResponseException error, String sourceName) {
        int status = error.getStatusCode().value();
        log.warn("{} returned HTTP {}", sourceName, status);
        if (status == 429 || status == 406) {
            Instant retryAt = retryAt(error, status);
            if (!sourceName.startsWith("Overpass")) nominatimBlockedUntil = retryAt;
            return new ErrorFuentePublica(sourceName + " limitó temporalmente las solicitudes; se respetará la pausa indicada.", error);
        }
        if (!sourceName.startsWith("Overpass") && status >= 500) {
            nominatimBlockedUntil = retryAt(error, status);
        }
        return new ErrorFuentePublica(sourceName + " no está disponible temporalmente.", error);
    }

    private ErrorFuentePublica handleNetworkError(RestClientException error, String sourceName) {
        log.warn("{} request failed before receiving an HTTP response: stage={}, exceptionTypes={}",
                sourceName, failureStage(error), exceptionTypes(error));
        if (!sourceName.startsWith("Overpass")) {
            nominatimBlockedUntil = clock.instant().plus(NOMINATIM_NETWORK_COOLDOWN);
        }
        return new ErrorFuentePublica(sourceName + " no está disponible temporalmente por un fallo de red.", error);
    }

    private static String exceptionTypes(Throwable error) {
        java.util.ArrayList<String> types = new java.util.ArrayList<>();
        Throwable cause = error;
        while (cause != null && !types.contains(cause.getClass().getSimpleName())) {
            types.add(cause.getClass().getSimpleName());
            if (cause.getCause() == cause) break;
            cause = cause.getCause();
        }
        return String.join(" -> ", types);
    }

    private static String failureStage(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException) return "dns";
            if (cause instanceof HttpConnectTimeoutException) return "connect_timeout";
            if (cause instanceof SSLException) return "tls";
            if (cause instanceof HttpTimeoutException) return "read_timeout";
            if (cause instanceof SocketTimeoutException) return "read_timeout";
            if (cause instanceof ConnectException) return "tcp_connect";
            if (cause == cause.getCause()) break;
        }
        return error instanceof InvalidOverpassResponseException ? "invalid_json" : "network";
    }

    private static Throwable rootCause(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause;
    }

    private Instant retryAt(RestClientResponseException error, int status) {
        String retryAfter = error.getResponseHeaders() == null ? null : error.getResponseHeaders().getFirst("Retry-After");
        long seconds = status == 429 || status == 406 ? 30 : 180;
        if (retryAfter != null) {
            try { seconds = Math.max(seconds, Long.parseLong(retryAfter)); }
            catch (NumberFormatException ignored) {
                try { return java.time.ZonedDateTime.parse(retryAfter, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
                catch (java.time.format.DateTimeParseException ignoredDate) { }
            }
        }
        return clock.instant().plusSeconds(seconds);
    }

    private static String normalizar(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String text(Object value) { return value == null ? null : value.toString(); }

    private static BigDecimal decimal(Object value) {
        if (value == null) return null;
        try { return new BigDecimal(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static boolean validCoordinate(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value != null && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record CachedPlaces(List<LugarPublico> places, Instant expiresAt) { }
    private record CachedGeoPoint(OpenStreetMapParser.GeoPoint point, Instant expiresAt) { }
    private record CachedDestinations(List<DestinoCandidato> candidates, Instant expiresAt) { }

    private static final class InvalidOverpassResponseException extends RuntimeException {
        private InvalidOverpassResponseException(String message) { super(message); }
    }
}
