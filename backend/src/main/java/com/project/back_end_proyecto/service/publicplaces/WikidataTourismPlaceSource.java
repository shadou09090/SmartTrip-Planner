package com.project.back_end_proyecto.service.publicplaces;

import com.project.back_end_proyecto.service.ProviderFailureDiagnostics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers notable, geolocated tourist places from Wikidata's public SPARQL
 * service.
 */
@Component
@Profile("!local-no-db")
public class WikidataTourismPlaceSource {
    private static final Logger log = LoggerFactory.getLogger(WikidataTourismPlaceSource.class);
    private static final String ENDPOINT = "https://query.wikidata.org/sparql";
    private static final String USER_AGENT = "SmartTripPlanner/1.0 (https://github.com/shadou09090/SmartTrip-Planner)";
    // A 150 km recursive-class query frequently exceeds public WDQS execution
    // limits and
    // returns regional noise. Keep discovery city-scale; excursions are explicitly
    // optional.
    private static final int SEARCH_RADIUS_KM = 40;
    private static final int RESULT_LIMIT = 100;
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final Duration EMPTY_CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration MINIMUM_REQUEST_INTERVAL = Duration.ofSeconds(5);
    private static final Duration DEFAULT_COOLDOWN = Duration.ofMinutes(2);
    private static final Pattern COORDINATE = Pattern
            .compile("Point\\(\\s*(-?\\d+(?:\\.\\d+)?)\\s+(-?\\d+(?:\\.\\d+)?)\\s*\\)");
    private static final String QUERY_TEMPLATE = """
            PREFIX wd: <http://www.wikidata.org/entity/>
            PREFIX wdt: <http://www.wikidata.org/prop/direct/>
            PREFIX wikibase: <http://wikiba.se/ontology#>
            PREFIX bd: <http://www.bigdata.com/rdf#>
            PREFIX geo: <http://www.opengis.net/ont/geosparql#>
            PREFIX schema: <http://schema.org/>
            SELECT DISTINCT ?item ?itemLabel ?itemDescription ?coordinate ?distance ?article ?tourismTypeLabel ?sitelinks ?countryLabel ?adminAreaLabel WHERE {
              SERVICE wikibase:around {
                ?item wdt:P625 ?coordinate.
                bd:serviceParam wikibase:center \"Point(%s %s)\"^^geo:wktLiteral.
                bd:serviceParam wikibase:radius \"%d\".
                bd:serviceParam wikibase:distance ?distance.
              }
              VALUES ?tourismType {
                wd:Q570116 wd:Q33506 wd:Q4989906 wd:Q839954 wd:Q9259 wd:Q16560
                wd:Q32815 wd:Q2416723 wd:Q46169 wd:Q40080
              }
              # Match the curated types directly to keep the spatial result set bounded.
              ?item wdt:P31 ?tourismType.
              ?item wikibase:sitelinks ?sitelinks.
              OPTIONAL { ?article schema:about ?item; schema:isPartOf <https://en.wikipedia.org/>. }
              OPTIONAL { ?item wdt:P17 ?country. }
              OPTIONAL { ?item wdt:P131 ?adminArea. }
              SERVICE wikibase:label { bd:serviceParam wikibase:language \"es,en\". }
            }
            ORDER BY DESC(?sitelinks) ASC(?distance)
            LIMIT %d
            """;

    private final RestClient client;
    private final OpenStreetMapPlaceSource geocoder;
    private final Clock clock;
    private final boolean enabled;
    private final Map<String, CachedPlaces> cache = new ConcurrentHashMap<>();
    private Instant lastRequestAt = Instant.EPOCH;
    private Instant cooldownUntil = Instant.EPOCH;

    @Autowired
    public WikidataTourismPlaceSource(RestClient.Builder builder, OpenStreetMapPlaceSource geocoder,
            @Value("${smarttrip.places.wikidata.enabled:true}") boolean enabled,
            @Value("${smarttrip.places.wikidata.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${smarttrip.places.wikidata.read-timeout-ms:10000}") long readTimeoutMs) {
        var requestFactory = com.project.back_end_proyecto.config.HttpRequestFactoryHelper.create(connectTimeoutMs,
                readTimeoutMs);
        this.client = builder.clone().baseUrl(ENDPOINT).requestFactory(requestFactory).build();
        this.geocoder = geocoder;
        this.enabled = enabled;
        this.clock = Clock.systemUTC();
    }

    WikidataTourismPlaceSource(RestClient client, OpenStreetMapPlaceSource geocoder, Clock clock) {
        this(client, geocoder, clock, true);
    }

    WikidataTourismPlaceSource(RestClient client, OpenStreetMapPlaceSource geocoder, Clock clock, boolean enabled) {
        this.client = client;
        this.geocoder = geocoder;
        this.clock = clock;
        this.enabled = enabled;
    }

