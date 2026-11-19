package com.project.back_end_proyecto.service.publicplaces;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WikipediaGeosearchPlaceSourceTest {
    @Test
    void requestsBoundedGeosearchAndRetainsEditoriallyDocumentedCandidates() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://en.wikipedia.org");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenStreetMapPlaceSource geocoder = mock(OpenStreetMapPlaceSource.class);
        when(geocoder.geocodificarDestino("London, United Kingdom")).thenReturn(Optional.of(
                new CoordenadasPublicas(new BigDecimal("51.5072"), new BigDecimal("-0.1276"))));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("en.wikipedia.org/w/api.php")))
                .andExpect(queryParam("generator", "geosearch"))
                .andExpect(queryParam("ggsradius", "10000"))
                .andExpect(header("User-Agent", org.hamcrest.Matchers.containsString("SmartTripPlanner")))
                .andRespond(withSuccess(payloadJson(), MediaType.APPLICATION_JSON));

        var source = new WikipediaGeosearchPlaceSource(builder.build(), geocoder, fixedClock(), true);
        List<LugarPublico> result = source.buscar("London, United Kingdom");

        assertEquals(1, result.size());
        LugarPublico place = result.get(0);
        assertEquals("Westminster Abbey", place.nombre());
        assertEquals("Wikipedia Geosearch", place.etiquetas().get("provider"));
        assertEquals("LOCAL_ATTRACTION", place.etiquetas().get("region_scope"));
        assertTrue(place.etiquetas().get("wikipedia_editorial_evidence").contains("Tourist attractions"));
        assertTrue(place.urlFuente().startsWith("https://en.wikipedia.org/wiki/"));
        assertTrue(RelevanceScorer.tourismScore(place) >= 60);
        server.verify();
    }

    @Test
    void excludesNearbyPagesWithoutTourismEditorialEvidence() {
        Map<?, ?> payload = Map.of("query", Map.of("pages", List.of(
                page(1, "Parking in London", 51.507, -0.128, "Category:Car parks in London"),
                page(2, "London museum", 51.508, -0.129, "Category:Museums in London"))));
        List<LugarPublico> result = WikipediaGeosearchPlaceSource.parse(payload,
                new CoordenadasPublicas(new BigDecimal("51.5072"), new BigDecimal("-0.1276")));
        assertEquals(List.of("London museum"), result.stream().map(LugarPublico::nombre).toList());
    }

    @Test
    void validEmptyGeosearchIsNotAnError() {
        assertTrue(WikipediaGeosearchPlaceSource.parse(Map.of("query", Map.of("pages", List.of())),
                new CoordenadasPublicas(new BigDecimal("48.8566"), new BigDecimal("2.3522"))).isEmpty());
    }

    @Test
    void malformedCoordinatesAndUnsafeArticleUrlsAreDropped() {
        Map<?, ?> payload = Map.of("query", Map.of("pages", List.of(
                page(1, "Outside", 95, 0, "Category:Monuments"),
                Map.of("pageid", 2, "title", "Unsafe", "fullurl", "https://evil.invalid/x",
                        "coordinates", List.of(Map.of("lat", 51.5, "lon", -0.1)),
                        "categories", List.of(Map.of("title", "Category:Tourist attractions"))))));
        assertTrue(WikipediaGeosearchPlaceSource.parse(payload,
                new CoordenadasPublicas(new BigDecimal("51.5072"), new BigDecimal("-0.1276"))).isEmpty());
    }

    private static String payloadJson() {
        return """
                {"query":{"pages":[{"pageid":123,"title":"Westminster Abbey",
                "fullurl":"https://en.wikipedia.org/wiki/Westminster_Abbey","extract":"A documented place.",
                "coordinates":[{"lat":51.4994,"lon":-0.1273}],
                "categories":[{"title":"Category:Tourist attractions in London"},{"title":"Category:Churches in London"}]}]}}
                """;
    }

    private static Map<String, Object> page(int id, String title, double lat, double lon, String... categories) {
        return Map.of("pageid", id, "title", title,
                "fullurl", "https://en.wikipedia.org/wiki/" + title.replace(" ", "_"),
                "extract", "A documented place.",
                "coordinates", List.of(Map.of("lat", lat, "lon", lon)),
                "categories", java.util.Arrays.stream(categories).map(value -> Map.of("title", value)).toList());
    }

    private static Clock fixedClock() { return Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC); }
}
