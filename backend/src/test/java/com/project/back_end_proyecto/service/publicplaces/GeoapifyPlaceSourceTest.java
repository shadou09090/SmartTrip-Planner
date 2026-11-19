package com.project.back_end_proyecto.service.publicplaces;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

class GeoapifyPlaceSourceTest {
    private static final String POINT = "{\"type\":\"FeatureCollection\",\"features\":[{"
            + "\"type\":\"Feature\",\"properties\":{\"place_id\":\"test-place\",\"name\":\"Lugar real de fixture\","
            + "\"formatted\":\"Calle 1, Ciudad\",\"categories\":[\"accommodation.hotel\"],"
            + "\"wiki_and_media\":{\"image\":\"https://example.test/photo.jpg\"}},"
            + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[-74.1,4.6]}}]}";
    private static final String TOURISM_POINT = "{\"type\":\"FeatureCollection\",\"features\":[{"
            + "\"type\":\"Feature\",\"properties\":{\"place_id\":\"tourism-fixture\",\"name\":\"Tourism fixture attraction\","
            + "\"formatted\":\"Historic center\",\"categories\":[\"tourism.attraction\"]},"
            + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[-74.1,4.6]}}]}";

    @Test
    void cityDistrictLabelDoesNotRejectNearbyPlacesWhenNoAdministrativeBoundaryWasResolved() {
        assertTrue(GeoapifyPlaceSource.matchesDestinationScope(false, false, 12));
        assertFalse(GeoapifyPlaceSource.matchesDestinationScope(false, false, 36));
        assertTrue(GeoapifyPlaceSource.matchesDestinationScope(true, false, 36));
        assertEquals("LOCAL_ATTRACTION", GeoapifyPlaceSource.classifyScope(
                GeoapifyPlaceSource.matchesDestinationScope(false, false, 12), 12));
        assertEquals("REGIONAL_ATTRACTION", GeoapifyPlaceSource.classifyScope(
                GeoapifyPlaceSource.matchesDestinationScope(false, false, 36), 36));
        assertEquals("EXCURSION_OPTIONAL", GeoapifyPlaceSource.classifyScope(false, 100));
    }

    @Test
    void searchesGeoapifyWithBoundedRadiusAndParsesCoordinatesImageAndAttributionLink() {
        var fixture = fixture("test-key");
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=accommodation&filter=circle:-74.1,4.6,5000&bias=proximity:-74.1,4.6&limit=50&apiKey=test-key"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(POINT, MediaType.APPLICATION_JSON));

        LugarPublico result = fixture.source.buscarAlojamientos("Bogota").get(0);

        assertEquals("Lugar real de fixture", result.nombre());
        assertEquals("Hotel", result.categoria());
        assertEquals("Calle 1, Ciudad", result.ubicacion());
        assertEquals(new BigDecimal("4.6"), result.latitud());
        assertEquals(new BigDecimal("-74.1"), result.longitud());
        assertEquals("https://example.test/photo.jpg", result.etiquetas().get("image"));
        assertTrue(result.urlFuente().contains("mlat=4.6"));
        fixture.server.verify();
    }

    @Test
    void parsesPoiSearchAndTreatsValidEmptyFeatureCollectionAsNoResults() {
        var fixture = fixture("test-key");
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=tourism.attraction,tourism.sights,entertainment.museum,entertainment.culture.gallery,entertainment.theme_park,entertainment.aquarium,leisure.park.nature_reserve,natural,beach,religion&filter=circle:-74.1,4.6,15000&bias=proximity:-74.1,4.6&limit=100&apiKey=test-key"))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=tourism,entertainment,natural,beach,religion,leisure.park&filter=circle:-74.1,4.6,40000&bias=proximity:-74.1,4.6&limit=100&apiKey=test-key"))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=tourism,entertainment,natural,beach,religion,leisure.park&filter=circle:-74.1,4.6,100000&bias=proximity:-74.1,4.6&limit=100&apiKey=test-key"))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=catering.restaurant,catering.cafe&filter=circle:-74.1,4.6,15000&bias=proximity:-74.1,4.6&limit=100&apiKey=test-key"))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        assertTrue(fixture.source.buscar("Bogota").isEmpty());
        fixture.server.verify();
    }

    @Test
    void expandsTourismCategoriesAndRadiusWhenInitialQueryHasTooFewResults() {
        var fixture = fixture("test-key");
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=tourism.attraction,tourism.sights,entertainment.museum,entertainment.culture.gallery,entertainment.theme_park,entertainment.aquarium,leisure.park.nature_reserve,natural,beach,religion&filter=circle:-74.1,4.6,15000&bias=proximity:-74.1,4.6&limit=100&apiKey=test-key"))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=tourism,entertainment,natural,beach,religion,leisure.park&filter=circle:-74.1,4.6,40000&bias=proximity:-74.1,4.6&limit=100&apiKey=test-key"))
                .andRespond(withSuccess(TOURISM_POINT, MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=tourism,entertainment,natural,beach,religion,leisure.park&filter=circle:-74.1,4.6,100000&bias=proximity:-74.1,4.6&limit=100&apiKey=test-key"))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=catering.restaurant,catering.cafe&filter=circle:-74.1,4.6,15000&bias=proximity:-74.1,4.6&limit=100&apiKey=test-key"))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));

        var places = fixture.source.buscar("Bogota");

        assertEquals(1, places.size());
        assertEquals("Tourism fixture attraction", places.get(0).nombre());
        assertEquals("LOCAL_ATTRACTION", places.get(0).etiquetas().get("region_scope"));
        fixture.server.verify();
    }

    @Test
    void geocodesThroughGeoapifyIndependentlyAndKeepsCountryContext() {
        var fixture = fixture("test-key");
        fixture.server.expect(requestTo(containsString("/v1/geocode/search?")))
                .andExpect(queryParam("text", "Barcelona,%20Spain"))
                .andExpect(queryParam("type", "city"))
                .andExpect(queryParam("apiKey", "test-key"))
                .andRespond(withSuccess("{\"results\":[{\"lat\":41.38,\"lon\":2.17,\"city\":\"Barcelona\",\"country\":\"Spain\",\"formatted\":\"Barcelona, Spain\"}]}", MediaType.APPLICATION_JSON));

        var point = fixture.source.geocodificarDestino("Barcelona, Spain").orElseThrow();

        assertEquals(new BigDecimal("41.38"), point.latitud());
        assertEquals(new BigDecimal("2.17"), point.longitud());
        org.mockito.Mockito.verifyNoInteractions(fixture.geocoder);
        fixture.server.verify();
    }

    @Test
    void preservesInternationalDestinationTextWhenGeocoding() {
        var fixture = fixture("test-key");
        fixture.coordinates.remember("Abu Dhabi, United Arab Emirates",
                new CoordenadasPublicas(new BigDecimal("24.4539"), new BigDecimal("54.3773")), "fixture");
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=accommodation&filter=circle:54.3773,24.4539,5000&bias=proximity:54.3773,24.4539&limit=50&apiKey=test-key"))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(fixture.source.buscarAlojamientos("Abu Dhabi, United Arab Emirates").isEmpty());
        org.mockito.Mockito.verifyNoInteractions(fixture.geocoder);
        fixture.server.verify();
    }

    @Test
    void usesResolvedCityBoundaryForWorldCoverageAndCachesTheBoundaryDuringProgressiveSearch() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.geoapify.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenStreetMapPlaceSource geocoder = mock(OpenStreetMapPlaceSource.class);
        var clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneId.of("UTC"));
        var coordinates = new DestinoCoordinateCache(clock);
        coordinates.remember("Paris, France", new CoordenadasPublicas(new BigDecimal("48.8566"), new BigDecimal("2.3522")), "fixture");
        var source = new GeoapifyPlaceSource(builder.build(), "test-key", geocoder, coordinates, clock, true);
        server.expect(requestTo(containsString("/v1/geocode/search?")))
                .andExpect(queryParam("text", "Paris,%20France"))
                .andExpect(queryParam("type", "city"))
                .andRespond(withSuccess("{\"results\":[{\"place_id\":\"paris-boundary\",\"lat\":48.8566,\"lon\":2.3522,\"city\":\"Paris\"}]}", MediaType.APPLICATION_JSON));
        for (int index = 0; index < 4; index++) {
            server.expect(requestTo(containsString("/v2/places?")))
                    .andExpect(queryParam("filter", "place:paris-boundary"))
                    .andExpect(queryParam("limit", "100"))
                    .andExpect(request -> assertFalse(request.getURI().getRawQuery().contains("bias=")))
                    .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        }
        for (int index = 0; index < 4; index++) {
            server.expect(requestTo(containsString("/v2/places?")))
                    .andExpect(queryParam("filter", "place:paris-boundary"))
                    .andExpect(queryParam("limit", "50"))
                    .andExpect(queryParam("bias", org.hamcrest.Matchers.startsWith("rect:")))
                    .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        }

        assertTrue(source.buscar("Paris, France").isEmpty());
        server.verify();
        org.mockito.Mockito.verifyNoInteractions(geocoder);
    }

    @Test
    void treatsResultsInsideResolvedCityBoundaryAsLocalDespiteBoroughNameDifferences() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.geoapify.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenStreetMapPlaceSource geocoder = mock(OpenStreetMapPlaceSource.class);
        var clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneId.of("UTC"));
        var coordinates = new DestinoCoordinateCache(clock);
        coordinates.remember("London, United Kingdom", new CoordenadasPublicas(new BigDecimal("51.5072"), new BigDecimal("-0.1276")), "fixture");
        var source = new GeoapifyPlaceSource(builder.build(), "test-key", geocoder, coordinates, clock, true);
        server.expect(requestTo(containsString("/v1/geocode/search?")))
                .andRespond(withSuccess("{\"results\":[{\"place_id\":\"london-boundary\",\"lat\":51.5072,\"lon\":-0.1276,\"city\":\"London\"}]}", MediaType.APPLICATION_JSON));
        String features = java.util.stream.IntStream.range(0, 12).mapToObj(index ->
                "{\"type\":\"Feature\",\"properties\":{\"place_id\":\"london-" + index
                        + "\",\"name\":\"Museum " + index
                        + "\",\"city\":\"City of Westminster\",\"categories\":[\"entertainment.museum\"]},\"geometry\":{\"type\":\"Point\",\"coordinates\":["
                        + (-0.18 + index % 4 * 0.06) + "," + (51.45 + index / 4 * 0.06) + "]}}")
                .collect(java.util.stream.Collectors.joining(","));
        server.expect(requestTo(containsString("/v2/places?")))
                .andExpect(queryParam("filter", "place:london-boundary"))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[" + features + "]}", MediaType.APPLICATION_JSON));
        var places = source.buscar("London, United Kingdom");

        assertEquals(12, places.size());
        assertTrue(places.stream().allMatch(place -> "LOCAL_ATTRACTION".equals(place.etiquetas().get("region_scope"))));
        server.verify();
    }

    @Test
    void samplesASecondCityZoneWithRectangularBiasInsideTheResolvedBoundaryWhenTheFirstPageIsCentral() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.geoapify.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenStreetMapPlaceSource geocoder = mock(OpenStreetMapPlaceSource.class);
        var clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneId.of("UTC"));
        var coordinates = new DestinoCoordinateCache(clock);
        coordinates.remember("Paris, France", new CoordenadasPublicas(new BigDecimal("48.8566"), new BigDecimal("2.3522")), "fixture");
        var source = new GeoapifyPlaceSource(builder.build(), "test-key", geocoder, coordinates, clock, true);
        server.expect(requestTo(containsString("/v1/geocode/search?")))
                .andRespond(withSuccess("{\"results\":[{\"place_id\":\"paris-boundary\",\"lat\":48.8566,\"lon\":2.3522,\"city\":\"Paris\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v2/places?")))
                .andExpect(queryParam("filter", "place:paris-boundary"))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v2/places?")))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v2/places?")))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{\"place_id\":\"central\",\"name\":\"Central museum\",\"categories\":[\"entertainment.museum\"]},\"geometry\":{\"type\":\"Point\",\"coordinates\":[2.35,48.85]}}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/v2/places?")))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}", MediaType.APPLICATION_JSON));
        String zoneFeatures = java.util.stream.IntStream.range(0, 10).mapToObj(index ->
                "{\"type\":\"Feature\",\"properties\":{\"place_id\":\"zone-" + index
                        + "\",\"name\":\"Museum " + index
                        + "\",\"categories\":[\"entertainment.museum\"]},\"geometry\":{\"type\":\"Point\",\"coordinates\":["
                        + (2.0 + index * 0.06) + ",48.85]}}").collect(java.util.stream.Collectors.joining(","));
        server.expect(requestTo(containsString("/v2/places?")))
                .andExpect(queryParam("filter", "place:paris-boundary"))
                .andExpect(queryParam("limit", "50"))
                .andExpect(queryParam("bias", org.hamcrest.Matchers.startsWith("rect:")))
                .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[" + zoneFeatures + "]}", MediaType.APPLICATION_JSON));
        var places = source.buscar("Paris, France");

        assertEquals(11, places.size());
        assertTrue(places.stream().anyMatch(place -> place.nombre().equals("Museum 9")));
        server.verify();
    }

    @Test
    void sendsGeocodedCoordinatesForSeveralInternationalAndDomesticDestinations() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.geoapify.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenStreetMapPlaceSource geocoder = mock(OpenStreetMapPlaceSource.class);
        var clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneId.of("UTC"));
        var coordinates = new DestinoCoordinateCache(clock);
        Map<String, CoordenadasPublicas> destinations = Map.of(
                "Santa Marta, Colombia", new CoordenadasPublicas(new BigDecimal("11.2408"), new BigDecimal("-74.1990")),
                "Abu Dhabi, United Arab Emirates", new CoordenadasPublicas(new BigDecimal("24.4539"), new BigDecimal("54.3773")),
                "Paris, France", new CoordenadasPublicas(new BigDecimal("48.8566"), new BigDecimal("2.3522")),
                "Tokyo, Japan", new CoordenadasPublicas(new BigDecimal("35.6762"), new BigDecimal("139.6503")));
        destinations.forEach((destination, point) -> coordinates.remember(destination, point, "fixture"));
        var source = new GeoapifyPlaceSource(builder.build(), "test-key", geocoder, coordinates, clock, false);

        destinations.forEach((destination, point) -> {
            String expectedUrl = "https://api.geoapify.com/v2/places?categories=accommodation&filter=circle:"
                    + point.longitud().toPlainString() + "," + point.latitud().toPlainString()
                    + ",5000&bias=proximity:" + point.longitud().toPlainString() + ","
                    + point.latitud().toPlainString() + "&limit=50&apiKey=test-key";
            server.expect(requestTo(expectedUrl))
                    .andRespond(withSuccess("{\"type\":\"FeatureCollection\",\"features\":[]}",
                            MediaType.APPLICATION_JSON));
        });

        destinations.keySet().forEach(destination -> assertTrue(source.buscarAlojamientos(destination).isEmpty()));

        org.mockito.Mockito.verifyNoInteractions(geocoder);
        server.verify();
    }

    @Test
    void doesNotCallExternalProviderWhenApiKeyIsMissing() {
        var fixture = fixture("");
        ErrorFuentePublica error = assertThrows(ErrorFuentePublica.class,
                () -> fixture.source.buscarAlojamientos("Bogota"));
        assertTrue(error.getMessage().contains("GEOAPIFY_API_KEY"));
        fixture.server.verify();
    }

    @Test
    void reportsProviderAuthenticationAndQuotaFailuresWithoutExposingKey() {
        var unauthorized = fixture("private-test-key");
        unauthorized.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=accommodation&filter=circle:-74.1,4.6,5000&bias=proximity:-74.1,4.6&limit=50&apiKey=private-test-key"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        ErrorFuentePublica denied = assertThrows(ErrorFuentePublica.class,
                () -> unauthorized.source.buscarAlojamientos("Bogota"));
        assertTrue(denied.getMessage().contains("configuración de la clave"));
        assertFalse(denied.getMessage().contains("private-test-key"));

        var limited = fixture("private-test-key");
        limited.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=accommodation&filter=circle:-74.1,4.6,5000&bias=proximity:-74.1,4.6&limit=50&apiKey=private-test-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        ErrorFuentePublica quota = assertThrows(ErrorFuentePublica.class,
                () -> limited.source.buscarAlojamientos("Bogota"));
        assertTrue(quota.getMessage().contains("límite"));
        assertFalse(quota.getMessage().contains("private-test-key"));
    }

    @Test
    void honorsRetryAfterAndDoesNotRepeatImmediatelyAfterQuotaResponse() {
        var fixture = fixture("test-key");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Retry-After", "60");
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=accommodation&filter=circle:-74.1,4.6,5000&bias=proximity:-74.1,4.6&limit=50&apiKey=test-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(headers));
        assertThrows(ErrorFuentePublica.class, () -> fixture.source.buscarAlojamientos("Bogota"));
        ErrorFuentePublica cooldown = assertThrows(ErrorFuentePublica.class,
                () -> fixture.source.buscarAlojamientos("Bogota"));
        assertTrue(cooldown.getMessage().contains("pausa"));
        fixture.server.verify();
    }

    @Test
    void rejectsInvalidResponseStructureInsteadOfReturningFakeResults() {
        var fixture = fixture("test-key");
        fixture.server.expect(requestTo("https://api.geoapify.com/v2/places?categories=accommodation&filter=circle:-74.1,4.6,5000&bias=proximity:-74.1,4.6&limit=50&apiKey=test-key"))
                .andRespond(withSuccess("{\"unexpected\":[]}", MediaType.APPLICATION_JSON));
        ErrorFuentePublica error = assertThrows(ErrorFuentePublica.class,
                () -> fixture.source.buscarAlojamientos("Bogota"));
        assertTrue(error.getMessage().contains("no válida"));
    }

    private static Fixture fixture(String apiKey) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.geoapify.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenStreetMapPlaceSource geocoder = mock(OpenStreetMapPlaceSource.class);
        var clock = Clock.fixed(Instant.parse("2026-11-14T00:00:00Z"), ZoneId.of("UTC"));
        var coordinates = new DestinoCoordinateCache(clock);
        coordinates.remember("Bogota", new CoordenadasPublicas(new BigDecimal("4.6"), new BigDecimal("-74.1")), "fixture");
        var source = new GeoapifyPlaceSource(builder.build(), apiKey, geocoder, coordinates, clock, false);
        return new Fixture(source, server, geocoder, coordinates);
    }

    private record Fixture(GeoapifyPlaceSource source, MockRestServiceServer server, OpenStreetMapPlaceSource geocoder,
                           DestinoCoordinateCache coordinates) { }
}
