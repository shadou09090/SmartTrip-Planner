package com.project.back_end_proyecto.service.publicplaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Verifies a place-tagged Commons image and its reuse metadata before exposing it to clients. */
@Component
public class WikimediaCommonsPhotoService {
    private static final Logger log = LoggerFactory.getLogger(WikimediaCommonsPhotoService.class);
    private static final String API = "https://commons.wikimedia.org/w/api.php";
    private static final String USER_AGENT = "SmartTripPlanner/1.0 (https://github.com/shadou09090/SmartTrip-Planner)";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    private static final Duration MIN_REQUEST_INTERVAL = Duration.ofSeconds(1);
    private static final int MAX_LOOKUPS_PER_RESPONSE = 2;

    private final RestClient client;
    private final Map<String, CachedPhoto> cache = new ConcurrentHashMap<>();
    private Instant lastRequestAt = Instant.EPOCH;
    private int lookupsThisResponse;

    @Autowired
    public WikimediaCommonsPhotoService(RestClient.Builder builder) {
        var requestFactory = com.project.back_end_proyecto.config.HttpRequestFactoryHelper.create(3000, 5000);
        this.client = builder.clone().baseUrl(API).requestFactory(requestFactory).build();
    }

    WikimediaCommonsPhotoService(RestClient client) { this.client = client; }

    /** A source URL must be attached to the specific OSM/Geoapify place; arbitrary search results are not used. */
    public synchronized VerifiedPhoto verifyPlaceImage(String imageReference) {
        String title = commonsFileTitle(imageReference);
        if (title == null) return null;
        return verifyFileTitle(title);
    }

    /** Uses only a Wikidata identifier attached to this exact OSM feature, never a name-only image search. */
    private synchronized VerifiedPhoto verifyWikidataImage(String wikidataId) {
        if (wikidataId == null || !wikidataId.matches("Q[1-9][0-9]*")) return null;
        String cacheKey = "wikidata:" + wikidataId;
        CachedPhoto cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.photo();
        if (!beginLookup()) return null;
        try {
            URI uri = UriComponentsBuilder.fromUriString("https://www.wikidata.org/w/api.php")
                    .queryParam("action", "wbgetentities").queryParam("ids", wikidataId)
                    .queryParam("props", "claims").queryParam("format", "json").build().encode().toUri();
            Map<?, ?> payload = client.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                    .header("User-Agent", USER_AGENT).retrieve().body(Map.class);
            String file = wikidataFile(payload, wikidataId);
            if (file == null) {
                cache.put(cacheKey, new CachedPhoto(null, Instant.now().plus(CACHE_TTL)));
                return null;
            }
            VerifiedPhoto photo = verifyFileTitle(normalizeTitle(file));
            cache.put(cacheKey, new CachedPhoto(photo, Instant.now().plus(CACHE_TTL)));
            return photo;
        } catch (RuntimeException error) {
            log.warn("Wikidata image lookup unavailable: exceptionTypes={}", exceptionTypes(error));
            return null;
        }
    }

