package com.project.back_end_proyecto.service.routes;

import com.project.back_end_proyecto.dto.RouteCalculationRequest;
import com.project.back_end_proyecto.dto.RoutePoint;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleRoutesDurationProviderTest {
    @Test
    void missingKeyDoesNotSendARequest() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ors.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new OpenRouteServiceDurationProvider(builder.build(), "");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> provider.calculate(request(), RouteTravelMode.DRIVE));

        assertEquals(503, error.getStatusCode().value());
        assertTrue(error.getReason().contains("ORS_API_KEY"));
        server.verify();
    }

    @Test
    void postsDrivingCoordinatesAsLongitudeLatitudeAndMapsMetersAndSeconds() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ors.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ors.test/openrouteservice/v2/directions/driving-car/json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "mock-ors-key"))
                .andExpect(header("Authorization", org.hamcrest.Matchers.not(org.hamcrest.Matchers.startsWith("Bearer"))))
                .andExpect(jsonPath("$.coordinates[0][0]").value(-74.1))
                .andExpect(jsonPath("$.coordinates[0][1]").value(4.6))
                .andExpect(jsonPath("$.coordinates[1][0]").value(-74.0))
                .andExpect(jsonPath("$.coordinates[1][1]").value(4.7))
                .andExpect(jsonPath("$.units").value("m"))
                .andRespond(withSuccess("{\"routes\":[{\"summary\":{\"distance\":15321.4,\"duration\":7201.2}}]}",
                        MediaType.APPLICATION_JSON));
        var provider = new OpenRouteServiceDurationProvider(builder.build(), "mock-ors-key");

        var result = provider.calculate(request(), RouteTravelMode.DRIVE);

        assertTrue(result.available());
        assertEquals(15321L, result.distanceMeters());
        assertEquals(7202L, result.durationSeconds());
        assertTrue(result.exceedsTwoHours());
        assertTrue(result.message().contains("120 minutos"));
        server.verify();
    }

    @Test
    void reportsWalkingAndTransitAsUnavailableWithoutCallingOrs() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ors.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new OpenRouteServiceDurationProvider(builder.build(), "mock-ors-key");

        var walking = provider.calculate(request(), RouteTravelMode.WALK);
        var transit = provider.calculate(request(), RouteTravelMode.TRANSIT);

        assertFalse(walking.available());
        assertFalse(transit.available());
        assertTrue(walking.message().contains("automóvil"));
        server.verify();
    }

    @Test
    void noRouteAndInvalidJsonAreNotPresentedAsSuccessfulResults() {
        RestClient.Builder emptyBuilder = RestClient.builder().baseUrl("https://ors.test");
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(emptyBuilder).build();
        emptyServer.expect(requestTo("https://ors.test/openrouteservice/v2/directions/driving-car/json"))
                .andRespond(withSuccess("{\"routes\":[]}", MediaType.APPLICATION_JSON));
        var emptyResult = new OpenRouteServiceDurationProvider(emptyBuilder.build(), "mock-key")
                .calculate(request(), RouteTravelMode.DRIVE);
        assertFalse(emptyResult.available());
        emptyServer.verify();

        RestClient.Builder invalidBuilder = RestClient.builder().baseUrl("https://ors.test");
        MockRestServiceServer invalidServer = MockRestServiceServer.bindTo(invalidBuilder).build();
        invalidServer.expect(requestTo("https://ors.test/openrouteservice/v2/directions/driving-car/json"))
                .andRespond(withSuccess("{\"routes\":[{\"summary\":{\"distance\":\"bad\"}}]}", MediaType.APPLICATION_JSON));
        ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
                () -> new OpenRouteServiceDurationProvider(invalidBuilder.build(), "mock-key")
                        .calculate(request(), RouteTravelMode.DRIVE));
        assertEquals(502, invalid.getStatusCode().value());
        invalidServer.verify();
    }

    @Test
    void mapsAuthenticationQuotaServerAndTimeoutFailuresWithoutLeakingKey() throws Exception {
        assertHttpStatus(HttpStatus.FORBIDDEN, 503);
        assertHttpStatus(HttpStatus.TOO_MANY_REQUESTS, 503);
        assertHttpStatus(HttpStatus.BAD_GATEWAY, 502);

        RestClient.Builder builder = RestClient.builder().baseUrl("https://ors.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ors.test/openrouteservice/v2/directions/driving-car/json"))
                .andRespond(withException(new java.io.IOException("simulated timeout")));
        ResponseStatusException timeout = assertThrows(ResponseStatusException.class,
                () -> new OpenRouteServiceDurationProvider(builder.build(), "secret-mock-value")
                        .calculate(request(), RouteTravelMode.DRIVE));
        assertEquals(502, timeout.getStatusCode().value());
        assertFalse(timeout.getReason().contains("secret-mock-value"));
        server.verify();
    }

    @Test
    void rejectsSameCoordinatesBeforeNetworkCall() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ors.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var point = new RoutePoint(new BigDecimal("4.6"), new BigDecimal("-74.1"));
        var samePoint = new RouteCalculationRequest(point, point);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> new OpenRouteServiceDurationProvider(builder.build(), "mock-key")
                        .calculate(samePoint, RouteTravelMode.DRIVE));
        assertEquals(400, error.getStatusCode().value());
        server.verify();
    }

    private static void assertHttpStatus(HttpStatus providerStatus, int expectedStatus) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://ors.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://ors.test/openrouteservice/v2/directions/driving-car/json"))
                .andRespond(withStatus(providerStatus).contentType(MediaType.APPLICATION_JSON).body("{}"));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> new OpenRouteServiceDurationProvider(builder.build(), "mock-key")
                        .calculate(request(), RouteTravelMode.DRIVE));
        assertEquals(expectedStatus, error.getStatusCode().value());
        server.verify();
    }

    private static RouteCalculationRequest request() {
        return new RouteCalculationRequest(new RoutePoint(new BigDecimal("4.6"), new BigDecimal("-74.1")),
                new RoutePoint(new BigDecimal("4.7"), new BigDecimal("-74.0")));
    }
}
