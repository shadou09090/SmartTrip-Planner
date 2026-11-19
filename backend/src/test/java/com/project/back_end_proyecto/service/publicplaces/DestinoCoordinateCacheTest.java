package com.project.back_end_proyecto.service.publicplaces;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DestinoCoordinateCacheTest {
    @Test
    void concurrentLookupsForTheSameDestinationShareOneProviderRequest() throws Exception {
        var cache = new DestinoCoordinateCache(Clock.systemUTC());
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var requests = new AtomicInteger();
        var workers = Executors.newFixedThreadPool(5);
        try {
            var futures = java.util.stream.IntStream.range(0, 5).mapToObj(index -> workers.submit(() ->
                    cache.resolve("Barcelona, Spain", "Geoapify", () -> {
                        requests.incrementAndGet();
                        started.countDown();
                        try { assertTrue(release.await(2, TimeUnit.SECONDS)); }
                        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                        return Optional.of(new CoordenadasPublicas(new BigDecimal("41.38"), new BigDecimal("2.17")));
                    }))).toList();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            release.countDown();
            for (var future : futures) assertTrue(future.get(2, TimeUnit.SECONDS).isPresent());
            assertEquals(1, requests.get());
        } finally {
            release.countDown();
            workers.shutdownNow();
        }
    }

    @Test
    void failedPrimaryProviderDoesNotBlockAnIndependentFallbackFlight() throws Exception {
        var cache = new DestinoCoordinateCache(Clock.systemUTC());
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var requests = new AtomicInteger();
        var worker = Executors.newSingleThreadExecutor();
        try {
            var primary = worker.submit(() -> cache.resolve("Barcelona, Spain", "Geoapify", () -> {
                requests.incrementAndGet();
                started.countDown();
                try { assertTrue(release.await(2, TimeUnit.SECONDS)); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                throw new IllegalStateException("HTTP 503");
            }));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            var fallbackPoint = point("41.38", "2.17");
            var fallback = cache.resolve("Barcelona, Spain", "Nominatim", () -> {
                requests.incrementAndGet(); return Optional.of(fallbackPoint);
            });
            assertEquals(Optional.of(fallbackPoint), fallback);
            release.countDown();
            assertEquals(Optional.of(fallbackPoint), primary.get(2, TimeUnit.SECONDS));
            assertEquals(2, requests.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void countryContextSeparatesHomonymousDestinations() {
        var cache = new DestinoCoordinateCache(Clock.systemUTC());
        var requests = new AtomicInteger();
        cache.resolve("Springfield, United States", "Geoapify", () -> {
            requests.incrementAndGet(); return Optional.of(point("39", "-89"));
        });
        cache.resolve("Springfield, United Kingdom", "Geoapify", () -> {
            requests.incrementAndGet(); return Optional.of(point("53", "-2"));
        });
        assertEquals(2, requests.get());
    }

    @Test
    void reusesVerifiedCoordinatesAcrossProvidersForTheSameCountryQualifiedDestination() {
        var cache = new DestinoCoordinateCache(Clock.systemUTC());
        var verified = point("41.38", "2.17");
        cache.remember("Barcelona, Spain", verified, "Geoapify");

        var result = cache.resolve(" BARCELONA,   SPAIN ", "Nominatim", () -> {
            fail("a verified cache hit must not send another provider request");
            return Optional.empty();
        });

        assertEquals(Optional.of(verified), result);
    }

    @Test
    void temporaryFailuresAndNoMatchesAreNotCached() {
        var cache = new DestinoCoordinateCache(Clock.systemUTC());
        var requests = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> cache.resolve("Cali, Colombia", "Nominatim", () -> {
            requests.incrementAndGet(); throw new IllegalStateException("HTTP 503");
        }));
        assertTrue(cache.resolve("Cali, Colombia", "Nominatim", () -> {
            requests.incrementAndGet(); return Optional.empty();
        }).isEmpty());
        assertTrue(cache.resolve("Cali, Colombia", "Geoapify", () -> {
            requests.incrementAndGet(); return Optional.of(point("3.45", "-76.53"));
        }).isPresent());
        assertEquals(3, requests.get());
    }

    private static CoordenadasPublicas point(String latitude, String longitude) {
        return new CoordenadasPublicas(new BigDecimal(latitude), new BigDecimal(longitude));
    }
}
