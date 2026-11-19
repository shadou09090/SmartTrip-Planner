package com.project.back_end_proyecto.service.routes;

import com.project.back_end_proyecto.dto.RouteCalculationRequest;
import com.project.back_end_proyecto.dto.RouteCalculationResponse;
import com.project.back_end_proyecto.service.TripPermissionService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Profile("oracle")
public class RouteDurationService {
    private static final Logger log = LoggerFactory.getLogger(RouteDurationService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration MINIMUM_REQUEST_INTERVAL = Duration.ofSeconds(2);
    private final TripPermissionService permissions;
    private final RouteDurationProvider provider;
    private final Map<RouteKey, CachedRoutes> cache = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastRequest = new AtomicReference<>(Instant.EPOCH);

    public RouteDurationService(TripPermissionService permissions, RouteDurationProvider provider) {
        this.permissions = permissions;
        this.provider = provider;
    }

    public RouteCalculationResponse calculate(Long userId, Long tripId, RouteCalculationRequest request) {
        permissions.requireRead(userId, tripId);
        RouteKey key = new RouteKey(request.origin().latitude().stripTrailingZeros().toPlainString(),
                request.origin().longitude().stripTrailingZeros().toPlainString(),
                request.destination().latitude().stripTrailingZeros().toPlainString(),
                request.destination().longitude().stripTrailingZeros().toPlainString());
        CachedRoutes cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.response();
        Instant now = Instant.now();
        while (true) {
            Instant previous = lastRequest.get();
            if (Duration.between(previous, now).compareTo(MINIMUM_REQUEST_INTERVAL) < 0) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Espera un momento antes de volver a calcular traslados.");
            }
            if (lastRequest.compareAndSet(previous, now)) break;
        }
        List<RouteCalculationResponse.ModeResult> routes = java.util.Arrays.stream(RouteTravelMode.values())
                .map(mode -> calculateMode(request, mode)).toList();
        RouteCalculationResponse response = new RouteCalculationResponse(routes);
        if (cache.size() >= 500) cache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        if (cache.size() >= 500) cache.clear();
        cache.put(key, new CachedRoutes(response, now.plus(CACHE_TTL)));
        return response;
    }

    private RouteCalculationResponse.ModeResult calculateMode(RouteCalculationRequest request, RouteTravelMode mode) {
        try {
            return provider.calculate(request, mode);
        } catch (ResponseStatusException exception) {
            log.warn("Route provider unavailable for mode {}: HTTP {}", mode, exception.getStatusCode().value());
            return new RouteCalculationResponse.ModeResult(mode.name(), false, null, null, false,
                    exception.getReason() == null ? "No se pudo calcular esta modalidad." : exception.getReason());
        } catch (RuntimeException exception) {
            log.warn("Route provider failed for mode {}: exceptionType={}", mode, exception.getClass().getSimpleName());
            return new RouteCalculationResponse.ModeResult(mode.name(), false, null, null, false,
                    "No se pudo calcular esta modalidad por un error del proveedor.");
        }
    }

    private record RouteKey(String originLat, String originLon, String destinationLat, String destinationLon) { }
    private record CachedRoutes(RouteCalculationResponse response, Instant expiresAt) { }
}
