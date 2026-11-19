package com.project.back_end_proyecto.service.publicplaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Shared, country-context-preserving geocode cache and single-flight coordinator. */
@Component
@Profile("oracle")
public class DestinoCoordinateCache {
    private static final Logger log = LoggerFactory.getLogger(DestinoCoordinateCache.class);
    private static final Duration TTL = Duration.ofHours(6);
    private final Clock clock;
    private final ConcurrentMap<String, Entry> values = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CompletableFuture<Optional<CoordenadasPublicas>>> inFlight = new ConcurrentHashMap<>();

    public DestinoCoordinateCache() { this(Clock.systemUTC()); }
    DestinoCoordinateCache(Clock clock) { this.clock = clock; }

    public Optional<CoordenadasPublicas> resolve(String destination, String source,
            Supplier<Optional<CoordenadasPublicas>> lookup) {
        String key = normalize(destination);
        if (key.isEmpty()) return Optional.empty();
        Entry cached = values.get(key);
        if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
            log.debug("destination_resolution source={} cache=hit", cached.source());
            return Optional.of(cached.coordinates());
        }
        if (cached != null) values.remove(key, cached);

        String flightKey = key + "|" + normalize(source);
        CompletableFuture<Optional<CoordenadasPublicas>> pending = new CompletableFuture<>();
        CompletableFuture<Optional<CoordenadasPublicas>> existing = inFlight.putIfAbsent(flightKey, pending);
        if (existing != null) {
            log.debug("destination_resolution source={} cache=single_flight_join", source);
            try { return existing.join(); }
            catch (CompletionException failure) { throw propagate(failure.getCause()); }
        }
        try {
            // Another provider may have completed while this provider's flight was being registered.
            Entry shared = values.get(key);
            if (shared != null && shared.expiresAt().isAfter(clock.instant())) {
                Optional<CoordenadasPublicas> reused = Optional.of(shared.coordinates());
                pending.complete(reused);
                log.debug("destination_resolution source={} cache=shared_provider_hit", shared.source());
                return reused;
            }
            Optional<CoordenadasPublicas> result = lookup.get();
            result = result == null ? Optional.empty() : result.filter(DestinoCoordinateCache::valid);
            Entry effectiveEntry = null;
            if (result.isPresent()) {
                Instant now = clock.instant();
                Entry fetched = new Entry(result.get(), source, now.plus(TTL));
                effectiveEntry = values.compute(key, (ignored, current) -> current != null && current.expiresAt().isAfter(now)
                        ? current : fetched);
                result = Optional.of(effectiveEntry.coordinates());
            }
            log.info("destination_resolution source={} cache=miss result={}",
                    effectiveEntry == null ? source : effectiveEntry.source(),
                    result.isPresent() ? "coordinates" : "no_match");
            pending.complete(result);
            return result;
        } catch (RuntimeException failure) {
            Entry seeded = values.get(key);
            if (seeded != null && seeded.expiresAt().isAfter(clock.instant())) {
                Optional<CoordenadasPublicas> reused = Optional.of(seeded.coordinates());
                log.info("destination_resolution source={} cache=concurrent_seed_after_provider_failure", seeded.source());
                pending.complete(reused);
                return reused;
            }
            log.info("destination_resolution source={} cache=miss result=provider_error exceptionType={}",
                    source, failure.getClass().getSimpleName());
            pending.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(flightKey, pending);
        }
    }

    public void remember(String destination, CoordenadasPublicas coordinates, String source) {
        String key = normalize(destination);
        if (key.isEmpty() || !valid(coordinates)) return;
        values.put(key, new Entry(coordinates, source, clock.instant().plus(TTL)));
        log.debug("destination_resolution source={} cache=seeded", source);
    }

    private static RuntimeException propagate(Throwable failure) {
        return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException("Geocoding failed", failure);
    }

    private static boolean valid(CoordenadasPublicas coordinates) {
        return coordinates != null && coordinates.latitud() != null && coordinates.longitud() != null
                && coordinates.latitud().compareTo(java.math.BigDecimal.valueOf(-90)) >= 0
                && coordinates.latitud().compareTo(java.math.BigDecimal.valueOf(90)) <= 0
                && coordinates.longitud().compareTo(java.math.BigDecimal.valueOf(-180)) >= 0
                && coordinates.longitud().compareTo(java.math.BigDecimal.valueOf(180)) <= 0;
    }

    private static String normalize(String destination) {
        return destination == null ? "" : destination.trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private record Entry(CoordenadasPublicas coordinates, String source, Instant expiresAt) { }
}
