package com.project.back_end_proyecto.service.publicplaces;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Scores provider metadata only; scores are ranking signals, not ratings or popularity claims. */
public final class RelevanceScorer {
    private static final Map<String, Set<String>> SYNONYMS = Map.ofEntries(
            Map.entry("naturaleza", Set.of("park", "garden", "viewpoint", "zoo", "natural", "nature", "beach")),
            Map.entry("cultura", Set.of("museum", "gallery", "arts centre", "theatre", "historic", "culture")),
            Map.entry("historia", Set.of("historic", "monument", "castle", "archaeological", "memorial", "history", "heritage")),
            Map.entry("arte", Set.of("artwork", "gallery", "arts centre", "museum", "art")),
            Map.entry("gastronomia", Set.of("restaurant", "cafe", "food", "gastronomy")),
            Map.entry("comida", Set.of("restaurant", "cafe", "food", "gastronomy")),
            Map.entry("parques", Set.of("park", "garden")),
            Map.entry("museos", Set.of("museum", "gallery")),
            Map.entry("aventura", Set.of("theme park", "viewpoint", "zoo", "adventure"))
    );

    private RelevanceScorer() { }

    /** Legacy interest-only ranker retained for callers that explicitly require a match. */
    public static List<RankedPlace> rank(List<LugarPublico> places, List<String> interests) {
        List<RankedPlace> ranked = new ArrayList<>();
        for (LugarPublico place : places) {
            List<String> matches = matchingInterests(place, interests);
            if (!matches.isEmpty()) ranked.add(new RankedPlace(place, matches, matches.size()));
        }
        ranked.sort(Comparator.comparingInt(RankedPlace::score).reversed()
                .thenComparing(item -> normalize(item.place().nombre())));
        return deduplicate(ranked);
    }

    /**
     * Tourist discovery ranker: places remain eligible without interests, while accommodation is
     * always excluded and food is kept as a clearly secondary option.
     */
    public static List<RankedPlace> rankTourism(List<LugarPublico> places, List<String> interests) {
        return rankTourismDetailed(places, interests).ranked();
    }

    public static RankingResult rankTourismDetailed(List<LugarPublico> places, List<String> interests) {
        List<RankedPlace> ranked = new ArrayList<>();
        int excludedByType = 0;
        List<LugarPublico> safePlaces = places == null ? List.of() : places;
        for (LugarPublico place : safePlaces) {
            int base = tourismScore(place);
            if (base < 8) { excludedByType++; continue; }
            List<String> matches = matchingInterests(place, interests);
            int score = base + matches.size() * 14;
            if (!normalize(tag(place, "wikidata")).isBlank()) score += 4;
            if (!normalize(tag(place, "wikipedia_url")).isBlank()) score += 5;
            score += editorialCoverageSignal(place);
            String scope = normalize(tag(place, "region_scope"));
            if (scope.equals("regional candidate") || scope.equals("regional attraction")) score -= 18;
            else if (scope.equals("excursion optional")) score -= 28;
            double distance = distance(place);
            // Distance breaks ties gently and cannot outrank a landmark/category signal.
            if (Double.isFinite(distance)) score -= (int) Math.min(8, distance / 20);
            ranked.add(new RankedPlace(place, matches, score));
        }
        ranked.sort(Comparator.comparingInt(RankedPlace::score).reversed()
                .thenComparingDouble(item -> distance(item.place()))
                .thenComparing(item -> normalize(item.place().nombre())));
        List<RankedPlace> unique = spreadAcrossGeographicCells(deduplicate(ranked));
        return new RankingResult(List.copyOf(unique), safePlaces.size(), excludedByType,
                ranked.size() - unique.size());
    }

    /** Keeps high relevance first while preventing one small map tile from occupying the whole page. */
    private static List<RankedPlace> spreadAcrossGeographicCells(List<RankedPlace> ranked) {
        Map<String, Integer> perCell = new java.util.HashMap<>();
        List<RankedPlace> spread = new ArrayList<>(ranked.size());
        List<RankedPlace> deferred = new ArrayList<>();
        for (RankedPlace item : ranked) {
            String cell = geographicCell(item.place());
            int count = perCell.getOrDefault(cell, 0);
            if (count < 4) {
                spread.add(item);
                perCell.put(cell, count + 1);
            } else deferred.add(item);
        }
        spread.addAll(deferred);
        return List.copyOf(spread);
    }

    private static String geographicCell(LugarPublico place) {
        if (place.latitud() == null || place.longitud() == null
                || !validCoordinate(place.latitud(), -90, 90) || !validCoordinate(place.longitud(), -180, 180)) {
            return "unlocated:" + place.id();
        }
        // A 0.05 degree grid is a few kilometres in most populated latitudes; it is a
        // presentation-diversity signal only, not an assertion of travel time or distance.
        return Math.floor(place.latitud().doubleValue() * 20) + ":"
                + Math.floor(place.longitud().doubleValue() * 20);
    }

    private static boolean validCoordinate(java.math.BigDecimal value, double minimum, double maximum) {
        double number = value.doubleValue();
        return Double.isFinite(number) && number >= minimum && number <= maximum;
    }

