package com.project.back_end_proyecto.service.publicplaces;

import com.project.back_end_proyecto.service.ProviderFailureDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
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
import java.util.concurrent.ConcurrentHashMap;

/** Wikipedia's read-only MediaWiki geosearch supplies editorially documented candidates. */
@Component
@Profile("oracle")
public class WikipediaGeosearchPlaceSource {
    private static final Logger log = LoggerFactory.getLogger(WikipediaGeosearchPlaceSource.class);
    private static final String BASE_URL = "https://en.wikipedia.org";
    private static final String ENDPOINT = "/w/api.php";
    private static final int SEARCH_RADIUS_METERS = 10_000;
    private static final int RESULT_LIMIT = 100;
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration EMPTY_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration MINIMUM_REQUEST_INTERVAL = Duration.ofSeconds(1);
    private static final String USER_AGENT = "SmartTripPlanner/1.0 (https://github.com/shadou09090/SmartTrip-Planner)";
    private static final List<String> EDITORIAL_CATEGORIES = List.of(
            "tourist attraction", "museum", "landmark", "monument", "heritage", "historic site",
            "historic building", "listed building", "palace", "castle", "cathedral", "national park",
            "botanical garden", "art gallery", "theme park", "zoo", "beach", "royal park", "historic district");

    private final RestClient client;
    private final OpenStreetMapPlaceSource geocoder;
    private final Clock clock;
    private final boolean enabled;
    private final Map<String, CachedPlaces> cache = new ConcurrentHashMap<>();
    private Instant lastRequestAt = Instant.EPOCH;

