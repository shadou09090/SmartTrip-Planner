package com.project.back_end_proyecto.service.publicplaces;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResilientPublicPlacesSourceTest {
    private final GeoapifyPlaceSource geoapify = mock(GeoapifyPlaceSource.class);
    private final OpenStreetMapPlaceSource osm = mock(OpenStreetMapPlaceSource.class);
    private final ResilientPublicPlacesSource source = new ResilientPublicPlacesSource(geoapify, osm);

    @Test
    void supplementsPartialGeoapifyResultsWithDeduplicatedOsmResults() {
        var geo = place("geoapify:hotel", "Hotel", "Avenida 1");
        var duplicate = place("osm:node:1", " HOTEL ", "Near the center");
        var fallback = place("osm:node:2", "Hostal", "Centro");
        when(geoapify.buscarAlojamientos("Cali")).thenReturn(List.of(geo));
        when(osm.buscarAlojamientos("Cali")).thenReturn(List.of(duplicate, fallback));

        assertEquals(List.of(geo, fallback), source.buscarAlojamientos("Cali"));
        verify(osm).buscarAlojamientos("Cali");
    }

    @Test
    void enrichesFullButLowSignalGeoapifyPageWithOtherSources() {
        List<LugarPublico> places = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> new LugarPublico("geoapify:" + index, "Attraction " + index, null,
                        "Attraction", "Address " + index, BigDecimal.ONE, BigDecimal.ONE, null,
                        Map.of("tourism_role", "attraction", "categories", "tourism.attraction"))).toList();
        when(geoapify.buscar("Cali")).thenReturn(places);
        when(osm.buscar("Cali")).thenReturn(List.of());

        assertEquals(20, source.buscar("Cali").size());
        verify(osm).buscar("Cali");
    }

    @Test
    void fallsBackToOsmWhenGeoapifyCannotBeUsed() {
        var fallback = place("osm:node:1", "Hotel OSM", "Centro");
        when(geoapify.buscarAlojamientos("Cali")).thenThrow(new ErrorFuentePublica("key unavailable"));
        when(osm.buscarAlojamientos("Cali")).thenReturn(List.of(fallback));

        assertEquals(List.of(fallback), source.buscarAlojamientos("Cali"));
    }

    @Test
    void treatsBothValidEmptyResultsAsAnEmptyResult() {
        when(geoapify.buscar("Cali")).thenReturn(List.of());
        when(osm.buscar("Cali")).thenReturn(List.of());

        assertTrue(source.buscar("Cali").isEmpty());
    }

    @Test
    void reportsUnavailableOnlyWhenBothSourcesFail() {
        when(geoapify.buscar("Cali")).thenThrow(new ErrorFuentePublica("Geoapify unavailable"));
        when(osm.buscar("Cali")).thenThrow(new ErrorFuentePublica("OSM timeout"));

        ErrorFuentePublica error = assertThrows(ErrorFuentePublica.class, () -> source.buscar("Cali"));
        assertTrue(error.getMessage().contains("no están disponibles"));
        assertEquals(List.of("Geoapify", "OpenStreetMap/Overpass"), error.fuentesConsultadas());
        assertEquals(2, error.avisos().size());
    }

    @Test
    void keepsNominatimGeocodingIndependentFromPlacesSearch() {
        when(geoapify.geocodificarDestino("Cali")).thenReturn(Optional.of(
                new CoordenadasPublicas(new BigDecimal("3.4"), new BigDecimal("-76.5"))));
        assertTrue(source.geocodificarDestino("Cali").isPresent());
        verifyNoInteractions(osm);
    }

    @Test
    void fallsBackToIndependentOsmGeocoderWhenGeoapifyIsUnavailableOrHasNoMatch() {
        var osmPoint = new CoordenadasPublicas(new BigDecimal("41.38"), new BigDecimal("2.17"));
        when(geoapify.geocodificarDestino("Barcelona, Spain")).thenThrow(new ErrorFuentePublica("Geoapify unavailable"));
        when(osm.geocodificarDestino("Barcelona, Spain")).thenReturn(Optional.of(osmPoint));

        assertEquals(Optional.of(osmPoint), source.geocodificarDestino("Barcelona, Spain"));
        verify(osm).geocodificarDestino("Barcelona, Spain");
    }

    @Test
    void aValidGeoapifyCoordinateDoesNotCallTheOsmFallback() {
        var point = new CoordenadasPublicas(new BigDecimal("41.38"), new BigDecimal("2.17"));
        when(geoapify.geocodificarDestino("Barcelona, Spain")).thenReturn(Optional.of(point));

        assertEquals(Optional.of(point), source.geocodificarDestino("Barcelona, Spain"));
        verifyNoInteractions(osm);
    }

    @Test
    void supplementsGenericNearbyPlacesWithWikidataNotableAttractionsAndDeduplicatesByEntity() {
        var geoPark = new LugarPublico("geo:park", "Generic Park", null, "Park", "Center",
                BigDecimal.ONE, BigDecimal.ONE, "https://example.test/park",
                Map.of("tourism_role", "attraction", "leisure", "park"));
        var geoDuplicate = new LugarPublico("geo:museum", "Known Museum", null, "Museum", "City",
                BigDecimal.ONE, BigDecimal.ONE, "https://example.test/museum", Map.of("wikidata", "Q44"));
        var wikidataDuplicate = new LugarPublico("wikidata:Q44", "Known Museum", null, "Museum", "City",
                BigDecimal.ONE, BigDecimal.ONE, "https://www.wikidata.org/wiki/Q44", Map.of("wikidata", "Q44", "provider", "Wikidata"));
        var palace = new LugarPublico("wikidata:Q55", "Notable Palace", null, "Palace", null,
                BigDecimal.ONE, BigDecimal.ONE, "https://www.wikidata.org/wiki/Q55", Map.of("wikidata", "Q55", "provider", "Wikidata"));
        var wikidata = mock(WikidataTourismPlaceSource.class);
        when(geoapify.buscar("Abu Dhabi")).thenReturn(List.of(geoPark, geoDuplicate));
        when(osm.buscar("Abu Dhabi")).thenReturn(List.of());
        when(wikidata.buscar("Abu Dhabi")).thenReturn(List.of(wikidataDuplicate, palace));
        var combinedSource = new ResilientPublicPlacesSource(geoapify, osm, wikidata);

        var results = combinedSource.buscar("Abu Dhabi");

        assertEquals(3, results.size());
        assertEquals(1, results.stream().filter(place -> "Q44".equals(place.etiquetas().get("wikidata"))).count());
        assertTrue(results.stream().anyMatch(place -> place.nombre().equals("Notable Palace")));
        verify(wikidata).buscar("Abu Dhabi");
    }

    @Test
    void stillReturnsWikidataPlacesWhenGeoapifyAndOverpassAreUnavailable() {
        var wikidata = mock(WikidataTourismPlaceSource.class);
        var landmark = new LugarPublico("wikidata:Q99", "Fixture landmark", null, "Monument", null,
                BigDecimal.ONE, BigDecimal.ONE, "https://www.wikidata.org/wiki/Q99", Map.of("wikidata", "Q99"));
        when(geoapify.buscar("Tokyo")).thenThrow(new ErrorFuentePublica("Geo unavailable"));
        when(osm.buscar("Tokyo")).thenThrow(new ErrorFuentePublica("Overpass unavailable"));
        when(wikidata.buscar("Tokyo")).thenReturn(List.of(landmark));
        var combinedSource = new ResilientPublicPlacesSource(geoapify, osm, wikidata);
        assertEquals(List.of(landmark), combinedSource.buscar("Tokyo"));
    }

    @Test
    void queriesWikidataWhenGeoapifyReturnsManyAttractionsButInsufficientCitywideCoverage() {
        List<LugarPublico> centralPage = java.util.stream.IntStream.range(0, 45)
                .mapToObj(index -> new LugarPublico("geo:" + index, "Attraction " + index, null, "museum",
                        "Central district", new BigDecimal("48.856"), new BigDecimal("2.350"),
                        "https://geo.test/" + index, Map.of("tourism_role", "attraction", "wikidata", "Q" + (index + 100))))
                .toList();
        var secondDistrict = new LugarPublico("wikidata:Q900", "District museum", null, "museum", "Other district",
                new BigDecimal("48.900"), new BigDecimal("2.400"), "https://www.wikidata.org/wiki/Q900",
                Map.of("provider", "Wikidata", "wikidata", "Q900", "wikidata_type", "museum"));
        var wikidata = mock(WikidataTourismPlaceSource.class);
        when(geoapify.buscar("Paris")).thenReturn(centralPage);
        when(wikidata.buscar("Paris")).thenReturn(List.of(secondDistrict));

        var result = new ResilientPublicPlacesSource(geoapify, osm, wikidata).buscar("Paris");

        verify(wikidata).buscar("Paris");
        assertTrue(result.stream().anyMatch(place -> place.id().equals("wikidata:Q900")));
    }

    @Test
    void wikipediaEditorialCandidatesSurviveWikidataFailureAndRemainDiscoverable() {
        var wikipediaPlace = new LugarPublico("wikipedia:123", "Fixture landmark", "Editorial extract",
                "Patrimonio / monumento", null, new BigDecimal("51.5"), new BigDecimal("-0.12"),
                "https://en.wikipedia.org/wiki/Fixture_landmark", Map.of(
                "provider", "Wikipedia Geosearch", "wikipedia_page_id", "123",
                "wikipedia_url", "https://en.wikipedia.org/wiki/Fixture_landmark",
                "wikipedia_editorial_evidence", "Tourist attractions in destination",
                "categories", "Tourist attractions in destination", "tourism_role", "attraction",
                "region_scope", "LOCAL_ATTRACTION", "distance_km", "1.2"));
        when(geoapify.buscar("London, United Kingdom")).thenReturn(List.of());
        when(osm.buscar("London, United Kingdom")).thenThrow(new ErrorFuentePublica("Overpass timeout"));
        var wikidata = mock(WikidataTourismPlaceSource.class);
        when(wikidata.buscar("London, United Kingdom")).thenThrow(new ErrorFuentePublica("WDQS timeout"));
        var wikipedia = mock(WikipediaGeosearchPlaceSource.class);
        when(wikipedia.buscar("London, United Kingdom")).thenReturn(List.of(wikipediaPlace));

        var combined = new ResilientPublicPlacesSource(geoapify, osm, wikidata, wikipedia);
        var result = combined.buscarConDiagnostico("London, United Kingdom");

        assertEquals(1, result.lugares().size());
        assertEquals("Wikipedia Geosearch", result.lugares().get(0).etiquetas().get("provider"));
        assertTrue(result.avisos().stream().anyMatch(message -> message.contains("Wikidata")));
        assertEquals(1, result.resultadosConservadosPorFuente().get("Wikipedia Geosearch"));
        verify(wikipedia).buscar("London, United Kingdom");
    }

    @Test
    void reportsWhichProviderFailedWhenAnotherSourceStillProvidesRealResults() {
        LugarPublico osmAttraction = new LugarPublico("osm:1", "Museum fixture", null, "museum", "Cali",
                BigDecimal.ONE, BigDecimal.ONE, "https://www.openstreetmap.org/node/1",
                Map.of("tourism", "museum", "provider", "OpenStreetMap"));
        when(geoapify.buscar("Cali")).thenThrow(new ErrorFuentePublica("quota"));
        when(osm.buscar("Cali")).thenReturn(List.of(osmAttraction));

        var result = source.buscarConDiagnostico("Cali");

        assertEquals(List.of(osmAttraction), result.lugares());
        assertTrue(result.avisos().stream().anyMatch(warning -> warning.contains("Geoapify")));
        assertEquals(List.of("Geoapify", "OpenStreetMap/Overpass"), result.fuentesConsultadas());
        assertEquals(1, result.resultadosConservadosPorFuente().get("OpenStreetMap"));
    }

    private static LugarPublico place(String id, String name, String address) {
        return new LugarPublico(id, name, null, "hotel", address, new BigDecimal("3.4"),
                new BigDecimal("-76.5"), "https://www.openstreetmap.org/", Map.of());
    }
}
