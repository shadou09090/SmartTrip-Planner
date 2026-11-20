package com.project.back_end_proyecto.service.flights;

import com.project.back_end_proyecto.dto.FlightSearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DuffelFlightSearchProviderTest {
    @Test
    void suggestsPlacesUsingEncodedQueryAndDuffelHeaders() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://duffel.test/places/suggestions?query=Bogota%20D.C."))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Accept", "application/json"))
                .andExpect(header("Duffel-Version", "v2"))
                .andExpect(header("Authorization", "Bearer mock-access-token"))
                .andRespond(withSuccess("{\"data\":[{\"name\":\"Bogota\",\"iata_code\":\"BOG\",\"type\":\"city\",\"iata_country_code\":\"CO\"}]}", MediaType.APPLICATION_JSON));

        var suggestions = new DuffelFlightSearchProvider(builder.build(), "mock-access-token")
                .suggestPlaces("Bogota D.C.");

        assertEquals(1, suggestions.size());
        assertEquals("BOG", suggestions.get(0).iataCode());
        server.verify();
    }

    @Test
    void missingCredentialReturnsServiceUnavailableForSuggestionsWithoutRequest() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new DuffelFlightSearchProvider(builder.build(), "");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> provider.suggestPlaces("Bogota"));

        assertEquals(503, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("DUFFEL_ACCESS_TOKEN"));
        server.verify();
    }

    @Test
    void emptySuggestionResponseReturnsEmptyList() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://duffel.test/places/suggestions?query=Nowhere"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        var suggestions = new DuffelFlightSearchProvider(builder.build(), "mock-access-token")
                .suggestPlaces("Nowhere");

        assertTrue(suggestions.isEmpty());
        server.verify();
    }

    @Test
    void mapsAuthenticationAndRateLimitResponsesToServiceUnavailable() {
        assertSuggestionHttpStatusMapsTo(HttpStatus.UNAUTHORIZED, 503);
        assertSuggestionHttpStatusMapsTo(HttpStatus.FORBIDDEN, 503);
        assertSuggestionHttpStatusMapsTo(HttpStatus.TOO_MANY_REQUESTS, 503);
    }

    @Test
    void mapsSuggestionTimeoutAndInvalidJsonToBadGateway() throws Exception {
        RestClient.Builder timeoutBuilder = configuredBuilder();
        MockRestServiceServer timeoutServer = MockRestServiceServer.bindTo(timeoutBuilder).build();
        timeoutServer.expect(requestTo("https://duffel.test/places/suggestions?query=Bogota"))
                .andRespond(withException(new java.io.IOException("simulated timeout")));
        var timeoutProvider = new DuffelFlightSearchProvider(timeoutBuilder.build(), "mock-access-token");
        ResponseStatusException timeout = assertThrows(ResponseStatusException.class,
                () -> timeoutProvider.suggestPlaces("Bogota"));
        assertEquals(502, timeout.getStatusCode().value());
        timeoutServer.verify();

        RestClient.Builder invalidBuilder = configuredBuilder();
        MockRestServiceServer invalidServer = MockRestServiceServer.bindTo(invalidBuilder).build();
        invalidServer.expect(requestTo("https://duffel.test/places/suggestions?query=Bogota"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        var invalidProvider = new DuffelFlightSearchProvider(invalidBuilder.build(), "mock-access-token");
        ResponseStatusException invalid = assertThrows(ResponseStatusException.class,
                () -> invalidProvider.suggestPlaces("Bogota"));
        assertEquals(502, invalid.getStatusCode().value());
        invalidServer.verify();
    }

    private static RestClient.Builder configuredBuilder() {
        return RestClient.builder().baseUrl("https://duffel.test")
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("Duffel-Version", "v2");
    }

    private static void assertSuggestionHttpStatusMapsTo(HttpStatus providerStatus, int expectedStatus) {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://duffel.test/places/suggestions?query=Bogota"))
                .andRespond(withStatus(providerStatus).contentType(MediaType.APPLICATION_JSON).body("{}"));
        var provider = new DuffelFlightSearchProvider(builder.build(), "mock-access-token");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> provider.suggestPlaces("Bogota"));

        assertEquals(expectedStatus, exception.getStatusCode().value());
        server.verify();
    }

    @Test
    void missingCredentialReturnsServiceUnavailableWithoutNetworkRequest() {
        DuffelFlightSearchProvider provider = new DuffelFlightSearchProvider(RestClient.builder(), "https://api.test", "");
        var request = new FlightSearchRequest("BOG", "MDE", LocalDate.now().plusDays(20), null, 1, "economy");
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> provider.search(request));
        assertEquals(503, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("DUFFEL_ACCESS_TOKEN"));
    }

    @Test
    void mapsOfferPriceTimesStopsAndBaggageFromMockedProviderResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("https://duffel.test").build();
        DuffelFlightSearchProvider provider = new DuffelFlightSearchProvider(client, "duffel_test_mock");
        String response = """
                {"data":{"offers":[{"id":"off_test","total_amount":"230.50","total_currency":"USD",
                "expires_at":"2030-01-01T12:00:00Z","slices":[{"duration":"PT4H30M","segments":[
                {"departing_at":"2030-02-01T08:00:00","arriving_at":"2030-02-01T10:00:00",
                "origin":{"name":"Bogota","iata_code":"BOG"},"destination":{"name":"Panama","iata_code":"PTY"},
                "marketing_carrier":{"name":"Test Air"},"marketing_carrier_flight_number":"123",
                "operating_carrier":{"name":"Operating Test Air"},
                "passengers":[{"baggages":[{"type":"checked","quantity":1}]}]},
                {"departing_at":"2030-02-01T11:00:00","arriving_at":"2030-02-01T12:30:00",
                "origin":{"name":"Panama","iata_code":"PTY"},"destination":{"name":"Medellin","iata_code":"MDE"},
                "marketing_carrier":{"name":"Test Air"},"marketing_carrier_flight_number":"456"}]}]}]}}
                """;
        server.expect(requestTo("https://duffel.test/air/offer_requests?return_offers=true&supplier_timeout=10000"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        var result = provider.search(new FlightSearchRequest("BOG", "MDE", LocalDate.of(2030, 2, 1), null, 1, "economy"));

        assertTrue(result.testData());
        assertEquals("Duffel", result.provider());
        assertEquals(1, result.offers().size());
        var offer = result.offers().get(0);
        assertEquals("230.50", offer.totalAmount().toPlainString());
        assertEquals("USD", offer.currency());
        assertNotNull(offer.expiresAt());
        var slice = offer.slices().get(0);
        assertEquals(1, slice.stops());
        assertEquals("Bogota (BOG)", slice.origin());
        assertEquals("Medellin (MDE)", slice.destination());
        assertEquals("Test Air", slice.airline());
        assertEquals("Operating Test Air", slice.segments().get(0).operatingCarrier());
        assertEquals(2, slice.segments().size());
        assertEquals("1 checked", slice.baggageAllowance());
        assertTrue(result.notice().contains("sandbox"));
        server.verify();
    }

    @Test
    void retrievesCurrentOfferByIdAndUsesProviderCanonicalData() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://duffel.test/air/offers/off_verified"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Duffel-Version", "v2"))
                .andExpect(header("Authorization", "Bearer mock-access-token"))
                .andRespond(withSuccess("""
                        {"data":{"id":"off_verified","total_amount":"1250.00","total_currency":"COP",
                        "expires_at":"2030-01-01T12:00:00Z","slices":[{"segments":[
                        {"departing_at":"2030-02-01T08:00:00Z","arriving_at":"2030-02-01T09:00:00Z",
                        "origin":{"name":"Bogota","iata_code":"BOG"},
                        "destination":{"name":"Medellin","iata_code":"MDE"},
                        "marketing_carrier":{"name":"Verified Air"},
                        "marketing_carrier_flight_number":"VA123"}]}]}}
                        """, MediaType.APPLICATION_JSON));

        var provider = new DuffelFlightSearchProvider(builder.build(), "mock-access-token");
        var offer = provider.retrieveOffer("off_verified");

        assertEquals("off_verified", offer.id());
        assertEquals("1250.00", offer.totalAmount().toPlainString());
        assertEquals("COP", offer.currency());
        assertEquals("Verified Air", offer.slices().get(0).segments().get(0).marketingCarrier());
        server.verify();
    }

    @Test
    void rejectsExpiredDuffelOffer() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://duffel.test/air/offers/off_expired"))
                .andRespond(withSuccess("""
                        {"data":{"id":"off_expired","total_amount":"10.00","total_currency":"COP",
                        "expires_at":"2020-01-01T12:00:00Z","slices":[{"segments":[
                        {"departing_at":"2030-02-01T08:00:00Z","arriving_at":"2030-02-01T09:00:00Z",
                        "origin":{"iata_code":"BOG"},"destination":{"iata_code":"MDE"}}]}]}}
                        """, MediaType.APPLICATION_JSON));
        var provider = new DuffelFlightSearchProvider(builder.build(), "mock-access-token");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> provider.retrieveOffer("off_expired"));

        assertEquals(410, exception.getStatusCode().value());
        server.verify();
    }

    @Test
    void missingCredentialPreventsOfferVerificationRequest() {
        RestClient.Builder builder = configuredBuilder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new DuffelFlightSearchProvider(builder.build(), "");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> provider.retrieveOffer("off_verified"));

        assertEquals(503, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("DUFFEL_ACCESS_TOKEN"));
        server.verify();
    }

    @Test
    void mapsEmptyMockOfferListToEmptyResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("https://duffel.test").build();
        DuffelFlightSearchProvider provider = new DuffelFlightSearchProvider(client, "duffel_test_mock");
        server.expect(requestTo("https://duffel.test/air/offer_requests?return_offers=true&supplier_timeout=10000"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"data\":{\"offers\":[]}}", MediaType.APPLICATION_JSON));

        var result = provider.search(new FlightSearchRequest("BOG", "MDE", LocalDate.of(2030, 2, 1), null, 1, "economy"));

        assertTrue(result.offers().isEmpty());
        server.verify();
    }

    @Test
    void sendsPassengerTypesAndKeepsTheOfferExpiryOffset() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("https://duffel.test").build();
        DuffelFlightSearchProvider provider = new DuffelFlightSearchProvider(client, "duffel_test_mock");
        server.expect(requestTo("https://duffel.test/air/offer_requests?return_offers=true&supplier_timeout=10000"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.data.passengers[0].type").value("adult"))
                .andExpect(jsonPath("$.data.passengers[1].type").value("child"))
                .andExpect(jsonPath("$.data.passengers[2].type").value("infant_without_seat"))
                .andRespond(withSuccess("""
                        {"data":{"offers":[{"id":"off_test","total_amount":"20.00","total_currency":"USD",
                        "expires_at":"2030-01-01T12:00:00+02:00","slices":[{"segments":[
                        {"departing_at":"2030-02-01T08:00:00Z","arriving_at":"2030-02-01T10:00:00Z",
                        "origin":{"iata_code":"BOG"},"destination":{"iata_code":"MDE"},
                        "operating_carrier":{"name":"Operator"}}]}]}]}}
                        """, MediaType.APPLICATION_JSON));

        var request = new FlightSearchRequest("BOG", "MDE", LocalDate.of(2030, 2, 1), null,
                3, 1, 1, 1, "economy");
        var result = provider.search(request);

        assertEquals(java.time.OffsetDateTime.parse("2030-01-01T12:00:00+02:00"), result.offers().get(0).expiresAt());
        server.verify();
    }
}
