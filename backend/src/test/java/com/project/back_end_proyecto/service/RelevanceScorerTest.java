package com.project.back_end_proyecto.service;

import com.project.back_end_proyecto.service.publicplaces.LugarPublico;
import com.project.back_end_proyecto.service.publicplaces.RelevanceScorer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RelevanceScorerTest {
    @Test
    void ranksPlacesByInterestMatchesAndNormalizesAccents() {
        LugarPublico park = place("osm:node:1", "Jardín Botánico", "Park", "https://osm.test/1");
        LugarPublico museum = place("osm:node:2", "Museo Central", "Museum", "https://osm.test/2");

        var results = RelevanceScorer.rank(List.of(museum, park), List.of("Naturaleza", "CULTURA"));

        assertEquals("Jardín Botánico", results.get(0).place().nombre());
        assertEquals(List.of("Naturaleza"), results.get(0).matchedInterests());
        assertEquals(List.of("CULTURA"), results.get(1).matchedInterests());
    }

    @Test
    void deduplicatesByNormalizedNameAndLocationOrSourceUrl() {
        LugarPublico first = new LugarPublico("1", "Museo de Bogotá", null, "museum", "Calle 1",
                BigDecimal.ONE, BigDecimal.ONE, "https://osm.test/1", Map.of());
        LugarPublico duplicateByName = new LugarPublico("2", "Museo de Bogota", null, "museum", "Calle 1",
                BigDecimal.ONE, BigDecimal.ONE, "https://osm.test/2", Map.of());
        LugarPublico duplicateByUrl = new LugarPublico("3", "Otro nombre", null, "museum", "Calle 2",
                BigDecimal.ONE, BigDecimal.ONE, "https://osm.test/1", Map.of());

        var results = RelevanceScorer.rank(List.of(first, duplicateByName, duplicateByUrl), List.of("museum"));

        assertEquals(1, results.size());
        assertEquals("Museo de Bogotá", results.get(0).place().nombre());
    }

    @Test
    void tourismRankingPrioritizesVerifiedLandmarkSignalsOverNearbyGenericParksAndFood() {
        LugarPublico cafe = new LugarPublico("cafe", "Cafe fixture", null, "Cafe", "Center", BigDecimal.ONE,
                BigDecimal.ONE, "https://example.test/cafe", Map.of("tourism_role", "gastronomy", "distance_km", "0.2"));
        LugarPublico park = new LugarPublico("park", "Neighborhood Park", null, "park", "Center", BigDecimal.ONE,
                BigDecimal.ONE, "https://example.test/park", Map.of("leisure", "park", "tourism_role", "attraction", "distance_km", "0.1"));
        LugarPublico museum = new LugarPublico("museum", "Historic Museum", null, "museum", "District", BigDecimal.ONE,
                BigDecimal.ONE, "https://example.test/museum", Map.of("wikidata", "Q123", "wikidata_type", "museum", "distance_km", "8"));
        LugarPublico hotel = new LugarPublico("hotel", "Hotel fixture", null, "hotel", "Center", BigDecimal.ONE,
                BigDecimal.ONE, "https://example.test/hotel", Map.of("tourism_role", "lodging"));

        var ranked = RelevanceScorer.rankTourism(List.of(cafe, park, hotel, museum), List.of());

        assertEquals(List.of("Historic Museum", "Neighborhood Park", "Cafe fixture"),
                ranked.stream().map(item -> item.place().nombre()).toList());
        assertFalse(ranked.stream().anyMatch(item -> item.place().nombre().startsWith("Hotel")));
    }

    @Test
    void identifiesWikidataCandidatesOutsideCityAsRegional() {
        LugarPublico regional = new LugarPublico("regional", "Fixture attraction", null, "attraction", null,
                BigDecimal.ONE, BigDecimal.ONE, "https://wikidata.org/Q1",
                Map.of("wikidata", "Q1", "wikidata_type", "museum", "region_scope", "REGIONAL_CANDIDATE", "distance_km", "110"));
        assertEquals(1, RelevanceScorer.rankTourism(List.of(regional), List.of()).size());
        assertEquals(1L, RelevanceScorer.tourismCount(List.of(regional)));
    }

    @Test
    void detailedTourismRankingCountsNonTourismAndDuplicatesForDiagnostics() {
        LugarPublico museum = new LugarPublico("wd:Q1", "Museum", null, "museum", "Old town",
                BigDecimal.ONE, BigDecimal.ONE, "https://wikidata.org/wiki/Q1", Map.of("wikidata", "Q1"));
        LugarPublico duplicate = new LugarPublico("osm:2", "Museum", null, "museum", "Old town",
                BigDecimal.ONE, BigDecimal.ONE, "https://osm.org/2", Map.of("tourism", "museum"));
        LugarPublico restaurant = new LugarPublico("geo:3", "Restaurant", null, "catering.restaurant", "Center",
                BigDecimal.ONE, BigDecimal.ONE, "https://geo.test/3", Map.of("tourism_role", "gastronomy"));
        LugarPublico hotel = new LugarPublico("geo:4", "Hotel", null, "hotel", "Center",
                BigDecimal.ONE, BigDecimal.ONE, "https://geo.test/4", Map.of("tourism_role", "lodging"));

        var result = RelevanceScorer.rankTourismDetailed(List.of(museum, duplicate, restaurant, hotel), List.of());

        assertEquals(4, result.inputCount());
        assertEquals(1, result.excludedByType());
        assertEquals(1, result.duplicatesRemoved());
        assertEquals(List.of("Museum", "Restaurant"), result.ranked().stream().map(row -> row.place().nombre()).toList());
    }

    @Test
    void excludesGenericMapIdentifiersAndSmallMonumentFragmentsFromNotableCount() {
        LugarPublico genericId = new LugarPublico("geo:1", "PA_394", null, "tourism.sights", null,
                BigDecimal.ONE, BigDecimal.ONE, "https://example.test/1", Map.of("tourism", "attraction"));
        LugarPublico fragment = new LugarPublico("geo:2", "Rosace Sud", null, "tourism.sights", null,
                BigDecimal.ONE, BigDecimal.ONE, "https://example.test/2", Map.of("tourism", "attraction"));
        LugarPublico landmark = new LugarPublico("geo:3", "Historic Museum", null, "museum", null,
                BigDecimal.ONE, BigDecimal.ONE, "https://example.test/3", Map.of("wikidata", "Q12", "tourism", "museum"));

        var result = RelevanceScorer.rankTourismDetailed(List.of(genericId, fragment, landmark), List.of());

        assertEquals(List.of("Historic Museum"), result.ranked().stream().map(row -> row.place().nombre()).toList());
        assertEquals(2, result.excludedByType());
        assertEquals(1, RelevanceScorer.tourismCount(List.of(genericId, fragment, landmark)));
    }

    @Test
    void spreadsResultsAcrossMapCellsAfterTheTopFourPlacesInOneTile() {
        List<LugarPublico> central = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> new LugarPublico("central:" + index, "A Central Museum " + index, null,
                        "museum", "Center", new BigDecimal("48.856"), new BigDecimal("2.350"),
                        "https://example.test/central/" + index, Map.of("wikidata", "Q" + (100 + index))))
                .toList();
        LugarPublico otherDistrict = new LugarPublico("district", "Z District Museum", null, "museum", "District",
                new BigDecimal("48.900"), new BigDecimal("2.400"), "https://example.test/district", Map.of("wikidata", "Q999"));

        var results = RelevanceScorer.rankTourismDetailed(java.util.stream.Stream.concat(central.stream(),
                java.util.stream.Stream.of(otherDistrict)).toList(), List.of()).ranked();

        assertEquals("Z District Museum", results.get(4).place().nombre());
        assertEquals(9, results.size());
    }

    @Test
    void treatsBroadAttractionTagsAsWeakEvidenceAndRejectsUndocumentedDecorativeObjects() {
        LugarPublico characterStatue = new LugarPublico("geo:batman", "Batman", null,
                "tourism.attraction", "Center", BigDecimal.ONE, BigDecimal.ONE, "https://example.test/batman",
                Map.of("provider", "Geoapify", "tourism_role", "attraction",
                        "categories", "tourism.attraction,man_made.artwork,artwork.statue"));
        LugarPublico documentedMuseum = new LugarPublico("geo:museum", "City Museum", null,
                "entertainment.museum", "Museum Quarter", BigDecimal.ONE, BigDecimal.ONE,
                "https://example.test/museum", Map.of("wikidata", "Q123", "wikipedia", "en:City_Museum",
                "categories", "entertainment.museum", "provider", "Geoapify"));

        var ranking = RelevanceScorer.rankTourismDetailed(List.of(characterStatue, documentedMuseum), List.of());

        assertEquals(List.of("City Museum"), ranking.ranked().stream()
                .map(row -> row.place().nombre()).toList());
        assertEquals(0, RelevanceScorer.tourismScore(characterStatue));
        assertTrue(RelevanceScorer.tourismScore(documentedMuseum) >= 80);
        assertEquals(1, RelevanceScorer.tourismCount(List.of(characterStatue, documentedMuseum)));
    }

    @Test
    void keepsDocumentedIncidentalStatuesSecondaryUnlessTheProviderMarksThemAsTouristAttractions() {
        LugarPublico incidental = new LugarPublico("geo:memorial", "A documented memorial statue", null,
                "memorial", "Central square", BigDecimal.ONE, BigDecimal.ONE, "https://example.test/memorial",
                Map.of("wikidata", "Q456", "wikipedia", "en:Memorial_statue", "wikidata_type", "memorial",
                        "categories", "historic.memorial,artwork.statue"));
        LugarPublico landmark = new LugarPublico("geo:landmark", "A documented landmark statue", null,
                "tourism.sights", "Waterfront", BigDecimal.ONE, BigDecimal.ONE, "https://example.test/landmark",
                Map.of("wikidata", "Q789", "wikipedia", "en:Landmark_statue", "wikidata_type", "statue",
                        "categories", "tourism.sights,artwork.statue"));

        assertEquals(28, RelevanceScorer.tourismScore(incidental));
        assertTrue(RelevanceScorer.tourismScore(landmark) >= 55);
    }

    @Test
    void doesNotPromoteDocumentedMemorialsWithoutTourismEvidenceAndKeepsDocumentedParksComplementary() {
        LugarPublico secondaryMemorial = new LugarPublico("memorial", "Documented memorial", null,
                "historic memorial", "City centre", BigDecimal.ONE, BigDecimal.ONE,
                "https://example.test/memorial", Map.of("wikidata", "Q900", "wikipedia", "en:Memorial",
                "categories", "historic.memorial"));
        LugarPublico touristMemorial = new LugarPublico("tourist-memorial", "Historic monument", null,
                "historic memorial", "City centre", BigDecimal.ONE, BigDecimal.ONE,
                "https://example.test/tourist-memorial", Map.of("wikidata", "Q901", "wikipedia", "en:Historic_monument",
                "categories", "tourism.sights,historic.memorial"));
        LugarPublico documentedPark = new LugarPublico("park", "Historic urban park", null,
                "park", "Green district", BigDecimal.ONE, BigDecimal.ONE,
                "https://example.test/park", Map.of("wikidata", "Q902", "wikipedia", "en:Historic_urban_park",
                "leisure", "park"));

        assertEquals(28, RelevanceScorer.tourismScore(secondaryMemorial));
        assertEquals(78, RelevanceScorer.tourismScore(touristMemorial));
        assertEquals(64, RelevanceScorer.tourismScore(documentedPark));
        var ranked = RelevanceScorer.rankTourism(List.of(secondaryMemorial, documentedPark, touristMemorial), List.of());
        assertEquals(List.of("Historic monument", "Historic urban park"), ranked.stream().limit(2)
                .map(row -> row.place().nombre()).toList());
        assertEquals("EXPLORABLE", RelevanceScorer.tourismImportance(
                RelevanceScorer.tourismScore(secondaryMemorial)));
    }

    @Test
    void excludesAdministrativeRecordsAndGenericNatureNamesWithoutRejectingAVisitorReserve() {
        LugarPublico cityArticle = new LugarPublico("city", "Cartagena, Colombia", null, "locality", null,
                BigDecimal.ONE, BigDecimal.ONE, "https://example.test/city", Map.of("place", "city"));
        LugarPublico genericMangrove = new LugarPublico("mangle", "Mangle", null, "natural feature", null,
                BigDecimal.ONE, BigDecimal.ONE, "https://example.test/mangle", Map.of("natural", "wetland"));
        LugarPublico indigenousTerritory = new LugarPublico("territory", "Community territory", null,
                "indigenous territory", null, BigDecimal.ONE, BigDecimal.ONE,
                "https://example.test/territory", Map.of("boundary", "aboriginal_lands"));
        LugarPublico visitorReserve = new LugarPublico("reserve", "Mangrove visitor reserve", null,
                "nature reserve visitor centre", null, BigDecimal.ONE, BigDecimal.ONE,
                "https://example.test/reserve", Map.of("natural", "nature_reserve", "tourism", "attraction"));

        assertEquals(0, RelevanceScorer.tourismScore(cityArticle));
        assertEquals(0, RelevanceScorer.tourismScore(genericMangrove));
        assertEquals(0, RelevanceScorer.tourismScore(indigenousTerritory));
        assertEquals(76, RelevanceScorer.tourismScore(visitorReserve));
        assertEquals("COMPLEMENTARIO", RelevanceScorer.tourismImportance(RelevanceScorer.tourismScore(visitorReserve)));
    }

    private static LugarPublico place(String id, String name, String category, String url) {
        return new LugarPublico(id, name, null, category, null, BigDecimal.ONE, BigDecimal.ONE, url,
                Map.of(category.equals("Park") ? "leisure" : "tourism", category.equals("Park") ? "park" : "museum"));
    }
}