    private VerifiedPhoto verifyFileTitle(String title) {
        String cacheKey = "file:" + title;
        CachedPhoto cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.photo();
        if (!beginLookup()) return null;

        try {
            URI uri = UriComponentsBuilder.fromUriString(API)
                    .queryParam("action", "query")
                    .queryParam("format", "json")
                    .queryParam("prop", "imageinfo")
                    .queryParam("iiprop", "url|extmetadata")
                    .queryParam("iiurlwidth", 1200)
                    .queryParam("titles", title)
                    .build().encode().toUri();
            Map<?, ?> payload = client.get().uri(uri).accept(MediaType.APPLICATION_JSON)
                    .header("User-Agent", USER_AGENT).retrieve().body(Map.class);
            VerifiedPhoto verified = parse(payload);
            Instant completedAt = Instant.now();
            cache.put(cacheKey, new CachedPhoto(verified, completedAt.plus(CACHE_TTL)));
            if (cache.size() > 500) cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(completedAt));
            return verified;
        } catch (RuntimeException error) {
            Throwable root = error;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.warn("Commons image metadata lookup unavailable: exceptionTypes={}, rootCauseSummary={}",
                    exceptionTypes(error), safeSummary(root.getMessage()));
            return null;
        }
    }

    public synchronized Map<String, VerifiedPhoto> verifyPlaceImages(List<LugarPublico> places) {
        lookupsThisResponse = 0;
        Map<String, VerifiedPhoto> verified = new LinkedHashMap<>();
        for (LugarPublico place : places) {
            VerifiedPhoto photo = verifyPlaceImage(place.etiquetas().get("image"));
            if (photo == null) photo = verifyWikidataImage(place.etiquetas().get("wikidata"));
            if (photo != null) verified.put(place.id(), photo);
        }
        return Map.copyOf(verified);
    }

    private boolean beginLookup() {
        if (lookupsThisResponse >= MAX_LOOKUPS_PER_RESPONSE) return false;
        Instant now = Instant.now();
        if (!lastRequestAt.equals(Instant.EPOCH)) {
            long waitMillis = MIN_REQUEST_INTERVAL.toMillis() - Duration.between(lastRequestAt, now).toMillis();
            if (waitMillis > 0) {
                try { Thread.sleep(waitMillis); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return false; }
                now = Instant.now();
            }
        }
        lookupsThisResponse++;
        lastRequestAt = now;
        return true;
    }

    private static String wikidataFile(Map<?, ?> payload, String entityId) {
        if (!(payload != null && payload.get("entities") instanceof Map<?, ?> entities
                && entities.get(entityId) instanceof Map<?, ?> entity
                && entity.get("claims") instanceof Map<?, ?> claims
                && claims.get("P18") instanceof List<?> images)) return null;
        for (Object item : images) {
            if (!(item instanceof Map<?, ?> claim) || !(claim.get("mainsnak") instanceof Map<?, ?> snak)
                    || !(snak.get("datavalue") instanceof Map<?, ?> data)) continue;
            Object value = data.get("value");
            if (value instanceof String file && !file.isBlank() && file.length() <= 250) return "File:" + file;
        }
        return null;
    }

    static VerifiedPhoto parse(Map<?, ?> payload) {
        if (!(payload != null && payload.get("query") instanceof Map<?, ?> query
                && query.get("pages") instanceof Map<?, ?> pages)) return null;
        for (Object rawPage : pages.values()) {
            if (!(rawPage instanceof Map<?, ?> page) || !(page.get("imageinfo") instanceof java.util.List<?> info)
                    || info.isEmpty() || !(info.get(0) instanceof Map<?, ?> image)) continue;
            String url = text(image.get("thumburl"));
            if (url == null) url = text(image.get("url"));
            String source = text(image.get("descriptionurl"));
            if (!httpsHost(url, "upload.wikimedia.org") || !httpsHost(source, "commons.wikimedia.org")) continue;
            Map<?, ?> metadata = image.get("extmetadata") instanceof Map<?, ?> values ? values : Map.of();
            String license = metadataValue(metadata, "LicenseShortName");
            String licenseUrl = metadataValue(metadata, "LicenseUrl");
            if (!isReusableLicense(license) || !httpsUrl(licenseUrl)) continue;
            String attribution = cleanHtml(metadataValue(metadata, "Artist"));
            if (attribution == null) attribution = cleanHtml(metadataValue(metadata, "Credit"));
            if (requiresAttribution(license) && attribution == null) continue;
            if (attribution == null) attribution = "Wikimedia Commons";
            return new VerifiedPhoto(url, attribution, license, licenseUrl, source);
        }
        return null;
    }

    private static String commonsFileTitle(String reference) {
        if (reference == null || reference.isBlank()) return null;
        String value = reference.trim();
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if ("commons.wikimedia.org".equalsIgnoreCase(host)) {
                String path = uri.getPath();
                if (path.startsWith("/wiki/File:")) return normalizeTitle(path.substring("/wiki/".length()));
                if (path.startsWith("/wiki/Special:FilePath/")) return normalizeTitle("File:" + path.substring("/wiki/Special:FilePath/".length()));
                if (path.startsWith("/wiki/Special:Redirect/file/")) return normalizeTitle("File:" + path.substring("/wiki/Special:Redirect/file/".length()));
                return null;
            }
            if ("upload.wikimedia.org".equalsIgnoreCase(host)) {
                String path = uri.getPath();
                if (!path.startsWith("/wikipedia/commons/")) return null;
                String fileName = path.substring(path.lastIndexOf('/') + 1);
                if (fileName.isBlank()) return null;
                return normalizeTitle("File:" + URLDecoder.decode(fileName.replace("+", "%2B"), StandardCharsets.UTF_8));
            }
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    private static String normalizeTitle(String raw) {
        String decoded = URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8).trim();
        if (!decoded.regionMatches(true, 0, "File:", 0, 5)) decoded = "File:" + decoded;
        return decoded.length() <= 250 ? decoded : null;
    }

    private static boolean isReusableLicense(String value) {
        if (value == null) return false;
        String normalized = value.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").trim();
        return normalized.matches("cc by(-sa)?( 1\\.0| 2\\.0| 2\\.5| 3\\.0| 4\\.0)?")
                || normalized.matches("cc0( 1\\.0)?") || normalized.contains("public domain");
    }

    private static boolean requiresAttribution(String license) {
        String normalized = license.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("cc by");
    }

    private static String metadataValue(Map<?, ?> metadata, String key) {
        Object raw = metadata.get(key);
        return raw instanceof Map<?, ?> item ? text(item.get("value")) : null;
    }

    private static String cleanHtml(String value) {
        if (value == null) return null;
        String clean = value.replaceAll("(?is)<br\\s*/?>", " ").replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&#160;", " ").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replaceAll("\\s+", " ").trim();
        return clean.isBlank() ? null : clean;
    }

    private static boolean httpsHost(String value, String host) {
        if (!httpsUrl(value)) return false;
        try { return host.equalsIgnoreCase(URI.create(value).getHost()); }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private static boolean httpsUrl(String value) {
        if (value == null) return false;
        try { URI uri = URI.create(value); return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null; }
        catch (IllegalArgumentException ignored) { return false; }
    }

    private static String text(Object value) { return value == null || value.toString().isBlank() ? null : value.toString().trim(); }

    private static String safeSummary(String message) {
        if (message == null) return "unavailable";
        String sanitized = message.replaceAll("[\\r\\n\\t]", " ").replaceAll("\\s+", " ").trim();
        return sanitized.length() <= 180 ? sanitized : sanitized.substring(0, 180);
    }

    private static String exceptionTypes(Throwable error) {
        java.util.ArrayList<String> types = new java.util.ArrayList<>();
        for (Throwable cause = error; cause != null && !types.contains(cause.getClass().getSimpleName()); cause = cause.getCause()) {
            types.add(cause.getClass().getSimpleName());
        }
        return String.join(" -> ", types);
    }

    public record VerifiedPhoto(String url, String attribution, String license, String licenseUrl, String sourceUrl) { }
    private record CachedPhoto(VerifiedPhoto photo, Instant expiresAt) { }
}
