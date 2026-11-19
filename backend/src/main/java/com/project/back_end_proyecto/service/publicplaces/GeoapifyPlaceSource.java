package com.project.back_end_proyecto.service.publicplaces;

import com.project.back_end_proyecto.service.ProviderFailureDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Geoapify Places adapter. API keys are sent only from the backend and are never logged. */
@Component
@Profile("oracle")
public class GeoapifyPlaceSource implements FuenteLugaresPublicos {
    private static final Logger log = LoggerFactory.getLogger(GeoapifyPlaceSource.class);
    private static final String DEFAULT_BASE_URL = "https://api.geoapify.com";
    private static final String ENDPOINT = "/v2/places";
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    // Geoapify caps a page at 500. A single 100-result page improves city-wide
    // candidate coverage without unbounded pagination or repeated bulk requests.
    private static final int RESULT_LIMIT = 100;
    private static final int LODGING_RESULT_LIMIT = 50;
    private static final List<Integer> TOURISM_SEARCH_RADII_METERS = List.of(15_000, 40_000, 100_000);
    private static final int MINIMUM_PRIORITY_RESULTS_BEFORE_EXPANSION = 8;
    private static final int MINIMUM_PRIORITY_RESULTS_BEFORE_REGIONAL_EXPANSION = 12;
    private static final int MINIMUM_RESULTS_BEFORE_GASTRONOMY = 5;
    private static final List<String> PLACE_CATEGORIES = List.of(
            "tourism.attraction", "tourism.sights", "entertainment.museum",
            "entertainment.culture.gallery", "entertainment.theme_park", "entertainment.aquarium",
            "leisure.park.nature_reserve", "natural", "beach", "religion");
    private static final List<String> BROAD_PLACE_CATEGORIES = List.of(
            "tourism", "entertainment", "natural", "beach", "religion", "leisure.park");
    private static final List<String> GASTRONOMY_CATEGORIES = List.of("catering.restaurant", "catering.cafe");

    private final RestClient client;
    private final String apiKey;
    private final OpenStreetMapPlaceSource nominatim;
    private final DestinoCoordinateCache destinationCoordinates;
    private final Clock clock;
    private final boolean boundarySearchEnabled;
    private final Map<String, CachedPlaces> cache = new ConcurrentHashMap<>();
    private final Map<String, CachedArea> areaCache = new ConcurrentHashMap<>();
    private Instant lastRequest = Instant.EPOCH;
    private Instant rateLimitedUntil = Instant.EPOCH;

