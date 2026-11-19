package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.service.publicplaces.ErrorFuentePublica;
import com.project.back_end_proyecto.service.publicplaces.CoordenadasPublicas;
import com.project.back_end_proyecto.service.publicplaces.FuenteLugaresPublicos;
import com.project.back_end_proyecto.service.publicplaces.LugarPublico;
import com.project.back_end_proyecto.service.publicplaces.WikimediaCommonsPhotoService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalActivityRecommendationServiceTest {
    private final FuenteLugaresPublicos source = mock(FuenteLugaresPublicos.class);
    private final WikimediaCommonsPhotoService photos = mock(WikimediaCommonsPhotoService.class);
    private final ExternalActivityRecommendationService service = new ExternalActivityRecommendationService(source, photos);

    @Test
    void returnsEmptyResultsWhenThePublicSourceHasNoMatches() {
        when(source.buscar("Cali")).thenReturn(List.of());

        var result = service.recommend(15L, "Cali", LocalDate.parse("2026-12-01"),
                LocalDate.parse("2026-12-03"), List.of("Naturaleza"));

        assertTrue(result.recommendations().isEmpty());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void isolatesSourceErrorsAndSimulatedTimeoutsAsWarnings() {
        when(source.buscar("Cali")).thenThrow(new ErrorFuentePublica("simulated timeout"));

        var result = service.recommend(15L, "Cali", LocalDate.parse("2026-12-01"),
                LocalDate.parse("2026-12-03"), List.of("Naturaleza"));

        assertTrue(result.recommendations().isEmpty());
        assertEquals("simulated timeout", result.warnings().get(0));
    }

    @Test
    void queriesPublicPlacesEvenWhenTripHasNoInterests() {
        when(source.buscar("Cali")).thenReturn(List.of());
        when(photos.verifyPlaceImages(List.of())).thenReturn(Map.of());

        var result = service.recommend(15L, "Cali", LocalDate.parse("2026-12-01"),
                LocalDate.parse("2026-12-03"), List.of());

        assertTrue(result.recommendations().isEmpty());
        verify(source).buscar("Cali");
    }

    @Test
    void exposesMatchedReasonButDoesNotInventPriceAndAllowsExplicitSelectionOnly() {
        LugarPublico place = new LugarPublico("osm:node:12", "Jardín Botánico", "Descripción pública",
                "park", "Bogotá", new BigDecimal("4.6"), new BigDecimal("-74.1"),
                "https://www.openstreetmap.org/node/12", Map.of("leisure", "park"));
        when(source.buscar("Bogotá")).thenReturn(List.of(place));
        when(photos.verifyPlaceImages(List.of(place))).thenReturn(Map.of());

        var result = service.recommend(15L, "Bogotá", LocalDate.parse("2026-12-01"),
                LocalDate.parse("2026-12-03"), List.of("Naturaleza"));

        assertEquals(1, result.recommendations().size());
        assertNull(result.recommendations().get(0).precio());
        assertNull(result.recommendations().get(0).imagenUrl());
        assertTrue(result.recommendations().get(0).motivo().contains("Naturaleza"));
        assertEquals(place, service.selection(15L, place.id(), "Bogotá", List.of("Naturaleza")).place());
        assertThrows(RuntimeException.class,
                () -> service.selection(15L, place.id(), "Medellín", List.of("Naturaleza")));
    }

    @Test
    void recommendsAndAllowsSelectingGeneralPlacesWithoutInterests() {
        LugarPublico place = new LugarPublico("geoapify:poi-2", "Qasr Al Watan", null, "Palace", "Abu Dhabi",
                new BigDecimal("24.46"), new BigDecimal("54.31"), "https://wikidata.org/wiki/Q1",
                Map.of("wikidata", "Q1", "provider", "Wikidata", "region_scope", "CITY", "distance_km", "4.2"));
        when(source.buscar("Abu Dhabi, United Arab Emirates")).thenReturn(List.of(place));
        when(photos.verifyPlaceImages(List.of(place))).thenReturn(Map.of());

        var result = service.recommend(15L, "Abu Dhabi, United Arab Emirates", LocalDate.parse("2026-12-01"),
                LocalDate.parse("2026-12-03"), List.of());

        assertEquals(1, result.recommendations().size());
        assertTrue(result.recommendations().get(0).motivo().contains("Alternativa turística general"));
        assertTrue(result.recommendations().get(0).disponibilidad().contains("no informa disponibilidad"));
        assertEquals("Wikidata", result.recommendations().get(0).proveedor());
        assertEquals(new BigDecimal("4.2"), result.recommendations().get(0).distanciaDestinoKm());
        assertEquals(place, service.selection(15L, place.id(), "Abu Dhabi, United Arab Emirates", List.of()).place());
    }

    @Test
    void includesNonMatchingPlacesAsClearlyLabelledAlternativesAfterInterestMatches() {
        LugarPublico match = new LugarPublico("osm:node:1", "Parque", null, "park", "Paris", null, null,
                "https://osm.org/1", Map.of());
        LugarPublico general = new LugarPublico("osm:node:2", "Mercado", null, "market", "Paris", null, null,
                "https://osm.org/2", Map.of());
        when(source.buscar("París, Francia")).thenReturn(List.of(match, general));
        when(photos.verifyPlaceImages(List.of(match, general))).thenReturn(Map.of());

        var result = service.recommend(15L, "París, Francia", LocalDate.parse("2026-12-01"),
                LocalDate.parse("2026-12-03"), List.of("Naturaleza"));

        assertEquals(1, result.recommendations().size());
        assertEquals(List.of("Naturaleza"), result.recommendations().get(0).interesesCoincidentes());
    }

    @Test
    void excludesAccommodationAndKeepsGastronomyAsSecondary() {
        LugarPublico cafe = new LugarPublico("geo:cafe", "Cafe fixture", null, "Cafe", "Center", null, null,
                "https://example.test/cafe", Map.of("tourism_role", "gastronomy", "provider", "Geoapify"));
        LugarPublico museum = new LugarPublico("wd:museum", "Fixture Museum", null, "Museum", "District", null, null,
                "https://www.wikidata.org/wiki/Q70", Map.of("wikidata", "Q70", "provider", "Wikidata"));
        LugarPublico hotel = new LugarPublico("geo:hotel", "Fixture Hotel", null, "Hotel", "Center", null, null,
                "https://example.test/hotel", Map.of("tourism_role", "lodging"));
        List<LugarPublico> places = List.of(cafe, hotel, museum);
        when(source.buscar("Paris, France")).thenReturn(places);
        when(photos.verifyPlaceImages(anyList())).thenReturn(Map.of());

        var result = service.recommend(15L, "Paris, France", LocalDate.parse("2026-12-01"),
                LocalDate.parse("2026-12-03"), List.of());

        assertEquals(List.of("Fixture Museum", "Cafe fixture"),
                result.recommendations().stream().map(item -> item.nombre()).toList());
        assertTrue(result.recommendations().get(1).motivo().contains("complementaria"));
    }

    @Test
    void developmentDiagnosticsReportResolvedCenterProvidersDiscardReasonsAndFinalResults() {
        LugarPublico museum = new LugarPublico("wd:Q77", "Verified Museum", null, "Museum", "District",
                new BigDecimal("35.6"), new BigDecimal("139.6"), "https://www.wikidata.org/wiki/Q77",
                Map.of("wikidata", "Q77", "wikidata_type", "museum", "provider", "Wikidata"));
        LugarPublico hotel = new LugarPublico("geo:hotel", "Hotel", null, "hotel", "City", null, null,
                null, Map.of("tourism_role", "lodging", "provider", "Geoapify"));
        when(source.buscarConDiagnostico("Tokyo, Japan")).thenReturn(new FuenteLugaresPublicos.ResultadoLugares(
                List.of(museum, hotel), List.of("Overpass unavailable"), List.of("Geoapify", "OpenStreetMap/Overpass", "Wikidata"),
                Map.of("Geoapify", 1, "Wikidata", 1)));
        when(source.geocodificarDestino("Tokyo, Japan")).thenReturn(Optional.of(
                new CoordenadasPublicas(new BigDecimal("35.68"), new BigDecimal("139.69"))));
        when(photos.verifyPlaceImages(List.of(museum))).thenReturn(Map.of());
        var diagnosticService = new ExternalActivityRecommendationService(source, photos, true);

        var result = diagnosticService.recommend(12L, "Tokyo, Japan", LocalDate.parse("2026-12-01"),
                LocalDate.parse("2026-12-03"), List.of("Cultura"));

        assertEquals(1, result.recommendations().size());
        assertNotNull(result.diagnostics());
        assertEquals(new BigDecimal("35.68"), result.diagnostics().latitudCentro());
        assertEquals(List.of("Geoapify", "OpenStreetMap/Overpass", "Wikidata"), result.diagnostics().fuentesConsultadas());
        assertEquals(1, result.diagnostics().descartes().get("noTuristicosOAlojamiento"));
        assertEquals(1, result.diagnostics().atractivosFinales());
        assertEquals(List.of("Overpass unavailable"), result.diagnostics().errores());
    }

    @Test
    void editorialWikipediaCandidateIsEligibleWithoutWikidataWhenCoordinatesAndLocalScopeAreVerified() {
        LugarPublico editorial = new LugarPublico("wikipedia:123", "Fixture landmark", "Documented extract",
                "Patrimonio / monumento", "London", new BigDecimal("51.50"), new BigDecimal("-0.12"),
                "https://en.wikipedia.org/wiki/Fixture_landmark", Map.of(
                "provider", "Wikipedia Geosearch", "wikipedia_page_id", "123",
                "wikipedia_url", "https://en.wikipedia.org/wiki/Fixture_landmark",
                "wikipedia_editorial_evidence", "Tourist attractions in destination",
                "categories", "Tourist attractions in destination", "tourism_role", "attraction",
                "region_scope", "LOCAL_ATTRACTION", "distance_km", "1.2"));
        when(source.buscar("London, United Kingdom")).thenReturn(List.of(editorial));
        when(photos.verifyPlaceImages(List.of(editorial))).thenReturn(Map.of());

        var result = service.recommend(40L, "London, United Kingdom", LocalDate.parse("2027-01-15"),
                LocalDate.parse("2027-01-19"), List.of());

        assertEquals(1, result.recommendations().size());
        assertEquals("PRINCIPAL", result.recommendations().get(0).clasificacionTuristica());
        assertTrue(result.recommendations().get(0).elegibleItinerario());
        assertEquals("Wikipedia Geosearch", result.recommendations().get(0).proveedor());
    }

    @Test
    void reportsWhySixtyFourStrongSignalsAreNotYetEligibleWhenTheirScopeIsRegional() {
        List<LugarPublico> candidates = java.util.stream.IntStream.range(0, 64)
                .mapToObj(index -> new LugarPublico("geo:" + index, "Museum " + index, null,
                        "entertainment.museum", "City of Westminster", new BigDecimal("51.5"),
                        new BigDecimal("-0.1"), "https://example.test/" + index,
                        Map.of("provider", "Geoapify", "categories", "entertainment.museum",
                                "region_scope", "REGIONAL_ATTRACTION", "distance_km", "2.0")))
                .toList();
        when(source.buscarConDiagnostico("London, United Kingdom")).thenReturn(
                new FuenteLugaresPublicos.ResultadoLugares(candidates, List.of(
                        "Wikidata no pudo completar el descubrimiento de atractivos en esta búsqueda."),
                        List.of("Geoapify", "Wikidata"), Map.of("Geoapify", 64)));
        when(photos.verifyPlaceImages(anyList())).thenReturn(Map.of());
        var diagnosticService = new ExternalActivityRecommendationService(source, photos, true);

        var result = diagnosticService.recommend(123L, "London, United Kingdom", LocalDate.parse("2027-01-15"),
                LocalDate.parse("2027-01-19"), List.of());

        assertEquals(64, result.recommendations().size());
        assertEquals(64, result.diagnostics().clasificacionCandidatos().get("principales"));
        assertEquals(64, result.diagnostics().clasificacionCandidatos().get("regionalesUOpcionales"));
        assertEquals(0, result.diagnostics().clasificacionCandidatos().get("aptosParaItinerario"));
        assertEquals(64, result.diagnostics().razonesExclusion().get(
                "El proveedor lo clasifica como regional o excursión; verifica el traslado antes de incluirlo."));
        assertTrue(result.warnings().get(0).contains("Wikidata"));
    }

    @Test
    void carriesProviderFailureWarningAlongsideAvailableRecommendations() {
        LugarPublico museum = new LugarPublico("osm:museum", "Museum fixture", null, "Museum", "Cali",
                BigDecimal.ONE, BigDecimal.ONE, "https://www.openstreetmap.org/node/1",
                Map.of("tourism", "museum", "provider", "OpenStreetMap"));
        when(source.buscarConDiagnostico("Cali")).thenReturn(new FuenteLugaresPublicos.ResultadoLugares(
                List.of(museum), List.of("Geoapify no pudo aportar atracciones en esta búsqueda.")));
        when(photos.verifyPlaceImages(List.of(museum))).thenReturn(Map.of());

        var result = service.recommend(15L, "Cali", LocalDate.parse("2026-12-01"),
                LocalDate.parse("2026-12-03"), List.of());

        assertEquals("OpenStreetMap", result.recommendations().get(0).proveedor());
        assertTrue(result.warnings().get(0).contains("Geoapify"));
    }

    @Test
    void preservesCityWideCandidatePoolBeyondTheOldTwentyRecommendationCap() {
        List<LugarPublico> places = java.util.stream.IntStream.range(0, 75)
                .mapToObj(index -> new LugarPublico("wikidata:Q" + (1000 + index), "Museum " + index,
                        null, "Museum", "Paris area", null, null,
                        "https://www.wikidata.org/wiki/Q" + (1000 + index),
                        Map.of("wikidata", "Q" + (1000 + index), "wikidata_type", "museum", "provider", "Wikidata")))
                .toList();
        when(source.buscar("Paris, France")).thenReturn(places);
        when(photos.verifyPlaceImages(anyList())).thenReturn(Map.of());

        var result = service.recommend(15L, "Paris, France", LocalDate.parse("2026-11-20"),
                LocalDate.parse("2026-11-24"), List.of());

        assertEquals(75, result.recommendations().size());
        org.mockito.ArgumentCaptor<List<LugarPublico>> photoCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(photos).verifyPlaceImages(photoCaptor.capture());
        assertEquals(20, photoCaptor.getValue().size(), "image lookups remain bounded independently of itinerary candidates");
    }
}
