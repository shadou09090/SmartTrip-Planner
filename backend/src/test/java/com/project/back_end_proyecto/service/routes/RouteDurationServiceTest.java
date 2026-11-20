package com.project.back_end_proyecto.service.routes;

import com.project.back_end_proyecto.dto.RouteCalculationRequest;
import com.project.back_end_proyecto.dto.RouteCalculationResponse;
import com.project.back_end_proyecto.dto.RoutePoint;
import com.project.back_end_proyecto.service.TripPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RouteDurationServiceTest {
    @Test
    void checksTripAccessAndCachesResultsAcrossAllTravelModes() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        RouteDurationProvider provider = mock(RouteDurationProvider.class);
        when(provider.calculate(any(), any())).thenAnswer(call -> new RouteCalculationResponse.ModeResult(
                ((RouteTravelMode) call.getArgument(1)).name(), true, 600L, 1000L, false, null));
        var service = new RouteDurationService(permissions, provider);
        var request = new RouteCalculationRequest(new RoutePoint(new BigDecimal("4.600"), new BigDecimal("-74.1")),
                new RoutePoint(new BigDecimal("4.7"), new BigDecimal("-74.0")));

        var first = service.calculate(2L, 9L, request);
        var second = service.calculate(2L, 9L, request);

        assertEquals(3, first.routes().size());
        assertEquals(first, second);
        verify(permissions, times(2)).requireRead(2L, 9L);
        verify(provider, times(3)).calculate(any(), any());
    }

    @Test
    void providerFailureForOneModeDoesNotDiscardOtherRouteResults() {
        TripPermissionService permissions = mock(TripPermissionService.class);
        RouteDurationProvider provider = mock(RouteDurationProvider.class);
        when(provider.calculate(any(), eq(RouteTravelMode.WALK)))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No API key"));
        when(provider.calculate(any(), eq(RouteTravelMode.TRANSIT))).thenReturn(
                new RouteCalculationResponse.ModeResult("TRANSIT", true, 900L, 2000L, false, null));
        when(provider.calculate(any(), eq(RouteTravelMode.DRIVE))).thenReturn(
                new RouteCalculationResponse.ModeResult("DRIVE", true, 500L, 1800L, false, null));
        var service = new RouteDurationService(permissions, provider);
        var request = new RouteCalculationRequest(new RoutePoint(new BigDecimal("4.6"), new BigDecimal("-74.1")),
                new RoutePoint(new BigDecimal("4.7"), new BigDecimal("-74.0")));

        var response = service.calculate(1L, 2L, request);

        assertEquals(3, response.routes().size());
        assertFalse(response.routes().get(0).available());
        assertTrue(response.routes().get(1).available());
        assertTrue(response.routes().get(2).available());
    }
}
