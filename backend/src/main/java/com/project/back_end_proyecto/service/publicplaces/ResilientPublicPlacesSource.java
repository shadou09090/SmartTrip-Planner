package com.project.back_end_proyecto.service.publicplaces;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Combines specialized public sources; lodging discovery remains separate from tourism discovery. */
@Component
@Primary
@Profile("oracle")
public class ResilientPublicPlacesSource implements FuenteLugaresPublicos {
    private static final Logger log = LoggerFactory.getLogger(ResilientPublicPlacesSource.class);
    private static final int RESULT_LIMIT = 100;
    private static final int MINIMUM_NOTABLE_RESULTS = 10;
    private static final int MINIMUM_CITY_COVERAGE_CANDIDATES = 40;
    private final GeoapifyPlaceSource geoapify;
    private final OpenStreetMapPlaceSource openStreetMap;
    private final WikidataTourismPlaceSource wikidata;
    private final WikipediaGeosearchPlaceSource wikipedia;

    @Autowired
    public ResilientPublicPlacesSource(GeoapifyPlaceSource geoapify, OpenStreetMapPlaceSource openStreetMap,
                                      WikidataTourismPlaceSource wikidata,
                                      WikipediaGeosearchPlaceSource wikipedia) {
        this.geoapify = geoapify;
        this.openStreetMap = openStreetMap;
        this.wikidata = wikidata;
        this.wikipedia = wikipedia;
    }

    /** Compatibility constructor for focused tests and existing callers. */
    ResilientPublicPlacesSource(GeoapifyPlaceSource geoapify, OpenStreetMapPlaceSource openStreetMap) {
        this(geoapify, openStreetMap, null, null);
    }

    ResilientPublicPlacesSource(GeoapifyPlaceSource geoapify, OpenStreetMapPlaceSource openStreetMap,
                                WikidataTourismPlaceSource wikidata) {
        this(geoapify, openStreetMap, wikidata, null);
    }

    @Override
    public List<LugarPublico> buscar(String destino) {
        return searchDetailed(destino, false).lugares();
    }

    @Override
    public ResultadoLugares buscarConDiagnostico(String destino) {
        return searchDetailed(destino, false);
    }

    @Override
    public List<LugarPublico> buscarAlojamientos(String destino) {
        return searchDetailed(destino, true).lugares();
    }

    @Override
    public Optional<CoordenadasPublicas> geocodificarDestino(String destino) {
        try {
            Optional<CoordenadasPublicas> resolved = geoapify.geocodificarDestino(destino);
            if (resolved.isPresent()) return resolved;
            log.info("destination_geocode source=Geoapify result=no_match fallback=Nominatim");
        } catch (RuntimeException primaryFailure) {
            log.info("destination_geocode source=Geoapify result=unavailable fallback=Nominatim exceptionType={}",
                    primaryFailure.getClass().getSimpleName());
        }
        try { return openStreetMap.geocodificarDestino(destino); }
        catch (RuntimeException fallbackFailure) {
            log.info("destination_geocode source=Nominatim result=unavailable exceptionType={}",
                    fallbackFailure.getClass().getSimpleName());
            throw fallbackFailure;
        }
    }

    @Override
    public String areaGeograficaConsultada(String destino) {
        return geoapify.areaGeograficaConsultada(destino);
    }

    @Override
    public List<DestinoCandidato> buscarDestinos(String consulta) {
        return openStreetMap.buscarDestinos(consulta);
    }

