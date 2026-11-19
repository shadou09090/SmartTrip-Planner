package com.project.back_end_proyecto.service.routes;

import com.project.back_end_proyecto.config.PublicPlacesHttpConfiguration;
import com.project.back_end_proyecto.dto.RouteCalculationRequest;
import com.project.back_end_proyecto.dto.RoutePoint;
import com.project.back_end_proyecto.service.TripPermissionService;
import com.project.back_end_proyecto.service.flights.DuffelFlightSearchProvider;
import com.project.back_end_proyecto.service.flights.FlightSearchProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GoogleRoutesProviderContextTest {
    @Test
    void oracleProfileStartsWithoutOrsKeyAndKeepsProviderUnavailableWithoutExternalCalls() {
        try (var context = oracleContext(Map.of("smarttrip.routes.ors.api-key", ""))) {
            assertInstanceOf(OpenRouteServiceDurationProvider.class, context.getBean(RouteDurationProvider.class));
            assertEquals(1, context.getBeansOfType(RouteDurationProvider.class).size());
            assertNotNull(context.getBean(RouteDurationService.class));
            assertInstanceOf(DuffelFlightSearchProvider.class, context.getBean(FlightSearchProvider.class));

            var response = context.getBean(RouteDurationService.class).calculate(1L, 2L, request());

            assertEquals(3, response.routes().size());
            assertFalse(response.routes().get(0).available());
            assertFalse(response.routes().get(1).available());
            assertFalse(response.routes().get(2).available());
            assertTrue(response.routes().get(0).message().contains("automóvil"));
            assertTrue(response.routes().get(2).message().contains("ORS_API_KEY"));
        }
    }

    @Test
    void oracleProfileInjectsOrsProviderWhenKeyIsConfiguredWithoutCallingExternalService() {
        try (var context = oracleContext(Map.of("smarttrip.routes.ors.api-key", "test-placeholder-only"))) {
            assertInstanceOf(OpenRouteServiceDurationProvider.class, context.getBean(RouteDurationProvider.class));
            assertEquals(1, context.getBeansOfType(RouteDurationProvider.class).size());
            assertInstanceOf(DuffelFlightSearchProvider.class, context.getBean(FlightSearchProvider.class));
            assertNotNull(context.getBean(RouteDurationService.class));
        }
    }

    private static AnnotationConfigApplicationContext oracleContext(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles("oracle");
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.registerBean(TripPermissionService.class, () -> mock(TripPermissionService.class));
        context.register(PublicPlacesHttpConfiguration.class, OpenRouteServiceDurationProvider.class,
                RouteDurationService.class, DuffelFlightSearchProvider.class);
        context.refresh();
        return context;
    }

    private static RouteCalculationRequest request() {
        return new RouteCalculationRequest(new RoutePoint(new BigDecimal("4.6"), new BigDecimal("-74.1")),
                new RoutePoint(new BigDecimal("4.7"), new BigDecimal("-74.0")));
    }
}
