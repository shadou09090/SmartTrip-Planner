package com.project.back_end_proyecto.service.publicplaces;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.SSLHandshakeException;
import java.util.List;
import java.net.URLDecoder;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenStreetMapLodgingSourceTest {
    private static final String GEOCODE = "https://nominatim.openstreetmap.org/search?q=Cartagena&format=jsonv2&limit=1";
    private static final String PRIMARY = "https://overpass.test/api/interpreter";
    private static final String ALTERNATE = "https://overpass-alt.test/api/interpreter";
    private static final String POINT = "[{\"lat\":\"10.4\",\"lon\":\"-75.5\"}]";
    private static final String HOTEL = "{\"elements\":[{\"type\":\"node\",\"id\":77,\"lat\":10.41,\"lon\":-75.51,\"tags\":{\"name\":\"Hotel de prueba\",\"tourism\":\"hotel\",\"addr:street\":\"Calle 1\"}}]}";
    private static final String LODGING_QUERY = "[out:json][timeout:10];(nwr(around:5000,10.4,-75.5)[\"name\"][\"tourism\"~\"hotel|motel|hostel|guest_house|apartment\"];);out tags center 100;";

    @Test
    void usesPrimaryEndpointWhenAvailable() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andExpect(method(HttpMethod.POST))
                .andExpect(header("User-Agent", "SmartTripPlanner/1.0 (https://github.com/shadou09090/SmartTrip-Planner)"))
                .andExpect(header("Referer", "https://github.com/shadou09090/SmartTrip-Planner"))
                .andExpect(header("Accept", "application/json"))
                .andExpect(formBody(LODGING_QUERY))
                .andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        var result = fixture.source().buscarAlojamientos("Cartagena");

        assertEquals(1, result.size());
        assertEquals("Hotel de prueba", result.get(0).nombre());
        assertEquals("hotel", result.get(0).categoria());
        assertEquals("https://www.openstreetmap.org/node/77", result.get(0).urlFuente());
        fixture.server().verify();
    }

    @Test
    void fallsBackToAlternateWhenPrimaryReturnsUnavailableStatus() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        assertEquals("Hotel de prueba", fixture.source().buscarAlojamientos("Cartagena").get(0).nombre());
        fixture.server().verify();
    }

    @Test
    void fallsBackAfterReadTimeoutAndReturnsTheAlternateResponse() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withException(new HttpTimeoutException("simulated read timeout")));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        assertEquals(1, fixture.source().buscarAlojamientos("Cartagena").size());
        fixture.server().verify();
    }

    @Test
    void fallsBackAfterConnectTimeout() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withException(new HttpConnectTimeoutException("simulated connect timeout")));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        assertEquals(1, fixture.source().buscarAlojamientos("Cartagena").size());
        fixture.server().verify();
    }

    @Test
    void fallsBackAfterDnsFailure() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withException(new UnknownHostException("simulated DNS failure")));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        assertEquals(1, fixture.source().buscarAlojamientos("Cartagena").size());
        fixture.server().verify();
    }

    @Test
    void treatsUnresolvedAddressAsDnsFailureAndFallsBack() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY))
                .andRespond(withException(new IOException("simulated unresolved address",
                        new UnresolvedAddressException())));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        assertEquals(1, fixture.source().buscarAlojamientos("Cartagena").size());
        fixture.server().verify();
    }

    @Test
    void fallsBackAfterTlsHandshakeFailure() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withException(new SSLHandshakeException("simulated TLS failure")));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        assertEquals(1, fixture.source().buscarAlojamientos("Cartagena").size());
        fixture.server().verify();
    }

    @Test
    void honorsRateLimitCooldownForPrimaryAndStillTriesAlternate() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "120");
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        assertEquals(1, fixture.source().buscarAlojamientos("Cartagena").size());
        fixture.server().verify();
    }

    @Test
    void doesNotRetryFailedEndpointsDuringTheirCooldown() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(ErrorFuentePublica.class, () -> fixture.source().buscarAlojamientos("Cartagena"));
        assertThrows(ErrorFuentePublica.class, () -> fixture.source().buscarAlojamientos("Cartagena"));
        fixture.server().verify();
    }

    @Test
    void coolingPrimaryDoesNotPreventUsingHealthyAlternateOnNextSearch() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));
        fixture.server().expect(requestTo("https://nominatim.openstreetmap.org/search?q=Bogota&format=jsonv2&limit=1"))
                .andRespond(withSuccess("[{\"lat\":\"4.6\",\"lon\":\"-74.1\"}]", MediaType.APPLICATION_JSON));
        fixture.server().expect(requestTo(ALTERNATE)).andExpect(method(HttpMethod.POST)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        assertEquals(1, fixture.source().buscarAlojamientos("Cartagena").size());
        fixture.clock().advance(Duration.ofSeconds(1));
        assertEquals(1, fixture.source().buscarAlojamientos("Bogota").size());
        fixture.server().verify();
    }

    @Test
    void probesRecoveredEndpointAfterItsCooldownExpires() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));
        assertThrows(ErrorFuentePublica.class, () -> fixture.source().buscarAlojamientos("Cartagena"));

        assertThrows(ErrorFuentePublica.class, () -> fixture.source().buscarAlojamientos("Cartagena"));
        fixture.clock().advance(Duration.ofMinutes(3).plusSeconds(1));

        assertEquals(1, fixture.source().buscarAlojamientos("Cartagena").size());
        fixture.server().verify();
    }

    @Test
    void generatesBoundedNearbyAttractionQueryAsEncodedPostForm() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        String attractionQuery = "[out:json][timeout:10];(" +
                "nwr(around:15000,10.4,-75.5)[\"name\"][\"tourism\"~\"attraction|museum|gallery|viewpoint|zoo|theme_park|artwork\"];" +
                "nwr(around:15000,10.4,-75.5)[\"name\"][\"historic\"~\"monument|castle|archaeological_site|memorial|palace\"];" +
                "nwr(around:15000,10.4,-75.5)[\"name\"][\"leisure\"~\"park|garden|nature_reserve\"];" +
                "nwr(around:15000,10.4,-75.5)[\"name\"][\"natural\"~\"beach|cape|peak\"];" +
                "nwr(around:15000,10.4,-75.5)[\"name\"][\"amenity\"~\"place_of_worship|theatre|arts_centre\"];" +
                ");out tags center 100;";
        fixture.server().expect(requestTo(PRIMARY)).andExpect(method(HttpMethod.POST))
                .andExpect(header("User-Agent", "SmartTripPlanner/1.0 (https://github.com/shadou09090/SmartTrip-Planner)"))
                .andExpect(header("Referer", "https://github.com/shadou09090/SmartTrip-Planner"))
                .andExpect(header("Accept", "application/json"))
                .andExpect(formBody(attractionQuery))
                .andRespond(withSuccess("{\"elements\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(fixture.source().buscar("Cartagena").isEmpty());
        fixture.server().verify();
    }

    @Test
    void fallsBackOnInvalidHttpEndpointResponse() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        assertEquals(1, fixture.source().buscarAlojamientos("Cartagena").size());
        fixture.server().verify();
    }

    @Test
    void fallsBackWhenSuccessfulHttpResponseHasInvalidOverpassPayload() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withSuccess("{\"unexpected\":[]}", MediaType.APPLICATION_JSON));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withSuccess(HOTEL, MediaType.APPLICATION_JSON));

        assertEquals(1, fixture.source().buscarAlojamientos("Cartagena").size());
        fixture.server().verify();
    }

    @Test
    void returnsARealEmptyResultWhenOverpassHasNoElements() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withSuccess("{\"elements\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(fixture.source().buscarAlojamientos("Cartagena").isEmpty());
        fixture.server().verify();
    }

    @Test
    void propagatesUnavailableWhenEveryConfiguredEndpointFails() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        ErrorFuentePublica error = assertThrows(ErrorFuentePublica.class,
                () -> fixture.source().buscarAlojamientos("Cartagena"));
        assertTrue(error.getMessage().contains("no están disponibles"));
        fixture.server().verify();
    }

    @Test
    void reusesCachedGeocodingResultForTheDestinationLocationEndpoint() {
        var fixture = fixture();
        fixture.server().expect(requestTo("https://nominatim.openstreetmap.org/search?q=Bogota&format=jsonv2&limit=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"lat\":\"4.6\",\"lon\":\"-74.1\"}]", MediaType.APPLICATION_JSON));

        assertTrue(fixture.source().geocodificarDestino("Bogota").isPresent());
        assertEquals("4.6", fixture.source().geocodificarDestino("Bogota").orElseThrow().latitud().toPlainString());
        fixture.server().verify();
    }

    @Test
    void returnsInternationalDestinationCandidatesAndCachesTheSelectedCanonicalPoint() {
        var fixture = fixture();
        fixture.server().expect(request -> {
            assertEquals(HttpMethod.GET, request.getMethod());
            assertTrue(request.getURI().getQuery().contains("addressdetails=1"));
            assertTrue(request.getURI().getQuery().contains("limit=5"));
            assertEquals("SmartTripPlanner/1.0 (https://github.com/shadou09090/SmartTrip-Planner)",
                    request.getHeaders().getFirst("User-Agent"));
        }).andRespond(withSuccess("""
                [{"place_id":1,"display_name":"Kyoto, Kyoto Prefecture, Japan","lat":"35.0116","lon":"135.7681","type":"city","address":{"city":"Kyoto","state":"Kyoto Prefecture","country":"Japan"}},
                 {"place_id":2,"display_name":"Kyoto, Yamashiro, Japan","lat":"34.99","lon":"135.75","type":"administrative","address":{"city":"Kyoto","region":"Yamashiro","country":"Japan"}}]
                """, MediaType.APPLICATION_JSON));

        var candidates = fixture.source().buscarDestinos("Kyoto");

        assertEquals(2, candidates.size());
        assertEquals("Kyoto", candidates.get(0).ciudad());
        assertEquals("Japan", candidates.get(0).pais());
        assertEquals("35.0116", candidates.get(0).latitud().toPlainString());
        assertTrue(fixture.source().geocodificarDestino(candidates.get(0).nombreCompleto()).isPresent());
        fixture.server().verify();
    }

    @Test
    void keepsNominatimIndependentFromOverpassEndpointCooldowns() {
        var fixture = fixture();
        expectGeocode(fixture.server());
        fixture.server().expect(requestTo(PRIMARY)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        fixture.server().expect(requestTo(ALTERNATE)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        fixture.server().expect(requestTo("https://nominatim.openstreetmap.org/search?q=Bogota&format=jsonv2&limit=1"))
                .andRespond(withSuccess("[{\"lat\":\"4.6\",\"lon\":\"-74.1\"}]", MediaType.APPLICATION_JSON));

        assertThrows(ErrorFuentePublica.class, () -> fixture.source().buscarAlojamientos("Cartagena"));
        fixture.clock().advance(Duration.ofSeconds(1));
        assertTrue(fixture.source().geocodificarDestino("Bogota").isPresent());
        fixture.server().verify();
    }

    @Test
    void appliesAnIndependentCooldownAfterNominatimNetworkFailure() {
        var fixture = fixture();
        fixture.server().expect(requestTo("https://nominatim.openstreetmap.org/search?q=Cartagena&format=jsonv2&limit=1"))
                .andRespond(withException(new IOException("simulated DNS failure", new UnknownHostException("nominatim.test"))));

        assertThrows(ErrorFuentePublica.class, () -> fixture.source().geocodificarDestino("Cartagena"));
        ErrorFuentePublica retry = assertThrows(ErrorFuentePublica.class,
                () -> fixture.source().geocodificarDestino("Cartagena"));
        assertTrue(retry.getMessage().contains("Nominatim solicitó una pausa"));
        fixture.server().verify();
    }

    @Test
    void respectsRetryAfterFromNominatimServiceUnavailableResponses() {
        var fixture = fixture();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "120");
        fixture.server().expect(requestTo("https://nominatim.openstreetmap.org/search?q=Barcelona,%20Spain&format=jsonv2&limit=1"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).headers(headers));

        assertThrows(ErrorFuentePublica.class, () -> fixture.source().geocodificarDestino("Barcelona, Spain"));
        ErrorFuentePublica retry = assertThrows(ErrorFuentePublica.class,
                () -> fixture.source().geocodificarDestino("Barcelona, Spain"));
        assertTrue(retry.getMessage().contains("Nominatim solicitó una pausa"));
        fixture.server().verify();
    }

    private static void expectGeocode(MockRestServiceServer server) {
        server.expect(requestTo(GEOCODE)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(POINT, MediaType.APPLICATION_JSON));
    }

    private static org.springframework.test.web.client.RequestMatcher formBody(String expectedQuery) {
        return request -> {
            assertEquals(HttpMethod.POST, request.getMethod());
            assertTrue(request.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED));
            String encoded = ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                    .getBodyAsString(StandardCharsets.UTF_8);
            assertTrue(encoded.startsWith("data="));
            assertEquals(expectedQuery, URLDecoder.decode(encoded.substring("data=".length()), StandardCharsets.UTF_8));
        };
    }

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MutableClock clock = new MutableClock(Instant.parse("2026-11-14T00:00:00Z"));
        var source = new OpenStreetMapPlaceSource(builder.build(), new OpenStreetMapParser(),
                List.of(URI.create(PRIMARY), URI.create(ALTERNATE)), clock, Duration.ofMinutes(3), 80);
        return new Fixture(source, server, clock);
    }

    private record Fixture(OpenStreetMapPlaceSource source, MockRestServiceServer server, MutableClock clock) { }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