    /** Category/source evidence, not popularity or a user rating. */
    public static int tourismScore(LugarPublico place) {
        if (place == null) return 0;
        if (isSecondaryFragment(place.nombre())) return 0;
        Map<String, String> tags = place.etiquetas() == null ? Map.of() : place.etiquetas();
        String role = normalize(tags.get("tourism_role"));
        String type = normalize(tags.get("wikidata_type") + " " + tags.get("categories") + " "
                + place.categoria() + " " + tags.get("tourism") + " " + tags.get("historic") + " "
                + tags.get("leisure") + " " + tags.get("natural") + " " + tags.get("amenity"));
        if (isGenericGeographicRecord(place, tags, type)) return 0;
        if (role.contains("lodging") || type.contains("hotel") || type.contains("hostel")
                || type.contains("guest house") || type.contains("accommodation")) return 0;
        if (role.contains("gastronomy") || type.contains("restaurant") || type.contains("cafe")
                || type.contains("catering")) return 8;
        boolean hasWikidata = !normalize(tags.get("wikidata")).isBlank();
        boolean hasWikipedia = !normalize(tags.get("wikipedia_url")).isBlank()
                || !normalize(tags.get("wikipedia")).isBlank();
        boolean hasEditorialWikipediaEvidence = !normalize(tags.get("wikipedia_editorial_evidence")).isBlank()
                && !normalize(tags.get("wikipedia_page_id")).isBlank();
        boolean documented = (hasWikidata && hasWikipedia) || hasEditorialWikipediaEvidence;
        if (isMinorArtifact(type)) {
            if (!documented) return 0;
            // Documentation alone does not make every statue, plaque or artwork a main stop.
            // Keep it eligible only when the source also gives it an explicit tourism/heritage signal.
            boolean explicitTourismSignal = containsAny(type, "tourism sights", "tourism attraction",
                    "tourist attraction", "landmark", "world heritage", "heritage site", "monument");
            if (!explicitTourismSignal) return 28;
        }
        if (hasEditorialWikipediaEvidence && containsAny(type, "tourist attraction", "landmark", "heritage",
                "historic site", "historic building", "listed building", "monument", "museum", "palace",
                "castle", "cathedral", "national park", "botanical garden", "art gallery", "theme park")) return 82;
        if (containsAny(type, "world heritage", "heritage site", "monument", "palace", "mosque",
                "temple", "historic site", "museum", "theme park", "amusement park")) return 88;
        // Specific outdoor visitor sites outrank the broad `tourism=attraction` fallback.
        // Otherwise that generic tag returned 36 before the verified nature-reserve signal
        // below could be considered.
        if (containsAny(type, "national park", "nature reserve", "beach", "viewpoint", "archaeological")) return 76;
        if (type.contains("tourism sights")) return 78;
        // `tourism.attraction` is a broad map category; it includes character statues,
        // plaques and minor objects, so it is eligible for exploration but cannot by itself
        // make a place a principal itinerary stop.
        if (type.contains("tourism attraction") || type.contains("tourist attraction")
                || type.contains("attraction")) return documented ? 62 : 36;
        if (type.contains("natural")) return 76;
        if (containsAny(type, "gallery", "theatre", "arts centre", "zoo", "aquarium", "castle")) return 70;
        if (type.contains("memorial")) return documented ? 62 : 35;
        if (type.contains("place of worship")) return 48;
        // A generic map park is not automatically a destination. Editorially documented
        // parks can be useful complementary visits, while undocumented neighborhood green
        // spaces remain below the automatic itinerary threshold.
        if (type.contains("park") || type.contains("garden")) return documented ? 64 : 42;
        if (role.contains("attraction")) return documented ? 58 : 32;
        return 0;
    }

    /** Category evidence tier. It is not a rating, popularity measure, or availability claim. */
    public static String tourismImportance(int score) {
        if (score >= 80) return "PRINCIPAL";
        if (score >= 55) return "COMPLEMENTARIO";
        return "EXPLORABLE";
    }

    /** Human-readable metadata signals used to explain the category-based classification. */
    public static List<String> tourismEvidence(LugarPublico place) {
        if (place == null) return List.of();
        Map<String, String> tags = place.etiquetas() == null ? Map.of() : place.etiquetas();
        List<String> evidence = new ArrayList<>();
        String category = safe(tags.get("categories"));
        if (category.isBlank()) category = safe(place.categoria());
        if (!category.isBlank()) evidence.add("Categoría informada por la fuente: " + category);
        String wikidata = safe(tags.get("wikidata"));
        if (wikidata.matches("Q[1-9][0-9]*")) evidence.add("Identificador Wikidata verificado: " + wikidata);
        String wikipedia = safe(tags.get("wikipedia_url"));
        if (wikipedia.isBlank()) wikipedia = safe(tags.get("wikipedia"));
        if (!wikipedia.isBlank()) evidence.add("Referencia Wikipedia informada por la fuente");
        String heritage = safe(tags.get("heritage"));
        if (!heritage.isBlank()) evidence.add("Metadato patrimonial informado: " + heritage);
        return List.copyOf(evidence);
    }