    @Autowired
    public GeoapifyPlaceSource(RestClient.Builder builder,
                               OpenStreetMapPlaceSource nominatim,
                               DestinoCoordinateCache destinationCoordinates,
                               @Value("${smarttrip.places.geoapify.base-url:" + DEFAULT_BASE_URL + "}") String baseUrl,
                               @Value("${smarttrip.places.geoapify.api-key:}") String apiKey,
                               @Value("${smarttrip.places.geoapify.connect-timeout-ms:3000}") long connectTimeoutMs,
                               @Value("${smarttrip.places.geoapify.read-timeout-ms:10000}") long readTimeoutMs,
                               @Value("${smarttrip.places.geoapify.boundary-search-enabled:true}") boolean boundarySearchEnabled) {
        var httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs))).build();
        var requestFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1000, readTimeoutMs)));
        this.client = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.nominatim = nominatim;
        this.destinationCoordinates = destinationCoordinates;
        this.clock = Clock.systemUTC();
        this.boundarySearchEnabled = boundarySearchEnabled;
    }

    GeoapifyPlaceSource(RestClient client, String apiKey, OpenStreetMapPlaceSource nominatim, Clock clock) {
        this(client, apiKey, nominatim, clock, false);
    }

    GeoapifyPlaceSource(RestClient client, String apiKey, OpenStreetMapPlaceSource nominatim, Clock clock,
                        boolean boundarySearchEnabled) {
        this(client, apiKey, nominatim, new DestinoCoordinateCache(clock), clock, boundarySearchEnabled);
    }

    GeoapifyPlaceSource(RestClient client, String apiKey, OpenStreetMapPlaceSource nominatim,
                        DestinoCoordinateCache destinationCoordinates, Clock clock,
                        boolean boundarySearchEnabled) {
        this.client = client;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.nominatim = nominatim;
        this.destinationCoordinates = destinationCoordinates;
        this.clock = clock;
        this.boundarySearchEnabled = boundarySearchEnabled;
    }

    @Override
    public List<LugarPublico> buscar(String destino) {
        return search(destino, "poi", PLACE_CATEGORIES);
    }

    @Override
    public List<LugarPublico> buscarAlojamientos(String destino) {
        return search(destino, "lodging", List.of("accommodation"));
    }

    @Override
    public Optional<CoordenadasPublicas> geocodificarDestino(String destino) {
        return destinationCoordinates.resolve(destino, "Geoapify Geocoding", () -> geocodeWithGeoapify(destino));
    }

    private Optional<CoordenadasPublicas> geocodeWithGeoapify(String destination) {
        if (apiKey.isBlank()) throw new ErrorFuentePublica("La geocodificación Geoapify requiere GEOAPIFY_API_KEY en el backend.");
        if (destination == null || destination.isBlank()) return Optional.empty();
        respectRateLimit();
        long startedNanos = System.nanoTime();
        URI uri = UriComponentsBuilder.fromPath("/v1/geocode/search")
                .queryParam("text", destination).queryParam("type", "city").queryParam("limit", 5)
                .queryParam("format", "json").queryParam("apiKey", apiKey).build().encode().toUri();
        try {
            Map<?, ?> payload = client.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(Map.class);
            Object raw = payload == null ? null : payload.get("results");
            if (!(raw instanceof List<?> rows)) throw new ErrorFuentePublica("Geoapify devolvió una respuesta de geocodificación no válida.");
            String normalizedQuery = RelevanceScorer.normalize(destination);
            String cityQuery = RelevanceScorer.normalize(destinationCity(destination));
            for (Object item : rows) {
                if (!(item instanceof Map<?, ?> row)) continue;
                BigDecimal lat = decimal(row.get("lat"));
                BigDecimal lon = decimal(row.get("lon"));
                if (!valid(lat, -90, 90) || !valid(lon, -180, 180)) continue;
                String formatted = RelevanceScorer.normalize(text(row.get("formatted")));
                String resultCity = RelevanceScorer.normalize(firstNonBlank(text(row.get("city")), text(row.get("name"))));
                String resultCountry = RelevanceScorer.normalize(text(row.get("country")));
                if (!cityQuery.isBlank() && !resultCity.isBlank() && !cityQuery.equals(resultCity)) continue;
                if (normalizedQuery.contains(",") && !formatted.contains(normalizedQuery)) {
                    String countryQuery = RelevanceScorer.normalize(destination.substring(destination.indexOf(',') + 1));
                    if (!countryQuery.isBlank() && !resultCountry.contains(countryQuery)) continue;
                }
                CoordenadasPublicas coordinates = new CoordenadasPublicas(lat, lon);
                String placeId = text(row.get("place_id"));
                if (placeId != null) areaCache.put(normalize(destination),
                        new CachedArea(placeId, clock.instant().plus(CACHE_TTL)));
                log.info("provider_request provider=Geoapify operation=geocode endpoint=api.geoapify.com/v1/geocode/search status=200 duration_ms={} result=coordinates",
                        ProviderFailureDiagnostics.elapsedMillis(startedNanos));
                return Optional.of(coordinates);
            }
            log.info("provider_request provider=Geoapify operation=geocode endpoint=api.geoapify.com/v1/geocode/search status=200 duration_ms={} result=no_match",
                    ProviderFailureDiagnostics.elapsedMillis(startedNanos));
            return Optional.empty();
        } catch (RestClientException error) {
            int status = error instanceof org.springframework.web.client.RestClientResponseException response
                    ? response.getStatusCode().value() : 0;
            log.warn("provider_request provider=Geoapify operation=geocode endpoint=api.geoapify.com/v1/geocode/search status={} stage={} duration_ms={} exceptionTypes={}",
                    status == 0 ? "no_http_response" : status, ProviderFailureDiagnostics.stage(error),
                    ProviderFailureDiagnostics.elapsedMillis(startedNanos), ProviderFailureDiagnostics.exceptionTypes(error));
            if (status == 401 || status == 403) throw new ErrorFuentePublica("Geoapify rechazó la configuración de la clave API.", error);
            if (status == 429) {
                rateLimitedUntil = retryAt(error);
                throw new ErrorFuentePublica("Geoapify alcanzó temporalmente el límite de solicitudes.", error);
            }
            throw new ErrorFuentePublica("Geoapify no está disponible temporalmente para geocodificar.", error);
        }
    }

    public String areaGeograficaConsultada(String destination) {
        CachedArea cached = areaCache.get(normalize(destination));
        if (cached == null) return boundarySearchEnabled
                ? "Límite de ciudad Geoapify solicitado; fallback circular si no se resolvió"
                : "Búsqueda circular centrada en la geocodificación";
        return cached.placeId() == null ? "Búsqueda circular centrada en la geocodificación (15/40/100 km progresivos)"
                : "Límite de ciudad Geoapify (place_id; una página de hasta 100 resultados)";
    }

    private synchronized List<LugarPublico> search(String destination, String kind, List<String> categories) {
        String normalized = normalize(destination);
        if (normalized.isEmpty()) throw new ErrorFuentePublica("Indica un destino para buscar lugares.");
        String cacheKey = kind + ":" + normalized;
        CachedPlaces cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached.places();
        if (apiKey.isBlank()) {
            throw new ErrorFuentePublica("La búsqueda de lugares requiere configurar GEOAPIFY_API_KEY en el backend.");
        }
        if (clock.instant().isBefore(rateLimitedUntil)) {
            throw new ErrorFuentePublica("Geoapify pidió una pausa por límite de solicitudes; inténtalo más tarde.");
        }

        Optional<CoordenadasPublicas> geocoded;
        try {
            geocoded = geocodificarDestino(destination);
        } catch (RuntimeException error) {
            log.warn("Geoapify search skipped because destination geocoding failed: kind={}, exceptionType={}",
                    kind, error.getClass().getSimpleName());
            throw new ErrorFuentePublica("Geoapify no pudo geocodificar el destino para buscar lugares.", error);
        }
        if (geocoded.isEmpty()) throw new ErrorFuentePublica("Geoapify no encontró coordenadas verificables para el destino.");
        CoordenadasPublicas point = geocoded.get();
        validate(point);
        String areaPlaceId = "lodging".equals(kind) || !boundarySearchEnabled ? null : resolveCityBoundary(destination, point);
        List<LugarPublico> places = requestPlaces(kind, categories, point,
                "lodging".equals(kind) ? 5_000 : TOURISM_SEARCH_RADII_METERS.get(0), destination, areaPlaceId);
        if (!"lodging".equals(kind)) {
            int priorityCount = priorityCount(places);
            if (priorityCount < MINIMUM_PRIORITY_RESULTS_BEFORE_EXPANSION) {
                places = expandSearch(destination, kind, point, places, TOURISM_SEARCH_RADII_METERS.get(1),
                        BROAD_PLACE_CATEGORIES, areaPlaceId);
                priorityCount = priorityCount(places);
            }
            if (priorityCount < MINIMUM_PRIORITY_RESULTS_BEFORE_REGIONAL_EXPANSION) {
                places = expandSearch(destination, kind, point, places, TOURISM_SEARCH_RADII_METERS.get(2),
                        BROAD_PLACE_CATEGORIES, areaPlaceId);
                if (places.size() < MINIMUM_RESULTS_BEFORE_GASTRONOMY) {
                    try {
                        List<LugarPublico> food = requestPlaces(kind, GASTRONOMY_CATEGORIES, point,
                                TOURISM_SEARCH_RADII_METERS.get(0), destination, areaPlaceId);
                        places = mergeUnique(places, food).stream().limit(RESULT_LIMIT).toList();
                    } catch (ErrorFuentePublica foodFailure) {
                        if (places.isEmpty()) throw foodFailure;
                        log.info("Geoapify complementary gastronomy search unavailable; retaining tourism results");
                    }
                }
            }
            // A city boundary query can still be ranked heavily toward one central district.
            // If the first bounded page covers only a few map cells, repeat a small, fixed
            // number of biased searches inside the same city boundary to sample other zones.
            // Geoapify documents `filter=place:<id>` together with rectangular `bias`; the
            // boundary still excludes neighboring cities. This is capped at four requests.
            if (areaPlaceId != null && geographicCellCount(places) < 6) {
                places = searchCityZones(destination, kind, point, places, areaPlaceId);
            }
        }
        cache.put(cacheKey, new CachedPlaces(places, clock.instant().plus(CACHE_TTL)));
        if (cache.size() > 400) {
            cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.instant()));
            if (cache.size() > 400) cache.clear();
        }
        return places;
    }

    private List<LugarPublico> expandSearch(String destination, String kind, CoordenadasPublicas point,
                                             List<LugarPublico> current, int radiusMeters, List<String> categories,
                                             String areaPlaceId) {
        try {
            List<LugarPublico> expanded = requestPlaces(kind, categories, point, radiusMeters, destination, areaPlaceId);
            return mergeUnique(current, expanded).stream().limit(resultLimit(kind)).toList();
        } catch (ErrorFuentePublica expansionFailure) {
            if (current.isEmpty()) throw expansionFailure;
            log.info("Geoapify expanded place search unavailable; retaining previous results: radiusMeters={}", radiusMeters);
            return current;
        }
    }

    private List<LugarPublico> searchCityZones(String destination, String kind, CoordenadasPublicas center,
                                                List<LugarPublico> current, String areaPlaceId) {
        double latitude = center.latitud().doubleValue();
        double longitude = center.longitud().doubleValue();
        double latOffset = 0.12;
        double lonOffset = Math.min(0.5, 0.12 / Math.max(0.25, Math.cos(Math.toRadians(latitude))));
        List<double[]> zoneCenters = List.of(new double[]{latitude + latOffset, longitude},
                new double[]{latitude - latOffset, longitude}, new double[]{latitude, longitude + lonOffset},
                new double[]{latitude, longitude - lonOffset});
        List<LugarPublico> result = current;
        int returned = 0;
        for (double[] zone : zoneCenters) {
            if (zone[0] < -90 || zone[0] > 90 || zone[1] < -180 || zone[1] > 180) continue;
            double halfLat = 0.055;
            double halfLon = Math.min(0.2, halfLat / Math.max(0.25, Math.cos(Math.toRadians(zone[0]))));
            String bias = "rect:" + (zone[1] - halfLon) + "," + (zone[0] - halfLat) + ","
                    + (zone[1] + halfLon) + "," + (zone[0] + halfLat);
            try {
                List<LugarPublico> zonePlaces = requestPlaces(kind, PLACE_CATEGORIES, center, 15_000,
                        destination, areaPlaceId, bias, 50);
                returned += zonePlaces.size();
                result = mergeUnique(result, zonePlaces);
            } catch (ErrorFuentePublica failure) {
                log.info("Geoapify city-zone query unavailable; retaining partial city results: exceptionType={}",
                        failure.getClass().getSimpleName());
            }
            if (geographicCellCount(result) >= 10) break;
        }
        log.info("Geoapify bounded city-zone sampling completed: zonesWithResults={}, uniquePlaces={}, geographicCells={}",
                returned, result.size(), geographicCellCount(result));
        return result.stream().limit(RESULT_LIMIT * 3L).toList();
    }

    private static int geographicCellCount(List<LugarPublico> places) {
        return (int) places.stream().filter(place -> place.latitud() != null && place.longitud() != null)
                .map(place -> Math.floor(place.latitud().doubleValue() * 20) + ":"
                        + Math.floor(place.longitud().doubleValue() * 20)).distinct().count();
    }

    private List<LugarPublico> requestPlaces(String kind, List<String> categories,
                                              CoordenadasPublicas point, int radiusMeters, String destination,
                                              String areaPlaceId) {
        return requestPlaces(kind, categories, point, radiusMeters, destination, areaPlaceId, null, resultLimit(kind));
    }

    private List<LugarPublico> requestPlaces(String kind, List<String> categories,
                                              CoordenadasPublicas point, int radiusMeters, String destination,
                                              String areaPlaceId, String zoneBias, int limit) {
        respectRateLimit();
        String spatialFilter = areaPlaceId == null ? "circle:" + point.longitud().toPlainString() + ","
                + point.latitud().toPlainString() + "," + radiusMeters : "place:" + areaPlaceId;
        var requestUri = UriComponentsBuilder.fromPath(ENDPOINT)
                .queryParam("categories", String.join(",", categories))
                .queryParam("filter", spatialFilter);
        if (areaPlaceId == null) {
            requestUri.queryParam("bias", "proximity:" + point.longitud().toPlainString() + ","
                    + point.latitud().toPlainString());
        }
        URI uri = requestUri
                // A center proximity bias is actively harmful for a city boundary: it can
                // fill the page with central map fragments before city-wide ranking runs.
                .queryParam("limit", Math.max(1, Math.min(100, limit)))
                .queryParam("apiKey", apiKey)
                .build().encode().toUri();
        if (zoneBias != null) {
            requestUri.queryParam("bias", zoneBias);
            uri = requestUri.build().encode().toUri();
        }
        long startedNanos = System.nanoTime();
        try {
            Map<?, ?> payload = client.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(Map.class);
            List<LugarPublico> parsed = parse(payload, point, destination, kind, areaPlaceId != null);
            log.info("provider_request provider=Geoapify operation=places endpoint={} status=200 duration_ms={} results={} boundary={}",
                    uri.getHost() + uri.getPath(), ProviderFailureDiagnostics.elapsedMillis(startedNanos),
                    parsed.size(), areaPlaceId == null ? "coordinate_circle" : "resolved_city_boundary");
            return parsed;
        } catch (RestClientException error) {
            int status = error instanceof org.springframework.web.client.RestClientResponseException response
                    ? response.getStatusCode().value() : 0;
            log.warn("provider_request provider=Geoapify operation=places kind={} endpoint={} status={} stage={} duration_ms={} exceptionTypes={}",
                    kind, uri.getHost() + uri.getPath(), status == 0 ? "no_http_response" : status,
                    ProviderFailureDiagnostics.stage(error), ProviderFailureDiagnostics.elapsedMillis(startedNanos),
                    ProviderFailureDiagnostics.exceptionTypes(error));
            if (status == 401 || status == 403) {
                throw new ErrorFuentePublica("Geoapify rechazó la configuración de la clave API.");
            }
            if (status == 429) {
                rateLimitedUntil = retryAt(error);
                throw new ErrorFuentePublica("Geoapify alcanzó temporalmente el límite de solicitudes.");
            }
            throw new ErrorFuentePublica("Geoapify no está disponible temporalmente; puedes reintentar.");
        } catch (ErrorFuentePublica invalidResponse) {
            log.warn("provider_request provider=Geoapify operation=places endpoint={} status=200 stage=invalid_response duration_ms={} exceptionType={}",
                    uri.getHost() + uri.getPath(), ProviderFailureDiagnostics.elapsedMillis(startedNanos),
                    invalidResponse.getClass().getSimpleName());
            throw invalidResponse;
        }
    }

    private static int resultLimit(String kind) {
        return "lodging".equals(kind) ? LODGING_RESULT_LIMIT : RESULT_LIMIT;
    }

    /** Resolve and cache the city's public Geoapify boundary. If it cannot be resolved, keep the
     * established coordinate-circle query as a safe fallback. */
    private String resolveCityBoundary(String destination, CoordenadasPublicas center) {
        String key = normalize(destination);
        CachedArea cached = areaCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached.placeId();
        long startedNanos = System.nanoTime();
        try {
            respectRateLimit();
            URI uri = UriComponentsBuilder.fromPath("/v1/geocode/search")
                    .queryParam("text", destination).queryParam("type", "city")
                    .queryParam("limit", 5).queryParam("format", "json").queryParam("apiKey", apiKey)
                    .build().encode().toUri();
            Map<?, ?> payload = client.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(Map.class);
            Object rawResults = payload == null ? null : payload.get("results");
            if (!(rawResults instanceof List<?> results)) return null;
            String cityQuery = RelevanceScorer.normalize(destinationCity(destination));
            String selectedId = null;
            double selectedDistance = Double.POSITIVE_INFINITY;
            for (Object raw : results) {
                if (!(raw instanceof Map<?, ?> row)) continue;
                String placeId = text(row.get("place_id"));
                BigDecimal latitude = decimal(row.get("lat"));
                BigDecimal longitude = decimal(row.get("lon"));
                if (placeId == null || !valid(latitude, -90, 90) || !valid(longitude, -180, 180)) continue;
                String city = RelevanceScorer.normalize(firstNonBlank(text(row.get("city")), text(row.get("name"))));
                if (!cityQuery.isBlank() && !city.isBlank() && !city.contains(cityQuery) && !cityQuery.contains(city)) continue;
                double distance = distanceKm(center.latitud(), center.longitud(), latitude, longitude);
                if (distance <= 100 && distance < selectedDistance) { selectedId = placeId; selectedDistance = distance; }
            }
            areaCache.put(key, new CachedArea(selectedId, clock.instant().plus(CACHE_TTL)));
            log.info("provider_request provider=Geoapify operation=city_boundary endpoint=api.geoapify.com/v1/geocode/search status=200 duration_ms={} selected={}",
                    ProviderFailureDiagnostics.elapsedMillis(startedNanos), selectedId != null);
            return selectedId;
        } catch (RestClientException | ErrorFuentePublica failure) {
            log.warn("provider_request provider=Geoapify operation=city_boundary endpoint=api.geoapify.com/v1/geocode/search status={} stage={} duration_ms={} exceptionTypes={}; fallback=coordinate_circle",
                    failure instanceof org.springframework.web.client.RestClientResponseException response
                            ? response.getStatusCode().value() : "no_http_response",
                    ProviderFailureDiagnostics.stage(failure), ProviderFailureDiagnostics.elapsedMillis(startedNanos),
                    ProviderFailureDiagnostics.exceptionTypes(failure));
            return null;
        }
    }

    private synchronized void respectRateLimit() {
        Instant now = clock.instant();
        Duration sinceLast = Duration.between(lastRequest, now);
        if (!lastRequest.equals(Instant.EPOCH) && sinceLast.compareTo(Duration.ofMillis(250)) < 0) {
            long waitMillis = 250 - Math.max(0, sinceLast.toMillis());
            try { Thread.sleep(waitMillis); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ErrorFuentePublica("La búsqueda se interrumpió antes de consultar Geoapify.", interrupted);
            }
        }
        lastRequest = now;
    }

    private static List<LugarPublico> mergeUnique(List<LugarPublico> first, List<LugarPublico> second) {
        Map<String, LugarPublico> unique = new LinkedHashMap<>();
        for (LugarPublico place : first) unique.put(placeKey(place), place);
        for (LugarPublico place : second) unique.putIfAbsent(placeKey(place), place);
        return List.copyOf(unique.values());
    }

    private static String placeKey(LugarPublico place) {
        if (place.id() != null && !place.id().isBlank()) return place.id();
        return RelevanceScorer.normalize(place.nombre()) + "|" + RelevanceScorer.normalize(place.ubicacion());
    }

    private Instant retryAt(RestClientException error) {
        if (error instanceof org.springframework.web.client.RestClientResponseException response) {
            String retryAfter = response.getResponseHeaders() == null ? null
                    : response.getResponseHeaders().getFirst("Retry-After");
            if (retryAfter != null) {
                try { return clock.instant().plusSeconds(Math.max(1, Long.parseLong(retryAfter))); }
                catch (NumberFormatException ignored) {
                    try { return java.time.ZonedDateTime.parse(retryAfter,
                            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); }
                    catch (java.time.format.DateTimeParseException ignoredDate) { }
                }
            }
        }
        return clock.instant().plusSeconds(30);
    }

    private static List<LugarPublico> parse(Map<?, ?> payload, CoordenadasPublicas center,
                                             String destination, String kind, boolean withinResolvedCityBoundary) {
        if (payload == null || !(payload.get("features") instanceof List<?> features)) {
            throw new ErrorFuentePublica("Geoapify devolvió una respuesta de lugares no válida.");
        }
        List<LugarPublico> results = new ArrayList<>();
        for (Object value : features) {
            if (!(value instanceof Map<?, ?> feature)
                    || !(feature.get("properties") instanceof Map<?, ?> properties)
                    || !(feature.get("geometry") instanceof Map<?, ?> geometry)
                    || !(geometry.get("coordinates") instanceof List<?> coordinates)
                    || coordinates.size() < 2) continue;
            String name = text(properties.get("name"));
            String id = text(properties.get("place_id"));
            BigDecimal longitude = decimal(coordinates.get(0));
            BigDecimal latitude = decimal(coordinates.get(1));
            if (name == null || name.isBlank() || id == null || !valid(latitude, -90, 90)
                    || !valid(longitude, -180, 180)) continue;
            List<String> categories = stringList(properties.get("categories"));
            String category = categories.isEmpty() ? "Lugar" : humanize(categories.get(0));
            String address = firstNonBlank(text(properties.get("formatted")),
                    joinAddress(properties.get("address_line1"), properties.get("address_line2")));
            Map<?, ?> media = properties.get("wiki_and_media") instanceof Map<?, ?> wiki ? wiki : Map.of();
            String image = image(media);
            String commonsFile = text(media.get("wikimedia_commons"));
            if (image == null && commonsFile != null && commonsFile.startsWith("File:")) {
                image = "https://commons.wikimedia.org/wiki/" + commonsFile.replace(" ", "_");
            }
            String website = safeHttpUrl(text(properties.get("website")));
            String sourceUrl = "https://www.openstreetmap.org/?mlat=" + latitude.toPlainString()
                    + "&mlon=" + longitude.toPlainString() + "&zoom=17";
            Map<String, String> tags = new LinkedHashMap<>();
            tags.put("categories", String.join(",", categories));
            tags.put("provider", "Geoapify");
            tags.put("tourism_role", "lodging".equals(kind) ? "lodging" : role(categories));
            tags.put("distance_km", Double.toString(distanceKm(center.latitud(), center.longitud(), latitude, longitude)));
            double distance = Double.parseDouble(tags.get("distance_km"));
            String resultCity = text(properties.get("city"));
            String destinationCity = destinationCity(destination);
            boolean cityLabelMatches = resultCity == null || destinationCity.isBlank()
                    || RelevanceScorer.normalize(resultCity).equals(RelevanceScorer.normalize(destinationCity));
            boolean sameCity = matchesDestinationScope(withinResolvedCityBoundary, cityLabelMatches, distance);
            if (resultCity != null) tags.put("city", resultCity);
            if (!destinationCity.isBlank()) tags.put("destination_city", destinationCity);
            tags.put("boundary_context", withinResolvedCityBoundary ? "resolved_city_boundary" : "coordinate_circle_fallback");
            String district = firstNonBlank(
                    firstNonBlank(text(properties.get("district")), text(properties.get("city_district"))),
                    firstNonBlank(text(properties.get("suburb")), text(properties.get("county"))));
            if (district != null) tags.put("district", district);
            String country = text(properties.get("country"));
            if (country != null) tags.put("country", country);
            String scope = classifyScope(sameCity, distance);
            tags.put("region_scope", scope);
            tags.put("scope_reason", withinResolvedCityBoundary
                    ? "inside_resolved_city_boundary"
                    : distance >= 100 ? "distance_at_least_100km"
                    : distance > 35 ? "distance_over_35km"
                    : sameCity ? (withinResolvedCityBoundary ? "inside_resolved_city_boundary"
                    : "matching_or_missing_city_label_or_within_35km_fallback")
                    : "city_label_mismatch_without_resolved_boundary");
            if (image != null) tags.put("image", image);
            String wikidata = text(media.get("wikidata"));
            if (wikidata != null && wikidata.matches("Q[1-9][0-9]*")) tags.put("wikidata", wikidata);
            String wikipedia = text(media.get("wikipedia"));
            if (wikipedia != null && wikipedia.matches("[a-z-]+:[^\\r\\n]{1,200}")) tags.put("wikipedia", wikipedia);
            if (commonsFile != null && commonsFile.length() <= 250) tags.put("wikimedia_commons", commonsFile);
            if (website != null) tags.put("website", website);
            results.add(new LugarPublico("geoapify:" + id, name.trim(), text(properties.get("description")),
                    category, address, latitude, longitude, sourceUrl, Map.copyOf(tags)));
        }
        return List.copyOf(results);
    }

    private static int priorityCount(List<LugarPublico> places) {
        return (int) places.stream().filter(place -> RelevanceScorer.tourismScore(place) >= 60).count();
    }

    static String classifyScope(boolean sameCity, double distanceKm) {
        if (distanceKm >= 100) return "EXCURSION_OPTIONAL";
        if (!sameCity || distanceKm > 35) return "REGIONAL_ATTRACTION";
        return "LOCAL_ATTRACTION";
    }

    static boolean matchesDestinationScope(boolean withinResolvedCityBoundary, boolean cityLabelMatches,
                                           double distanceKm) {
        // Geoapify can return a borough/district (e.g. City of Westminster) in `city` for
        // a city-wide coordinate fallback. Proximity is the available geographic evidence there.
        return withinResolvedCityBoundary || cityLabelMatches || distanceKm <= 35;
    }

    private static String role(List<String> categories) {
        if (categories.stream().anyMatch(category -> category.startsWith("catering.restaurant")
                || category.startsWith("catering.cafe"))) return "gastronomy";
        return "attraction";
    }

    private static String destinationCity(String destination) {
        if (destination == null) return "";
        int separator = destination.indexOf(',');
        return (separator < 0 ? destination : destination.substring(0, separator)).trim();
    }

    private static double distanceKm(BigDecimal latitudeA, BigDecimal longitudeA,
                                     BigDecimal latitudeB, BigDecimal longitudeB) {
        double latA = Math.toRadians(latitudeA.doubleValue());
        double latB = Math.toRadians(latitudeB.doubleValue());
        double dLat = latB - latA;
        double dLon = Math.toRadians(longitudeB.doubleValue() - longitudeA.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(latA) * Math.cos(latB) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return Math.round(6_371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 10) / 10d;
    }

    private static String image(Object value) {
        if (value instanceof Map<?, ?> media) {
            String image = text(media.get("image"));
            if (image != null && (image.startsWith("https://") || image.startsWith("http://"))) return image;
        }
        return null;
    }

    private static String safeHttpUrl(String value) {
        if (value == null) return null;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null ? uri.toString() : null;
        } catch (IllegalArgumentException ignored) { return null; }
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static String humanize(String category) {
        int dot = category.lastIndexOf('.');
        String label = (dot >= 0 ? category.substring(dot + 1) : category).replace('_', ' ');
        return label.isBlank() ? "Lugar" : Character.toUpperCase(label.charAt(0)) + label.substring(1);
    }

    private static String joinAddress(Object first, Object second) {
        String left = text(first), right = text(second);
        if (left == null) return right;
        if (right == null) return left;
        return left + ", " + right;
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static void validate(CoordenadasPublicas point) {
        if (!valid(point.latitud(), -90, 90) || !valid(point.longitud(), -180, 180)) {
            throw new ErrorFuentePublica("El geocodificador devolvió coordenadas inválidas.");
        }
    }

    private static boolean valid(BigDecimal value, int min, int max) {
        return value != null && value.compareTo(BigDecimal.valueOf(min)) >= 0
                && value.compareTo(BigDecimal.valueOf(max)) <= 0;
    }

    private static BigDecimal decimal(Object value) {
        try { return value == null ? null : new BigDecimal(value.toString()); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private record CachedPlaces(List<LugarPublico> places, Instant expiresAt) { }
    private record CachedArea(String placeId, Instant expiresAt) { }
}