    private ResultadoLugares searchDetailed(String destination, boolean lodging) {
        RuntimeException geoFailure = null;
        RuntimeException osmFailure = null;
        RuntimeException wikidataFailure = null;
        List<String> warnings = new ArrayList<>();
        List<String> queriedSources = new ArrayList<>();
        List<LugarPublico> combined = new ArrayList<>();

        try {
            queriedSources.add("Geoapify");
            combined.addAll(lodging ? geoapify.buscarAlojamientos(destination) : geoapify.buscar(destination));
        } catch (RuntimeException failure) {
            geoFailure = failure;
            warnings.add(lodging ? "Geoapify no pudo aportar alojamientos en esta búsqueda."
                    : "Geoapify no pudo aportar atracciones en esta búsqueda.");
            log.info("Geoapify source unavailable: kind={}, exceptionType={}",
                    lodging ? "lodging" : "tourism", failure.getClass().getSimpleName());
        }

        if (lodging) {
            if (combined.size() >= RESULT_LIMIT) return diagnosticResult(combined.subList(0, RESULT_LIMIT), warnings, queriedSources, destination);
            try {
                queriedSources.add("OpenStreetMap");
                return diagnosticResult(mergeUnique(combined, openStreetMap.buscarAlojamientos(destination)).stream()
                        .limit(RESULT_LIMIT).toList(), warnings, queriedSources, destination);
            } catch (RuntimeException failure) {
                if (combined.isEmpty() && geoFailure != null) {
                    throw new ErrorFuentePublica("Geoapify y OpenStreetMap no están disponibles para alojamientos; puedes reintentar.", failure);
                }
                if (combined.isEmpty()) throw new ErrorFuentePublica("OpenStreetMap no está disponible para alojamientos; puedes reintentar.", failure);
                warnings.add("OpenStreetMap no pudo complementar los alojamientos en esta búsqueda.");
                log.info("OSM lodging source unavailable; retaining Geoapify results: exceptionType={}",
                        failure.getClass().getSimpleName());
                return diagnosticResult(combined, warnings, queriedSources, destination);
            }
        }

        if (RelevanceScorer.tourismCount(combined) < MINIMUM_NOTABLE_RESULTS) {
            try {
                queriedSources.add("OpenStreetMap/Overpass");
                combined = mergeUnique(combined, openStreetMap.buscar(destination));
            } catch (RuntimeException failure) {
                osmFailure = failure;
                warnings.add("OpenStreetMap no pudo aportar atracciones en esta búsqueda.");
                log.info("OSM tourism source unavailable: exceptionType={}", failure.getClass().getSimpleName());
            }
        }

        // A full Geoapify page is not necessarily a broad city itinerary candidate pool. Query
        // Wikidata below 40 notable candidates so long trips can get distributed choices and
        // city-wide landmark coverage, even when the center already supplied a few attractions.
        if (wikidata != null && (RelevanceScorer.tourismCount(combined) < MINIMUM_CITY_COVERAGE_CANDIDATES
                || !hasGeographicCoverage(combined))) {
            try {
                queriedSources.add("Wikidata");
                combined = mergeUnique(combined, wikidata.buscar(destination));
            } catch (RuntimeException failure) {
                wikidataFailure = failure;
                warnings.add("Wikidata no pudo completar el descubrimiento de atractivos en esta búsqueda.");
                log.info("Wikidata tourism source unavailable: exceptionType={}", failure.getClass().getSimpleName());
            }
        }

        // Wikipedia geosearch is a separate editorial signal. It is queried when the map
        // sources do not already give enough noteworthy, geographically distributed candidates;
        // a WDQS outage therefore cannot veto the rest of the discovery pipeline.
        if (wikipedia != null && (RelevanceScorer.tourismCount(combined) < MINIMUM_CITY_COVERAGE_CANDIDATES
                || !hasGeographicCoverage(combined))) {
            try {
                queriedSources.add("Wikipedia Geosearch");
                combined = mergeUnique(combined, wikipedia.buscar(destination));
            } catch (RuntimeException failure) {
                warnings.add("Wikipedia no pudo aportar candidatos editoriales en esta búsqueda.");
                log.info("Wikipedia geosearch unavailable: exceptionType={}", failure.getClass().getSimpleName());
            }
        }

        if (combined.isEmpty() && (geoFailure != null || osmFailure != null || wikidataFailure != null)) {
            throw new ErrorFuentePublica("Los proveedores de lugares turísticos no están disponibles; puedes reintentar.",
                    wikidataFailure != null ? wikidataFailure : osmFailure != null ? osmFailure : geoFailure,
                    queriedSources, warnings);
        }
        // Rank the combined candidate pool before applying the response cap. Previously, a
        // full Geoapify page could consume the entire cap before notable OSM/Wikidata results
        // were considered, even when the provider page covered only a few central blocks.
        List<LugarPublico> rankedAndDistributed = RelevanceScorer.rankTourism(combined, List.of()).stream()
                .map(RelevanceScorer.RankedPlace::place).limit(RESULT_LIMIT).toList();
        return diagnosticResult(rankedAndDistributed, warnings, queriedSources, destination);
    }

    private ResultadoLugares diagnosticResult(List<LugarPublico> places, List<String> warnings,
                                               List<String> queriedSources, String destination) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (LugarPublico place : places) {
            String provider = tag(place, "provider");
            if (!provider.isBlank()) counts.merge(provider, 1, Integer::sum);
        }
        return new ResultadoLugares(places, warnings, queriedSources, counts,
                geoapify.areaGeograficaConsultada(destination));
    }

    private static List<LugarPublico> mergeUnique(List<LugarPublico> first, List<LugarPublico> second) {
        List<LugarPublico> unique = new ArrayList<>(first);
        for (LugarPublico candidate : second) {
            if (unique.stream().noneMatch(existing -> samePlace(existing, candidate))) unique.add(candidate);
        }
        return List.copyOf(unique);
    }

    private static boolean samePlace(LugarPublico first, LugarPublico second) {
        String firstQid = tag(first, "wikidata");
        String secondQid = tag(second, "wikidata");
        if (!firstQid.isBlank() && firstQid.equalsIgnoreCase(secondQid)) return true;
        String firstName = RelevanceScorer.normalize(first.nombre());
        if (firstName.isBlank() || !firstName.equals(RelevanceScorer.normalize(second.nombre()))) return false;
        String firstAddress = RelevanceScorer.normalize(first.ubicacion());
        String secondAddress = RelevanceScorer.normalize(second.ubicacion());
        if (!firstAddress.isBlank() && firstAddress.equals(secondAddress)) return true;
        if (first.latitud() == null || first.longitud() == null || second.latitud() == null || second.longitud() == null) return false;
        double firstLat = Math.toRadians(first.latitud().doubleValue());
        double secondLat = Math.toRadians(second.latitud().doubleValue());
        double deltaLat = secondLat - firstLat;
        double deltaLon = Math.toRadians(second.longitud().doubleValue() - first.longitud().doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(firstLat) * Math.cos(secondLat) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return 6_371_000d * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) <= 100;
    }

    private static String tag(LugarPublico place, String key) {
        return place.etiquetas() == null ? "" : place.etiquetas().getOrDefault(key, "").trim();
    }

    private static boolean hasGeographicCoverage(List<LugarPublico> places) {
        long cells = places.stream().filter(place -> place.latitud() != null && place.longitud() != null)
                .map(place -> Math.floor(place.latitud().doubleValue() * 20) + ":"
                        + Math.floor(place.longitud().doubleValue() * 20))
                .distinct().count();
        return cells >= 8;
    }
}