    @Autowired
    public WikipediaGeosearchPlaceSource(RestClient.Builder builder, OpenStreetMapPlaceSource geocoder,
                                         @Value("${smarttrip.places.wikipedia.enabled:true}") boolean enabled,
                                         @Value("${smarttrip.places.wikipedia.connect-timeout-ms:3000}") long connectTimeoutMs,
                                         @Value("${smarttrip.places.wikipedia.read-timeout-ms:8000}") long readTimeoutMs) {
        var httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, connectTimeoutMs))).build();
        var requestFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1000, readTimeoutMs)));
        this.client = builder.clone().baseUrl(BASE_URL).requestFactory(requestFactory).build();
        this.geocoder = geocoder;
        this.clock = Clock.systemUTC();
        this.enabled = enabled;
    }

    WikipediaGeosearchPlaceSource(RestClient client, OpenStreetMapPlaceSource geocoder, Clock clock, boolean enabled) {
        this.client = client;
        this.geocoder = geocoder;
        this.clock = clock;
        this.enabled = enabled;
    }

    public synchronized List<LugarPublico> buscar(String destination) {
        if (!enabled) return List.of();
        String key = normalize(destination);
        CachedPlaces cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) return cached.places();
        CoordenadasPublicas center;
        try {
            center = geocoder.geocodificarDestino(destination).orElse(null);
        } catch (RuntimeException failure) {
            throw new ErrorFuentePublica("No se pudo ubicar el destino para consultar Wikipedia.", failure);
        }
        if (center == null || !valid(center.latitud(), -90, 90) || !valid(center.longitud(), -180, 180)) return List.of();
        respectRequestInterval();

        URI uri = UriComponentsBuilder.fromPath(ENDPOINT)
                .queryParam("action", "query")
                .queryParam("generator", "geosearch")
                .queryParam("ggscoord", center.latitud().toPlainString() + "|" + center.longitud().toPlainString())
                .queryParam("ggsradius", SEARCH_RADIUS_METERS)
                .queryParam("ggslimit", RESULT_LIMIT)
                .queryParam("prop", "coordinates|extracts|categories|info")
                .queryParam("cllimit", "max")
                .queryParam("exintro", 1)
                .queryParam("explaintext", 1)
                .queryParam("exchars", 400)
                .queryParam("inprop", "url")
                .queryParam("format", "json")
                .queryParam("formatversion", 2)
                .build().encode().toUri();
        long started = System.nanoTime();
        try {
            Map<?, ?> payload = client.get().uri(uri).header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json").retrieve().body(Map.class);
            List<LugarPublico> places = parse(payload, center);
            cache.put(key, new CachedPlaces(places, clock.instant().plus(
                    places.isEmpty() ? EMPTY_CACHE_TTL : CACHE_TTL)));
            if (cache.size() > 400) {
                cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.instant()));
                if (cache.size() > 400) cache.clear();
            }
            log.info("provider_request provider=WikipediaGeosearch operation=tourism endpoint=en.wikipedia.org/w/api.php status=200 duration_ms={} results={} radius_m={}",
                    ProviderFailureDiagnostics.elapsedMillis(started), places.size(), SEARCH_RADIUS_METERS);
            return places;
        } catch (RestClientException failure) {
            int status = failure instanceof org.springframework.web.client.RestClientResponseException response
                    ? response.getStatusCode().value() : 0;
            log.warn("provider_request provider=WikipediaGeosearch operation=tourism endpoint=en.wikipedia.org/w/api.php status={} stage={} duration_ms={} exceptionTypes={}",
                    status == 0 ? "no_http_response" : status, ProviderFailureDiagnostics.stage(failure),
                    ProviderFailureDiagnostics.elapsedMillis(started), ProviderFailureDiagnostics.exceptionTypes(failure));
            throw new ErrorFuentePublica("Wikipedia no está disponible temporalmente para descubrir lugares.", failure);
        } catch (RuntimeException failure) {
            log.warn("provider_request provider=WikipediaGeosearch operation=tourism endpoint=en.wikipedia.org/w/api.php status=200 stage=invalid_response duration_ms={} exceptionType={}",
                    ProviderFailureDiagnostics.elapsedMillis(started), failure.getClass().getSimpleName());
            throw new ErrorFuentePublica("Wikipedia devolvió una respuesta de lugares no válida.", failure);
        }
    }

    static List<LugarPublico> parse(Map<?, ?> payload, CoordenadasPublicas center) {
        if (payload == null || !(payload.get("query") instanceof Map<?, ?> query)
                || !(query.get("pages") instanceof List<?> pages)) {
            if (payload != null && payload.get("query") instanceof Map<?, ?> query
                    && Boolean.TRUE.equals(query.get("geosearchempty"))) return List.of();
            throw new IllegalArgumentException("Invalid Wikipedia geosearch response envelope");
        }
        Map<String, LugarPublico> results = new LinkedHashMap<>();
        for (Object raw : pages) {
            if (!(raw instanceof Map<?, ?> page)) continue;
            String title = text(page.get("title"));
            String pageId = text(page.get("pageid"));
            BigDecimal latitude = null, longitude = null;
            if (page.get("coordinates") instanceof List<?> coordinates && !coordinates.isEmpty()
                    && coordinates.get(0) instanceof Map<?, ?> point) {
                latitude = decimal(point.get("lat"));
                longitude = decimal(point.get("lon"));
            }
            if (title == null || pageId == null || !pageId.matches("[1-9][0-9]*")
                    || !valid(latitude, -90, 90) || !valid(longitude, -180, 180)) continue;
            List<String> categories = categories(page.get("categories"));
            List<String> evidence = categories.stream().filter(WikipediaGeosearchPlaceSource::isEditorialCategory).toList();
            if (evidence.isEmpty()) continue;
            String url = safeWikipediaUrl(text(page.get("fullurl")), title);
            if (url == null) continue;
            String extract = text(page.get("extract"));
            BigDecimal distance = BigDecimal.valueOf(distanceKm(center, latitude, longitude));
            Map<String, String> tags = new LinkedHashMap<>();
            tags.put("provider", "Wikipedia Geosearch");
            tags.put("wikipedia_page_id", pageId);
            tags.put("wikipedia_url", url);
            tags.put("wikipedia_editorial_evidence", String.join(";", evidence));
            tags.put("categories", String.join(",", categories));
            tags.put("tourism_role", "attraction");
            tags.put("region_scope", distance.compareTo(BigDecimal.valueOf(35)) <= 0
                    ? "LOCAL_ATTRACTION" : "REGIONAL_ATTRACTION");
            tags.put("distance_km", distance.stripTrailingZeros().toPlainString());
            tags.put("scope_reason", "wikipedia_geosearch_within_10km_of_resolved_destination");
            LugarPublico place = new LugarPublico("wikipedia:" + pageId, title, extract,
                    category(evidence), null, latitude, longitude, url, Map.copyOf(tags));
            results.putIfAbsent(pageId, place);
        }
        return List.copyOf(results.values());
    }

    private static List<String> categories(Object value) {
        if (!(value instanceof List<?> items)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> category) {
                String title = text(category.get("title"));
                if (title != null && title.startsWith("Category:")) result.add(title.substring(9));
            }
        }
        return List.copyOf(result);
    }

    private void respectRequestInterval() {
        Instant now = clock.instant();
        if (!lastRequestAt.equals(Instant.EPOCH)) {
            long waitMillis = MINIMUM_REQUEST_INTERVAL.toMillis() - Duration.between(lastRequestAt, now).toMillis();
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ErrorFuentePublica("La consulta pública a Wikipedia fue interrumpida.", interrupted);
                }
            }
        }
        lastRequestAt = clock.instant();
    }

    private static boolean isEditorialCategory(String category) {
        String normalized = RelevanceScorer.normalize(category);
        return EDITORIAL_CATEGORIES.stream().anyMatch(normalized::contains);
    }

    private static String category(List<String> evidence) {
        String value = RelevanceScorer.normalize(String.join(" ", evidence));
        if (value.contains("museum")) return "Museo / cultura";
        if (value.contains("heritage") || value.contains("historic") || value.contains("listed building")
                || value.contains("monument") || value.contains("landmark")) return "Patrimonio / monumento";
        if (value.contains("park") || value.contains("garden") || value.contains("beach")
                || value.contains("national park")) return "Naturaleza / parque";
        if (value.contains("theme park")) return "Parque temático";
        if (value.contains("cathedral")) return "Arquitectura / patrimonio";
        return "Atracción documentada en Wikipedia";
    }

    private static String safeWikipediaUrl(String url, String title) {
        try {
            URI candidate = url == null ? URI.create("https://en.wikipedia.org/wiki/" + title.replace(" ", "_")) : URI.create(url);
            return "https".equalsIgnoreCase(candidate.getScheme()) && "en.wikipedia.org".equalsIgnoreCase(candidate.getHost())
                    ? candidate.toString() : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }

    private static double distanceKm(CoordenadasPublicas center, BigDecimal latitude, BigDecimal longitude) {
        double lat1 = Math.toRadians(center.latitud().doubleValue()), lat2 = Math.toRadians(latitude.doubleValue());
        double dLat = lat2 - lat1, dLon = Math.toRadians(longitude.doubleValue() - center.longitud().doubleValue());
        double a = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static boolean valid(BigDecimal value, int min, int max) {
        return value != null && value.compareTo(BigDecimal.valueOf(min)) >= 0 && value.compareTo(BigDecimal.valueOf(max)) <= 0;
    }
    private static BigDecimal decimal(Object value) {
        try { return value == null ? null : new BigDecimal(value.toString()); }
        catch (NumberFormatException invalid) { return null; }
    }
    private static String text(Object value) { return value == null || value.toString().isBlank() ? null : value.toString().trim(); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " "); }
    private record CachedPlaces(List<LugarPublico> places, Instant expiresAt) { }
}