    private static boolean isMinorArtifact(String type) {
        return containsAny(type, "statue", "sculpture", "plaque", "bust", "artwork", "memorial",
                "wayside cross", "boundary stone");
    }

    private static boolean isGenericGeographicRecord(LugarPublico place, Map<String, String> tags, String type) {
        String placeType = normalize(tags.get("place"));
        String boundary = normalize(tags.get("boundary"));
        if (Set.of("city", "town", "village", "municipality", "county", "state", "province", "country",
                "region", "locality", "hamlet", "suburb", "neighbourhood", "neighborhood").contains(placeType)
                || boundary.equals("administrative")) return true;
        if (containsAny(type, "indigenous reservation", "indigenous reserve", "indigenous territory",
                "aboriginal reserve", "community land", "resguardo indigena", "territorio indigena")) return true;

        String name = normalize(place.nombre());
        boolean genericNaturalName = Set.of("mangle", "manglar", "mangrove", "wetland", "marsh", "swamp",
                "forest", "bosque", "river", "rio", "island", "isla", "mountain", "montana", "landscape", "paisaje")
                .contains(name);
        if (!genericNaturalName) return false;
        String tourism = normalize(tags.get("tourism"));
        String leisure = normalize(tags.get("leisure"));
        String natural = normalize(tags.get("natural"));
        boolean visitorExperience = containsAny(type, "tourism sights", "tourist attraction", "tourism attraction",
                "national park", "nature reserve", "protected area", "visitor centre", "visitor center", "trail", "boardwalk")
                || Set.of("attraction", "museum", "viewpoint").contains(tourism)
                || Set.of("nature reserve", "protected area", "national park").contains(leisure)
                || Set.of("nature reserve", "protected area").contains(natural);
        return !visitorExperience;
    }

    private static boolean isSecondaryFragment(String name) {
        String normalized = normalize(name);
        return normalized.matches("(?:pa|poi|node|way|relation)[ _-]?\\d{2,}")
                || normalized.matches("(?:rosace|rose window|sculpture|statue) (?:north|south|east|west|nord|sud|est|ouest)")
                || normalized.matches("(?:fragment|detail|element|part) (?:of )?(?:a )?(?:monument|building|sculpture)");
    }

    public static long tourismCount(List<LugarPublico> places) {
        return places == null ? 0 : places.stream().filter(place -> tourismScore(place) >= 60).count();
    }

    static List<RankedPlace> deduplicate(List<RankedPlace> ranked) {
        List<RankedPlace> result = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();
        Set<String> ids = new LinkedHashSet<>();
        Set<String> nameLocations = new LinkedHashSet<>();
        for (RankedPlace item : ranked) {
            LugarPublico place = item.place();
            String id = normalize(place.etiquetas() == null ? null : place.etiquetas().get("wikidata"));
            String url = normalize(place.urlFuente());
            String name = normalize(place.nombre());
            String location = normalize(place.ubicacion());
            String nameLocation = name + "|" + location;
            boolean uniqueId = id.isBlank() || ids.add(id);
            boolean uniqueUrl = url.isBlank() || urls.add(url);
            boolean uniqueName = name.isBlank() || nameLocations.add(nameLocation);
            if (uniqueId && uniqueUrl && uniqueName) result.add(item);
        }
        return List.copyOf(result);
    }

    private static List<String> matchingInterests(LugarPublico place, List<String> interests) {
        if (interests == null || interests.isEmpty()) return List.of();
        return interests.stream().filter(interest -> matches(place, interest)).distinct().toList();
    }

    private static boolean matches(LugarPublico place, String interest) {
        String normalizedInterest = normalize(interest);
        if (normalizedInterest.isBlank()) return false;
        String haystack = normalize(place.nombre() + " " + safe(place.descripcion()) + " "
                + safe(place.categoria()) + " " + place.etiquetas());
        if (haystack.contains(normalizedInterest)) return true;
        return SYNONYMS.getOrDefault(normalizedInterest, Set.of()).stream().anyMatch(haystack::contains);
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }

    private static double distance(LugarPublico place) {
        if (place.etiquetas() == null) return Double.POSITIVE_INFINITY;
        try { return Double.parseDouble(place.etiquetas().get("distance_km")); }
        catch (RuntimeException ignored) { return Double.POSITIVE_INFINITY; }
    }

    private static int editorialCoverageSignal(LugarPublico place) {
        if (place.etiquetas() == null) return 0;
        String raw = place.etiquetas().get("wikidata_sitelinks");
        if (raw == null || !raw.matches("\\d+")) return 0;
        try { return (int) Math.min(8, Math.log10(Math.max(1, Long.parseLong(raw))) * 2); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static String tag(LugarPublico place, String key) {
        return place.etiquetas() == null ? "" : place.etiquetas().getOrDefault(key, "");
    }

    public static String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private static String safe(String value) { return value == null ? "" : value; }
    public record RankedPlace(LugarPublico place, List<String> matchedInterests, int score) { }
    public record RankingResult(List<RankedPlace> ranked, int inputCount, int excludedByType,
                                int duplicatesRemoved) { }
}
