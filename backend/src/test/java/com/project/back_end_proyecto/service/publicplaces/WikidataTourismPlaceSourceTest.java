package com.project.back_end_proyecto.service.publicplaces;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WikidataTourismPlaceSourceTest {
    private static final String RESULT = """
            {"head":{"vars":["item","itemLabel","itemDescription","coordinate","distance","article","tourismTypeLabel","countryLabel","adminAreaLabel"]},
             "results":{"bindings":[{"item":{"type":"uri","value":"http://www.wikidata.org/entity/Q12345"},
              "itemLabel":{"type":"literal","value":"Fixture heritage museum"},
              "itemDescription":{"type":"literal","value":"Fixture description"},
              "coordinate":{"type":"literal","value":"Point(54.3773 24.4539)"},
              "distance":{"type":"literal","value":"3.2"},
              "article":{"type":"uri","value":"https://en.wikipedia.org/wiki/Fixture_museum"},
              "tourismTypeLabel":{"type":"literal","value":"museum"},
              "countryLabel":{"type":"literal","value":"United Arab Emirates"},
              "adminAreaLabel":{"type":"literal","value":"Abu Dhabi"}}]}}
            """;

    @Test
    void sendsBoundedCoordinateQueryAndMapsVerifiableWikidataIdentity() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("https://query.wikidata.org/sparql"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("query=")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Point")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("P31")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("P279*"))))
                .andRespond(withSuccess(RESULT, MediaType.APPLICATION_JSON));

        List<LugarPublico> places = fixture.source.buscar("Abu Dhabi, United Arab Emirates");

        assertEquals(1, places.size());
        LugarPublico place = places.get(0);
        assertEquals("Fixture heritage museum", place.nombre());
        assertEquals(new BigDecimal("24.4539"), place.latitud());
        assertEquals(new BigDecimal("54.3773"), place.longitud());
        assertEquals("Q12345", place.etiquetas().get("wikidata"));
        assertEquals("Wikidata", place.etiquetas().get("provider"));
        assertEquals("LOCAL_ATTRACTION", place.etiquetas().get("region_scope"));
        assertEquals("https://en.wikipedia.org/wiki/Fixture_museum", place.urlFuente());
        assertEquals("Abu Dhabi, United Arab Emirates", place.ubicacion());
        fixture.server.verify();
    }

    @Test
    void treatsValidEmptyBindingsAsNoResults() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("https://query.wikidata.org/sparql"))
                .andRespond(withSuccess("{\"results\":{\"bindings\":[]}}", MediaType.APPLICATION_JSON));
        assertTrue(fixture.source.buscar("Paris, France").isEmpty());
        fixture.server.verify();
    }

    @Test
    void rejectsInvalidGeocoderCoordinatesBeforeCallingWikidata() {
        OpenStreetMapPlaceSource geocoder = mock(OpenStreetMapPlaceSource.class);
        when(geocoder.geocodificarDestino("Tokyo, Japan")).thenReturn(Optional.of(
                new CoordenadasPublicas(new BigDecimal("190"), new BigDecimal("139"))));
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var source = new WikidataTourismPlaceSource(builder.build(), geocoder, fixedClock());

        assertThrows(ErrorFuentePublica.class, () -> source.buscar("Tokyo, Japan"));
        server.verify();
    }

    @Test
    void cooldownPreventsRepeatedRequestsAfterNetworkFailure() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo("https://query.wikidata.org/sparql"))
                .andRespond(request -> { throw new ResourceAccessException("simulated timeout"); });
        assertThrows(ErrorFuentePublica.class, () -> fixture.source.buscar("Santa Marta, Colombia"));
        ErrorFuentePublica cooldown = assertThrows(ErrorFuentePublica.class,
                () -> fixture.source.buscar("Santa Marta, Colombia"));
        assertTrue(cooldown.getMessage().contains("pausa"));
        fixture.server.verify();
    }

    @Test
    void disabledSourceDoesNotMakeExternalRequests() {
        OpenStreetMapPlaceSource geocoder = mock(OpenStreetMapPlaceSource.class);
        var source = new WikidataTourismPlaceSource(RestClient.builder().build(), geocoder, fixedClock(), false);
        assertTrue(source.buscar("Paris").isEmpty());
        verifyNoInteractions(geocoder);
    }

    @Test
    void geocodesAndQueriesFourInternationalDestinationsWithTheirOwnCoordinates() {
        Map<String, CoordenadasPublicas> destinations = Map.of(
                "Santa Marta, Colombia", new CoordenadasPublicas(new BigDecimal("11.2408"), new BigDecimal("-74.1990")),
                "Abu Dhabi, United Arab Emirates", new CoordenadasPublicas(new BigDecimal("24.4539"), new BigDecimal("54.3773")),
                "Paris, France", new CoordenadasPublicas(new BigDecimal("48.8566"), new BigDecimal("2.3522")),
                "Tokyo, Japan", new CoordenadasPublicas(new BigDecimal("35.6762"), new BigDecimal("139.6503")));
        destinations.forEach((destination, point) -> {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            OpenStreetMapPlaceSource geocoder = mock(OpenStreetMapPlaceSource.class);
            when(geocoder.geocodificarDestino(destination)).thenReturn(Optional.of(point));
            server.expect(requestTo("https://query.wikidata.org/sparql"))
                    .andExpect(method(org.springframework.http.HttpMethod.POST))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(point.longitud().toPlainString())))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(point.latitud().toPlainString())))
                    .andRespond(withSuccess("{\"results\":{\"bindings\":[]}}", MediaType.APPLICATION_JSON));
            assertTrue(new WikidataTourismPlaceSource(builder.build(), geocoder, fixedClock())
                    .buscar(destination).isEmpty());
            verify(geocoder).geocodificarDestino(destination);
            server.verify();
        });
    }

    private static Fixture fixture() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenStreetMapPlaceSource geocoder = mock(OpenStreetMapPlaceSource.class);
        when(geocoder.geocodificarDestino(anyString())).thenReturn(Optional.of(
                new CoordenadasPublicas(new BigDecimal("24.4539"), new BigDecimal("54.3773"))));
        return new Fixture(new WikidataTourismPlaceSource(builder.build(), geocoder, fixedClock()), server);
    }

    private static Clock fixedClock() {
        return Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneOffset.UTC);
    }

    private record Fixture(WikidataTourismPlaceSource source, MockRestServiceServer server) { }
}