    public synchronized List<LugarPublico> buscar(String destination) {
        if (!enabled)
            return List.of();
        String key = normalize(destination);
        CachedPlaces cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant()))
            return cached.places();
        if (clock.instant().isBefore(cooldownUntil)) {
            throw new ErrorFuentePublica("Wikidata solicitó una pausa temporal para proteger su servicio público.");
        }

        CoordenadasPublicas center;
        try {
            center = geocoder.geocodificarDestino(destination).orElse(null);
        } catch (RuntimeException failure) {
            log.warn("Wikidata place search skipped because geocoding failed: exceptionType={}",
                    failure.getClass().getSimpleName());
            throw new ErrorFuentePublica("No se pudo ubicar el destino para consultar atracciones en Wikidata.",
                    failure);
        }
        if (center == null)
            return List.of();
        validate(center);
        respectRequestInterval();

        String query = QUERY_TEMPLATE.formatted(center.longitud().toPlainString(), center.latitud().toPlainString(),
                SEARCH_RADIUS_KM, RESULT_LIMIT);
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", query);
        form.add("format", "json");
        long startedNanos = System.nanoTime();
        try {
            Map<?, ?> response = client.post().uri(ENDPOINT).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.parseMediaType("application/sparql-results+json"))
                    .header("User-Agent", USER_AGENT).body(form).retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            List<LugarPublico> places = parse(response);
            log.info(
                    "provider_request provider=Wikidata operation=tourism endpoint=query.wikidata.org/sparql status=200 duration_ms={} results={}",
                    ProviderFailureDiagnostics.elapsedMillis(startedNanos), places.size());
            Duration ttl = places.isEmpty() ? EMPTY_CACHE_TTL : CACHE_TTL;
            cache.put(key, new CachedPlaces(places, clock.instant().plus(ttl)));
            if (cache.size() > 400) {
                cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(clock.instant()));
                if (cache.size() > 400)
                    cache.clear();
            }
            return places;
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            cooldownUntil = retryAt(failure, status);
            log.warn(
                    "provider_request provider=Wikidata operation=tourism endpoint=query.wikidata.org/sparql status={} stage=http_{} duration_ms={} exceptionTypes={}",
                    status, status, ProviderFailureDiagnostics.elapsedMillis(startedNanos),
                    ProviderFailureDiagnostics.exceptionTypes(failure));
            throw new ErrorFuentePublica("Wikidata no está disponible temporalmente para descubrir atractivos.",
                    failure);
        } catch (RestClientException failure) {
            cooldownUntil = clock.instant().plus(DEFAULT_COOLDOWN);
            log.warn(
                    "provider_request provider=Wikidata operation=tourism endpoint=query.wikidata.org/sparql status=no_http_response stage={} duration_ms={} exceptionTypes={}",
                    ProviderFailureDiagnostics.stage(failure), ProviderFailureDiagnostics.elapsedMillis(startedNanos),
                    ProviderFailureDiagnostics.exceptionTypes(failure));
            throw new ErrorFuentePublica("Wikidata no está disponible temporalmente para descubrir atractivos.",
                    failure);
        } catch (RuntimeException failure) {
            cooldownUntil = clock.instant().plus(DEFAULT_COOLDOWN);
            log.warn(
                    "provider_request provider=Wikidata operation=tourism endpoint=query.wikidata.org/sparql status=200 stage=invalid_response duration_ms={} exceptionType={}",
                    ProviderFailureDiagnostics.elapsedMillis(startedNanos), failure.getClass().getSimpleName());
            throw new ErrorFuentePublica("Wikidata devolvió una respuesta de lugares no válida.", failure);
        }
    }

    static List<LugarPublico> parse(Map<?, ?> payload) {
        if (payload == null || !(payload.get("results") instanceof Map<?, ?> results)
                || !(results.get("bindings") instanceof List<?> bindings)) {
            throw new IllegalArgumentException("Invalid Wikidata SPARQL result envelope");
        }
        Map<String, LugarPublico> places = new LinkedHashMap<>();
        for (Object value : bindings) {
            if (!(value instanceof Map<?, ?> binding))
                continue;
            String entityUrl = value(binding, "item");
            String qid = entityId(entityUrl);
            String name = value(binding, "itemLabel");
            BigDecimal[] point = coordinates(value(binding, "coordinate"));
            if (qid == null || name == null || name.isBlank() || point == null
                    || !valid(point[0], -90, 90) || !valid(point[1], -180, 180))
                continue;
            String article = safeWikipediaUrl(value(binding, "article"));
            String type = value(binding, "tourismTypeLabel");
            BigDecimal distance = decimal(value(binding, "distance"));
            String sitelinks = value(binding, "sitelinks");
            String country = value(binding, "countryLabel");
            String adminArea = value(binding, "adminAreaLabel");
            Map<String, String> tags = new LinkedHashMap<>();
            tags.put("provider", "Wikidata");
            tags.put("wikidata", qid);
            tags.put("wikidata_type", type == null ? "" : type);
            tags.put("categories", type == null ? "" : type);
            if (sitelinks != null && sitelinks.matches("\\d+"))
                tags.put("wikidata_sitelinks", sitelinks);
            if (country != null)
                tags.put("country", country);
            if (adminArea != null)
                tags.put("admin_area", adminArea);
            tags.put("region_scope", classifyScope(distance));
            if (distance != null)
                tags.put("distance_km", distance.stripTrailingZeros().toPlainString());
            if (article != null)
                tags.put("wikipedia_url", article);
            String description = value(binding, "itemDescription");
            String sourceUrl = article != null ? article : "https://www.wikidata.org/wiki/" + qid;
            LugarPublico place = new LugarPublico("wikidata:" + qid, name.trim(), description,
                    category(type), location(adminArea, country), point[0], point[1], sourceUrl, Map.copyOf(tags));
            places.putIfAbsent(qid, place);
        }
        return List.copyOf(places.values());
    }

    private void respectRequestInterval() {
        Instant now = clock.instant();
        if (!lastRequestAt.equals(Instant.EPOCH)) {
            long wait = MINIMUM_REQUEST_INTERVAL.toMillis() - Duration.between(lastRequestAt, now).toMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new ErrorFuentePublica("La consulta pública a Wikidata fue interrumpida.", interrupted);
                }
            }
        }
        lastRequestAt = clock.instant();
    }

    private Instant retryAt(RestClientResponseException failure, int status) {
        long seconds = status == 429 || status == 503 ? 60 : DEFAULT_COOLDOWN.toSeconds();
        String retryAfter = failure.getResponseHeaders() == null ? null
                : failure.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter != null) {
            try {
                seconds = Math.max(seconds, Long.parseLong(retryAfter));
            } catch (NumberFormatException ignored) {
                try {
                    return java.time.ZonedDateTime.parse(retryAfter,
                            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                } catch (java.time.format.DateTimeParseException ignoredDate) {
                }
            }
        }
        return clock.instant().plusSeconds(seconds);
    }

    private static String category(String type) {
        String normalized = RelevanceScorer.normalize(type);
        if (normalized.contains("museum"))
            return "Museo / patrimonio cultural";
        if (normalized.contains("monument") || normalized.contains("historic") || normalized.contains("heritage")) {
            return "Monumento / patrimonio histórico";
        }
        if (normalized.contains("theme park") || normalized.contains("amusement park"))
            return "Parque temático";
        if (normalized.contains("palace"))
            return "Palacio / arquitectura";
        if (normalized.contains("mosque") || normalized.contains("religious"))
            return "Lugar de interés religioso";
        if (normalized.contains("national park") || normalized.contains("beach"))
            return "Naturaleza";
        return "Atracción turística catalogada en Wikidata";
    }

    private static String classifyScope(BigDecimal distanceKm) {
        if (distanceKm == null)
            return "LOCAL_ATTRACTION";
        if (distanceKm.compareTo(BigDecimal.valueOf(100)) >= 0)
            return "EXCURSION_OPTIONAL";
        if (distanceKm.compareTo(BigDecimal.valueOf(35)) > 0)
            return "REGIONAL_ATTRACTION";
        return "LOCAL_ATTRACTION";
    }

    private static String location(String adminArea, String country) {
        if (adminArea == null || adminArea.isBlank())
            return country;
        if (country == null || country.isBlank()
                || RelevanceScorer.normalize(adminArea).equals(RelevanceScorer.normalize(country)))
            return adminArea;
        return adminArea + ", " + country;
    }

    private static String entityId(String uri) {
        if (uri == null || !uri.startsWith("http://www.wikidata.org/entity/"))
            return null;
        String id = uri.substring(uri.lastIndexOf('/') + 1);
        return id.matches("Q[1-9][0-9]*") ? id : null;
    }

    private static BigDecimal[] coordinates(String value) {
        if (value == null)
            return null;
        Matcher matcher = COORDINATE.matcher(value);
        if (!matcher.matches())
            return null;
        BigDecimal longitude = decimal(matcher.group(1));
        BigDecimal latitude = decimal(matcher.group(2));
        return longitude == null || latitude == null ? null : new BigDecimal[] { latitude, longitude };
    }

    private static String safeWikipediaUrl(String value) {
        if (value == null)
            return null;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getHost().equalsIgnoreCase("en.wikipedia.org") ? value : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String value(Map<?, ?> binding, String key) {
        if (!(binding.get(key) instanceof Map<?, ?> item))
            return null;
        Object value = item.get("value");
        return value == null ? null : value.toString();
    }

    private static BigDecimal decimal(String value) {
        if (value == null)
            return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean valid(BigDecimal value, int min, int max) {
        return value != null && value.compareTo(BigDecimal.valueOf(min)) >= 0
                && value.compareTo(BigDecimal.valueOf(max)) <= 0;
    }

    private static void validate(CoordenadasPublicas point) {
        if (!valid(point.latitud(), -90, 90) || !valid(point.longitud(), -180, 180)) {
            throw new ErrorFuentePublica("El geocodificador devolvió coordenadas inválidas para Wikidata.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private record CachedPlaces(List<LugarPublico> places, Instant expiresAt) {
    }
}
